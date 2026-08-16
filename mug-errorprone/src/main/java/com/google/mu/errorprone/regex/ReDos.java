package com.google.mu.errorprone.regex;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.graph.Walker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Utility to detect Exponential Degree of Ambiguity (EDA) / Catastrophic Backtracking (ReDoS) and
 * Polynomial Degree of Ambiguity (PDA) vulnerabilities in {@link RegexPattern} ASTs.
 *
 * @since 10.9
 */
public final class ReDos {

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to exponential backtracking
   * (ReDoS).
   *
   * @throws VulnerableRegexException if the pattern contains an exponential backtracking
   *     vulnerability
   */
  public static void checkRedosVulnerability(RegexPattern pattern) {
    Optional<RegexPattern> nullable = findNullableRepeatedElement(pattern);
    if (nullable.isPresent()) {
      String payload =
          attackPayloadForSubPattern(pattern, nullable.get(), sampleMatchingString(nullable.get()));
      List<Suggestion> suggestions = suggestRedosAlternatives(pattern);
      String message = formatErrorMessage(
          "exponential backtracking (ReDoS)",
          pattern,
          "contains unbounded repetition of nullable sub-pattern '" + nullable.get() + "'",
          payload,
          suggestions);
      throw new VulnerableRegexException(message, pattern, payload, suggestions);
    }
    Nfa nfa = Nfa.from(pattern);
    if (hasExponentialAmbiguity(nfa)) {
      String detail = findStructuralDetail(pattern)
          .orElse("contains ambiguous cycle across overlapping transitions");
      String payload = findNestedQuantified(pattern)
          .findFirst()
          .map(culprit -> attackPayloadForSubPattern(
              pattern, culprit, sampleMatchingString(unwrapGroup(culprit.element()))))
          .orElseGet(() -> attackPayload("", sampleMatchingString(pattern)));
      List<Suggestion> suggestions = suggestRedosAlternatives(pattern);
      String message = formatErrorMessage(
          "exponential backtracking (ReDoS)", pattern, detail, payload, suggestions);
      throw new VulnerableRegexException(message, pattern, payload, suggestions);
    }
  }

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to polynomial backtracking (PDA).
   *
   * @throws VulnerableRegexException if the pattern contains polynomial degree of ambiguity (e.g.
   *     consecutive overlapping quantifiers)
   */
  public static void checkPolynomialBacktracking(RegexPattern pattern) {
    Nfa nfa = Nfa.from(pattern);
    if (hasPolynomialAmbiguity(nfa)) {
      String desc = findPolynomialDetail(pattern).orElse("contains overlapping consecutive cycles");
      String payload = (pattern instanceof RegexPattern.Sequence seq
              ? findOverlappingQuantifiers(seq)
                  .findFirst()
                  .map(pair -> attackPayloadForOverlappingPair(seq, pair))
              : Optional.<String>empty())
          .orElseGet(() -> attackPayload("", sampleMatchingString(pattern)));
      List<Suggestion> suggestions = suggestPolynomialAlternatives(pattern);
      String message =
          formatErrorMessage("polynomial backtracking (PDA)", pattern, desc, payload, suggestions);
      throw new VulnerableRegexException(message, pattern, payload, suggestions);
    }
  }

  /**
   * Returns a list of suggested alternatives or safe rewrites for an exponential ReDoS vulnerable
   * pattern, ordered by preference (Regex -> StringFormat -> Substring -> Parser).
   */
  public static List<Suggestion> suggestRedosAlternatives(RegexPattern pattern) {
    requireNonNull(pattern);
    List<Suggestion> suggestions = new ArrayList<>();
    suggestRedosRegexSuggestion(pattern).ifPresent(suggestions::add);
    suggestStringFormat(pattern).ifPresent(suggestions::add);
    suggestSubstring(pattern).ifPresent(suggestions::add);
    if (isStructuredGrammar(pattern)) {
      suggestions.add(
          new Suggestion.ParserSuggestion(
              /* replacement= */ "Parsers.integer().repeatedly()",
              "Parser combinators parse deterministically with prioritized choice and do not"
                  + " backtrack non-deterministically across ambiguous boundaries"));
    }
    return List.copyOf(suggestions);
  }

  /**
   * Returns a list of suggested alternatives or safe rewrites for a polynomial backtracking
   * vulnerable pattern, ordered by preference (Regex -> StringFormat -> Substring -> Parser).
   */
  public static List<Suggestion> suggestPolynomialAlternatives(RegexPattern pattern) {
    requireNonNull(pattern);
    List<Suggestion> suggestions = new ArrayList<>();
    suggestPolynomialRegexSuggestion(pattern).ifPresent(suggestions::add);
    suggestStringFormat(pattern).ifPresent(suggestions::add);
    suggestSubstring(pattern).ifPresent(suggestions::add);
    return List.copyOf(suggestions);
  }

  private static Optional<Suggestion.SubstringSuggestion> suggestSubstring(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Sequence seq) {
      List<RegexPattern> elements = seq.elements();
      if (elements.size() == 3) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal lit && isWildcard(e2)) {
          String delim = lit.value();
          if (!delim.isEmpty()) {
            String delimEscaped = delim.length() == 1 ? "'" + delim + "'" : "\"" + delim + "\"";
            String replacement = "Substring.first(" + delimEscaped + ").split(input)";
            return Optional.of(
                new Suggestion.SubstringSuggestion(
                    replacement, "Substring splits at the first occurrence of the delimiter"));
          }
        }
      }
      if (elements.size() == 5) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        RegexPattern e3 = elements.get(3);
        RegexPattern e4 = unwrapGroup(elements.get(4));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal openLit && isWildcard(e2)
            && e3 instanceof RegexPattern.Literal closeLit && isWildcard(e4)) {
          String open = openLit.value();
          String close = closeLit.value();
          if (!open.isEmpty() && !close.isEmpty()) {
            String replacement =
                "Substring.between(\"" + open + "\", \"" + close + "\").from(input)";
            return Optional.of(
                new Suggestion.SubstringSuggestion(
                    replacement, "Substring.between extracts the first matching enclosed range"));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static String formatErrorMessage(
      String vulnerabilityType,
      RegexPattern pattern,
      String detail,
      String payload,
      List<Suggestion> suggestions) {
    StringBuilder sb = new StringBuilder();
    sb.append("Regular expression is vulnerable to ")
        .append(vulnerabilityType)
        .append(": '")
        .append(pattern)
        .append("' ")
        .append(detail);
    if (!payload.isEmpty()) {
      sb.append("\n  attack payload: \"").append(payload).append("\"");
    }
    if (!suggestions.isEmpty()) {
      Suggestion first = suggestions.get(0);
      String replacement =
          first instanceof Suggestion.RegexSuggestion
              ? "'" + first.replacement() + "'"
              : first.replacement();
      sb.append("\n  consider: ").append(replacement);
      for (String caveat : first.caveats()) {
        sb.append("\n  caveat: ").append(caveat);
      }
    }
    return sb.toString();
  }

  private static Optional<Suggestion.StringFormatSuggestion> suggestStringFormat(
      RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Sequence seq) {
      List<RegexPattern> elements = seq.elements();
      if (elements.size() == 3) {
        RegexPattern e0 = unwrapGroup(elements.get(0));
        RegexPattern e1 = elements.get(1);
        RegexPattern e2 = unwrapGroup(elements.get(2));
        if (isWildcard(e0) && e1 instanceof RegexPattern.Literal lit && isWildcard(e2)) {
          String delim = lit.value();
          if (!delim.isEmpty()) {
            String format = "{left}" + delim + "{right}";
            return Optional.of(
                new Suggestion.StringFormatSuggestion(
                    format,
                    "StringFormat matches delimiters from left to right, whereas greedy '.*' in"
                        + " regex matches the last occurrence"));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static boolean isWildcard(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      return inner instanceof RegexPattern.PredefinedCharClass pcc
          && pcc == RegexPattern.PredefinedCharClass.ANY_CHAR;
    }
    return false;
  }

  private static boolean isStructuredGrammar(RegexPattern pattern) {
    return Walker.inTree(ReDos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Quantified.class::isInstance)
        .map(RegexPattern.Quantified.class::cast)
        .anyMatch(q -> {
          RegexPattern inner = unwrapGroup(q.element());
          if (inner instanceof RegexPattern.Alternation alt) {
            return alt.alternatives().stream()
                .anyMatch(branch ->
                    branch instanceof RegexPattern.Sequence
                        || branch instanceof RegexPattern.Quantified);
          }
          return false;
        });
  }

  /**
   * Suggests a safe rewrite for an exponential ReDoS vulnerable pattern if a high-confidence fix is
   * known.
   */
  public static Optional<String> suggestRedosRewrite(RegexPattern pattern) {
    return suggestRedosRegexSuggestion(pattern).map(Suggestion.RegexSuggestion::replacement);
  }

  private static Optional<Suggestion.RegexSuggestion> suggestRedosRegexSuggestion(
      RegexPattern pattern) {
    requireNonNull(pattern);
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified innerQ) {
        boolean canBeEmpty = innerQ.metadata().minSize() == 0 || q.metadata().minSize() == 0;
        String op = canBeEmpty ? "*" : "+";
        String replacement = innerQ.element().toString() + op;
        return Optional.of(new Suggestion.RegexSuggestion(replacement));
      }
      if (inner.metadata().minSize() == 0 && inner.metadata().maxSize() > 0) {
        String replacement = inner.toString() + "*";
        return Optional.of(new Suggestion.RegexSuggestion(replacement));
      }
    }
    return Optional.empty();
  }

  /**
   * Suggests a safe rewrite for a polynomial backtracking vulnerable pattern if a high-confidence
   * fix is known (e.g. using possessive quantifier).
   */
  public static Optional<String> suggestPolynomialRewrite(RegexPattern pattern) {
    return suggestPolynomialRegexSuggestion(pattern).map(Suggestion.RegexSuggestion::replacement);
  }

  private static Optional<Suggestion.RegexSuggestion> suggestPolynomialRegexSuggestion(
      RegexPattern pattern) {
    requireNonNull(pattern);
    if (pattern instanceof RegexPattern.Sequence seq) {
      return findOverlappingQuantifiers(seq)
          .map(p -> {
            if (p.secondIndex() == p.firstIndex() + 1
                && p.first().element().equals(p.second().element())
                && p.first().quantifier() instanceof RegexPattern.AtLeast q1
                && p.second().quantifier() instanceof RegexPattern.AtLeast q2) {
              int totalMin = q1.min() + q2.min();
              RegexPattern merged = new RegexPattern.Quantified(
                  p.first().element(), RegexPattern.Quantifier.atLeast(totalMin));
              List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
              rewritten.set(p.firstIndex(), merged);
              rewritten.remove(p.secondIndex());
              String replacement =
                  rewritten.size() == 1
                      ? rewritten.get(0).toString()
                      : new RegexPattern.Sequence(rewritten).toString();
              return new Suggestion.RegexSuggestion(replacement);
            }
            RegexPattern rewrittenFirst = new RegexPattern.Quantified(
                p.first().element(), p.first().quantifier().possessive());
            List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
            rewritten.set(p.firstIndex(), rewrittenFirst);
            String replacement = new RegexPattern.Sequence(rewritten).toString();
            return new Suggestion.RegexSuggestion(
                replacement,
                "Possessive quantifier '" + rewrittenFirst
                    + "' prevents backtracking and may fail if subsequent tokens require"
                    + " characters greedily consumed by '" + rewrittenFirst + "'");
          })
          .findFirst();
    }
    return Optional.empty();
  }

  private static String attackPayload(String prefix, String pump) {
    int repetitions = Math.max(1, 30 / Math.max(1, pump.length()));
    return prefix + pump.repeat(repetitions) + "!";
  }

  private static String attackPayloadForSubPattern(
      RegexPattern pattern, RegexPattern target, String pump) {
    if (pattern instanceof RegexPattern.Sequence seq) {
      StringBuilder prefix = new StringBuilder();
      for (RegexPattern elem : seq.elements()) {
        if (containsNode(elem, target)) {
          break;
        }
        prefix.append(sampleMatchingString(elem));
      }
      return attackPayload(prefix.toString(), pump);
    }
    return attackPayload("", pump);
  }

  private static String attackPayloadForOverlappingPair(
      RegexPattern.Sequence seq, OverlappingQuantifierPair pair) {
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < pair.firstIndex(); i++) {
      prefix.append(sampleMatchingString(seq.elements().get(i)));
    }
    String pump = sampleMatchingString(pair.first().element());
    return attackPayload(prefix.toString(), pump);
  }

  private static boolean containsNode(RegexPattern root, RegexPattern target) {
    return root.equals(target)
        || Walker.inTree(ReDos::childrenOf)
            .preOrderFrom(root)
            .anyMatch(node -> node.equals(target));
  }

  private static Stream<RegexPattern> childrenOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> seq.elements().stream();
      case RegexPattern.Alternation alt -> alt.alternatives().stream();
      case RegexPattern.Group group -> Stream.of(group.content());
      case RegexPattern.Quantified q -> Stream.of(q.element());
      default -> Stream.empty();
    };
  }

  private static Optional<String> findPolynomialDetail(RegexPattern pattern) {
    return Walker.inTree(ReDos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Sequence.class::isInstance)
        .map(RegexPattern.Sequence.class::cast)
        .flatMap(ReDos::findOverlappingQuantifiers)
        .map(pair ->
            "contains consecutive overlapping quantifiers on '" + pair.first() + "' and '"
                + pair.second() + "'")
        .findFirst();
  }

  private record OverlappingQuantifierPair(
      int firstIndex,
      int secondIndex,
      RegexPattern.Quantified first,
      RegexPattern.Quantified second) {}

  private static Stream<OverlappingQuantifierPair> findOverlappingQuantifiers(
      RegexPattern.Sequence seq) {
    List<RegexPattern> elements = seq.elements();
    for (int i = 0; i < elements.size(); i++) {
      RegexPattern ei = elements.get(i);
      if (isUnboundedQuantified(ei)) {
        for (int j = i + 1; j < elements.size(); j++) {
          RegexPattern ej = elements.get(j);
          if (isUnboundedQuantified(ej)) {
            if (charRangesOf(ei).intersects(charRangesOf(ej))) {
              return Stream.of(
                  new OverlappingQuantifierPair(
                      i, j, (RegexPattern.Quantified) ei, (RegexPattern.Quantified) ej));
            }
          }
          if (ej.metadata().minSize() > 0) {
            break;
          }
        }
      }
    }
    return Stream.empty();
  }

  private static boolean isUnboundedQuantified(RegexPattern pattern) {
    return pattern instanceof RegexPattern.Quantified q && !q.quantifier().isPossessive()
        && switch (q.quantifier()) {
          case RegexPattern.AtLeast atLeast -> atLeast.min() >= 0;
          case RegexPattern.Limited limited -> limited.max() > 5;
          default -> false;
        };
  }

  private static CharRanges charRangesOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Quantified q -> charRangesOf(q.element());
      case RegexPattern.Group group -> charRangesOf(group.content());
      case RegexPattern.CharacterSet cs -> CharRanges.from(cs);
      case RegexPattern.PredefinedCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.PosixCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.Literal lit -> {
        String val = lit.value();
        yield val.isEmpty() ? CharRanges.ANY : CharRanges.of(val.charAt(0));
      }
      default -> CharRanges.ANY;
    };
  }

  private static Optional<RegexPattern> findNullableRepeatedElement(RegexPattern pattern) {
    return Walker.inTree(ReDos::childrenOf)
        .preOrderFrom(pattern)
        .filter(ReDos::isUnboundedNullable)
        .map(p -> ((RegexPattern.Quantified) p).element())
        .findFirst();
  }

  private static boolean isUnboundedNullable(RegexPattern pattern) {
    return pattern instanceof RegexPattern.Quantified q && !q.quantifier().isPossessive()
        && q.element().metadata().minSize() == 0 && q.element().metadata().maxSize() > 0
        && switch (q.quantifier()) {
          case RegexPattern.AtLeast atLeast -> atLeast.min() >= 0;
          case RegexPattern.Limited limited -> limited.max() > 10;
          default -> false;
        };
  }

  private static Optional<String> findStructuralDetail(RegexPattern pattern) {
    return Walker.inTree(ReDos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Quantified.class::isInstance)
        .map(RegexPattern.Quantified.class::cast)
        .flatMap(ReDos::structuralDetailOf)
        .findFirst();
  }

  private static Stream<String> structuralDetailOf(RegexPattern.Quantified q) {
    RegexPattern inner = unwrapGroup(q.element());
    return switch (inner) {
      case RegexPattern.Quantified quantified ->
          Stream.of("contains nested quantifiers on '" + inner + "'");
      case RegexPattern.Alternation alt -> Stream.of(
          findNestedQuantified(inner)
              .map(nq -> "contains nested quantifiers on '" + nq + "'")
              .findFirst()
              .orElse("contains overlapping alternation branches '" + inner + "'"));
      case RegexPattern.Sequence seq -> findNestedQuantified(inner)
          .limit(1)
          .map(nq -> "contains nested quantifiers on '" + nq + "'");
      default -> Stream.empty();
    };
  }

  private static RegexPattern unwrapGroup(RegexPattern pattern) {
    while (pattern instanceof RegexPattern.Group group) {
      pattern = group.content();
    }
    return pattern;
  }

  private static Stream<RegexPattern.Quantified> findNestedQuantified(RegexPattern pattern) {
    return Walker.inTree(ReDos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Quantified.class::isInstance)
        .map(RegexPattern.Quantified.class::cast);
  }

  private static String sampleMatchingString(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Literal lit -> lit.value().isEmpty() ? "a" : lit.value();
      case RegexPattern.CharacterSet cs -> {
        CharRanges ranges = CharRanges.from(cs);
        yield ranges.isEmpty() ? "a" : String.valueOf((char) ranges.ranges().get(0).start());
      }
      case RegexPattern.PredefinedCharClass pcc -> {
        CharRanges ranges = CharRanges.from(pcc);
        yield ranges.isEmpty() ? "a" : String.valueOf((char) ranges.ranges().get(0).start());
      }
      case RegexPattern.PosixCharClass pcc -> {
        CharRanges ranges = CharRanges.from(pcc);
        yield ranges.isEmpty() ? "a" : String.valueOf((char) ranges.ranges().get(0).start());
      }
      case RegexPattern.Quantified q -> sampleMatchingString(q.element());
      case RegexPattern.Group group -> sampleMatchingString(group.content());
      case RegexPattern.Sequence seq -> {
        StringBuilder sb = new StringBuilder();
        for (RegexPattern elem : seq.elements()) {
          sb.append(sampleMatchingString(elem));
        }
        yield sb.length() == 0 ? "a" : sb.toString();
      }
      case RegexPattern.Alternation alt -> {
        List<RegexPattern> alts = alt.alternatives();
        yield alts.isEmpty() ? "a" : sampleMatchingString(alts.get(0));
      }
      case RegexPattern.Anchor anchor -> "";
      default -> "a";
    };
  }

  private static boolean hasExponentialAmbiguity(Nfa nfa) {
    if (nfa.charTransitions.isEmpty()) {
      return false;
    }
    Graph<TransitionPair> graph = productGraph(nfa);
    return Walker.inGraph((TransitionPair u) -> graph.successors(u).stream())
        .stronglyConnectedComponentsFrom(graph.nodes())
        .filter(scc -> scc.size() > 1 || graph.hasEdgeConnecting(scc.get(0), scc.get(0)))
        .anyMatch(scc -> isAmbiguousScc(nfa, graph, new HashSet<>(scc)));
  }

  private static boolean isAmbiguousScc(
      Nfa nfa, Graph<TransitionPair> graph, Set<TransitionPair> scc) {
    boolean hasOffDiagonal = false;
    boolean hasDiagonal = false;
    boolean hasBranching = false;
    for (TransitionPair node : scc) {
      if (node.isDiagonal()) {
        hasDiagonal = true;
        Nfa.CharTransition ti = nfa.charTransitions.get(node.left());
        for (TransitionPair next : graph.successors(node)) {
          if (scc.contains(next) && next.isDiagonal()) {
            Nfa.CharTransition tip = nfa.charTransitions.get(next.left());
            if (nfa.countEpsilonPaths(ti.target(), tip.source()) >= 2) {
              return true;
            }
          }
        }
      } else {
        hasOffDiagonal = true;
      }
      if (graph.successors(node).stream().filter(scc::contains).count() >= 2) {
        hasBranching = true;
      }
    }
    return hasOffDiagonal && (hasDiagonal || hasBranching);
  }

  private static boolean hasPolynomialAmbiguity(Nfa nfa) {
    if (nfa.charTransitions.size() < 2) {
      return false;
    }
    Graph<TransitionPair> graph = productGraph(nfa);
    List<List<TransitionPair>> sccs = Walker.inGraph(
            (TransitionPair u) -> graph.successors(u).stream())
        .stronglyConnectedComponentsFrom(graph.nodes())
        .collect(toList());

    Map<TransitionPair, Integer> sccMap = new HashMap<>();
    Set<TransitionPair> inCycle = new HashSet<>();
    for (int id = 0; id < sccs.size(); id++) {
      List<TransitionPair> scc = sccs.get(id);
      boolean isCycle =
          scc.size() > 1 || (scc.size() == 1 && graph.hasEdgeConnecting(scc.get(0), scc.get(0)));
      for (TransitionPair node : scc) {
        sccMap.put(node, id);
        if (isCycle) {
          inCycle.add(node);
        }
      }
    }

    List<DiagonalCycle> cycles = nfa.charTransitions.stream()
        .map(t -> new TransitionPair(t.id(), t.id()))
        .filter(u -> graph.nodes().contains(u) && inCycle.contains(u))
        .map(u -> new DiagonalCycle(u, nfa.charTransitions.get(u.left()).chars(), sccMap.get(u)))
        .toList();

    for (int a = 0; a < cycles.size(); a++) {
      DiagonalCycle ca = cycles.get(a);
      for (int b = 0; b < cycles.size(); b++) {
        if (a != b) {
          DiagonalCycle cb = cycles.get(b);
          if (ca.sccId() != cb.sccId() && ca.chars().intersects(cb.chars())) {
            boolean reachable = Walker.inGraph((TransitionPair u) -> {
              if (u.isDiagonal()
                  && nfa.charTransitions.get(u.left()).chars().intersects(ca.chars())) {
                return graph.successors(u).stream();
              }
              return Stream.empty();
            })
                .breadthFirstFrom(ca.state())
                .anyMatch(cb.state()::equals);
            if (reachable) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private record DiagonalCycle(TransitionPair state, CharRanges chars, int sccId) {}

  private record TransitionPair(int left, int right) {
    boolean isDiagonal() {
      return left == right;
    }
  }

  private static Graph<TransitionPair> productGraph(Nfa nfa) {
    MutableGraph<TransitionPair> rawGraph = GraphBuilder.directed().allowsSelfLoops(true).build();

    List<List<Nfa.CharTransition>> nextTransitions =
        nfa.charTransitions.stream().map(t -> nfa.reachableCharTransitions(t.target())).toList();

    for (int i = 0; i < nfa.charTransitions.size(); i++) {
      List<Nfa.CharTransition> candI = nextTransitions.get(i);
      for (int j = 0; j < nfa.charTransitions.size(); j++) {
        List<Nfa.CharTransition> candJ = nextTransitions.get(j);
        TransitionPair u = new TransitionPair(i, j);
        for (Nfa.CharTransition tip : candI) {
          for (Nfa.CharTransition tjp : candJ) {
            if (tip.chars().intersects(tjp.chars())) {
              rawGraph.putEdge(u, new TransitionPair(tip.id(), tjp.id()));
            }
          }
        }
      }
    }

    List<Nfa.CharTransition> startTransitions = nfa.reachableCharTransitions(nfa.startState);
    List<TransitionPair> startPairs = new ArrayList<>();
    for (Nfa.CharTransition ti : startTransitions) {
      for (Nfa.CharTransition tj : startTransitions) {
        if (ti.chars().intersects(tj.chars())) {
          TransitionPair node = new TransitionPair(ti.id(), tj.id());
          startPairs.add(node);
          rawGraph.addNode(node);
        }
      }
    }

    List<Nfa.CharTransition> acceptTransitions =
        nfa.charTransitions.stream().filter(t -> nfa.canReachAccept(t.target())).toList();
    List<TransitionPair> acceptPairs = new ArrayList<>();
    for (Nfa.CharTransition ti : acceptTransitions) {
      for (Nfa.CharTransition tj : acceptTransitions) {
        TransitionPair node = new TransitionPair(ti.id(), tj.id());
        acceptPairs.add(node);
        rawGraph.addNode(node);
      }
    }

    Set<TransitionPair> canReachAccept = Walker.inGraph(
            (TransitionPair u) -> rawGraph.predecessors(u).stream())
        .preOrderFrom(acceptPairs)
        .collect(toSet());

    Set<TransitionPair> activeNodes = Walker.inGraph(
            (TransitionPair u) -> rawGraph.successors(u).stream())
        .preOrderFrom(startPairs)
        .filter(canReachAccept::contains)
        .collect(toSet());

    return Graphs.inducedSubgraph(rawGraph, activeNodes);
  }

  private ReDos() {}
}

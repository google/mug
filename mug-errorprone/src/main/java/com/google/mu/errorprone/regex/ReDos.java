package com.google.mu.errorprone.regex;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.graph.Walker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Static analyzer for detecting Exponential Degree of Ambiguity (EDA) / Catastrophic Backtracking
 * (ReDoS) and Polynomial Degree of Ambiguity (PDA) vulnerabilities in {@link RegexPattern} ASTs.
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
      String suggestionMsg = formatSuggestionMessage(suggestions);
      throw new VulnerableRegexException(
          "Regular expression is vulnerable to exponential backtracking (ReDoS): '" + pattern
              + "' contains unbounded repetition of nullable sub-pattern '" + nullable.get()
              + "' (attack payload: \"" + payload + "\")" + suggestionMsg,
          pattern,
          payload,
          suggestions);
    }
    Nfa nfa = Nfa.from(pattern);
    if (hasExponentialAmbiguity(ProductGraph.from(nfa))) {
      String detail = findStructuralDetail(pattern)
          .orElse("contains ambiguous cycle across overlapping transitions");
      Optional<RegexPattern.Quantified> culprit = findNestedQuantified(pattern).findFirst();
      String payload =
          culprit.isPresent()
              ? attackPayloadForSubPattern(
                  pattern,
                  culprit.get(),
                  sampleMatchingString(unwrapGroup(culprit.get().element())))
              : attackPayload("", sampleMatchingString(pattern));
      List<Suggestion> suggestions = suggestRedosAlternatives(pattern);
      String suggestionMsg = formatSuggestionMessage(suggestions);
      throw new VulnerableRegexException(
          "Regular expression is vulnerable to exponential backtracking (ReDoS): '" + pattern + "' "
              + detail + " (attack payload: \"" + payload + "\")" + suggestionMsg,
          pattern,
          payload,
          suggestions);
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
    if (hasPolynomialAmbiguity(ProductGraph.from(nfa))) {
      String desc = findPolynomialDetail(pattern).orElse("contains overlapping consecutive cycles");
      Optional<OverlappingQuantifierPair> pair =
          pattern instanceof RegexPattern.Sequence seq
              ? findOverlappingQuantifiers(seq).findFirst()
              : Optional.empty();
      String payload =
          pair.isPresent()
              ? attackPayloadForOverlappingPair((RegexPattern.Sequence) pattern, pair.get())
              : attackPayload("", sampleMatchingString(pattern));
      List<Suggestion> suggestions = suggestPolynomialAlternatives(pattern);
      String suggestionMsg = formatSuggestionMessage(suggestions);
      throw new VulnerableRegexException(
          "Regular expression is vulnerable to polynomial backtracking (PDA): '" + pattern + "' "
              + desc + " (attack payload: \"" + payload + "\")" + suggestionMsg,
          pattern,
          payload,
          suggestions);
    }
  }

  /**
   * Returns a list of suggested alternatives or safe rewrites for an exponential ReDoS vulnerable
   * pattern, ordered by preference (Regex -> StringFormat -> Substring -> Parser).
   */
  public static List<Suggestion> suggestRedosAlternatives(RegexPattern pattern) {
    Objects.requireNonNull(pattern);
    List<Suggestion> suggestions = new ArrayList<>();
    suggestRedosRegexSuggestion(pattern).ifPresent(suggestions::add);
    suggestStringFormat(pattern).ifPresent(suggestions::add);
    suggestSubstring(pattern).ifPresent(suggestions::add);
    if (isStructuredGrammar(pattern)) {
      suggestions.add(
          new Suggestion.ParserSuggestion(
              "Parsers.integer().repeatedly()",
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
    Objects.requireNonNull(pattern);
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
            return Optional.of(
                new Suggestion.SubstringSuggestion(
                    "Substring.first(" + delimEscaped + ").split(input)",
                    "Substring splits at the first occurrence of the delimiter"));
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
            return Optional.of(
                new Suggestion.SubstringSuggestion(
                    "Substring.between(\"" + open + "\", \"" + close + "\").from(input)",
                    "Substring.between extracts the first matching enclosed range"));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static String formatSuggestionMessage(List<Suggestion> suggestions) {
    if (suggestions.isEmpty()) {
      return "";
    }
    Suggestion first = suggestions.get(0);
    if (first instanceof Suggestion.RegexSuggestion regex) {
      return " (suggested rewrite: '" + regex.replacement() + "')";
    }
    return " (Consider using " + first.replacement() + ")";
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
            return Optional.of(
                new Suggestion.StringFormatSuggestion(
                    "{left}" + delim + "{right}",
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
    Objects.requireNonNull(pattern);
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified innerQ) {
        boolean canBeEmpty = innerQ.metadata().minSize() == 0 || q.metadata().minSize() == 0;
        String op = canBeEmpty ? "*" : "+";
        return Optional.of(new Suggestion.RegexSuggestion(innerQ.element().toString() + op));
      }
      if (inner.metadata().minSize() == 0 && inner.metadata().maxSize() > 0) {
        return Optional.of(new Suggestion.RegexSuggestion(inner.toString() + "*"));
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
    Objects.requireNonNull(pattern);
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
              String regex =
                  rewritten.size() == 1
                      ? rewritten.get(0).toString()
                      : new RegexPattern.Sequence(rewritten).toString();
              return new Suggestion.RegexSuggestion(regex);
            }
            RegexPattern rewrittenFirst = new RegexPattern.Quantified(
                p.first().element(), p.first().quantifier().possessive());
            List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
            rewritten.set(p.firstIndex(), rewrittenFirst);
            String regex = new RegexPattern.Sequence(rewritten).toString();
            return new Suggestion.RegexSuggestion(
                regex,
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

  private static boolean hasExponentialAmbiguity(ProductGraph g) {
    if (g.tCount == 0) {
      return false;
    }

    // Check if any SCC with a cycle has non-deterministic branching or multi-epsilon loopbacks
    for (List<Integer> scc : g.sccs) {
      if (scc.size() > 1 || (scc.size() == 1 && g.adj[scc.get(0)].contains(scc.get(0)))) {
        Set<Integer> sccSet = new HashSet<>(scc);
        boolean hasOffDiagonal = false;
        boolean hasDiagonal = false;
        boolean hasBranching = false;
        for (int node : scc) {
          int row = node / g.tCount;
          int col = node % g.tCount;
          if (row == col) {
            hasDiagonal = true;
            Nfa.CharTransition ti = g.nfa.charTransitions.get(row);
            for (int next : g.adj[node]) {
              if (sccSet.contains(next) && (next / g.tCount == next % g.tCount)) {
                Nfa.CharTransition tip = g.nfa.charTransitions.get(next / g.tCount);
                if (g.nfa.countEpsilonPaths(ti.target(), tip.source()) >= 2) {
                  return true;
                }
              }
            }
          } else {
            hasOffDiagonal = true;
          }
          int branchCount = 0;
          for (int next : g.adj[node]) {
            if (sccSet.contains(next)) {
              branchCount++;
            }
          }
          if (branchCount >= 2) {
            hasBranching = true;
          }
        }
        if (hasOffDiagonal && (hasDiagonal || hasBranching)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean hasPolynomialAmbiguity(ProductGraph g) {
    if (g.tCount < 2) {
      return false;
    }

    List<DiagonalCycle> cycles = new ArrayList<>();
    for (int i = 0; i < g.tCount; i++) {
      int u = i * g.tCount + i;
      if (g.active[u] && g.inCycle[u]) {
        Nfa.CharTransition ti = g.nfa.charTransitions.get(i);
        cycles.add(new DiagonalCycle(u, ti.chars(), g.sccMap[u]));
      }
    }

    for (int a = 0; a < cycles.size(); a++) {
      DiagonalCycle ca = cycles.get(a);
      for (int b = 0; b < cycles.size(); b++) {
        if (a != b) {
          DiagonalCycle cb = cycles.get(b);
          if (ca.sccId() != cb.sccId() && ca.chars().intersects(cb.chars())) {
            boolean reachable = Walker.inGraph((Integer u) -> {
              int row = u / g.tCount;
              int col = u % g.tCount;
              if (row == col) {
                Nfa.CharTransition tu = g.nfa.charTransitions.get(row);
                if (tu.chars().intersects(ca.chars())) {
                  return g.adj[u].stream().filter(v -> g.active[v]);
                }
              }
              return Stream.empty();
            })
                .breadthFirstFrom(ca.state())
                .anyMatch(v -> v == cb.state());
            if (reachable) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private record DiagonalCycle(int state, CharRanges chars, int sccId) {}

  private static final class ProductGraph {
    final Nfa nfa;
    final int tCount;
    final List<Integer>[] adj;
    final boolean[] active;
    final List<List<Integer>> sccs;
    final int[] sccMap;
    final boolean[] inCycle;

    static ProductGraph from(Nfa nfa) {
      int tCount = nfa.charTransitions.size();
      int sCount = nfa.states.size();
      int vCount = tCount * tCount;

      @SuppressWarnings("unchecked")
      List<Integer>[] transitionsBySource = new List[sCount];
      @SuppressWarnings("unchecked")
      List<Integer>[] revEps = new List[sCount];
      for (int s = 0; s < sCount; s++) {
        transitionsBySource[s] = new ArrayList<>();
        revEps[s] = new ArrayList<>();
      }
      for (int i = 0; i < tCount; i++) {
        transitionsBySource[nfa.charTransitions.get(i).source()].add(i);
      }
      for (int s = 0; s < sCount; s++) {
        for (int next : nfa.states.get(s).epsilonTransitions) {
          revEps[next].add(s);
        }
      }

      @SuppressWarnings("unchecked")
      List<Integer>[] transitionsReachableFrom = new List[sCount];
      for (int s = 0; s < sCount; s++) {
        List<Integer> reachable = new ArrayList<>();
        Walker.inGraph((Integer st) -> nfa.states.get(st).epsilonTransitions.stream())
            .preOrderFrom(s)
            .forEach(st -> reachable.addAll(transitionsBySource[st]));
        transitionsReachableFrom[s] = reachable;
      }

      Set<Integer> canReachAcceptStates = Walker.inGraph((Integer st) -> revEps[st].stream())
          .preOrderFrom(nfa.acceptState)
          .collect(toSet());

      @SuppressWarnings("unchecked")
      List<Integer>[] adj = new List[vCount];
      @SuppressWarnings("unchecked")
      List<Integer>[] revAdj = new List[vCount];
      for (int i = 0; i < vCount; i++) {
        adj[i] = new ArrayList<>();
        revAdj[i] = new ArrayList<>();
      }

      for (int i = 0; i < tCount; i++) {
        Nfa.CharTransition ti = nfa.charTransitions.get(i);
        List<Integer> candI = transitionsReachableFrom[ti.target()];
        if (candI.isEmpty()) {
          continue;
        }
        for (int j = 0; j < tCount; j++) {
          Nfa.CharTransition tj = nfa.charTransitions.get(j);
          List<Integer> candJ = transitionsReachableFrom[tj.target()];
          if (candJ.isEmpty()) {
            continue;
          }
          int u = i * tCount + j;
          for (int ip : candI) {
            Nfa.CharTransition tip = nfa.charTransitions.get(ip);
            for (int jp : candJ) {
              Nfa.CharTransition tjp = nfa.charTransitions.get(jp);
              if (tip.chars().intersects(tjp.chars())) {
                int v = ip * tCount + jp;
                adj[u].add(v);
                revAdj[v].add(u);
              }
            }
          }
        }
      }

      List<Integer> initialStartNodes = new ArrayList<>();
      List<Integer> startCandidates = transitionsReachableFrom[nfa.startState];
      for (int i : startCandidates) {
        Nfa.CharTransition ti = nfa.charTransitions.get(i);
        for (int j : startCandidates) {
          Nfa.CharTransition tj = nfa.charTransitions.get(j);
          if (ti.chars().intersects(tj.chars())) {
            initialStartNodes.add(i * tCount + j);
          }
        }
      }
      Set<Integer> reachableFromStart = Walker.inGraph((Integer u) -> adj[u].stream())
          .preOrderFrom(initialStartNodes)
          .collect(toSet());

      List<Integer> acceptTransitions = new ArrayList<>();
      for (int i = 0; i < tCount; i++) {
        if (canReachAcceptStates.contains(nfa.charTransitions.get(i).target())) {
          acceptTransitions.add(i);
        }
      }
      List<Integer> initialAcceptNodes = new ArrayList<>();
      for (int i : acceptTransitions) {
        for (int j : acceptTransitions) {
          initialAcceptNodes.add(i * tCount + j);
        }
      }
      Set<Integer> canReachAccept = Walker.inGraph((Integer u) -> revAdj[u].stream())
          .preOrderFrom(initialAcceptNodes)
          .collect(toSet());

      boolean[] active = new boolean[vCount];
      List<Integer> activeNodes = new ArrayList<>();
      for (int i = 0; i < vCount; i++) {
        active[i] = reachableFromStart.contains(i) && canReachAccept.contains(i);
        if (active[i]) {
          activeNodes.add(i);
        }
      }

      List<List<Integer>> sccs = Walker.inGraph(
              (Integer u) -> adj[u].stream().filter(v -> active[v]))
          .stronglyConnectedComponentsFrom(activeNodes)
          .collect(toList());
      int[] sccMap = new int[vCount];
      Arrays.fill(sccMap, -1);
      boolean[] inCycle = new boolean[vCount];
      for (int id = 0; id < sccs.size(); id++) {
        List<Integer> scc = sccs.get(id);
        boolean isCycle =
            scc.size() > 1 || (scc.size() == 1 && adj[scc.get(0)].contains(scc.get(0)));
        for (int node : scc) {
          sccMap[node] = id;
          inCycle[node] = isCycle;
        }
      }
      return new ProductGraph(nfa, tCount, adj, active, sccs, sccMap, inCycle);
    }

    private ProductGraph(
        Nfa nfa,
        int tCount,
        List<Integer>[] adj,
        boolean[] active,
        List<List<Integer>> sccs,
        int[] sccMap,
        boolean[] inCycle) {
      this.nfa = nfa;
      this.tCount = tCount;
      this.adj = adj;
      this.active = active;
      this.sccs = sccs;
      this.sccMap = sccMap;
      this.inCycle = inCycle;
    }
  }

  private ReDos() {}
}

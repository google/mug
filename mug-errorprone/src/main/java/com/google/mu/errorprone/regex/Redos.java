package com.google.mu.errorprone.regex;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.Walker;

/**
 * Static analyzer for detecting Exponential Degree of Ambiguity (EDA) / Catastrophic Backtracking
 * (ReDoS) and Polynomial Degree of Ambiguity (PDA) vulnerabilities in {@link RegexPattern} ASTs.
 */
public final class Redos {

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to exponential backtracking
   * (ReDoS).
   *
   * @throws IllegalArgumentException if the pattern contains an exponential backtracking
   *     vulnerability
   */
  public static void checkRedosVulnerability(RegexPattern pattern) {
    requireNonNull(pattern);
    Optional<RegexPattern> nullable = findNullableRepeatedElement(pattern);
    if (nullable.isPresent()) {
      String sample = sampleMatchingString(nullable.get());
      String payload = attackPayload(sample);
      String suggestion =
          suggestRedosRewrite(pattern).map(s -> " (suggested rewrite: '" + s + "')").orElse("");
      throw new IllegalArgumentException(
          "Regular expression is vulnerable to exponential backtracking (ReDoS): '" + pattern
              + "' contains unbounded repetition of nullable sub-pattern '" + nullable.get()
              + "' (attack payload: \"" + payload + "\")" + suggestion);
    }
    Nfa nfa = Nfa.from(pattern);
    if (hasExponentialAmbiguity(ProductGraph.from(nfa))) {
      String detail = findStructuralDetail(pattern)
          .orElse("contains ambiguous cycle across overlapping transitions");
      String sample = sampleMatchingString(pattern);
      String payload = attackPayload(sample);
      String suggestion =
          suggestRedosRewrite(pattern).map(s -> " (suggested rewrite: '" + s + "')").orElse("");
      throw new IllegalArgumentException(
          "Regular expression is vulnerable to exponential backtracking (ReDoS): '" + pattern + "' "
              + detail + " (attack payload: \"" + payload + "\")" + suggestion);
    }
  }

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to polynomial backtracking (PDA).
   *
   * @throws IllegalArgumentException if the pattern contains polynomial degree of ambiguity (e.g.
   *     consecutive overlapping quantifiers)
   */
  public static void checkPolynomialBacktracking(RegexPattern pattern) {
    Objects.requireNonNull(pattern);
    Optional<String> detail = findPolynomialDetail(pattern);
    Nfa nfa = Nfa.from(pattern);
    if (detail.isPresent() || hasPolynomialAmbiguity(ProductGraph.from(nfa))) {
      String desc = detail.orElse("contains overlapping consecutive cycles");
      String sample = sampleMatchingString(pattern);
      String payload = attackPayload(sample);
      String suggestion = suggestPolynomialRewrite(pattern)
          .map(s -> " (suggested rewrite: '" + s + "')")
          .orElse("");
      throw new IllegalArgumentException(
          "Regular expression is vulnerable to polynomial backtracking (PDA): '" + pattern + "' "
              + desc + " (attack payload: \"" + payload + "\")" + suggestion);
    }
  }

  /**
   * Suggests a safe rewrite for an exponential ReDoS vulnerable pattern if a high-confidence fix is
   * known.
   */
  public static Optional<String> suggestRedosRewrite(RegexPattern pattern) {
    Objects.requireNonNull(pattern);
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified innerQ) {
        boolean canBeEmpty = innerQ.metadata().minSize() == 0 || q.metadata().minSize() == 0;
        String op = canBeEmpty ? "*" : "+";
        return Optional.of(innerQ.element().toString() + op);
      }
      if (inner.metadata().minSize() == 0 && inner.metadata().maxSize() > 0) {
        return Optional.of(inner.toString() + "*");
      }
    }
    return Optional.empty();
  }

  /**
   * Suggests a safe rewrite for a polynomial backtracking vulnerable pattern if a high-confidence
   * fix is known (e.g. using possessive quantifier).
   */
  public static Optional<String> suggestPolynomialRewrite(RegexPattern pattern) {
    Objects.requireNonNull(pattern);
    if (pattern instanceof RegexPattern.Sequence seq) {
      return findOverlappingQuantifiers(seq)
          .map(p -> {
            RegexPattern rewrittenFirst = new RegexPattern.Quantified(
                p.first().element(), p.first().quantifier().possessive());
            List<RegexPattern> rewritten = new ArrayList<>(seq.elements());
            rewritten.set(p.firstIndex(), rewrittenFirst);
            return new RegexPattern.Sequence(rewritten).toString();
          })
          .findFirst();
    }
    return Optional.empty();
  }

  private static String attackPayload(String sample) {
    int repetitions = Math.max(1, 30 / Math.max(1, sample.length()));
    return sample.repeat(repetitions) + "!";
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
    return Walker.inTree(Redos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Sequence.class::isInstance)
        .map(RegexPattern.Sequence.class::cast)
        .flatMap(Redos::findOverlappingQuantifiers)
        .map(pair ->
            "contains consecutive overlapping quantifiers on '" + pair.first() + "' and '"
                + pair.second() + "'")
        .findFirst();
  }

  private record OverlappingQuantifierPair(
      int firstIndex, RegexPattern.Quantified first, RegexPattern.Quantified second) {}

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
                      i, (RegexPattern.Quantified) ei, (RegexPattern.Quantified) ej));
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
        yield val.isEmpty() ? CharRanges.any() : CharRanges.of(val.charAt(0));
      }
      default -> CharRanges.any();
    };
  }

  private static Optional<RegexPattern> findNullableRepeatedElement(RegexPattern pattern) {
    return Walker.inTree(Redos::childrenOf)
        .preOrderFrom(pattern)
        .filter(Redos::isUnboundedNullable)
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
    return Walker.inTree(Redos::childrenOf)
        .preOrderFrom(pattern)
        .filter(RegexPattern.Quantified.class::isInstance)
        .map(RegexPattern.Quantified.class::cast)
        .flatMap(Redos::structuralDetailOf)
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
    return Walker.inTree(Redos::childrenOf)
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
      int vCount = tCount * tCount;
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
        for (int j = 0; j < tCount; j++) {
          Nfa.CharTransition tj = nfa.charTransitions.get(j);
          int u = i * tCount + j;
          for (int ip = 0; ip < tCount; ip++) {
            Nfa.CharTransition tip = nfa.charTransitions.get(ip);
            if (nfa.countEpsilonPaths(ti.target(), tip.source()) == 0) {
              continue;
            }
            for (int jp = 0; jp < tCount; jp++) {
              Nfa.CharTransition tjp = nfa.charTransitions.get(jp);
              if (nfa.countEpsilonPaths(tj.target(), tjp.source()) == 0) {
                continue;
              }
              if (!tip.chars().intersects(tjp.chars())) {
                continue;
              }
              int v = ip * tCount + jp;
              adj[u].add(v);
              revAdj[v].add(u);
            }
          }
        }
      }

      List<Integer> initialStartNodes = new ArrayList<>();
      for (int i = 0; i < tCount; i++) {
        Nfa.CharTransition ti = nfa.charTransitions.get(i);
        if (nfa.countEpsilonPaths(nfa.startState, ti.source()) == 0) {
          continue;
        }
        for (int j = 0; j < tCount; j++) {
          Nfa.CharTransition tj = nfa.charTransitions.get(j);
          if (nfa.countEpsilonPaths(nfa.startState, tj.source()) == 0) {
            continue;
          }
          if (ti.chars().intersects(tj.chars())) {
            initialStartNodes.add(i * tCount + j);
          }
        }
      }
      Set<Integer> reachableFromStart = Walker.inGraph((Integer u) -> adj[u].stream())
          .preOrderFrom(initialStartNodes)
          .collect(toSet());

      List<Integer> initialAcceptNodes = new ArrayList<>();
      for (int i = 0; i < tCount; i++) {
        Nfa.CharTransition ti = nfa.charTransitions.get(i);
        if (nfa.countEpsilonPaths(ti.target(), nfa.acceptState) == 0) {
          continue;
        }
        for (int j = 0; j < tCount; j++) {
          Nfa.CharTransition tj = nfa.charTransitions.get(j);
          if (nfa.countEpsilonPaths(tj.target(), nfa.acceptState) == 0) {
            continue;
          }
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

  private Redos() {}
}

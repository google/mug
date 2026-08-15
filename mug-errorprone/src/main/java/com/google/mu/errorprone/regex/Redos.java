package com.google.mu.errorprone.regex;

import com.google.common.base.Strings;
import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

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
    if (hasExponentialAmbiguity(nfa)) {
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
    Optional<String> detail = findPolynomialDetail(pattern);
    Nfa nfa = Nfa.from(pattern);
    if (detail.isPresent() || hasPolynomialAmbiguity(nfa)) {
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
    if (pattern instanceof RegexPattern.Quantified) {
      RegexPattern.Quantified q = (RegexPattern.Quantified) pattern;
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified) {
        RegexPattern.Quantified innerQ = (RegexPattern.Quantified) inner;
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
    if (pattern instanceof RegexPattern.Sequence) {
      RegexPattern.Sequence seq = (RegexPattern.Sequence) pattern;
      List<RegexPattern> elements = seq.elements();
      for (int i = 0; i < elements.size(); i++) {
        RegexPattern ei = elements.get(i);
        if (isUnboundedQuantified(ei)) {
          for (int j = i + 1; j < elements.size(); j++) {
            RegexPattern ej = elements.get(j);
            if (isUnboundedQuantified(ej)) {
              CharRanges rangesI = charRangesOf(ei);
              CharRanges rangesJ = charRangesOf(ej);
              if (rangesI.intersects(rangesJ)) {
                RegexPattern.Quantified qi = (RegexPattern.Quantified) ei;
                RegexPattern rewrittenEi =
                    new RegexPattern.Quantified(qi.element(), qi.quantifier().possessive());
                List<RegexPattern> rewrittenElements = new ArrayList<>(elements);
                rewrittenElements.set(i, rewrittenEi);
                return Optional.of(new RegexPattern.Sequence(rewrittenElements).toString());
              }
            }
            if (ej.metadata().minSize() > 0) {
              break;
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  @SuppressWarnings("InlineMeInliner")
  private static String attackPayload(String sample) {
    int repetitions = Math.max(1, 30 / Math.max(1, sample.length()));
    return Strings.repeat(sample, repetitions) + "!";
  }

  private static Optional<String> findPolynomialDetail(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Sequence) {
      RegexPattern.Sequence seq = (RegexPattern.Sequence) pattern;
      List<RegexPattern> elements = seq.elements();
      for (int i = 0; i < elements.size(); i++) {
        RegexPattern ei = elements.get(i);
        if (isUnboundedQuantified(ei)) {
          for (int j = i + 1; j < elements.size(); j++) {
            RegexPattern ej = elements.get(j);
            if (isUnboundedQuantified(ej)) {
              CharRanges rangesI = charRangesOf(ei);
              CharRanges rangesJ = charRangesOf(ej);
              if (rangesI.intersects(rangesJ)) {
                return Optional.of(
                    "contains consecutive overlapping quantifiers on '" + ei + "' and '" + ej
                        + "'");
              }
            }
            if (ej.metadata().minSize() > 0) {
              break;
            }
          }
        }
      }
      for (RegexPattern elem : elements) {
        Optional<String> detail = findPolynomialDetail(elem);
        if (detail.isPresent()) {
          return detail;
        }
      }
    }
    if (pattern instanceof RegexPattern.Alternation) {
      for (RegexPattern alt : ((RegexPattern.Alternation) pattern).alternatives()) {
        Optional<String> detail = findPolynomialDetail(alt);
        if (detail.isPresent()) {
          return detail;
        }
      }
    }
    if (pattern instanceof RegexPattern.Group) {
      return findPolynomialDetail(((RegexPattern.Group) pattern).content());
    }
    if (pattern instanceof RegexPattern.Quantified) {
      return findPolynomialDetail(((RegexPattern.Quantified) pattern).element());
    }
    return Optional.empty();
  }

  private static boolean isUnboundedQuantified(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified) {
      RegexPattern.Quantified q = (RegexPattern.Quantified) pattern;
      RegexPattern.Quantifier quantifier = q.quantifier();
      if (quantifier.isPossessive()) {
        return false;
      }
      return (quantifier instanceof RegexPattern.AtLeast
              && ((RegexPattern.AtLeast) quantifier).min() >= 0)
          || (quantifier instanceof RegexPattern.Limited
              && ((RegexPattern.Limited) quantifier).max() > 5);
    }
    return false;
  }

  private static CharRanges charRangesOf(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified) {
      return charRangesOf(((RegexPattern.Quantified) pattern).element());
    }
    if (pattern instanceof RegexPattern.Group) {
      return charRangesOf(((RegexPattern.Group) pattern).content());
    }
    if (pattern instanceof RegexPattern.CharacterSet) {
      return CharRanges.from((RegexPattern.CharacterSet) pattern);
    }
    if (pattern instanceof RegexPattern.PredefinedCharClass) {
      return CharRanges.from((RegexPattern.PredefinedCharClass) pattern);
    }
    if (pattern instanceof RegexPattern.PosixCharClass) {
      return CharRanges.from((RegexPattern.PosixCharClass) pattern);
    }
    if (pattern instanceof RegexPattern.Literal) {
      String val = ((RegexPattern.Literal) pattern).value();
      return val.isEmpty() ? CharRanges.any() : CharRanges.of(val.charAt(0));
    }
    return CharRanges.any();
  }

  private static Optional<RegexPattern> findNullableRepeatedElement(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified) {
      RegexPattern.Quantified q = (RegexPattern.Quantified) pattern;
      RegexPattern.Quantifier quantifier = q.quantifier();
      if (!quantifier.isPossessive()) {
        boolean isUnbounded =
            (quantifier instanceof RegexPattern.AtLeast
                    && ((RegexPattern.AtLeast) quantifier).min() >= 0)
                || (quantifier instanceof RegexPattern.Limited
                    && ((RegexPattern.Limited) quantifier).max() > 10);
        if (isUnbounded && q.element().metadata().minSize() == 0
            && q.element().metadata().maxSize() > 0) {
          return Optional.of(q.element());
        }
      }
      return findNullableRepeatedElement(q.element());
    }
    if (pattern instanceof RegexPattern.Sequence) {
      RegexPattern.Sequence seq = (RegexPattern.Sequence) pattern;
      for (RegexPattern elem : seq.elements()) {
        Optional<RegexPattern> result = findNullableRepeatedElement(elem);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Alternation) {
      RegexPattern.Alternation alt = (RegexPattern.Alternation) pattern;
      for (RegexPattern altElement : alt.alternatives()) {
        Optional<RegexPattern> result = findNullableRepeatedElement(altElement);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Group) {
      return findNullableRepeatedElement(((RegexPattern.Group) pattern).content());
    }
    return Optional.empty();
  }

  private static Optional<String> findStructuralDetail(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified) {
      RegexPattern.Quantified q = (RegexPattern.Quantified) pattern;
      RegexPattern inner = unwrapGroup(q.element());
      if (inner instanceof RegexPattern.Quantified) {
        return Optional.of("contains nested quantifiers on '" + inner + "'");
      }
      if (inner instanceof RegexPattern.Alternation) {
        Optional<RegexPattern.Quantified> nestedQuantified = findNestedQuantified(inner);
        if (nestedQuantified.isPresent()) {
          return Optional.of("contains nested quantifiers on '" + nestedQuantified.get() + "'");
        }
        return Optional.of("contains overlapping alternation branches '" + inner + "'");
      }
      if (inner instanceof RegexPattern.Sequence) {
        Optional<RegexPattern.Quantified> nestedQuantified = findNestedQuantified(inner);
        if (nestedQuantified.isPresent()) {
          return Optional.of("contains nested quantifiers on '" + nestedQuantified.get() + "'");
        }
      }
      return findStructuralDetail(q.element());
    }
    if (pattern instanceof RegexPattern.Sequence) {
      for (RegexPattern elem : ((RegexPattern.Sequence) pattern).elements()) {
        Optional<String> result = findStructuralDetail(elem);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Alternation) {
      for (RegexPattern altElement : ((RegexPattern.Alternation) pattern).alternatives()) {
        Optional<String> result = findStructuralDetail(altElement);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Group) {
      return findStructuralDetail(((RegexPattern.Group) pattern).content());
    }
    return Optional.empty();
  }

  private static RegexPattern unwrapGroup(RegexPattern pattern) {
    while (pattern instanceof RegexPattern.Group) {
      pattern = ((RegexPattern.Group) pattern).content();
    }
    return pattern;
  }

  private static Optional<RegexPattern.Quantified> findNestedQuantified(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified) {
      return Optional.of((RegexPattern.Quantified) pattern);
    }
    if (pattern instanceof RegexPattern.Sequence) {
      for (RegexPattern elem : ((RegexPattern.Sequence) pattern).elements()) {
        Optional<RegexPattern.Quantified> result = findNestedQuantified(elem);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Alternation) {
      for (RegexPattern altElement : ((RegexPattern.Alternation) pattern).alternatives()) {
        Optional<RegexPattern.Quantified> result = findNestedQuantified(altElement);
        if (result.isPresent()) {
          return result;
        }
      }
      return Optional.empty();
    }
    if (pattern instanceof RegexPattern.Group) {
      return findNestedQuantified(((RegexPattern.Group) pattern).content());
    }
    return Optional.empty();
  }

  private static String sampleMatchingString(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Literal) {
      String val = ((RegexPattern.Literal) pattern).value();
      return val.isEmpty() ? "a" : val;
    }
    if (pattern instanceof RegexPattern.CharacterSet) {
      CharRanges ranges = CharRanges.from((RegexPattern.CharacterSet) pattern);
      if (!ranges.isEmpty()) {
        return String.valueOf((char) ranges.ranges().get(0).start);
      }
      return "a";
    }
    if (pattern instanceof RegexPattern.PredefinedCharClass) {
      CharRanges ranges = CharRanges.from((RegexPattern.PredefinedCharClass) pattern);
      if (!ranges.isEmpty()) {
        return String.valueOf((char) ranges.ranges().get(0).start);
      }
      return "a";
    }
    if (pattern instanceof RegexPattern.PosixCharClass) {
      CharRanges ranges = CharRanges.from((RegexPattern.PosixCharClass) pattern);
      if (!ranges.isEmpty()) {
        return String.valueOf((char) ranges.ranges().get(0).start);
      }
      return "a";
    }
    if (pattern instanceof RegexPattern.Quantified) {
      return sampleMatchingString(((RegexPattern.Quantified) pattern).element());
    }
    if (pattern instanceof RegexPattern.Group) {
      return sampleMatchingString(((RegexPattern.Group) pattern).content());
    }
    if (pattern instanceof RegexPattern.Sequence) {
      StringBuilder sb = new StringBuilder();
      for (RegexPattern elem : ((RegexPattern.Sequence) pattern).elements()) {
        sb.append(sampleMatchingString(elem));
      }
      return sb.length() == 0 ? "a" : sb.toString();
    }
    if (pattern instanceof RegexPattern.Alternation) {
      List<RegexPattern> alts = ((RegexPattern.Alternation) pattern).alternatives();
      return alts.isEmpty() ? "a" : sampleMatchingString(alts.get(0));
    }
    return "a";
  }

  private static boolean hasExponentialAmbiguity(Nfa nfa) {
    int tCount = nfa.charTransitions.size();
    if (tCount == 0) {
      return false;
    }
    int vCount = tCount * tCount;

    @SuppressWarnings("unchecked")
    List<Integer>[] adj = new List[vCount];
    @SuppressWarnings("unchecked")
    List<Integer>[] revAdj = new List[vCount];
    for (int i = 0; i < vCount; i++) {
      adj[i] = new ArrayList<>();
      revAdj[i] = new ArrayList<>();
    }

    // Build paired transition product graph
    for (int i = 0; i < tCount; i++) {
      Nfa.CharTransition ti = nfa.charTransitions.get(i);
      for (int j = 0; j < tCount; j++) {
        Nfa.CharTransition tj = nfa.charTransitions.get(j);
        int u = i * tCount + j;
        for (int ip = 0; ip < tCount; ip++) {
          Nfa.CharTransition tip = nfa.charTransitions.get(ip);
          if (nfa.countEpsilonPaths(ti.target, tip.source) == 0) {
            continue;
          }
          for (int jp = 0; jp < tCount; jp++) {
            Nfa.CharTransition tjp = nfa.charTransitions.get(jp);
            if (nfa.countEpsilonPaths(tj.target, tjp.source) == 0) {
              continue;
            }
            if (!tip.chars.intersects(tjp.chars)) {
              continue;
            }
            int v = ip * tCount + jp;
            adj[u].add(v);
            revAdj[v].add(u);
          }
        }
      }
    }

    // Find nodes reachable from start
    boolean[] reachableFromStart = new boolean[vCount];
    Queue<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < tCount; i++) {
      Nfa.CharTransition ti = nfa.charTransitions.get(i);
      if (nfa.countEpsilonPaths(nfa.startState, ti.source) == 0) {
        continue;
      }
      for (int j = 0; j < tCount; j++) {
        Nfa.CharTransition tj = nfa.charTransitions.get(j);
        if (nfa.countEpsilonPaths(nfa.startState, tj.source) == 0) {
          continue;
        }
        if (ti.chars.intersects(tj.chars)) {
          int u = i * tCount + j;
          reachableFromStart[u] = true;
          queue.add(u);
        }
      }
    }
    while (!queue.isEmpty()) {
      int u = queue.poll();
      for (int v : adj[u]) {
        if (!reachableFromStart[v]) {
          reachableFromStart[v] = true;
          queue.add(v);
        }
      }
    }

    // Find nodes that can reach accept
    boolean[] canReachAccept = new boolean[vCount];
    for (int i = 0; i < tCount; i++) {
      Nfa.CharTransition ti = nfa.charTransitions.get(i);
      if (nfa.countEpsilonPaths(ti.target, nfa.acceptState) == 0) {
        continue;
      }
      for (int j = 0; j < tCount; j++) {
        Nfa.CharTransition tj = nfa.charTransitions.get(j);
        if (nfa.countEpsilonPaths(tj.target, nfa.acceptState) == 0) {
          continue;
        }
        int u = i * tCount + j;
        canReachAccept[u] = true;
        queue.add(u);
      }
    }
    while (!queue.isEmpty()) {
      int u = queue.poll();
      for (int v : revAdj[u]) {
        if (!canReachAccept[v]) {
          canReachAccept[v] = true;
          queue.add(v);
        }
      }
    }

    // Filter active nodes
    boolean[] active = new boolean[vCount];
    for (int i = 0; i < vCount; i++) {
      active[i] = reachableFromStart[i] && canReachAccept[i];
    }

    // Detect ambiguous cycles
    for (int i = 0; i < tCount; i++) {
      for (int j = 0; j < tCount; j++) {
        int u = i * tCount + j;
        if (!active[u]) {
          continue;
        }
        if (i == j) {
          // Diagonal state: check if multiple distinct epsilon paths loop around
          Nfa.CharTransition ti = nfa.charTransitions.get(i);
          if (nfa.countEpsilonPaths(ti.target, ti.source) >= 2 && canReachSelf(u, adj, active)) {
            return true;
          }
        } else {
          // Off-diagonal state: check if in an EDA cycle with branching or diagonal connection
          Set<Integer> scc = getScc(u, adj, revAdj, active);
          if (!scc.isEmpty()) {
            for (int node : scc) {
              int row = node / tCount;
              int col = node % tCount;
              if (row == col) {
                return true;
              }
              int branchCount = 0;
              for (int next : adj[node]) {
                if (scc.contains(next)) {
                  branchCount++;
                }
              }
              if (branchCount >= 2) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean hasPolynomialAmbiguity(Nfa nfa) {
    int tCount = nfa.charTransitions.size();
    if (tCount < 2) {
      return false;
    }
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
          if (nfa.countEpsilonPaths(ti.target, tip.source) == 0) {
            continue;
          }
          for (int jp = 0; jp < tCount; jp++) {
            Nfa.CharTransition tjp = nfa.charTransitions.get(jp);
            if (nfa.countEpsilonPaths(tj.target, tjp.source) == 0) {
              continue;
            }
            if (!tip.chars.intersects(tjp.chars)) {
              continue;
            }
            int v = ip * tCount + jp;
            adj[u].add(v);
            revAdj[v].add(u);
          }
        }
      }
    }

    boolean[] reachableFromStart = new boolean[vCount];
    Queue<Integer> queue = new ArrayDeque<>();
    for (int i = 0; i < tCount; i++) {
      Nfa.CharTransition ti = nfa.charTransitions.get(i);
      if (nfa.countEpsilonPaths(nfa.startState, ti.source) == 0) {
        continue;
      }
      for (int j = 0; j < tCount; j++) {
        Nfa.CharTransition tj = nfa.charTransitions.get(j);
        if (nfa.countEpsilonPaths(nfa.startState, tj.source) == 0) {
          continue;
        }
        if (ti.chars.intersects(tj.chars)) {
          int u = i * tCount + j;
          reachableFromStart[u] = true;
          queue.add(u);
        }
      }
    }
    while (!queue.isEmpty()) {
      int u = queue.poll();
      for (int v : adj[u]) {
        if (!reachableFromStart[v]) {
          reachableFromStart[v] = true;
          queue.add(v);
        }
      }
    }

    boolean[] canReachAccept = new boolean[vCount];
    for (int i = 0; i < tCount; i++) {
      Nfa.CharTransition ti = nfa.charTransitions.get(i);
      if (nfa.countEpsilonPaths(ti.target, nfa.acceptState) == 0) {
        continue;
      }
      for (int j = 0; j < tCount; j++) {
        Nfa.CharTransition tj = nfa.charTransitions.get(j);
        if (nfa.countEpsilonPaths(tj.target, nfa.acceptState) == 0) {
          continue;
        }
        int u = i * tCount + j;
        canReachAccept[u] = true;
        queue.add(u);
      }
    }
    while (!queue.isEmpty()) {
      int u = queue.poll();
      for (int v : revAdj[u]) {
        if (!canReachAccept[v]) {
          canReachAccept[v] = true;
          queue.add(v);
        }
      }
    }

    boolean[] active = new boolean[vCount];
    for (int i = 0; i < vCount; i++) {
      active[i] = reachableFromStart[i] && canReachAccept[i];
    }

    List<DiagonalCycle> cycles = new ArrayList<>();
    for (int i = 0; i < tCount; i++) {
      int u = i * tCount + i;
      if (active[u] && canReachSelf(u, adj, active)) {
        Nfa.CharTransition ti = nfa.charTransitions.get(i);
        cycles.add(new DiagonalCycle(u, ti.chars));
      }
    }

    for (int a = 0; a < cycles.size(); a++) {
      DiagonalCycle ca = cycles.get(a);
      for (int b = 0; b < cycles.size(); b++) {
        if (a != b) {
          DiagonalCycle cb = cycles.get(b);
          if (ca.chars.intersects(cb.chars) && canReach(ca.state, cb.state, adj, active)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static final class DiagonalCycle {
    final int state;
    final CharRanges chars;

    DiagonalCycle(int state, CharRanges chars) {
      this.state = state;
      this.chars = chars;
    }
  }

  private static boolean canReach(int start, int target, List<Integer>[] adj, boolean[] active) {
    boolean[] visited = new boolean[adj.length];
    Queue<Integer> queue = new ArrayDeque<>();
    visited[start] = true;
    queue.add(start);
    while (!queue.isEmpty()) {
      int curr = queue.poll();
      for (int next : adj[curr]) {
        if (active[next]) {
          if (next == target) {
            return true;
          }
          if (!visited[next]) {
            visited[next] = true;
            queue.add(next);
          }
        }
      }
    }
    return false;
  }

  private static Set<Integer> getScc(
      int start, List<Integer>[] adj, List<Integer>[] revAdj, boolean[] active) {
    Set<Integer> forward = new HashSet<>();
    Queue<Integer> q = new ArrayDeque<>();
    forward.add(start);
    q.add(start);
    while (!q.isEmpty()) {
      int curr = q.poll();
      for (int next : adj[curr]) {
        if (active[next] && forward.add(next)) {
          q.add(next);
        }
      }
    }

    boolean canReturn = false;
    for (int prev : revAdj[start]) {
      if (forward.contains(prev)) {
        canReturn = true;
        break;
      }
    }
    if (!canReturn) {
      return Collections.emptySet();
    }

    Set<Integer> backward = new HashSet<>();
    backward.add(start);
    q.add(start);
    while (!q.isEmpty()) {
      int curr = q.poll();
      for (int prev : revAdj[curr]) {
        if (active[prev] && backward.add(prev)) {
          q.add(prev);
        }
      }
    }

    forward.retainAll(backward);
    return forward;
  }

  private static boolean canReachSelf(int start, List<Integer>[] adj, boolean[] active) {
    boolean[] visited = new boolean[adj.length];
    Queue<Integer> queue = new ArrayDeque<>();
    for (int next : adj[start]) {
      if (active[next]) {
        if (next == start) {
          return true;
        }
        visited[next] = true;
        queue.add(next);
      }
    }
    while (!queue.isEmpty()) {
      int curr = queue.poll();
      for (int next : adj[curr]) {
        if (active[next]) {
          if (next == start) {
            return true;
          }
          if (!visited[next]) {
            visited[next] = true;
            queue.add(next);
          }
        }
      }
    }
    return false;
  }

  private Redos() {}
}

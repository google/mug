package com.google.mu.errorprone.regex;

import static java.util.stream.Collectors.toSet;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.Walker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Non-deterministic Finite Automaton (NFA) constructed from {@link RegexPattern} AST using
 * Thompson's construction.
 */
final class Nfa {
  final List<State> states = new ArrayList<>();
  final List<CharTransition> charTransitions = new ArrayList<>();
  final Map<RegexPattern, Integer> nodeToStartState = new IdentityHashMap<>();
  int startState;
  int acceptState;

  static final class State {
    final int id;
    final List<Integer> epsilonTransitions = new ArrayList<>();

    State(int id) {
      this.id = id;
    }
  }

  record CharTransition(int id, int source, CharRanges chars, int target, RegexPattern astNode) {}

  private record Fragment(int start, int accept) {}

  State newState() {
    State s = new State(states.size());
    states.add(s);
    return s;
  }

  void addEpsilon(int from, int to) {
    states.get(from).epsilonTransitions.add(to);
  }

  void addCharTransition(int from, CharRanges chars, int to, RegexPattern astNode) {
    if (chars.isEmpty()) {
      return;
    }
    CharTransition t = new CharTransition(charTransitions.size(), from, chars, to, astNode);
    charTransitions.add(t);
  }

  static Nfa from(RegexPattern pattern) {
    Nfa nfa = new Nfa();
    Fragment fragment = nfa.compile(pattern);
    nfa.startState = fragment.start;
    nfa.acceptState = fragment.accept;
    return nfa;
  }

  OptionalInt startStateOf(RegexPattern node) {
    Integer s = nodeToStartState.get(node);
    return s == null ? OptionalInt.empty() : OptionalInt.of(s);
  }

  private Fragment compile(RegexPattern pattern) {
    Fragment f = switch (pattern) {
      case RegexPattern.Literal lit -> compileLiteral(lit);
      case RegexPattern.CharacterSet cs -> compileCharRanges(CharRanges.from(cs), cs);
      case RegexPattern.PredefinedCharClass pcc -> compileCharRanges(CharRanges.from(pcc), pcc);
      case RegexPattern.PosixCharClass pcc -> compileCharRanges(CharRanges.from(pcc), pcc);
      case RegexPattern.CharacterProperty.Negated neg ->
          compileCharRanges(CharRanges.from(neg), neg);
      case RegexPattern.UnicodeProperty up -> compileCharRanges(CharRanges.from(up), up);
      case RegexPattern.Sequence seq -> compileSequence(seq.elements());
      case RegexPattern.Alternation alt -> compileAlternation(alt.alternatives());
      case RegexPattern.Group group -> compile(group.content());
      case RegexPattern.Quantified q -> compileQuantified(q);
      default -> compileEmpty();
    };
    nodeToStartState.put(pattern, f.start);
    return f;
  }

  private Fragment compileEmpty() {
    State s = newState();
    return new Fragment(s.id, s.id);
  }

  private Fragment compileCharRanges(CharRanges ranges, RegexPattern pattern) {
    State start = newState();
    State accept = newState();
    addCharTransition(start.id, ranges, accept.id, pattern);
    return new Fragment(start.id, accept.id);
  }

  private Fragment compileLiteral(RegexPattern.Literal lit) {
    String s = lit.value();
    if (s.isEmpty()) {
      return compileEmpty();
    }
    State first = newState();
    State current = first;
    for (int i = 0; i < s.length(); i++) {
      State next = newState();
      addCharTransition(current.id, CharRanges.of(s.charAt(i)), next.id, lit);
      current = next;
    }
    return new Fragment(first.id, current.id);
  }

  private Fragment compileSequence(List<RegexPattern> elements) {
    if (elements.isEmpty()) {
      return compileEmpty();
    }
    List<Fragment> fragments = new ArrayList<>();
    for (RegexPattern elem : elements) {
      fragments.add(compile(elem));
    }
    for (int i = 0; i < fragments.size() - 1; i++) {
      addEpsilon(fragments.get(i).accept, fragments.get(i + 1).start);
    }
    return new Fragment(fragments.get(0).start, fragments.get(fragments.size() - 1).accept);
  }

  private Fragment compileAlternation(List<RegexPattern> alternatives) {
    if (alternatives.isEmpty()) {
      return compileEmpty();
    }
    State start = newState();
    State accept = newState();
    for (RegexPattern alt : alternatives) {
      Fragment f = compile(alt);
      addEpsilon(start.id, f.start);
      addEpsilon(f.accept, accept.id);
    }
    return new Fragment(start.id, accept.id);
  }

  private Fragment compileQuantified(RegexPattern.Quantified quantified) {
    RegexPattern.Quantifier q = quantified.quantifier();
    if (q.isPossessive()) {
      return compile(quantified.element());
    }

    return switch (q) {
      case RegexPattern.AtLeast atLeast -> {
        if (atLeast.min() == 0) {
          Fragment f = compile(quantified.element());
          State start = newState();
          State accept = newState();
          addEpsilon(start.id, f.start);
          addEpsilon(start.id, accept.id);
          addEpsilon(f.accept, f.start);
          addEpsilon(f.accept, accept.id);
          yield new Fragment(start.id, accept.id);
        } else if (atLeast.min() == 1) {
          Fragment f = compile(quantified.element());
          State start = newState();
          State accept = newState();
          addEpsilon(start.id, f.start);
          addEpsilon(f.accept, f.start);
          addEpsilon(f.accept, accept.id);
          yield new Fragment(start.id, accept.id);
        } else {
          List<Fragment> parts = new ArrayList<>();
          for (int i = 0; i < atLeast.min() - 1; i++) {
            parts.add(compile(quantified.element()));
          }
          Fragment loopPart = compileQuantified(
              new RegexPattern.Quantified(
                  quantified.element(), RegexPattern.Quantifier.atLeast(1)));
          parts.add(loopPart);
          for (int i = 0; i < parts.size() - 1; i++) {
            addEpsilon(parts.get(i).accept, parts.get(i + 1).start);
          }
          yield new Fragment(parts.get(0).start, parts.get(parts.size() - 1).accept);
        }
      }
      case RegexPattern.AtMost atMost -> {
        if (atMost.max() == 1) {
          Fragment f = compile(quantified.element());
          State start = newState();
          State accept = newState();
          addEpsilon(start.id, f.start);
          addEpsilon(start.id, accept.id);
          addEpsilon(f.accept, accept.id);
          yield new Fragment(start.id, accept.id);
        } else {
          State start = newState();
          State current = start;
          State accept = newState();
          int max = Math.min(atMost.max(), 5);
          for (int i = 0; i < max; i++) {
            Fragment f = compile(quantified.element());
            addEpsilon(current.id, f.start);
            addEpsilon(current.id, accept.id);
            addEpsilon(f.accept, accept.id);
            current = states.get(f.accept);
          }
          yield new Fragment(start.id, accept.id);
        }
      }
      case RegexPattern.Limited limited -> {
        List<Fragment> parts = new ArrayList<>();
        for (int i = 0; i < limited.min(); i++) {
          parts.add(compile(quantified.element()));
        }
        int extra = Math.min(limited.max() - limited.min(), 5);
        for (int i = 0; i < extra; i++) {
          Fragment opt = compile(quantified.element());
          State s = newState();
          State e = newState();
          addEpsilon(s.id, opt.start);
          addEpsilon(s.id, e.id);
          addEpsilon(opt.accept, e.id);
          parts.add(new Fragment(s.id, e.id));
        }
        for (int i = 0; i < parts.size() - 1; i++) {
          addEpsilon(parts.get(i).accept, parts.get(i + 1).start);
        }
        yield new Fragment(parts.get(0).start, parts.get(parts.size() - 1).accept);
      }
      default -> compile(quantified.element());
    };
  }

  int countEpsilonPaths(int from, int to) {
    boolean[] visited = new boolean[states.size()];
    return countEpsilonPaths(from, to, visited);
  }

  private int countEpsilonPaths(int current, int target, boolean[] visited) {
    if (current == target) {
      return 1;
    }
    visited[current] = true;
    int count = 0;
    for (int next : states.get(current).epsilonTransitions) {
      if (!visited[next]) {
        count += countEpsilonPaths(next, target, visited);
        if (count >= 2) {
          break;
        }
      }
    }
    visited[current] = false;
    return count;
  }

  Set<Integer> epsilonClosure(int state) {
    return Walker.inGraph((Integer s) -> states.get(s).epsilonTransitions.stream())
        .preOrderFrom(state)
        .collect(toSet());
  }

  List<CharTransition> reachableCharTransitions(int state) {
    Set<Integer> closure = epsilonClosure(state);
    return charTransitions.stream().filter(t -> closure.contains(t.source())).toList();
  }

  boolean canReachAccept(int state) {
    return epsilonClosure(state).contains(acceptState);
  }

  String shortestPathToString(int from, int to) {
    if (from == to) {
      return "";
    }
    int numStates = states.size();
    int[] dist = new int[numStates];
    Arrays.fill(dist, Integer.MAX_VALUE);
    int[] prev = new int[numStates];
    Arrays.fill(prev, -1);
    char[] prevChar = new char[numStates];

    List<List<CharTransition>> outgoingChar = new ArrayList<>(numStates);
    for (int i = 0; i < numStates; i++) {
      outgoingChar.add(new ArrayList<>());
    }
    for (CharTransition t : charTransitions) {
      outgoingChar.get(t.source()).add(t);
    }

    Deque<Integer> deque = new ArrayDeque<>();
    dist[from] = 0;
    deque.add(from);

    while (!deque.isEmpty()) {
      int u = deque.pollFirst();
      if (u == to) {
        break;
      }
      for (int v : states.get(u).epsilonTransitions) {
        if (dist[u] < dist[v]) {
          dist[v] = dist[u];
          prev[v] = u;
          prevChar[v] = 0;
          deque.addFirst(v);
        }
      }
      for (CharTransition t : outgoingChar.get(u)) {
        int v = t.target();
        if (dist[u] + 1 < dist[v]) {
          dist[v] = dist[u] + 1;
          prev[v] = u;
          prevChar[v] = (char) t.chars().sampleChar();
          deque.addLast(v);
        }
      }
    }

    if (dist[to] == Integer.MAX_VALUE) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    int curr = to;
    while (curr != from && curr != -1) {
      char c = prevChar[curr];
      if (c != 0) {
        sb.append(c);
      }
      curr = prev[curr];
    }
    return sb.reverse().toString();
  }
}

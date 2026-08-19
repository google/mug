package com.google.mu.errorprone.regex;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.ShortestPath;
import com.google.mu.util.graph.Walker;
import com.google.mu.util.stream.BiStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Non-deterministic Finite Automaton (NFA) constructed from {@link RegexPattern} AST using
 * Thompson's construction.
 */
final class Nfa {
  final List<State> states = new ArrayList<>();
  final List<CharTransition> charTransitions = new ArrayList<>();
  final Set<Integer> anchorStates = new HashSet<>();
  private final Map<RegexPattern, Integer> nodeToStartState = new IdentityHashMap<>();
  private final Deque<RegexPattern.Quantified> quantifierStack = new ArrayDeque<>();
  int startState;
  int acceptState;

  static final class State {
    final int id;
    final List<Integer> epsilonTransitions = new ArrayList<>();

    State(int id) {
      this.id = id;
    }
  }

  record CharTransition(
      int id,
      int source,
      ImmutableRangeSet<Integer> chars,
      int target,
      RegexPattern astNode,
      List<RegexPattern.Quantified> enclosingQuantifiers) {}

  private record Fragment(int start, int accept) {}

  State newState() {
    State s = new State(states.size());
    states.add(s);
    return s;
  }

  void addEpsilon(int from, int to) {
    states.get(from).epsilonTransitions.add(to);
  }

  private void addCharTransition(
      int from, ImmutableRangeSet<Integer> chars, int to, RegexPattern astNode) {
    if (chars.isEmpty()) {
      return;
    }
    CharTransition t = new CharTransition(
        charTransitions.size(), from, chars, to, astNode, List.copyOf(quantifierStack));
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
      case RegexPattern.Anchor anchor -> compileAnchor();
      default -> compileEmpty();
    };
    nodeToStartState.put(pattern, f.start);
    return f;
  }

  private Fragment compileAnchor() {
    State s = newState();
    anchorStates.add(s.id);
    return new Fragment(s.id, s.id);
  }

  private Fragment compileEmpty() {
    State s = newState();
    return new Fragment(s.id, s.id);
  }

  private Fragment compileCharRanges(ImmutableRangeSet<Integer> ranges, RegexPattern pattern) {
    State start = newState();
    State accept = newState();
    addCharTransition(start.id, ranges, accept.id, pattern);
    return new Fragment(start.id, accept.id);
  }

  private Fragment compileLiteral(RegexPattern.Literal lit) {
    String s = lit.value();
    State first = newState();
    State current = first;
    int[] codePoints = s.codePoints().toArray();
    for (int cp : codePoints) {
      State next = newState();
      addCharTransition(current.id, CharRanges.of(cp), next.id, lit);
      current = next;
    }
    return new Fragment(first.id, current.id);
  }

  private Fragment compileSequence(List<RegexPattern> elements) {
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
    State start = newState();
    State accept = newState();
    for (RegexPattern alt : alternatives) {
      Fragment f = compile(alt);
      addEpsilon(start.id, f.start);
      addEpsilon(f.accept, accept.id);
    }
    return new Fragment(start.id, accept.id);
  }

  private static final int MAX_UNROLL = 5;

  private Fragment compileQuantified(RegexPattern.Quantified quantified) {
    quantifierStack.addLast(quantified);
    try {
      RegexPattern.Quantifier q = quantified.quantifier();
      return switch (q) {
        case RegexPattern.AtLeast atLeast -> {
          int min = Math.min(atLeast.min(), MAX_UNROLL);
          if (min == 0) {
            Fragment f = compile(quantified.element());
            State start = newState();
            State accept = newState();
            addEpsilon(start.id, f.start);
            addEpsilon(start.id, accept.id);
            addEpsilon(f.accept, f.start);
            addEpsilon(f.accept, accept.id);
            yield new Fragment(start.id, accept.id);
          } else if (min == 1) {
            Fragment f = compile(quantified.element());
            State start = newState();
            State accept = newState();
            addEpsilon(start.id, f.start);
            addEpsilon(f.accept, f.start);
            addEpsilon(f.accept, accept.id);
            yield new Fragment(start.id, accept.id);
          } else {
            List<Fragment> parts = new ArrayList<>();
            for (int i = 0; i < min - 1; i++) {
              parts.add(compile(quantified.element()));
            }
            Fragment f = compile(quantified.element());
            State start = newState();
            State accept = newState();
            addEpsilon(start.id, f.start);
            addEpsilon(f.accept, f.start);
            addEpsilon(f.accept, accept.id);
            parts.add(new Fragment(start.id, accept.id));
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
            int max = Math.min(atMost.max(), MAX_UNROLL);
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
          int min = Math.min(limited.min(), MAX_UNROLL);
          for (int i = 0; i < min; i++) {
            parts.add(compile(quantified.element()));
          }
          int extra = Math.min(limited.max() - limited.min(), MAX_UNROLL);
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
          yield parts.isEmpty()
              ? compileEmpty()
              : new Fragment(parts.get(0).start, parts.get(parts.size() - 1).accept);
        }
        default -> compile(quantified.element());
      };
    } finally {
      quantifierStack.removeLast();
    }
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

  private Set<Integer> epsilonClosure(int state) {
    return Walker.inGraph((Integer s) -> states.get(s).epsilonTransitions.stream())
        .preOrderFrom(state)
        .collect(toSet());
  }

  List<CharTransition> reachableCharTransitions(int state) {
    Set<Integer> closure = epsilonClosure(state);
    return charTransitions.stream().filter(t -> closure.contains(t.source())).toList();
  }

  Set<Integer> epsilonClosureWithoutAnchors(int state) {
    return Walker.inGraph((Integer s) ->
            anchorStates.contains(s) && s != state
                ? Stream.<Integer>empty()
                : states.get(s).epsilonTransitions.stream())
        .preOrderFrom(state)
        .collect(toSet());
  }

  boolean canReachWithoutAnchors(int fromState, int toState) {
    return Walker.inGraph((Integer s) -> {
      if (anchorStates.contains(s) && s != fromState) {
        return Stream.<Integer>empty();
      }
      Stream.Builder<Integer> builder = Stream.builder();
      for (int next : states.get(s).epsilonTransitions) {
        builder.add(next);
      }
      for (CharTransition t : charTransitions) {
        if (t.source() == s) {
          builder.add(t.target());
        }
      }
      return builder.build();
    })
        .preOrderFrom(fromState)
        .anyMatch(s -> s == toState);
  }

  List<CharTransition> reachableCharTransitionsWithoutAnchors(int state) {
    Set<Integer> closure = epsilonClosureWithoutAnchors(state);
    return charTransitions.stream().filter(t -> closure.contains(t.source())).toList();
  }

  boolean canReachAccept(int state) {
    return epsilonClosure(state).contains(acceptState);
  }

  String shortestPathToString(int from, int to) {
    if (from == to) {
      return "";
    }
    record Step(int state, char charConsumed) {}

    return ShortestPath.shortestPathsFrom(
            new Step(from, '\0'),
            (Step step) -> {
              BiStream.Builder<Step, Double> builder = BiStream.builder();
              for (int next : states.get(step.state()).epsilonTransitions) {
                builder.add(new Step(next, '\0'), 0.0);
              }
              for (CharTransition t : charTransitions) {
                if (t.source() == step.state()) {
                  builder.add(new Step(t.target(), (char) CharRanges.sampleChar(t.chars())), 1.0);
                }
              }
              return builder.build();
            })
        .filter(path -> path.to().state() == to)
        .findFirst()
        .map(path -> path.stream()
            .keys()
            .map(Step::charConsumed)
            .filter(c -> c != '\0')
            .map(Object::toString)
            .collect(joining()))
        .orElse("");
  }
}

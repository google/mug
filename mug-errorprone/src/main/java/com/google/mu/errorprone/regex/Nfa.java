package com.google.mu.errorprone.regex;

import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-deterministic Finite Automaton (NFA) constructed from {@link RegexPattern} AST using
 * Thompson's construction.
 */
final class Nfa {
  final List<State> states = new ArrayList<>();
  final List<CharTransition> charTransitions = new ArrayList<>();
  int startState;
  int acceptState;

  static final class State {
    final int id;
    final List<Integer> epsilonTransitions = new ArrayList<>();

    State(int id) {
      this.id = id;
    }
  }

  record CharTransition(int id, int source, CharRanges chars, int target) {}

  private record Fragment(int start, int accept) {}

  State newState() {
    State s = new State(states.size());
    states.add(s);
    return s;
  }

  void addEpsilon(int from, int to) {
    states.get(from).epsilonTransitions.add(to);
  }

  void addCharTransition(int from, CharRanges chars, int to) {
    if (chars.isEmpty()) {
      return;
    }
    CharTransition t = new CharTransition(charTransitions.size(), from, chars, to);
    charTransitions.add(t);
  }

  static Nfa from(RegexPattern pattern) {
    Nfa nfa = new Nfa();
    Fragment fragment = nfa.compile(pattern);
    nfa.startState = fragment.start;
    nfa.acceptState = fragment.accept;
    return nfa;
  }

  private Fragment compile(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Literal lit -> compileLiteral(lit.value());
      case RegexPattern.CharacterSet cs -> compileCharRanges(CharRanges.from(cs));
      case RegexPattern.PredefinedCharClass pcc -> compileCharRanges(CharRanges.from(pcc));
      case RegexPattern.PosixCharClass pcc -> compileCharRanges(CharRanges.from(pcc));
      case RegexPattern.CharacterProperty.Negated neg -> compileCharRanges(CharRanges.from(neg));
      case RegexPattern.UnicodeProperty up -> compileCharRanges(CharRanges.from(up));
      case RegexPattern.Sequence seq -> compileSequence(seq.elements());
      case RegexPattern.Alternation alt -> compileAlternation(alt.alternatives());
      case RegexPattern.Group group -> compile(group.content());
      case RegexPattern.Quantified q -> compileQuantified(q);
      default -> compileEmpty();
    };
  }

  private Fragment compileEmpty() {
    State s = newState();
    return new Fragment(s.id, s.id);
  }

  private Fragment compileCharRanges(CharRanges ranges) {
    State start = newState();
    State accept = newState();
    addCharTransition(start.id, ranges, accept.id);
    return new Fragment(start.id, accept.id);
  }

  private Fragment compileLiteral(String s) {
    if (s.isEmpty()) {
      return compileEmpty();
    }
    State first = newState();
    State current = first;
    for (int i = 0; i < s.length(); i++) {
      State next = newState();
      addCharTransition(current.id, CharRanges.of(s.charAt(i)), next.id);
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
}

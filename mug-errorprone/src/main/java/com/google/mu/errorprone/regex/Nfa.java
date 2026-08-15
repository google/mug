package com.google.mu.errorprone.regex;

import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

  static final class CharTransition {
    final int id;
    final int source;
    final CharRanges chars;
    final int target;

    CharTransition(int id, int source, CharRanges chars, int target) {
      this.id = id;
      this.source = source;
      this.chars = chars;
      this.target = target;
    }

    public int id() {
      return id;
    }

    public int source() {
      return source;
    }

    public CharRanges chars() {
      return chars;
    }

    public int target() {
      return target;
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof CharTransition) {
        CharTransition other = (CharTransition) obj;
        return this.id == other.id && this.source == other.source
            && Objects.equals(this.chars, other.chars) && this.target == other.target;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hash(id, source, chars, target);
    }
  }

  private static final class Fragment {
    final int start;
    final int accept;

    Fragment(int start, int accept) {
      this.start = start;
      this.accept = accept;
    }

    int start() {
      return start;
    }

    int accept() {
      return accept;
    }
  }

  Nfa() {}

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

  public static Nfa from(RegexPattern pattern) {
    Nfa nfa = new Nfa();
    Fragment fragment = nfa.compile(pattern);
    nfa.startState = fragment.start();
    nfa.acceptState = fragment.accept();
    return nfa;
  }

  private Fragment compile(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Literal) {
      return compileLiteral(((RegexPattern.Literal) pattern).value());
    }
    if (pattern instanceof RegexPattern.CharacterSet) {
      return compileCharRanges(CharRanges.from((RegexPattern.CharacterSet) pattern));
    }
    if (pattern instanceof RegexPattern.PredefinedCharClass) {
      return compileCharRanges(CharRanges.from((RegexPattern.PredefinedCharClass) pattern));
    }
    if (pattern instanceof RegexPattern.PosixCharClass) {
      return compileCharRanges(CharRanges.from((RegexPattern.PosixCharClass) pattern));
    }
    if (pattern instanceof RegexPattern.CharacterProperty.Negated) {
      return compileCharRanges(CharRanges.from((RegexPattern.CharacterProperty.Negated) pattern));
    }
    if (pattern instanceof RegexPattern.UnicodeProperty) {
      return compileCharRanges(CharRanges.from((RegexPattern.UnicodeProperty) pattern));
    }
    if (pattern instanceof RegexPattern.Sequence) {
      return compileSequence(((RegexPattern.Sequence) pattern).elements());
    }
    if (pattern instanceof RegexPattern.Alternation) {
      return compileAlternation(((RegexPattern.Alternation) pattern).alternatives());
    }
    if (pattern instanceof RegexPattern.Group) {
      return compile(((RegexPattern.Group) pattern).content());
    }
    if (pattern instanceof RegexPattern.Quantified) {
      return compileQuantified((RegexPattern.Quantified) pattern);
    }
    return compileEmpty();
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
      addEpsilon(fragments.get(i).accept(), fragments.get(i + 1).start());
    }
    return new Fragment(fragments.get(0).start(), fragments.get(fragments.size() - 1).accept());
  }

  private Fragment compileAlternation(List<RegexPattern> alternatives) {
    if (alternatives.isEmpty()) {
      return compileEmpty();
    }
    State start = newState();
    State accept = newState();
    for (RegexPattern alt : alternatives) {
      Fragment f = compile(alt);
      addEpsilon(start.id, f.start());
      addEpsilon(f.accept(), accept.id);
    }
    return new Fragment(start.id, accept.id);
  }

  private Fragment compileQuantified(RegexPattern.Quantified quantified) {
    RegexPattern.Quantifier q = quantified.quantifier();
    if (q.isPossessive()) {
      return compile(quantified.element());
    }

    if (q instanceof RegexPattern.AtLeast) {
      RegexPattern.AtLeast atLeast = (RegexPattern.AtLeast) q;
      if (atLeast.min() == 0) {
        Fragment f = compile(quantified.element());
        State start = newState();
        State accept = newState();
        addEpsilon(start.id, f.start());
        addEpsilon(start.id, accept.id);
        addEpsilon(f.accept(), f.start());
        addEpsilon(f.accept(), accept.id);
        return new Fragment(start.id, accept.id);
      } else if (atLeast.min() == 1) {
        Fragment f = compile(quantified.element());
        State start = newState();
        State accept = newState();
        addEpsilon(start.id, f.start());
        addEpsilon(f.accept(), f.start());
        addEpsilon(f.accept(), accept.id);
        return new Fragment(start.id, accept.id);
      } else {
        List<Fragment> parts = new ArrayList<>();
        for (int i = 0; i < atLeast.min() - 1; i++) {
          parts.add(compile(quantified.element()));
        }
        Fragment loopPart = compileQuantified(
            new RegexPattern.Quantified(quantified.element(), RegexPattern.Quantifier.atLeast(1)));
        parts.add(loopPart);
        for (int i = 0; i < parts.size() - 1; i++) {
          addEpsilon(parts.get(i).accept(), parts.get(i + 1).start());
        }
        return new Fragment(parts.get(0).start(), parts.get(parts.size() - 1).accept());
      }
    } else if (q instanceof RegexPattern.AtMost) {
      RegexPattern.AtMost atMost = (RegexPattern.AtMost) q;
      if (atMost.max() == 1) {
        Fragment f = compile(quantified.element());
        State start = newState();
        State accept = newState();
        addEpsilon(start.id, f.start());
        addEpsilon(start.id, accept.id);
        addEpsilon(f.accept(), accept.id);
        return new Fragment(start.id, accept.id);
      } else {
        State start = newState();
        State current = start;
        State accept = newState();
        int max = Math.min(atMost.max(), 5);
        for (int i = 0; i < max; i++) {
          Fragment f = compile(quantified.element());
          addEpsilon(current.id, f.start());
          addEpsilon(current.id, accept.id);
          addEpsilon(f.accept(), accept.id);
          current = states.get(f.accept());
        }
        return new Fragment(start.id, accept.id);
      }
    } else if (q instanceof RegexPattern.Limited) {
      RegexPattern.Limited limited = (RegexPattern.Limited) q;
      List<Fragment> parts = new ArrayList<>();
      for (int i = 0; i < limited.min(); i++) {
        parts.add(compile(quantified.element()));
      }
      int extra = Math.min(limited.max() - limited.min(), 5);
      for (int i = 0; i < extra; i++) {
        Fragment opt = compile(quantified.element());
        State s = newState();
        State e = newState();
        addEpsilon(s.id, opt.start());
        addEpsilon(s.id, e.id);
        addEpsilon(opt.accept(), e.id);
        parts.add(new Fragment(s.id, e.id));
      }
      for (int i = 0; i < parts.size() - 1; i++) {
        addEpsilon(parts.get(i).accept(), parts.get(i + 1).start());
      }
      return new Fragment(parts.get(0).start(), parts.get(parts.size() - 1).accept());
    }

    return compile(quantified.element());
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

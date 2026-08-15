package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.regex.RegexPattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NfaTest {

  @Test public void starQuantifier_hasEpsilonBypassFromStartToAccept() {
    Nfa nfa = Nfa.from(RegexPattern.of("a*"));
    assertThat(nfa.countEpsilonPaths(nfa.startState, nfa.acceptState)).isEqualTo(1);
  }

  @Test public void plusQuantifier_noDirectEpsilonBypassFromStartToAccept() {
    Nfa nfa = Nfa.from(RegexPattern.of("a+"));
    assertThat(nfa.countEpsilonPaths(nfa.startState, nfa.acceptState)).isEqualTo(0);
  }

  @Test public void countEpsilonPaths_parallelPaths_countsMultiple() {
    Nfa nfa = Nfa.from(RegexPattern.of("(?:a|b|c)"));
    assertThat(nfa.countEpsilonPaths(nfa.startState, nfa.acceptState)).isEqualTo(0);
    assertThat(nfa.states.get(nfa.startState).epsilonTransitions).hasSize(3);
  }

  @Test public void compileQuantified_possessive_doesNotAddBacktrackingLoopback() {
    Nfa possessive = Nfa.from(RegexPattern.of("a++"));
    Nfa greedy = Nfa.from(RegexPattern.of("a+"));
    assertThat(possessive.states.size()).isLessThan(greedy.states.size());
  }
}

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
    Nfa nfa = new Nfa();
    Nfa.State s1 = nfa.newState();
    Nfa.State s2 = nfa.newState();
    Nfa.State s3 = nfa.newState();
    Nfa.State s4 = nfa.newState();
    Nfa.State s5 = nfa.newState();
    nfa.addEpsilon(s1.id, s2.id);
    nfa.addEpsilon(s1.id, s3.id);
    nfa.addEpsilon(s1.id, s4.id);
    nfa.addEpsilon(s2.id, s5.id);
    nfa.addEpsilon(s3.id, s5.id);
    nfa.addEpsilon(s4.id, s5.id);
    assertThat(nfa.countEpsilonPaths(s1.id, s5.id)).isEqualTo(2);
  }

  @Test public void compileQuantified_possessive_doesNotAddBacktrackingLoopback() {
    Nfa possessive = Nfa.from(RegexPattern.of("a++"));
    Nfa greedy = Nfa.from(RegexPattern.of("a+"));
    assertThat(possessive.states.size()).isLessThan(greedy.states.size());
  }
}

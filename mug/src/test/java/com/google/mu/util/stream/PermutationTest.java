package com.google.mu.util.stream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.truth.Truth8;

@RunWith(JUnit4.class)
public class PermutationTest {

  @Test public void permute_empty() {
    Truth8.assertThat(permute(Set.of())).containsExactly(List.of());
  }

  @Test public void permute_singleElement() {
    Truth8.assertThat(permute(Set.of(1))).containsExactly(List.of(1));
  }

  @Test public void permute_twoElements() {
    Truth8.assertThat(permute(Set.of(1, 2))).containsExactly(List.of(1, 2), List.of(2, 1));
  }

  @Test public void permute_threeElements() {
    Truth8.assertThat(permute(Set.of(1, 2, 3)))
        .containsExactly(List.of(1, 2, 3), List.of(1, 3, 2), List.of(2, 1, 3), List.of(2, 3, 1), List.of(3, 1, 2), List.of(3, 2, 1));
  }

  static <T> Stream<List<T>> permute(Set<T> elements) {
    class Permutation extends Iteration<List<T>> {
      Permutation() {
        lazily(() -> next(List.of(), elements));
      }

      void next(List<T> sofar, Set<T> remaining) {
        if (remaining.isEmpty()) {
          emit(sofar);
        }
        for (T element : remaining) {
          List<T> state = new ArrayList<>(sofar);
          state.add(element);
          Set<T> newRemaining = new LinkedHashSet<>(remaining);
          newRemaining.remove(element);
          lazily(() -> next(state, newRemaining));
        }
      }
    }
    return new Permutation().iterate();
  }
}

package com.google.mu.util.stream;

import static com.google.common.truth.Truth8.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;

@RunWith(JUnit4.class)
public class PermutationTest {

  @Test public void permute_empty() {
    assertThat(permute(List.of())).containsExactly(List.of());
  }

  @Test public void permute_singleElement() {
    assertThat(permute(List.of(1))).containsExactly(List.of(1));
  }

  @Test public void permute_twoElements() {
    assertThat(permute(List.of(1, 2))).containsExactly(List.of(1, 2), List.of(2, 1));
  }

  @Test public void permute_threeElements() {
    assertThat(permute(List.of(1, 2, 3)))
        .containsExactly(List.of(1, 2, 3), List.of(1, 3, 2), List.of(2, 1, 3), List.of(2, 3, 1), List.of(3, 1, 2), List.of(3, 2, 1));
  }

  static <T> Stream<ImmutableList<T>> permute(Collection<T> elements) {
    class Permutation extends Iteration<ImmutableList<T>> {
      Permutation() {
        lazily(() -> next(new ArrayList<>(elements), 0));
      }

      void next(List<T> buffer, int i) {
        if (i == buffer.size()) {
          emit(ImmutableList.copyOf(buffer));
          return;
        }
        lazily(() -> next(buffer, i + 1));
        forEachLazily(
            IntStream.range(i + 1, buffer.size()),
            j -> {
              Collections.swap(buffer, i, j);
              next(buffer, i + 1);
              lazily(() -> Collections.swap(buffer, i, j));
            });
      }
    }
    return new Permutation().iterate();
  }
}

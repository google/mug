package com.google.mu.util.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
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

  static <T> Stream<ImmutableList<T>> permute(Set<T> elements) {
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
              swap(buffer, i, j);
              next(buffer, i + 1);
              lazily(() -> swap(buffer, i, j));
            });
      }
    }
    return new Permutation().iterate();
  }

  private static <T> void swap(List<T> list, int i, int j) {
    T temp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, temp);
  }
}

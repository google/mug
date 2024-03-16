package com.google.mu.collect;


import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.collect.MoreIterables.pairwise;
import static java.util.Arrays.asList;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import com.google.mu.collect.MoreIterables;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class MoreIterablesTest {

  @Test
  public void pairwise_bothEmpty(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(), kind2.of(), (a, b) -> false)).isTrue();
    assertThat(pairwise(kind1.of(), kind2.of(), (a, b) -> true)).isTrue();
  }

  @Test
  public void pairwise_oneIterableIsEmpty(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1), kind2.of(), (a, b) -> true)).isFalse();
    assertThat(pairwise(kind1.of(), kind2.of(1), (a, b) -> true)).isFalse();
  }

  @Test
  public void pairwise_sameSizeAllMatch(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1, 2), kind2.of("1", "2"), (a, b) -> a.toString().equals(b)))
        .isTrue();
  }

  @Test
  public void pairwise_sameSizeFirstPairMismatch(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1, 2), kind2.of("x", "2"), (a, b) -> a.toString().equals(b)))
        .isFalse();
    assertThat(pairwise(kind1.of(10, 2), kind2.of("1", "2"), (a, b) -> a.toString().equals(b)))
        .isFalse();
  }

  @Test
  public void pairwise_sameSizeSecondPairMismatch(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1, 2), kind2.of("1", "x"), (a, b) -> a.toString().equals(b)))
        .isFalse();
    assertThat(pairwise(kind1.of(1, 20), kind2.of("1", "2"), (a, b) -> a.toString().equals(b)))
        .isFalse();
  }

  @Test
  public void pairwise_firstIsShorter(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1, 2), kind2.of("1", "2", "3"), (a, b) -> true)).isFalse();
  }

  @Test
  public void pairwise_secondIsShorter(
      @TestParameter IterableKind kind1, @TestParameter IterableKind kind2) {
    assertThat(pairwise(kind1.of(1, 2, 3), kind2.of("1", "2"), (a, b) -> true)).isFalse();
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MoreIterables.class);
  }

  private enum IterableKind {
    ARRAY_LIST {
      @Override
      @SafeVarargs
      final <E> List<E> of(E... elements) {
        return asList(elements);
      }
    },
    IMMUTABLE_SET {
      @Override
      @SafeVarargs
      final <E> ImmutableSet<E> of(E... elements) {
        return ImmutableSet.copyOf(elements);
      }
    };

    abstract <E> Iterable<E> of(@SuppressWarnings("unchecked") E... elements);
  }
}

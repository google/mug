package com.google.mu.algorithms;

import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;

public class FibonacciTest {
  @Test public void fibonacci() {
    assertThat(Fibonacci.iterate().limit(7))
        .containsExactly(0L, 1L, 1L, 2L, 3L, 5L, 8L)
        .inOrder();
  }
}

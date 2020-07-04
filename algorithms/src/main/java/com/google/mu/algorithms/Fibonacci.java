package com.google.mu.algorithms;

import java.util.stream.Stream;

import com.google.mu.util.stream.Iteration;

/**
 * The Fibonacci sequence implemented recursively and consumed as an iterative stream.
 *
 * @since 4.5
 */
public final class Fibonacci extends Iteration<Long> {
  public static Stream<Long> iterate() {
    return new Fibonacci().from(0L, 1L).stream();
  }

  private Fibonacci from(Long v1, Long v2) {
    yield(v1);
    yield(() -> from(v2, v1 + v2));
    return this;
  }

  private Fibonacci() {}
}
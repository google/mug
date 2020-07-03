package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Map;

import com.google.mu.util.stream.Iteration.Continuation;

/**
 * Similar to {@link Iteration}, but is used to iteratively {@link #yeild yield()} pairs into a
 * lazy {@link BiStream}.
 */
public class BiIteration<L, R> {
  private final Iteration<Map.Entry<L, R>> iteration = new Iteration<>();

  /** Yields the pair of {@code left} and {@code right} to the result {@code BiStream}. */
  public final BiIteration<L, R> yield(L left, R right) {
    iteration.yield(
        new AbstractMap.SimpleImmutableEntry<>(requireNonNull(left), requireNonNull(right)));
    return this;
  }

  /**
   * Yields to the result {@code BiStream} a recursive iteration or lazy side-effect wrapped in
   * {@code continuation}.
   */
  public final BiIteration<L, R> yield(Continuation continuation) {
    iteration.yield(continuation);
    return this;
  }

  /**
   * Returns the {@code BiStreram} that iterates through the {@link #yield yielded} pairs.
   *
   * <p>Because a {@code BiIteration} instance is stateful and mutable, {@code stream()} can be
   * called at most once per instance.
   *
   * @throws IllegalStateException if {@code stream()} has already been called.
   */
  public final BiStream<L, R> stream() {
    return BiStream.from(iteration.stream());
  }
}

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

  /** Yields {@code key}  and {@code value} pair to the result stream. */
  public final BiIteration<L, R> yield(L left, R right) {
    iteration.yield(
        new AbstractMap.SimpleImmutableEntry<>(requireNonNull(left), requireNonNull(right)));
    return this;
  }

  /**
   * Yields to the stream a recursive iteration or lazy side-effect
   * wrapped in {@code continuation}.
   */
  public final BiIteration<L, R> yield(Continuation continuation) {
    iteration.yield(continuation);
    return this;
  }

  /** Returns the {@code BiStreram} that iterates through the {@link #yield yielded} pairs. */
  public final BiStream<L, R> stream() {
    return BiStream.from(iteration.stream());
  }
}

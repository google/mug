package com.google.mu.function;

import static java.util.Objects.requireNonNull;

/**
 * A 64-bit long consumer that can throw checked exceptions.
 */
@FunctionalInterface
public interface CheckedLongConsumer<E extends Throwable> {
  void accept(long input) throws E;

  /**
   * Returns a new {@code CheckedLongConsumer} that also passes the input to {@code that}.
   * For example: {@code out::writeLong.andThen(logger::logLong).accept(123L)}.
   */
  default CheckedLongConsumer<E> andThen(CheckedLongConsumer<E> that) {
    requireNonNull(that);
    return input -> {
      accept(input);
      that.accept(input);
    };
  }
}
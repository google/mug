package com.google.mu.function;

import static java.util.Objects.requireNonNull;

/**
 * An 64-bit long consumer that can throw checked exceptions.
 */
@FunctionalInterface
public interface CheckedLongConsumer<E extends Throwable> {
  void accept(long input) throws E;

  /**
   * Returns a new {@code CheckedIntConsumer} that also passes the input to {@code that}.
   * For example: {@code out::writeInt.andThen(logger::logInt).accept(123)}.
   */
  default CheckedLongConsumer<E> andThen(CheckedLongConsumer<E> that) {
    requireNonNull(that);
    return input -> {
      accept(input);
      that.accept(input);
    };
  }
}
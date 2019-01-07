package com.google.mu.function;

import static java.util.Objects.requireNonNull;

/**
 * An double consumer that can throw checked exceptions.
 */
@FunctionalInterface
public interface CheckedDoubleConsumer<E extends Throwable> {
  void accept(double input) throws E;

  /**
   * Returns a new {@code CheckedIntConsumer} that also passes the input to {@code that}.
   * For example: {@code out::writeInt.andThen(logger::logInt).accept(123)}.
   */
  default CheckedDoubleConsumer<E> andThen(CheckedDoubleConsumer<E> that) {
    requireNonNull(that);
    return input -> {
      accept(input);
      that.accept(input);
    };
  }
}
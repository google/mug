package com.google.mu.function;

import static java.util.Objects.requireNonNull;

/**
 * An int consumer that can throw checked exceptions.
 *
 * @since 1.14
 */
@FunctionalInterface
public interface CheckedIntConsumer<E extends Throwable> {
  void accept(int input) throws E;

  /**
   * Returns a new {@code CheckedIntConsumer} that also passes the input to {@code that}.
   * For example: {@code out::writeInt.andThen(logger::logInt).accept(123)}.
   */
  default CheckedIntConsumer<E> andThen(CheckedIntConsumer<E> that) {
    requireNonNull(that);
    return input -> {
      accept(input);
      that.accept(input);
    };
  }
}
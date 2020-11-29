package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Represents two unrelated or loosely-related things of type {@code A} and {@code B}.
 * Usually as a return type of a function that needs to return two things.
 *
 * @since 5.1
 */
@FunctionalInterface
public interface Both<A, B> {
  /**
   * Combines the pair of things with {@code combiner} function.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  <T> T combine(BiFunction<? super A, ? super B, T> combiner);

  /**
   * If the pair {@link #match match()} {@code condition}, returns a {@link BiOptional} containing
   * the pair, or else returns empty.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  default BiOptional<A, B> filter(BiPredicate<? super A, ? super B> condition) {
    requireNonNull(condition);
    return combine((a, b) -> condition.test(a, b) ? BiOptional.of(a, b) : BiOptional.empty());
  }

  /**
   * Returns true if the pair match {@code condition}.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  default boolean match(BiPredicate<? super A, ? super B> condition) {
    return combine(condition::test);
  }

  /**
   * Invokes {@code consumer} with the pair and returns this object as is.
   *
   * @throws NullPointerException if {@code consumer} is null
   */
  default Both<A, B> peek(BiConsumer<? super A, ? super B> consumer) {
    requireNonNull(consumer);
    return combine((a, b) -> {
      consumer.accept(a, b);
      return this;
    });
  }
}

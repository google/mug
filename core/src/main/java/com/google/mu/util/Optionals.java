package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import com.google.mu.function.CheckedBiConsumer;
import com.google.mu.function.CheckedBiFunction;

/** Utilities pertaining to {@link Optional}. */
public final class Optionals {

  /**
   * Invokes {@code consumer} if both {@code left} and {@code right} are present.
   *
   * @throws E if consumer throws
   */
  public static <A, B, E extends Throwable> void ifPresent(
      Optional<A> left, Optional<B> right, CheckedBiConsumer<? super A, ? super B, E> consumer)
      throws E {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(consumer);
    if (left.isPresent() && right.isPresent()) {
      consumer.accept(left.get(), right.get());
    }
  }

  /**
   * Maps {@code left} and {@code right} using {@code mapper} if both are present.
   * Returns an {@link Optional} wrapping the result of {@code mapper} if non-null, or else returns
   * {@code Optional.empty()}.
   *
   * @throws E if mapper throws
   */
  public static <A, B, R, E extends Throwable> Optional<R> map(
      Optional<A> left, Optional<B> right, CheckedBiFunction<? super A, ? super B, ? extends R, E> mapper)
      throws E {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(mapper);
    if (left.isPresent() && right.isPresent()) {
      return Optional.ofNullable(mapper.apply(left.get(), right.get()));
    }
    return Optional.empty();
  }

  /**
   * Maps {@code left} and {@code right} using {@code mapper} if both are present.
   * Returns the result of {@code mapper} or {@code Optional.empty()} if either {@code left} or {@code right}
   * is empty.
   *
   * @throws NullPointerException if {@code mapper} returns null
   * @throws E if mapper throws
   */
  public static <A, B, R, E extends Throwable> Optional<R> flatMap(
      Optional<A> left, Optional<B> right,
      CheckedBiFunction<? super A, ? super B, ? extends Optional<R>, E> mapper)
      throws E {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(mapper);
    if (left.isPresent() && right.isPresent()) {
      return requireNonNull(mapper.apply(left.get(), right.get()));
    }
    return Optional.empty();
  }
  
  private Optionals() {}
}

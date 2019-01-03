package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

import com.google.mu.function.CheckedBiConsumer;
import com.google.mu.function.CheckedBiFunction;
import com.google.mu.function.CheckedConsumer;

/**
 * Utilities pertaining to {@link Optional}.
 *
 * @since 1.14
 */
public final class Optionals {

  /**
<<<<<<< HEAD
   * Invokes {@code consumer} if both {@code left} and {@code right} are present.
=======
   * Invokes {@code consumer} if {@code optional} is present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example: <pre>
   *   ifPresent(optionalStory, Story::tell).orElse(() -> print("no story"));
   * </pre>
   *
   * <p>This method is very similar to JDK {@link Optional#ifPresent} with two differences: <ol>
   * <li>{@code ifPresent(optionalStory, Story::tell)} reads with "if" at the beginning, which may read
   *     somewhat more natural.
   * <li>{@code orElse()} is chained fluently, compared to {@link Optional#ifPresentOrElse}.
   * </ol>
>>>>>>> master
   */
  public static <T, E extends Throwable> Premise ifPresent(
      Optional<T> optional, CheckedConsumer<? super T, E> consumer) throws E {
    requireNonNull(optional);
    requireNonNull(consumer);
    if (optional.isPresent()) {
      consumer.accept(optional.get());
      return Conditional.TRUE;
    } else {
      return Conditional.FALSE;
    }
  }

  /**
   * Invokes {@code consumer} if both {@code left} and {@code right} are present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example: <pre>
   *   ifPresent(when, where, Story::tell).orElse(() -> print("no story"));
   * </pre>
   */
  public static <A, B, E extends Throwable> Premise ifPresent(
      Optional<A> left, Optional<B> right, CheckedBiConsumer<? super A, ? super B, E> consumer)
      throws E {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(consumer);
    if (left.isPresent() && right.isPresent()) {
      consumer.accept(left.get(), right.get());
      return Conditional.TRUE;
    } else {
      return Conditional.FALSE;
    }
  }

  /**
   * Maps {@code left} and {@code right} using {@code mapper} if both are present.
   * Returns an {@link Optional} wrapping the result of {@code mapper} if non-null, or else returns
   * {@code Optional.empty()}.
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

/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.google.mu.function.CheckedBiConsumer;
import com.google.mu.function.CheckedBiFunction;
import com.google.mu.function.CheckedConsumer;
import com.google.mu.function.CheckedDoubleConsumer;
import com.google.mu.function.CheckedIntConsumer;
import com.google.mu.function.CheckedLongConsumer;
import com.google.mu.function.CheckedSupplier;
import com.google.mu.function.OptionalDoubleFunction;
import com.google.mu.function.OptionalFunction;
import com.google.mu.function.OptionalIntFunction;
import com.google.mu.function.OptionalLongFunction;

/**
 * Utilities pertaining to {@link Optional}.
 *
 * @since 1.14
 */
public final class Optionals {
  /**
   * Returns an Optional that wraps the nullable result from {@code supplier} if {@code condition}
   * is true, or else {@code empty()}.
   *
   * <p>Example: {@code Optional<Double> avg = optionally(count > 0, () -> sum / count);}
   *
   * @throws NullPointerException if {@code supplier} is null
   * @since 3.7
   */
  public static <T, E extends Throwable> Optional<T> optionally(
      boolean condition, CheckedSupplier<? extends T, E> supplier) throws E {
    requireNonNull(supplier);
    return condition ? Optional.ofNullable(supplier.get()) : Optional.empty();
  }

  /**
   * Returns an Optional that wraps {@code value} if {@code condition} is true and {@code value} is
   * not null, or else {@code empty()}.
   *
   * <p>Example: {@code Optional<Foo> foo = optional(input.hasFoo(), input.getFoo());}
   *
   * @since 3.7
   */
  public static <T> Optional<T> optional(boolean condition, T value) {
    return condition ? Optional.ofNullable(value) : Optional.empty();
  }

  /**
   * Invokes {@code consumer} if {@code optional} is present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example:
   *
   * <pre>{@code
   *   ifPresent(findId(), System.out::print)
   *       .or(() -> ifPresent(findName(), System.out::print))
   *       .or(() -> ifPresent(findCreditCardNumber(), System.out::print))
   *       .orElse(() -> System.out.print("no identity found"));
   * }</pre>
   *
   * <p>This method is very similar to JDK {@link OptionalInt#ifPresent} with a few differences: <ol>
   * <li>{@link Premise#orElse orElse()} is chained fluently, compared to {@link OptionalInt#ifPresentOrElse}.
   * <li>{@link Premise#or or()} allows chaining arbitrary number of alternative options on arbitrary
   *     optional types.
   * <li>Propagates checked exceptions from the {@code consumer}.
   * <li>Syntax is consistent across one-Optional and two-Optional {@code ifPresent()} overloads.
   * <li>{@code ifPresent(findId(), System.out::print)} begins the statement with "if", which may read
   *     somewhat closer to regular {@code if} statements.
   * </ol>
   */
  public static <E extends Throwable> Premise ifPresent(
      OptionalInt optional, CheckedIntConsumer<E> consumer) throws E {
    requireNonNull(optional);
    requireNonNull(consumer);
    if (optional.isPresent()) {
      consumer.accept(optional.getAsInt());
      return Conditional.TRUE;
    } else {
      return Conditional.FALSE;
    }
  }

  /**
   * Invokes {@code consumer} if {@code optional} is present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example:
   *
   * <pre>{@code
   *   ifPresent(findId(), System.out::print)
   *       .or(() -> ifPresent(findName(), System.out::print))
   *       .or(() -> ifPresent(findCreditCardNumber(), System.out::print))
   *       .orElse(() -> System.out.print("id not found"));
   * }</pre>
   *
   * <p>This method is very similar to JDK {@link OptionalLong#ifPresent} with a few differences: <ol>
   * <li>{@link Premise#orElse orElse()} is chained fluently, compared to {@link OptionalLong#ifPresentOrElse}.
   * <li>{@link Premise#or or()} allows chaining arbitrary number of alternative options on arbitrary
   *     optional types.
   * <li>Propagates checked exceptions from the {@code consumer}.
   * <li>Syntax is consistent across one-Optional and two-Optional {@code ifPresent()} overloads.
   * <li>{@code ifPresent(findId(), System.out::print)} begins the statement with "if", which may read
   *     somewhat closer to regular {@code if} statements.
   * </ol>
   */
  public static <E extends Throwable> Premise ifPresent(
      OptionalLong optional, CheckedLongConsumer<E> consumer) throws E {
    requireNonNull(optional);
    requireNonNull(consumer);
    if (optional.isPresent()) {
      consumer.accept(optional.getAsLong());
      return Conditional.TRUE;
    } else {
      return Conditional.FALSE;
    }
  }

  /**
   * Invokes {@code consumer} if {@code optional} is present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example:
   *
   * <pre>{@code
   *   ifPresent(findMileage(), System.out::print)
   *       .or(() -> ifPresent(findName(), System.out::print))
   *       .or(() -> ifPresent(findCreditCardNumber(), System.out::print))
   *       .orElse(() -> System.out.print("id mileage found"));
   * }</pre>
   *
   * <p>This method is very similar to JDK {@link OptionalDouble#ifPresent} with a few differences: <ol>
   * <li>{@link Premise#orElse orElse()} is chained fluently, compared to {@link OptionalDouble#ifPresentOrElse}.
   * <li>{@link Premise#or or()} allows chaining arbitrary number of alternative options on arbitrary
   *     optional types.
   * <li>Propagates checked exceptions from the {@code consumer}.
   * <li>Syntax is consistent across one-Optional and two-Optional {@code ifPresent()} overloads.
   * <li>{@code ifPresent(findMileage(), System.out::print)} begins the statement with "if", which may read
   *     somewhat closer to regular {@code if} statements.
   * </ol>
   */
  public static <E extends Throwable> Premise ifPresent(
      OptionalDouble optional, CheckedDoubleConsumer<E> consumer) throws E {
    requireNonNull(optional);
    requireNonNull(consumer);
    if (optional.isPresent()) {
      consumer.accept(optional.getAsDouble());
      return Conditional.TRUE;
    } else {
      return Conditional.FALSE;
    }
  }

  /**
   * Invokes {@code consumer} if {@code optional} is present. Returns a {@code Premise}
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example:
   *
   * <pre>{@code
   *   ifPresent(findStory(), Story::tell)
   *       .or(() -> ifPresent(findGame(), Game::play))
   *       .or(() -> ifPresent(findMovie(), Movie::watch))
   *       .orElse(() -> print("Nothing to do"));
   * }</pre>
   *
   * <p>This method is very similar to JDK {@link Optional#ifPresent} with a few differences: <ol>
   * <li>{@link Premise#orElse orElse()} is chained fluently, compared to {@link Optional#ifPresentOrElse}.
   * <li>{@link Premise#or or()} allows chaining arbitrary number of alternative options on arbitrary
   *     optional types.
   * <li>Propagates checked exceptions from the {@code consumer}.
   * <li>Syntax is consistent across one-Optional and two-Optional {@code ifPresent()} overloads.
   * <li>{@code ifPresent(findStory(), Story::tell)} begins the statement with "if", which may read
   *     somewhat closer to regular {@code if} statements.
   * </ol>
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
   * object to allow {@link Premise#orElse orElse()} and friends to be chained. For example:
   *
   * <pre>{@code
   *   ifPresent(when, where, Story::tell)
   *       .orElse(() -> print("no story"));
   * }</pre>
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
   * Runs {@code action} if the pair is present. Allows chaining multiple {@code BiOptional}
   * and {@code Optional} together. For example:
   * <pre>{@code
   * ifPresent(both(firstName, lastName), (f, l) -> System.out.println(...))
   *     .or(() -> ifPresent(firstName, f -> ...))
   *     .or(() -> ifPresent(lastName, l -> ...))
   *     .orElse(...);
   * }</pre>
   *
   * @since 5.0
   */
  public static<A, B, E extends Throwable> Premise ifPresent(
      BiOptional<A, B> optional, CheckedBiConsumer<? super A, ? super B, E> consumer) throws E{
    requireNonNull(optional);
    requireNonNull(consumer);
    if (optional.isPresent()) {
      consumer.accept(optional.map(Optionals::first).get(), optional.map(Optionals::second).get());
      return Conditional.TRUE;
    }
    return Conditional.FALSE;
  }

  /**
   * Collects {@code optional} using {@code function}. For example:
   * <pre>{@code
   * private static final OptionalFunction<String, UserId> TO_USER_ID =
   *     OptionalFunction.with(ThisClass::parse, UserId::none);
   * // ...
   *
   *   UserId userId = Optionals.collect(optionalUserIdString, TO_USER_ID);
   * }</pre>
   *
   * @since 5.3
   */
  public static <T, R> R collect(Optional<T> optional, OptionalFunction<? super T, R> function) {
    return optional.isPresent() ? function.present(optional.get()) : function.absent();
  }

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * OptionalInt} to {@code Optional<Integer>} using:
   * <pre>{@code
   *   Optionals.collect(computeOptionalInt(), toOptional());
   * }</pre>
   *
   * @since 5.3
   */
  public static <R> R collect(OptionalInt optional, OptionalIntFunction<R> function) {
    return optional.isPresent() ? function.present(optional.getAsInt()) : function.intAbsent();
  }

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * OptionalLong} to {@code Optional<Long>} using:
   * <pre>{@code
   *   Optionals.collect(computeOptionalLong(), toOptional());
   * }</pre>
   *
   * @since 5.3
   */
  public static <R> R collect(OptionalLong optional, OptionalLongFunction<R> function) {
    return optional.isPresent() ? function.present(optional.getAsLong()) : function.longAbsent();
  }

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * OptionalDouble} to {@code Optional<Double>} using:
   * <pre>{@code
   *   Optionals.collect(computeOptionalDouble(), toOptional());
   * }</pre>
   *
   * @since 5.3
   */
  public static <R> R collect(OptionalDouble optional, OptionalDoubleFunction<R> function) {
    return optional.isPresent() ? function.present(optional.getAsDouble()) : function.doubleAbsent();
  }

  private static final OptionalFunction<Integer, OptionalInt> TO_OPTIONAL_INT =
      OptionalFunction.with(OptionalInt::of, OptionalInt::empty);

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * Optional<Integer>} to {@code OptionalInt} using:
   * <pre>{@code
   *   Optionals.collect(computeOptional(), toOptionalInt());
   * }</pre>
   *
   * @since 5.3
   */
  public static OptionalFunction<Integer, OptionalInt> toOptionalInt() {
    return TO_OPTIONAL_INT;
  }

  private static final OptionalFunction<Long, OptionalLong> TO_OPTIONAL_LONG =
      OptionalFunction.with(OptionalLong::of, OptionalLong::empty);

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * Optional<Long>} to {@code OptionalLong} using:
   * <pre>{@code
   *   Optionals.collect(computeOptional(), toOptionalLong());
   * }</pre>
   *
   * @since 5.3
   */
  public static OptionalFunction<Long, OptionalLong> toOptionalLong() {
    return TO_OPTIONAL_LONG;
  }

  private static final OptionalFunction<Double, OptionalDouble> TO_OPTIONAL_DOUBLE =
      OptionalFunction.with(OptionalDouble::of, OptionalDouble::empty);

  /**
   * Collects {@code optional} using {@code function}. For example, you can convert an {@code
   * Optional<Double>} to {@code OptionalDouble} using:
   * <pre>{@code
   *   Optionals.collect(computeOptional(), toOptionalDouble());
   * }</pre>
   *
   * @since 5.3
   */
  public static OptionalFunction<Double, OptionalDouble> toOptionalDouble() {
    return TO_OPTIONAL_DOUBLE;
  }

  private static final ToOptional TO_OPTIONAL = new ToOptional();

  /**
   * Returns an object that can be passed to {@code collect()} to collect an
   * {@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble}
   * to {@code Optional<Integer}, {@code Optional<Long>} and {@code Optional<Double>}
   * respectively.
   *
   * @since 5.3
   */
  public static ToOptional toOptional() {
    return TO_OPTIONAL;
  }

  /**
   * This type can convert an{@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble}
   * to {@code Optional<Integer}, {@code Optional<Long>} and {@code Optional<Double>}
   * respectively.
   *
   * @since 5.3
   */
  public static final class ToOptional
      implements
          OptionalIntFunction<Optional<Integer>>,
          OptionalLongFunction<Optional<Long>>,
          OptionalDoubleFunction<Optional<Double>> {

    @Override public Optional<Integer> present(int value) {
      return Optional.of(value);
    }

    @Override public Optional<Long> present(long value) {
      return Optional.of(value);
    }

    @Override public Optional<Double> present(double value) {
      return Optional.of(value);
    }

    @Override public Optional<Long> longAbsent() {
      return Optional.empty();
    }

    @Override public Optional<Integer> intAbsent() {
      return Optional.empty();
    }

    @Override public Optional<Double> doubleAbsent() {
      return Optional.empty();
    }

    ToOptional() {}
  }

  /**
   * Maps {@code left} and {@code right} using {@code mapper} if both are present.
   * Returns an {@link Optional} wrapping the result of {@code mapper} if non-null, or else returns
   * {@code Optional.empty()}.
   *
   * @since 3.8
   * @deprecated Use {@link BiOptional#both} instead.
   */
  @Deprecated
  public static <A, B, R, E extends Throwable> Optional<R> mapBoth(
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
   * @since 3.8
   * @deprecated Use {@link BiOptional#both} instead.
   */
  @Deprecated
  public static <A, B, R, E extends Throwable> Optional<R> flatMapBoth(
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

  private static <A, B> A first(A a, B b) {
    return a;
  }

  private static <A, B> B second(A a, B b) {
    return b;
  }

  private Optionals() {}
}

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

import java.util.Collections;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import com.google.mu.function.CheckedBiConsumer;
import com.google.mu.function.CheckedConsumer;
import com.google.mu.function.CheckedDoubleConsumer;
import com.google.mu.function.CheckedIntConsumer;
import com.google.mu.function.CheckedLongConsumer;
import com.google.mu.function.CheckedSupplier;

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
   * Returns an immutable singleton {@link Set} whose only element is the contained instance if it
   * is present; an empty immutable {@link Set} otherwise.
   *
   * <p>This is useful when you are trying to apply some side-effects on the value contained in the
   * {@code Optional}: <pre>{@code
   * for (Foo foo : asSet(optionalFoo)) {
   *   // ...
   * }
   * }</pre>
   *
   * While you could use {@code optional.ifPresent(v -> ...)}, the lambda has limitations
   * (no checked exceptions etc.). The body of the lambda can also become unwieldy if there are
   * more than 5 lines of code, with conditionals and what not.
   *
   * <p>If you need to add the Optional's contained value into another collection, consider
   * {@code results.addAll(asSet(optional))}. The {@code addAll()} side effect stands out
   * more crisply than in {@code optional.ifPresent(results::add)} where the important data
   * flow into the {@code results} collection is somewhat buried and easy to miss.
   *
   * <p>Lastly, if you need to check whether the optional contains a particular value, consider using
   * {@code asSet(optional).contains(value)}.
   *
   * @since 6.1
   */
  public static <T> Set<T> asSet(Optional<? extends T> optional) {
    return optional.isPresent() ? Collections.singleton(optional.get()) : Collections.emptySet();
  }

  /**
   * If {@code a} and {@code b} are present, returns a {@code BiOptional} instance containing them;
   * otherwise returns an empty {@code BiOptional}.
   *
   * @throws NullPointerException if {@code a} or {@code b} is null
   * @since 5.7
   */
  public static <A, B> BiOptional<A, B> both(Optional<? extends A> a, Optional<? extends B> b) {
    requireNonNull(a);
    requireNonNull(b);
    return a.isPresent() && b.isPresent() ? BiOptional.of(a.get(), b.get()) : BiOptional.empty();
  }

  /**
   * If both {@code a} and {@code b} return non-empty values, returns a {@code BiOptional} instance
   * containing the non-empty values; otherwise returns an empty {@code BiOptional}.
   *
   * <p>Short-circuits if {@code a} returns empty.
   *
   * @throws NullPointerException if {@code a} or {@code b} is null
   * @throws E if either {@code a} or {@code b} throws {@code E}
   * @since 8.0
   */
  public static <A, B, E extends Throwable> BiOptional<A, B> both(
      CheckedSupplier<? extends Optional<? extends A>, ? extends E> a,
      CheckedSupplier<? extends Optional<? extends B>, ? extends E> b) throws E {
    requireNonNull(a);
    requireNonNull(b);
    A r1 = a.get().orElse(null);
    if (r1 == null) {
      return BiOptional.empty();
    }
    B r2 = b.get().orElse(null);
    if (r2 == null) {
      return BiOptional.empty();
    }
    return BiOptional.of(r1, r2);
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

  private static <A, B> A first(A a, B b) {
    return a;
  }

  private static <A, B> B second(A a, B b) {
    return b;
  }

  private Optionals() {}
}

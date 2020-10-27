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

import java.util.Objects;
import java.util.Optional;

import com.google.mu.function.CheckedBiConsumer;
import com.google.mu.function.CheckedBiFunction;
import com.google.mu.function.CheckedBiPredicate;
import com.google.mu.function.CheckedFunction;
import com.google.mu.function.CheckedSupplier;
import com.google.mu.util.stream.BiStream;

/**
 * An optional pair
 *
 * @since 5.0
 */
public abstract class BiOptional<A, B> {
  /** Returns an empty (absent) instance. */
  @SuppressWarnings("unchecked") // EMPTY contains no A or B.
  public static <A, B> BiOptional<A, B> empty() {
    return (BiOptional<A, B>) EMPTY;
  }

  /** Returns an instance wrapping non-null pair {@code (a, b)}. */
  public static <A, B> BiOptional<A, B> of(A a, B b) {
    return new Present<>(a, b);
  }

  /**
   * Returns an instance wrapping non-null pair {@code (a.get(), b.get())} if both
   * are present, or else returns empty instance.
   */
  public static <A, B> BiOptional<A, B> both(Optional<A> a, Optional<B> b) {
    requireNonNull(a);
    requireNonNull(b);
    return a.isPresent() && b.isPresent() ? of(a.get(), b.get()) : empty();
  }

  /**
   * Returns BiOptional wrapping the results of applying the {@code toA} and {@code toB}
   * functions on the valued wrapped in {@code optional} if it's present, or else returns
   * {@link #empty}.
   */
  public static <T, A, B, E extends Throwable> BiOptional<A, B> from(
      Optional<T> optional,
      CheckedFunction<? super T, ? extends A, ? extends E> toA,
      CheckedFunction<? super T, ? extends B, ? extends E> toB) throws E {
    requireNonNull(toA);
    requireNonNull(toB);
    return optional.isPresent()
        ? of(toA.apply(optional.get()), toB.apply(optional.get()))
        : empty();
  }

  /**
   * Joins the pair using the {@code mapper} function.
   *
   * <p>
   * Returns {@code Optional.empty()} if the pair is absent or if {@code mapper}
   * returns null.
   */
  public abstract <T, E extends Throwable> Optional<T> join(
      CheckedBiFunction<? super A, ? super B, ? extends T, E> mapper) throws E;

  /**
   * Joins the pair using the {@code mapper} function.
   *
   * <p>
   * Returns {@code Optional.empty()} if the pair is absent or if {@code mapper}
   * returns empty.
   */
  public abstract <T, E extends Throwable> Optional<T> flatJoin(
      CheckedBiFunction<? super A, ? super B, Optional<T>, E> mapper) throws E;

  /**
   * Maps the pair using the {@code mapper} function.
   *
   * <p>
   * Returns {@code Optional.empty()} if the pair is absent or if {@code mapper}
   * returns empty.
   */
  public abstract <A2, B2, E extends Throwable> BiOptional<A2, B2> flatMap(
      CheckedBiFunction<? super A, ? super B, BiOptional<A2, B2>, E> mapper) throws E;

  /**
   * Returns this BiOptional object as is if the pair is present and matches
   * {@code predicate}, or else returns {@link #empty}.
   */
  public abstract <E extends Throwable> BiOptional<A, B> filter(
      CheckedBiPredicate<? super A, ? super B, E> predicate)
      throws E;

  /** Returns true if the pair is present and matches {@code predicate}. */
  public abstract <E extends Throwable> boolean match(
      CheckedBiPredicate<? super A, ? super B, E> predicate) throws E;

  /** Runs {@code consumer} if the pair is present. */
  public abstract <E extends Throwable> void ifPresent(
      CheckedBiConsumer<? super A, ? super B, E> consumer) throws E;

  /** Returns true if the pair is present. */
  public abstract boolean isPresent();

  /**
   * Returns this instance if not empty,
   * or else returns the result of {@code alternative} supplier.
   */
  public abstract <E extends Throwable> BiOptional<A, B> or(
      CheckedSupplier<? extends BiOptional<? extends A, ? extends B>, E> alternative) throws E;

  /** Returns a {@code BiStream} view of this BiOptional. */
  public abstract BiStream<A, B> stream();

  private static final BiOptional<Object, Object> EMPTY = new BiOptional<Object, Object>() {
    @Override
    public <T, E extends Throwable> Optional<T> join(
        CheckedBiFunction<Object, Object, ? extends T, E> mapper) throws E {
      requireNonNull(mapper);
      return Optional.empty();
    }

    @Override
    public <T, E extends Throwable> Optional<T> flatJoin(
        CheckedBiFunction<Object, Object, Optional<T>, E> mapper)
        throws E {
      requireNonNull(mapper);
      return Optional.empty();
    }

    @Override
    public <A2, B2, E extends Throwable> BiOptional<A2, B2> flatMap(
        CheckedBiFunction<Object, Object, BiOptional<A2, B2>, E> mapper) throws E {
      requireNonNull(mapper);
      return empty();
    }

    @Override
    public <E extends Throwable> BiOptional<Object, Object> filter(
        CheckedBiPredicate<Object, Object, E> predicate) {
      requireNonNull(predicate);
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")  // BiOptional is an immutable type.
    public <E extends Throwable> BiOptional<Object, Object> or(
        CheckedSupplier<? extends BiOptional<?, ?>, E> alternative) throws E {
      return (BiOptional<Object, Object>) (alternative.get());
    }

    @Override
    public <E extends Throwable> boolean match(CheckedBiPredicate<Object, Object, E> predicate) {
      requireNonNull(predicate);
      return false;
    }

    @Override
    public <E extends Throwable> void ifPresent(CheckedBiConsumer<Object, Object, E> consumer) {
      requireNonNull(consumer);
    }

    @Override
    public boolean isPresent() {
      return false;
    }

    @Override
    public BiStream<Object, Object> stream() {
      return BiStream.empty();
    }

    @Override
    public String toString() {
      return "empty()";
    }
  };

  private static final class Present<A, B> extends BiOptional<A, B> {
    private final A a;
    private final B b;

    Present(A a, B b) {
      this.a = requireNonNull(a);
      this.b = requireNonNull(b);
    }

    @Override
    public <T, E extends Throwable> Optional<T> join(
        CheckedBiFunction<? super A, ? super B, ? extends T, E> mapper)
        throws E {
      return Optional.ofNullable(mapper.apply(a, b));
    }

    @Override
    public <T, E extends Throwable> Optional<T> flatJoin(
        CheckedBiFunction<? super A, ? super B, Optional<T>, E> mapper)
        throws E {
      return requireNonNull(mapper.apply(a, b));
    }

    @Override
    public <A2, B2, E extends Throwable> BiOptional<A2, B2> flatMap(
        CheckedBiFunction<? super A, ? super B, BiOptional<A2, B2>, E> mapper) throws E {
      return requireNonNull(mapper.apply(a, b));
    }

    @Override
    public <E extends Throwable> BiOptional<A, B> filter(
        CheckedBiPredicate<? super A, ? super B, E> predicate)
        throws E {
      return match(predicate) ? this : empty();
    }

    @Override
    public <E extends Throwable> BiOptional<A, B> or(
        CheckedSupplier<? extends BiOptional<? extends A, ? extends B>, E> alternative) {
      requireNonNull(alternative);
      return this;
    }

    @Override
    public <E extends Throwable> boolean match(
        CheckedBiPredicate<? super A, ? super B, E> predicate) throws E {
      return predicate.test(a, b);
    }

    @Override
    public <E extends Throwable> void ifPresent(
        CheckedBiConsumer<? super A, ? super B, E> consumer) throws E {
      consumer.accept(a, b);
    }

    @Override
    public boolean isPresent() {
      return true;
    }

    @Override
    public BiStream<A, B> stream() {
      return BiStream.of(a, b);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Present<?, ?>) {
        Present<?, ?> that = (Present<?, ?>) obj;
        return a.equals(that.a) && b.equals(that.b);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(a, b);
    }

    @Override
    public String toString() {
      return "of(" + a + ", " + b + ")";
    }
  }

  BiOptional() {}
}

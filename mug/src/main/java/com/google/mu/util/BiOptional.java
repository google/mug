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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.mu.util.stream.BiStream;

/**
 * An optional pair of values; either the pair is present with both values, or is absent with no
 * value.
 *
 * <p>This is essentially an {@code Optional<Pair>} that users can access through fluent methods
 * like {@link #map}, {@link #filter}, {@link #or} instead of opaque names like
 * {@code first}, {@code second}.
 *
 * @since 5.0
 */
public abstract class BiOptional<A, B> {
  /** Returns an empty {@code BiOptional} instance. */
  @SuppressWarnings("unchecked") // EMPTY contains no A or B.
  public static <A, B> BiOptional<A, B> empty() {
    return (BiOptional<A, B>) EMPTY;
  }

  /**
   * Returns a {@code BiOptional} containing the pair {@code (a, b)}.
   *
   * @throws NullPointerException if either {@code a} or {@code b} is null
   */
  public static <A, B> BiOptional<A, B> of(A a, B b) {
    return new Present<>(requireNonNull(a), requireNonNull(b));
  }

  /** @deprecated Use {@link Optionals#both} instead. */
  @Deprecated
  public static <A, B> BiOptional<A, B> both(Optional<A> a, Optional<B> b) {
    return Optionals.both(a, b);
  }

  /**
   * If {@code optional} is present with value {@code v}, adapts it to {@code BiOptional} containing
   * values {@code (v, v)}.
   *
   * @throws NullPointerException if {@code optional} is null.
   */
  public static <T> BiOptional<T, T> from(Optional<T> optional) {
    return optional.isPresent() ? of(optional.get(), optional.get()) : empty();
  }

  /**
   * If a pair of values is present, apply {@code mapper} to them, and if the result is non-null,
   * return an {@code Optional} containing it. Otherwise return an empty {@code Optional}.
   *
   * @throws NullPointerException if {@code mapper} is null
   */
  public abstract <T> Optional<T> map(BiFunction<? super A, ? super B, ? extends T> mapper);

  /**
   * If a pair of values is present, apply {@code aMapper} and {@code bMapper} to them, and if both
   * results are non-null, return a new {@code BiOptional} instance containing the results.
   * Otherwise return an empty {@code BiOptional}.
   *
   * @throws NullPointerException if {@code aMapper} or {@code bMapper} is null
   */
  public abstract <A2, B2> BiOptional<A2, B2> map(
      BiFunction<? super A, ? super B, ? extends A2> aMapper,
      BiFunction<? super A, ? super B, ? extends B2> bMapper);

  /**
   * If a pair of values is present, apply {@code aMapper} and {@code bMapper} to each respectively,
   * and if both results are non-null, return a new {@code BiOptional} instance containing the
   * results. Otherwise return an empty {@code BiOptional}.
   *
   * <p>The following example parses and evaluates a numeric comparison:
   *
   * <pre>{@code
   * if (Substring.first('=')
   *     .splitThenTrim("10 = 20")
   *     .map(Ints::tryParse, Ints::tryParse)
   *     .filter((a, b) -> a.equals(b))
   *     .isPresent()) {
   *   // The two numbers are equal.
   * }
   * }</pre>
   *
   * @throws NullPointerException if {@code aMapper} or {@code bMapper} is null
   */
  public abstract <A2, B2> BiOptional<A2, B2> map(
      Function<? super A, ? extends A2> aMapper, Function<? super B, ? extends B2> bMapper);

  /**
   * If a pair of values is present, apply the {@code BiOptional}-bearing {@code mapper} to them,
   * and return that result. Otherwise, return an empty {@code BiOptional}.
   *
   * @throws NullPointerException if {@code mapper} is null or returns a null result
   */
  public abstract <T> Optional<T> flatMap(
      BiFunction<? super A, ? super B, ? extends Optional<? extends T>> mapper);

  /**
   * Maps the value contained in {@code optional} to a {@code BiOptional} using {@code mapper}, or
   * else returns empty. For example, the following code uses {@link Substring.Pattern#split} to
   * split an optional string:
   *
   * <pre>{@code
   * Optional<KeyValue> keyValue =
   *     BiOptional.flatMap(getOptionalInput(), Substring.first(':')::split)
   *         .map(KeyValue::new);
   * }</pre>
   *
   * <p>Use this method to bridge from an {@code Optional} to {@code BiOptional} chain.
   *
   * @throws NullPointerException if {@code optional} or {@code mapper} is null, or if {@code
   *     mapper} returns null.
   */
  public static <T, A, B> BiOptional<A, B> flatMap(
      Optional<T> optional,
      Function<? super T, ? extends BiOptional<? extends A, ? extends B>> mapper) {
    return optional.map(mapper).map(BiOptional::<A, B>covariant).orElse(empty());
  }

  /**
   * If a pair of values is present and matches {@code predicate}, return this {@code BiOptional
   * instance}. Otherwise, return an empty {@code BiOptional}.
   *
   * @throws NullPointerException if {@code predicate} is null
   */
  public abstract BiOptional<A, B> filter(BiPredicate<? super A, ? super B> predicate);

  /**
   * If a pair of values is present and matches {@code predicate}, the pair is skipped (returns empty).
   *
   * @throws NullPointerException if {@code predicate} is null
   * @since 6.6
   */
  public BiOptional<A, B> skipIf(BiPredicate<? super A, ? super B> predicate) {
    return filter(predicate.negate());
  }

  /**
   * Invokes {@code consumer} with the pair if present and returns this object as is.
   *
   * @throws NullPointerException if consumer is null
   * @since 5.1
   */
  public abstract BiOptional<A, B> peek(BiConsumer<? super A, ? super B> consumer);

  /**
   * Returns true if the pair exists and satisfies the {@code condition} predicate.
   *
   * @since 5.7
   */
  public abstract boolean matches(BiPredicate<? super A, ? super B> condition);

  /**
   * If a pair of values is present, invoke {@code consumer} with them. Otherwise, do nothing.
   *
   * @throws NullPointerException if consumer is null
   */
  public abstract void ifPresent(BiConsumer<? super A, ? super B> consumer);

  /** Returns true if the pair of values is present. */
  public abstract boolean isPresent();

  /**
   * If a pair of values are present, return this {@code BiOptional} instance. Otherwise, returns a
   * {@code BiOptional} produced by {@code alternative}.
   */
  public abstract BiOptional<A, B> or(
      Supplier<? extends BiOptional<? extends A, ? extends B>> alternative);

  /**
   * Returns the pair if present, or else returns {@code (a, b)}. {@code a} and {@code b}
   * are allowed to be null.
   *
   * @since 5.1
   */
  public abstract Both<A, B> orElse(A a, B b);

  /**
   * Ensures that the pair must be present or else throws {@link NoSuchElementException}.
   *
   * @since 5.1
   */
  public abstract Both<A, B> orElseThrow();

  /**
   * Ensures that the pair must be present or else throws the exception returned by
   * {@code exceptionSupplier}.
   *
   * @throws NullPointerException if {@code exceptionSupplier} is null, or returns null.
   * @throws E if the pair is absent.
   * @since 5.1
   */
  public abstract <E extends Throwable> Both<A, B> orElseThrow(Supplier<E> exceptionSupplier)
      throws E;

  /**
   * Ensures that the pair must be present or else throws {@link NoSuchElementException} with
   * {@code message} formatted with {@code args}.
   *
   * @throws NullPointerException if {@code message} is null, or if
   *     {@code exceptionFactory} returns null.
   * @throws NoSuchElementException if the pair is absent.
   * @since 7.2
   */
  public final <E extends Throwable> Both<A, B> orElseThrow(
      String message, Object... args) throws E {
    return orElseThrow(NoSuchElementException::new, message, args);
  }

  /**
   * Ensures that the pair must be present or else throws the exception returned by {@code
   * exceptionFactory} with {@code message} formatted with {@code args}.
   *
   * @throws NullPointerException if {@code exceptionFactory} or {@code message} is null, or if
   *     {@code exceptionFactory} returns null.
   * @throws E if the pair is absent.
   * @since 6.6
   */
  public final <E extends Throwable> Both<A, B> orElseThrow(
      Function<String, E> exceptionFactory, String message, Object... args) throws E {
    requireNonNull(exceptionFactory);
    requireNonNull(message);
    requireNonNull(args);
    if (isPresent()) {
      return orElseThrow();
    }
    throw exceptionFactory.apply(String.format(message, args));
  }

  /** Returns a {@code BiStream} view of this BiOptional. */
  public abstract BiStream<A, B> stream();

  @SuppressWarnings("unchecked") // BiOptional is an immutable type.
  static <A, B> BiOptional<A, B> covariant(BiOptional<? extends A, ? extends B> optional) {
    return (BiOptional<A, B>) requireNonNull(optional);
  }

  @SuppressWarnings("unchecked") // Optional is an immutable type.
  static <T> Optional<T> covariant(Optional<? extends T> optional) {
    return (Optional<T>) requireNonNull(optional);
  }

  private static final BiOptional<Object, Object> EMPTY =
      new BiOptional<Object, Object>() {
        @Override
        public <T> Optional<T> map(BiFunction<Object, Object, ? extends T> mapper) {
          requireNonNull(mapper);
          return Optional.empty();
        }

        @Override
        public <A2, B2> BiOptional<A2, B2> map(
            BiFunction<Object, Object, ? extends A2> aMapper,
            BiFunction<Object, Object, ? extends B2> bMapper) {
          requireNonNull(aMapper);
          requireNonNull(bMapper);
          return empty();
        }

        @Override
        public <A2, B2> BiOptional<A2, B2> map(
            Function<Object, ? extends A2> aMapper, Function<Object, ? extends B2> bMapper) {
          requireNonNull(aMapper);
          requireNonNull(bMapper);
          return empty();
        }

        @Override
        public <T> Optional<T> flatMap(
            BiFunction<Object, Object, ? extends Optional<? extends T>> mapper) {
          requireNonNull(mapper);
          return Optional.empty();
        }

        @Override
        public BiOptional<Object, Object> filter(BiPredicate<Object, Object> predicate) {
          requireNonNull(predicate);
          return this;
        }

        @Override
        public boolean matches(BiPredicate<Object, Object> condition) {
          requireNonNull(condition);
          return false;
        }

        @Override
        public BiOptional<Object, Object> or(Supplier<? extends BiOptional<?, ?>> alternative) {
          return covariant(alternative.get());
        }

        @Override
        public Both<Object, Object> orElse(Object a, Object b) {
          return new Present<>(a, b);
        }

        @Override
        public Both<Object, Object> orElseThrow() {
          throw new NoSuchElementException();
        }

        @Override
        public <E extends Throwable> Both<Object, Object> orElseThrow(Supplier<E> exceptionSupplier)
            throws E {
          throw exceptionSupplier.get();
        }

        @Override
        public BiOptional<Object, Object> peek(BiConsumer<Object, Object> consumer) {
          requireNonNull(consumer);
          return this;
        }

        @Override
        public void ifPresent(BiConsumer<Object, Object> consumer) {
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

  static final class Present<A, B> extends BiOptional<A, B> implements Both<A, B> {
    private final A a;
    private final B b;

    Present(A a, B b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public <T> Optional<T> map(BiFunction<? super A, ? super B, ? extends T> mapper) {
      return Optional.ofNullable(mapper.apply(a, b));
    }

    @Override
    public <A2, B2> BiOptional<A2, B2> map(
        BiFunction<? super A, ? super B, ? extends A2> aMapper,
        BiFunction<? super A, ? super B, ? extends B2> bMapper) {
      requireNonNull(aMapper);
      requireNonNull(bMapper);
      A2 a2 = aMapper.apply(a, b);
      if (a2 == null) {
        return empty();
      }
      B2 b2 = bMapper.apply(a, b);
      if (b2 == null) {
        return empty();
      }
      return of(a2, b2);
    }

    @Override
    public <A2, B2> BiOptional<A2, B2> map(
        Function<? super A, ? extends A2> aMapper, Function<? super B, ? extends B2> bMapper) {
      requireNonNull(aMapper);
      requireNonNull(bMapper);
      A2 a2 = aMapper.apply(a);
      if (a2 == null) {
        return empty();
      }
      B2 b2 = bMapper.apply(b);
      if (b2 == null) {
        return empty();
      }
      return of(a2, b2);
    }

    @Override
    public <T> Optional<T> flatMap(
        BiFunction<? super A, ? super B, ? extends Optional<? extends T>> mapper) {
      return covariant(mapper.apply(a, b));
    }

    @Override
    public BiOptional<A, B> filter(BiPredicate<? super A, ? super B> predicate) {
      return predicate.test(a, b) ? this : empty();
    }

    @Override
    public boolean matches(BiPredicate<? super A, ? super B> predicate) {
      return predicate.test(a, b);
    }

    @Override
    public BiOptional<A, B> or(
        Supplier<? extends BiOptional<? extends A, ? extends B>> alternative) {
      requireNonNull(alternative);
      return this;
    }

    @Override
    public Both<A, B> orElse(A a, B b) {
      return this;
    }

    @Override
    public Both<A, B> orElseThrow() {
      return this;
    }

    @Override
    public <E extends Throwable> Both<A, B> orElseThrow(Supplier<E> exceptionSupplier) {
      requireNonNull(exceptionSupplier);
      return this;
    }

    @Override
    public Present<A, B> peek(BiConsumer<? super A, ? super B> consumer) {
      consumer.accept(a, b);
      return this;
    }

    @Override
    public void ifPresent(BiConsumer<? super A, ? super B> consumer) {
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
        return Objects.equals(a, that.a) && Objects.equals(b, that.b);
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

    @Override
    public <T> T andThen(BiFunction<? super A, ? super B, T> combiner) {
      return combiner.apply(a, b);
    }
  }

  BiOptional() {}
}

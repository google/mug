package com.google.mu.function;

import static java.util.Objects.requireNonNull;

import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * A function that maps from type {@code long} to type {@code T} using {@link #present} when the optional
 * input is present, or else fall bacck to {@link #absent}.
 *
 * @since 5.3
 */
public interface OptionalLongFunction<T> {
  T present(long l);
  T longAbsent();

  /** Conveniently implement {@link OptionalLongFunction} using lambda or method reference. */
  static <T> OptionalLongFunction<T> with(
      LongFunction<? extends T> function, Supplier<? extends T> orElse) {
    requireNonNull(function);
    requireNonNull(orElse);
    return new OptionalLongFunction<T>() {
      @Override public T present(long l) {
        return function.apply(l);
      }
      @Override public T longAbsent() {
        return orElse.get();
      }
    };
  }
}

package com.google.mu.function;

import static java.util.Objects.requireNonNull;

import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * A function that maps from type {@code double} to type {@code T} using {@link #present} when the optional
 * input is present, or else fall bacck to {@link #absent}.
 *
 * @since 5.3
 */
public interface OptionalDoubleFunction<T> {
  T present(double d);
  T doubleAbsent();

  /** Conveniently implement {@link OptionalDoubleFunction} using lambda or method reference. */
  static <T> OptionalDoubleFunction<T> with(
      DoubleFunction<? extends T> function, Supplier<? extends T> orElse) {
    requireNonNull(function);
    requireNonNull(orElse);
    return new OptionalDoubleFunction<T>() {
      @Override public T present(double d) {
        return function.apply(d);
      }
      @Override public T doubleAbsent() {
        return orElse.get();
      }
    };
  }
}

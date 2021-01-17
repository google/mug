package com.google.mu.function;

import static java.util.Objects.requireNonNull;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A function that maps from type {@code int} to type {@code T} using {@link #present} when the optional
 * input is present, or else fall bacck to {@link #absent}.
 *
 * @since 5.3
 */
public interface OptionalIntFunction<T> {
  T present(int i);
  T intAbsent();

  /** Conveniently implement {@link OptionalIntFunction} using lambda or method reference. */
  static <T> OptionalIntFunction<T> with(
      IntFunction<? extends T> function, Supplier<? extends T> orElse) {
    requireNonNull(function);
    requireNonNull(orElse);
    return new OptionalIntFunction<T>() {
      @Override public T present(int i) {
        return function.apply(i);
      }
      @Override public T intAbsent() {
        return orElse.get();
      }
    };
  }
}

package com.google.mu.function;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A function that maps from type {@code F} to type {@code T} using {@link #present} when the optional
 * input is present, or else fall bacck to {@link #absent}.
 *
 * @since 5.3
 */
public interface OptionalFunction<F, T> {
  T present(F from);
  T absent();

  /** Conveniently implement {@link OptionalFunction} using lambda or method reference. */
  static <F, T> OptionalFunction<F, T> with(
      Function<? super F, ? extends T> function, Supplier<? extends T> orElse) {
    requireNonNull(function);
    requireNonNull(orElse);
    return new OptionalFunction<F, T>() {
      @Override public T present(F from) {
        return function.apply(from);
      }
      @Override public T absent() {
        return orElse.get();
      }
    };
  }
}

package com.google.mu.function;

/**
 * A function with one object and two int parameters.
 *
 * @since 10.6
 */
@FunctionalInterface
public interface ObjInt2Function<F, T> {
  T apply(F a, int b, int c);
}

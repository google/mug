package com.google.mu.function;

/**
 * A 3-arg function.
 *
 * @since 9.5
 */
@FunctionalInterface
public interface TriFunction<A, B, C, T> {
  T apply(A a, B b, C c);
}

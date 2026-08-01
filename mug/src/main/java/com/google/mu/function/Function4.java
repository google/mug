package com.google.mu.function;

/**
 * A 4-arg function.
 *
 * @since 9.5
 */
@FunctionalInterface
public interface Function4<A, B, C, D, T> {
  T apply(A a, B b, C c, D d);
}

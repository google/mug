package com.google.mu.function;

/** A 6-arg function of the signature of {@code (T, T, T, T, T, T, T) -> R}. */
public interface MapFrom7<T, R> {
  R apply(T a, T b, T c, T d, T e, T f, T g);
}
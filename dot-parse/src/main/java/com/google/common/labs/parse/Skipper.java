package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import com.google.mu.util.CharPredicate;

/** Strategy interface to skip input characters. */
@FunctionalInterface
interface Skipper {

  /**
   * Skips consecutive characters starting from {@code start} matching this skipper and returns the
   * ending index.
   */
  int skip(CharInput input, int start);

  static Skipper precomputed(CharPredicate predicate, long low64, long high64) {
    return (input, start) -> input.skipWhile(low64, high64, predicate, start);
  }

  /**
   * Returns a {@link Skipper} for {@code predicate} used in character skipping (e.g. whitespace),
   * pre-computing only the low 64 ASCII bits to minimize per-parse setup overhead.
   */
  static Skipper forLower64Ascii(CharPredicate predicate) {
    requireNonNull(predicate);
    long low = 0L;
    for (int i = 0; i < 64; i++) {
      if (predicate.test((char) i)) {
        low |= (1L << i);
      }
    }
    return precomputed(predicate, low, 0);
  }

  /**
   * Returns a {@link Skipper} for {@code predicate}, pre-computing ASCII bitmasks to optimize
   * scanning.
   */
  static Skipper from(CharPredicate predicate) {
    requireNonNull(predicate);
    long low = 0L;
    long high = 0L;
    for (int i = 0; i < 64; i++) {
      if (predicate.test((char) i)) {
        low |= (1L << i);
      }
      if (predicate.test((char) (i + 64))) {
        high |= (1L << i);
      }
    }
    return precomputed(predicate, low, high);
  }
}

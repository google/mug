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
    long low64 = low;
    long high64 = high;
    return (input, start) -> input.skipWhile(low64, high64, predicate, start);
  }
}

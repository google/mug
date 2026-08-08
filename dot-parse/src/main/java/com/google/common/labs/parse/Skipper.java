package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import com.google.mu.util.CharPredicate;

@FunctionalInterface
interface Skipper {
  /**
   * Skips chars from {@code input} starting from {@code index}, and returns the index after
   * skipping.
   */
  int skip(CharInput input, int index);

  static Skipper zeroOrMore(CharPredicate predicate) {
    requireNonNull(predicate);
    return (input, index) -> {
      while (input.startsWith(predicate, index)) index++;
      return index;
    };
  }

  static Skipper zeroOrMore(Parser<?> parser) {
    Parser<?> skip = parser.ignoreReturn();
    return (input, index) -> {
      for (; ; ) {
        switch (skip.tryParse(input, index, ErrorContext.MINIMAL)) {
          case MatchResult.Success<?> success -> index = success.tail();
          default -> {
            return index;
          }
        }
      }
    };
  }
}

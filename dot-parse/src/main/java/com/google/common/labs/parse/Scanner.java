package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import java.util.Set;

abstract class Scanner extends Parser<Void> {
  private final String name;

  Scanner(String name) {
    this.name = requireNonNull(name);
  }

  @Override final MatchResult<Void> skipAndMatch(
      Parser<?> skip, CharInput input, int start, ErrorContext context) {
    start = skipIfAny(skip, input, start);
    int end = scan(input, start);
    return end > start
        ? new MatchResult.Success<>(start, end, null)
        : context.expecting(name, start);
  }

  /** Matches one or more chars starting from {@code index} and returns the ending index. */
  abstract int scan(CharInput input, int from);

  @Override Set<String> getExpectedSymbols() {
    return Set.of(name);
  }
}

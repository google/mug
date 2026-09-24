package com.google.common.labs.parse;

import static java.util.Objects.requireNonNull;

import java.util.Set;

abstract class Scanner extends Parser<Void> {
  private final String name;

  Scanner(String name) {
    this.name = requireNonNull(name);
  }

  @Override final MatchResult<Void> skipAndMatch(
      Skipper preskipper, Skipper innerSkipper, CharInput input, int start, ErrorContext context) {
    start = Parser.skipIfAny(preskipper, input, start);
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

package com.google.common.labs.parse;

class ErrorContext {
  static final ErrorContext MINIMAL = new ErrorContext();

  /** Note down that {@code symbol} was missing at the specified index. */
  void missing(String symbolName, int at) {}

  final <V> MatchResult.Failure<V> expecting(String symbolName, int at) {
    return expecting(symbolName, at, at);
  }

  final <V> MatchResult.Failure<V> expectingInternal(Object symbol, int at) {
    return expectingInternal(symbol, at, at);
  }

  <V> MatchResult.Failure<V> expecting(String symbolName, int at, long frontier) {
    return failAt(at, frontier, "expecting <{name}>.", symbolName);
  }

  <V> MatchResult.Failure<V> expectingInternal(Object symbol, int at, long frontier) {
    return failAt(at, frontier, "expecting {name}.", symbol);
  }

  final <V> MatchResult.Failure<V> failAt(int at, String messageTemplate, Object symbol) {
    return failAt(at, at, messageTemplate, symbol);
  }

  <V> MatchResult.Failure<V> failAt(int at, long frontier, String messageTemplate, Object symbol) {
    return new MatchResult.Failure<V>(at, frontier, messageTemplate, symbol);
  }

  final <V> MatchResult.Failure<V> errorAt(int at, long frontier, ParseError error) {
    return failAt(at, frontier | (1L << 32), "{name}\n{snippet}", error.getMessage());
  }

  static final class ErrorTracker extends ErrorContext {
    private static final String EXPECTING = "expecting <{name}>, encountered:{snippet}";
    private MatchResult.Failure<?> farthestFailure = null;

    @Override void missing(String symbolName, int at) {
      if (isFarthest(at)) {
        farthestFailure = new MatchResult.Failure<>(at, at, EXPECTING, symbolName);
      }
    }

    @Override <V> MatchResult.Failure<V> expecting(String symbolName, int at, long frontier) {
      return failAt(at, frontier, EXPECTING, symbolName);
    }

    @Override <V> MatchResult.Failure<V> expectingInternal(Object symbol, int at, long frontier) {
      return failAt(at, frontier, "expecting {name}, encountered:{snippet}", symbol);
    }

    @Override <V> MatchResult.Failure<V> failAt(
        int at, long frontier, String messageTemplate, Object symbol) {
      var failure = new MatchResult.Failure<V>(at, frontier, messageTemplate, symbol);
      // prefer the farthest then the most recent failure
      if (isFarthest(frontier)) {
        farthestFailure = failure;
      }
      return failure;
    }

    Parser.ParseException report(MatchResult.Failure<?> failure, CharInput input) {
      return isFarthest(failure.frontier())
          ? failure.toException(input)
          : farthestFailure.toException(input);
    }

    private boolean isFarthest(long index) {
      return farthestFailure == null || index >= farthestFailure.frontier();
    }
  }
}

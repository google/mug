package com.google.common.labs.parse;

class ErrorContext {
  static final ErrorContext MINIMAL = new ErrorContext();

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
    private MatchResult.Failure<?> farthestFailure = null;

    @Override <V> MatchResult.Failure<V> expecting(String symbolName, int at, long frontier) {
      return failAt(at, frontier, "expecting <{name}>, encountered:{snippet}", symbolName);
    }

    @Override <V> MatchResult.Failure<V> expectingInternal(Object symbol, int at, long frontier) {
      return failAt(at, frontier, "expecting {name}, encountered:{snippet}", symbol);
    }

    @Override <V> MatchResult.Failure<V> failAt(
        int at, long frontier, String messageTemplate, Object symbol) {
      MatchResult.Failure<V> failure = super.failAt(at, frontier, messageTemplate, symbol);
      // prefer the farthest then the most recent failure
      if (farthestFailure == null || failure.frontier() >= farthestFailure.frontier()) {
        farthestFailure = failure;
      }
      return failure;
    }

    Parser.ParseException report(MatchResult.Failure<?> failure, CharInput input) {
      return (farthestFailure == null || failure.frontier() >= farthestFailure.frontier())
          ? failure.toException(input)
          : farthestFailure.toException(input);
    }
  }
}

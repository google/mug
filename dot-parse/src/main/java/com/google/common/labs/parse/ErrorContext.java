package com.google.common.labs.parse;

/**
 * Writes failures to the {@link MatchResult}. The failure methods always return false, so that a
 * parser can {@code return context.expecting(...)}.
 */
class ErrorContext {
  static final ErrorContext MINIMAL = new ErrorContext();

  /** Note down that {@code symbol} was missing at the specified index. */
  void missed(String symbolName, int at) {}

  final boolean expecting(String symbolName, int at, MatchResult result) {
    return expecting(symbolName, at, at, result);
  }

  final boolean expectingInternal(Object symbol, int at, MatchResult result) {
    return expectingInternal(symbol, at, at, result);
  }

  boolean expecting(String symbolName, int at, long frontier, MatchResult result) {
    return failAt(at, frontier, "expecting <{name}>.", symbolName, result);
  }

  boolean expectingInternal(Object symbol, int at, long frontier, MatchResult result) {
    return failAt(at, frontier, "expecting {name}.", symbol, result);
  }

  final boolean failAt(int at, String messageTemplate, Object symbol, MatchResult result) {
    return failAt(at, at, messageTemplate, symbol, result);
  }

  boolean failAt(int at, long frontier, String messageTemplate, Object symbol, MatchResult result) {
    return result.fail(at, frontier, messageTemplate, symbol);
  }

  final boolean errorAt(int at, long frontier, ParseError error, MatchResult result) {
    return failAt(at, frontier | (1L << 32), "{name}\n{snippet}", error.getMessage(), result);
  }

  static final class ErrorTracker extends ErrorContext {
    private static final String EXPECTING = "expecting <{name}>, encountered:{snippet}";
    private int farthestAt;
    private long farthestFrontier = -1;
    private String farthestTemplate;
    private Object farthestSymbol;

    @Override void missed(String symbolName, int at) {
      if (isFarthest(at)) {
        trackFarthest(at, at, EXPECTING, symbolName);
      }
    }

    @Override boolean expecting(String symbolName, int at, long frontier, MatchResult result) {
      return failAt(at, frontier, EXPECTING, symbolName, result);
    }

    @Override boolean expectingInternal(Object symbol, int at, long frontier, MatchResult result) {
      return failAt(at, frontier, "expecting {name}, encountered:{snippet}", symbol, result);
    }

    @Override boolean failAt(
        int at, long frontier, String messageTemplate, Object symbol, MatchResult result) {
      // prefer the farthest then the most recent failure
      if (isFarthest(frontier)) {
        trackFarthest(at, frontier, messageTemplate, symbol);
      }
      return result.fail(at, frontier, messageTemplate, symbol);
    }

    Parser.ParseException report(MatchResult failure, CharInput input) {
      return isFarthest(failure.frontier())
          ? failure.toException(input)
          : MatchResult.toException(input, farthestAt, farthestTemplate, farthestSymbol);
    }

    private boolean isFarthest(long index) {
      return index >= farthestFrontier;
    }

    private void trackFarthest(int at, long frontier, String messageTemplate, Object symbol) {
      farthestAt = at;
      farthestFrontier = frontier;
      farthestTemplate = messageTemplate;
      farthestSymbol = symbol;
    }
  }
}

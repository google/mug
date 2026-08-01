package com.google.common.labs.parse;

final class ParseError extends Error {
  ParseError(String message) {
    super(
        message, null,
        /* enableSuppression= */ debugMode(),
        /* writableStackTrace= */ debugMode());
  }

  @SuppressWarnings("OverrideThrowableToString")
  @Override public String toString() {
    return "ParseError isn't expected to be caught or propagated outside of the Parser"
        + " framework.";
  }

  @SuppressWarnings("AssignmentExpression")
  private static boolean debugMode() {
    boolean debugMode = false;
    assert debugMode = true;
    return debugMode;
  }
}
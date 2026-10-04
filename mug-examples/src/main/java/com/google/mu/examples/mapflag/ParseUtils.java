package com.google.mu.examples.mapflag;

public final class ParseUtils {
  public static Number toNumber(String s) {
    // (Number) cast prevents ternary numeric promotion from widening Integer to double.
    return s.contains(".") ? (Number) Double.parseDouble(s) : Integer.parseInt(s);
  }
}

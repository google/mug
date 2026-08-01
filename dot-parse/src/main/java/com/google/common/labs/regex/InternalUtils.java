package com.google.common.labs.regex;

import com.google.errorprone.annotations.FormatMethod;

final class InternalUtils {
  @FormatMethod
  static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  @FormatMethod
  static void checkState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, args));
    }
  }
}

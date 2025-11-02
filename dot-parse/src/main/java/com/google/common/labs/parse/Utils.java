package com.google.common.labs.parse;

class Utils {
  static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  static void checkState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  static int checkPositionIndex(int index, int size, String name) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(
         String.format("%s (%s) must be in range of [0, %s]", name, index, size));
    }
    return index;
  }
}

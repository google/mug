package com.google.common.labs.parse;

import static java.lang.Math.min;

import java.util.LinkedHashSet;
import java.util.Set;

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
         String.format("%s (%s) must be in range of [0..%s]", name, index, size));
    }
    return index;
  }

  static Set<String> caseInsensitivePrefixes(String string, int maxLength) {
    checkArgument(maxLength >= 0, "maxLength (%s) must not be negative", maxLength);
    int chars = min(maxLength, string.length());
    Set<String> prefixes = new LinkedHashSet<>();
    char[] buffer = new char[chars];
    new Object() {
      void from(int index) {
        if (index >= chars) {
          prefixes.add(new String(buffer));
          return;
        }
        char c = string.charAt(index);
        buffer[index] = Character.toLowerCase(c);
        from(index + 1);
        buffer[index] = Character.toUpperCase(c);
        from(index + 1);
      }
    }.from(0);
    return prefixes;
  }
}

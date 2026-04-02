package com.google.common.labs.parse;

import static java.lang.Math.min;
import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.stream.Iteration;

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
    class Enumeration extends Iteration<String> {
      private final char[] buffer = new char[chars];

      @CanIgnoreReturnValue
      Enumeration from(int index) {
        if (index >= chars) {
          emit(new String(buffer));
          return this;
        }
        char c = string.charAt(index);
        buffer[index] = Character.toLowerCase(c);
        from(index + 1);
        lazily(() -> {
          buffer[index] = Character.toUpperCase(c);
          from(index + 1);
        });
        return this;
      }
    }
    return new Enumeration().from(0).iterate().collect(toCollection(LinkedHashSet::new));
  }
}

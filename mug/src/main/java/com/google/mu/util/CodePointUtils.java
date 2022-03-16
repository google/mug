package com.google.mu.util;

import static java.util.Spliterator.ORDERED;

import java.util.Spliterators.AbstractIntSpliterator;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

final class CodePointUtils {
  /** Returns the index of the last code point from {@code s}, or -1 if {@code s} is empty. */
  static int lastCodePointIndex(CharSequence s) {
    if (s.length() < 2) {
      return s.length() - 1;
    }
    if (s instanceof String) {
      String str = (String) s;
      return str.length()  - Character.charCount(str.codePointAt(str.length() - 2));
    } else {
      return s.length() - Character.charCount(Character.codePointAt(s, s.length() - 2));
    }
  }

  static IntStream codePointIndexes(CharSequence s, int fromIndex) {
    return StreamSupport.intStream(
        new AbstractIntSpliterator(s.length() - fromIndex, ORDERED) {
          int index = fromIndex;
          @Override public boolean tryAdvance(IntConsumer consumer) {
            if (index >= s.length()) return false;
            int ch = Character.codePointAt(s, index);
            consumer.accept(index);
            index += Character.charCount(ch);
            return true;
          }
        },
        /*parallel=*/ false);
  }

  static IntStream codePointIndexes(CharSequence s) {
    return codePointIndexes(s, 0);
  }

  static IntStream backwardCodePointIndexes(CharSequence s) {
    return backwardCodePointIndexes(s, 0);
  }

  static IntStream backwardCodePointIndexes(CharSequence s, int fromIndex) {
    return StreamSupport.intStream(
        new AbstractIntSpliterator(s.length() - fromIndex, ORDERED) {
          int index = lastCodePointIndex(s);
          @Override public boolean tryAdvance(IntConsumer consumer) {
            if (index < fromIndex) return false;
            consumer.accept(index);
            if (index == fromIndex) {
              index--;
            } else {
              index -= Character.charCount(Character.codePointBefore(s, index));
            }
            return true;
          }
        },
        /*parallel=*/ false);
  }
}

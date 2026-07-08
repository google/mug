package com.google.mu.util;

import java.util.Arrays;

abstract class CharacterRangeSet implements CharPredicate {
  abstract String getRangeString();

  static abstract class Computed extends CharacterRangeSet {
    private String rangeString;

    abstract String computeRangeString();

    @Override final String getRangeString() {
      String result = rangeString;
      if (result == null) {
        rangeString = result = computeRangeString();
      }
      return result;
    }
  }

  @Override public final String characterRangeSet() {
    String rangeString = getRangeString();
    if (rangeString == null) {
      return "";
    }
    if (rangeString.isEmpty()) {
      return "[]";
    }
    String prefix = "[";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < rangeString.length(); i += 2) {
      char start = rangeString.charAt(i);
      char end = rangeString.charAt(i + 1);
      if (start == '-' && end == '-') {
        prefix = "[-";
        continue;
      }
      if (start == end) {
        builder.append(escapeChar(start));
      } else if (start + 1 == end) {
        builder.append(escapeChar(start)).append(escapeChar(end));
      } else {
        builder.append(escapeChar(start)).append('-').append(escapeChar(end));
      }
    }
    return prefix + builder + "]";
  }

  @Override public String toString() {
    return characterRangeSet();
  }

  static final class Alpha extends CharacterRangeSet {
    @Override String getRangeString() {
      return "AZaz";
    }

    @Override public boolean test(char c) {
      return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    @Override public String toString() {
      return "ALPHA";
    }
  }

  static final class Word extends CharacterRangeSet {
    @Override String getRangeString() {
      return "09AZ__az";
    }

    @Override public boolean test(char c) {
      return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    @Override public String toString() {
      return "WORD";
    }
  }

  static final class Ascii extends CharacterRangeSet {
    @Override String getRangeString() {
      return "\u0000\u007F";
    }

    @Override public boolean test(char c) {
      return c < 128;
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return "ASCII";
    }
  }

  static final class Any extends CharacterRangeSet {
    @Override String getRangeString() {
      return "\u0000\uFFFF";
    }

    @Override public boolean test(char c) {
      return true;
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return "ANY";
    }
  }

  static final class None extends CharacterRangeSet {
    @Override String getRangeString() {
      return "";
    }

    @Override public boolean test(char c) {
      return false;
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return "NONE";
    }
  }


  static final class Single extends CharacterRangeSet {
    private final char ch;

    Single(char ch) {
      this.ch = ch;
    }

    @Override String getRangeString() {
      return "" + ch + ch;
    }

    @Override public boolean test(char c) {
      return c == ch;
    }

    @Override public String toString() {
      return "'" + ch + "'";
    }
  }

  static final class Range extends CharacterRangeSet {
    private final char from;
    private final char to;

    Range(char from, char to) {
      this.from = from;
      this.to = to;
    }

    @Override String getRangeString() {
      return from <= to ? "" + from + to : "";
    }

    @Override public boolean test(char c) {
      return c >= from && c <= to;
    }

    @Override public String toString() {
      return "['" + from + "', '" + to + "']";
    }
  }

  static final class AnyOf extends CharacterRangeSet {
    private final char[] array;

    AnyOf(String chars) {
      this.array = chars.toCharArray();
      Arrays.sort(array);
    }

    @Override String getRangeString() {
      StringBuilder builder = new StringBuilder(array.length * 2);
      for (char c : array) {
        builder.append(c).append(c);
      }
      return coalesce(builder);
    }

    @Override public boolean test(char c) {
      return Arrays.binarySearch(array, c) >= 0;
    }

    private static String coalesce(CharSequence rangeString) {
      return rangeString.length() <= 2
          ? rangeString.toString()
          : unionRangeStrings(rangeString, rangeString.subSequence(0, 2));
    }
  }

  static final class Or extends Computed {
    private final CharPredicate a;
    private final CharPredicate b;

    Or(CharPredicate a, CharPredicate b) {
      this.a = a;
      this.b = b;
    }

    @Override String computeRangeString() {
      String aString = rangeStringOf(a);
      String bString = rangeStringOf(b);
      return aString == null || bString == null ? null : unionRangeStrings(aString, bString);
    }

    @Override public boolean test(char c) {
      return a.test(c) || b.test(c);
    }

    @Override public String toString() {
      String characterClass = characterRangeSet();
      return characterClass.isEmpty() ? a + " | " + b : characterClass;
    }
  }

  static final class And extends Computed {
    private final CharPredicate a;
    private final CharPredicate b;

    And(CharPredicate a, CharPredicate b) {
      this.a = a;
      this.b = b;
    }

    @Override String computeRangeString() {
      String aRangeString = rangeStringOf(a);
      String bRangeString = rangeStringOf(b);
      if (aRangeString == null || bRangeString == null) {
        return null;
      }
      if (aRangeString.isEmpty() || bRangeString.isEmpty()) return "";
      StringBuilder builder = new StringBuilder();
      for (int i = 0, j = 0; i < aRangeString.length() && j < bRangeString.length(); ) {
        char as = aRangeString.charAt(i), ae = aRangeString.charAt(i + 1);
        char bs = bRangeString.charAt(j), be = bRangeString.charAt(j + 1);
        char start = (char) Math.max(as, bs);
        char end = (char) Math.min(ae, be);
        if (start <= end) {
          builder.append(start).append(end);
        }
        if (ae < be) {
          i += 2;
        } else if (be < ae) {
          j += 2;
        } else {
          i += 2;
          j += 2;
        }
      }
      return builder.toString();
    }

    @Override public boolean test(char c) {
      return a.test(c) && b.test(c);
    }

    @Override public String toString() {
      String characterClass = characterRangeSet();
      return characterClass.isEmpty() ? a + " & " + b : characterClass;
    }
  }

  static final class Negation extends Computed {
    private final CharPredicate predicate;

    Negation(CharPredicate predicate) {
      this.predicate = predicate;
    }

    @Override String computeRangeString() {
      String base = rangeStringOf(predicate);
      if (base == null) {
        return null;
      }
      StringBuilder builder = new StringBuilder();
      char current = 0;
      for (int i = 0; i < base.length(); i += 2) {
        char start = base.charAt(i);
        char end = base.charAt(i + 1);
        if (start > current) {
          builder.append(current).append((char) (start - 1));
        }
        current = (char) (end + 1);
        if (end == 0xFFFF) {
          break;
        }
      }
      if (base.isEmpty() || base.charAt(base.length() - 1) < 0xFFFF) {
        builder.append(current).append((char) 0xFFFF);
      }
      return builder.toString();
    }

    @Override public boolean test(char c) {
      return !predicate.test(c);
    }

    @Override public CharPredicate not() {
      return predicate;
    }

    @Override public String toString() {
      String characterClass = characterRangeSet();
      return characterClass.isEmpty() ? "not (" + predicate + ")" : characterClass;
    }
  }

  static final class PrecomputedForAscii extends CharacterRangeSet {
    private final CharPredicate base;
    private final long low64;
    private final long high64;

    PrecomputedForAscii(CharPredicate base) {
      this.base = base;
      this.low64 = computeMask(base, 0);
      this.high64 = computeMask(base, 64);
    }


    @Override public boolean test(char c) {
      if (c < 64) {
        return ((low64 >>> c) & 1L) != 0;
      }
      if (c < 128) {
        return ((high64 >>> (c - 64)) & 1L) != 0;
      }
      return base.test(c);
    }

    @Override String getRangeString() {
      return rangeStringOf(base);
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return base.toString();
    }

    private static long computeMask(CharPredicate predicate, int offset) {
      long mask = 0L;
      for (int i = 0; i < 64; i++) {
        if (predicate.test((char) (offset + i))) {
          mask |= (1L << i);
        }
      }
      return mask;
    }
  }

  private static String unionRangeStrings(CharSequence aRangeString, CharSequence bRangeString) {
    if (aRangeString.isEmpty()) return bRangeString.toString();
    if (bRangeString.isEmpty()) return aRangeString.toString();

    StringBuilder result = new StringBuilder();
    int i = 0, j = 0;
    char currentStart, currentEnd;
    if (aRangeString.charAt(0) <= bRangeString.charAt(0)) {
      currentStart = aRangeString.charAt(0);
      currentEnd = aRangeString.charAt(1);
      i = 2;
    } else {
      currentStart = bRangeString.charAt(0);
      currentEnd = bRangeString.charAt(1);
      j = 2;
    }

    while (i < aRangeString.length() || j < bRangeString.length()) {
      char nextStart, nextEnd;
      if (i < aRangeString.length()
          && (j >= bRangeString.length() || aRangeString.charAt(i) <= bRangeString.charAt(j))) {
        nextStart = aRangeString.charAt(i);
        nextEnd = aRangeString.charAt(i + 1);
        i += 2;
      } else {
        nextStart = bRangeString.charAt(j);
        nextEnd = bRangeString.charAt(j + 1);
        j += 2;
      }
      if (nextStart <= currentEnd + 1) {
        currentEnd = (char) Math.max(currentEnd, nextEnd);
      } else {
        result.append(currentStart).append(currentEnd);
        currentStart = nextStart;
        currentEnd = nextEnd;
      }
    }

    result.append(currentStart).append(currentEnd);
    return result.toString();
  }

  private static String rangeStringOf(CharPredicate predicate) {
    return predicate instanceof CharacterRangeSet
        ? ((CharacterRangeSet) predicate).getRangeString()
        : null;
  }

  private static String escapeChar(int c) {
    switch (c) {
      case '\r': return "\\r";
      case '\n': return "\\n";
      case '\t': return "\\t";
      case '\f': return "\\f";
      case '\b': return "\\b";
      case '\\': return "\\\\";
      default:
        if (c < 0x20 || c >= 127) {
          return String.format("\\u%04X", c);
        }
        return Character.toString((char) c);
    }
  }
}

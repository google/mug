package com.google.mu.errorprone.regex;

import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of a set of character code points, stored as a sorted list of disjoint,
 * inclusive intervals [start, end].
 */
final class CharRanges {
  private static final int MAX_CODE_POINT = Character.MAX_CODE_POINT;

  private static final CharRanges EMPTY = new CharRanges(Collections.<Range>emptyList());
  private static final CharRanges ANY =
      new CharRanges(Collections.singletonList(new Range(0, MAX_CODE_POINT)));

  private final List<Range> ranges;

  static final class Range {
    final int start;
    final int end;

    Range(int start, int end) {
      if (start > end) {
        throw new IllegalArgumentException("start (" + start + ") > end (" + end + ")");
      }
      this.start = start;
      this.end = end;
    }

    boolean contains(int c) {
      return c >= start && c <= end;
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof Range) {
        Range other = (Range) obj;
        return this.start == other.start && this.end == other.end;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hash(start, end);
    }

    @Override public String toString() {
      return "[" + start + ", " + end + "]";
    }
  }

  private CharRanges(List<Range> ranges) {
    this.ranges = ranges;
  }

  public static CharRanges empty() {
    return EMPTY;
  }

  public static CharRanges any() {
    return ANY;
  }

  public static CharRanges of(int codePoint) {
    return range(codePoint, codePoint);
  }

  public static CharRanges range(int start, int end) {
    if (start > end) {
      return EMPTY;
    }
    return new CharRanges(Collections.singletonList(new Range(start, end)));
  }

  public boolean isEmpty() {
    return ranges.isEmpty();
  }

  public List<Range> ranges() {
    return ranges;
  }

  public boolean contains(int codePoint) {
    int low = 0;
    int high = ranges.size() - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      Range r = ranges.get(mid);
      if (r.contains(codePoint)) {
        return true;
      }
      if (codePoint < r.start) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
    return false;
  }

  public boolean intersects(CharRanges other) {
    return !intersection(other).isEmpty();
  }

  public CharRanges union(CharRanges other) {
    if (this.isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    List<Range> combined = new ArrayList<>(this.ranges.size() + other.ranges.size());
    combined.addAll(this.ranges);
    combined.addAll(other.ranges);
    Collections.sort(combined, (a, b) -> Integer.compare(a.start, b.start));

    List<Range> merged = new ArrayList<>();
    Range current = combined.get(0);
    for (int i = 1; i < combined.size(); i++) {
      Range next = combined.get(i);
      if (next.start <= current.end + 1) {
        current = new Range(current.start, Math.max(current.end, next.end));
      } else {
        merged.add(current);
        current = next;
      }
    }
    merged.add(current);
    return new CharRanges(Collections.unmodifiableList(merged));
  }

  public CharRanges intersection(CharRanges other) {
    if (this.isEmpty() || other.isEmpty()) {
      return EMPTY;
    }
    List<Range> result = new ArrayList<>();
    int i = 0;
    int j = 0;
    while (i < this.ranges.size() && j < other.ranges.size()) {
      Range a = this.ranges.get(i);
      Range b = other.ranges.get(j);
      int start = Math.max(a.start, b.start);
      int end = Math.min(a.end, b.end);
      if (start <= end) {
        result.add(new Range(start, end));
      }
      if (a.end < b.end) {
        i++;
      } else {
        j++;
      }
    }
    return result.isEmpty() ? EMPTY : new CharRanges(Collections.unmodifiableList(result));
  }

  public CharRanges complement() {
    if (this.isEmpty()) {
      return ANY;
    }
    List<Range> result = new ArrayList<>();
    int current = 0;
    for (Range r : ranges) {
      if (r.start > current) {
        result.add(new Range(current, r.start - 1));
      }
      current = r.end + 1;
    }
    if (current <= MAX_CODE_POINT) {
      result.add(new Range(current, MAX_CODE_POINT));
    }
    return result.isEmpty() ? EMPTY : new CharRanges(Collections.unmodifiableList(result));
  }

  public static CharRanges from(RegexPattern.CharSetElement element) {
    if (element instanceof RegexPattern.LiteralChar) {
      return of(((RegexPattern.LiteralChar) element).value());
    }
    if (element instanceof RegexPattern.CharRange) {
      RegexPattern.CharRange cr = (RegexPattern.CharRange) element;
      return range(cr.start(), cr.end());
    }
    if (element instanceof RegexPattern.PredefinedCharClass) {
      return from((RegexPattern.PredefinedCharClass) element);
    }
    if (element instanceof RegexPattern.PosixCharClass) {
      return from((RegexPattern.PosixCharClass) element);
    }
    if (element instanceof RegexPattern.CharacterProperty.Negated) {
      return from(((RegexPattern.CharacterProperty.Negated) element).property()).complement();
    }
    if (element instanceof RegexPattern.UnicodeProperty) {
      return fromUnicodeProperty(((RegexPattern.UnicodeProperty) element).propertyName());
    }
    return any();
  }

  public static CharRanges from(RegexPattern.CharacterSet characterSet) {
    if (characterSet instanceof RegexPattern.CharacterSet.AnyOf) {
      RegexPattern.CharacterSet.AnyOf anyOf = (RegexPattern.CharacterSet.AnyOf) characterSet;
      CharRanges result = EMPTY;
      for (RegexPattern.CharSetElement e : anyOf.elements()) {
        result = result.union(from(e));
      }
      return result;
    }
    if (characterSet instanceof RegexPattern.CharacterSet.NoneOf) {
      RegexPattern.CharacterSet.NoneOf noneOf = (RegexPattern.CharacterSet.NoneOf) characterSet;
      CharRanges inner = EMPTY;
      for (RegexPattern.CharSetElement e : noneOf.elements()) {
        inner = inner.union(from(e));
      }
      return inner.complement();
    }
    return any();
  }

  public static CharRanges from(RegexPattern.PredefinedCharClass pcc) {
    switch (pcc) {
      case ANY_CHAR:
        return any().intersection(of('\n').union(of('\r')).complement());
      case DIGIT:
        return range('0', '9');
      case NON_DIGIT:
        return range('0', '9').complement();
      case WHITESPACE:
        return of(' ').union(of('\t'))
            .union(of('\n'))
            .union(of('\r'))
            .union(of('\f'))
            .union(of(0x0B));
      case NON_WHITESPACE:
        return from(RegexPattern.PredefinedCharClass.WHITESPACE).complement();
      case WORD:
        return range('a', 'z').union(range('A', 'Z')).union(range('0', '9')).union(of('_'));
      case NON_WORD:
        return from(RegexPattern.PredefinedCharClass.WORD).complement();
    }
    return any();
  }

  public static CharRanges from(RegexPattern.PosixCharClass pcc) {
    switch (pcc) {
      case LOWER:
        return range('a', 'z');
      case UPPER:
        return range('A', 'Z');
      case ASCII:
        return range(0, 0x7F);
      case ALPHA:
        return range('a', 'z').union(range('A', 'Z'));
      case DIGIT:
        return range('0', '9');
      case ALNUM:
        return range('a', 'z').union(range('A', 'Z')).union(range('0', '9'));
      case PUNCT:
        {
          CharRanges punct = EMPTY;
          String chars = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
          for (int i = 0; i < chars.length(); i++) {
            punct = punct.union(of(chars.charAt(i)));
          }
          return punct;
        }
      case GRAPH:
        return range(0x21, 0x7E);
      case PRINT:
        return range(0x20, 0x7E);
      case BLANK:
        return of(' ').union(of('\t'));
      case CNTRL:
        return range(0, 0x1F).union(of(0x7F));
      case XDIGIT:
        return range('0', '9').union(range('a', 'f')).union(range('A', 'F'));
      case SPACE:
        return of(' ').union(of('\t'))
            .union(of('\n'))
            .union(of('\r'))
            .union(of('\f'))
            .union(of(0x0B));
    }
    return any();
  }

  private static CharRanges fromUnicodeProperty(String name) {
    if ("Nd".equalsIgnoreCase(name) || "Digit".equalsIgnoreCase(name)) {
      return range('0', '9');
    }
    if ("L".equalsIgnoreCase(name) || "Letter".equalsIgnoreCase(name)) {
      return range('a', 'z').union(range('A', 'Z'));
    }
    return any();
  }

  @Override public boolean equals(Object obj) {
    if (obj instanceof CharRanges) {
      CharRanges other = (CharRanges) obj;
      return this.ranges.equals(other.ranges);
    }
    return false;
  }

  @Override public int hashCode() {
    return ranges.hashCode();
  }

  @Override public String toString() {
    return ranges.toString();
  }
}

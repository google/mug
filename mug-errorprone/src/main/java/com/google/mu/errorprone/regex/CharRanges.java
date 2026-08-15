package com.google.mu.errorprone.regex;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.labs.regex.RegexPattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable representation of a set of character code points, stored as a sorted list of disjoint,
 * inclusive intervals [start, end].
 */
record CharRanges(List<Range> ranges) {
  private static final int MAX_CODE_POINT = Character.MAX_CODE_POINT;

  private static final CharRanges EMPTY = new CharRanges(Collections.<Range>emptyList());
  private static final CharRanges ANY =
      new CharRanges(Collections.singletonList(new Range(0, MAX_CODE_POINT)));

  record Range(int start, int end) {
    Range {
      checkArgument(start <= end, "start (%s) > end (%s)", start, end);
    }

    boolean contains(int c) {
      return c >= start && c <= end;
    }

    @Override public String toString() {
      return "[" + start + ", " + end + "]";
    }
  }

  static CharRanges empty() {
    return EMPTY;
  }

  static CharRanges any() {
    return ANY;
  }

  static CharRanges of(int codePoint) {
    return range(codePoint, codePoint);
  }

  static CharRanges range(int start, int end) {
    if (start > end) {
      return EMPTY;
    }
    return new CharRanges(Collections.singletonList(new Range(start, end)));
  }

  boolean isEmpty() {
    return ranges.isEmpty();
  }

  boolean contains(int codePoint) {
    int low = 0;
    int high = ranges.size() - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      Range r = ranges.get(mid);
      if (r.contains(codePoint)) {
        return true;
      }
      if (codePoint < r.start()) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
    return false;
  }

  boolean intersects(CharRanges other) {
    return !intersection(other).isEmpty();
  }

  CharRanges union(CharRanges other) {
    if (this.isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    List<Range> combined = new ArrayList<>(this.ranges.size() + other.ranges.size());
    combined.addAll(this.ranges);
    combined.addAll(other.ranges);
    Collections.sort(combined, (a, b) -> Integer.compare(a.start(), b.start()));

    List<Range> merged = new ArrayList<>();
    Range current = combined.get(0);
    for (int i = 1; i < combined.size(); i++) {
      Range next = combined.get(i);
      if (next.start() <= current.end() + 1) {
        current = new Range(current.start(), Math.max(current.end(), next.end()));
      } else {
        merged.add(current);
        current = next;
      }
    }
    merged.add(current);
    return new CharRanges(Collections.unmodifiableList(merged));
  }

  CharRanges intersection(CharRanges other) {
    if (this.isEmpty() || other.isEmpty()) {
      return EMPTY;
    }
    List<Range> result = new ArrayList<>();
    int i = 0;
    int j = 0;
    while (i < this.ranges.size() && j < other.ranges.size()) {
      Range a = this.ranges.get(i);
      Range b = other.ranges.get(j);
      int start = Math.max(a.start(), b.start());
      int end = Math.min(a.end(), b.end());
      if (start <= end) {
        result.add(new Range(start, end));
      }
      if (a.end() < b.end()) {
        i++;
      } else {
        j++;
      }
    }
    return result.isEmpty() ? EMPTY : new CharRanges(Collections.unmodifiableList(result));
  }

  CharRanges complement() {
    if (this.isEmpty()) {
      return ANY;
    }
    List<Range> result = new ArrayList<>();
    int current = 0;
    for (Range r : ranges) {
      if (r.start() > current) {
        result.add(new Range(current, r.start() - 1));
      }
      current = r.end() + 1;
    }
    if (current <= MAX_CODE_POINT) {
      result.add(new Range(current, MAX_CODE_POINT));
    }
    return result.isEmpty() ? EMPTY : new CharRanges(Collections.unmodifiableList(result));
  }

  static CharRanges from(RegexPattern.CharSetElement element) {
    return switch (element) {
      case RegexPattern.LiteralChar lc -> of(lc.value());
      case RegexPattern.CharRange cr -> range(cr.start(), cr.end());
      case RegexPattern.PredefinedCharClass pcc -> from(pcc);
      case RegexPattern.PosixCharClass pcc -> from(pcc);
      case RegexPattern.CharacterProperty.Negated neg -> from(neg.property()).complement();
      case RegexPattern.UnicodeProperty up -> fromUnicodeProperty(up.propertyName());
      default -> any();
    };
  }

  static CharRanges from(RegexPattern.CharacterSet characterSet) {
    return switch (characterSet) {
      case RegexPattern.CharacterSet.AnyOf anyOf -> {
        CharRanges result = EMPTY;
        for (RegexPattern.CharSetElement e : anyOf.elements()) {
          result = result.union(from(e));
        }
        yield result;
      }
      case RegexPattern.CharacterSet.NoneOf noneOf -> {
        CharRanges inner = EMPTY;
        for (RegexPattern.CharSetElement e : noneOf.elements()) {
          inner = inner.union(from(e));
        }
        yield inner.complement();
      }
      default -> any();
    };
  }

  static CharRanges from(RegexPattern.PredefinedCharClass pcc) {
    return switch (pcc) {
      case ANY_CHAR -> any().intersection(of('\n').union(of('\r')).complement());
      case DIGIT -> range('0', '9');
      case NON_DIGIT -> range('0', '9').complement();
      case WHITESPACE ->
          of(' ').union(of('\t')).union(of('\n')).union(of('\r')).union(of('\f')).union(of(0x0B));
      case NON_WHITESPACE -> from(RegexPattern.PredefinedCharClass.WHITESPACE).complement();
      case WORD -> range('a', 'z').union(range('A', 'Z')).union(range('0', '9')).union(of('_'));
      case NON_WORD -> from(RegexPattern.PredefinedCharClass.WORD).complement();
    };
  }

  static CharRanges from(RegexPattern.PosixCharClass pcc) {
    return switch (pcc) {
      case LOWER -> range('a', 'z');
      case UPPER -> range('A', 'Z');
      case ASCII -> range(0, 0x7F);
      case ALPHA -> range('a', 'z').union(range('A', 'Z'));
      case DIGIT -> range('0', '9');
      case ALNUM -> range('a', 'z').union(range('A', 'Z')).union(range('0', '9'));
      case PUNCT -> {
        CharRanges punct = EMPTY;
        String chars = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
        for (int i = 0; i < chars.length(); i++) {
          punct = punct.union(of(chars.charAt(i)));
        }
        yield punct;
      }
      case GRAPH -> range(0x21, 0x7E);
      case PRINT -> range(0x20, 0x7E);
      case BLANK -> of(' ').union(of('\t'));
      case CNTRL -> range(0, 0x1F).union(of(0x7F));
      case XDIGIT -> range('0', '9').union(range('a', 'f')).union(range('A', 'F'));
      case SPACE ->
          of(' ').union(of('\t')).union(of('\n')).union(of('\r')).union(of('\f')).union(of(0x0B));
    };
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

  @Override public String toString() {
    return ranges.toString();
  }
}

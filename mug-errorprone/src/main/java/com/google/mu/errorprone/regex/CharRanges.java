package com.google.mu.errorprone.regex;

import static com.google.common.collect.Range.closedOpen;
import static java.lang.Character.MAX_CODE_POINT;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.labs.regex.RegexPattern;

/**
 * Utility functions for operating on Unicode character sets represented as {@link
 * ImmutableRangeSet} of code points.
 */
final class CharRanges {
  static final ImmutableRangeSet<Integer> EMPTY = ImmutableRangeSet.of();
  static final ImmutableRangeSet<Integer> ANY =
      ImmutableRangeSet.of(closedOpen(0, MAX_CODE_POINT + 1));

  static ImmutableRangeSet<Integer> of(int codePoint) {
    return ImmutableRangeSet.of(only(codePoint));
  }

  static boolean intersects(RangeSet<Integer> a, RangeSet<Integer> b) {
    return a.asRanges().stream().anyMatch(b::intersects);
  }

  static ImmutableRangeSet<Integer> union(RangeSet<Integer> a, RangeSet<Integer> b) {
    RangeSet<Integer> tree = TreeRangeSet.create(a);
    tree.addAll(b);
    return ImmutableRangeSet.copyOf(tree);
  }

  static ImmutableRangeSet<Integer> intersection(RangeSet<Integer> a, RangeSet<Integer> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return EMPTY;
    }
    RangeSet<Integer> tree = TreeRangeSet.create();
    for (Range<Integer> range : a.asRanges()) {
      tree.addAll(b.subRangeSet(range));
    }
    return ImmutableRangeSet.copyOf(tree);
  }

  static int sampleChar(RangeSet<Integer> ranges) {
    if (ranges.contains((int) 'a')) {
      return 'a';
    }
    for (Range<Integer> r : ranges.asRanges()) {
      int start = Math.max(r.lowerEndpoint(), 32);
      int end = Math.min(r.upperEndpoint() - 1, 126);
      if (start <= end) {
        return start;
      }
    }
    return ranges.asRanges().iterator().next().lowerEndpoint();
  }

  static ImmutableRangeSet<Integer> from(RegexPattern.CharSetElement element) {
    return switch (element) {
      case RegexPattern.LiteralChar lc -> of(lc.codePoint());
      case RegexPattern.CharRange cr ->
          cr.start() > cr.end() ? EMPTY : ImmutableRangeSet.of(range(cr.start(), cr.end()));
      case RegexPattern.PredefinedCharClass pcc -> from(pcc);
      case RegexPattern.PosixCharClass pcc -> from(pcc);
      case RegexPattern.CharacterProperty.Negated neg -> complement(from(neg.property()));
      case RegexPattern.UnicodeProperty up -> fromUnicodeProperty(up.propertyName());
      case RegexPattern.CharacterSet cs -> from(cs);
      default -> ANY;
    };
  }

  private static ImmutableRangeSet<Integer> fromCharSetElement(
      RegexPattern.CharSetElement element) {
    if (element == RegexPattern.PredefinedCharClass.ANY_CHAR) {
      return of('.');
    }
    if (element == RegexPattern.PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER) {
      return of('X');
    }
    if (element == RegexPattern.PredefinedCharClass.LINEBREAK) {
      return of('R');
    }
    return from(element);
  }

  static ImmutableRangeSet<Integer> from(RegexPattern.CharacterSet characterSet) {
    return switch (characterSet) {
      case RegexPattern.CharacterSet.AnyOf anyOf -> {
        RangeSet<Integer> tree = TreeRangeSet.create();
        for (RegexPattern.CharSetElement e : anyOf.elements()) {
          tree.addAll(fromCharSetElement(e));
        }
        yield ImmutableRangeSet.copyOf(tree);
      }
      case RegexPattern.CharacterSet.NoneOf noneOf -> {
        RangeSet<Integer> tree = TreeRangeSet.create();
        for (RegexPattern.CharSetElement e : noneOf.elements()) {
          tree.addAll(fromCharSetElement(e));
        }
        yield complement(tree);
      }
      case RegexPattern.CharacterSet.Intersection is -> {
        ImmutableRangeSet<Integer> result = ANY;
        for (RegexPattern.CharacterSet operand : is.operands()) {
          result = intersection(result, from(operand));
        }
        yield result;
      }
      default -> ANY;
    };
  }

  static ImmutableRangeSet<Integer> from(RegexPattern.PredefinedCharClass pcc) {
    return switch (pcc) {
      case ANY_CHAR -> ANY_CHAR;
      case DIGIT -> DIGIT;
      case NON_DIGIT -> NON_DIGIT;
      case WHITESPACE -> WHITESPACE;
      case NON_WHITESPACE -> NON_WHITESPACE;
      case WORD -> WORD;
      case NON_WORD -> NON_WORD;
      case HORIZONTAL_WHITESPACE -> H_WHITESPACE;
      case NON_HORIZONTAL_WHITESPACE -> NON_H_WHITESPACE;
      case VERTICAL_WHITESPACE -> V_WHITESPACE;
      case NON_VERTICAL_WHITESPACE -> NON_V_WHITESPACE;
      case LINEBREAK -> LINEBREAK;
      case EXTENDED_GRAPHEME_CLUSTER -> ANY;
    };
  }

  static ImmutableRangeSet<Integer> from(RegexPattern.PosixCharClass pcc) {
    return switch (pcc) {
      case LOWER -> LOWER;
      case UPPER -> UPPER;
      case ASCII -> ASCII;
      case ALPHA -> ALPHA;
      case DIGIT -> DIGIT;
      case ALNUM -> ALNUM;
      case PUNCT -> PUNCT;
      case GRAPH -> GRAPH;
      case PRINT -> PRINT;
      case BLANK -> BLANK;
      case CNTRL -> CNTRL;
      case XDIGIT -> XDIGIT;
      case SPACE -> SPACE;
    };
  }

  private static ImmutableRangeSet<Integer> complement(RangeSet<Integer> ranges) {
    return ImmutableRangeSet.copyOf(
        ranges.complement().subRangeSet(closedOpen(0, MAX_CODE_POINT + 1)));
  }

  private static Range<Integer> only(int c) {
    return range(c, c);
  }

  private static Range<Integer> range(int start, int end) {
    return closedOpen(start, end + 1);
  }

  private static final ImmutableRangeSet<Integer> DIGIT = ImmutableRangeSet.of(range('0', '9'));
  private static final ImmutableRangeSet<Integer> NON_DIGIT = complement(DIGIT);
  private static final ImmutableRangeSet<Integer> WHITESPACE = whitespaceRanges();

  private static ImmutableRangeSet<Integer> whitespaceRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(only(' '))
        .add(range('\t', '\r'))
        .add(only(0x85))
        .add(range(0x2028, 0x2029))
        .build();
  }

  private static final ImmutableRangeSet<Integer> NON_WHITESPACE = complement(WHITESPACE);
  private static final ImmutableRangeSet<Integer> WORD = wordRanges();

  private static ImmutableRangeSet<Integer> wordRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(range('a', 'z'))
        .add(range('A', 'Z'))
        .add(range('0', '9'))
        .add(only('_'))
        .build();
  }

  private static final ImmutableRangeSet<Integer> NON_WORD = complement(WORD);

  private static final ImmutableRangeSet<Integer> LOWER = ImmutableRangeSet.of(range('a', 'z'));
  private static final ImmutableRangeSet<Integer> UPPER = ImmutableRangeSet.of(range('A', 'Z'));
  private static final ImmutableRangeSet<Integer> ASCII = ImmutableRangeSet.of(range(0, 0x7F));
  private static final ImmutableRangeSet<Integer> ALPHA = union(LOWER, UPPER);
  private static final ImmutableRangeSet<Integer> ALNUM = union(ALPHA, DIGIT);
  private static final ImmutableRangeSet<Integer> PUNCT = punctRanges();
  private static final ImmutableRangeSet<Integer> GRAPH = ImmutableRangeSet.of(range(0x21, 0x7E));
  private static final ImmutableRangeSet<Integer> PRINT = ImmutableRangeSet.of(range(0x20, 0x7E));
  private static final ImmutableRangeSet<Integer> BLANK = union(of(' '), of('\t'));
  private static final ImmutableRangeSet<Integer> CNTRL =
      union(ImmutableRangeSet.of(range(0, 0x1F)), of(0x7F));
  private static final ImmutableRangeSet<Integer> XDIGIT = union(
      DIGIT, union(ImmutableRangeSet.of(range('a', 'f')), ImmutableRangeSet.of(range('A', 'F'))));
  private static final ImmutableRangeSet<Integer> SPACE = WHITESPACE;

  private static ImmutableRangeSet<Integer> punctRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(range(0x21, 0x2F))
        .add(range(0x3A, 0x40))
        .add(range(0x5B, 0x60))
        .add(range(0x7B, 0x7E))
        .build();
  }

  private static final ImmutableRangeSet<Integer> LINEBREAK = ImmutableRangeSet.<Integer>builder()
      .add(range('\n', '\r'))
      .add(only(0x85))
      .add(range(0x2028, 0x2029))
      .build();

  static final ImmutableRangeSet<Integer> ANY_CHAR = complement(LINEBREAK);

  private static final ImmutableRangeSet<Integer> UNICODE_ZS = ImmutableRangeSet.<Integer>builder()
      .add(only(0x0020))
      .add(only(0x00A0))
      .add(only(0x1680))
      .add(range(0x2000, 0x200A))
      .add(only(0x202F))
      .add(only(0x205F))
      .add(only(0x3000))
      .build();
  private static final ImmutableRangeSet<Integer> UNICODE_ZL = of(0x2028);
  private static final ImmutableRangeSet<Integer> UNICODE_ZP = of(0x2029);
  private static final ImmutableRangeSet<Integer> UNICODE_Z =
      union(UNICODE_ZS, union(UNICODE_ZL, UNICODE_ZP));

  private static final ImmutableRangeSet<Integer> H_WHITESPACE =
      ImmutableRangeSet.<Integer>builder()
          .add(only('\t'))
          .add(only(0xA0))
          .add(only(0x1680))
          .add(only(0x180E))
          .add(range(0x2000, 0x200A))
          .add(only(0x202F))
          .add(only(0x205F))
          .add(only(0x3000))
          .build();
  private static final ImmutableRangeSet<Integer> NON_H_WHITESPACE = complement(H_WHITESPACE);

  private static final ImmutableRangeSet<Integer> V_WHITESPACE =
      ImmutableRangeSet.<Integer>builder()
          .add(only('\n'))
          .add(only(0x0B))
          .add(only('\f'))
          .add(only('\r'))
          .add(only(0x85))
          .add(range(0x2028, 0x2029))
          .build();
  private static final ImmutableRangeSet<Integer> NON_V_WHITESPACE = complement(V_WHITESPACE);

  private static ImmutableRangeSet<Integer> fromUnicodeProperty(String name) {
    return switch (Ascii.toLowerCase(name)) {
      case "nd", "digit" -> DIGIT;
      case "l", "letter" -> ALPHA;
      case "lu" -> UPPER;
      case "ll" -> LOWER;
      case "alpha" -> ALPHA;
      case "alnum" -> ALNUM;
      case "ascii" -> ASCII;
      case "punct" -> PUNCT;
      case "space" -> SPACE;
      case "zl" -> UNICODE_ZL;
      case "zp" -> UNICODE_ZP;
      case "zs" -> UNICODE_ZS;
      case "z", "separator" -> UNICODE_Z;
      default -> ANY;
    };
  }

  private CharRanges() {}
}

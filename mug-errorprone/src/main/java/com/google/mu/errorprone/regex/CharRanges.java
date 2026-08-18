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
    return ImmutableRangeSet.of(closedOpen(codePoint, codePoint + 1));
  }

  static ImmutableRangeSet<Integer> range(int start, int end) {
    return ImmutableRangeSet.of(closedOpen(start, end + 1));
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

  static ImmutableRangeSet<Integer> complement(RangeSet<Integer> ranges) {
    return ImmutableRangeSet.copyOf(
        ranges.complement().subRangeSet(closedOpen(0, MAX_CODE_POINT + 1)));
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
      case RegexPattern.LiteralChar lc -> of(lc.value());
      case RegexPattern.CharRange cr -> cr.start() > cr.end() ? EMPTY : range(cr.start(), cr.end());
      case RegexPattern.PredefinedCharClass pcc -> from(pcc);
      case RegexPattern.PosixCharClass pcc -> from(pcc);
      case RegexPattern.CharacterProperty.Negated neg -> complement(from(neg.property()));
      case RegexPattern.UnicodeProperty up -> fromUnicodeProperty(up.propertyName());
      case RegexPattern.CharacterSet cs -> from(cs);
      default -> ANY;
    };
  }

  static ImmutableRangeSet<Integer> fromCharSetElement(RegexPattern.CharSetElement element) {
    if (element == RegexPattern.PredefinedCharClass.ANY_CHAR) {
      return of('.');
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

  private static final ImmutableRangeSet<Integer> ANY_CHAR =
      intersection(ANY, complement(union(of('\n'), of('\r'))));
  private static final ImmutableRangeSet<Integer> DIGIT = range('0', '9');
  private static final ImmutableRangeSet<Integer> NON_DIGIT = complement(DIGIT);
  private static final ImmutableRangeSet<Integer> WHITESPACE = whitespaceRanges();

  private static ImmutableRangeSet<Integer> whitespaceRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(closedOpen((int) ' ', (int) ' ' + 1))
        .add(closedOpen((int) '\t', (int) '\r' + 1))
        .add(closedOpen(0x85, 0x85 + 1))
        .add(closedOpen(0x2028, 0x2029 + 1))
        .build();
  }

  private static final ImmutableRangeSet<Integer> NON_WHITESPACE = complement(WHITESPACE);
  private static final ImmutableRangeSet<Integer> WORD = wordRanges();

  private static ImmutableRangeSet<Integer> wordRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(closedOpen((int) 'a', (int) 'z' + 1))
        .add(closedOpen((int) 'A', (int) 'Z' + 1))
        .add(closedOpen((int) '0', (int) '9' + 1))
        .add(closedOpen((int) '_', (int) '_' + 1))
        .build();
  }

  private static final ImmutableRangeSet<Integer> NON_WORD = complement(WORD);

  private static final ImmutableRangeSet<Integer> LOWER = range('a', 'z');
  private static final ImmutableRangeSet<Integer> UPPER = range('A', 'Z');
  private static final ImmutableRangeSet<Integer> ASCII = range(0, 0x7F);
  private static final ImmutableRangeSet<Integer> ALPHA = union(LOWER, UPPER);
  private static final ImmutableRangeSet<Integer> ALNUM = union(ALPHA, DIGIT);
  private static final ImmutableRangeSet<Integer> PUNCT = punctRanges();
  private static final ImmutableRangeSet<Integer> GRAPH = range(0x21, 0x7E);
  private static final ImmutableRangeSet<Integer> PRINT = range(0x20, 0x7E);
  private static final ImmutableRangeSet<Integer> BLANK = union(of(' '), of('\t'));
  private static final ImmutableRangeSet<Integer> CNTRL = union(range(0, 0x1F), of(0x7F));
  private static final ImmutableRangeSet<Integer> XDIGIT =
      union(DIGIT, union(range('a', 'f'), range('A', 'F')));
  private static final ImmutableRangeSet<Integer> SPACE = WHITESPACE;

  private static ImmutableRangeSet<Integer> punctRanges() {
    return ImmutableRangeSet.<Integer>builder()
        .add(closedOpen(0x21, 0x2F + 1))
        .add(closedOpen(0x3A, 0x40 + 1))
        .add(closedOpen(0x5B, 0x60 + 1))
        .add(closedOpen(0x7B, 0x7E + 1))
        .build();
  }

  private static final ImmutableRangeSet<Integer> LINEBREAK = ImmutableRangeSet.<Integer>builder()
      .add(closedOpen((int) '\n', (int) '\r' + 1))
      .add(closedOpen(0x85, 0x85 + 1))
      .add(closedOpen(0x2028, 0x2029 + 1))
      .build();

  private static final ImmutableRangeSet<Integer> UNICODE_ZS = ImmutableRangeSet.<Integer>builder()
      .add(closedOpen(0x0020, 0x0020 + 1))
      .add(closedOpen(0x00A0, 0x00A0 + 1))
      .add(closedOpen(0x1680, 0x1680 + 1))
      .add(closedOpen(0x2000, 0x200A + 1))
      .add(closedOpen(0x202F, 0x202F + 1))
      .add(closedOpen(0x205F, 0x205F + 1))
      .add(closedOpen(0x3000, 0x3000 + 1))
      .build();
  private static final ImmutableRangeSet<Integer> UNICODE_ZL = range(0x2028, 0x2028);
  private static final ImmutableRangeSet<Integer> UNICODE_ZP = range(0x2029, 0x2029);
  private static final ImmutableRangeSet<Integer> UNICODE_Z =
      union(UNICODE_ZS, union(UNICODE_ZL, UNICODE_ZP));

  private static final ImmutableRangeSet<Integer> HORIZONTAL_WHITESPACE =
      ImmutableRangeSet.<Integer>builder()
          .add(closedOpen((int) '\t', (int) '\t' + 1))
          .add(closedOpen(0xA0, 0xA0 + 1))
          .add(closedOpen(0x1680, 0x1680 + 1))
          .add(closedOpen(0x180E, 0x180E + 1))
          .add(closedOpen(0x2000, 0x200A + 1))
          .add(closedOpen(0x202F, 0x202F + 1))
          .add(closedOpen(0x205F, 0x205F + 1))
          .add(closedOpen(0x3000, 0x3000 + 1))
          .build();
  private static final ImmutableRangeSet<Integer> NON_HORIZONTAL_WHITESPACE =
      complement(HORIZONTAL_WHITESPACE);

  private static final ImmutableRangeSet<Integer> VERTICAL_WHITESPACE =
      ImmutableRangeSet.<Integer>builder()
          .add(closedOpen((int) '\n', (int) '\n' + 1))
          .add(closedOpen(0x0B, 0x0B + 1))
          .add(closedOpen((int) '\f', (int) '\f' + 1))
          .add(closedOpen((int) '\r', (int) '\r' + 1))
          .add(closedOpen(0x85, 0x85 + 1))
          .add(closedOpen(0x2028, 0x2029 + 1))
          .build();
  private static final ImmutableRangeSet<Integer> NON_VERTICAL_WHITESPACE =
      complement(VERTICAL_WHITESPACE);

  private static ImmutableRangeSet<Integer> from(RegexPattern.PredefinedCharClass pcc) {
    return switch (pcc) {
      case ANY_CHAR -> ANY_CHAR;
      case DIGIT -> DIGIT;
      case NON_DIGIT -> NON_DIGIT;
      case WHITESPACE -> WHITESPACE;
      case NON_WHITESPACE -> NON_WHITESPACE;
      case WORD -> WORD;
      case NON_WORD -> NON_WORD;
      case HORIZONTAL_WHITESPACE -> HORIZONTAL_WHITESPACE;
      case NON_HORIZONTAL_WHITESPACE -> NON_HORIZONTAL_WHITESPACE;
      case VERTICAL_WHITESPACE -> VERTICAL_WHITESPACE;
      case NON_VERTICAL_WHITESPACE -> NON_VERTICAL_WHITESPACE;
      case LINEBREAK -> LINEBREAK;
    };
  }

  private static ImmutableRangeSet<Integer> from(RegexPattern.PosixCharClass pcc) {
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

  private static ImmutableRangeSet<Integer> fromUnicodeProperty(String name) {
    return switch (Ascii.toLowerCase(name)) {
      case "nd", "digit" -> range('0', '9');
      case "l", "letter" -> union(range('a', 'z'), range('A', 'Z'));
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

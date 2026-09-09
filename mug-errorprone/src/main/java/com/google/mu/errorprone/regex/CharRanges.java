package com.google.mu.errorprone.regex;

import static com.google.common.collect.Range.closedOpen;
import static java.lang.Character.MAX_CODE_POINT;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.labs.regex.RegexPattern;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

  static ImmutableRangeSet<Integer> from(RegexPattern.CharacterSet characterSet) {
    return switch (characterSet) {
      case RegexPattern.CharacterSet.AnyOf anyOf -> {
        RangeSet<Integer> tree = TreeRangeSet.create();
        for (RegexPattern.CharSetElement e : anyOf.elements()) {
          tree.addAll(from(e));
        }
        yield ImmutableRangeSet.copyOf(tree);
      }
      case RegexPattern.CharacterSet.NoneOf noneOf -> {
        RangeSet<Integer> tree = TreeRangeSet.create();
        for (RegexPattern.CharSetElement e : noneOf.elements()) {
          tree.addAll(from(e));
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

  private static final ImmutableRangeSet<Integer> LINE_TERMINATORS =
      ImmutableRangeSet.<Integer>builder()
          .add(only('\n'))
          .add(only('\r'))
          .add(only(0x85))
          .add(range(0x2028, 0x2029))
          .build();

  static final ImmutableRangeSet<Integer> ANY_CHAR = complement(LINE_TERMINATORS);

  private static final ImmutableRangeSet<Integer> LINEBREAK = ImmutableRangeSet.<Integer>builder()
      .add(range('\n', '\r'))
      .add(only(0x85))
      .add(range(0x2028, 0x2029))
      .build();

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
          .add(only(' '))
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
      case "nd" -> UnicodeData.UNICODE_DECIMAL_DIGIT;
      case "digit" -> DIGIT;
      case "l", "letter" -> UnicodeData.UNICODE_LETTER;
      case "lu" -> UnicodeData.UNICODE_UPPER;
      case "ll" -> UnicodeData.UNICODE_LOWER;
      case "alpha" -> ALPHA;
      case "alnum" -> ALNUM;
      case "ascii" -> ASCII;
      case "punct" -> PUNCT;
      case "space" -> SPACE;
      case "zl" -> UNICODE_ZL;
      case "zp" -> UNICODE_ZP;
      case "zs" -> UNICODE_ZS;
      case "z", "separator" -> UNICODE_Z;
      default -> {
        ImmutableRangeSet<Integer> resolved = UnicodeData.resolve(name);
        if (resolved != null) {
          yield resolved;
        }
        throw new IllegalArgumentException("unrecognized Unicode property: " + name);
      }
    };
  }

  private static final class UnicodeData {
    static final ImmutableRangeSet<Integer> UNICODE_LETTER;
    static final ImmutableRangeSet<Integer> UNICODE_UPPER;
    static final ImmutableRangeSet<Integer> UNICODE_LOWER;
    static final ImmutableRangeSet<Integer> UNICODE_DECIMAL_DIGIT;
    private static final Map<Character.UnicodeBlock, Range<Integer>> BLOCK_RANGES;

    static {
      ImmutableRangeSet.Builder<Integer> letterBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> upperBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> lowerBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> digitBuilder = ImmutableRangeSet.builder();
      Map<Character.UnicodeBlock, Range<Integer>> blockMap = new HashMap<>();

      int letterStart = -1;
      int upperStart = -1;
      int lowerStart = -1;
      int digitStart = -1;
      int blockStart = -1;
      Character.UnicodeBlock currentBlock = null;

      for (int cp = 0; cp <= MAX_CODE_POINT; cp++) {
        int type = Character.getType(cp);
        boolean isLetter =
            type == Character.UPPERCASE_LETTER || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER;
        boolean isUpper = type == Character.UPPERCASE_LETTER;
        boolean isLower = type == Character.LOWERCASE_LETTER;
        boolean isDigit = type == Character.DECIMAL_DIGIT_NUMBER;

        if (isLetter) {
          if (letterStart < 0) {
            letterStart = cp;
          }
        } else if (letterStart >= 0) {
          letterBuilder.add(closedOpen(letterStart, cp));
          letterStart = -1;
        }

        if (isUpper) {
          if (upperStart < 0) {
            upperStart = cp;
          }
        } else if (upperStart >= 0) {
          upperBuilder.add(closedOpen(upperStart, cp));
          upperStart = -1;
        }

        if (isLower) {
          if (lowerStart < 0) {
            lowerStart = cp;
          }
        } else if (lowerStart >= 0) {
          lowerBuilder.add(closedOpen(lowerStart, cp));
          lowerStart = -1;
        }

        if (isDigit) {
          if (digitStart < 0) {
            digitStart = cp;
          }
        } else if (digitStart >= 0) {
          digitBuilder.add(closedOpen(digitStart, cp));
          digitStart = -1;
        }

        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        if (b != currentBlock) {
          if (currentBlock != null) {
            blockMap.put(currentBlock, closedOpen(blockStart, cp));
          }
          currentBlock = b;
          blockStart = cp;
        }
      }

      if (letterStart >= 0) {
        letterBuilder.add(closedOpen(letterStart, MAX_CODE_POINT + 1));
      }
      if (upperStart >= 0) {
        upperBuilder.add(closedOpen(upperStart, MAX_CODE_POINT + 1));
      }
      if (lowerStart >= 0) {
        lowerBuilder.add(closedOpen(lowerStart, MAX_CODE_POINT + 1));
      }
      if (digitStart >= 0) {
        digitBuilder.add(closedOpen(digitStart, MAX_CODE_POINT + 1));
      }
      if (currentBlock != null) {
        blockMap.put(currentBlock, closedOpen(blockStart, MAX_CODE_POINT + 1));
      }

      UNICODE_LETTER = letterBuilder.build();
      UNICODE_UPPER = upperBuilder.build();
      UNICODE_LOWER = lowerBuilder.build();
      UNICODE_DECIMAL_DIGIT = digitBuilder.build();
      BLOCK_RANGES = Collections.unmodifiableMap(blockMap);
    }

    static ImmutableRangeSet<Integer> resolve(String name) {
      if (name.startsWith("gc=")) {
        return resolveCategory(name.substring(3));
      }
      if (name.startsWith("blk=")) {
        return resolveBlock(name.substring(4));
      }
      if (name.startsWith("sc=")) {
        return resolveScript(name.substring(3));
      }
      if (name.length() >= 3 && (name.startsWith("In") || name.startsWith("in"))) {
        ImmutableRangeSet<Integer> block = resolveBlock(name.substring(2));
        if (block != null) {
          return block;
        }
      }
      if (name.length() >= 3 && (name.startsWith("Is") || name.startsWith("is"))) {
        ImmutableRangeSet<Integer> script = resolveScript(name.substring(2));
        if (script != null) {
          return script;
        }
      }
      ImmutableRangeSet<Integer> cat = resolveCategory(name);
      if (cat != null) {
        return cat;
      }
      ImmutableRangeSet<Integer> block = resolveBlock(name);
      if (block != null) {
        return block;
      }
      return resolveScript(name);
    }

    private static ImmutableRangeSet<Integer> resolveCategory(String cat) {
      return switch (Ascii.toLowerCase(cat)) {
        case "l", "letter" -> UNICODE_LETTER;
        case "lu" -> UNICODE_UPPER;
        case "ll" -> UNICODE_LOWER;
        case "nd" -> UNICODE_DECIMAL_DIGIT;
        default -> null;
      };
    }

    private static ImmutableRangeSet<Integer> resolveBlock(String blockName) {
      try {
        Character.UnicodeBlock block = Character.UnicodeBlock.forName(blockName);
        Range<Integer> range = BLOCK_RANGES.get(block);
        return range == null ? null : ImmutableRangeSet.of(range);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }

    private static ImmutableRangeSet<Integer> resolveScript(String scriptName) {
      try {
        Character.UnicodeScript script = Character.UnicodeScript.forName(scriptName);
        ImmutableRangeSet.Builder<Integer> builder = ImmutableRangeSet.builder();
        int start = -1;
        for (int cp = 0; cp <= MAX_CODE_POINT; cp++) {
          if (Character.UnicodeScript.of(cp) == script) {
            if (start < 0) {
              start = cp;
            }
          } else if (start >= 0) {
            builder.add(closedOpen(start, cp));
            start = -1;
          }
        }
        if (start >= 0) {
          builder.add(closedOpen(start, MAX_CODE_POINT + 1));
        }
        return builder.build();
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
  }

  private CharRanges() {}
}

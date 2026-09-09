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
      case "digit" -> DIGIT;
      case "alpha" -> ALPHA;
      case "alnum" -> ALNUM;
      case "ascii" -> ASCII;
      case "blank" -> BLANK;
      case "cntrl" -> CNTRL;
      case "graph" -> GRAPH;
      case "print" -> PRINT;
      case "punct" -> PUNCT;
      case "space" -> SPACE;
      case "word" -> WORD;
      case "xdigit" -> XDIGIT;
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
    private static final Map<String, ImmutableRangeSet<Integer>> CATEGORIES;
    private static final Map<String, ImmutableRangeSet<Integer>> BINARY_PROPERTIES;
    private static final Map<Character.UnicodeBlock, Range<Integer>> BLOCK_RANGES;

    static {
      @SuppressWarnings("unchecked")
      ImmutableRangeSet.Builder<Integer>[] typeBuilders =
          (ImmutableRangeSet.Builder<Integer>[]) new ImmutableRangeSet.Builder<?>[31];
      for (int i = 0; i < 31; i++) {
        typeBuilders[i] = ImmutableRangeSet.builder();
      }
      ImmutableRangeSet.Builder<Integer> alphaBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> ideoBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> digitBuilder = ImmutableRangeSet.builder();
      ImmutableRangeSet.Builder<Integer> wsBuilder = ImmutableRangeSet.builder();
      Map<Character.UnicodeBlock, Range<Integer>> blockMap = new HashMap<>();

      int currentType = -1;
      int currentTypeStart = -1;
      int alphaStart = -1;
      int ideoStart = -1;
      int digitStart = -1;
      int wsStart = -1;
      int blockStart = -1;
      Character.UnicodeBlock currentBlock = null;

      for (int cp = 0; cp <= MAX_CODE_POINT; cp++) {
        int type = Character.getType(cp);
        if (type != currentType) {
          if (currentType >= 0) {
            typeBuilders[currentType].add(closedOpen(currentTypeStart, cp));
          }
          currentType = type;
          currentTypeStart = cp;
        }

        boolean isAlpha = Character.isAlphabetic(cp);
        if (isAlpha) {
          if (alphaStart < 0) {
            alphaStart = cp;
          }
        } else if (alphaStart >= 0) {
          alphaBuilder.add(closedOpen(alphaStart, cp));
          alphaStart = -1;
        }

        boolean isIdeo = Character.isIdeographic(cp);
        if (isIdeo) {
          if (ideoStart < 0) {
            ideoStart = cp;
          }
        } else if (ideoStart >= 0) {
          ideoBuilder.add(closedOpen(ideoStart, cp));
          ideoStart = -1;
        }

        boolean isDigit = Character.isDigit(cp);
        if (isDigit) {
          if (digitStart < 0) {
            digitStart = cp;
          }
        } else if (digitStart >= 0) {
          digitBuilder.add(closedOpen(digitStart, cp));
          digitStart = -1;
        }

        boolean isWs = (H_WHITESPACE.contains(cp) || V_WHITESPACE.contains(cp)) && cp != 0x180E;
        if (isWs) {
          if (wsStart < 0) {
            wsStart = cp;
          }
        } else if (wsStart >= 0) {
          wsBuilder.add(closedOpen(wsStart, cp));
          wsStart = -1;
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

      if (currentType >= 0) {
        typeBuilders[currentType].add(closedOpen(currentTypeStart, MAX_CODE_POINT + 1));
      }
      if (alphaStart >= 0) {
        alphaBuilder.add(closedOpen(alphaStart, MAX_CODE_POINT + 1));
      }
      if (ideoStart >= 0) {
        ideoBuilder.add(closedOpen(ideoStart, MAX_CODE_POINT + 1));
      }
      if (digitStart >= 0) {
        digitBuilder.add(closedOpen(digitStart, MAX_CODE_POINT + 1));
      }
      if (wsStart >= 0) {
        wsBuilder.add(closedOpen(wsStart, MAX_CODE_POINT + 1));
      }
      if (currentBlock != null) {
        blockMap.put(currentBlock, closedOpen(blockStart, MAX_CODE_POINT + 1));
      }

      @SuppressWarnings("unchecked")
      ImmutableRangeSet<Integer>[] typeRanges =
          (ImmutableRangeSet<Integer>[]) new ImmutableRangeSet<?>[31];
      for (int i = 0; i < 31; i++) {
        typeRanges[i] = typeBuilders[i].build();
      }

      Map<String, ImmutableRangeSet<Integer>> categories = new HashMap<>();
      categories.put("cn", typeRanges[Character.UNASSIGNED]);
      categories.put("lu", typeRanges[Character.UPPERCASE_LETTER]);
      categories.put("ll", typeRanges[Character.LOWERCASE_LETTER]);
      categories.put("lt", typeRanges[Character.TITLECASE_LETTER]);
      categories.put("lm", typeRanges[Character.MODIFIER_LETTER]);
      categories.put("lo", typeRanges[Character.OTHER_LETTER]);
      categories.put("mn", typeRanges[Character.NON_SPACING_MARK]);
      categories.put("me", typeRanges[Character.ENCLOSING_MARK]);
      categories.put("mc", typeRanges[Character.COMBINING_SPACING_MARK]);
      categories.put("nd", typeRanges[Character.DECIMAL_DIGIT_NUMBER]);
      categories.put("nl", typeRanges[Character.LETTER_NUMBER]);
      categories.put("no", typeRanges[Character.OTHER_NUMBER]);
      categories.put("zs", typeRanges[Character.SPACE_SEPARATOR]);
      categories.put("zl", typeRanges[Character.LINE_SEPARATOR]);
      categories.put("zp", typeRanges[Character.PARAGRAPH_SEPARATOR]);
      categories.put("cc", typeRanges[Character.CONTROL]);
      categories.put("cf", typeRanges[Character.FORMAT]);
      categories.put("co", typeRanges[Character.PRIVATE_USE]);
      categories.put("cs", typeRanges[Character.SURROGATE]);
      categories.put("pd", typeRanges[Character.DASH_PUNCTUATION]);
      categories.put("ps", typeRanges[Character.START_PUNCTUATION]);
      categories.put("pe", typeRanges[Character.END_PUNCTUATION]);
      categories.put("pc", typeRanges[Character.CONNECTOR_PUNCTUATION]);
      categories.put("po", typeRanges[Character.OTHER_PUNCTUATION]);
      categories.put("sm", typeRanges[Character.MATH_SYMBOL]);
      categories.put("sc", typeRanges[Character.CURRENCY_SYMBOL]);
      categories.put("sk", typeRanges[Character.MODIFIER_SYMBOL]);
      categories.put("so", typeRanges[Character.OTHER_SYMBOL]);
      categories.put("pi", typeRanges[Character.INITIAL_QUOTE_PUNCTUATION]);
      categories.put("pf", typeRanges[Character.FINAL_QUOTE_PUNCTUATION]);

      ImmutableRangeSet<Integer> letter = unionOf(
          typeRanges, Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER,
          Character.TITLECASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER);
      categories.put("l", letter);
      categories.put("letter", letter);

      ImmutableRangeSet<Integer> mark = unionOf(
          typeRanges, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK,
          Character.COMBINING_SPACING_MARK);
      categories.put("m", mark);
      categories.put("mark", mark);

      ImmutableRangeSet<Integer> number = unionOf(
          typeRanges, Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER,
          Character.OTHER_NUMBER);
      categories.put("n", number);
      categories.put("number", number);

      ImmutableRangeSet<Integer> separator = unionOf(
          typeRanges, Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
          Character.PARAGRAPH_SEPARATOR);
      categories.put("z", separator);
      categories.put("separator", separator);

      ImmutableRangeSet<Integer> other = unionOf(
          typeRanges, Character.UNASSIGNED, Character.CONTROL, Character.FORMAT,
          Character.PRIVATE_USE, Character.SURROGATE);
      categories.put("c", other);
      categories.put("other", other);

      ImmutableRangeSet<Integer> punctuation = unionOf(
          typeRanges, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
          Character.END_PUNCTUATION, Character.CONNECTOR_PUNCTUATION, Character.OTHER_PUNCTUATION,
          Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION);
      categories.put("p", punctuation);
      categories.put("punctuation", punctuation);

      ImmutableRangeSet<Integer> symbol = unionOf(
          typeRanges, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
          Character.OTHER_SYMBOL);
      categories.put("s", symbol);
      categories.put("symbol", symbol);

      CATEGORIES = Collections.unmodifiableMap(categories);

      Map<String, ImmutableRangeSet<Integer>> binaryProps = new HashMap<>();
      binaryProps.put("alphabetic", alphaBuilder.build());
      binaryProps.put("ideographic", ideoBuilder.build());
      binaryProps.put("letter", letter);
      binaryProps.put("titlecase", typeRanges[Character.TITLECASE_LETTER]);
      binaryProps.put("digit", digitBuilder.build());
      binaryProps.put("lower", typeRanges[Character.LOWERCASE_LETTER]);
      binaryProps.put("lowercase", typeRanges[Character.LOWERCASE_LETTER]);
      binaryProps.put("upper", typeRanges[Character.UPPERCASE_LETTER]);
      binaryProps.put("uppercase", typeRanges[Character.UPPERCASE_LETTER]);
      binaryProps.put("whitespace", wsBuilder.build());
      binaryProps.put("white_space", wsBuilder.build());
      binaryProps.put("punctuation", punctuation);
      BINARY_PROPERTIES = Collections.unmodifiableMap(binaryProps);

      BLOCK_RANGES = Collections.unmodifiableMap(blockMap);
    }

    private static ImmutableRangeSet<Integer> unionOf(
        ImmutableRangeSet<Integer>[] typeRanges, int... types) {
      RangeSet<Integer> tree = TreeRangeSet.create();
      for (int t : types) {
        tree.addAll(typeRanges[t]);
      }
      return ImmutableRangeSet.copyOf(tree);
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
        String sub = name.substring(2);
        ImmutableRangeSet<Integer> prop = resolveBinaryProperty(sub);
        if (prop != null) {
          return prop;
        }
        ImmutableRangeSet<Integer> script = resolveScript(sub);
        if (script != null) {
          return script;
        }
        ImmutableRangeSet<Integer> cat = resolveCategory(sub);
        if (cat != null) {
          return cat;
        }
        ImmutableRangeSet<Integer> block = resolveBlock(sub);
        if (block != null) {
          return block;
        }
      }
      ImmutableRangeSet<Integer> cat = resolveCategory(name);
      if (cat != null) {
        return cat;
      }
      ImmutableRangeSet<Integer> prop = resolveBinaryProperty(name);
      if (prop != null) {
        return prop;
      }
      ImmutableRangeSet<Integer> block = resolveBlock(name);
      if (block != null) {
        return block;
      }
      return resolveScript(name);
    }

    private static ImmutableRangeSet<Integer> resolveCategory(String cat) {
      return CATEGORIES.get(Ascii.toLowerCase(cat));
    }

    private static ImmutableRangeSet<Integer> resolveBinaryProperty(String prop) {
      return BINARY_PROPERTIES.get(Ascii.toLowerCase(prop));
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

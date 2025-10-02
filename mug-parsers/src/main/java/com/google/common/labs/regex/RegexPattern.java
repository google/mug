package com.google.common.labs.regex;

import static com.google.common.labs.regex.InternalUtils.checkArgument;
import static com.google.mu.util.stream.MoreStreams.groupConsecutive;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.labs.parser.Parser;
import com.google.mu.util.CharPredicate;


/**
 * Defines the Abstract Syntax Tree (AST) for a regular expression.
 *
 * <p>This AST is used to represent parsed regular expressions, as a basis to enable static analysis
 * of regexes.
 */
// TODO(benyu): Add support for free-spacing mode (?x:...).
public sealed interface RegexPattern
    permits RegexPattern.Alternation,
        RegexPattern.Sequence,
        RegexPattern.Quantified,
        RegexPattern.Group,
        RegexPattern.Literal,
        RegexPattern.PredefinedCharClass,
        RegexPattern.CharacterProperty,
        RegexPattern.CharacterProperty.Negated,
        RegexPattern.CharacterSet,
        RegexPattern.Anchor,
        RegexPattern.Lookaround {
  /** Returns a {@link Sequence} of the given elements. */
  @SafeVarargs
  static Sequence sequence(RegexPattern... elements) {
    return new Sequence(Arrays.stream(elements).collect(toUnmodifiableList()));
  }


  /**
   * A collector that collects the input {@code RegexPattern} as a sequence. Nested sequences are
   * flattened and adjacent literals are concatenated as a single literal.
   */
  static Collector<RegexPattern, ?, RegexPattern> inSequence() {
    return collectingAndThen(
        toList(),
        list -> {
          // First flatten the nested Sequence elements
          var flattened =
              list.stream()
                  .flatMap(p -> p instanceof Sequence seq ? seq.elements().stream() : Stream.of(p));
          // Then merge adjacent literals
          List<RegexPattern> segments =
              groupConsecutive(
                      flattened,
                      (a, b) -> a instanceof Literal && b instanceof Literal,
                      (a, b) -> new Literal(((Literal) a).value() + ((Literal) b).value()))
                  .collect(toUnmodifiableList());
          // Wrap in sequence if needed.
          return segments.size() == 1 ? segments.get(0) : new Sequence(segments);
        });
  }

  /** Returns an {@link Alternation} of the given alternatives. */
  @SafeVarargs
  static Alternation alternation(RegexPattern... alternatives) {
    return new Alternation(Arrays.stream(alternatives).collect(toUnmodifiableList()));
  }

  /** A collector that collects the input {@code RegexPattern} as an alternation. */
  static Collector<RegexPattern, ?, RegexPattern> asAlternation() {
    return collectingAndThen(
        toUnmodifiableList(), list -> list.size() == 1 ? list.get(0) : new Alternation(list));
  }

  /** Returns a {@link CharacterSet} of the given elements. */
  @SafeVarargs
  static CharacterSet.AnyOf anyOf(CharSetElement... elements) {
    return new CharacterSet.AnyOf(Arrays.stream(elements).collect(toUnmodifiableList()));
  }

  /** Returns a {@link CharacterSet} of the given elements. */
  static CharacterSet.AnyOf anyOf(Collection<? extends CharSetElement> elements) {
    return new CharacterSet.AnyOf(elements.stream().collect(toUnmodifiableList()));
  }

  /** Returns a negated {@link CharacterSet} of the given elements. */
  @SafeVarargs
  static CharacterSet.NoneOf noneOf(CharSetElement... elements) {
    return new CharacterSet.NoneOf(Arrays.stream(elements).collect(toUnmodifiableList()));
  }

  /** Returns a negated {@link CharacterSet} of the given elements. */
  static CharacterSet.NoneOf noneOf(Collection<? extends CharSetElement> elements) {
    return new CharacterSet.NoneOf(elements.stream().collect(toUnmodifiableList()));
  }

  /**
   * Returns a pattern that matches {@code this} only if it is preceded by {@code prefix}.
   * Equivalent to {@code (?<=prefix)this}.
   */
  default RegexPattern precededBy(RegexPattern prefix) {
    return sequence(new Lookaround.Lookbehind(prefix), this);
  }

  /**
   * Returns a pattern that matches {@code this} only if it is followed by {@code suffix}.
   * Equivalent to {@code this(?=suffix)}.
   */
  default RegexPattern followedBy(RegexPattern suffix) {
    return sequence(this, new Lookaround.Lookahead(suffix));
  }

  /**
   * Returns a pattern that matches {@code this} only if it is NOT preceded by {@code prefix}.
   * Equivalent to {@code (?<!prefix)this}.
   */
  default RegexPattern notPrecededBy(RegexPattern prefix) {
    return sequence(new Lookaround.NegativeLookbehind(prefix), this);
  }

  /**
   * Returns a pattern that matches {@code this} only if it is NOT followed by {@code suffix}.
   * Equivalent to {@code this(?!suffix)}.
   */
  default RegexPattern notFollowedBy(RegexPattern suffix) {
    return sequence(this, new Lookaround.NegativeLookahead(suffix));
  }

  /** Represents a sequence of regex patterns that must match consecutively. */
  record Sequence(List<RegexPattern> elements) implements RegexPattern {
    public Sequence {
      checkArgument(elements.size() > 0, "elements cannot be empty");
    }

    @Override
    public String toString() {
      return elements.stream().map(Object::toString).collect(joining());
    }
  }

  /** Represents a choice between multiple alternative regex patterns. */
  record Alternation(List<RegexPattern> alternatives) implements RegexPattern {
    public Alternation {
      checkArgument(alternatives.size() > 0, "alternatives cannot be empty");
    }

    @Override
    public String toString() {
      return alternatives.stream().map(Object::toString).collect(joining("|"));
    }
  }

  /** Represents a regex pattern that is modified by a quantifier. */
  record Quantified(RegexPattern element, Quantifier quantifier) implements RegexPattern {
    @Override
    public String toString() {
      return element instanceof Sequence
              || element instanceof Alternation
              || element instanceof Quantified
          ? "(?:" + element + ")" + quantifier
          : element.toString() + quantifier;
    }
  }

  /** Base interface for all quantifier types. */
  sealed interface Quantifier extends UnaryOperator<RegexPattern> permits AtLeast, AtMost, Limited {
    boolean isReluctant();

    boolean isPossessive();

    Quantifier reluctant();

    Quantifier possessive();

    @Override
    default Quantified apply(RegexPattern pattern) {
      return new Quantified(pattern, this);
    }

    static AtLeast atLeast(int n) {
      checkArgument(n >= 0, "min must be non-negative");
      return new AtLeast(n, false, false);
    }

    static AtMost atMost(int n) {
      checkArgument(n >= 0, "max must be non-negative");
      return new AtMost(n, false, false);
    }

    static AtLeast repeated() {
      return new AtLeast(0, false, false);
    }

    static Quantifier repeated(int times) {
      return repeated(times, times);
    }

    static Quantifier repeated(int min, int max) {
      checkArgument(min >= 0, "min must be non-negative");
      checkArgument(max >= min, "max must be at least min");
      if (min == 0) {
        return atMost(max);
      }
      if (max == Integer.MAX_VALUE) {
        return atLeast(min);
      }
      return new Limited(min, max, false, false);
    }
  }

  /** Represents a quantifier with a minimum bound, like {@code {min,}}, {@code *}, or {@code +}. */
  record AtLeast(int min, boolean isReluctant, boolean isPossessive) implements Quantifier {
    @Override
    public AtLeast reluctant() {
      return new AtLeast(min, true, isPossessive);
    }

    @Override
    public AtLeast possessive() {
      return new AtLeast(min, isReluctant, true);
    }

    @Override
    public String toString() {
      StringBuilder builder =
          new StringBuilder((min == 0) ? "*" : (min == 1) ? "+" : "{" + min + ",}");
      if (isReluctant) {
        builder.append('?');
      }
      if (isPossessive) {
        builder.append('+');
      }
      return builder.toString();
    }
  }

  /**
   * Represents a quantifier with a maximum bound and a minimum of 0, like {@code {0,max}} or {@code
   * ?}.
   */
  record AtMost(int max, boolean isReluctant, boolean isPossessive) implements Quantifier {
    @Override
    public AtMost reluctant() {
      return new AtMost(max, true, isPossessive);
    }

    @Override
    public AtMost possessive() {
      return new AtMost(max, isReluctant, true);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder((max == 1) ? "?" : "{0," + max + "}");
      if (isReluctant) {
        builder.append('?');
      }
      if (isPossessive) {
        builder.append('+');
      }
      return builder.toString();
    }
  }

  /**
   * Represents a quantifier with both minimum and maximum bounds, like {@code {n}} or {@code
   * {min,max}}.
   */
  record Limited(int min, int max, boolean isReluctant, boolean isPossessive) implements Quantifier {
    @Override
    public Limited reluctant() {
      return new Limited(min, max, true, isPossessive);
    }

    @Override
    public Limited possessive() {
      return new Limited(min, max, isReluctant, true);
    }

    @Override
    public String toString() {
      StringBuilder builder =
          new StringBuilder((min == max) ? "{" + min + "}" : "{" + min + "," + max + "}");
      if (isReluctant) {
        builder.append('?');
      }
      if (isPossessive) {
        builder.append('+');
      }
      return builder.toString();
    }
  }

  /** Represents a grouping construct in a regex. */
  sealed interface Group extends RegexPattern
      permits Group.Capturing, Group.NonCapturing, Group.Named {

    /** A capturing group, like {@code (a)}. */
    record Capturing(RegexPattern content) implements Group {
      @Override
      public String toString() {
        return "(" + content + ")";
      }
    }

    /** A non-capturing group, like {@code (?:a)}. */
    record NonCapturing(RegexPattern content) implements Group {
      @Override
      public String toString() {
        return "(?:" + content + ")";
      }
    }

    /** A named capturing group, like {@code (?<name>a)}. */
    record Named(String name, RegexPattern content) implements Group {
      @Override
      public String toString() {
        return "(?<" + name + ">" + content + ")";
      }
    }
  }

  /** Represents a literal string to be matched. */
  record Literal(String value) implements RegexPattern {
    private static final CharPredicate META_CHARS = CharPredicate.anyOf(".[]{}()*+-?^$|\\");

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (META_CHARS.test(c)) {
          builder.append('\\');
        }
        builder.append(c);
      }
      return builder.toString();
    }
  }

  /** Represents a predefined character class like {@code \d} or {@code \w}. */
  public enum PredefinedCharClass implements RegexPattern, CharSetElement {
    ANY_CHAR("."),
    DIGIT("\\d"),
    NON_DIGIT("\\D"),
    WHITESPACE("\\s"),
    NON_WHITESPACE("\\S"),
    WORD("\\w"),
    NON_WORD("\\W");

    private final String pattern;

    PredefinedCharClass(String pattern) {
      this.pattern = pattern;
    }

    @Override
    public String toString() {
      return pattern;
    }
  }

  /** Represents a custom character class, like {@code [a-z]} or {@code [^0-9]}. */
  sealed interface CharacterSet extends RegexPattern
      permits CharacterSet.AnyOf, CharacterSet.NoneOf {

    /** A positive character class, like {@code [a-z]}. */
    record AnyOf(List<CharSetElement> elements) implements CharacterSet {
      public AnyOf {
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override
      public String toString() {
        return "[" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }
    }

    /** A negated character class, like {@code [^a-z]}. */
    record NoneOf(List<CharSetElement> elements) implements CharacterSet {
      public NoneOf {
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override
      public String toString() {
        return "[^" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }
    }
  }

  /** Base interface for elements within a {@link CharacterSet}. */
  sealed interface CharSetElement
      permits LiteralChar, CharRange,
        CharacterProperty,
        CharacterProperty.Negated,
        PredefinedCharClass {}

  /** Represents a single literal character within a character class. */
  record LiteralChar(char value) implements CharSetElement {
    @Override
    public String toString() {
      return switch (value) {
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        case '\t' -> "\\t";
        case '\f' -> "\\f";
        // Characters that are special inside character classes.
        case ']', '\\', '^', '&' -> "\\" + value;
        default -> String.valueOf(value);
      };
    }
  }

  /** Represents a range of characters within a character class, e.g., 'a-z'. */
  record CharRange(char start, char end) implements CharSetElement {
    @Override
    public String toString() {
      return new LiteralChar(start) + "-" + new LiteralChar(end);
    }
  }

  /** Represents a character property, like {@code \p{Lower}} or {@code \P{Lower}}. */
  public sealed interface CharacterProperty extends CharSetElement, RegexPattern
      permits PosixCharClass, UnicodeProperty {
    String propertyName();

    default Negated negated() {
      return new Negated(this);
    }

    /** Represents a negated character property, like {@code \P{Lower}}. */
    record Negated(CharacterProperty property) implements CharSetElement, RegexPattern {
      @Override
      public String toString() {
        return "\\P{" + property.propertyName() + "}";
      }
    }
  }

  /** Represents a POSIX character class inside a CharacterSet: e.g. \p{Lower} */
  public enum PosixCharClass implements CharacterProperty {
    LOWER("Lower", "lower"),
    UPPER("Upper", "upper"),
    ASCII("ASCII", "ASCII"),
    ALPHA("Alpha", "alpha"),
    DIGIT("Digit", "digit"),
    ALNUM("Alnum", "alnum"),
    PUNCT("Punct", "punct"),
    GRAPH("Graph", "graph"),
    PRINT("Print", "print"),
    BLANK("Blank", "blank"),
    CNTRL("Cntrl", "cntrl"),
    XDIGIT("XDigit", "xdigit"),
    SPACE("Space", "space");

    private final String posixName;
    private final String javaStyleName;

    PosixCharClass(String name, String alias) {
      this.posixName = name;
      this.javaStyleName = alias;
    }

    @Override
    public String propertyName() {
      return posixName;
    }

    /** Returns alternative name for this class, such as "lower" for "Lower". */
    public String javaStyleName() {
      return javaStyleName;
    }

    public Set<String> names() {
      return Stream.of(posixName, javaStyleName).collect(toUnmodifiableSet());
    }

    @Override
    public String toString() {
      return "\\p{" + posixName + "}";
    }
  }

  /** Represents a Unicode property class: e.g. \p{Nd} */
  public record UnicodeProperty(String propertyName) implements CharacterProperty {
    @Override
    public String toString() {
      return "\\p{" + propertyName + "}";
    }
  }

  /** Represents an anchor, which matches a position like start or end of a line. */
  enum Anchor implements RegexPattern {
    BEGINNING("^"),
    END("$"),
    DOC_BEGINNING("\\A"),
    DOC_END("\\Z"),
    DOC_ABSOLUTE_END("\\z"),
    WORD_BOUNDARY("\\b"),
    NON_WORD_BOUNDARY("\\B");

    private final String pattern;

    Anchor(String pattern) {
      this.pattern = pattern;
    }

    @Override
    public String toString() {
      return pattern;
    }
  }

  /** Represents a lookaround assertion: (?=...), (?!...), (?<=...), (?<!...). */
  sealed interface Lookaround extends RegexPattern
      permits Lookaround.Lookahead,
          Lookaround.NegativeLookahead,
          Lookaround.Lookbehind,
          Lookaround.NegativeLookbehind {

    /** Returns the AST node representing the pattern inside the lookaround. */
    RegexPattern target();

    /** Positive lookahead: {@code (?=pattern)}. */
    record Lookahead(RegexPattern target) implements Lookaround {
      @Override
      public String toString() {
        return "(?=" + target + ")";
      }
    }

    /** Negative lookahead: {@code (?!pattern)}. */
    record NegativeLookahead(RegexPattern target) implements Lookaround {
      @Override
      public String toString() {
        return "(?!" + target + ")";
      }
    }

    /** Positive lookbehind: {@code (?<=pattern)}. */
    record Lookbehind(RegexPattern target) implements Lookaround {
      @Override
      public String toString() {
        return "(?<=" + target + ")";
      }
    }

    /** Negative lookbehind: {@code (?<!pattern)}. */
    record NegativeLookbehind(RegexPattern target) implements Lookaround {
      @Override
      public String toString() {
        return "(?<!" + target + ")";
      }
    }
  }

  /**
   * Parses the given regular expression string and returns its {@link RegexPattern} representation.
   *
   * @throws Parser.ParseException if the regex pattern is malformed
   * @throws IllegalArgumentException if the regex pattern is invalid
   */
  public static RegexPattern parse(String regex) {
    return regex.isEmpty() ? new Literal("") : RegexParsers.pattern().parse(regex);
  }
}

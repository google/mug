package com.google.common.labs.regex;

import static com.google.common.labs.regex.InternalUtils.checkArgument;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collector;

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
        RegexPattern.AnyChar,
        RegexPattern.PredefinedCharClass,
        RegexPattern.CharacterClass,
        RegexPattern.Anchor,
        RegexPattern.Lookaround {
  /** Returns a {@link Sequence} of the given elements. */
  @SafeVarargs
  static Sequence sequence(RegexPattern... elements) {
    return new Sequence(Arrays.stream(elements).collect(toUnmodifiableList()));
  }

  static Collector<RegexPattern, ?, Sequence> toSequence() {
    return collectingAndThen(toUnmodifiableList(), Sequence::new);
  }

  /** Returns an {@link Alternation} of the given alternatives. */
  @SafeVarargs
  static Alternation alternation(RegexPattern... alternatives) {
    return new Alternation(Arrays.stream(alternatives).collect(toUnmodifiableList()));
  }

  static Collector<RegexPattern, ?, Alternation> toAlternation() {
    return collectingAndThen(toUnmodifiableList(), Alternation::new);
  }

  /** Returns a {@link CharacterClass} of the given elements. */
  @SafeVarargs
  static CharacterClass.AnyOf anyOf(CharSetElement... elements) {
    return new CharacterClass.AnyOf(Arrays.stream(elements).collect(toUnmodifiableList()));
  }

  /** Returns a {@link CharacterClass} of the given elements. */
  static CharacterClass.AnyOf anyOf(Collection<? extends CharSetElement> elements) {
    return new CharacterClass.AnyOf(elements.stream().collect(toUnmodifiableList()));
  }

  /** Returns a negated {@link CharacterClass} of the given elements. */
  @SafeVarargs
  static CharacterClass.NoneOf noneOf(CharSetElement... elements) {
    return new CharacterClass.NoneOf(Arrays.stream(elements).collect(toUnmodifiableList()));
  }

  /** Returns a negated {@link CharacterClass} of the given elements. */
  static CharacterClass.NoneOf noneOf(Collection<? extends CharSetElement> elements) {
    return new CharacterClass.NoneOf(elements.stream().collect(toUnmodifiableList()));
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
  sealed interface Quantifier permits AtLeast, AtMost, Limited {
    boolean isGreedy();

    static AtLeast atLeast(int n) {
      checkArgument(n >= 0, "min must be non-negative");
      return new AtLeast(n, true);
    }

    static AtMost atMost(int n) {
      checkArgument(n >= 0, "max must be non-negative");
      return new AtMost(n, true);
    }

    static AtLeast repeated() {
      return new AtLeast(0, true);
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
      return new Limited(min, max, true);
    }
  }

  /** Represents a quantifier with a minimum bound, like {@code {min,}}, {@code *}, or {@code +}. */
  record AtLeast(int min, boolean greedy) implements Quantifier {
    @Override
    public boolean isGreedy() {
      return greedy;
    }

    @Override
    public String toString() {
      String s = (min == 0) ? "*" : (min == 1) ? "+" : "{" + min + ",}";
      return greedy ? s : s + "?";
    }
  }

  /**
   * Represents a quantifier with a maximum bound and a minimum of 0, like {@code {0,max}} or {@code
   * ?}.
   */
  record AtMost(int max, boolean greedy) implements Quantifier {
    @Override
    public boolean isGreedy() {
      return greedy;
    }

    @Override
    public String toString() {
      String s = (max == 1) ? "?" : "{0," + max + "}";
      return greedy ? s : s + "?";
    }
  }

  /**
   * Represents a quantifier with both minimum and maximum bounds, like {@code {n}} or {@code
   * {min,max}}.
   */
  record Limited(int min, int max, boolean greedy) implements Quantifier {
    @Override
    public boolean isGreedy() {
      return greedy;
    }

    @Override
    public String toString() {
      String s = (min == max) ? "{" + min + "}" : "{" + min + "," + max + "}";
      return greedy ? s : s + "?";
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

  /** Represents the dot ('.') character, which matches any character. */
  record AnyChar() implements RegexPattern {
    @Override
    public String toString() {
      return ".";
    }
  }

  /** Represents a predefined character class like {@code \d} or {@code \w}. */
  public enum PredefinedCharClass implements RegexPattern {
    DIGIT("\\d"),
    NON_DIGIT("\\D"),
    WHITESPACE("\\s"),
    NON_WHITESPACE("\\S"),
    WORD("\\w"),
    NON_WORD("\\W"),
    WORD_BOUNDARY("\\b"),
    NON_WORD_BOUNDARY("\\B");

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
  sealed interface CharacterClass extends RegexPattern
      permits CharacterClass.AnyOf, CharacterClass.NoneOf {

    /** A positive character class, like {@code [a-z]}. */
    record AnyOf(List<CharSetElement> elements) implements CharacterClass {
      public AnyOf {
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override
      public String toString() {
        return "[" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }
    }

    /** A negated character class, like {@code [^a-z]}. */
    record NoneOf(List<CharSetElement> elements) implements CharacterClass {
      public NoneOf {
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override
      public String toString() {
        return "[^" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }
    }
  }

  /** Base interface for elements within a {@link CharacterClass}. */
  sealed interface CharSetElement permits LiteralChar, CharRange {}

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

  /** Represents an anchor, which matches a position like start or end of a line. */
  sealed interface Anchor extends RegexPattern permits Anchor.AtBeginning, Anchor.AtEnd {
    /** Represents the start of the input string anchor (^). */
    record AtBeginning() implements Anchor {
      @Override
      public String toString() {
        return "^";
      }
    }

    /** Represents the end of the input string anchor ($). */
    record AtEnd() implements Anchor {
      @Override
      public String toString() {
        return "$";
      }
    }
  }

  /** Represents a lookaround assertion: (?=...), (?!...), (?<=...), (?<!...). */
  sealed interface Lookaround extends RegexPattern
      permits Lookaround.Lookahead,
          Lookaround.NegativeLookahead,
          Lookaround.Lookbehind,
          Lookaround.NegativeLookbehind {

    /** Returns the AST node representing the pattern inside the lookaround. */
    RegexPattern subject();

    /** Positive lookahead: {@code (?=pattern)}. */
    record Lookahead(RegexPattern subject) implements Lookaround {
      @Override
      public String toString() {
        return "(?=" + subject + ")";
      }
    }

    /** Negative lookahead: {@code (?!pattern)}. */
    record NegativeLookahead(RegexPattern subject) implements Lookaround {
      @Override
      public String toString() {
        return "(?!" + subject + ")";
      }
    }

    /** Positive lookbehind: {@code (?<=pattern)}. */
    record Lookbehind(RegexPattern subject) implements Lookaround {
      @Override
      public String toString() {
        return "(?<=" + subject + ")";
      }
    }

    /** Negative lookbehind: {@code (?<!pattern)}. */
    record NegativeLookbehind(RegexPattern subject) implements Lookaround {
      @Override
      public String toString() {
        return "(?<!" + subject + ")";
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
    return RegexParsers.pattern().parse(regex);
  }
}

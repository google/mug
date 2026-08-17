/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.common.labs.regex;

import static com.google.common.labs.regex.InternalUtils.checkArgument;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.stream.MoreStreams.mergeConsecutive;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.labs.parse.Parser;
import com.google.mu.annotations.ParametersMustMatchByName;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Defines the Abstract Syntax Tree (AST) for a regular expression.
 *
 * <p>This AST is used to represent parsed regular expressions, as a basis to enable static analysis
 * of regexes.
 */
public sealed interface RegexPattern {

  /**
   * Common metadata shared by all regex patterns.
   *
   * @param minSize the minimum match size of this pattern in UTF-16 code units (chars).
   *     Particularly, optional patterns like {@code .?}, {@code .*}, {@code c*}, <code>foo{,2}
   *     </code> will return 0.
   * @param maxSize the maximum match size of this pattern in UTF-16 code units (chars), or {@link
   *     Integer#MAX_VALUE} if it can match infinitely long strings (e.g. {@code .*}, {@code \d+},
   *     <code>foo{1,}</code> etc).
   * @since 10.9
   */
  record Metadata(int minSize, int maxSize) {
    @ParametersMustMatchByName
    public Metadata {
      checkArgument(minSize >= 0, "minSize cannot be negative: %s", minSize);
      checkArgument(
          maxSize >= minSize, "maxSize (%s) cannot be less than minSize (%s)", maxSize, minSize);
    }
  }

  /**
   * Returns this pattern's metadata that may be useful for static analysis.
   *
   * @since 10.9
   */
  Metadata metadata();

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
          var flattened = list.stream().flatMap(RegexPattern::flattenSequences);
          // Then merge adjacent literals
          List<RegexPattern> segments = mergeConsecutive(
                  flattened, Literal.class, (a, b) -> new Literal(a.value() + b.value()))
              .collect(toUnmodifiableList());
          // Wrap in sequence if needed.
          return segments.size() == 1 ? segments.get(0) : new Sequence(segments);
        });
  }

  private static Stream<RegexPattern> flattenSequences(RegexPattern pattern) {
    return pattern instanceof Sequence seq
        ? seq.elements().stream().flatMap(RegexPattern::flattenSequences)
        : Stream.of(pattern);
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

  /** Returns a character set intersection of the given character sets. */
  @SafeVarargs
  static CharacterSet.Intersection intersection(CharacterSet... operands) {
    return new CharacterSet.Intersection(Arrays.stream(operands).collect(toUnmodifiableList()));
  }

  /** Returns a character set intersection of the given character sets. */
  static CharacterSet.Intersection intersection(Collection<? extends CharacterSet> operands) {
    return new CharacterSet.Intersection(operands.stream().collect(toUnmodifiableList()));
  }

  /** A collector that collects the input {@code CharacterSet} as an intersection. */
  static Collector<CharacterSet, ?, CharacterSet> asIntersection() {
    return collectingAndThen(
        toUnmodifiableList(),
        list -> list.size() == 1 ? list.get(0) : new CharacterSet.Intersection(list));
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
      elements = List.copyOf(elements);
      checkArgument(elements.size() > 0, "elements cannot be empty");
    }

    @Override public Metadata metadata() {
      int minSize = 0;
      int maxSize = 0;
      for (RegexPattern element : elements) {
        Metadata metadata = element.metadata();
        minSize = SafeMath.saturatedAdd(minSize, metadata.minSize());
        maxSize = SafeMath.saturatedAdd(maxSize, metadata.maxSize());
      }
      return new Metadata(minSize, maxSize);
    }

    @Override public String toString() {
      return elements.stream().map(Object::toString).collect(joining());
    }
  }

  /** Represents a choice between multiple alternative regex patterns. */
  record Alternation(List<RegexPattern> alternatives) implements RegexPattern {
    public Alternation {
      alternatives = List.copyOf(alternatives);
      checkArgument(alternatives.size() > 0, "alternatives cannot be empty");
    }

    @Override public Metadata metadata() {
      return new Metadata(
          alternatives.stream().mapToInt(p -> p.metadata().minSize()).min().orElse(0),
          alternatives.stream().mapToInt(p -> p.metadata().maxSize()).max().orElse(0));
    }

    @Override public String toString() {
      return alternatives.stream().map(Object::toString).collect(joining("|"));
    }
  }

  /** Represents a regex pattern that is modified by a quantifier. */
  record Quantified(RegexPattern element, Quantifier quantifier) implements RegexPattern {

    @Override public Metadata metadata() {
      Metadata elementMetadata = element.metadata();
      int elementMin = elementMetadata.minSize();
      int minSize =
          elementMin == 0
              ? 0
              : switch (quantifier) {
                case AtLeast atLeast -> SafeMath.saturatedMultiply(elementMin, atLeast.min());
                case AtMost atMost -> 0;
                case Limited limited -> SafeMath.saturatedMultiply(elementMin, limited.min());
              };
      int elementMax = elementMetadata.maxSize();
      int maxSize =
          elementMax == 0
              ? 0
              : switch (quantifier) {
                case AtLeast atLeast -> Integer.MAX_VALUE;
                case AtMost atMost -> SafeMath.saturatedMultiply(elementMax, atMost.max());
                case Limited limited -> SafeMath.saturatedMultiply(elementMax, limited.max());
              };
      return new Metadata(minSize, maxSize);
    }

    @Override public String toString() {
      return element instanceof Sequence || element instanceof Alternation
              || element instanceof Quantified
              || (element instanceof Literal lit && lit.value().length() != 1)
          ? "(?:" + element + ")" + quantifier
          : element.toString() + quantifier;
    }
  }

  /** Base interface for all quantifier types. */
  sealed interface Quantifier extends UnaryOperator<RegexPattern> {
    boolean isReluctant();
    boolean isPossessive();
    Quantifier reluctant();
    Quantifier possessive();

    @Override default Quantified apply(RegexPattern pattern) {
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
    public AtLeast {
      checkArgument(min >= 0, "min must be non-negative");
      checkArgument(!(isReluctant && isPossessive), "cannot be both reluctant and possessive");
    }

    @Override public AtLeast reluctant() {
      return new AtLeast(min, true, false);
    }

    @Override public AtLeast possessive() {
      return new AtLeast(min, false, true);
    }

    @Override public String toString() {
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
    public AtMost {
      checkArgument(max >= 0, "max must be non-negative");
      checkArgument(!(isReluctant && isPossessive), "cannot be both reluctant and possessive");
    }

    @Override public AtMost reluctant() {
      return new AtMost(max, true, false);
    }

    @Override public AtMost possessive() {
      return new AtMost(max, false, true);
    }

    @Override public String toString() {
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
  record Limited(int min, int max, boolean isReluctant, boolean isPossessive)
      implements Quantifier {
    public Limited {
      checkArgument(min >= 0, "min must be non-negative");
      checkArgument(max >= min, "max must be at least min");
      checkArgument(!(isReluctant && isPossessive), "cannot be both reluctant and possessive");
    }

    @Override public Limited reluctant() {
      return new Limited(min, max, true, false);
    }

    @Override public Limited possessive() {
      return new Limited(min, max, false, true);
    }

    @Override public String toString() {
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

  /** Regex modifiers that can be enabled or disabled inline. */
  enum ModifierFlag {
    CASE_INSENSITIVE("i"),
    UNIX_LINES("d"),
    MULTILINE("m"),
    DOTALL("s"),
    UNICODE_CASE("u"),
    COMMENTS("x"),
    UNICODE_CHARACTER_CLASS("U");

    private final String shortName;

    ModifierFlag(String shortName) {
      this.shortName = shortName;
    }

    @Override public String toString() {
      return shortName;
    }
  }

  /** Represents a grouping construct in a regex. */
  sealed interface Group extends RegexPattern {
    RegexPattern content();

    @Override default Metadata metadata() {
      return content().metadata();
    }

    /** A capturing group, like {@code (a)}. */
    record Capturing(RegexPattern content) implements Group {
      @Override public String toString() {
        return "(" + content + ")";
      }
    }

    /** A non-capturing group, like {@code (?:a)}. */
    record NonCapturing(
        RegexPattern content, List<ModifierFlag> enabledModifierFlags,
        List<ModifierFlag> disabledModifierFlags)
        implements Group {
      public NonCapturing {
        enabledModifierFlags = List.copyOf(enabledModifierFlags);
        disabledModifierFlags = List.copyOf(disabledModifierFlags);
      }

      public NonCapturing(RegexPattern content) {
        this(content, List.of(), List.of());
      }

      @Override public String toString() {
        if (content instanceof Literal lit && lit.value().isEmpty() && hasModifierFlags()) {
          return "(?" + formatFlags() + ")";
        }
        return "(?" + formatFlags() + ":" + content + ")";
      }

      private boolean hasModifierFlags() {
        return !enabledModifierFlags.isEmpty() || !disabledModifierFlags.isEmpty();
      }

      private String formatFlags() {
        String enabledStr = enabledModifierFlags.stream().map(Object::toString).collect(joining());
        if (disabledModifierFlags.isEmpty()) {
          return enabledStr;
        }
        String disabledStr =
            disabledModifierFlags.stream().map(Object::toString).collect(joining());
        return enabledStr + "-" + disabledStr;
      }
    }

    /** A named capturing group, like {@code (?<name>a)}. */
    record Named(String name, RegexPattern content) implements Group {
      @Override public String toString() {
        return "(?<" + name + ">" + content + ")";
      }
    }

    /** An atomic group, like {@code (?>a)}. */
    record Atomic(RegexPattern content) implements Group {
      @Override public String toString() {
        return "(?>" + content + ")";
      }
    }
  }

  /** Represents a literal string to be matched. */
  record Literal(String value) implements RegexPattern {
    private static final Substring.RepeatingPattern META_CHARS =
        all(CharPredicate.anyOf(".[]{}()*+-?^$|\\"));

    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ value.length(), /* maxSize= */ value.length());
    }

    @Override public String toString() {
      return META_CHARS.replaceAllFrom(value, m -> "\\" + m);
    }
  }

  /** Represents a backreference to a capturing group. */
  sealed interface Backreference extends RegexPattern {

    @Override default Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ Integer.MAX_VALUE);
    }

    record Numbered(int groupNumber) implements Backreference {
      public Numbered {
        checkArgument(groupNumber > 0, "group number must be positive: %s", groupNumber);
      }

      @Override public String toString() {
        return "\\" + groupNumber;
      }
    }

    record Named(String groupName) implements Backreference {
      @Override public String toString() {
        return "\\k<" + groupName + ">";
      }
    }
  }

  /** Represents a predefined character class like {@code \d} or {@code \w}. */
  enum PredefinedCharClass implements RegexPattern, CharSetElement {
    ANY_CHAR("."),
    DIGIT("\\d"),
    NON_DIGIT("\\D"),
    WHITESPACE("\\s"),
    NON_WHITESPACE("\\S"),
    WORD("\\w"),
    NON_WORD("\\W"),
    LINEBREAK("\\R");

    private final String pattern;

    PredefinedCharClass(String pattern) {
      this.pattern = pattern;
    }

    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ 1, /* maxSize= */ 2);
    }

    @Override public String toString() {
      return pattern;
    }
  }

  /** Represents a custom character class, like {@code [a-z]} or {@code [^0-9]}. */
  sealed interface CharacterSet extends RegexPattern, CharSetElement {

    @Override default Metadata metadata() {
      return new Metadata(/* minSize= */ 1, /* maxSize= */ 2);
    }

    default String elementString() {
      return toString();
    }

    /** A positive character class, like {@code [a-z]}. */
    record AnyOf(List<CharSetElement> elements) implements CharacterSet {
      public AnyOf {
        elements = List.copyOf(elements);
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override public String elementString() {
        return elements.stream().map(Object::toString).collect(joining());
      }

      @Override public String toString() {
        return "[" + elementString() + "]";
      }
    }

    /** A negated character class, like {@code [^a-z]}. */
    record NoneOf(List<CharSetElement> elements) implements CharacterSet {
      public NoneOf {
        elements = List.copyOf(elements);
        checkArgument(elements.size() > 0, "elements cannot be empty");
      }

      @Override public String elementString() {
        return "[^" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }

      @Override public String toString() {
        return "[^" + elements.stream().map(Object::toString).collect(joining()) + "]";
      }
    }

    /** An intersection of character classes, like {@code [a-z&&[^bc]]}. */
    record Intersection(List<CharacterSet> operands) implements CharacterSet {
      public Intersection {
        operands = List.copyOf(operands);
        checkArgument(operands.size() > 0, "operands cannot be empty");
      }

      @Override public String elementString() {
        return operands.stream().map(CharacterSet::elementString).collect(joining("&&"));
      }

      @Override public String toString() {
        if (operands.get(0) instanceof NoneOf noneOf) {
          return "[^"
              + noneOf.elements().stream().map(Object::toString).collect(joining())
              + "&&"
              + operands.subList(1, operands.size()).stream()
                  .map(CharacterSet::elementString)
                  .collect(joining("&&"))
              + "]";
        }
        return "[" + elementString() + "]";
      }
    }
  }

  /** Base interface for elements within a {@link CharacterSet}. */
  sealed interface CharSetElement {}

  /** Represents a single literal character within a character class. */
  record LiteralChar(char value) implements CharSetElement {
    @Override public String toString() {
      return switch (value) {
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        case '\t' -> "\\t";
        case '\f' -> "\\f";
        // Characters that are special inside character classes.
        case ']', '\\', '^', '&', '-' -> "\\" + value;
        default -> String.valueOf(value);
      };
    }
  }

  /** Represents a range of characters within a character class, e.g., 'a-z'. */
  record CharRange(char start, char end) implements CharSetElement {
    @Override public String toString() {
      return new LiteralChar(start) + "-" + new LiteralChar(end);
    }
  }

  /** Represents a character property, like {@code \p{Lower}} or {@code \P{Lower}}. */
  sealed interface CharacterProperty extends CharSetElement, RegexPattern {
    String propertyName();

    @Override default Metadata metadata() {
      return new Metadata(/* minSize= */ 1, /* maxSize= */ 2);
    }

    default Negated negated() {
      return new Negated(this);
    }

    /** Represents a negated character property, like {@code \P{Lower}}. */
    record Negated(CharacterProperty property) implements CharSetElement, RegexPattern {

      @Override public Metadata metadata() {
        return new Metadata(/* minSize= */ 1, /* maxSize= */ 2);
      }

      @Override public String toString() {
        return "\\P{" + property.propertyName() + "}";
      }
    }
  }

  /** Represents a POSIX character class inside a CharacterSet: e.g. \p{Lower} */
  enum PosixCharClass implements CharacterProperty {
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

    @Override public String propertyName() {
      return posixName;
    }

    /** Returns alternative name for this class, such as "lower" for "Lower". */
    public String javaStyleName() {
      return javaStyleName;
    }

    public Set<String> names() {
      return Stream.of(posixName, javaStyleName).collect(toUnmodifiableSet());
    }

    @Override public String toString() {
      return "\\p{" + posixName + "}";
    }
  }

  /** Represents a Unicode property class: e.g. \p{Nd} */
  record UnicodeProperty(String propertyName) implements CharacterProperty {
    @Override public String toString() {
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

    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ 0);
    }

    @Override public String toString() {
      return pattern;
    }
  }

  /**
   * Represents a lookaround assertion: {@code (?=...)}, {@code (?!...)}, {@code (?<=...)}, {@code
   * (?<!...)}.
   */
  sealed interface Lookaround extends RegexPattern {

    @Override default Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ 0);
    }

    /** Returns the AST node representing the pattern inside the lookaround. */
    RegexPattern target();

    /** Positive lookahead: {@code (?=pattern)}. */
    record Lookahead(RegexPattern target) implements Lookaround {
      @Override public String toString() {
        return "(?=" + target + ")";
      }
    }

    /** Negative lookahead: {@code (?!pattern)}. */
    record NegativeLookahead(RegexPattern target) implements Lookaround {
      @Override public String toString() {
        return "(?!" + target + ")";
      }
    }

    /** Positive lookbehind: {@code (?<=pattern)}. */
    record Lookbehind(RegexPattern target) implements Lookaround {
      @Override public String toString() {
        return "(?<=" + target + ")";
      }
    }

    /** Negative lookbehind: {@code (?<!pattern)}. */
    record NegativeLookbehind(RegexPattern target) implements Lookaround {
      @Override public String toString() {
        return "(?<!" + target + ")";
      }
    }
  }

  /**
   * Parses the given regular expression string and returns its {@link RegexPattern} representation.
   *
   * @deprecated use {@link #of} instead
   */
  @Deprecated
  static RegexPattern parse(String regex) {
    return of(regex);
  }

  /**
   * Parses the given regular expression string and returns its {@link RegexPattern} representation.
   *
   * @throws Parser.ParseException if the regex pattern is malformed
   * @throws IllegalArgumentException if the regex pattern is invalid
   * @since 10.8
   */
  static RegexPattern of(String regex) {
    Parser<RegexPattern>.OrEmpty parser = RegexParsers.PARSER.orElse(new Literal(""));
    return after(prefix("(?x)")).from(regex)
        .map(p -> parser.parseSkipping(RegexParsers.FREE_SPACES, p))
        .orElseGet(() -> parser.parse(regex));
  }
}

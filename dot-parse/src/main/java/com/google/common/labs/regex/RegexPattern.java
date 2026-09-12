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
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.labs.parse.Parser;
import com.google.mu.annotations.ParametersMustMatchByName;
import java.util.ArrayList;
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
  static Sequence sequence(RegexPattern... elements) {
    return new Sequence(List.of(elements));
  }

  /**
   * A collector that collects the input {@code RegexPattern} as a sequence. Nested sequences are
   * flattened and adjacent literals are concatenated as a single literal.
   */
  static Collector<RegexPattern, ?, RegexPattern> inSequence() {
    class Builder {
      private final List<RegexPattern> elements = new ArrayList<>();
      private RegexPattern top;

      void add(RegexPattern pattern) {
        if (pattern instanceof Literal literal && top instanceof Literal prev) {
          top = new Literal(prev.value() + literal.value());
          elements.set(elements.size() - 1, top);
        } else if (pattern instanceof Sequence seq) {
          seq.elements().forEach(this::add);
        } else {
          top = pattern;
          elements.add(pattern);
        }
      }

      Builder addAll(Builder that) {
        that.elements.forEach(this::add);
        return this;
      }

      RegexPattern build() {
        return elements.size() == 1 ? elements.get(0) : new Sequence(elements);
      }
    }
    return Collector.of(Builder::new, Builder::add, Builder::addAll, Builder::build);
  }

  /** Returns an {@link Alternation} of the given alternatives. */
  static Alternation alternation(RegexPattern... alternatives) {
    return new Alternation(List.of(alternatives));
  }

  /** A collector that collects the input {@code RegexPattern} as an alternation. */
  static Collector<RegexPattern, ?, RegexPattern> asAlternation() {
    return collectingAndThen(
        toUnmodifiableList(), list -> list.size() == 1 ? list.get(0) : new Alternation(list));
  }

  /** Returns a {@link CharacterSet} of the given elements. */
  static CharacterSet.AnyOf anyOf(CharSetElement... elements) {
    return anyOf(List.of(elements));
  }

  /** Returns a {@link CharacterSet} of the given elements. */
  static CharacterSet.AnyOf anyOf(Collection<? extends CharSetElement> elements) {
    return new CharacterSet.AnyOf(List.copyOf(elements));
  }

  /** Returns a negated {@link CharacterSet} of the given elements. */
  static CharacterSet.NoneOf noneOf(CharSetElement... elements) {
    return noneOf(List.of(elements));
  }

  /** Returns a negated {@link CharacterSet} of the given elements. */
  static CharacterSet.NoneOf noneOf(Collection<? extends CharSetElement> elements) {
    return new CharacterSet.NoneOf(List.copyOf(elements));
  }

  /** Returns a character set intersection of the given character sets. */
  static CharacterSet.Intersection intersection(CharacterSet... operands) {
    return intersection(List.of(operands));
  }

  /** Returns a character set intersection of the given character sets. */
  static CharacterSet.Intersection intersection(Collection<? extends CharacterSet> operands) {
    return new CharacterSet.Intersection(List.copyOf(operands));
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
      elements = flatten(elements);
      checkArgument(elements.size() > 0, "elements cannot be empty");
    }

    private static List<RegexPattern> flatten(List<RegexPattern> elements) {
      boolean hasNested = false;
      for (RegexPattern element : elements) {
        if (element instanceof Sequence) {
          hasNested = true;
          break;
        }
      }
      if (!hasNested) {
        return List.copyOf(elements);
      }
      List<RegexPattern> flattened = new ArrayList<>(elements.size() + 4);
      addFlattened(flattened, elements);
      return List.copyOf(flattened);
    }

    private static void addFlattened(List<RegexPattern> target, List<RegexPattern> elements) {
      for (RegexPattern element : elements) {
        if (element instanceof Sequence seq) {
          addFlattened(target, seq.elements());
        } else {
          target.add(element);
        }
      }
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
      StringBuilder builder = new StringBuilder();
      boolean afterGroupNumber = false;
      boolean directivesOnly = true;
      for (int i = 0; i < elements.size(); i++) {
        RegexPattern element = elements.get(i);
        // `|` binds loosest, so a nested alternation has to be grouped to stay one element. The
        // exception is a trailing alternation behind nothing but modifier directives: Java scopes
        // their flags to the end of the enclosing group, so `(?i)a|b` needs no parentheses, and
        // that is the shape a leading `(?i)` with a top-level `|` parses to.
        boolean scopeSpanning = directivesOnly && i == elements.size() - 1;
        String rendered =
            element instanceof Alternation && !scopeSpanning
                ? "(?:" + element + ")"
                : element.toString();
        // `\1` followed by `0` must not render as `\10`, which reads back as group 10. Only a
        // directly adjacent sibling is considered; the parser never nests a Sequence in a Sequence.
        if (afterGroupNumber && startsWithDigit(rendered)) {
          builder.append("\\x3");
        }
        builder.append(rendered);
        afterGroupNumber = element instanceof Backreference.Numbered;
        directivesOnly &= element instanceof ModifierDirective;
      }
      return builder.toString();
    }

    /** True if {@code rendered} starts with an ASCII digit, which {@code \x3N} can escape. */
    private static boolean startsWithDigit(String rendered) {
      return rendered.length() > 0 && rendered.charAt(0) >= '0' && rendered.charAt(0) <= '9';
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
          alternatives.stream().mapToInt(p -> p.metadata().minSize()).min().getAsInt(),
          alternatives.stream().mapToInt(p -> p.metadata().maxSize()).max().getAsInt());
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

    /**
     * @since 10.9
     */
    int min();

    /**
     * @since 10.9
     */
    int max();

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
      // Unbounded first: {0,} is `*`, not {0,Integer.MAX_VALUE}. But `{n}` is an exact count even
      // when n happens to be Integer.MAX_VALUE, so the sentinel only applies above the min.
      if (max == Integer.MAX_VALUE && max > min) {
        return atLeast(min);
      }
      if (min == 0) {
        return atMost(max);
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

    @Override public int max() {
      return Integer.MAX_VALUE;
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

    @Override public int min() {
      return 0;
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
    UNICODE_CHARACTER_CLASS("U"),
    CANONICAL_EQUIVALENCE("c");

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
        return "(?" + formatFlags(enabledModifierFlags, disabledModifierFlags) + ":" + content
            + ")";
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

  /**
   * A standalone modifier directive, like {@code (?i)} or {@code (?is-m)}, which turns flags on or
   * off for the remainder of the enclosing group. Unlike {@code (?i:)}, whose flags are scoped to
   * the (empty) group content, a directive affects everything that follows it.
   */
  record ModifierDirective(
      List<ModifierFlag> enabledModifierFlags, List<ModifierFlag> disabledModifierFlags)
      implements RegexPattern {
    public ModifierDirective {
      enabledModifierFlags = List.copyOf(enabledModifierFlags);
      disabledModifierFlags = List.copyOf(disabledModifierFlags);
    }

    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ 0);
    }

    @Override public String toString() {
      return "(?" + formatFlags(enabledModifierFlags, disabledModifierFlags) + ")";
    }
  }

  private static String formatFlags(
      List<ModifierFlag> enabledModifierFlags, List<ModifierFlag> disabledModifierFlags) {
    String enabled = enabledModifierFlags.stream().map(Object::toString).collect(joining());
    if (disabledModifierFlags.isEmpty()) {
      return enabled;
    }
    return enabled + "-" + disabledModifierFlags.stream().map(Object::toString).collect(joining());
  }

  /** Represents a literal string to be matched. */
  record Literal(String value) implements RegexPattern {
    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ value.length(), /* maxSize= */ value.length());
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      value
          .codePoints()
          .forEach(cp -> {
            switch (cp) {
              case '\n' -> sb.append("\\n");
              case '\r' -> sb.append("\\r");
              case '\t' -> sb.append("\\t");
              case '\f' -> sb.append("\\f");
              // ' ' and '#' are only special under free spacing, but escaping them unconditionally
              // keeps the rendering correct regardless of the flags in effect.
              case '.',
                  '[',
                  ']',
                  '{',
                  '}',
                  '(',
                  ')',
                  '*',
                  '+',
                  '-',
                  '?',
                  '^',
                  '$',
                  '|',
                  '\\',
                  ' ',
                  '#' ->
                  sb.append('\\').append((char) cp);
              default -> {
                if (cp < 0x20 || cp == 0x7F
                    || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE)) {
                  sb.append(String.format("\\u%04X", cp));
                } else {
                  sb.appendCodePoint(cp);
                }
              }
            }
          });
      return sb.toString();
    }
  }

  /** Represents a backreference to a capturing group. */
  sealed interface Backreference extends RegexPattern {
    @Override default Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ Integer.MAX_VALUE);
    }

    /**
     * A backreference to a capturing group by number, like {@code \1}.
     *
     * <p>When parsed via {@link RegexPattern#of}, digits are consumed only while the number does
     * not exceed the count of capturing groups seen so far (the first digit is always consumed),
     * matching {@link java.util.regex.Pattern} semantics. Any remaining digits become literal
     * characters.
     */
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
    HORIZONTAL_WHITESPACE("\\h"),
    NON_HORIZONTAL_WHITESPACE("\\H"),
    VERTICAL_WHITESPACE("\\v"),
    NON_VERTICAL_WHITESPACE("\\V"),
    LINEBREAK("\\R"),
    EXTENDED_GRAPHEME_CLUSTER("\\X");

    private final String pattern;

    PredefinedCharClass(String pattern) {
      this.pattern = pattern;
    }

    @Override public Metadata metadata() {
      return this == EXTENDED_GRAPHEME_CLUSTER
          ? new Metadata(/* minSize= */ 1, /* maxSize= */ Integer.MAX_VALUE)
          : new Metadata(/* minSize= */ 1, /* maxSize= */ 2);
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
        return "[" + elementString() + "]";
      }
    }
  }

  /** Base interface for elements within a {@link CharacterSet}. */
  sealed interface CharSetElement {}

  /** Represents a single literal character or code point within a character class. */
  record LiteralChar(int codePoint) implements CharSetElement {
    public LiteralChar {
      checkArgument(Character.isValidCodePoint(codePoint), "not a valid code point: %s", codePoint);
    }

    @Override public String toString() {
      return switch (codePoint) {
        case '\n' -> "\\n";
        case '\r' -> "\\r";
        case '\t' -> "\\t";
        case '\f' -> "\\f";
        // Characters that are special inside character classes. ' ' and '#' are only special under
        // free spacing, but escaping them unconditionally keeps the rendering correct regardless of
        // the flags in effect.
        case '[', ']', '\\', '^', '&', '-', ' ', '#' -> "\\" + (char) codePoint;
        default -> {
          if (codePoint < 0x20 || codePoint == 0x7F
              || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
            yield String.format("\\u%04X", codePoint);
          }
          yield Character.toString(codePoint);
        }
      };
    }
  }

  /** Represents a range of characters within a character class, e.g., 'a-z'. */
  record CharRange(int start, int end) implements CharSetElement {
    public CharRange {
      checkArgument(Character.isValidCodePoint(start), "not a valid start code point: %s", start);
      checkArgument(Character.isValidCodePoint(end), "not a valid end code point: %s", end);
      checkArgument(
          start <= end, "invalid range %s-%s", Character.toString(start), Character.toString(end));
    }

    public CharRange(char start, char end) {
      this((int) start, (int) end);
    }

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
    PREVIOUS_MATCH_END("\\G"),
    GRAPHEME_CLUSTER_BOUNDARY("\\b", "{g}"),
    WORD_BOUNDARY("\\b"),
    NON_WORD_BOUNDARY("\\B");

    @SuppressWarnings("ImmutableEnumChecker")
    private final List<String> tokens;

    Anchor(String... tokens) {
      this.tokens = List.of(tokens);
    }

    List<String> tokens() {
      return tokens;
    }

    @Override public Metadata metadata() {
      return new Metadata(/* minSize= */ 0, /* maxSize= */ 0);
    }

    @Override public String toString() {
      return String.join("", tokens);
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
   * <p>Validation is syntactic. Constraints that depend on the pattern as a whole are not enforced,
   * so unlike {@link java.util.regex.Pattern} this accepts duplicate group names, a {@code
   * \k<name>} that names no group, and a backreference to a group that is defined later or not at
   * all. Callers that need those guarantees should check the parsed tree.
   *
   * @throws Parser.ParseException if the regex pattern is malformed
   * @throws IllegalArgumentException if the regex pattern is invalid
   * @since 10.8
   */
  static RegexPattern of(String regex) {
    RegexPattern parsed = RegexParsers.TOP_LEVEL.orElse(new Literal("")).parse(regex);
    return hasMultiDigitBackreference(regex) ? resolveBackreferences(parsed) : parsed;
  }

  private static boolean hasMultiDigitBackreference(String regex) {
    int index = 0;
    while ((index = regex.indexOf('\\', index)) >= 0) {
      if (index + 2 < regex.length()) {
        char c1 = regex.charAt(index + 1);
        char c2 = regex.charAt(index + 2);
        if (c1 >= '1' && c1 <= '9' && c2 >= '0' && c2 <= '9') {
          return true;
        }
      }
      index++;
    }
    return false;
  }

  private static RegexPattern resolveBackreferences(RegexPattern root) {
    class Resolver {
      private int groupCount = 0;

      RegexPattern resolve(RegexPattern pattern) {
        if (pattern instanceof Group.Capturing group) {
          groupCount++;
          RegexPattern newContent = resolve(group.content());
          return newContent == group.content() ? group : new Group.Capturing(newContent);
        }
        if (pattern instanceof Group.Named group) {
          groupCount++;
          RegexPattern newContent = resolve(group.content());
          return newContent == group.content() ? group : new Group.Named(group.name(), newContent);
        }
        if (pattern instanceof Group.NonCapturing group) {
          RegexPattern newContent = resolve(group.content());
          return newContent == group.content()
              ? group
              : new Group.NonCapturing(
                  newContent, group.enabledModifierFlags(), group.disabledModifierFlags());
        }
        if (pattern instanceof Group.Atomic group) {
          RegexPattern newContent = resolve(group.content());
          return newContent == group.content() ? group : new Group.Atomic(newContent);
        }
        if (pattern instanceof Lookaround.Lookahead look) {
          RegexPattern newTarget = resolve(look.target());
          return newTarget == look.target() ? look : new Lookaround.Lookahead(newTarget);
        }
        if (pattern instanceof Lookaround.Lookbehind look) {
          RegexPattern newTarget = resolve(look.target());
          return newTarget == look.target() ? look : new Lookaround.Lookbehind(newTarget);
        }
        if (pattern instanceof Lookaround.NegativeLookahead look) {
          RegexPattern newTarget = resolve(look.target());
          return newTarget == look.target() ? look : new Lookaround.NegativeLookahead(newTarget);
        }
        if (pattern instanceof Lookaround.NegativeLookbehind look) {
          RegexPattern newTarget = resolve(look.target());
          return newTarget == look.target() ? look : new Lookaround.NegativeLookbehind(newTarget);
        }
        if (pattern instanceof Alternation alt) {
          List<RegexPattern> newAlts = null;
          List<RegexPattern> alts = alt.alternatives();
          for (int i = 0; i < alts.size(); i++) {
            RegexPattern original = alts.get(i);
            RegexPattern resolved = resolve(original);
            if (resolved != original && newAlts == null) {
              newAlts = new ArrayList<>(alts.subList(0, i));
            }
            if (newAlts != null) {
              newAlts.add(resolved);
            }
          }
          return newAlts == null ? alt : new Alternation(newAlts);
        }
        if (pattern instanceof Sequence seq) {
          List<RegexPattern> newElements = null;
          List<RegexPattern> elements = seq.elements();
          for (int i = 0; i < elements.size(); i++) {
            RegexPattern original = elements.get(i);
            RegexPattern resolved = resolve(original);
            if (resolved != original && newElements == null) {
              newElements = new ArrayList<>(elements.subList(0, i));
            }
            if (newElements != null) {
              newElements.add(resolved);
            }
          }
          return newElements == null ? seq : new Sequence(newElements);
        }
        if (pattern instanceof Quantified q) {
          if (q.element() instanceof Backreference.Numbered numbered) {
            RegexPattern split = splitNumbered(numbered, q.quantifier());
            if (split != null) {
              return split;
            }
          }
          RegexPattern newTarget = resolve(q.element());
          return newTarget == q.element() ? q : new Quantified(newTarget, q.quantifier());
        }
        if (pattern instanceof Backreference.Numbered numbered) {
          RegexPattern split = splitNumbered(numbered, null);
          return split != null ? split : numbered;
        }
        return pattern;
      }

      private RegexPattern splitNumbered(Backreference.Numbered numbered, Quantifier quantifier) {
        int num = numbered.groupNumber();
        if (num <= groupCount || num < 10) {
          return null;
        }
        String digits = Integer.toString(num);
        int bestPrefixLength = 1;
        for (int len = 2; len <= digits.length(); len++) {
          int prefixVal = Integer.parseInt(digits.substring(0, len));
          if (prefixVal <= groupCount) {
            bestPrefixLength = len;
          } else {
            break;
          }
        }
        if (bestPrefixLength == digits.length()) {
          return null;
        }
        int groupNum = Integer.parseInt(digits.substring(0, bestPrefixLength));
        String trailingDigits = digits.substring(bestPrefixLength);
        Backreference.Numbered ref = new Backreference.Numbered(groupNum);
        if (quantifier == null) {
          return new Sequence(List.of(ref, new Literal(trailingDigits)));
        }
        if (trailingDigits.length() == 1) {
          return new Sequence(
              List.of(ref, new Quantified(new Literal(trailingDigits), quantifier)));
        }
        String prefixOfDigits = trailingDigits.substring(0, trailingDigits.length() - 1);
        String lastDigit = trailingDigits.substring(trailingDigits.length() - 1);
        return new Sequence(
            List.of(
                ref,
                new Literal(prefixOfDigits),
                new Quantified(new Literal(lastDigit), quantifier)));
      }
    }
    return new Resolver().resolve(root);
  }
}

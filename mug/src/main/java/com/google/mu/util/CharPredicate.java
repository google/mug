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
package com.google.mu.util;

import static java.util.Objects.requireNonNull;

/**
 * A predicate of character. More efficient than {@code Predicate<Character>}.
 *
 * @since 6.0
 */
@FunctionalInterface
public interface CharPredicate {

  /** Equivalent to the {@code [a-zA-Z]} character class. */
  static CharPredicate ALPHA = new CharacterRangeSet.Alpha();

  /** Equivalent to the {@code [a-zA-Z0-9_]} character class. */
  static CharPredicate WORD = new CharacterRangeSet.Word();

  /** Corresponds to the ASCII characters. */
  static CharPredicate ASCII = new CharacterRangeSet.Ascii();

  /** Corresponds to all characters. */
  static CharPredicate ANY = new CharacterRangeSet.Any();

  /** Corresponds to no characters. */
  static CharPredicate NONE = new CharacterRangeSet.None();

  /**
   * Equivalent to {@link Character#isWhitespace}.
   *
   * @since 10.6
   */
  static CharPredicate WHITESPACE = new CharPredicate() {
    @Override public boolean test(char c) {
      return Character.isWhitespace(c);
    }

    @Override public String toString() {
      return "WHITESPACE";
    }
  };

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate is(char ch) {
    return new CharacterRangeSet.Single(ch);
  }

  /** Returns a CharPredicate that matches except {@code ch}. */
  static CharPredicate isNot(char ch) {
    return is(ch).not();
  }

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate range(char from, char to) {
    return new CharacterRangeSet.Range(from, to);
  }

  /** Returns a CharPredicate that matches any of {@code chars}. */
  static CharPredicate anyOf(String chars) {
    switch (chars.length()) {
      case 2: return is(chars.charAt(0)).or(chars.charAt(1));
      case 1: return is(chars.charAt(0));
      case 0: return NONE;
      default: return new CharacterRangeSet.AnyOf(chars);
    }
  }

  /** Returns a CharPredicate that matches any of {@code chars}. */
  static CharPredicate noneOf(String chars) {
    return anyOf(chars).not();
  }

  /**
   * Returns a regex-like character range set metadata of this predicate (e.g.,
   * {@code "[0-9a-zA-Z_-]"}, {@code "[]"}). By default {@code ""} is returned, indicating
   * that the character range set metadata is unknown.
   *
   * <p>It's used both for debugging and as metadata for optimizations.
   * Thus it's critical for the returned character set range string to be accurate, if implemented.
   * That is, if your {@link #characterRangeSet} is inconsistent with {@link #test}, behavior is
   * unpredictable.
   *
   * <p>Special characters inside the class body are serialized and parsed as follows:
   * <ul>
   *   <li>Brackets {@code [} and {@code ]} are treated as literal (don't need to be escaped).
   *   <li>Dash {@code -} is treated as literal (doesn't need to be escaped) but positioned at
   *       the start of the body (e.g., {@code "[-..."}) to avoid being interpreted as a range separator.
   *       It is treated as a range separator only when positioned between two characters
   *       (e.g. {@code "a-z"}),  and as a literal dash otherwise.
   *   <li>Caret {@code ^} is always treated as a literal character (doesn't need to be escaped).
   *       Negative character range set isn't supported.
   *   <li>Unicode code unit ({@code \u1234}), {@code \\}, {@code \t}, {@code \r}, {@code \n},
   *       {@code \f} and {@code \b} escape sequences are allowed. Non-ASCII characters must be
   *       unicode escaped.
   * </ul>
   *
   * <p>For any range {@code c1-c2} inside the character range set, the start character {@code c1} must not
   * be greater than the end character {@code c2} (i.e. {@code c1 <= c2}). Standard parser engines and
   * regex compilers will reject out-of-order ranges (such as {@code "z-a"}).
   *
   * @since 10.6.1
   */
  default String characterRangeSet() {
    return "";
  }

  /** Returns true if {@code ch} satisfies this predicate. */
  boolean test(char ch);

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this or {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate or(CharPredicate that) {
    requireNonNull(that);
    return new CharacterRangeSet.Union(this, that);
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this evaluates to true,
   * or the character is equal to any of {@code chars}.
   *
   * @since 9.9.4
   */
  default CharPredicate or(String chars) {
    return chars.isEmpty() ? this : or(anyOf(chars));
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if both this and {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate and(CharPredicate that) {
    requireNonNull(that);
    return new CharacterRangeSet.Intersection(this, that);
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this predicate evaluates to true,
   * or the character is {@code ch}.
   */
  default CharPredicate or(char ch) {
    return or(is(ch));
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this predicate evaluates to true,
   * or the character is in the range of {@code [from, to]}.
   */
  default CharPredicate orRange(char from, char to) {
    return or(range(from, to));
  }

  /** Returns the negation of this {@code CharPredicate}. */
  default CharPredicate not() {
    return new CharacterRangeSet.Negation(this);
  }

  /**
   * Returns {@code true} if a character sequence contains at least one matching BMP character.
   * Equivalent to {@code !matchesNoneOf(sequence)}.
   *
   * @since 7.0
   */
  default boolean matchesAnyOf(CharSequence sequence) {
    return !matchesNoneOf(sequence);
  }

  /**
   * Returns {@code true} if a character sequence contains only matching BMP characters.
   *
   * @since 7.0
   */
  default boolean matchesAllOf(CharSequence sequence) {
    for (int i = sequence.length() - 1; i >= 0; i--) {
      if (!test(sequence.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns {@code true} if a character sequence contains no matching BMP characters. Equivalent to
   * {@code !matchesAnyOf(sequence)}.
   *
   * @since 7.0
   */
  default boolean matchesNoneOf(CharSequence sequence) {
    for (int i = sequence.length() - 1; i >= 0; i--) {
      if (test(sequence.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if {@code sequence} starts with a character that matches this predicate.
   *
   * @since 9.0
   */
  default boolean isPrefixOf(CharSequence sequence) {
    return sequence.length() > 0 && test(sequence.charAt(0));
  }

  /**
   * Returns true if {@code sequence} ends with a character that matches this predicate.
   *
   * @since 9.0
   */
  default boolean isSuffixOf(CharSequence sequence) {
    int len = sequence.length();
    return len > 0 && test(sequence.charAt(len - 1));
  }

  /**
   * Returns an equivalent {@link CharPredicate} but pre-computes the results for all ASCII characters.
   * Useful if the CharPredicate is used in a hot path.
   *
   * <p>This method is more efficient for ASCII chars than Guava {@link
   * com.google.common.base.CharMatcher#precomputed CharMatcher.precomputed()}, and is far cheaper
   * because it only uses two 64-bit long integers to store the pre-computation results.
   *
   * <p>Note that {@link #WORD}, {@link #anyOf} and {@link #noneOf} are already pre-computed for
   * ASCII chars. You may still want to call it on a deeply composed {@code CharPredicate} though.
   *
   * @since 9.9.4
   */
  default CharPredicate precomputeForAscii() {
    return new CharacterRangeSet.PrecomputedForAscii(this);
  }
}

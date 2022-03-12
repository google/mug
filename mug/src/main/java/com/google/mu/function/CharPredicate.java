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
package com.google.mu.function;

import static java.util.Objects.requireNonNull;

/**
 * A predicate of character. More efficient than {@code Predicate<Character>}.
 *
 * @since 6.0
 */
@FunctionalInterface
public interface CharPredicate {
  /**
   * Equivalent to the {@code [a-z]} character class.
   *
   * @since 6.0
   **/
  static final CharPredicate LOWER_CASE = range('a', 'z');

  /**
   * Equivalent to the {@code [A-Z]} character class.
   *
   * @since 6.0
   **/
  static final CharPredicate UPPER_CASE = range('A', 'Z');

  /**
   * Equivalent to the {@code [a-zA-Z]} character class.
   *
   * @since 6.0
   **/
  static final CharPredicate ALPHA = LOWER_CASE.or(UPPER_CASE);

  /**
   * Equivalent to the {@code [0-9]} character class.
   *
   * @since 6.0
   **/
  static final CharPredicate DIGIT = range('0', '9');

  /** Equivalent to the {@code [a-zA-Z0-9_]} character class. */
  static CharPredicate WORD = ALPHA .or(DIGIT).or('_');

  /**
   * Corresponds to the ASCII characters.
   *
   * @since 6.0
   **/
  static CharPredicate ASCII = new CharPredicate() {
    @Override public boolean matches(char c) {
      return c <= '\u007f';
    }

    @Override public String toString() {
      return "ASCII";
    }
  };

  /**
   * Corresponds to all characters.
   *
   * @since 6.0
   **/
  static CharPredicate ANY = new CharPredicate() {
    @Override public boolean matches(char c) {
      return true;
    }

    @Override public String toString() {
      return "ANY";
    }
  };

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate is(char ch) {
    return new CharPredicate() {
      @Override public boolean matches(char c) {
        return c == ch;
      }

      @Override public String toString() {
        return "'" + ch + "'";
      }
    };
  }

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate range(char from, char to) {
    return new CharPredicate() {
      @Override public boolean matches(char c) {
        return c >= from && c <= to;
      }

      @Override public String toString() {
        return "['" + from + "', '" + to + "']";
      }
    };
  }

  /** Returns true if {@code ch} satisfies this predicate. */
  boolean matches(char ch);

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this or {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate or(CharPredicate that) {
    requireNonNull(that);
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean matches(char c) {
        return me.matches(c) || that.matches(c);
      }

      @Override public String toString() {
        return me + " | " + that;
      }
    };
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if both this and {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate and(CharPredicate that) {
    requireNonNull(that);
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean matches(char c) {
        return me.matches(c) && that.matches(c);
      }

      @Override public String toString() {
        return me + " & " + that;
      }
    };
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
   * or the character is in the range of {@code [from, to]).
   */
  default CharPredicate orRange(char from, char to) {
    return or(range(from, to));
  }

  /** Returns the negation of this {@code CharPredicate}. */
  default CharPredicate not() {
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean matches(char c) {
        return !me.matches(c);
      }

      @Override public String toString() {
        return "not (" + me + ")";
      }
    };
  }
}

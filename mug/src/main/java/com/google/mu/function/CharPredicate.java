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

  /** Equivalent to the {@code [a-zA-Z]} character class. */
  static CharPredicate ALPHA = range('a', 'z').orRange('A', 'Z');

  /** Equivalent to the {@code [a-zA-Z0-9_]} character class. */
  static CharPredicate WORD = ALPHA .orRange('0', '9').or('_');

  /** Corresponds to the ASCII characters. **/
  static CharPredicate ASCII = new CharPredicate() {
    @Override public boolean matches(int c) {
      return c <= '\u007f';
    }

    @Override public String toString() {
      return "ASCII";
    }
  };

  /** Corresponds to all characters. */
  static CharPredicate ANY = new CharPredicate() {
    @Override public boolean matches(int c) {
      return true;
    }

    @Override public String toString() {
      return "ANY";
    }
  };

  /** Returns a CharPredicate that only matches {@code ch}. */
  static CharPredicate is(int ch) {
    return new CharPredicate() {
      @Override public boolean matches(int c) {
        return c == ch;
      }

      @Override public String toString() {
        return "'" + new String(Character.toChars(ch)) + "'";
      }
    };
  }

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate range(int from, int to) {
    if (from > to) {
      throw new IllegalArgumentException("Not true that " + from + " <= " + to);
    }
    return new CharPredicate() {
      @Override public boolean matches(int c) {
        return c >= from && c <= to;
      }

      @Override public String toString() {
        return "['" + new String(Character.toChars(from)) + "', '" + new String(Character.toChars(to)) + "']";
      }
    };
  }

  /** Returns true if {@code ch} satisfies this predicate. */
  boolean matches(int ch);

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this or {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate or(CharPredicate that) {
    requireNonNull(that);
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean matches(int c) {
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
      @Override public boolean matches(int c) {
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
  default CharPredicate or(int ch) {
    return or(is(ch));
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this predicate evaluates to true,
   * or the character is in the range of {@code [from, to]}.
   */
  default CharPredicate orRange(int from, int to) {
    return or(range(from, to));
  }

  /** Returns the negation of this {@code CharPredicate}. */
  default CharPredicate not() {
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean matches(int c) {
        return !me.matches(c);
      }

      @Override public String toString() {
        return "not (" + me + ")";
      }
    };
  }
}

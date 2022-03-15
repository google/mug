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

import java.util.function.IntPredicate;

/**
 * A matcher of a Unicode code points.
 *
 * @since 6.0
 */
@FunctionalInterface
public interface CodePointMatcher extends IntPredicate {

  /** Equivalent to the {@code [a-zA-Z]} character class. */
  static CodePointMatcher ALPHA = range('a', 'z').orRange('A', 'Z');

  /** Equivalent to the {@code [a-zA-Z0-9_]} character class. */
  static CodePointMatcher WORD = ALPHA .orRange('0', '9').or('_');

  /** Corresponds to the ASCII characters. **/
  static CodePointMatcher ASCII = new CodePointMatcher() {
    @Override public boolean test(int c) {
      return c <= '\u007f';
    }

    @Override public String toString() {
      return "ASCII";
    }
  };

  /** Corresponds to all characters. */
  static CodePointMatcher ANY = new CodePointMatcher() {
    @Override public boolean test(int c) {
      return true;
    }

    @Override public String toString() {
      return "ANY";
    }
  };

  /** Returns a CharPredicate that only matches {@code ch}. */
  static CodePointMatcher is(int ch) {
    return new CodePointMatcher() {
      @Override public boolean test(int c) {
        return c == ch;
      }

      @Override public String toString() {
        return "'" + new String(Character.toChars(ch)) + "'";
      }
    };
  }

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CodePointMatcher range(int from, int to) {
    if (from > to) {
      throw new IllegalArgumentException("Not true that " + from + " <= " + to);
    }
    return new CodePointMatcher() {
      @Override public boolean test(int c) {
        return c >= from && c <= to;
      }

      @Override public String toString() {
        return "['" + new String(Character.toChars(from)) + "', '" + new String(Character.toChars(to)) + "']";
      }
    };
  }

  /**
   * Returns a {@link CodePointMatcher} that evaluates true if either this or {@code that} predicate
   * evaluate to true.
   */
  @Override
  default CodePointMatcher or(IntPredicate that) {
    requireNonNull(that);
    CodePointMatcher me = this;
    return new CodePointMatcher() {
      @Override public boolean test(int c) {
        return me.test(c) || that.test(c);
      }

      @Override public String toString() {
        return me + " | " + that;
      }
    };
  }

  /**
   * Returns a {@link CodePointMatcher} that evaluates true if both this and {@code that} predicate
   * evaluate to true.
   */
  @Override
  default CodePointMatcher and(IntPredicate that) {
    requireNonNull(that);
    CodePointMatcher me = this;
    return new CodePointMatcher() {
      @Override public boolean test(int c) {
        return me.test(c) && that.test(c);
      }

      @Override public String toString() {
        return me + " & " + that;
      }
    };
  }

  /**
   * Returns a {@link CodePointMatcher} that evaluates true if either this predicate evaluates to true,
   * or the character is {@code ch}.
   */
  default CodePointMatcher or(int ch) {
    return or(is(ch));
  }

  /**
   * Returns a {@link CodePointMatcher} that evaluates true if either this predicate evaluates to true,
   * or the character is in the range of {@code [from, to]}.
   */
  default CodePointMatcher orRange(int from, int to) {
    return or(range(from, to));
  }

  /** Returns the negation of this {@code CharPredicate}. */
  @Override
  default CodePointMatcher negate() {
    CodePointMatcher me = this;
    return new CodePointMatcher() {
      @Override public boolean test(int c) {
        return !me.test(c);
      }

      @Override public String toString() {
        return "not (" + me + ")";
      }
    };
  }
}

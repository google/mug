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

import java.util.Arrays;

/**
 * A predicate of character. More efficient than {@code Predicate<Character>}.
 *
 * @since 6.0
 */
@FunctionalInterface
public interface CharPredicate {

  /** Equivalent to the {@code [a-zA-Z]} character class. */
  CharPredicate ALPHA =
      new CharPredicate() {
        @Override public boolean test(char c) {
          return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        @Override public String toString() {
          return "ALPHA";
        }
      }.precomputeForAscii();

  /** Equivalent to the {@code [a-zA-Z0-9_]} character class. */
  CharPredicate WORD =
      new CharPredicate() {
        @Override public boolean test(char c) {
          return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
              || c == '_';
        }

        @Override public String toString() {
          return "WORD";
        }
      }.precomputeForAscii();

  /** Corresponds to the ASCII characters. */
  CharPredicate ASCII =
      new CharPredicate() {
        @Override public boolean test(char c) {
          return c <= '\u007f';
        }

        @Override public String toString() {
          return "ASCII";
        }
      }.precomputeForAscii();

  /** Corresponds to all characters. */
  CharPredicate ANY = new CharPredicate() {
    @Override public boolean test(char c) {
      return true;
    }

    @Override public int skipLeading(CharSequence s, int begin, int end) {
      requireNonNull(s);
      return end;
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return "ANY";
    }
  };

  /** Corresponds to no characters. */
  CharPredicate NONE = new CharPredicate() {
    @Override public boolean test(char c) {
      return false;
    }

    @Override public int skipLeading(CharSequence s, int begin, int end) {
      requireNonNull(s);
      return begin;
    }

    @Override public CharPredicate precomputeForAscii() {
      return this;
    }

    @Override public String toString() {
      return "NONE";
    }
  };

  /**
   * Equivalent to {@link Character#isWhitespace}.
   *
   * @since 10.6
   */
  CharPredicate WHITESPACE = new CharPredicate() {
    @Override public boolean test(char c) {
      return Character.isWhitespace(c);
    }

    @Override public String toString() {
      return "WHITESPACE";
    }
  };

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate is(char ch) {
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return c == ch;
      }

      @Override public String toString() {
        return "'" + ch + "'";
      }
    }.precomputeForAscii();
  }

  /** Returns a CharPredicate that matches except {@code ch}. */
  static CharPredicate isNot(char ch) {
    return is(ch).not();
  }

  /** Returns a CharPredicate for the range of characters: {@code [from, to]}. */
  static CharPredicate range(char from, char to) {
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return c >= from && c <= to;
      }

      @Override public String toString() {
        return "['" + from + "', '" + to + "']";
      }
    }.precomputeForAscii();
  }

  /** Returns a CharPredicate that matches any of {@code chars}. */
  static CharPredicate anyOf(String chars) {
    switch (chars.length()) {
      case 2:
        return is(chars.charAt(0)).or(chars.charAt(1));
      case 1:
        return is(chars.charAt(0));
      case 0:
        return NONE;
    }
    char[] array = chars.toCharArray();
    Arrays.sort(array);
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return Arrays.binarySearch(array, c) >= 0;
      }

      @Override public String toString() {
        return "anyOf('" + chars + "')";
      }
    }.precomputeForAscii();
  }

  /** Returns a CharPredicate that matches any of {@code chars}. */
  static CharPredicate noneOf(String chars) {
    return anyOf(chars).not();
  }

  /** Returns true if {@code ch} satisfies this predicate. */
  boolean test(char ch);

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this or {@code that} predicate
   * evaluate to true.
   */
  default CharPredicate or(CharPredicate that) {
    requireNonNull(that);
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return me.test(c) || that.test(c);
      }

      @Override public String toString() {
        return me + " | " + that;
      }
    };
  }

  /**
   * Returns a {@link CharPredicate} that evaluates true if either this evaluates to true, or the
   * character is equal to any of {@code chars}.
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
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return me.test(c) && that.test(c);
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
   * or the character is in the range of {@code [from, to]}.
   */
  default CharPredicate orRange(char from, char to) {
    return or(range(from, to));
  }

  /** Returns the negation of this {@code CharPredicate}. */
  default CharPredicate not() {
    CharPredicate me = this;
    return new CharPredicate() {
      @Override public boolean test(char c) {
        return !me.test(c);
      }

      @Override public CharPredicate not() {
        return me;
      }

      @Override public CharPredicate precomputeForAscii() {
        CharPredicate precomputed = me.precomputeForAscii();
        return precomputed == me ? this : precomputed.not();
      }

      @Override public String toString() {
        return "not (" + me + ")";
      }
    };
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
   * Returns the index in the range of {@code [begin, end]}, pointing to either {@code end} or the
   * index of the first character that does not match this predicate.
   *
   * @since 10.0
   * @hidden
   */
  default int skipLeading(CharSequence s, int begin, int end) {
    requireNonNull(s);
    int i = begin;
    while (i < end && test(s.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * Returns an equivalent {@link CharPredicate} but pre-computes the results for all ASCII
   * characters. Useful if the CharPredicate is used in a hot path.
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
    CharPredicate base = this;

    return new CharPredicate() {
      private final long low64 = computeMask(0); // ASCII 0-63
      private final long high64 = computeMask(64); // ASCII 64-127

      @Override public boolean test(char c) {
        if (c < 64) {
          return ((low64 >>> c) & 1L) != 0;
        }
        if (c < 128) {
          return ((high64 >>> (c - 64)) & 1L) != 0;
        }
        return base.test(c); // Fallback for non-ASCII
      }

      @Override public int skipLeading(CharSequence s, int begin, int end) {
        requireNonNull(s);
        int i = begin;
        int limit = end - 4;

        int offset = (low64 == 0L && high64 != 0L) ? 64 : 0;
        int asciiMask = (low64 != 0L && high64 != 0L) ? ~0x7F : ~0x3F;

        while (i <= limit) {
          char c0 = s.charAt(i);
          char c1 = s.charAt(i + 1);
          char c2 = s.charAt(i + 2);
          char c3 = s.charAt(i + 3);

          if ((((c0 ^ offset) | (c1 ^ offset) | (c2 ^ offset) | (c3 ^ offset)) & asciiMask) == 0) {
            long m0;
            long m1;
            long m2;
            long m3;
            if (high64 == 0L) {
              m0 = low64 >>> c0;
              m1 = low64 >>> c1;
              m2 = low64 >>> c2;
              m3 = low64 >>> c3;
            } else if (low64 == 0L) {
              m0 = high64 >>> c0;
              m1 = high64 >>> c1;
              m2 = high64 >>> c2;
              m3 = high64 >>> c3;
            } else {
              m0 = (c0 < 64) ? (low64 >>> c0) : (high64 >>> c0);
              m1 = (c1 < 64) ? (low64 >>> c1) : (high64 >>> c1);
              m2 = (c2 < 64) ? (low64 >>> c2) : (high64 >>> c2);
              m3 = (c3 < 64) ? (low64 >>> c3) : (high64 >>> c3);
            }

            if (((m0 & m1 & m2 & m3) & 1L) != 0) {
              i += 4;
              continue;
            }

            if ((m0 & 1L) == 0) return i;
            if ((m1 & 1L) == 0) return i + 1;
            if ((m2 & 1L) == 0) return i + 2;
            return i + 3;
          }

          if (!base.test(c0)) return i;
          if (!base.test(c1)) return i + 1;
          if (!base.test(c2)) return i + 2;
          if (!base.test(c3)) return i + 3;
          i += 4;
        }

        while (i < end && base.test(s.charAt(i))) {
          i++;
        }
        return i;
      }

      @Override public CharPredicate precomputeForAscii() {
        return this;
      }

      @Override public String toString() {
        return base.toString();
      }

      private long computeMask(int offset) {
        long mask = 0L;
        for (int i = 0; i < 64; i++) {
          if (base.test((char) (offset + i))) {
            mask |= (1L << i);
          }
        }
        return mask;
      }
    };
  }
}

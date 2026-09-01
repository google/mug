/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.util.Objects.requireNonNull;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An abstraction over sequentially read characters. */
abstract class CharInput {
  int nestingLevel = 0;

  /** Reads the character at {@code index}. */
  abstract char charAt(int index);

  /** Returns the index of {@code str} starting from {@code fromIndex}, or -1 if not found. */
  abstract int indexOf(String str, int fromIndex);

  /** Returns a {@link Matcher} for the given regex pattern starting from {@code start} index. */
  abstract Matcher matcher(Pattern pattern, RegexPattern.Metadata metadata, int start);

  /**
   * Matches the given regex pattern starting from {@code start} index and returns the ending index
   * (exclusive). Returns {@code start} if no match is found.
   */
  abstract int match(Pattern pattern, RegexPattern.Metadata metadata, int start);

  /** Translates the end index of the given {@code matcher} to the logical index in the input. */
  int matchEnd(Matcher matcher) {
    return matcher.end();
  }

  final boolean startsWith(CharPredicate predicate, int index) {
    return isInRange(index) && predicate.test(charAt(index));
  }

  /** Do the characters starting from {@code index} start with {@code prefix}? */
  abstract boolean startsWith(String prefix, int index);

  /** Do the characters starting from {@code index} start with {@code prefix}, case insensitive? */
  abstract boolean startsWithCaseInsensitive(String prefix, int index);

  /** Is {@code index} the end of the input? */
  abstract boolean isEof(int index);

  final boolean isInRange(int index) {
    return !isEof(index);
  }

  /** Returns a snippet of string starting from {@code index} with at most {@code maxChars}. */
  abstract String snippet(int index, int maxChars);

  /** characters before {@code checkpointIndex} are no longer needed. */
  void markCheckpoint(int checkpointIndex) {}

  /**
   * Skips consecutive characters starting from {@code fromIndex} matching the 128-bit ASCII masks
   * {@code low64} and {@code high64} and returns the ending index (first non-matching index or
   * EOF).
   */
  abstract int skipWhile(CharPredicate condition, long low64, long high64, int from);

  final int skipWhile(Skipper skipper, int fromIndex) {
    return skipper.skip(this, fromIndex);
  }

  /**
   * Returns the source position of the character at {@code at}. It's assumed that the index {@code
   * at} has been read.
   */
  abstract String sourcePosition(int at);

  /** An input backed by in-memory string. */
  static CharInput from(String text) {
    requireNonNull(text);
    return new CharInput() {
      @Override char charAt(int index) {
        return text.charAt(index);
      }

      @Override int indexOf(String str, int fromIndex) {
        return text.indexOf(str, fromIndex);
      }

      @Override Matcher matcher(Pattern pattern, RegexPattern.Metadata metadata, int start) {
        Matcher matcher = pattern.matcher(text);
        matcher.region(start, text.length());
        return matcher;
      }

      @Override int match(Pattern pattern, RegexPattern.Metadata metadata, int start) {
        Matcher matcher = matcher(pattern, metadata, start);
        return matcher.lookingAt() ? matcher.end() : start;
      }

      @Override boolean startsWith(String prefix, int index) {
        return text.startsWith(prefix, index);
      }

      @Override boolean startsWithCaseInsensitive(String prefix, int index) {
        // shorter.regionMatches(..., longer, ...) appears to be faster, according to benchmark.
        return prefix.regionMatches(/* ignoreCase= */ true, 0, text, index, prefix.length());
      }

      @Override int skipWhile(CharPredicate condition, long low64, long high64, int from) {
        return scanWhile(text, from, text.length(), condition, low64, high64);
      }

      @Override boolean isEof(int index) {
        return index >= text.length();
      }

      @Override String snippet(int index, int maxLength) {
        return text.substring(index, Math.min(text.length(), index + maxLength));
      }

      @Override String sourcePosition(int at) {
        int line = 1;
        int lineStartIndex = 0;
        for (Substring.Match match :
            iterateOnce(Substring.all('\n').match(text).takeWhile(m -> m.index() < at))) {
          lineStartIndex = match.index() + 1;
          line++;
        }
        return line + ":" + (at - lineStartIndex + 1);
      }
    };
  }

  /** A lazily-loaded input from {@code reader}. */
  static CharInput from(Reader reader) {
    return from(reader, /* bufferSize= */ 8192, /* compactionThreshold= */ 128 * 1024);
  }

  /**
   * A lazily-loaded input from {@code reader}.
   *
   * @param compactionThreshold compact the buffer if we have this number of chars no longer needed.
   */
  static CharInput from(Reader reader, int bufferSize, int compactionThreshold) {
    requireNonNull(reader);
    return new CharInput() {
      private final char[] temp = new char[bufferSize];
      private final StringBuilder chars = new StringBuilder();
      private int garbageCharCount = 0;

      @Override char charAt(int index) {
        ensureCharCount(index + 1);
        return chars.charAt(toPhysicalIndex(index));
      }

      @Override int indexOf(String str, int fromIndex) {
        checkArgument(fromIndex >= garbageCharCount, "fromIndex < %s", garbageCharCount);
        for (int i = fromIndex; ; ) {
          ensureCharCount(i + str.length());
          int fromPhysicalIndex = toPhysicalIndex(i);
          // If after expansion, we don't have enough chars, we've reached the end.
          if (fromPhysicalIndex + str.length() > chars.length()) {
            return -1;
          }
          int foundPhysicalIndex = chars.indexOf(str, fromPhysicalIndex);
          // if String.indeexOf() has found it, translate the physical index back to logical.
          if (foundPhysicalIndex >= fromPhysicalIndex) {
            return toLogicalIndex(foundPhysicalIndex);
          }
          // Assuming `str` is 5 chars, when we load the next page of characters, we can resume the
          // scan with the last 4 chars in the current page, just in case. All other chars are
          // provably useless.
          i = toLogicalIndex(chars.length() - str.length() + 1);
        }
      }

      @Override Matcher matcher(Pattern pattern, RegexPattern.Metadata metadata, int start) {
        long requiredCharCount = (long) start + metadata.maxSize();
        if (requiredCharCount >= Integer.MAX_VALUE) {
          throw new UnsupportedOperationException(
              "regex with unbounded matching size is not supported on Reader-based input: "
                  + pattern);
        }
        ensureCharCount((int) requiredCharCount);
        Matcher matcher = pattern.matcher(chars);
        matcher.region(toPhysicalIndex(start), chars.length());
        return matcher;
      }

      @Override int match(Pattern pattern, RegexPattern.Metadata metadata, int start) {
        Matcher matcher = matcher(pattern, metadata, start);
        return matcher.lookingAt() ? toLogicalIndex(matcher.end()) : start;
      }

      @Override int matchEnd(Matcher matcher) {
        return toLogicalIndex(matcher.end());
      }

      @Override boolean startsWith(String prefix, int index) {
        ensureCharCount(index + prefix.length());
        index = toPhysicalIndex(index);
        if (chars.length() < index + prefix.length()) {
          return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
          if (prefix.charAt(i) != chars.charAt(index + i)) {
            return false;
          }
        }
        return true;
      }

      @Override boolean startsWithCaseInsensitive(String prefix, int index) {
        ensureCharCount(index + prefix.length());
        index = toPhysicalIndex(index);
        if (chars.length() < index + prefix.length()) {
          return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
          char c1 = chars.charAt(index + i);
          char c2 = prefix.charAt(i);
          if (c1 != c2 && Character.toUpperCase(c1) != Character.toUpperCase(c2)) {
            return false;
          }
        }
        return true;
      }

      @Override int skipWhile(CharPredicate condition, long low64, long high64, int from) {
        checkArgument(from >= garbageCharCount, "fromIndex < %s", garbageCharCount);
        for (int i = from; ; ) {
          ensureCharCount(i + 4);
          int p = toPhysicalIndex(i);
          int limit = chars.length();
          if (p >= limit) {
            return i;
          }
          int matched = scanWhile(chars, p, limit, condition, low64, high64);
          i = toLogicalIndex(matched);
          if (matched < limit) {
            return i;
          }
          int prevLen = chars.length();
          ensureCharCount(i + 1);
          if (chars.length() == prevLen) {
            return i;
          }
        }
      }

      @Override boolean isEof(int index) {
        ensureCharCount(index + 1);
        return toPhysicalIndex(index) >= chars.length();
      }

      @Override String snippet(int index, int maxLength) {
        ensureCharCount(index + maxLength);
        index = toPhysicalIndex(index);
        return chars.substring(index, Math.min(chars.length(), index + maxLength));
      }

      @Override void markCheckpoint(int checkpointIndex) {
        int unused = checkpointIndex - garbageCharCount;
        if (unused > compactionThreshold) {
          chars.delete(0, unused);
          garbageCharCount += unused;
        }
      }

      @Override String sourcePosition(int at) {
        return garbageCharCount > 0
            ? Integer.toString(at)
            : from(chars.toString()).sourcePosition(at);
      }

      private int toPhysicalIndex(int index) {
        index -= garbageCharCount;
        if (index < 0) {
          throw new IndexOutOfBoundsException("index must be at least " + garbageCharCount);
        }
        return index;
      }

      private int toLogicalIndex(int physical) {
        return garbageCharCount + physical;
      }

      private void ensureCharCount(int charCount) {
        for (int missing = charCount - garbageCharCount - chars.length(); missing > 0; ) {
          try {
            int loaded = reader.read(temp);
            if (loaded <= 0) { // no more to load
              break;
            }
            chars.append(temp, 0, loaded);
            missing -= loaded;
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
    };
  }

  /**
   * Scans {@code cs} in 4-character chunks using SWAR (SIMD Within A Register) bitmask evaluation.
   *
   * <p>Optimizes scanning into three specialized modes:
   *
   * <ul>
   *   <li><b>Lower-64 mode</b> ({@code high64 == 0L}): for matchers in ASCII 0..63 (e.g.
   *       whitespace, digits). Evaluates via direct {@code low64 >>> c} shifts (0 cmov).
   *   <li><b>Higher-64 mode</b> ({@code low64 == 0L}): for matchers in ASCII 64..127 (e.g. {@code
   *       [a-z]}, {@code [A-Z]}, {@code [a-zA-Z]}). Translates range via XOR and shifts {@code
   *       high64 >>> c} directly (0 cmov).
   *   <li><b>128-bit mixed mode</b> ({@code low64 != 0 && high64 != 0}): for matchers spanning both
   *       halves (e.g. {@code [a-zA-Z0-9_]}). Evaluates via branchless {@code cmov} selection.
   * </ul>
   */
  private static int scanWhile(
      CharSequence source, int from, int to, CharPredicate fallback, long low64, long high64) {
    int i = from;
    int limit = to - 4;

    // Determine the active SWAR partition parameters:
    // offset: For Higher-64 matchers (e.g. [a-z], [a-zA-Z]), all matching chars have bit 6 set
    //    (codepoints 64..127). XORing with 64 flips bit 6, mapping the [64..127] range to [0..63].
    //    For Lower-64 and 128-bit mixed modes, offset is 0.
    int offset = (low64 == 0L && high64 != 0L) ? 64 : 0;

    // asciiMask:
    //    - Single 64-character partition (Lower-64 or Higher-64): mask ~0x3F verifies that bits >=
    // 6
    //      are all zero after XOR offset, ensuring the character lies strictly in the 64-char
    // window.
    //    - 128-bit mixed mode: mask ~0x7F verifies that bits >= 7 are all zero (validating 7-bit
    // ASCII).
    int asciiMask = (low64 != 0L && high64 != 0L) ? ~0x7F : ~0x3F;

    while (i <= limit) {
      char c0 = source.charAt(i);
      char c1 = source.charAt(i + 1);
      char c2 = source.charAt(i + 2);
      char c3 = source.charAt(i + 3);

      // Fast check: verify in bitwise ops that all 4 characters belong to the active partition
      // without any non-ASCII or out-of-partition characters.
      if ((((c0 ^ offset) | (c1 ^ offset) | (c2 ^ offset) | (c3 ^ offset)) & asciiMask) == 0) {
        long m0;
        long m1;
        long m2;
        long m3;
        if (high64 == 0L) {
          // Lower-64 mode: direct shift into low64 (0 cmov).
          m0 = low64 >>> c0;
          m1 = low64 >>> c1;
          m2 = low64 >>> c2;
          m3 = low64 >>> c3;
        } else if (low64 == 0L) {
          // Higher-64 mode: direct shift into high64 (0 cmov).
          // Per JLS §15.19, (high64 >>> c) automatically masks shift amount to (c & 63) == (c -
          // 64).
          m0 = high64 >>> c0;
          m1 = high64 >>> c1;
          m2 = high64 >>> c2;
          m3 = high64 >>> c3;
        } else {
          // 128-bit mixed mode: branchless ternary lowered to cmov.
          m0 = (c0 < 64) ? (low64 >>> c0) : (high64 >>> c0);
          m1 = (c1 < 64) ? (low64 >>> c1) : (high64 >>> c1);
          m2 = (c2 < 64) ? (low64 >>> c2) : (high64 >>> c2);
          m3 = (c3 < 64) ? (low64 >>> c3) : (high64 >>> c3);
        }

        // Fast path: bit 0 represents the match flag (mask >>> c & 1L).
        // If bit 0 is set for all 4 chars, all 4 matched; advance by 4 with 0 branches.
        if (((m0 & m1 & m2 & m3) & 1L) != 0) {
          i += 4;
          continue;
        }

        // Mismatch encountered within this 4-char chunk; return the earliest non-matching index.
        if ((m0 & 1L) == 0) return i;
        if ((m1 & 1L) == 0) return i + 1;
        if ((m2 & 1L) == 0) return i + 2;
        return i + 3;
      }

      // Non-ASCII or out-of-partition fallback: test characters sequentially via fallback.
      if (!fallback.test(c0)) return i;
      if (!fallback.test(c1)) return i + 1;
      if (!fallback.test(c2)) return i + 2;
      if (!fallback.test(c3)) return i + 3;
      i += 4;
    }

    // Process remaining trailing characters (< 4).
    while (i < to && fallback.test(source.charAt(i))) {
      i++;
    }
    return i;
  }
}

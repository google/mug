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

class PrecomputedForAscii implements CharPredicate {
  private final CharPredicate base;
  private final long low64;
  private final long high64;
  // For predicates matching only within [0, 63] or [64, 127], offset and asciiMask allow
  // validating range containment with a single XOR and AND: ((c ^ offset) & asciiMask) == 0.
  private final int offset;
  private final int asciiMask;

  static PrecomputedForAscii of(CharPredicate base) {
    long low64 = computeMask(base, 0);
    long high64 = computeMask(base, 64);
    return new PrecomputedForAscii(base, low64, high64);
  }

  private PrecomputedForAscii(CharPredicate base, long low64, long high64) {
    this.base = base;
    this.low64 = low64;
    this.high64 = high64;
    this.offset = (low64 == 0L && high64 != 0L) ? 64 : 0;
    this.asciiMask = (low64 != 0L && high64 != 0L) ? ~0x7F : ~0x3F;
  }

  @Override public boolean test(char c) {
    if (c < 64) {
      return ((low64 >>> c) & 1L) != 0;
    }
    if (c < 128) {
      return ((high64 >>> (c - 64)) & 1L) != 0;
    }
    return base.test(c);
  }

  @Override public int skipLeading(CharSequence s, int fromIndex) {
    int end = s.length();
    int i = fromIndex;
    int limit = end - 4;

    // Process 4 characters at a time using bitmask-parallel scanning (SWAR).
    while (i <= limit) {
      char c0 = s.charAt(i);
      char c1 = s.charAt(i + 1);
      char c2 = s.charAt(i + 2);
      char c3 = s.charAt(i + 3);

      // Check if all 4 characters fall within the precomputed ASCII range in one test.
      // - If matches are strictly in [0, 63], offset=0 and asciiMask=~63 checks c in [0, 63].
      // - If matches are strictly in [64, 127], offset=64 and asciiMask=~63 checks c in [64, 127].
      // - If matches span both halves, offset=0 and asciiMask=~127 checks c in [0, 127].
      // OR-ing the adjusted character values tests whether any character violates the range.
      if ((((c0 ^ offset) | (c1 ^ offset) | (c2 ^ offset) | (c3 ^ offset)) & asciiMask) == 0) {
        long m0;
        long m1;
        long m2;
        long m3;
        // Shift each character's matching bit to the least significant bit position (bit 0).
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

        // Bitwise AND of all 4 shifted values checks if bit 0 is 1 for all 4 characters.
        // If non-zero, all 4 characters matched; advance past the full 4-char chunk.
        if (((m0 & m1 & m2 & m3) & 1L) != 0) {
          i += 4;
          continue;
        }

        // At least one character failed; find the exact index of the first mismatch.
        if ((m0 & 1L) == 0) return i;
        if ((m1 & 1L) == 0) return i + 1;
        if ((m2 & 1L) == 0) return i + 2;
        return i + 3;
      }

      // Non-ASCII fallback: test characters in the chunk sequentially using base predicate.
      if (!base.test(c0)) return i;
      if (!base.test(c1)) return i + 1;
      if (!base.test(c2)) return i + 2;
      if (!base.test(c3)) return i + 3;
      i += 4;
    }

    // Scalar loop for remaining 1 to 3 trailing characters.
    while (i < end && base.test(s.charAt(i))) {
      i++;
    }
    return i;
  }

  @Override public CharPredicate not() {
    PrecomputedForAscii precomputed = this;
    return new PrecomputedForAscii(base.not(), ~low64, ~high64) {
      @Override public CharPredicate not() {
        return precomputed;
      }

      @Override public String toString() {
        return "not (" + base + ")";
      }
    };
  }

  @Override public CharPredicate precomputeForAscii() {
    return this;
  }

  @Override public String toString() {
    return base.toString();
  }

  private static long computeMask(CharPredicate base, int offset) {
    long mask = 0L;
    for (int i = 0; i < 64; i++) {
      if (base.test((char) (offset + i))) {
        mask |= (1L << i);
      }
    }
    return mask;
  }
}

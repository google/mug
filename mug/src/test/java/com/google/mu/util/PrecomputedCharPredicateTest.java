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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.CharPredicate.WHITESPACE;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrecomputedCharPredicateTest {

  @Test public void precomputeForAscii_none() {
    CharPredicate predicate = CharPredicate.NONE.precomputeForAscii();
    for (int i = 0; i <= 128; i++) {
      assertThat(predicate.test((char) i)).isFalse();
    }
    assertThat(predicate.test('π')).isFalse();
  }

  @Test public void precomputeForAscii_ascii() {
    CharPredicate predicate = CharPredicate.ASCII.precomputeForAscii();
    for (int i = 0; i < 128; i++) {
      assertThat(predicate.test((char) i)).isTrue();
    }
    assertThat(predicate.test('π')).isFalse();
  }

  @Test public void precomputeForAscii_nonAscii() {
    CharPredicate predicate = CharPredicate.ASCII.not().precomputeForAscii();
    for (int i = 0; i < 128; i++) {
      assertThat(predicate.test((char) i)).isFalse();
    }
    assertThat(predicate.test('π')).isTrue();
  }

  @Test public void precomputeForAscii_isoControl() {
    CharPredicate isIsoControl = Character::isISOControl;
    CharPredicate predicate = isIsoControl.precomputeForAscii();
    assertThat(predicate.test('a')).isFalse();
    assertThat(predicate.test('\n')).isTrue();
    assertThat(predicate.test('\0')).isTrue();
    assertThat(predicate.test('\t')).isTrue();
  }

  @Test public void precomputeForAscii_whitespace() {
    assertThat(WHITESPACE.test('a')).isFalse();
    assertThat(WHITESPACE.test('\n')).isTrue();
    assertThat(WHITESPACE.test(' ')).isTrue();
    assertThat(WHITESPACE.test('\t')).isTrue();
  }

  @Test public void precomputeForAscii_idempotent() {
    CharPredicate predicate = CharPredicate.ASCII.precomputeForAscii();
    assertThat(predicate.precomputeForAscii()).isSameInstanceAs(predicate);
  }

  @Test public void precomputeForAscii_idempotent_specialCases() {
    assertThat(CharPredicate.ANY.precomputeForAscii()).isSameInstanceAs(CharPredicate.ANY);
    assertThat(CharPredicate.NONE.precomputeForAscii()).isSameInstanceAs(CharPredicate.NONE);

    CharPredicate isA = CharPredicate.is('a');
    CharPredicate precomputedA = isA.precomputeForAscii();
    assertThat(precomputedA).isNotSameInstanceAs(isA);
    assertThat(precomputedA.precomputeForAscii()).isSameInstanceAs(precomputedA);

    CharPredicate rangeAZ = CharPredicate.range('A', 'Z');
    CharPredicate precomputedRange = rangeAZ.precomputeForAscii();
    assertThat(precomputedRange).isNotSameInstanceAs(rangeAZ);
    assertThat(precomputedRange.precomputeForAscii()).isSameInstanceAs(precomputedRange);

    CharPredicate precomputedAlpha = CharPredicate.ALPHA.precomputeForAscii();
    assertThat(precomputedAlpha).isNotSameInstanceAs(CharPredicate.ALPHA);
    assertThat(precomputedAlpha.precomputeForAscii()).isSameInstanceAs(precomputedAlpha);

    CharPredicate precomputedAscii = CharPredicate.ASCII.precomputeForAscii();
    assertThat(precomputedAscii).isNotSameInstanceAs(CharPredicate.ASCII);
    assertThat(precomputedAscii.precomputeForAscii()).isSameInstanceAs(precomputedAscii);
  }

  @Test public void precomputed_not_not_isSameInstance() {
    CharPredicate digits = PrecomputedCharPredicate.of(c -> c >= '0' && c <= '9');
    assertThat(digits.not().not()).isSameInstanceAs(digits);
  }

  @Test public void precomputed_not_skipLeading_negatedRange() {
    CharPredicate digits = PrecomputedCharPredicate.of(c -> c >= '0' && c <= '9');
    assertThat(digits.not().skipLeading("abcdefgh123", 0)).isEqualTo(8);
  }

  @Test public void precomputed_not_skipLeading_longRun() {
    CharPredicate notDigits = PrecomputedCharPredicate.of(c -> c >= '0' && c <= '9').not();
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(4);
    assertThat(notDigits.skipLeading(letters + "123", 0)).isEqualTo(letters.length());
  }

  @Test public void precomputed_not_skipLeading_doesNotInvokeTestForAscii() {
    CharPredicate isA = PrecomputedCharPredicate.of(c -> c == 'a');
    CharPredicate precomputed = spy(isA);
    assertThat(precomputed.not().skipLeading("bcde", 0)).isEqualTo(4);
    verify(precomputed, never()).test(anyChar());
  }

  @Test public void precomputed_matchesNoneOf_doesNotInvokeTestForAscii() {
    CharPredicate isA = PrecomputedCharPredicate.of(c -> c == 'a');
    CharPredicate precomputed = spy(isA);
    assertThat(precomputed.matchesNoneOf("bcde")).isTrue();
    verify(precomputed, never()).test(anyChar());
  }

  @Test public void precomputed_not_matchesAllOf_doesNotInvokeTestForAscii() {
    CharPredicate isA = PrecomputedCharPredicate.of(c -> c == 'a');
    CharPredicate precomputed = spy(isA);
    assertThat(precomputed.not().matchesAllOf("bcde")).isTrue();
    verify(precomputed, never()).test(anyChar());
  }

  @Test public void skipLeading_precomputed_lower64() {
    assertThat(CharPredicate.range('0', '9').precomputeForAscii().skipLeading("1234567890abc", 0))
        .isEqualTo(10);
  }

  @Test public void skipLeading_precomputed_higher64() {
    assertThat(CharPredicate.range('a', 'z').precomputeForAscii().skipLeading("abcdefgh123", 0))
        .isEqualTo(8);
  }

  @Test public void skipLeading_precomputed_128bitMixed() {
    assertThat(CharPredicate.WORD.precomputeForAscii().skipLeading("user_name_123!done", 0))
        .isEqualTo(13);
  }

  @Test public void skipLeading_precomputed_nonAsciiFallback() {
    CharPredicate nonAsciiOrA = CharPredicate.is('a').or(c -> c > 127);
    assertThat(nonAsciiOrA.precomputeForAscii().skipLeading("a\u00E9\u00FCb", 0)).isEqualTo(3);
  }

  @Test public void skipLeading_precomputed_chunkBoundaryMismatch() {
    CharPredicate letters = CharPredicate.range('a', 'z').precomputeForAscii();
    assertThat(letters.skipLeading("0bcdefgh", 0)).isEqualTo(0);
    assertThat(letters.skipLeading("a0cdefgh", 0)).isEqualTo(1);
    assertThat(letters.skipLeading("ab0defgh", 0)).isEqualTo(2);
    assertThat(letters.skipLeading("abc0efgh", 0)).isEqualTo(3);
    assertThat(letters.skipLeading("abcd0fgh", 0)).isEqualTo(4);
    assertThat(letters.skipLeading("abcde0gh", 0)).isEqualTo(5);
    assertThat(letters.skipLeading("abcdef0h", 0)).isEqualTo(6);
    assertThat(letters.skipLeading("abcdefg0", 0)).isEqualTo(7);
  }

  @Test public void skipLeading_lower64_allMatch() {
    assertThat(CharPredicate.range('0', '9').precomputeForAscii().skipLeading("01234567", 0))
        .isEqualTo(8);
  }

  @Test public void skipLeading_lower64_longRun() {
    String digits = "0123456789".repeat(20);
    assertThat(CharPredicate.range('0', '9').precomputeForAscii().skipLeading(digits + "xyz", 0))
        .isEqualTo(200);
  }

  @Test public void skipLeading_lower64_fromOffset() {
    assertThat(CharPredicate.range('0', '9').precomputeForAscii().skipLeading("xx12345yy", 2))
        .isEqualTo(7);
  }

  @Test public void skipLeading_higher64_allMatch() {
    assertThat(CharPredicate.range('a', 'z').precomputeForAscii().skipLeading("abcdefgh", 0))
        .isEqualTo(8);
  }

  @Test public void skipLeading_higher64_longRun() {
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    assertThat(CharPredicate.range('a', 'z').precomputeForAscii().skipLeading(letters + "123", 0))
        .isEqualTo(260);
  }

  @Test public void skipLeading_higher64_fromOffset() {
    assertThat(CharPredicate.range('a', 'z').precomputeForAscii().skipLeading("12abcdef34", 2))
        .isEqualTo(8);
  }

  @Test public void skipLeading_128bit_allMatch() {
    assertThat(CharPredicate.WORD.precomputeForAscii().skipLeading("a0_b1_c2_d3_e4_f5", 0))
        .isEqualTo(17);
  }

  @Test public void skipLeading_128bit_longRun() {
    String words = "a0_b1_c2_d3_e4_".repeat(20);
    assertThat(CharPredicate.WORD.precomputeForAscii().skipLeading(words + "   ", 0))
        .isEqualTo(300);
  }

  @Test public void skipLeading_128bit_fromOffset() {
    assertThat(CharPredicate.WORD.precomputeForAscii().skipLeading("   a0_b1_c2   ", 3))
        .isEqualTo(11);
  }

  @Test public void skipLeading_nonAscii_allMatch() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8').precomputeForAscii();
    assertThat(nonAscii.skipLeading("\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8", 0))
        .isEqualTo(8);
  }

  @Test public void skipLeading_nonAscii_longRun() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8').precomputeForAscii();
    String unicodeRun = "\u00E9\u00E8".repeat(50);
    assertThat(nonAscii.skipLeading(unicodeRun + "end", 0)).isEqualTo(100);
  }

  @Test public void skipLeading_nonAscii_fromOffset() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8').precomputeForAscii();
    assertThat(nonAscii.skipLeading("xx\u00E9\u00E8\u00E9\u00E8yy", 2)).isEqualTo(6);
  }

  @Test public void skipLeading_mixedAsciiAndNonAscii() {
    CharPredicate mixed = CharPredicate.range('a', 'z').or('\u00E9').precomputeForAscii();
    assertThat(mixed.skipLeading("abc\u00E9def\u00E9123", 0)).isEqualTo(8);
  }
}

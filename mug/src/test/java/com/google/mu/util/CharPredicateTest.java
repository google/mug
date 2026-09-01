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
import static com.google.mu.util.CharPredicate.anyOf;

import com.google.common.testing.NullPointerTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CharPredicateTest {

  @Test public void testRange() {
    assertThat(CharPredicate.range('a', 'z').test('a')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('z')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('v')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('0')).isFalse();
    assertThat(CharPredicate.range('a', 'z').test('C')).isFalse();
  }

  @Test public void testRange_toString() {
    assertThat(CharPredicate.range('a', 'z').toString()).isEqualTo("['a', 'z']");
  }

  @Test public void testIs() {
    assertThat(CharPredicate.is('c').test('c')).isTrue();
    assertThat(CharPredicate.is('x').test('c')).isFalse();
  }

  @Test public void testIs_toString() {
    assertThat(CharPredicate.is('c').toString()).isEqualTo("'c'");
  }

  @Test public void testIsNot() {
    assertThat(CharPredicate.isNot('c').test('c')).isFalse();
    assertThat(CharPredicate.isNot('c').test('x')).isTrue();
  }

  @Test public void testIsNot_toString() {
    assertThat(CharPredicate.isNot('c').toString()).isEqualTo("not ('c')");
  }

  @Test public void testNot() {
    assertThat(CharPredicate.is('c').not().test('c')).isFalse();
    assertThat(CharPredicate.is('c').not().test('x')).isTrue();
  }

  @Test public void testNot_toString() {
    assertThat(CharPredicate.is('c').not().toString()).isEqualTo("not ('c')");
  }

  @Test public void testOr_char() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('c')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('Z')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('z')).isFalse();
    assertThat(CharPredicate.is('x').or('X').test('x')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('X')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('y')).isFalse();
  }

  @Test public void testOr_char_toString() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').toString()).isEqualTo("'c' | ['A', 'Z']");
    assertThat(CharPredicate.range('A', 'Z').or('c').toString()).isEqualTo("['A', 'Z'] | 'c'");
  }

  @Test public void testOr_string() {
    assertThat(CharPredicate.is('x').or("XY").test('x')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('X')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('Y')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('Z')).isFalse();
  }

  @Test public void testOr_emptyString() {
    CharPredicate predicate = CharPredicate.is('x');
    assertThat(predicate.or("")).isSameInstanceAs(predicate);
  }

  @Test public void testAnyOf() {
    assertThat(CharPredicate.anyOf("").test('a')).isFalse();
    assertThat(CharPredicate.anyOf("a").test('a')).isTrue();
    assertThat(CharPredicate.anyOf("b").test('a')).isFalse();
    assertThat(CharPredicate.anyOf("ab").test('a')).isTrue();
    assertThat(CharPredicate.anyOf("ab").test('b')).isTrue();
    assertThat(CharPredicate.anyOf("ab").test('c')).isFalse();
    assertThat(CharPredicate.anyOf("abc").test('a')).isTrue();
    assertThat(CharPredicate.anyOf("abc").test('b')).isTrue();
    assertThat(CharPredicate.anyOf("abc").test('c')).isTrue();
    assertThat(CharPredicate.anyOf("abc").test('d')).isFalse();
  }

  @Test public void testAnyOf_toString() {
    assertThat(CharPredicate.anyOf("abc").toString()).isEqualTo("anyOf('abc')");
    assertThat(CharPredicate.anyOf("ab").toString()).isEqualTo("'a' | 'b'");
    assertThat(CharPredicate.anyOf("a").toString()).isEqualTo("'a'");
    assertThat(CharPredicate.anyOf("").toString()).isEqualTo("NONE");
  }

  @Test public void testNoneOf() {
    assertThat(CharPredicate.noneOf("").test('a')).isTrue();
    assertThat(CharPredicate.noneOf("a").test('a')).isFalse();
    assertThat(CharPredicate.noneOf("a").test('b')).isTrue();
    assertThat(CharPredicate.noneOf("ab").test('a')).isFalse();
    assertThat(CharPredicate.noneOf("ab").test('b')).isFalse();
    assertThat(CharPredicate.noneOf("ab").test('c')).isTrue();
    assertThat(CharPredicate.noneOf("abc").test('a')).isFalse();
    assertThat(CharPredicate.noneOf("abc").test('b')).isFalse();
    assertThat(CharPredicate.noneOf("abc").test('c')).isFalse();
    assertThat(CharPredicate.noneOf("abc").test('d')).isTrue();
  }

  @Test public void testNoneOf_toString() {
    assertThat(CharPredicate.noneOf("ab").toString()).isEqualTo("not ('a' | 'b')");
    assertThat(CharPredicate.noneOf("a").toString()).isEqualTo("not ('a')");
    assertThat(CharPredicate.noneOf("abc").toString()).isEqualTo("not (anyOf('abc'))");
    assertThat(CharPredicate.noneOf("").toString()).isEqualTo("not (NONE)");
  }

  @Test public void testMatchesAnyOf() {
    assertThat(CharPredicate.range('0', '9').matchesAnyOf("-")).isFalse();
    assertThat(CharPredicate.range('0', '9').matchesAnyOf("0")).isTrue();
  }

  @Test public void testMatchesNoneOf() {
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("a")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("b")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("c")).isTrue();
  }

  @Test public void testMatchesAllOf() {
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("a")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("b")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("ba")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("abc")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("c")).isFalse();
  }

  @Test public void testIsPrefixOf() {
    assertThat(CharPredicate.range('0', '9').isPrefixOf("0-")).isTrue();
    assertThat(CharPredicate.range('0', '9').isPrefixOf("1-")).isTrue();
    assertThat(CharPredicate.range('0', '9').isPrefixOf("-1")).isFalse();
  }

  @Test public void testIsSuffixOf() {
    assertThat(CharPredicate.range('0', '9').isSuffixOf("10")).isTrue();
    assertThat(CharPredicate.range('0', '9').isSuffixOf("a1")).isTrue();
    assertThat(CharPredicate.range('0', '9').isSuffixOf("1a")).isFalse();
  }

  @Test public void testNulls() throws Throwable {
    CharPredicate p = CharPredicate.is('a');
    new NullPointerTester().testAllPublicInstanceMethods(p);
    new NullPointerTester().testAllPublicStaticMethods(CharPredicate.class);
  }

  @Test public void precomputeForAscii_lowerRangeMatches() {
    // ' ' is 32, '#' is 35
    CharPredicate predicate = anyOf(" #").precomputeForAscii();

    assertThat(predicate.test(' ')).isTrue();
    assertThat(predicate.test('#')).isTrue();
    assertThat(predicate.test('!')).isFalse(); // 33 - not in mask
  }

  @Test public void precomputeForAscii_higherRangeMatches() {
    // 'A' is 65, 'Z' is 90
    CharPredicate predicate = anyOf("AZ").precomputeForAscii();

    assertThat(predicate.test('A')).isTrue();
    assertThat(predicate.test('Z')).isTrue();
    assertThat(predicate.test('B')).isFalse(); // 66 - not in mask
  }

  @Test public void precomputeForAscii_boundaryBetweenMasks() {
    // 63 is '?', 64 is '@'
    CharPredicate predicate = anyOf("?@").precomputeForAscii();

    assertThat(predicate.test('?')).isTrue();
    assertThat(predicate.test('@')).isTrue();
  }

  @Test public void precomputeForAscii_nonAsciiTrue() {
    // 'π' (U+03C0) = 960, '€' (U+20AC) = 8364
    CharPredicate predicate = anyOf("π€").precomputeForAscii();

    assertThat(predicate.test('π')).isTrue();
    assertThat(predicate.test('€')).isTrue();
  }

  @Test public void precomputeForAscii_nonAsciiFalse() {
    // Only contains 'π'
    CharPredicate predicate = anyOf("π").precomputeForAscii();

    // 'Ω' (U+03A9) = 937. Non-ASCII, but not in the string.
    assertThat(predicate.test('Ω')).isFalse();
    // 'ÿ' (U+00FF) = 255.
    assertThat(predicate.test('ÿ')).isFalse();
  }

  @Test public void precomputeForAscii_asciiBoundaryToSlowPath() {
    CharPredicate predicate = anyOf("a").precomputeForAscii();
    assertThat(predicate.test((char) 127)).isFalse();
    assertThat(predicate.test((char) 128)).isFalse();
  }

  @Test public void precomputeForAscii_emptyInput() {
    CharPredicate predicate = anyOf("").precomputeForAscii();
    assertThat(predicate.test('a')).isFalse();
    assertThat(predicate.test(' ')).isFalse();
    assertThat(predicate.test('π')).isFalse();
  }

  @Test public void precomputeForAscii_any() {
    CharPredicate predicate = CharPredicate.ANY.precomputeForAscii();
    for (int i = 0; i <= 128; i++) {
      assertThat(predicate.test((char) i)).isTrue();
    }
    assertThat(predicate.test('π')).isTrue();
  }

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
    assertThat(CharPredicate.ASCII.precomputeForAscii()).isSameInstanceAs(CharPredicate.ASCII);
    assertThat(CharPredicate.ALPHA.precomputeForAscii()).isSameInstanceAs(CharPredicate.ALPHA);

    CharPredicate isA = CharPredicate.is('a');
    assertThat(isA.precomputeForAscii()).isSameInstanceAs(isA);

    CharPredicate rangeAZ = CharPredicate.range('A', 'Z');
    assertThat(rangeAZ.precomputeForAscii()).isSameInstanceAs(rangeAZ);

    CharPredicate notA = isA.not().precomputeForAscii();
    assertThat(notA.precomputeForAscii()).isSameInstanceAs(notA);
  }

  @Test public void skipLeading_emptyCharSequence() {
    assertThat(CharPredicate.is('a').skipLeading("", 0)).isEqualTo(0);
  }

  @Test public void skipLeading_allMatch() {
    assertThat(CharPredicate.is('a').skipLeading("aaaa", 0)).isEqualTo(4);
  }

  @Test public void skipLeading_partialMatch() {
    assertThat(CharPredicate.is('a').skipLeading("aaba", 0)).isEqualTo(2);
  }

  @Test public void skipLeading_noMatch() {
    assertThat(CharPredicate.is('a').skipLeading("baaa", 0)).isEqualTo(0);
  }

  @Test public void skipLeading_withOffsetBegin() {
    assertThat(CharPredicate.is('a').skipLeading("xxaay", 2)).isEqualTo(4);
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
    assertThat(CharPredicate.range('0', '9').skipLeading("01234567", 0)).isEqualTo(8);
  }

  @Test public void skipLeading_lower64_longRun() {
    String digits = "0123456789".repeat(20);
    assertThat(CharPredicate.range('0', '9').skipLeading(digits + "xyz", 0)).isEqualTo(200);
  }

  @Test public void skipLeading_lower64_fromOffset() {
    assertThat(CharPredicate.range('0', '9').skipLeading("xx12345yy", 2)).isEqualTo(7);
  }

  @Test public void skipLeading_higher64_allMatch() {
    assertThat(CharPredicate.range('a', 'z').skipLeading("abcdefgh", 0)).isEqualTo(8);
  }

  @Test public void skipLeading_higher64_longRun() {
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    assertThat(CharPredicate.range('a', 'z').skipLeading(letters + "123", 0)).isEqualTo(260);
  }

  @Test public void skipLeading_higher64_fromOffset() {
    assertThat(CharPredicate.range('a', 'z').skipLeading("12abcdef34", 2)).isEqualTo(8);
  }

  @Test public void skipLeading_128bit_allMatch() {
    assertThat(CharPredicate.WORD.skipLeading("a0_b1_c2_d3_e4_f5", 0)).isEqualTo(17);
  }

  @Test public void skipLeading_128bit_longRun() {
    String words = "a0_b1_c2_d3_e4_".repeat(20);
    assertThat(CharPredicate.WORD.skipLeading(words + "   ", 0)).isEqualTo(300);
  }

  @Test public void skipLeading_128bit_fromOffset() {
    assertThat(CharPredicate.WORD.skipLeading("   a0_b1_c2   ", 3)).isEqualTo(11);
  }

  @Test public void skipLeading_nonAscii_allMatch() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8');
    assertThat(nonAscii.skipLeading("\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8", 0))
        .isEqualTo(8);
  }

  @Test public void skipLeading_nonAscii_longRun() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8');
    String unicodeRun = "\u00E9\u00E8".repeat(50);
    assertThat(nonAscii.skipLeading(unicodeRun + "end", 0)).isEqualTo(100);
  }

  @Test public void skipLeading_nonAscii_fromOffset() {
    CharPredicate nonAscii = CharPredicate.is('\u00E9').or('\u00E8');
    assertThat(nonAscii.skipLeading("xx\u00E9\u00E8\u00E9\u00E8yy", 2)).isEqualTo(6);
  }

  @Test public void skipLeading_mixedAsciiAndNonAscii() {
    CharPredicate mixed = CharPredicate.range('a', 'z').or('\u00E9');
    assertThat(mixed.skipLeading("abc\u00E9def\u00E9123", 0)).isEqualTo(8);
  }

  @Test public void default_skipLeading_emptyCharSequence() {
    CharPredicate isA = c -> c == 'a';
    assertThat(isA.skipLeading("", 0)).isEqualTo(0);
  }

  @Test public void default_skipLeading_allMatch() {
    CharPredicate isA = c -> c == 'a';
    assertThat(isA.skipLeading("aaaa", 0)).isEqualTo(4);
  }

  @Test public void default_skipLeading_partialMatch() {
    CharPredicate isA = c -> c == 'a';
    assertThat(isA.skipLeading("aaba", 0)).isEqualTo(2);
  }

  @Test public void default_skipLeading_noMatch() {
    CharPredicate isA = c -> c == 'a';
    assertThat(isA.skipLeading("baaa", 0)).isEqualTo(0);
  }

  @Test public void default_skipLeading_fromOffset() {
    CharPredicate isA = c -> c == 'a';
    assertThat(isA.skipLeading("xxaay", 2)).isEqualTo(4);
  }

  @Test public void any_skipLeading_allMatch() {
    assertThat(CharPredicate.ANY.skipLeading("hello world", 0)).isEqualTo(11);
  }

  @Test public void any_skipLeading_fromOffset() {
    assertThat(CharPredicate.ANY.skipLeading("hello world", 3)).isEqualTo(11);
  }

  @Test public void any_skipLeading_empty() {
    assertThat(CharPredicate.ANY.skipLeading("", 0)).isEqualTo(0);
  }

  @Test public void none_skipLeading_returnsBegin() {
    assertThat(CharPredicate.NONE.skipLeading("hello world", 0)).isEqualTo(0);
  }

  @Test public void none_skipLeading_fromOffset() {
    assertThat(CharPredicate.NONE.skipLeading("hello world", 3)).isEqualTo(3);
  }

  @Test public void none_skipLeading_empty() {
    assertThat(CharPredicate.NONE.skipLeading("", 0)).isEqualTo(0);
  }

  @Test public void ascii_skipLeading_asciiOnly() {
    assertThat(CharPredicate.ASCII.skipLeading("abc123XYZ!@#", 0)).isEqualTo(12);
  }

  @Test public void ascii_skipLeading_stopsAtNonAscii() {
    assertThat(CharPredicate.ASCII.skipLeading("abc\u00E9def", 0)).isEqualTo(3);
  }

  @Test public void alpha_skipLeading_matchesAlpha() {
    assertThat(CharPredicate.ALPHA.skipLeading("HelloWorld123", 0)).isEqualTo(10);
  }

  @Test public void whitespace_skipLeading_matchesWhitespace() {
    assertThat(CharPredicate.WHITESPACE.skipLeading("  \t\n  abc", 0)).isEqualTo(6);
  }

  @Test public void anyOf_skipLeading_matchesChars() {
    assertThat(CharPredicate.anyOf("abc").skipLeading("cbabacdef", 0)).isEqualTo(6);
  }

  @Test public void noneOf_skipLeading_matchesNonChars() {
    assertThat(CharPredicate.noneOf("abc").skipLeading("xyz123abc", 0)).isEqualTo(6);
  }

  @Test public void matchesAllOf_empty_isTrue() {
    assertThat(CharPredicate.range('0', '9').matchesAllOf("")).isTrue();
  }

  @Test public void matchesAllOf_allMatch_isTrue() {
    assertThat(CharPredicate.range('0', '9').matchesAllOf("1234567890")).isTrue();
  }

  @Test public void matchesAllOf_mismatchAtStart_isFalse() {
    assertThat(CharPredicate.range('0', '9').matchesAllOf("x1234567890")).isFalse();
  }

  @Test public void matchesAllOf_mismatchAtEnd_isFalse() {
    assertThat(CharPredicate.range('0', '9').matchesAllOf("1234567890x")).isFalse();
  }

  @Test public void matchesAllOf_longRun_isTrue() {
    String digits = "0123456789".repeat(10);
    assertThat(CharPredicate.range('0', '9').matchesAllOf(digits)).isTrue();
  }

  @Test public void matchesNoneOf_empty_isTrue() {
    assertThat(CharPredicate.range('0', '9').matchesNoneOf("")).isTrue();
  }

  @Test public void matchesNoneOf_noneMatch_isTrue() {
    assertThat(CharPredicate.range('0', '9').matchesNoneOf("abcdefgh")).isTrue();
  }

  @Test public void matchesNoneOf_matchAtStart_isFalse() {
    assertThat(CharPredicate.range('0', '9').matchesNoneOf("1abcdefgh")).isFalse();
  }

  @Test public void matchesNoneOf_matchAtEnd_isFalse() {
    assertThat(CharPredicate.range('0', '9').matchesNoneOf("abcdefgh1")).isFalse();
  }

  @Test public void matchesNoneOf_longRun_isTrue() {
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    assertThat(CharPredicate.range('0', '9').matchesNoneOf(letters)).isTrue();
  }
}

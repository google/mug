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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CharPredicateTest {

  @Test
  public void testRange() {
    assertThat(CharPredicate.range('a', 'z').test('a')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('z')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('v')).isTrue();
    assertThat(CharPredicate.range('a', 'z').test('0')).isFalse();
    assertThat(CharPredicate.range('a', 'z').test('C')).isFalse();
  }

  @Test
  public void testRange_toString() {
    assertThat(CharPredicate.range('a', 'z').toString()).isEqualTo("['a', 'z']");
  }

  @Test
  public void testIs() {
    assertThat(CharPredicate.is('c').test('c')).isTrue();
    assertThat(CharPredicate.is('x').test('c')).isFalse();
  }

  @Test
  public void testIs_toString() {
    assertThat(CharPredicate.is('c').toString()).isEqualTo("'c'");
  }

  @Test
  public void testIsNot() {
    assertThat(CharPredicate.isNot('c').test('c')).isFalse();
    assertThat(CharPredicate.isNot('c').test('x')).isTrue();
  }

  @Test
  public void testIsNot_toString() {
    assertThat(CharPredicate.isNot('c').toString()).isEqualTo("[\\u0000-bd-\\uFFFF]");
  }

  @Test
  public void testNot() {
    assertThat(CharPredicate.is('c').not().test('c')).isFalse();
    assertThat(CharPredicate.is('c').not().test('x')).isTrue();
  }

  @Test
  public void testNot_toString() {
    assertThat(CharPredicate.is('c').not().toString()).isEqualTo("[\\u0000-bd-\\uFFFF]");
  }

  @Test
  public void testOr_char() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('c')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('Z')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('z')).isFalse();
    assertThat(CharPredicate.is('x').or('X').test('x')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('X')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('y')).isFalse();
  }

  @Test
  public void testOr_char_toString() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').toString()).isEqualTo("[A-Zc]");
    assertThat(CharPredicate.range('A', 'Z').or('c').toString()).isEqualTo("[A-Zc]");
  }

  @Test
  public void testOr_string() {
    assertThat(CharPredicate.is('x').or("XY").test('x')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('X')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('Y')).isTrue();
    assertThat(CharPredicate.is('x').or("XY").test('Z')).isFalse();
  }

  @Test
  public void testOr_emptyString() {
    CharPredicate predicate = CharPredicate.is('x');
    assertThat(predicate.or("")).isSameInstanceAs(predicate);
  }

  @Test
  public void testAnyOf() {
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

  @Test
  public void testAnyOf_toString() {
    assertThat(CharPredicate.anyOf("abc").toString()).isEqualTo("[a-c]");
    assertThat(CharPredicate.anyOf("ab").toString()).isEqualTo("[ab]");
    assertThat(CharPredicate.anyOf("a").toString()).isEqualTo("'a'");
    assertThat(CharPredicate.anyOf("").toString()).isEqualTo("NONE");
  }

  @Test
  public void testAnyOf_withControlAndUnicodeChars_toString() {
    assertThat(CharPredicate.anyOf("\n\r\t").toString()).isEqualTo("[\\t\\n\\r]");
    assertThat(CharPredicate.anyOf("a\u0080\u00FF").toString()).isEqualTo("[a\\u0080\\u00FF]");
  }

  @Test
  public void testNoneOf() {
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

  @Test
  public void testNoneOf_toString() {
    assertThat(CharPredicate.noneOf("ab").toString()).isEqualTo("[\\u0000-`c-\\uFFFF]");
    assertThat(CharPredicate.noneOf("a").toString()).isEqualTo("[\\u0000-`b-\\uFFFF]");
    assertThat(CharPredicate.noneOf("abc").toString()).isEqualTo("[\\u0000-`d-\\uFFFF]");
    assertThat(CharPredicate.noneOf("").toString()).isEqualTo("[\\u0000-\\uFFFF]");
  }

  @Test
  public void testMatchesAnyOf() {
    assertThat(CharPredicate.range('0', '9').matchesAnyOf("-")).isFalse();
    assertThat(CharPredicate.range('0', '9').matchesAnyOf("0")).isTrue();
  }

  @Test
  public void testMatchesNoneOf() {
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("a")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("b")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesNoneOf("c")).isTrue();
  }

  @Test
  public void testMatchesAllOf() {
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("a")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("b")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("ba")).isTrue();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("abc")).isFalse();
    assertThat(CharPredicate.anyOf("ab").matchesAllOf("c")).isFalse();
  }

  @Test
  public void testIsPrefixOf() {
    assertThat(CharPredicate.range('0', '9').isPrefixOf("0-")).isTrue();
    assertThat(CharPredicate.range('0', '9').isPrefixOf("1-")).isTrue();
    assertThat(CharPredicate.range('0', '9').isPrefixOf("-1")).isFalse();
  }

  @Test
  public void testIsSuffixOf() {
    assertThat(CharPredicate.range('0', '9').isSuffixOf("10")).isTrue();
    assertThat(CharPredicate.range('0', '9').isSuffixOf("a1")).isTrue();
    assertThat(CharPredicate.range('0', '9').isSuffixOf("1a")).isFalse();
  }

  @Test
  public void testNulls() throws Throwable {
    CharPredicate p = CharPredicate.is('a');
    new NullPointerTester().testAllPublicInstanceMethods(p);
    new NullPointerTester().testAllPublicStaticMethods(CharPredicate.class);
  }

  @Test
  public void precomputeForAscii_lowerRangeMatches() {
    // ' ' is 32, '#' is 35
    CharPredicate predicate = anyOf(" #").precomputeForAscii();

    assertThat(predicate.test(' ')).isTrue();
    assertThat(predicate.test('#')).isTrue();
    assertThat(predicate.test('!')).isFalse(); // 33 - not in mask
  }

  @Test
  public void precomputeForAscii_higherRangeMatches() {
    // 'A' is 65, 'Z' is 90
    CharPredicate predicate = anyOf("AZ").precomputeForAscii();

    assertThat(predicate.test('A')).isTrue();
    assertThat(predicate.test('Z')).isTrue();
    assertThat(predicate.test('B')).isFalse(); // 66 - not in mask
  }

  @Test
  public void precomputeForAscii_boundaryBetweenMasks() {
    // 63 is '?', 64 is '@'
    CharPredicate predicate = anyOf("?@").precomputeForAscii();

    assertThat(predicate.test('?')).isTrue();
    assertThat(predicate.test('@')).isTrue();
  }

  @Test
  public void precomputeForAscii_nonAsciiTrue() {
    // 'π' (U+03C0) = 960, '€' (U+20AC) = 8364
    CharPredicate predicate = anyOf("π€").precomputeForAscii();

    assertThat(predicate.test('π')).isTrue();
    assertThat(predicate.test('€')).isTrue();
  }

  @Test
  public void precomputeForAscii_nonAsciiFalse() {
    // Only contains 'π'
    CharPredicate predicate = anyOf("π").precomputeForAscii();

    // 'Ω' (U+03A9) = 937. Non-ASCII, but not in the string.
    assertThat(predicate.test('Ω')).isFalse();
    // 'ÿ' (U+00FF) = 255.
    assertThat(predicate.test('ÿ')).isFalse();
  }

  @Test
  public void precomputeForAscii_asciiBoundaryToSlowPath() {
    CharPredicate predicate = anyOf("a").precomputeForAscii();
    assertThat(predicate.test((char) 127)).isFalse();
    assertThat(predicate.test((char) 128)).isFalse();
  }

  @Test
  public void precomputeForAscii_emptyInput() {
    CharPredicate predicate = anyOf("").precomputeForAscii();
    assertThat(predicate.test('a')).isFalse();
    assertThat(predicate.test(' ')).isFalse();
    assertThat(predicate.test('π')).isFalse();
  }

  @Test
  public void precomputeForAscii_any() {
    CharPredicate predicate = CharPredicate.ANY.precomputeForAscii();
    for (int i = 0; i <= 128; i++) {
      assertThat(predicate.test((char) i)).isTrue();
    }
    assertThat(predicate.test('π')).isTrue();
  }

  @Test
  public void precomputeForAscii_none() {
    CharPredicate predicate = CharPredicate.NONE.precomputeForAscii();
    for (int i = 0; i <= 128; i++) {
      assertThat(predicate.test((char) i)).isFalse();
    }
    assertThat(predicate.test('π')).isFalse();
  }

  @Test
  public void precomputeForAscii_ascii() {
    CharPredicate predicate = CharPredicate.ASCII.precomputeForAscii();
    for (int i = 0; i < 128; i++) {
      assertThat(predicate.test((char) i)).isTrue();
    }
    assertThat(predicate.test('π')).isFalse();
  }

  @Test
  public void precomputeForAscii_nonAscii() {
    CharPredicate predicate = CharPredicate.ASCII.not().precomputeForAscii();
    for (int i = 0; i < 128; i++) {
      assertThat(predicate.test((char) i)).isFalse();
    }
    assertThat(predicate.test('π')).isTrue();
  }

  @Test
  public void precomputeForAscii_isoControl() {
    CharPredicate isIsoControl =  Character::isISOControl;
    CharPredicate predicate = isIsoControl.precomputeForAscii();
    assertThat(predicate.test('a')).isFalse();
    assertThat(predicate.test('\n')).isTrue();
    assertThat(predicate.test('\0')).isTrue();
    assertThat(predicate.test('\t')).isTrue();
  }

  @Test
  public void precomputeForAscii_whitespace() {
    assertThat(WHITESPACE.test('a')).isFalse();
    assertThat(WHITESPACE.test('\n')).isTrue();
    assertThat(WHITESPACE.test(' ')).isTrue();
    assertThat(WHITESPACE.test('\t')).isTrue();
  }

  @Test
  public void precomputeForAscii_idempotent() {
    CharPredicate predicate = CharPredicate.ASCII.precomputeForAscii();
    assertThat(predicate.precomputeForAscii()).isSameInstanceAs(predicate);
  }

  @Test
  public void characterRangeSet_alpha() {
    assertThat(CharPredicate.ALPHA.characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_word() {
    assertThat(CharPredicate.WORD.characterRangeSet()).isEqualTo("[0-9A-Z_a-z]");
  }

  @Test
  public void characterRangeSet_ascii() {
    assertThat(CharPredicate.ASCII.characterRangeSet()).isEqualTo("[\\u0000-\\u007F]");
  }

  @Test
  public void characterRangeSet_any() {
    assertThat(CharPredicate.ANY.characterRangeSet()).isEqualTo("[\\u0000-\\uFFFF]");
  }

  @Test
  public void characterRangeSet_none() {
    assertThat(CharPredicate.NONE.characterRangeSet()).isEqualTo("[]");
  }

  @Test
  public void characterRangeSet_whitespace() {
    assertThat(WHITESPACE.characterRangeSet()).isEqualTo("");
  }

  @Test
  public void characterRangeSet_is() {
    assertThat(CharPredicate.is('x').characterRangeSet()).isEqualTo("[x]");
  }

  @Test
  public void characterRangeSet_range() {
    assertThat(CharPredicate.range('a', 'c').characterRangeSet()).isEqualTo("[a-c]");
    assertThat(CharPredicate.range('a', 'b').characterRangeSet()).isEqualTo("[ab]");
  }

  @Test
  public void characterRangeSet_anyOf() {
    assertThat(anyOf("xyz").characterRangeSet()).isEqualTo("[x-z]");
    assertThat(anyOf("acb-").characterRangeSet()).isEqualTo("[-a-c]");
    assertThat(anyOf("fooBAR345").characterRangeSet()).isEqualTo("[3-5ABRfo]");
    assertThat(anyOf("fooBAR345").not().characterRangeSet())
        .isEqualTo("[\\u0000-26-@C-QS-eg-np-\\uFFFF]");
    assertThat(anyOf("fooBAR345").not().not().characterRangeSet()).isEqualTo("[3-5ABRfo]");
  }

  @Test
  public void characterRangeSet_or_adjacent_ranges() {
    assertThat(CharPredicate.range('a', 'c').or(CharPredicate.range('d', 'f')).characterRangeSet()).isEqualTo("[a-f]");
  }

  @Test
  public void characterRangeSet_or_overlapping_ranges() {
    assertThat(CharPredicate.range('a', 'd').or(CharPredicate.range('c', 'f')).characterRangeSet()).isEqualTo("[a-f]");
  }

  @Test
  public void characterRangeSet_or_subset_ranges() {
    assertThat(CharPredicate.range('a', 'f').or(CharPredicate.range('c', 'e')).characterRangeSet()).isEqualTo("[a-f]");
  }

  @Test
  public void characterRangeSet_and_disjoint_ranges() {
    assertThat(CharPredicate.range('a', 'c').and(CharPredicate.range('e', 'g')).characterRangeSet()).isEqualTo("[]");
  }

  @Test
  public void characterRangeSet_and_overlapping_ranges() {
    assertThat(CharPredicate.range('a', 'd').and(CharPredicate.range('c', 'f')).characterRangeSet()).isEqualTo("[cd]");
  }

  @Test
  public void characterRangeSet_and_subset_ranges() {
    assertThat(CharPredicate.range('a', 'f').and(CharPredicate.range('c', 'e')).characterRangeSet()).isEqualTo("[c-e]");
  }

  @Test
  public void characterRangeSet_coalesce_unsorted() {
    assertThat(anyOf("aeb").characterRangeSet()).isEqualTo("[abe]");
  }

  @Test
  public void characterRangeSet_coalesce_adjacent() {
    assertThat(anyOf("ab").characterRangeSet()).isEqualTo("[ab]");
  }

  @Test
  public void characterRangeSet_or_disjoint() {
    CharPredicate digit = CharPredicate.range('0', '9');
    assertThat(CharPredicate.ALPHA.or(digit).characterRangeSet()).isEqualTo("[0-9A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_overlapping() {
    assertThat(CharPredicate.ALPHA.or(anyOf("a0")).characterRangeSet()).isEqualTo("[0A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_char() {
    assertThat(CharPredicate.ALPHA.or('0').characterRangeSet()).isEqualTo("[0A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_range() {
    assertThat(CharPredicate.ALPHA.orRange('0', '9').characterRangeSet()).isEqualTo("[0-9A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_string() {
    assertThat(CharPredicate.ALPHA.or("012").characterRangeSet()).isEqualTo("[0-2A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_duplicate_self() {
    assertThat(CharPredicate.ALPHA.or(CharPredicate.ALPHA).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_duplicate_subset() {
    assertThat(CharPredicate.ALPHA.or(anyOf("aA")).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_none() {
    assertThat(CharPredicate.ALPHA.or(CharPredicate.NONE).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_or_any() {
    assertThat(CharPredicate.ALPHA.or(CharPredicate.ANY).characterRangeSet()).isEqualTo("[\\u0000-\\uFFFF]");
  }

  @Test
  public void characterRangeSet_and_disjoint() {
    CharPredicate digit = CharPredicate.range('0', '9');
    assertThat(CharPredicate.ALPHA.and(digit).characterRangeSet()).isEqualTo("[]");
  }

  @Test
  public void characterRangeSet_and_overlapping() {
    assertThat(CharPredicate.ALPHA.and(anyOf("a0")).characterRangeSet()).isEqualTo("[a]");
  }

  @Test
  public void characterRangeSet_and_subset() {
    assertThat(CharPredicate.ALPHA.and(CharPredicate.ASCII).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_and_duplicate_self() {
    assertThat(CharPredicate.ALPHA.and(CharPredicate.ALPHA).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_and_duplicate_subset() {
    assertThat(CharPredicate.ALPHA.and(anyOf("aA")).characterRangeSet()).isEqualTo("[Aa]");
  }

  @Test
  public void characterRangeSet_and_none() {
    assertThat(CharPredicate.ALPHA.and(CharPredicate.NONE).characterRangeSet()).isEqualTo("[]");
  }

  @Test
  public void characterRangeSet_and_any() {
    assertThat(CharPredicate.ALPHA.and(CharPredicate.ANY).characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void characterRangeSet_customPredicateWithCharacterClass() {
    CharPredicate custom = new CharPredicate() {
      @Override public boolean test(char c) {
        return c >= '0' && c <= '9';
      }
      @Override public String characterRangeSet() {
        return "[0-9]";
      }
    };
    assertThat(CharPredicate.ALPHA.or(custom).characterRangeSet()).isEmpty();
    assertThat(CharPredicate.ALPHA.and(custom).characterRangeSet()).isEmpty();
  }

  @Test
  public void characterRangeSet_or_lambda() {
    CharPredicate lambda = c -> c == '0';
    assertThat(CharPredicate.ALPHA.or(lambda).characterRangeSet()).isEmpty();
  }

  @Test
  public void characterRangeSet_and_lambda() {
    CharPredicate lambda = c -> c == '0';
    assertThat(CharPredicate.ALPHA.and(lambda).characterRangeSet()).isEmpty();
  }

  @Test
  public void characterRangeSet_not_alpha() {
    assertThat(CharPredicate.ALPHA.not().characterRangeSet()).isEqualTo("[\\u0000-@[-`{-\\uFFFF]");
  }

  @Test
  public void characterRangeSet_not_none() {
    assertThat(CharPredicate.NONE.not().characterRangeSet()).isEqualTo("[\\u0000-\\uFFFF]");
  }

  @Test
  public void characterRangeSet_not_any() {
    assertThat(CharPredicate.ANY.not().characterRangeSet()).isEqualTo("[]");
  }

  @Test
  public void characterRangeSet_not_lambda() {
    CharPredicate lambda = c -> c == '0';
    assertThat(lambda.not().characterRangeSet()).isEmpty();
  }

  @Test
  public void characterRangeSet_noneOf() {
    assertThat(CharPredicate.noneOf("abc").characterRangeSet()).isEqualTo("[\\u0000-`d-\\uFFFF]");
  }

  @Test
  public void characterRangeSet_escapes() {
    assertThat(anyOf("\r\n\t\f\b\\[]^").characterRangeSet()).isEqualTo("[\\b-\\n\\f\\r[-^]");
  }

  @Test
  public void characterRangeSet_not_rangeEndingAtMax() {
    assertThat(CharPredicate.range('a', '\uFFFF').not().characterRangeSet()).isEqualTo("[\\u0000-`]");
  }

  @Test
  public void characterRangeSet_anyOf_dashWithControl() {
    assertThat(anyOf("\n-").characterRangeSet()).isEqualTo("[-\\n]");
  }

  @Test
  public void characterRangeSet_anyOf_dash() {
    assertThat(anyOf("-").characterRangeSet()).isEqualTo("[-]");
  }

  @Test
  public void characterRangeSet_anyOf_caret() {
    assertThat(anyOf("^").characterRangeSet()).isEqualTo("[^]");
  }

  @Test
  public void characterRangeSet_anyOf_leftBracket() {
    assertThat(anyOf("[").characterRangeSet()).isEqualTo("[[]");
  }

  @Test
  public void characterRangeSet_anyOf_rightBracket() {
    assertThat(anyOf("]").characterRangeSet()).isEqualTo("[]]");
  }

  @Test
  public void characterRangeSet_precomputed() {
    assertThat(CharPredicate.ALPHA.precomputeForAscii().characterRangeSet()).isEqualTo("[A-Za-z]");
  }

  @Test
  public void not_not_idempotent() {
    CharPredicate alpha = CharPredicate.ALPHA;
    assertThat(alpha.not().not()).isSameInstanceAs(alpha);

    CharPredicate custom = c -> c == 'a';
    assertThat(custom.not().not()).isSameInstanceAs(custom);
  }

  @Test
  public void toString_alpha() {
    assertThat(CharPredicate.ALPHA.toString()).isEqualTo("ALPHA");
  }

  @Test
  public void toString_unionWithKnownCharacterClass() {
    CharPredicate digit = CharPredicate.range('0', '9');
    assertThat(CharPredicate.ALPHA.or(digit).toString()).isEqualTo("[0-9A-Za-z]");
  }

  @Test
  public void toString_unionWithUnknownCharacterClass() {
    CharPredicate custom = new CharPredicate() {
      @Override public boolean test(char c) { return false; }
      @Override public String toString() { return "custom"; }
    };
    assertThat(CharPredicate.ALPHA.or(custom).toString()).isEqualTo("ALPHA | custom");
  }

  @Test
  public void toString_intersectionWithKnownCharacterClass() {
    CharPredicate digit = CharPredicate.range('0', '9');
    assertThat(CharPredicate.ALPHA.and(digit).toString()).isEqualTo("[]");
  }

  @Test
  public void toString_intersectionWithUnknownCharacterClass() {
    CharPredicate custom = new CharPredicate() {
      @Override public boolean test(char c) { return false; }
      @Override public String toString() { return "custom"; }
    };
    assertThat(CharPredicate.ALPHA.and(custom).toString()).isEqualTo("ALPHA & custom");
  }

  @Test
  public void toString_negationWithKnownCharacterClass() {
    assertThat(CharPredicate.ALPHA.not().toString()).isEqualTo("[\\u0000-@[-`{-\\uFFFF]");
  }

  @Test
  public void toString_negationWithUnknownCharacterClass() {
    CharPredicate custom = new CharPredicate() {
      @Override public boolean test(char c) { return false; }
      @Override public String toString() { return "custom"; }
    };
    assertThat(custom.not().toString()).isEqualTo("not (custom)");
  }

  @Test
  public void precomputeForAscii_optimizations() {
    assertThat(CharPredicate.NONE.precomputeForAscii()).isSameInstanceAs(CharPredicate.NONE);
    assertThat(CharPredicate.NONE.precomputeForAscii().precomputeForAscii())
        .isSameInstanceAs(CharPredicate.NONE);

    CharPredicate nonAsciiRange = CharPredicate.range('\u0080', '\u00FF');
    assertThat(nonAsciiRange.precomputeForAscii()).isSameInstanceAs(nonAsciiRange);
    assertThat(nonAsciiRange.precomputeForAscii().precomputeForAscii()).isSameInstanceAs(nonAsciiRange);

    CharPredicate nonAsciiSingle = CharPredicate.is('\u0080');
    assertThat(nonAsciiSingle.precomputeForAscii()).isSameInstanceAs(nonAsciiSingle);

    CharPredicate asciiRange = CharPredicate.range('a', 'z');
    CharPredicate precomputedAsciiRange = asciiRange.precomputeForAscii();
    assertThat(precomputedAsciiRange).isNotSameInstanceAs(asciiRange);
    assertThat(precomputedAsciiRange.precomputeForAscii()).isSameInstanceAs(precomputedAsciiRange);

    CharPredicate asciiSingle = CharPredicate.is('a');
    CharPredicate precomputedAsciiSingle = asciiSingle.precomputeForAscii();
    assertThat(precomputedAsciiSingle).isNotSameInstanceAs(asciiSingle);
    assertThat(precomputedAsciiSingle.precomputeForAscii()).isSameInstanceAs(precomputedAsciiSingle);
  }
}

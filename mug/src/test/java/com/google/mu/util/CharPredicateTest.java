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
    assertThat(CharPredicate.isNot('c').toString()).isEqualTo("not ('c')");
  }

  @Test
  public void testNot() {
    assertThat(CharPredicate.is('c').not().test('c')).isFalse();
    assertThat(CharPredicate.is('c').not().test('x')).isTrue();
  }

  @Test
  public void testNot_toString() {
    assertThat(CharPredicate.is('c').not().toString()).isEqualTo("not ('c')");
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
    assertThat(CharPredicate.is('c').orRange('A', 'Z').toString()).isEqualTo("'c' | ['A', 'Z']");
    assertThat(CharPredicate.range('A', 'Z').or('c').toString()).isEqualTo("['A', 'Z'] | 'c'");
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
    assertThat(CharPredicate.is('x').or("").test('x')).isTrue();
    assertThat(CharPredicate.is('x').or("").test('y')).isFalse();
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
    assertThat(CharPredicate.anyOf("abc").toString()).isEqualTo("anyOf('abc')");
    assertThat(CharPredicate.anyOf("ab").toString()).isEqualTo("'a' | 'b'");
    assertThat(CharPredicate.anyOf("a").toString()).isEqualTo("'a'");
    assertThat(CharPredicate.anyOf("").toString()).isEqualTo("NONE");
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
    assertThat(CharPredicate.noneOf("ab").toString()).isEqualTo("not ('a' | 'b')");
    assertThat(CharPredicate.noneOf("a").toString()).isEqualTo("not ('a')");
    assertThat(CharPredicate.noneOf("abc").toString()).isEqualTo("not (anyOf('abc'))");
    assertThat(CharPredicate.noneOf("").toString()).isEqualTo("not (NONE)");
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
  public void precomputeForAscii_isWhitespace() {
    CharPredicate whitespace = Character::isWhitespace;
    CharPredicate predicate = whitespace.precomputeForAscii();
    assertThat(predicate.test('a')).isFalse();
    assertThat(predicate.test('\n')).isTrue();
    assertThat(predicate.test(' ')).isTrue();
    assertThat(predicate.test('\t')).isTrue();
  }

  @Test
  public void precomputeForAscii_idempotent() {
    CharPredicate whitespace = Character::isWhitespace;
    CharPredicate predicate = whitespace.precomputeForAscii();
    assertThat(predicate.precomputeForAscii()).isSameInstanceAs(predicate);
  }
}

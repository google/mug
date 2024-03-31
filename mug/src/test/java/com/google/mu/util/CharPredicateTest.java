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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

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

  @Test public void testNot() {
    assertThat(CharPredicate.is('c').not().test('c')).isFalse();
    assertThat(CharPredicate.is('c').not().test('x')).isTrue();
  }

  @Test public void testNot_toString() {
    assertThat(CharPredicate.is('c').not().toString()).isEqualTo("not ('c')");
  }

  @Test public void testOr() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('c')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('Z')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').test('z')).isFalse();
    assertThat(CharPredicate.is('x').or('X').test('x')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('X')).isTrue();
    assertThat(CharPredicate.is('x').or('X').test('y')).isFalse();
  }

  @Test public void testOr_toString() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').toString()).isEqualTo("'c' | ['A', 'Z']");
    assertThat(CharPredicate.range('A', 'Z').or('c').toString()).isEqualTo("['A', 'Z'] | 'c'");
  }

  @Test public void testAnyOf() {
    assertThat(CharPredicate.anyOf("ab").test('a')).isTrue();
    assertThat(CharPredicate.anyOf("ab").test('b')).isTrue();
    assertThat(CharPredicate.anyOf("ab").test('c')).isFalse();
  }

  @Test public void testAnyOf_toString() {
    assertThat(CharPredicate.anyOf("ab").toString()).isEqualTo("anyOf('ab')");
  }

  @Test public void testNoneOf() {
    assertThat(CharPredicate.noneOf("ab").test('a')).isFalse();
    assertThat(CharPredicate.noneOf("ab").test('b')).isFalse();
    assertThat(CharPredicate.noneOf("ab").test('c')).isTrue();
  }

  @Test public void testNoneOf_toString() {
    assertThat(CharPredicate.noneOf("ab").toString()).isEqualTo("noneOf('ab')");
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

  @Test public void testNulls() throws Throwable {
    CharPredicate p = CharPredicate.is('a');
    new NullPointerTester().testAllPublicInstanceMethods(p);
    new NullPointerTester().testAllPublicStaticMethods(CharPredicate.class);
  }
}

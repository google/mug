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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CharPredicateTest {

  @Test public void testRange() {
    assertThat(CharPredicate.range('a', 'z').matches('a')).isTrue();
    assertThat(CharPredicate.range('a', 'z').matches('z')).isTrue();
    assertThat(CharPredicate.range('a', 'z').matches('v')).isTrue();
    assertThat(CharPredicate.range('a', 'z').matches('0')).isFalse();
    assertThat(CharPredicate.range('a', 'z').matches('C')).isFalse();
    assertThat(CharPredicate.range('a', 'a').matches('a')).isTrue();
    assertThat(CharPredicate.range('a', 'a').matches('A')).isFalse();
  }

  @Test public void testInvalidRange() {
    assertThrows(IllegalArgumentException.class, () -> CharPredicate.range('b', 'a'));
  }

  @Test public void testRange_toString() {
    assertThat(CharPredicate.range('a', 'z').toString()).isEqualTo("['a', 'z']");
  }

  @Test public void testIs() {
    assertThat(CharPredicate.is('c').matches('c')).isTrue();
    assertThat(CharPredicate.is('x').matches('c')).isFalse();
  }

  @Test public void testIs_toString() {
    assertThat(CharPredicate.is('c').toString()).isEqualTo("'c'");
  }

  @Test public void testNot() {
    assertThat(CharPredicate.is('c').not().matches('c')).isFalse();
    assertThat(CharPredicate.is('c').not().matches('x')).isTrue();
  }

  @Test public void testNot_toString() {
    assertThat(CharPredicate.is('c').not().toString()).isEqualTo("not ('c')");
  }

  @Test public void testOr() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').matches('c')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').matches('Z')).isTrue();
    assertThat(CharPredicate.is('c').orRange('A', 'Z').matches('z')).isFalse();
    assertThat(CharPredicate.is('x').or('X').matches('x')).isTrue();
    assertThat(CharPredicate.is('x').or('X').matches('X')).isTrue();
    assertThat(CharPredicate.is('x').or('X').matches('y')).isFalse();
  }

  @Test public void testOr_toString() {
    assertThat(CharPredicate.is('c').orRange('A', 'Z').toString()).isEqualTo("'c' | ['A', 'Z']");
    assertThat(CharPredicate.range('A', 'Z').or('c').toString()).isEqualTo("['A', 'Z'] | 'c'");
  }

  @Test public void testNulls() throws Throwable {
    CharPredicate p = CharPredicate.is('a');
    new NullPointerTester().testAllPublicInstanceMethods(p);
    new NullPointerTester().testAllPublicStaticMethods(CharPredicate.class);
  }
}

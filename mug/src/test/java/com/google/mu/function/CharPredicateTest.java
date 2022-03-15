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
    assertThat(CodePointMatcher.range('a', 'z').test('a')).isTrue();
    assertThat(CodePointMatcher.range('a', 'z').test('z')).isTrue();
    assertThat(CodePointMatcher.range('a', 'z').test('v')).isTrue();
    assertThat(CodePointMatcher.range('a', 'z').test('0')).isFalse();
    assertThat(CodePointMatcher.range('a', 'z').test('C')).isFalse();
    assertThat(CodePointMatcher.range('a', 'a').test('a')).isTrue();
    assertThat(CodePointMatcher.range('a', 'a').test('A')).isFalse();
  }

  @Test public void testInvalidRange() {
    assertThrows(IllegalArgumentException.class, () -> CodePointMatcher.range('b', 'a'));
  }

  @Test public void testRange_toString() {
    assertThat(CodePointMatcher.range('a', 'z').toString()).isEqualTo("['a', 'z']");
  }

  @Test public void testIs() {
    assertThat(CodePointMatcher.is('c').test('c')).isTrue();
    assertThat(CodePointMatcher.is('x').test('c')).isFalse();
  }

  @Test public void testIs_toString() {
    assertThat(CodePointMatcher.is('c').toString()).isEqualTo("'c'");
  }

  @Test public void testNot() {
    assertThat(CodePointMatcher.is('c').negate().test('c')).isFalse();
    assertThat(CodePointMatcher.is('c').negate().test('x')).isTrue();
  }

  @Test public void testNot_toString() {
    assertThat(CodePointMatcher.is('c').negate().toString()).isEqualTo("not ('c')");
  }

  @Test public void testOr() {
    assertThat(CodePointMatcher.is('c').orRange('A', 'Z').test('c')).isTrue();
    assertThat(CodePointMatcher.is('c').orRange('A', 'Z').test('Z')).isTrue();
    assertThat(CodePointMatcher.is('c').orRange('A', 'Z').test('z')).isFalse();
    assertThat(CodePointMatcher.is('x').or('X').test('x')).isTrue();
    assertThat(CodePointMatcher.is('x').or('X').test('X')).isTrue();
    assertThat(CodePointMatcher.is('x').or('X').test('y')).isFalse();
  }

  @Test public void testOr_toString() {
    assertThat(CodePointMatcher.is('c').orRange('A', 'Z').toString()).isEqualTo("'c' | ['A', 'Z']");
    assertThat(CodePointMatcher.range('A', 'Z').or('c').toString()).isEqualTo("['A', 'Z'] | 'c'");
  }

  @Test public void testNulls() throws Throwable {
    CodePointMatcher p = CodePointMatcher.is('a');
    new NullPointerTester().testAllPublicInstanceMethods(p);
    new NullPointerTester().testAllPublicStaticMethods(CodePointMatcher.class);
  }
}

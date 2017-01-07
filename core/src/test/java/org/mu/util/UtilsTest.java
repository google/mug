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
package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Test;
import org.mockito.Mockito;

public class UtilsTest {

  @Test public void testMapList_empty() {
    assertThat(Utils.mapList(asList(), Object::toString)).isEmpty();
  }

  @Test public void testMapList_nonEmpty() {
    assertThat(Utils.mapList(asList(1, 2), Object::toString)).containsExactly("1", "2").inOrder();
  }

  @Test public void testMapList_nulls() {
    assertThrows(NullPointerException.class, () -> Utils.mapList(null, v -> v));
    assertThrows(NullPointerException.class, () -> Utils.mapList(asList(), null));
  }

  @Test public void testTyped_nulls() {
    assertThrows(NullPointerException.class, () -> Utils.typed(null, x -> true));
    assertThrows(NullPointerException.class, () -> Utils.typed(String.class, null));
  }

  @Test public void testTyped_notOfType() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    assertThat(Utils.typed(String.class, condition::test).test(1)).isFalse();
    verify(condition, never()).test(any(String.class));
  }

  @Test public void testTyped_ofType_false() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    when(condition.test("hi")).thenReturn(false);
    assertThat(Utils.typed(String.class, condition::test).test("hi")).isFalse();
    verify(condition).test("hi");
  }

  @Test public void testTyped_ofType_true() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    when(condition.test("hi")).thenReturn(true);
    assertThat(Utils.typed(String.class, condition::test).test("hi")).isTrue();
    verify(condition).test("hi");
  }

  @Test public void testCast_notAnInstance() {
    assertThat(Utils.cast(1, String.class)).isEqualTo(Optional.empty());
  }

  @Test public void testCast_isAnInstance() {
    assertThat(Utils.cast("hi", String.class)).isEqualTo(Optional.of("hi"));
  }

  @Test public void testCast_null() {
    assertThat(Utils.cast(null, String.class)).isEqualTo(Optional.empty());
    assertThrows(NullPointerException.class, () -> Utils.cast("hi", null));
  }

  @Test public void testTyped_doesNotPassCondition() {
    assertThat(Utils.typed(String.class, x -> true).test(1)).isFalse();
  }

  private interface StringCondition {
    boolean test(String s);
  }
}

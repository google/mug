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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Selection.all;
import static com.google.mu.util.Selection.none;
import static com.google.mu.util.Selection.only;
import static com.google.mu.util.Selection.toIntersection;
import static com.google.mu.util.Selection.toSelection;
import static com.google.mu.util.Selection.toUnion;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class SelectionTest {

  @Test
  public void all_limit() {
    assertThat(all().limited()).isEmpty();
  }

  @Test
  public void all_has() {
    assertThat(all().has("foo")).isTrue();
  }

  @Test
  public void all_isEmpty() {
    assertThat(all().isEmpty()).isFalse();
  }

  @Test
  public void none_limit() {
    assertThat(none().limited()).hasValue(ImmutableSet.of());
  }

  @Test
  public void none_has() {
    assertThat(none().has("foo")).isFalse();
  }

  @Test
  public void none_isEmpty() {
    assertThat(none().isEmpty()).isTrue();
  }

  @Test
  public void only_limit() {
    assertThat(only("foo", "bar").limited()).hasValue(ImmutableSet.of("foo", "bar"));
  }

  @Test
  public void only_has() {
    assertThat(only("foo").has("foo")).isTrue();
    assertThat(only("foo").has("bar")).isFalse();
  }

  @Test
  public void only_isEmpty() {
    assertThat(only("foo").isEmpty()).isFalse();
  }

  @Test
  public void only_union() {
    assertThat(only("foo").union(only("bar"))).isEqualTo(only("foo", "bar"));
    assertThat(only("foo").union(only("bar")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(only("foo", "bar").union(only("bar", "dog")).limited().get())
        .containsExactly("foo", "bar", "dog")
        .inOrder();
  }

  @Test
  public void only_intersect() {
    assertThat(only("foo", "bar").intersect(only("dog", "bar", "foo")))
        .isEqualTo(only("foo", "bar"));
    assertThat(only("foo", "bar").intersect(only("bar", "dog", "foo")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(only("foo").intersect(only("bar"))).isEqualTo(none());
  }

  @Test
  public void nullChecks() throws Exception {
    new ClassSanityTester().testNulls(Selection.class);
    new ClassSanityTester().forAllPublicStaticMethods(Selection.class).testNulls();
  }

  @Test
  public void testEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(
            all(),
            all().union(all()),
            all().union(none()),
            none().union(all()),
            all().union(only("foo")),
            only("foo").union(all()),
            all().intersect(all()))
        .addEqualityGroup(
            none(),
            only(),
            none().intersect(none()),
            none().intersect(all()),
            all().intersect(none()),
            none().intersect(only("foo")),
            only("foo").intersect(none()))
        .addEqualityGroup(
            only("foo"),
            only("foo", "foo"),
            all().intersect(only("foo")),
            none().union(only("foo")),
            only("foo").intersect(all()),
            only("foo").union(none()),
            only("foo", "bar").intersect(only("baz", "foo")),
            Stream.of("foo", "foo").collect(toSelection()),
            Stream.of(only("foo", "bar"), only("foo")).collect(toIntersection()),
            Stream.of(only("foo"), Selection.<String>none()).collect(toUnion()))
        .addEqualityGroup(
            only("foo", "bar"), only("foo", "bar", "bar"), only("bar").union(only("foo")))
        .testEquals();
  }
}
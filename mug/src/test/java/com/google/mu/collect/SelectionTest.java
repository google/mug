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
package com.google.mu.collect;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.collect.Selection.all;
import static com.google.mu.collect.Selection.nonEmptyOrAll;
import static com.google.mu.collect.Selection.none;
import static com.google.mu.collect.Selection.only;
import static com.google.mu.collect.Selection.toIntersection;
import static com.google.mu.collect.Selection.toSelection;
import static com.google.mu.collect.Selection.toUnion;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;
import com.google.mu.collect.Selection;
import com.google.mu.util.Substring;

@RunWith(JUnit4.class)
public class SelectionTest {

  @Test public void all_limit() {
    assertThat(all().limited()).isEmpty();
  }

  @Test public void all_has() {
    assertThat(all().has("foo")).isTrue();
    assertThat(all().has(null)).isTrue();
  }

  @Test public void all_isEmpty() {
    assertThat(all().isEmpty()).isFalse();
  }

  @Test public void none_limit() {
    assertThat(none().limited()).hasValue(ImmutableSet.of());
  }

  @Test public void none_has() {
    assertThat(none().has("foo")).isFalse();
    assertThat(none().has(null)).isFalse();
  }

  @Test public void none_isEmpty() {
    assertThat(none().isEmpty()).isTrue();
  }

  @Test public void only_limit() {
    assertThat(only("foo", "bar").limited()).hasValue(ImmutableSet.of("foo", "bar"));
  }

  @Test public void only_has() {
    assertThat(only("foo").has("foo")).isTrue();
    assertThat(only("foo").has("bar")).isFalse();
    assertThat(only("foo").has(null)).isFalse();
  }

  @Test public void only_isEmpty() {
    assertThat(only("foo").isEmpty()).isFalse();
  }

  @Test public void only_union() {
    assertThat(only("foo").union(only("bar"))).isEqualTo(only("foo", "bar"));
    assertThat(only("foo").union(only("bar")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(only("foo", "bar").union(only("bar", "dog")).limited().get())
        .containsExactly("foo", "bar", "dog")
        .inOrder();
  }

  @Test public void only_intersect() {
    assertThat(only("foo", "bar").intersect(only("dog", "bar", "foo")))
        .isEqualTo(only("foo", "bar"));
    assertThat(only("foo", "bar").intersect(only("bar", "dog", "foo")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(only("foo").intersect(only("bar"))).isEqualTo(none());
  }

  @Test public void nonEmptyOrAll_empty() {
    assertThat(nonEmptyOrAll(asList()).limited()).isEmpty();
    assertThat(nonEmptyOrAll(asList()).has("foo")).isTrue();
  }

  @Test public void nonEmptyOrAll_nonEmpty() {
    assertThat(nonEmptyOrAll(asList("foo", "bar")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(nonEmptyOrAll(asList("foo", "bar")).has("foo")).isTrue();
    assertThat(nonEmptyOrAll(asList("foo", "bar")).has("bar")).isTrue();
    assertThat(nonEmptyOrAll(asList("foo", "bar")).has("zoo")).isFalse();
  }

  @Test public void nonEmptyOrAll_withDuplicates() {
    assertThat(nonEmptyOrAll(asList("foo", "bar", "foo")).limited().get())
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(nonEmptyOrAll(asList("foo", "bar", "foo")).has("foo")).isTrue();
    assertThat(nonEmptyOrAll(asList("foo", "bar", "foo")).has("bar")).isTrue();
    assertThat(nonEmptyOrAll(asList("foo", "bar", "foo")).has("zoo")).isFalse();
  }

  @Test public void parser_all() {
    assertThat(Selection.parser().parse("*")).isEqualTo(Selection.all());
  }

  @Test public void parser_empty() {
    assertThat(Selection.parser().parse("")).isEqualTo(Selection.none());
    assertThat(Selection.parser().parse("  ")).isEqualTo(Selection.none());
  }

  @Test public void parser_oneElement() {
    assertThat(Selection.parser().parse("foo")).isEqualTo(Selection.only("foo"));
  }

  @Test public void parser_twoElements() {
    assertThat(Selection.parser().parse("foo,bar")).isEqualTo(Selection.only("foo", "bar"));
  }

  @Test public void parser_twoElementsWithWhitespacesTrimmed() {
    assertThat(Selection.parser().parse("foo , bar")).isEqualTo(Selection.only("foo", "bar"));
  }

  @Test public void parser_twoElementsWithEmptyElements() {
    assertThat(Selection.parser().parse("foo ,")).isEqualTo(Selection.only("foo"));
  }

  @Test public void delimitedByChar_withElementParser() {
    assertThat(Selection.delimitedBy('|').parse("1 |0x2", Integer::decode))
        .isEqualTo(Selection.only(1, 2));
  }

  @Test public void delimitedByWhitespace_withElementParser() {
    assertThat(Selection.delimitedBy(' ').parse("1  0x2 ", Integer::decode))
        .isEqualTo(Selection.only(1, 2));
  }

  @Test public void delimitedByStar() {
    assertThrows(IllegalArgumentException.class, () -> Selection.delimitedBy('*'));
    assertThrows(
        IllegalArgumentException.class,
        () -> Selection.delimitedBy(Substring.first('*').or(Substring.last('/'))));
  }

  @Test public void testEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(
            all(),
            all().union(all()),
            all().union(none()),
            none().union(all()),
            all().union(only("foo")),
            only("foo").union(all()),
            all().intersect(all()),
            nonEmptyOrAll(asList()))
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
            Stream.of(only("foo"), Selection.<String>none()).collect(toUnion()),
            nonEmptyOrAll(asList("foo")))
        .addEqualityGroup(
            only("foo", "bar"),
            only("foo", "bar", "bar"),
            only("bar").union(only("foo")),
            nonEmptyOrAll(asList("bar", "foo")),
            nonEmptyOrAll(asList("foo", "bar", "foo")))
        .testEquals();
  }
}
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
package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.stream.Case.match;
import static com.google.mu.util.stream.MoreCollectors.atLeast;
import static com.google.mu.util.stream.MoreCollectors.empty;
import static com.google.mu.util.stream.MoreCollectors.exactly;
import static com.google.mu.util.stream.MoreCollectors.firstElement;
import static com.google.mu.util.stream.MoreCollectors.lastElement;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CaseTest {
  @Test public void testEmpty() {
    assertThat(match(asList(), empty(() -> "ok"))).isEqualTo("ok");
    assertThrows(IllegalArgumentException.class, () -> match(asList(1), empty(() -> "ok")));
  }

  @Test public void testEmpty_asCollector() {
    assertThat(Stream.empty().collect(empty(() -> "ok"))).isEqualTo("ok");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(empty(() -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1]) doesn't match pattern <empty>.");
  }

  @Test public void testEmpty_orNot() {
    assertThat(Stream.empty().collect(empty(() -> "ok").orNot())).hasValue("ok");
    assertThat(Stream.of(1).collect(empty(() -> "ok").orNot())).isEmpty();
  }

  @Test public void testOnlyElement() {
    assertThat(onlyElement()).isSameAs(onlyElement());
    assertThat(Stream.of("foo").collect(onlyElement())).isEqualTo("foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(onlyElement()));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testOnlyElement_orNot() {
    assertThat(Stream.of("foo").collect(onlyElement().orNot())).hasValue("foo");
    assertThat(Stream.of(1, 2).collect(onlyElement().orNot())).isEmpty();
    assertThat(Stream.empty().collect(onlyElement().orNot())).isEmpty();
  }

  @Test public void testExactlyOneElement() {
    assertThat(match(asList(1), exactly(n -> n * 10)).intValue()).isEqualTo(10);
    assertThrows(IllegalArgumentException.class, () -> match(asList(), exactly(n -> "ok")));
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2), exactly(n -> "ok")));
  }

  @Test public void testExactlyOne_asCollector() {
    assertThat(Stream.of("foo").collect(exactly("ok:"::concat))).isEqualTo("ok:foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(exactly(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testExactlyOne_orNot() {
    assertThat(Stream.of("foo").collect(exactly("ok:"::concat).orNot())).hasValue("ok:foo");
    assertThat(Stream.of(1, 2).collect(exactly(a -> "ok").orNot())).isEmpty();
    assertThat(Stream.empty().collect(exactly(a -> "ok").orNot())).isEmpty();
  }

  @Test public void testExactlyTwoElements() {
    assertThat(match(asList(1, 10), exactly(Integer::sum)).intValue()).isEqualTo(11);
    assertThrows(IllegalArgumentException.class, () -> match(asList(1), exactly(Integer::sum)));
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2, 3), exactly(Integer::sum)));
  }

  @Test public void testExactlyTwo_asCollector() {
    assertThat(Stream.of(1, 10).collect(exactly(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(exactly((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, 3]) doesn't match pattern <exactly 2 elements>.");
  }

  @Test public void testExactlyTwo_orNot() {
    assertThat(Stream.of(1, 10).collect(exactly(Integer::sum).orNot())).hasValue(11);
    assertThat(Stream.of(1, 2, 3).collect(exactly((a, b) -> "ok").orNot())).isEmpty();
    assertThat(Stream.of(1).collect(exactly((a, b) -> "ok").orNot())).isEmpty();
  }

  @Test public void testExactlyThreeElements() {
    assertThat(match(asList(1, 3, 5), exactly((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2), exactly((a, b, c) -> a + b + c)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4), exactly((a, b, c) -> a + b + c)));
  }

  @Test public void testExactlyThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(exactly((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 3 elements>.");
  }

  @Test public void testExactlyThree_orNot() {
    assertThat(Stream.of(1, 3, 5).collect(Case.maybe(exactly((a, b, c) -> a + b + c))))
        .hasValue(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(Case.maybe(exactly((a, b, c) -> a + b + c))))
        .isEmpty();
    assertThat(Stream.of(1, 2).collect(exactly((a, b, c) -> "ok").orNot())).isEmpty();
  }

  @Test public void testExactlyFourElements() {
    assertThat(match(asList(1, 3, 5, 7), exactly((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3), exactly((a, b, c, d) -> a + b + c + d)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4, 5), exactly((a, b, c, d) -> a + b + c)));
  }

  @Test public void testExactlyFour_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(exactly((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 4 elements>.");
  }

  @Test public void testExactlyFour_orNot() {
    assertThat(
            Stream.of(1, 3, 5, 7)
                .collect(Case.maybe(exactly((a, b, c, d) -> a + b + c + d))))
        .hasValue(16);
    assertThat(
        Stream.of(1, 3, 5, 7, 9)
            .collect(Case.maybe(exactly((a, b, c, d) -> a + b + c + d))))
        .isEmpty();
    assertThat(Stream.of(1, 2, 3).collect(exactly((a, b, c, d) -> "ok").orNot())).isEmpty();
  }

  @Test public void testExactlyFiveElements() {
    assertThat(match(asList(1, 3, 5, 7, 9), exactly((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4), exactly((a, b, c, d, e) -> a + b + c + d + e)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4, 5, 6), exactly((a, b, c, d, e) -> a + b + c + e)));
  }

  @Test public void testExactlyFive_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(exactly((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 5 elements>.");
  }

  @Test public void testExactlyFive_orNot() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9)
                .collect(Case.maybe(exactly((a, b, c, d, e) -> a + b + c + d + e))))
        .hasValue(25);
    assertThat(
        Stream.of(1, 3, 5, 7, 9, 11)
            .collect(Case.maybe(exactly((a, b, c, d, e) -> a + b + c + d + e))))
        .isEmpty();
    assertThat(Stream.of(1, 2, 3, 4).collect(exactly((a, b, c, d, e) -> "ok").orNot())).isEmpty();
  }

  @Test public void testExactlySixElements() {
    assertThat(match(asList(1, 3, 5, 7, 9, 11), exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4, 5), exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4, 5, 6, 7), exactly((a, b, c, d, e, f) -> a + b + c + e + f)));
  }

  @Test public void testExactlySix_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 6 elements>.");
  }

  @Test public void testExactlySix_orNot() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9, 11)
                .collect(Case.maybe(exactly((a, b, c, d, e, f) -> a + b + c + d + e + f))))
        .hasValue(36);
    assertThat(
        Stream.of(1, 3, 5, 7, 9, 11, 13)
            .collect(Case.maybe(exactly((a, b, c, d, e, f) -> a + b + c + d + e + f))))
        .isEmpty();
    assertThat(Stream.of(1, 2, 3, 4, 5).collect(exactly((a, b, c, d, e, f) -> "ok").orNot()))
        .isEmpty();
  }

  @Test public void testFirstElement() {
    assertThat(firstElement()).isSameAs(firstElement());
    assertThat(match(asList(1), firstElement()).intValue()).isEqualTo(1);
    assertThat(match(asList(1, 2, 3), firstElement()).intValue()).isEqualTo(1);
    assertThrows(
        IllegalArgumentException.class, () -> match(asList(), firstElement()));
  }

  @Test public void testFirstElement_asCollector() {
    assertThat(Stream.of(1).collect(firstElement()).intValue()).isEqualTo(1);
    assertThat(Stream.of(1, 2, 3).collect(firstElement()).intValue()).isEqualTo(1);
    assertThrows(
        IllegalArgumentException.class, () -> Stream.empty().collect(firstElement()));
  }

  @Test public void testFirstElement_orNot() {
    assertThat(Stream.of(1).collect(firstElement().orNot())).hasValue(1);
    assertThat(Stream.of(1, 2, 3).collect(firstElement().orNot())).hasValue(1);
    assertThat(Stream.empty().collect(firstElement().orNot())).isEmpty();
  }

  @Test public void testLastElement() {
    assertThat(lastElement()).isSameAs(lastElement());
    assertThat(match(asList(1), lastElement()).intValue()).isEqualTo(1);
    assertThat(match(asList(1, 2, 3), lastElement()).intValue()).isEqualTo(3);
    assertThrows(
        IllegalArgumentException.class, () -> match(asList(), lastElement()));
  }

  @Test public void testLastElement_asCollector() {
    assertThat(Stream.of(1).collect(lastElement()).intValue()).isEqualTo(1);
    assertThat(Stream.of(1, 2, 3).collect(lastElement()).intValue()).isEqualTo(3);
    assertThrows(
        IllegalArgumentException.class, () -> Stream.empty().collect(lastElement()));
  }

  @Test public void testLastElement_orNot() {
    assertThat(Stream.of(1).collect(lastElement().orNot())).hasValue(1);
    assertThat(Stream.of(1, 2, 3).collect(lastElement().orNot())).hasValue(3);
    assertThat(Stream.empty().collect(lastElement().orNot())).isEmpty();
  }

  @Test public void testAtLeastOneElement() {
    assertThat(match(asList(1), atLeast(hd -> hd * 10)).intValue()).isEqualTo(10);
    assertThat(match(asList(1, 2, 3), atLeast(hd -> hd * 10)).intValue()).isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class, () -> match(asList(), atLeast(hd -> hd)));
  }

  @Test public void testAtLeastOne_asCollector() {
    assertThat(Stream.of("foo").collect(atLeast("ok:"::concat)))
        .isEqualTo("ok:foo");
    assertThat(Stream.of("foo", "bar").collect(atLeast("ok:"::concat)))
        .isEqualTo("ok:foo");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 1 element>.");
  }

  @Test public void testAtLeastOne_orNot() {
    assertThat(Stream.of("foo").collect(atLeast("ok:"::concat).orNot()))
        .hasValue("ok:foo");
    assertThat(Stream.of("foo", "bar").collect(atLeast("ok:"::concat).orNot()))
        .hasValue("ok:foo");
    assertThat(Stream.empty().collect(atLeast(a -> "ok").orNot())).isEmpty();
  }

  @Test public void testAtLeastTwoElements() {
    assertThat(match(asList(1, 2), atLeast((a, b) -> a + b)).intValue()).isEqualTo(3);
    assertThat(match(asList(1, 3, 5), atLeast((a, b) -> a + b)).intValue()).isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), atLeast((a, b) -> a + b)));
  }

  @Test public void testAtLeastTwo_asCollector() {
    assertThat(Stream.of("foo", "bar").collect(atLeast(String::concat)))
        .isEqualTo("foobar");
    assertThat(Stream.of("foo", "bar", "baz").collect(atLeast(String::concat)))
        .isEqualTo("foobar");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testAtLeastTwo_orNot() {
    assertThat(Stream.of("foo", "bar").collect(atLeast(String::concat).orNot()))
        .hasValue("foobar");
    assertThat(Stream.of("foo", "bar", "baz").collect(atLeast(String::concat).orNot()))
        .hasValue("foobar");
    assertThat(Stream.empty().collect(atLeast((a, b) -> "ok").orNot())).isEmpty();
  }

  @Test public void testAtLeastThreeElements() {
    assertThat(match(asList(1, 3, 5), atLeast((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThat(match(asList(1, 3, 5, 7), atLeast((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2), atLeast((a, b, c) -> a + b)));
  }

  @Test public void testAtLeastThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(atLeast((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(atLeast((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 3 elements>.");
  }

  @Test public void testAtLeastThree_orNot() {
    assertThat(Stream.of(1, 3, 5).collect(Case.maybe(atLeast((a, b, c) -> a + b + c))))
        .hasValue(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(atLeast((Integer a, Integer b, Integer c) -> a + b + c).orNot()))
        .hasValue(9);
    assertThat(Stream.empty().collect(atLeast((a, b, c) -> "ok").orNot())).isEmpty();
    assertThat(Stream.of(1, 2).collect(atLeast((a, b, c) -> "ok").orNot())).isEmpty();
  }

  @Test public void testAtLeastFourElements() {
    assertThat(match(asList(1, 3, 5, 7), atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThat(match(asList(1, 3, 5, 7, 9), atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3), atLeast((a, b, c, d) -> a + b)));
  }

  @Test public void testAtLeastFour_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 4 elements>.");
  }

  @Test public void testAtLeastFour_orNot() {
    assertThat(Stream.of(1, 3, 5, 7).collect(Case.maybe(atLeast((a, b, c, d) -> a + b + c + d))))
        .hasValue(16);
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(Case.maybe(atLeast((a, b, c, d) -> a + b + c + d))))
        .hasValue(16);
    assertThat(Stream.empty().collect(atLeast((a, b, c, d) -> "ok").orNot())).isEmpty();
    assertThat(Stream.of(1, 2, 3).collect(atLeast((a, b, c, d) -> "ok").orNot())).isEmpty();
  }

  @Test public void testAtLeastFiveElements() {
    assertThat(match(asList(1, 3, 5, 7, 9), atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThat(match(asList(1, 3, 5, 7, 9, 11), atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4), atLeast((a, b, c, d, e) -> a + b)));
  }

  @Test public void testAtLeastFive_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 5 elements>.");
  }

  @Test public void testAtLeastFive_orNot() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9)
                .collect(Case.maybe(atLeast((a, b, c, d, e) -> a + b + c + d + e))))
        .hasValue(25);
    assertThat(
        Stream.of(1, 3, 5, 7, 9, 11)
            .collect(Case.maybe(atLeast((a, b, c, d, e) -> a + b + c + d + e))))
        .hasValue(25);
    assertThat(Stream.empty().collect(atLeast((a, b, c, d, e) -> "ok").orNot())).isEmpty();
    assertThat(Stream.of(1, 2, 3, 4).collect(atLeast((a, b, c, d, e) -> "ok").orNot())).isEmpty();
  }

  @Test public void testAtLeastSixElements() {
    assertThat(match(asList(1, 3, 5, 7, 9, 11), atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThat(match(asList(1, 3, 5, 7, 9, 11, 13), atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4, 5), atLeast((a, b, c, d, e, f) -> a + b)));
  }

  @Test public void testAtLeastSix_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11, 13).collect(atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 6 elements>.");
  }

  @Test public void testAtLeastSix_orNot() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9, 11)
                .collect(Case.maybe(atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f))))
        .hasValue(36);
    assertThat(
        Stream.of(1, 3, 5, 7, 9, 11, 13)
            .collect(Case.maybe(atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f))))
        .hasValue(36);
    assertThat(Stream.empty().collect(atLeast((a, b, c, d, e, f) -> "ok").orNot())).isEmpty();
    assertThat(Stream.of(1, 2, 3, 4, 5).collect(atLeast((a, b, c, d, e, f) -> "ok").orNot())).isEmpty();
  }

  @Test public void testOneElementWithCondition() {
    assertThat(match(asList(1), Case.when(n -> n == 1, a -> a * 10)).intValue()).isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(), Case.when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2), Case.when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), Case.when(n -> false, a -> a)));
  }

  @Test public void testOneElementWithCondition_asCollector() {
    assertThat(Stream.of(1).collect(Case.when(n -> n == 1, a -> a * 10)).intValue()).isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(Case.when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(Case.when(n -> false, a -> a)));
  }

  @Test public void testOneElementWithCondition_orNot() {
    assertThat(Stream.of(1).collect(Case.when((Integer n) -> n == 1, a -> a * 10).orNot())).hasValue(10);
    assertThat(Stream.empty().collect(Case.when(n -> true, a -> a).orNot())).isEmpty();
    assertThat(Stream.of(1, 2).collect(Case.when(n -> true, a -> a).orNot())).isEmpty();
    assertThat(Stream.of(1).collect(Case.when(n -> false, a -> a).orNot())).isEmpty();
  }

  @Test public void testTwoElementsWithCondition() {
    assertThat(match(asList(1, 3), Case.when((a, b) -> a < b, (a, b) -> a + b)).intValue()).isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), Case.when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3), Case.when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(3, 1), Case.when((a, b) -> a < b, (a, b) -> a + b)));
  }

  @Test public void testTwoElementsWithCondition_asCollector() {
    assertThat(Stream.of(1, 3).collect(Case.when((a, b) -> a < b, (a, b) -> a + b)).intValue()).isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(Case.when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(Case.when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(3, 1).collect(Case.when((a, b) -> a < b, (a, b) -> a + b)));
  }

  @Test public void testTwoElementsWithCondition_orNot() {
    assertThat(Stream.of(1, 3).collect(Case.when((Integer a, Integer b) -> a < b, (a, b) -> a + b).orNot()))
        .hasValue(4);
    assertThat(Stream.of(1).collect(Case.when((Integer a, Integer b) -> a < b, (a, b) -> a + b).orNot()))
        .isEmpty();
    assertThat(Stream.of(1, 2, 3).collect(Case.when((Integer a, Integer b) -> a < b, (a, b) -> a + b).orNot()))
        .isEmpty();
    assertThat(Stream.of(3, 1).collect(Case.when((Integer a, Integer b) -> a < b, (a, b) -> a + b).orNot()))
        .isEmpty();
  }

  @Test public void testMultipleCases_firstCaseMatches() {
    int result = match(
        asList(1),
        exactly(a -> a * 10),
        atLeast((a) -> a));
    assertThat(result).isEqualTo(10);
  }

  @Test public void testMultipleCases_secondCaseMatches() {
    int result = match(
        asList(1),
        Case.when(a -> a > 2, a -> a * 10),
        atLeast((a) -> -a));
    assertThat(result).isEqualTo(-1);
  }

  @Test
  public void testMultipleCases_fallthrough() {
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), Case.when(a -> a > 2, a -> a * 10), Case.when(a -> a > 2, a -> -a)));
  }

  @Test public void testOrElse() {
    String result = Stream.of(1, 3).collect(Case.orElse(List::toString));
    assertThat(result).isEqualTo("[1, 3]");
  }

  @Test public void testOrElseNotReached() {
    String result = Stream.of(123)
        .collect(
            Case.matching(
                exactly(Object::toString),
                Case.orElse(List::toString)));
    assertThat(result).isEqualTo("123");
  }

  @Test public void testOrElseAsCatchAll() {
    String result = Stream.of(1, 3)
        .collect(
            Case.matching(
                exactly(Object::toString),
                Case.orElse(List::toString)));
    assertThat(result).isEqualTo("[1, 3]");
  }

  @Test public void testOrNot_patternMatches() {
    assertThat(Stream.of(1, 3).collect(exactly(Integer::sum).orNot())).hasValue(4);
  }

  @Test public void testOrNot_patternDoesNotMatch() {
    assertThat(Stream.of(1).collect(exactly(Integer::sum).orNot())).isEmpty();
  }

  @Test public void testOrNot_patternMatchesButMapsToNull() {
    assertThrows(
        NullPointerException.class,
        () -> Stream.of(1, 3).collect(exactly((a, b) -> null).orNot()));
  }


  @Test public void testShortListInErrorMessage() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(atLeast((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testLongListInErrorMessage() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(exactly(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, ...]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(Case.class);
  }
}

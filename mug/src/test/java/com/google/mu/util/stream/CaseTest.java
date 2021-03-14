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
import static com.google.mu.util.stream.MoreCollectors.exactly;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CaseTest {
  @Test public void testEmpty() {
    assertThat(match(asList(), Case.empty(() -> "ok"))).hasValue("ok");
    assertThat(match(asList(1), Case.empty(() -> "ok"))).isEmpty();
  }

  @Test public void testEmpty_asCollector() {
    assertThat(Stream.empty().collect(Case.empty(() -> "ok"))).isEqualTo("ok");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(Case.empty(() -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1]) doesn't match pattern <empty>.");
  }

  @Test public void testOnlyElement() {
    assertThat(onlyElement()).isSameAs(onlyElement());
    assertThat(Stream.of("foo").collect(onlyElement())).isEqualTo("foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(onlyElement()));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testExactlyOneElement() {
    assertThat(match(asList(1), exactly(n -> n * 10))).hasValue(10);
    assertThat(match(asList(), exactly(n -> "ok"))).isEmpty();
    assertThat(match(asList(1, 2), exactly(n -> "ok"))).isEmpty();
  }

  @Test public void testExactlyOne_asCollector() {
    assertThat(Stream.of("foo").collect(exactly("ok:"::concat))).isEqualTo("ok:foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(exactly(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testExactlyTwoElements() {
    assertThat(match(asList(1, 10), exactly(Integer::sum))).hasValue(11);
    assertThat(match(asList(1), exactly(Integer::sum))).isEmpty();
    assertThat(match(asList(1, 2, 3), exactly(Integer::sum))).isEmpty();
  }

  @Test public void testExactlyTwo_asCollector() {
    assertThat(Stream.of(1, 10).collect(exactly(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(exactly((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, 3]) doesn't match pattern <exactly 2 elements>.");
  }

  @Test public void testExactlyThreeElements() {
    assertThat(match(asList(1, 3, 5), exactly((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(match(asList(1, 2), exactly((a, b, c) -> a + b + c))).isEmpty();
    assertThat(match(asList(1, 2, 3, 4), exactly((a, b, c) -> a + b + c))).isEmpty();
  }

  @Test public void testExactlyThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(exactly((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 3 elements>.");
  }

  @Test public void testExactlyFourElements() {
    assertThat(match(asList(1, 3, 5, 7), exactly((a, b, c, d) -> a + b + c + d))).hasValue(16);
    assertThat(match(asList(1, 2, 3), exactly((a, b, c, d) -> a + b + c + d))).isEmpty();
    assertThat(match(asList(1, 2, 3, 4, 5), exactly((a, b, c, d) -> a + b + c))).isEmpty();
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

  @Test public void testExactlyFiveElements() {
    assertThat(match(asList(1, 3, 5, 7, 9), exactly((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(match(asList(1, 2, 3, 4), exactly((a, b, c, d, e) -> a + b + c + d + e)))
        .isEmpty();
    assertThat(match(asList(1, 2, 3, 4, 5, 6), exactly((a, b, c, d, e) -> a + b + c + e)))
        .isEmpty();
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

  @Test public void testExactlySixElements() {
    assertThat(match(asList(1, 3, 5, 7, 9, 11), exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(match(asList(1, 2, 3, 4, 5), exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .isEmpty();
    assertThat(match(asList(1, 2, 3, 4, 5, 6, 7), exactly((a, b, c, d, e, f) -> a + b + c + e + f)))
        .isEmpty();
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

  @Test public void testAtLeastOneElement() {
    assertThat(match(asList(1), Case.atLeast(hd -> hd * 10))).hasValue(10);
    assertThat(match(asList(1, 2, 3), Case.atLeast(hd -> hd * 10))).hasValue(10);
    assertThat(match(asList(), Case.atLeast(hd -> hd))).isEmpty();
  }

  @Test public void testAtLeastOne_asCollector() {
    assertThat(Stream.of("foo").collect(Case.atLeast("ok:"::concat)))
        .isEqualTo("ok:foo");
    assertThat(Stream.of("foo", "bar").collect(Case.atLeast("ok:"::concat)))
        .isEqualTo("ok:foo");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 1 element>.");
  }

  @Test public void testAtLeastTwoElements() {
    assertThat(match(asList(1, 2), Case.atLeast((a, b) -> a + b))).hasValue(3);
    assertThat(match(asList(1, 3, 5), Case.atLeast((a, b) -> a + b))).hasValue(4);
    assertThat(match(asList(1), Case.atLeast((a, b) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastTwo_asCollector() {
    assertThat(Stream.of("foo", "bar").collect(Case.atLeast(String::concat)))
        .isEqualTo("foobar");
    assertThat(Stream.of("foo", "bar", "baz").collect(Case.atLeast(String::concat)))
        .isEqualTo("foobar");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testAtLeastThreeElements() {
    assertThat(match(asList(1, 3, 5), Case.atLeast((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(match(asList(1, 3, 5, 7), Case.atLeast((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(match(asList(1, 2), Case.atLeast((a, b, c) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(Case.atLeast((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(Case.atLeast((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 3 elements>.");
  }

  @Test public void testAtLeastFourElements() {
    assertThat(match(asList(1, 3, 5, 7), Case.atLeast((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(match(asList(1, 3, 5, 7, 9), Case.atLeast((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(match(asList(1, 2, 3), Case.atLeast((a, b, c, d) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastFour_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(Case.atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(Case.atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 4 elements>.");
  }

  @Test public void testAtLeastFiveElements() {
    assertThat(match(asList(1, 3, 5, 7, 9), Case.atLeast((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(match(asList(1, 3, 5, 7, 9, 11), Case.atLeast((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(match(asList(1, 2, 3, 4), Case.atLeast((a, b, c, d, e) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastFive_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(Case.atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(Case.atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 5 elements>.");
  }

  @Test public void testAtLeastSixElements() {
    assertThat(match(asList(1, 3, 5, 7, 9, 11), Case.atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(match(asList(1, 3, 5, 7, 9, 11, 13), Case.atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(match(asList(1, 2, 3, 4, 5), Case.atLeast((a, b, c, d, e, f) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastSix_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(Case.atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11, 13).collect(Case.atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(Case.atLeast((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 6 elements>.");
  }

  @Test public void testOneElementWithCondition() {
    assertThat(match(asList(1), Case.when(n -> n == 1, a -> a * 10))).hasValue(10);
    assertThat(match(asList(), Case.when(n -> true, a -> a))).isEmpty();
    assertThat(match(asList(1, 2), Case.when(n -> true, a -> a))).isEmpty();
    assertThat(match(asList(1), Case.when(n -> false, a -> a))).isEmpty();
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

  @Test public void testTwoElementsWithCondition() {
    assertThat(match(asList(1, 3), Case.when((a, b) -> a < b, (a, b) -> a + b))).hasValue(4);
    assertThat(match(asList(1), Case.when((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
    assertThat(match(asList(1, 2, 3), Case.when((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
    assertThat(match(asList(3, 1), Case.when((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
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

  @Test public void testMultipleCases_firstCaseMatches() {
    assertThat(
        match(
            asList(1),
            exactly(a -> a * 10),
            Case.atLeast(a -> a)))
        .hasValue(10);
  }

  @Test public void testMultipleCases_secondCaseMatches() {
    assertThat(
        match(
            asList(1),
            Case.when(a -> a > 2, a -> a * 10),
            Case.atLeast((a) -> -a)))
        .hasValue(-1);
  }

  @Test
  public void testMultipleCases_fallthrough() {
    assertThat(match(asList(1), Case.when(a -> a > 2, a -> a * 10), Case.when(a -> a > 2, a -> -a)))
        .isEmpty();
  }


  @Test public void testShortListInErrorMessage() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(Case.atLeast((a, b) -> "ok")));
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

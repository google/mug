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
import static com.google.mu.util.stream.Case.findFrom;
import static com.google.mu.util.stream.Case.firstElement;
import static com.google.mu.util.stream.Case.firstElementIf;
import static com.google.mu.util.stream.Case.firstElements;
import static com.google.mu.util.stream.Case.firstElementsIf;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static com.google.mu.util.stream.MoreCollectors.onlyElementIf;
import static com.google.mu.util.stream.MoreCollectors.onlyElements;
import static com.google.mu.util.stream.MoreCollectors.onlyElementsIf;
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
    assertThat(findFrom(asList(), Case.empty(() -> "ok"))).hasValue("ok");
    assertThat(findFrom(asList(1), Case.empty(() -> "ok"))).isEmpty();
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
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 1 element>.");
  }

  @Test public void testExactlyOneElement() {
    assertThat(findFrom(asList(1), onlyElement(n -> n * 10))).hasValue(10);
    assertThat(findFrom(asList(), onlyElement(n -> "ok"))).isEmpty();
    assertThat(findFrom(asList(1, 2), onlyElement(n -> "ok"))).isEmpty();
  }

  @Test public void testExactlyOne_asCollector() {
    assertThat(Stream.of("foo").collect(onlyElement("ok:"::concat))).isEqualTo("ok:foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(onlyElement(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 1 element>.");
  }

  @Test public void testExactlyTwoElements() {
    assertThat(findFrom(asList(1, 10), onlyElements(Integer::sum))).hasValue(11);
    assertThat(findFrom(asList(1), onlyElements(Integer::sum))).isEmpty();
    assertThat(findFrom(asList(1, 2, 3), onlyElements(Integer::sum))).isEmpty();
  }

  @Test public void testExactlyTwo_asCollector() {
    assertThat(Stream.of(1, 10).collect(onlyElements(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(onlyElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, 3]) doesn't match pattern <only 2 elements>.");
  }

  @Test public void testExactlyThreeElements() {
    assertThat(findFrom(asList(1, 3, 5), onlyElements((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(findFrom(asList(1, 2), onlyElements((a, b, c) -> a + b + c))).isEmpty();
    assertThat(findFrom(asList(1, 2, 3, 4), onlyElements((a, b, c) -> a + b + c))).isEmpty();
  }

  @Test public void testExactlyThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(onlyElements((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 3 elements>.");
  }

  @Test public void testExactlyFourElements() {
    assertThat(findFrom(asList(1, 3, 5, 7), onlyElements((a, b, c, d) -> a + b + c + d))).hasValue(16);
    assertThat(findFrom(asList(1, 2, 3), onlyElements((a, b, c, d) -> a + b + c + d))).isEmpty();
    assertThat(findFrom(asList(1, 2, 3, 4, 5), onlyElements((a, b, c, d) -> a + b + c))).isEmpty();
  }

  @Test public void testExactlyFour_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(onlyElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 4 elements>.");
  }

  @Test public void testExactlyFiveElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9), onlyElements((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(findFrom(asList(1, 2, 3, 4), onlyElements((a, b, c, d, e) -> a + b + c + d + e)))
        .isEmpty();
    assertThat(findFrom(asList(1, 2, 3, 4, 5, 6), onlyElements((a, b, c, d, e) -> a + b + c + e)))
        .isEmpty();
  }

  @Test public void testExactlyFive_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(onlyElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 5 elements>.");
  }

  @Test public void testExactlySixElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11), onlyElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(findFrom(asList(1, 2, 3, 4, 5), onlyElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .isEmpty();
    assertThat(findFrom(asList(1, 2, 3, 4, 5, 6, 7), onlyElements((a, b, c, d, e, f) -> a + b + c + e + f)))
        .isEmpty();
  }

  @Test public void testExactlySix_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(onlyElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <only 6 elements>.");
  }

  @Test public void testAtLeastOneElement() {
    assertThat(findFrom(asList(1), firstElement(hd -> hd * 10))).hasValue(10);
    assertThat(findFrom(asList(1, 2, 3), firstElement(hd -> hd * 10))).hasValue(10);
    assertThat(findFrom(asList(), firstElement(hd -> hd))).isEmpty();
  }

  @Test public void testAtLeastOne_asCollector() {
    assertThat(Stream.of("foo").collect(firstElement("ok:"::concat)))
        .isEqualTo("ok:foo");
    assertThat(Stream.of("foo", "bar").collect(firstElement("ok:"::concat)))
        .isEqualTo("ok:foo");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElement(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 1 element>.");
  }

  @Test public void testAtLeastTwoElements() {
    assertThat(findFrom(asList(1, 2), firstElements((a, b) -> a + b))).hasValue(3);
    assertThat(findFrom(asList(1, 3, 5), firstElements((a, b) -> a + b))).hasValue(4);
    assertThat(findFrom(asList(1), firstElements((a, b) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastTwo_asCollector() {
    assertThat(Stream.of("foo", "bar").collect(firstElements(String::concat)))
        .isEqualTo("foobar");
    assertThat(Stream.of("foo", "bar", "baz").collect(firstElements(String::concat)))
        .isEqualTo("foobar");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testAtLeastThreeElements() {
    assertThat(findFrom(asList(1, 3, 5), firstElements((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(findFrom(asList(1, 3, 5, 7), firstElements((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(findFrom(asList(1, 2), firstElements((a, b, c) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastThree_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(firstElements((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(firstElements((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 3 elements>.");
  }

  @Test public void testAtLeastFourElements() {
    assertThat(findFrom(asList(1, 3, 5, 7), firstElements((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(findFrom(asList(1, 3, 5, 7, 9), firstElements((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(findFrom(asList(1, 2, 3), firstElements((a, b, c, d) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastFour_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(firstElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(firstElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 4 elements>.");
  }

  @Test public void testAtLeastFiveElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9), firstElements((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11), firstElements((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(findFrom(asList(1, 2, 3, 4), firstElements((a, b, c, d, e) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastFive_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(firstElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(firstElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 5 elements>.");
  }

  @Test public void testAtLeastSixElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11), firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11, 13), firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(findFrom(asList(1, 2, 3, 4, 5), firstElements((a, b, c, d, e, f) -> a + b))).isEmpty();
  }

  @Test public void testAtLeastSix_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11, 13).collect(firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 6 elements>.");
  }

  @Test public void testFirstElement() {
    assertThat(findFrom(asList(1), firstElement(hd -> hd * 10))).hasValue(10);
    assertThat(findFrom(asList(1, 2, 3), firstElement(hd -> hd * 10))).hasValue(10);
    assertThat(findFrom(asList(), firstElement(hd -> hd))).isEmpty();
  }

  @Test public void testFirstElement_asCollector() {
    assertThat(Stream.of("foo").collect(firstElement("ok:"::concat)))
        .isEqualTo("ok:foo");
    assertThat(Stream.of("foo", "bar").collect(firstElement("ok:"::concat)))
        .isEqualTo("ok:foo");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElement(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 1 element>.");
  }

  @Test public void testFirstTwoElements() {
    assertThat(findFrom(asList(1, 2), firstElements((a, b) -> a + b))).hasValue(3);
    assertThat(findFrom(asList(1, 3, 5), firstElements((a, b) -> a + b))).hasValue(4);
    assertThat(findFrom(asList(1), firstElements((a, b) -> a + b))).isEmpty();
  }

  @Test public void testFirstTwoElements_asCollector() {
    assertThat(Stream.of("foo", "bar").collect(firstElements(String::concat)))
        .isEqualTo("foobar");
    assertThat(Stream.of("foo", "bar", "baz").collect(firstElements(String::concat)))
        .isEqualTo("foobar");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testFirstThreeElements() {
    assertThat(findFrom(asList(1, 3, 5), firstElements((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(findFrom(asList(1, 3, 5, 7), firstElements((a, b, c) -> a + b + c))).hasValue(9);
    assertThat(findFrom(asList(1, 2), firstElements((a, b, c) -> a + b))).isEmpty();
  }

  @Test public void testFirstThreeElements_asCollector() {
    assertThat(Stream.of(1, 3, 5).collect(firstElements((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    assertThat(Stream.of(1, 3, 5, 7).collect(firstElements((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 3 elements>.");
  }

  @Test public void testFirstFourElements() {
    assertThat(findFrom(asList(1, 3, 5, 7), firstElements((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(findFrom(asList(1, 3, 5, 7, 9), firstElements((a, b, c, d) -> a + b + c + d)))
        .hasValue(16);
    assertThat(findFrom(asList(1, 2, 3), firstElements((a, b, c, d) -> a + b))).isEmpty();
  }

  @Test public void testFirstFourElements_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(firstElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(firstElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 4 elements>.");
  }

  @Test public void testFirstFiveElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9), firstElements((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11), firstElements((a, b, c, d, e) -> a + b + c + d + e)))
        .hasValue(25);
    assertThat(findFrom(asList(1, 2, 3, 4), firstElements((a, b, c, d, e) -> a + b))).isEmpty();
  }

  @Test public void testFirstFiveElements_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(firstElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(firstElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 5 elements>.");
  }

  @Test public void testFirstSixElements() {
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11), firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(findFrom(asList(1, 3, 5, 7, 9, 11, 13), firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)))
        .hasValue(36);
    assertThat(findFrom(asList(1, 2, 3, 4, 5), firstElements((a, b, c, d, e, f) -> a + b))).isEmpty();
  }

  @Test public void testFirstSixElements_asCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    assertThat(Stream.of(1, 3, 5, 7, 9, 11, 13).collect(firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElements((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 6 elements>.");
  }

  @Test public void testOnlyElementIf() {
    assertThat(findFrom(asList(1), onlyElementIf(n -> n == 1, a -> a * 10))).hasValue(10);
    assertThat(findFrom(asList(), onlyElementIf(n -> true))).isEmpty();
    assertThat(findFrom(asList(1, 2), onlyElementIf(n -> true))).isEmpty();
    assertThat(findFrom(asList(1), onlyElementIf(n -> false))).isEmpty();
  }

  @Test public void testOnlyElementIf_asCollector() {
    assertThat(Stream.of(1).collect(onlyElementIf(n -> n == 1, a -> a * 10)).intValue())
        .isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(onlyElementIf(n -> true)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElementIf(n -> true)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(onlyElementIf(n -> false)));
  }

  @Test public void testOnlyElementsIf() {
    assertThat(findFrom(asList(1, 3), onlyElementsIf((a, b) -> a < b, (a, b) -> a + b))).hasValue(4);
    assertThat(findFrom(asList(1), onlyElementsIf((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
    assertThat(findFrom(asList(1, 2, 3), onlyElementsIf((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
    assertThat(findFrom(asList(3, 1), onlyElementsIf((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
  }

  @Test public void testOnlyElementsIf_asCollector() {
    assertThat(Stream.of(1, 3).collect(onlyElementsIf((a, b) -> a < b, (a, b) -> a + b)).intValue())
        .isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(onlyElementsIf((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(onlyElementsIf((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(3, 1).collect(onlyElementsIf((a, b) -> a < b, (a, b) -> a + b)));
  }

  @Test public void testFirstElementIf() {
    assertThat(findFrom(asList(1, 2), firstElementIf(n -> n == 1, a -> a * 10))).hasValue(10);
    assertThat(findFrom(asList(1), firstElementIf(n -> true))).hasValue(1);
    assertThat(findFrom(asList(), firstElementIf(n -> true))).isEmpty();
    assertThat(findFrom(asList(1), firstElementIf(n -> false))).isEmpty();
  }

  @Test public void testFirstElementIf_asCollector() {
    assertThat(Stream.of(1, 2).collect(firstElementIf(n -> n == 1, a -> a * 10)).intValue())
        .isEqualTo(10);
    assertThat(Stream.of(1).collect(firstElementIf(n -> n == 1)).intValue())
        .isEqualTo(1);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(firstElementIf(n -> true)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(onlyElementIf(n -> false)));
  }

  @Test public void testFirstElementsIf() {
    assertThat(findFrom(asList(1, 3), firstElementsIf((a, b) -> a < b, (a, b) -> a + b))).hasValue(4);
    assertThat(findFrom(asList(1, 3, 5), firstElementsIf((a, b) -> a < b, (a, b) -> a + b))).hasValue(4);
    assertThat(findFrom(asList(3, 1), firstElementsIf((a, b) -> a < b, (a, b) -> a + b))).isEmpty();
  }

  @Test public void testFirstElementsIf_asCollector() {
    assertThat(Stream.of(1, 3).collect(firstElementsIf((a, b) -> a < b, (a, b) -> a + b)).intValue())
        .isEqualTo(4);
    assertThat(Stream.of(1, 3, 5).collect(firstElementsIf((a, b) -> a < b, (a, b) -> a + b)).intValue())
        .isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1).collect(onlyElementsIf((a, b) -> true, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(3, 1).collect(onlyElementsIf((a, b) -> a < b, (a, b) -> a + b)));
  }

  @Test public void testMultipleCases_firstCaseMatches() {
    assertThat(
        findFrom(
            asList(1),
            onlyElement(a -> a * 10),
            firstElement(a -> a)))
        .hasValue(10);
  }

  @Test public void testMultipleCases_secondCaseMatches() {
    assertThat(
        findFrom(
            asList(1),
            onlyElementIf(a -> a > 2, a -> a * 10),
            firstElement((a) -> -a)))
        .hasValue(-1);
  }

  @Test
  public void testMultipleCases_fallthrough() {
    assertThat(findFrom(asList(1), onlyElementIf(a -> a > 2, a -> a * 10), onlyElementIf(a -> a > 2, a -> -a)))
        .isEmpty();
  }

  @Test public void testShortListInErrorMessage() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(firstElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testLongListInErrorMessage() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(onlyElement(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, ...]) doesn't match pattern <only 1 element>.");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(Case.class);
  }
}

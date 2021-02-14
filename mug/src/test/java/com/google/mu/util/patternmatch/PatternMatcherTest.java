package com.google.mu.util.patternmatch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.patternmatch.PatternMatcher.atLeast;
import static com.google.mu.util.patternmatch.PatternMatcher.empty;
import static com.google.mu.util.patternmatch.PatternMatcher.exactly;
import static com.google.mu.util.patternmatch.PatternMatcher.firstElement;
import static com.google.mu.util.patternmatch.PatternMatcher.match;
import static com.google.mu.util.patternmatch.PatternMatcher.onlyElement;
import static com.google.mu.util.patternmatch.PatternMatcher.when;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class PatternMatcherTest {
  @Test public void testEmpty() {
    assertThat(match(asList(), empty(() -> "ok"))).isEqualTo("ok");
    assertThrows(IllegalArgumentException.class, () -> match(asList(1), empty(() -> "ok")));
  }

  @Test public void testEmptyAsCollector() {
    assertThat(Stream.empty().collect(empty(() -> "ok"))).isEqualTo("ok");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1).collect(empty(() -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1]) doesn't match pattern <empty>.");
  }

  @Test public void testExactlyOneElement() {
    assertThat(match(asList(1), exactly(n -> n * 10)).intValue()).isEqualTo(10);
    assertThrows(IllegalArgumentException.class, () -> match(asList(), exactly(n -> "ok")));
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2), exactly(n -> "ok")));
  }

  @Test public void testExactlyOneAsCollector() {
    assertThat(Stream.of("foo").collect(exactly("ok:"::concat))).isEqualTo("ok:foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(exactly(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testOnlyElement() {
    assertThat(Stream.of("foo").collect(onlyElement())).isEqualTo("foo");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Stream.of(1, 2).collect(onlyElement()));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testExactlyTwoElements() {
    assertThat(match(asList(1, 10), exactly(Integer::sum)).intValue()).isEqualTo(11);
    assertThrows(IllegalArgumentException.class, () -> match(asList(1), exactly(Integer::sum)));
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2, 3), exactly(Integer::sum)));
  }

  @Test public void testExactlyTwoAsCollector() {
    assertThat(Stream.of(1, 10).collect(exactly(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(exactly((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2, 3]) doesn't match pattern <exactly 2 elements>.");
  }

  @Test public void testExactlyThreeElements() {
    assertThat(match(asList(1, 3, 5), exactly((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThrows(IllegalArgumentException.class, () -> match(asList(1, 2), exactly((a, b, c) -> a + b + c)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3, 4), exactly((a, b, c) -> a + b + c)));
  }

  @Test public void testExactlyThreeAsCollector() {
    assertThat(Stream.of(1, 3, 5).collect(exactly((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 3 elements>.");
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

  @Test public void testExactlyFourAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(exactly((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 4 elements>.");
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

  @Test public void testExactlyFiveAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(exactly((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 5 elements>.");
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

  @Test public void testExactlySixAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(exactly((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(exactly((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([1, 2]) doesn't match pattern <exactly 6 elements>.");
  }

  @Test public void testAtLeastOneElement() {
    assertThat(match(asList(1), atLeast(hd -> hd * 10)).intValue()).isEqualTo(10);
    assertThat(match(asList(1, 2, 3), atLeast(hd -> hd * 10)).intValue()).isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class, () -> match(asList(), atLeast(hd -> hd)));
  }

  @Test public void testFirstElement() {
    assertThat(match(asList(1), firstElement()).intValue()).isEqualTo(1);
    assertThat(match(asList(1, 2, 3), firstElement()).intValue()).isEqualTo(1);
    assertThrows(
        IllegalArgumentException.class, () -> match(asList(), atLeast(hd -> hd)));
  }

  @Test public void testAtLeastOneAsCollector() {
    assertThat(Stream.of("foo", "bar").collect(atLeast("ok:"::concat)))
        .isEqualTo("ok:foo");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast(a -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 1 element>.");
  }

  @Test public void testAtLeastTwoElements() {
    assertThat(match(asList(1, 2), atLeast((a, b) -> a + b)).intValue()).isEqualTo(3);
    assertThat(match(asList(1, 3, 5), atLeast((a, b) -> a + b)).intValue()).isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), atLeast((a, b) -> a + b)));
  }

  @Test public void testAtLeastTwoAsCollector() {
    assertThat(Stream.of("foo", "bar").collect(atLeast(String::concat)))
        .isEqualTo("foobar");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 2 elements>.");
  }

  @Test public void testAtLeastThreeElements() {
    assertThat(match(asList(1, 3, 5), atLeast((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThat(match(asList(1, 3, 5, 7), atLeast((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2), atLeast((a, b, c) -> a + b)));
  }

  @Test public void testAtLeastThreeAsCollector() {
    assertThat(Stream.of(1, 3, 5).collect(atLeast((a, b, c) -> a + b + c)).intValue())
        .isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 3 elements>.");
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

  @Test public void testAtLeastFourAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7).collect(atLeast((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 4 elements>.");
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

  @Test public void testAtLeastFiveAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(atLeast((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 5 elements>.");
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

  @Test public void testAtLeastSixAsCollector() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(atLeast((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.empty().collect(atLeast((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Input ([]) doesn't match pattern <at least 6 elements>.");
  }

  @Test public void testOneElementWithCondition() {
    assertThat(match(asList(1), when(n -> n == 1, a -> a * 10)).intValue()).isEqualTo(10);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(), when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2), when(n -> true, a -> a)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), when(n -> false, a -> a)));
  }

  @Test public void testTwoElementsWithCondition() {
    assertThat(match(asList(1, 3), when((a, b) -> a < b, (a, b) -> a + b)).intValue()).isEqualTo(4);
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1, 2, 3), when((a, b) -> a < b, (a, b) -> a + b)));
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(3, 1), when((a, b) -> a < b, (a, b) -> a + b)));
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
        when(a -> a > 2, a -> a * 10),
        atLeast((a) -> -a));
    assertThat(result).isEqualTo(-1);
  }

  @Test
  public void testMultipleCases_fallthrough() {
    assertThrows(
        IllegalArgumentException.class,
        () -> match(asList(1), when(a -> a > 2, a -> a * 10), when(a -> a > 2, a -> -a)));
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
        .isEqualTo("Input of size = 10 ([1, 2, 3, 4, 5, 6, 7, 8, ...]) doesn't match pattern <exactly 1 element>.");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(PatternMatcher.class);
  }
}

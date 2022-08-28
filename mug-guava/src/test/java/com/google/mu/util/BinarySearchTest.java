package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.BinarySearch.inSortedArray;
import static com.google.mu.util.BinarySearch.inSortedArrayWithTolerance;
import static com.google.mu.util.BinarySearch.inSortedList;
import static com.google.mu.util.BinarySearch.inSortedListWithTolerance;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class BinarySearchTest {

  @Test
  public void inRangeInclusive_invalidIndex() {
    assertThrows(IllegalArgumentException.class, () -> BinarySearch.inRangeInclusive(2, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE - 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Integer.MIN_VALUE + 2, Integer.MIN_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MIN_VALUE));
  }

  @Test
  public void inLongRangeInclusive_invalidIndex() {
    assertThrows(IllegalArgumentException.class, () -> BinarySearch.inRangeInclusive(2L, 0L));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE - 2));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Long.MIN_VALUE + 2, Long.MIN_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MIN_VALUE));
  }

  @Test
  public void inRangeInclusive_empty() {
    assertThat(BinarySearch.inRangeInclusive(0, -1).find((l, i, h) -> 0)).isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, -1).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.inRangeInclusive(0, -1).insertionPointFor((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, -1).insertionPointBefore((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, -1).insertionPointAfter((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE - 1).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE - 1).find((l, i, h) -> 0))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE + 1, Integer.MIN_VALUE).find((l, i, h) -> 0))
        .isEmpty();
  }

  @Test
  public void inLongRangeInclusive_empty() {
    assertThat(BinarySearch.inRangeInclusive(0L, -1L).find((l, i, h) -> 0)).isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0L, -1L).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.inRangeInclusive(0L, -1L).insertionPointFor((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0L, -1L).insertionPointBefore((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0L, -1L).insertionPointAfter((l, i, h) -> 0))
    .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE - 1).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE - 1).find((l, i, h) -> 0))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE + 1, Long.MIN_VALUE).find((l, i, h) -> 0))
        .isEmpty();
  }


  @Test
  public void inRangeInclusive_singleCandidateRange_found() {
    assertThat(BinarySearch.inRangeInclusive(1, 1).find((l, i, h) -> Integer.compare(i, 1)))
        .hasValue(1);
    assertThat(BinarySearch.inRangeInclusive(1, 1).rangeOf((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(Range.closed(1, 1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointFor((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.at(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointBefore((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointAfter((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.after(1));
  }


  @Test
  public void inLongRangeInclusive_singleCandidateRange_found() {
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).find((l, i, h) -> Long.compare(i, 1)))
        .hasValue(1L);
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).rangeOf((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(Range.closed(1L, 1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointFor((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.at(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointBefore((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointAfter((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.after(1L));
  }

  @Test
  public void inRangeInclusive_singleCandidateRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(1, 1).find((l, i, h) -> Integer.compare(0, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(1, 1).rangeOf((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(Range.closedOpen(1, 1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointFor((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointBefore((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointAfter((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void inRangeInclusive_singleCandidateRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(1, 1).find((l, i, h) -> Integer.compare(10, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(1, 1).rangeOf((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(Range.closedOpen(2, 2));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointFor((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointBefore((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
    assertThat(BinarySearch.inRangeInclusive(1, 1).insertionPointAfter((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void inLongRangeInclusive_singleCandidateRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).find((l, i, h) -> Long.compare(0, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).rangeOf((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(Range.closedOpen(1L, 1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointFor((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointBefore((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointAfter((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
  }

  @Test
  public void inLongRangeInclusive_singleCandidateRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).find((l, i, h) -> Long.compare(3, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).rangeOf((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(Range.closedOpen(2L, 2L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointFor((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointBefore((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
    assertThat(BinarySearch.inRangeInclusive(1L, 1L).insertionPointAfter((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
  }

  @Test
  public void inRangeInclusive_preventsUderflow_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE, Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }

  @Test
  public void inRangeInclusive_preventsUnderflow_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MIN_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
  }

  @Test
  public void inLongRangeInclusive_preventsUderflow_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }

  @Test
  public void inLongRangeInclusive_preventsUnderflow_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE + 1, Long.MIN_VALUE + 1));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MIN_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
  }

  @Test
  public void inRangeInclusive_preventsOverflow_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
  }

  @Test
  public void inRangeInclusive_preventsOverflow_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MAX_VALUE, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }

  @Test
  public void inLongRangeInclusive_preventsOverflow_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
  }

  @Test
  public void inLongRangeInclusive_preventsOverflow_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MAX_VALUE, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void inRangeInclusive_maxRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE, Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }


  @Test
  public void inRangeInclusive_maxRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }

  @Test
  public void inLongRangeInclusive_maxRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }


  @Test
  public void inLongRangeInclusive_maxRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void inRangeInclusive_maxPositiveRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
  }


  @Test
  public void inRangeInclusive_maxPositiveRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }

  @Test
  public void inLongRangeInclusive_maxPositiveRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0L));
  }


  @Test
  public void inLongRangeInclusive_maxPositiveRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.inRangeInclusive(0L, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void inRangeInclusive_maxNegativeRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE, Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }


  @Test
  public void inRangeInclusive_maxNegativeRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
  }

  @Test
  public void inLongRangeInclusive_maxNegativeRange_shouldBeBefore() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }


  @Test
  public void inLongRangeInclusive_maxNegativeRange_shouldBeAfter() {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
  }

  @Test
  public void inRangeInclusive_maxRange_found(
      @TestParameter(valuesProvider = IntValues.class) int target) {
    assertThat(
            BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(
            BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(
            BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(
            BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(
            BinarySearch.inRangeInclusive(Integer.MIN_VALUE, Integer.MAX_VALUE)
                .insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inRangeInclusive_maxNegativeRange_found(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.inRangeInclusive(Integer.MIN_VALUE, -1).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inLongRangeInclusive_maxRange_found(
      @TestParameter(valuesProvider = LongValues.class) long target) {
    assertThat(
            BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE)
                .find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(
            BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE)
                .rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(
            BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE)
                .insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(
            BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE)
                .insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(
            BinarySearch.inRangeInclusive(Long.MIN_VALUE, Long.MAX_VALUE)
                .insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inLongRangeInclusive_maxNegativeRange_found(
      @TestParameter(valuesProvider = NegativeLongValues.class) long target) {
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.inRangeInclusive(Long.MIN_VALUE, -1L).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inRangeInclusive_maxNonNegativeRange_found(
      @TestParameter(valuesProvider = NonNegativeValues.class) int target) {
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inLongRangeInclusive_maxNonNegativeRange_found(
      @TestParameter(valuesProvider = NonNegativeLongValues.class) long target) {
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void inRangeInclusive_maxNonNegativeRange_negativeNotFound(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).find((l, i, h) -> Integer.compare(target, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void inLongRangeInclusive_maxNonNegativeRange_negativeNotFound(
      @TestParameter(valuesProvider = NegativeLongValues.class) long target) {
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).find((l, i, h) -> Long.compare(target, i)))
        .isEmpty();
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.inRangeInclusive(0, Long.MAX_VALUE).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0L));
  }

  @Test
  public void binarySearch_inSortedIntArray_found() {
    int[] sorted = new int[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(20)).hasValue(1);
    assertThat(inSortedArray(sorted).rangeOf(20)).isEqualTo(Range.closed(1, 1));
    assertThat(inSortedArray(sorted).insertionPointFor(20)).isEqualTo(InsertionPoint.at(1));
    assertThat(inSortedArray(sorted).insertionPointBefore(20)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(20)).isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void binarySearch_inSortedIntArray_notFoundInTheMiddle() {
    int[] sorted = new int[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(19)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(19)).isEqualTo(Range.closedOpen(1, 1));
    assertThat(inSortedArray(sorted).insertionPointFor(19)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointBefore(19)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(19)).isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void binarySearch_inSortedIntArray_notFoundAtTheBeginning() {
    int[] sorted = new int[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(-1)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(-1)).isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedArray(sorted).insertionPointFor(Integer.MIN_VALUE)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArray(sorted).insertionPointBefore(-1)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArray(sorted).insertionPointAfter(0)).isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void binarySearch_inSortedIntArray_notFoundAtTheEnd() {
    int[] sorted = new int[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(41)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(Integer.MAX_VALUE)).isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedArray(sorted).insertionPointFor(50)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArray(sorted).insertionPointBefore(Integer.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArray(sorted).insertionPointAfter(Integer.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedLongArray_found() {
    long[] sorted = new long[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(20L)).hasValue(1);
    assertThat(inSortedArray(sorted).rangeOf(20L)).isEqualTo(Range.closed(1, 1));
    assertThat(inSortedArray(sorted).insertionPointFor(20L)).isEqualTo(InsertionPoint.at(1));
    assertThat(inSortedArray(sorted).insertionPointBefore(20L)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(20L)).isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void binarySearch_inSortedLongArray_notFoundInTheMiddle() {
    long[] sorted = new long[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(19L)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(19L)).isEqualTo(Range.closedOpen(1, 1));
    assertThat(inSortedArray(sorted).insertionPointFor(19L)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointBefore(19L)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(19L)).isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void binarySearch_inSortedLongArray_notFoundAtTheBeginning() {
    long[] sorted = new long[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(-1L)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(-1L)).isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedArray(sorted).insertionPointFor(Long.MIN_VALUE)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArray(sorted).insertionPointBefore(-1L)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArray(sorted).insertionPointAfter(0L)).isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void binarySearch_inSortedLongArray_notFoundAtTheEnd() {
    long[] sorted = new long[] {10, 20, 30, 40};
    assertThat(inSortedArray(sorted).find(41L)).isEmpty();
    assertThat(inSortedArray(sorted).rangeOf(Long.MAX_VALUE)).isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedArray(sorted).insertionPointFor(50L)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArray(sorted).insertionPointBefore(Long.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArray(sorted).insertionPointAfter(Long.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedIntArray_withDuplicates() {
    int[] sorted = new int[] {10, 20, 20, 30, 40, 40, 40};
    assertThat(inSortedArray(sorted).find(10)).hasValue(0);
    assertThat(inSortedArray(sorted).find(20).get()).isIn(ImmutableSet.of(1, 2));
    assertThat(inSortedArray(sorted).rangeOf(20)).isEqualTo(Range.closed(1, 2));
    assertThat(inSortedArray(sorted).insertionPointBefore(20)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(20)).isEqualTo(InsertionPoint.after(2));
    assertThat(inSortedArray(sorted).rangeOf(40)).isEqualTo(Range.closed(4, 6));
    assertThat(inSortedArray(sorted).insertionPointBefore(40)).isEqualTo(InsertionPoint.before(4));
    assertThat(inSortedArray(sorted).insertionPointAfter(40)).isEqualTo(InsertionPoint.after(6));
  }

  @Test
  public void binarySearch_inSortedLongArray_withDuplicates() {
    long[] sorted = new long[] {10, 20, 20, 30, 40, 40, 40};
    assertThat(inSortedArray(sorted).find(10L)).hasValue(0);
    assertThat(inSortedArray(sorted).find(20L).get()).isIn(ImmutableSet.of(1, 2));
    assertThat(inSortedArray(sorted).rangeOf(20L)).isEqualTo(Range.closed(1, 2));
    assertThat(inSortedArray(sorted).insertionPointBefore(20L)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArray(sorted).insertionPointAfter(20L)).isEqualTo(InsertionPoint.after(2));
    assertThat(inSortedArray(sorted).rangeOf(40L)).isEqualTo(Range.closed(4, 6));
    assertThat(inSortedArray(sorted).insertionPointBefore(40L)).isEqualTo(InsertionPoint.before(4));
    assertThat(inSortedArray(sorted).insertionPointAfter(40L)).isEqualTo(InsertionPoint.after(6));
  }

  @Test
  public void binarySearch_inSortedList_found() {
    ImmutableList<Integer> sorted = ImmutableList.of(10, 20, 30, 40);
    assertThat(inSortedList(sorted).find(20)).hasValue(1);
    assertThat(inSortedList(sorted).rangeOf(20)).isEqualTo(Range.closed(1, 1));
    assertThat(inSortedList(sorted).insertionPointFor(20)).isEqualTo(InsertionPoint.at(1));
    assertThat(inSortedList(sorted).insertionPointBefore(20)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedList(sorted).insertionPointAfter(20)).isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void binarySearch_inSortedList_notFoundInTheMiddle() {
    ImmutableList<Integer> sorted = ImmutableList.of(10, 20, 30, 40);
    assertThat(inSortedList(sorted).find(19)).isEmpty();
    assertThat(inSortedList(sorted).rangeOf(19)).isEqualTo(Range.closedOpen(1, 1));
    assertThat(inSortedList(sorted).insertionPointFor(19)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedList(sorted).insertionPointBefore(19)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedList(sorted).insertionPointAfter(19)).isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void binarySearch_inSortedList_notFoundAtTheBeginning() {
    ImmutableList<Integer> sorted = ImmutableList.of(10, 20, 30, 40);
    assertThat(inSortedList(sorted).find(-1)).isEmpty();
    assertThat(inSortedList(sorted).rangeOf(-1)).isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedList(sorted).insertionPointFor(Integer.MIN_VALUE)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedList(sorted).insertionPointBefore(-1)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedList(sorted).insertionPointAfter(0)).isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void binarySearch_inSortedList_notFoundAtTheEnd() {
    ImmutableList<Integer> sorted = ImmutableList.of(10, 20, 30, 40);
    assertThat(inSortedList(sorted).find(41)).isEmpty();
    assertThat(inSortedList(sorted).rangeOf(Integer.MAX_VALUE)).isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedList(sorted).insertionPointFor(50)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedList(sorted).insertionPointBefore(Integer.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedList(sorted).insertionPointAfter(Integer.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedList_withDuplicates() {
    ImmutableList<Integer> sorted = ImmutableList.of(10, 20, 20, 30, 40, 40, 40);
    assertThat(inSortedList(sorted).find(10)).hasValue(0);
    assertThat(inSortedList(sorted).find(20).get()).isIn(ImmutableSet.of(1, 2));
    assertThat(inSortedList(sorted).rangeOf(20)).isEqualTo(Range.closed(1, 2));
    assertThat(inSortedList(sorted).insertionPointBefore(20)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedList(sorted).insertionPointAfter(20)).isEqualTo(InsertionPoint.after(2));
    assertThat(inSortedList(sorted).rangeOf(40)).isEqualTo(Range.closed(4, 6));
    assertThat(inSortedList(sorted).insertionPointBefore(40)).isEqualTo(InsertionPoint.before(4));
    assertThat(inSortedList(sorted).insertionPointAfter(40)).isEqualTo(InsertionPoint.after(6));
  }

  @Test
  public void binarySearch_inSortedList_byKeyFunction() {
    ImmutableList<String> sorted = ImmutableList.of("x", "ab", "foo", "zerg");
    assertThat(inSortedList(sorted, String::length).find(2)).hasValue(1);
  }

  @Test
  public void binarySearch_inSortedList_byComparator() {
    List<String> sorted = asList(null, "a", "b", "c");
    assertThat(inSortedList(sorted, Comparator.nullsFirst(Comparator.naturalOrder())).find(null)).hasValue(0);
    assertThat(inSortedList(sorted, Comparator.nullsFirst(Comparator.naturalOrder())).find("b")).hasValue(2);
  }

  @Test
  public void binarySearch_inSortedDoubleArrayWithTolerance_found() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, 0.9).find(20D)).hasValue(1);
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(21D)).hasValue(1);
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(19D)).hasValue(1);
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(20D)).isEqualTo(Range.closed(1, 1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointFor(20D)).isEqualTo(InsertionPoint.at(1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(20D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(20D)).isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_notFoundInTheMiddle() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(18D)).isEmpty();
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(18D)).isEqualTo(Range.closedOpen(1, 1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointFor(18D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(18D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(18D)).isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_notFoundAtTheBeginning() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(-1D)).isEmpty();
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(-1D)).isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointFor(Double.MIN_VALUE)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointFor(Double.NEGATIVE_INFINITY)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(-1D)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(0D)).isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_notFoundAtTheEnd() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(42D)).isEmpty();
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(Double.MAX_VALUE)).isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointFor(50D)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(Double.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(Double.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArrayWithTolerance(sorted, 100).insertionPointFor(Double.NaN)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.POSITIVE_INFINITY).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_withDuplicates() {
    double[] sorted = new double[] {10, 20.1, 20.2, 30, 40.1, 40.2, 40.3};
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(10D)).hasValue(0);
    assertThat(inSortedArrayWithTolerance(sorted, 1).find(20D).get()).isIn(ImmutableSet.of(1, 2));
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(20D)).isEqualTo(Range.closed(1, 2));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(20D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(20D)).isEqualTo(InsertionPoint.after(2));
    assertThat(inSortedArrayWithTolerance(sorted, 1).rangeOf(40D)).isEqualTo(Range.closed(4, 6));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointBefore(40D)).isEqualTo(InsertionPoint.before(4));
    assertThat(inSortedArrayWithTolerance(sorted, 1).insertionPointAfter(40D)).isEqualTo(InsertionPoint.after(6));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_infinityTolerance() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(0D))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(Double.NEGATIVE_INFINITY))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(Double.POSITIVE_INFINITY))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.POSITIVE_INFINITY).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_maxTolerance() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).rangeOf(0D))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).rangeOf(Double.NEGATIVE_INFINITY))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).rangeOf(Double.POSITIVE_INFINITY))
        .isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedArrayWithTolerance_invalidTolerance() {
    double[] sorted = new double[] {10, 20, 30, 40};
    assertThrows(IllegalArgumentException.class, () -> inSortedArrayWithTolerance(sorted, -1));
    assertThrows(IllegalArgumentException.class, () -> inSortedArrayWithTolerance(sorted, -Double.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> inSortedArrayWithTolerance(sorted, Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> inSortedArrayWithTolerance(sorted, Double.NaN));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_found() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, 0.9).find(20D)).hasValue(1);
    assertThat(inSortedListWithTolerance(sorted, 1).find(21D)).hasValue(1);
    assertThat(inSortedListWithTolerance(sorted, 1).find(19D)).hasValue(1);
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(20D)).isEqualTo(Range.closed(1, 1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointFor(20D)).isEqualTo(InsertionPoint.at(1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(20D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(20D)).isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_notFoundInTheMiddle() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, 1).find(18D)).isEmpty();
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(18D)).isEqualTo(Range.closedOpen(1, 1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointFor(18D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(18D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(18D)).isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_notFoundAtTheBeginning() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, 1).find(-1D)).isEmpty();
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(-1D)).isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointFor(Double.MIN_VALUE)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointFor(Double.NEGATIVE_INFINITY)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(-1D)).isEqualTo(InsertionPoint.before(0));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(0D)).isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_notFoundAtTheEnd() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, 1).find(42D)).isEmpty();
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(Double.MAX_VALUE)).isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointFor(50D)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(Double.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(Double.MAX_VALUE)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedListWithTolerance(sorted, 100).insertionPointFor(Double.NaN)).isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedListWithTolerance(sorted, Double.MAX_VALUE).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
    assertThat(inSortedListWithTolerance(sorted, Double.POSITIVE_INFINITY).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_withDuplicates() {
    ImmutableList<Double> sorted = ImmutableList.of(10.1, 20.1, 20.2, 30.1, 40.1, 40.2, 40.3);
    assertThat(inSortedListWithTolerance(sorted, 1).find(10D)).hasValue(0);
    assertThat(inSortedListWithTolerance(sorted, 1).find(20D).get()).isIn(ImmutableSet.of(1, 2));
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(20D)).isEqualTo(Range.closed(1, 2));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(20D)).isEqualTo(InsertionPoint.before(1));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(20D)).isEqualTo(InsertionPoint.after(2));
    assertThat(inSortedListWithTolerance(sorted, 1).rangeOf(40D)).isEqualTo(Range.closed(4, 6));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointBefore(40D)).isEqualTo(InsertionPoint.before(4));
    assertThat(inSortedListWithTolerance(sorted, 1).insertionPointAfter(40D)).isEqualTo(InsertionPoint.after(6));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_infinityTolerance() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(0D))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedListWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(Double.NEGATIVE_INFINITY))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedListWithTolerance(sorted, Double.POSITIVE_INFINITY).rangeOf(Double.POSITIVE_INFINITY))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedListWithTolerance(sorted, Double.POSITIVE_INFINITY).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_maxTolerance() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThat(inSortedListWithTolerance(sorted, Double.MAX_VALUE).rangeOf(0D))
        .isEqualTo(Range.closed(0, 3));
    assertThat(inSortedListWithTolerance(sorted, Double.MAX_VALUE).rangeOf(Double.NEGATIVE_INFINITY))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(inSortedListWithTolerance(sorted, Double.MAX_VALUE).rangeOf(Double.POSITIVE_INFINITY))
        .isEqualTo(Range.closedOpen(4, 4));
    assertThat(inSortedListWithTolerance(sorted, Double.MAX_VALUE).insertionPointFor(Double.NaN))
        .isEqualTo(InsertionPoint.after(3));
  }

  @Test
  public void binarySearch_inSortedListWithTolerance_invalidTolerance() {
    ImmutableList<Double> sorted = ImmutableList.of(10D, 20D, 30D, 40D);
    assertThrows(IllegalArgumentException.class, () -> inSortedListWithTolerance(sorted, -1));
    assertThrows(IllegalArgumentException.class, () -> inSortedListWithTolerance(sorted, -Double.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> inSortedListWithTolerance(sorted, Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> inSortedListWithTolerance(sorted, Double.NaN));
  }

  @Test
  public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(BinarySearch.class);
    new ClassSanityTester().forAllPublicStaticMethods(BinarySearch.class).testNulls();
  }

  @Test
  public void binarySearchSqrt_smallNumbers() {
    assertThat(sqrt(4)).isEqualTo(2);
    assertThat(sqrt(1)).isEqualTo(1);
    assertThat(sqrt(0)).isEqualTo(0);
    assertThat(sqrt(5)).isEqualTo(2);
    assertThat(sqrt(101)).isEqualTo(10);
    assertThat(sqrt(4097)).isEqualTo(64);
  }

  @Test
  public void binarySearchSqrt_largeNumbers() {
    int[] numbers = {
      Integer.MAX_VALUE,
      Integer.MAX_VALUE - 1,
      Integer.MAX_VALUE - 2,
      Integer.MAX_VALUE / 2,
      Integer.MAX_VALUE / 10
    };
    for (int n : numbers) {
      long square = ((long) n) * n;
      assertThat(sqrt(square)).isEqualTo(n);
      assertThat(sqrt(square + 1)).isEqualTo(n);
      assertThat(sqrt(square - 1)).isEqualTo(n - 1);
    }
  }

  @Test
  public void binarySearchRotated_empty() {
    int[] sorted = {};
    assertThat(binarySearchRotated(sorted, 1)).isEmpty();
  }

  @Test
  public void binarySearchRotated_singleElement() {
    int[] sorted = {1};
    assertThat(binarySearchRotated(sorted, 1)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 2)).isEmpty();
  }

  @Test
  public void binarySearchRotated_twoElements() {
    int[] sorted = {1, 2};
    assertThat(binarySearchRotated(sorted, 1)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 2)).hasValue(1);
    assertThat(binarySearchRotated(sorted, 3)).isEmpty();
  }

  @Test
  public void binarySearchRotated_twoElementsReversed() {
    int[] sorted = {20, 10};
    assertThat(binarySearchRotated(sorted, 10)).hasValue(1);
    assertThat(binarySearchRotated(sorted, 20)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 30)).isEmpty();
  }

  @Test
  public void binarySearchRotated_notRatated() {
    int[] sorted = {10, 20, 30, 40, 50, 60, 70};
    for (int i = 0; i < sorted.length; i++) {
      assertThat(binarySearchRotated(sorted, sorted[i])).hasValue(i);
    }
    assertThat(binarySearchRotated(sorted, 0)).isEmpty();
    assertThat(binarySearchRotated(sorted, 80)).isEmpty();
    assertThat(binarySearchRotated(sorted, 15)).isEmpty();
  }

  @Test
  public void binarySearchRotated_ratated() {
    int[] rotated = {40, 50, 60, 70, 10, 20, 30};
    for (int i = 0; i < rotated.length; i++) {
      assertThat(binarySearchRotated(rotated, rotated[i])).hasValue(i);
    }
    assertThat(binarySearchRotated(rotated, 0)).isEmpty();
    assertThat(binarySearchRotated(rotated, 80)).isEmpty();
    assertThat(binarySearchRotated(rotated, 15)).isEmpty();
  }

  // Demo how binarySearch() can be used to implement more advanced binary search algorithms
  // such as searching within a rotated array.
  private static Optional<Integer> binarySearchRotated(int[] rotated, int target) {
    return BinarySearch.inRangeInclusive(0, rotated.length - 1)
        .find((low, mid, high) -> {
          int probe = rotated[mid];
          if (target < probe) {
            // target < mid value.
            // If we are in the first ascending half, it's in the right side if target <
            // rotated[lower].
            // If we are in the second ascending half, the right half is useless. Look left.
            return rotated[low] <= probe && target < rotated[low] ? 1 : -1;
          } else if (target > probe) {
            // target > mid value.
            // If we are in the first ascending half, the left side is useless. Look right.
            // If we are in the second ascending half, it's in the left side if target >
            // rotated[high].
            return probe <= rotated[high] && target > rotated[high] ? -1 : 1;
          } else {
            return 0;
          }
        });
  }

  private static int sqrt(long square) {
    return BinarySearch.inRangeInclusive(0, Integer.MAX_VALUE)
        .insertionPointFor((low, mid, high) -> Long.compare(square, (long) mid * mid))
        .floor();
  }

  static class NegativeValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.of(
          Integer.MIN_VALUE,
          Integer.MIN_VALUE + 1,
          Integer.MIN_VALUE / 2,
          Integer.MIN_VALUE / 3,
          -3,
          -2,
          -1);
    }
  }

  static class NegativeLongValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.of(
          Long.MIN_VALUE,
          Long.MIN_VALUE + 1,
          Long.MIN_VALUE / 2,
          Long.MIN_VALUE / 3,
          -3L,
          -2L,
          -1L);
    }
  }

  static class NonNegativeValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.of(
          0,
          1,
          2,
          3,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE - 1,
          Integer.MAX_VALUE - 2,
          Integer.MAX_VALUE - 3,
          Integer.MAX_VALUE / 2,
          Integer.MAX_VALUE / 3);
    }
  }

  static class NonNegativeLongValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.of(
          0L,
          1L,
          2L,
          3L,
          Long.MAX_VALUE,
          Long.MAX_VALUE - 1,
          Long.MAX_VALUE - 2,
          Long.MAX_VALUE - 3,
          Long.MAX_VALUE / 2,
          Long.MAX_VALUE / 3);
    }
  }

  static class IntValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.builder()
          .addAll(new NegativeValues().provideValues())
          .addAll(new NonNegativeValues().provideValues())
          .build();
    }
  }

  static class LongValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.builder()
          .addAll(new NegativeLongValues().provideValues())
          .addAll(new NonNegativeLongValues().provideValues())
          .build();
    }
  }
}

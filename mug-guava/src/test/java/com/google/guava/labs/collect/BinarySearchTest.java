package com.google.guava.labs.collect;

import static com.google.common.collect.Range.atLeast;
import static com.google.common.collect.Range.atMost;
import static com.google.common.collect.Range.closed;
import static com.google.common.collect.Range.closedOpen;
import static com.google.common.collect.Range.greaterThan;
import static com.google.common.collect.Range.lessThan;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.guava.labs.collect.BinarySearch.inSortedArray;
import static com.google.guava.labs.collect.BinarySearch.inSortedArrayWithTolerance;
import static com.google.guava.labs.collect.BinarySearch.inSortedList;
import static com.google.guava.labs.collect.BinarySearch.inSortedListWithTolerance;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.truth.Expect;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class BinarySearchTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void inRangeInclusive_invalidIndex() {
    assertThrows(IllegalArgumentException.class, () -> BinarySearch.forInts(closed(2, 0)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forInts(closed(Integer.MAX_VALUE, Integer.MAX_VALUE - 2)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forInts(closed(Integer.MIN_VALUE + 2, Integer.MIN_VALUE)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forInts(closed(Integer.MAX_VALUE, Integer.MIN_VALUE)));
  }

  @Test
  public void forLongs_Inclusive_invalidIndex() {
    assertThrows(IllegalArgumentException.class, () -> BinarySearch.forLongs(closed(2L, 0L)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forLongs(closed(Long.MAX_VALUE, Long.MAX_VALUE - 2)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forLongs(closed(Long.MIN_VALUE + 2, Long.MIN_VALUE)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forLongs(closed(Long.MAX_VALUE, Long.MIN_VALUE)));
  }

  @Test
  public void forInts_empty() {
    assertThat(BinarySearch.forInts(closedOpen(0, 0)).find((l, i, h) -> 0)).isEmpty();
    assertThat(BinarySearch.forInts(closedOpen(0, 0)).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.forInts(closedOpen(0, 0)).insertionPointFor((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(closedOpen(0, 0)).insertionPointBefore((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(closedOpen(0, 0)).insertionPointAfter((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(greaterThan(Integer.MAX_VALUE)).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(greaterThan(Integer.MAX_VALUE)).find((l, i, h) -> 0))
        .isEmpty();
    assertThat(BinarySearch.forInts(lessThan(Integer.MIN_VALUE)).find((l, i, h) -> 0))
        .isEmpty();
  }

  @Test
  public void forLongs_empty() {
    assertThat(BinarySearch.forLongs(closedOpen(0L, 0L)).find((l, i, h) -> 0)).isEmpty();
    assertThat(BinarySearch.forLongs(closedOpen(0L, 0L)).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.forLongs(closedOpen(0L, 0L)).insertionPointFor((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.forLongs(closedOpen(0L, 0L)).insertionPointBefore((l, i, h) -> 0))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.forLongs(closedOpen(0L, 0L)).insertionPointAfter((l, i, h) -> 0))
    .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.forLongs(greaterThan(Long.MAX_VALUE)).rangeOf((l, i, h) -> 0))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(greaterThan(Long.MAX_VALUE)).find((l, i, h) -> 0))
        .isEmpty();
    assertThat(BinarySearch.forLongs(lessThan(Long.MIN_VALUE)).find((l, i, h) -> 0))
        .isEmpty();
  }


  @Test
  public void forInts_singleCandidateRange_found() {
    assertThat(BinarySearch.forInts(closed(1, 1)).find((l, i, h) -> Integer.compare(i, 1)))
        .hasValue(1);
    assertThat(BinarySearch.forInts(closed(1, 1)).rangeOf((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(Range.closed(1, 1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointFor((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.at(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointBefore((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointAfter((l, i, h) -> Integer.compare(i, 1)))
        .isEqualTo(InsertionPoint.after(1));
  }


  @Test
  public void forLongs_singleCandidateRange_found() {
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).find((l, i, h) -> Long.compare(i, 1)))
        .hasValue(1L);
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).rangeOf((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(Range.closed(1L, 1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointFor((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.at(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointBefore((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointAfter((l, i, h) -> Long.compare(i, 1)))
        .isEqualTo(InsertionPoint.after(1L));
  }

  @Test
  public void forInts_singleCandidateRange_shouldBeBefore() {
    assertThat(BinarySearch.forInts(closed(1, 1)).find((l, i, h) -> Integer.compare(0, i)))
        .isEmpty();
    assertThat(BinarySearch.forInts(closed(1, 1)).rangeOf((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(Range.closedOpen(1, 1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointFor((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointBefore((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointAfter((l, i, h) -> Integer.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1));
  }

  @Test
  public void forInts_singleCandidateRange_shouldBeAfter() {
    assertThat(BinarySearch.forInts(closed(1, 1)).find((l, i, h) -> Integer.compare(10, i)))
        .isEmpty();
    assertThat(BinarySearch.forInts(closed(1, 1)).rangeOf((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(Range.closedOpen(2, 2));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointFor((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointBefore((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
    assertThat(BinarySearch.forInts(closed(1, 1)).insertionPointAfter((l, i, h) -> Integer.compare(10, i)))
        .isEqualTo(InsertionPoint.after(1));
  }

  @Test
  public void forLongs_singleCandidateRange_shouldBeBefore() {
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).find((l, i, h) -> Long.compare(0, i)))
        .isEmpty();
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).rangeOf((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(Range.closedOpen(1L, 1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointFor((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointBefore((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointAfter((l, i, h) -> Long.compare(0, i)))
        .isEqualTo(InsertionPoint.before(1L));
  }

  @Test
  public void forLongs_singleCandidateRange_shouldBeAfter() {
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).find((l, i, h) -> Long.compare(3, i)))
        .isEmpty();
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).rangeOf((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(Range.closedOpen(2L, 2L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointFor((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointBefore((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
    assertThat(BinarySearch.forLongs(closed(1L, 1L)).insertionPointAfter((l, i, h) -> Long.compare(3, i)))
        .isEqualTo(InsertionPoint.after(1L));
  }

  @Test
  public void forInts_useMinValueForLeft() {
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).find((l, i, h) -> Integer.MIN_VALUE))
        .isEmpty();
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).rangeOf((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(belowAllInts());
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointFor((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointBefore((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointAfter((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }

  @Test
  public void forInts_preventsUderflow_shouldBeBefore() {
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllInts());
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }

  @Test
  public void forInts_useMaxValueForRight() {
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).find((l, i, h) -> Integer.MAX_VALUE))
        .isEmpty();
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).rangeOf((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointFor((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointBefore((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointAfter((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
  }

  @Test
  public void forInts_preventsUnderflow_shouldBeAfter() {
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts(atMost(Integer.MIN_VALUE)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MIN_VALUE));
  }

  @Test
  public void forInts_aboveAll() {
    assertThat(BinarySearch.forInts().rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllInts());
  }

  @Test
  public void forInts_belowAll() {
    assertThat(BinarySearch.forInts().rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllInts());
  }

  @Test
  public void forLongs_aboveAll() {
    assertThat(BinarySearch.forLongs().rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllLongs());
  }

  @Test
  public void forLongs_belowAll() {
    assertThat(BinarySearch.forLongs().rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllLongs());
  }

  @Test
  public void forLongs_useMinValueForLeft() {
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).find((l, i, h) -> Integer.MIN_VALUE))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).rangeOf((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(belowAllLongs());
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointFor((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointBefore((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointAfter((l, i, h) -> Integer.MIN_VALUE))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }

  @Test
  public void forLongs_preventsUderflow_shouldBeBefore() {
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllLongs());
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }

  @Test
  public void forLongs_useMaxValueForRight() {
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).find((l, i, h) -> Integer.MAX_VALUE))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).rangeOf((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE + 1, Long.MIN_VALUE + 1));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointFor((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointBefore((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointAfter((l, i, h) -> Integer.MAX_VALUE))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
  }

  @Test
  public void forLongs_preventsUnderflow_shouldBeAfter() {
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(Long.MIN_VALUE + 1, Long.MIN_VALUE + 1));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(atMost(Long.MIN_VALUE)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MIN_VALUE));
  }

  @Test
  public void forInts_preventsOverflow_shouldBeBefore() {
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Integer.MAX_VALUE, Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MAX_VALUE));
  }

  @Test
  public void forInts_preventsOverflow_shouldBeAfter() {
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllInts());
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(Integer.MAX_VALUE)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }

  @Test
  public void forLongs_preventsOverflow_shouldBeBefore() {
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MAX_VALUE));
  }

  @Test
  public void forLongs_preventsOverflow_shouldBeAfter() {
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllLongs());
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(Long.MAX_VALUE)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void forInts_maxRange_shouldBeBefore() {
    assertThat(BinarySearch.forInts().find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forInts().rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllInts());
    assertThat(BinarySearch.forInts().insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts().insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
    assertThat(BinarySearch.forInts().insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Integer.MIN_VALUE));
  }


  @Test
  public void forInts_maxRange_shouldBeAfter() {
    assertThat(BinarySearch.forInts().find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forInts().rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllInts());
    assertThat(BinarySearch.forInts().insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts().insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts().insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }

  @Test
  public void forLongs_maxRange_shouldBeBefore() {
    assertThat(BinarySearch.forLongs().find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forLongs().rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllLongs());
    assertThat(BinarySearch.forLongs().insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs().insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs().insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }


  @Test
  public void forLongs_maxRange_shouldBeAfter() {
    assertThat(BinarySearch.forLongs().find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forLongs().rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllLongs());
    assertThat(BinarySearch.forLongs().insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs().insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs().insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void forInts_maxPositiveRange_shouldBeBefore() {
    assertThat(BinarySearch.forInts(atLeast(0)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atLeast(0)).rangeOf((l, i, h) -> -1))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(0));
  }


  @Test
  public void forInts_maxPositiveRange_shouldBeAfter() {
    assertThat(BinarySearch.forInts(atLeast(0)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forInts(atLeast(0)).rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllInts());
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Integer.MAX_VALUE));
  }


  @Test
  public void forLongs_maxPositiveRange_shouldBeAfter() {
    assertThat(BinarySearch.forLongs(atLeast(0L)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atLeast(0L)).rangeOf((l, i, h) -> 1))
        .isEqualTo(aboveAllLongs());
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(Long.MAX_VALUE));
  }

  @Test
  public void forInts_maxNegativeRange_shouldBeAfter() {
    assertThat(BinarySearch.forInts(Range.lessThan(0)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forInts(Range.lessThan(0)).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1));
  }

  @Test
  public void forLongs_maxNegativeRange_shouldBeBefore() {
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).find((l, i, h) -> -1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).rangeOf((l, i, h) -> -1))
        .isEqualTo(belowAllLongs());
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointFor((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointBefore((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointAfter((l, i, h) -> -1))
        .isEqualTo(InsertionPoint.before(Long.MIN_VALUE));
  }

  @Test
  public void forLongs_maxNegativeRange_shouldBeAfter() {
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).find((l, i, h) -> 1))
        .isEmpty();
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).rangeOf((l, i, h) -> 1))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointFor((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointBefore((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointAfter((l, i, h) -> 1))
        .isEqualTo(InsertionPoint.after(-1L));
  }

  @Test
  public void forInts_maxRange_found(
      @TestParameter(valuesProvider = IntValues.class) int target) {
    assertThat(
            BinarySearch.forInts(Range.<Integer>all())
                .find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(
            BinarySearch.forInts(Range.<Integer>all())
                .rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(
            BinarySearch.forInts(Range.<Integer>all())
                .insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(
            BinarySearch.forInts(Range.<Integer>all())
                .insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(
            BinarySearch.forInts(Range.<Integer>all())
                .insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forInts_maxNegativeRange_found(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(BinarySearch.forInts(Range.lessThan(0)).find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.forInts(Range.lessThan(0)).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.forInts(Range.lessThan(0)).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forLongs_maxRange_found(
      @TestParameter(valuesProvider = LongValues.class) long target) {
    assertThat(
            BinarySearch.forLongs(Range.<Long>all())
                .find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(
            BinarySearch.forLongs(Range.<Long>all())
                .rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(
            BinarySearch.forLongs(Range.<Long>all())
                .insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(
            BinarySearch.forLongs(Range.<Long>all())
                .insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(
            BinarySearch.forLongs(Range.<Long>all())
                .insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forLongs_maxNegativeRange_found(
      @TestParameter(valuesProvider = NegativeLongValues.class) long target) {
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.forLongs(Range.lessThan(0L)).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forInts_maxNonNegativeRange_found(
      @TestParameter(valuesProvider = NonNegativeValues.class) int target) {
    assertThat(BinarySearch.forInts(atLeast(0)).find((l, i, h) -> Integer.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.forInts(atLeast(0)).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forLongs_maxNonNegativeRange_found(
      @TestParameter(valuesProvider = NonNegativeLongValues.class) long target) {
    assertThat(BinarySearch.forLongs(atLeast(0L)).find((l, i, h) -> Long.compare(target, i)))
        .hasValue(target);
    assertThat(BinarySearch.forLongs(atLeast(0L)).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closed(target, target));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.at(target));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(target));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.after(target));
  }

  @Test
  public void forInts_maxNonNegativeRange_negativeNotFound(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(BinarySearch.forInts(atLeast(0)).find((l, i, h) -> Integer.compare(target, i)))
        .isEmpty();
    assertThat(BinarySearch.forInts(atLeast(0)).rangeOf((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(Range.closedOpen(0, 0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointFor((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointBefore((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
    assertThat(BinarySearch.forInts(atLeast(0)).insertionPointAfter((l, i, h) -> Integer.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0));
  }

  @Test
  public void forLongs_maxNonNegativeRange_negativeNotFound(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(BinarySearch.forLongs(atLeast(0L)).find((l, i, h) -> Long.compare(target, i)))
        .isEmpty();
    assertThat(BinarySearch.forLongs(atLeast(0L)).rangeOf((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(Range.closedOpen(0L, 0L));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointFor((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointBefore((l, i, h) -> Long.compare(target, i)))
        .isEqualTo(InsertionPoint.before(0L));
    assertThat(BinarySearch.forLongs(atLeast(0L)).insertionPointAfter((l, i, h) -> Long.compare(target, i)))
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
    assertThat(inSortedArrayWithTolerance(sorted, Double.MAX_VALUE).insertionPointFor(Double.POSITIVE_INFINITY))
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
  public void binarySearchIntSqrt_smallNumbers() {
    assertThat(intSqrt().insertionPointFor(4L).floor()).isEqualTo(2);
    assertThat(intSqrt().insertionPointFor(1L).floor()).isEqualTo(1);
    assertThat(intSqrt().insertionPointFor(0L).floor()).isEqualTo(0);
    assertThat(intSqrt().insertionPointFor(5L).floor()).isEqualTo(2);
    assertThat(intSqrt().insertionPointFor(101L).floor()).isEqualTo(10);
    assertThat(intSqrt().insertionPointFor(4097L).floor()).isEqualTo(64);
  }

  @Test
  public void binarySearchIntSqrt_largeNumbers() {
    int[] numbers = {
      Integer.MAX_VALUE,
      Integer.MAX_VALUE - 1,
      Integer.MAX_VALUE - 2,
      Integer.MAX_VALUE / 2,
      Integer.MAX_VALUE / 10
    };
    for (int n : numbers) {
      long square = ((long) n) * n;
      assertThat(intSqrt().insertionPointFor(square).floor()).isEqualTo(n);
      assertThat(intSqrt().insertionPointFor(square + 1).floor()).isEqualTo(n);
      assertThat(intSqrt().insertionPointFor(square - 1).floor()).isEqualTo(n - 1);
      assertThat(intSqrt().find(square)).hasValue(n);
      assertThat(intSqrt().find(square + 1)).isEmpty();
      assertThat(intSqrt().find(square - 1)).isEmpty();
    }
  }

  @Test
  public void binarySearchDoubleSqrt_smallNumbers(
      @TestParameter(
          {"0", "0.1", "0.2", "0.5", "1", "2", "3", "4", "5", "10", "20", "100", "1000", "9999999999"})
      double square) {
    double epsilon = 0.0000000001;
    InsertionPoint<Double> insertionPoint = squareRoot().insertionPointFor(square);
    assertThat(insertionPoint.floor()).isWithin(epsilon).of(Math.sqrt(square));
    assertThat(insertionPoint.ceiling()).isWithin(epsilon).of(Math.sqrt(square));
  }

  @Test
  public void binarySearchDoubleCubeRoot_smallNumbers(
      @TestParameter(
          {"0", "0.1", "0.2", "0.5", "1", "2", "-3", "4", "5", "10", "-20", "100", "1000", "-9999.99999"})
      double cube) {
    double epsilon = 0.0000000001;
    InsertionPoint<Double> insertionPoint = cubeRoot().insertionPointFor(cube);
    assertThat(insertionPoint.floor()).isWithin(epsilon).of(Math.cbrt(cube));
    assertThat(insertionPoint.ceiling()).isWithin(epsilon).of(Math.cbrt(cube));
  }

  @Test
  public void forDoubles_fullRange_smallNumberFound(
      @TestParameter(
          {"0", "0.1", "1", "100", "1000", "9999999999", "-1", "-100.345", "-999999.999"})
      double secret) {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_fullRange_maxValueFound() {
    double secret = Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
    assertThat(BinarySearch.forDoubles()
            .rangeOf((low, mid, high) -> Double.compare(secret, mid)))
        .isEqualTo(Range.closed(secret, secret));
  }

  @Test
  public void forDoubles_fullRange_maxValueNotFound() {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> 1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(Double.MAX_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(BinarySearch.forDoubles().rangeOf(((low, mid, high) -> 1)))
        .isEqualTo(Range.closedOpen(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
  }

  @Test
  public void forDoubles_fullRange_minValueFound() {
    double secret = -Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
    assertThat(BinarySearch.forDoubles()
            .rangeOf((low, mid, high) -> Double.compare(secret, mid)))
        .isEqualTo(Range.closed(secret, secret));
  }

  @Test
  public void forDoubles_fullRange_minValueNotFound() {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> -1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(insertionPoint.ceiling()).isEqualTo(-Double.MAX_VALUE);
    assertThat(BinarySearch.forDoubles().rangeOf(((low, mid, high) -> -1)))
        .isEqualTo(Range.closedOpen(-Double.MAX_VALUE, -Double.MAX_VALUE));
  }

  @Test
  public void forDoubles_nonNegativeRange_smallNumberFound(
      @TestParameter(
          {"0", "0.1", "1", "100", "1000", "9999999999"})
      double secret) {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.atLeast(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
    assertThat(BinarySearch.forDoubles(Range.atLeast(0D))
            .rangeOf((low, mid, high) -> Double.compare(secret, mid)))
        .isEqualTo(Range.closed(secret, secret));
  }

  @Test
  public void forDoubles_positiveRange_smallNumberFound(
      @TestParameter(
          {"0.1", "1", "100", "1000", "9999999999"})
      double secret) {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.greaterThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_positiveRange_maxValueFound() {
    double secret = Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.greaterThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_positiveRange_largeNumberFound() {
    double secret = Double.MAX_VALUE / Integer.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.greaterThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_positiveRange_maxValueNotFound() {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(greaterThan(0D))
            .insertionPointFor((low, mid, high) -> 1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(Double.MAX_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  public void forDoubles_positiveRange_positiveInfinityNotFound() {
    assertThat(BinarySearch.forDoubles(greaterThan(0D))
            .rangeOf((low, mid, high) -> {
              assertThat(low).isFinite();
              assertThat(mid).isFinite();
              assertThat(high).isFinite();
              return Double.compare(Double.POSITIVE_INFINITY, mid);
            }))
        .isEqualTo(Range.closedOpen(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
  }

  @Test
  public void forDoubles_positiveRange_invisibleNumber() {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(greaterThan(0D))
            .insertionPointFor((low, mid, high) -> mid <= 100 ? 1 : -1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(100.0);
    assertThat(insertionPoint.ceiling()).isEqualTo(Math.nextUp(100.0));
    assertThat(BinarySearch.forDoubles(greaterThan(0D))
            .rangeOf((low, mid, high) -> mid <= 100 ? 1 : -1))
        .isEqualTo(Range.closedOpen(Math.nextUp(100.0), Math.nextUp(100.0)));
    assertThat(BinarySearch.forDoubles(greaterThan(0D))
            .find((low, mid, high) -> mid <= 100 ? 1 : -1))
       .isEmpty();
  }

  @Test
  public void forDoubles_singletonRange_maxValueFound() {
    double secret = Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.closed(secret, secret))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.exact()).hasValue(secret);
  }

  @Test
  public void forDoubles_singletonRange_maxValueNotFound() {
    double secret = Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.closed(secret, secret))
            .insertionPointFor((low, mid, high) -> -1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.MAX_VALUE);
  }

  @Test
  public void forDoubles_singletonRange_minValueFound() {
    double secret = Double.MIN_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.closed(secret, secret))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.exact()).hasValue(secret);
  }

  @Test
  public void forDoubles_singletonRange_minValueNotFound() {
    double secret = Double.MIN_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(Range.closed(secret, secret))
            .insertionPointFor((low, mid, high) -> 1);
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(Double.MIN_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  public void forDoubles_emptyRange(
      @TestParameter({"-1", "-0.5", "0", "0.1", "1", "100"}) double at) {
    assertThat(BinarySearch.forDoubles(Range.closedOpen(at, at))
            .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.before(at));
    assertThat(BinarySearch.forDoubles(Range.openClosed(at, at))
        .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.after(at));
  }

  @Test
  public void forDoubles_emptyRange_maxValue() {
    double at = Double.MAX_VALUE;
    assertThat(BinarySearch.forDoubles(Range.closedOpen(at, at))
            .rangeOf((low, mid, high) -> 0))
        .isEqualTo(Range.closedOpen(at, at));
    assertThat(BinarySearch.forDoubles(Range.closedOpen(at, at))
            .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.before(at));
    assertThat(BinarySearch.forDoubles(Range.openClosed(at, at))
        .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.after(at));
  }

  @Test
  public void forDoubles_emptyRange_negativeMaxValue() {
    double at = -Double.MAX_VALUE;
    assertThat(BinarySearch.forDoubles(Range.closedOpen(at, at))
            .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.before(at));
    assertThat(BinarySearch.forDoubles(Range.openClosed(at, at))
        .insertionPointFor((low, mid, high) -> 0)).isEqualTo(InsertionPoint.after(at));
  }

  @Test
  public void forDoubles_emptyRange_infinityDisallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(Range.closedOpen(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(Range.closedOpen(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(Range.openClosed(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(Range.openClosed(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)));
  }

  @Test
  public void forDoubles_fullRange_infinityNotFound() {
    double secret = Double.POSITIVE_INFINITY;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(Double.MAX_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  public void forDoubles_fullRange_infinityNeverProbed() {
    double secret = Double.POSITIVE_INFINITY;
    assertThat(
        BinarySearch.forDoubles()
            .rangeOf((low, mid, high) -> {
              assertThat(low).isFinite();
              assertThat(mid).isFinite();
              assertThat(high).isFinite();
              return Double.compare(secret, mid);
            }))
        .isEqualTo(Range.closedOpen(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
  }

  @Test
  public void forDoubles_fullRange_negativeInfinityNotFound() {
    double secret = Double.NEGATIVE_INFINITY;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles()
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.ceiling()).isEqualTo(-Double.MAX_VALUE);
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
  }

  @Test
  public void forDoubles_fullRange_negativeInfinityNeverProbed() {
    double secret = Double.NEGATIVE_INFINITY;
    assertThat(
        BinarySearch.forDoubles()
            .rangeOf((low, mid, high) -> {
              assertThat(low).isFinite();
              assertThat(mid).isFinite();
              assertThat(high).isFinite();
              return Double.compare(secret, mid);
            }))
        .isEqualTo(Range.closedOpen(-Double.MAX_VALUE, -Double.MAX_VALUE));
  }

  @Test
  public void forDoubles_negativeRange_smallNumberFound(
      @TestParameter({"-1", "-100.345", "-999999.999"}) double secret) {
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(lessThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_negativeRange_maxValueFound() {
    double secret = -Double.MIN_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(lessThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isWithin(2 * Double.MIN_NORMAL).of(secret);
    assertThat(insertionPoint.ceiling()).isWithin(2 * Double.MIN_NORMAL).of(secret);
  }

  @Test
  public void forDoubles_negativeRange_minValueFound() {
    double secret = -Double.MAX_VALUE;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(lessThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.floor()).isEqualTo(secret);
    assertThat(insertionPoint.ceiling()).isEqualTo(secret);
  }

  @Test
  public void forDoubles_negativeRange_infinityNotFound() {
    double secret = Double.POSITIVE_INFINITY;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(lessThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.exact()).isEmpty();
    assertThat(insertionPoint.floor()).isEqualTo(-Double.MIN_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test
  public void forDoubles_negativeRange_negativeInfinityNotFound() {
    double secret = Double.NEGATIVE_INFINITY;
    InsertionPoint<Double> insertionPoint =
        BinarySearch.forDoubles(lessThan(0D))
            .insertionPointFor((low, mid, high) -> Double.compare(secret, mid));
    assertThat(insertionPoint.ceiling()).isEqualTo(-Double.MAX_VALUE);
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
  }

  @Test
  public void forDoubles_infiniteRange_disallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(closed(Double.NEGATIVE_INFINITY, 0D)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(closed(Double.NaN, 0D)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(closed(0D, Double.POSITIVE_INFINITY)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(closed(0D, Double.NaN)));
    assertThrows(
        IllegalArgumentException.class,
        () -> BinarySearch.forDoubles(closed(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)));
  }

  @Test
  public void binarySearchRotated_empty() {
    int[] sorted = {};
    assertThat(inCircularSortedArray(sorted).find(1)).isEmpty();
  }

  @Test
  public void binarySearchRotated_singleElement() {
    int[] sorted = {1};
    assertThat(inCircularSortedArray(sorted).find(1)).hasValue(0);
    assertThat(inCircularSortedArray(sorted).find(2)).isEmpty();
  }

  @Test
  public void binarySearchRotated_twoElements() {
    int[] sorted = {1, 2};
    assertThat(inCircularSortedArray(sorted).find(1)).hasValue(0);
    assertThat(inCircularSortedArray(sorted).find(2)).hasValue(1);
    assertThat(inCircularSortedArray(sorted).find(3)).isEmpty();
  }

  @Test
  public void binarySearchRotated_twoElementsReversed() {
    int[] sorted = {20, 10};
    assertThat(inCircularSortedArray(sorted).find(10)).hasValue(1);
    assertThat(inCircularSortedArray(sorted).find(20)).hasValue(0);
    assertThat(inCircularSortedArray(sorted).find(30)).isEmpty();
  }

  @Test
  public void binarySearchRotated_notRatated() {
    int[] sorted = {10, 20, 30, 40, 50, 60, 70};
    for (int i = 0; i < sorted.length; i++) {
      assertThat(inCircularSortedArray(sorted).find(sorted[i])).hasValue(i);
    }
    assertThat(inCircularSortedArray(sorted).find(0)).isEmpty();
    assertThat(inCircularSortedArray(sorted).find(80)).isEmpty();
    assertThat(inCircularSortedArray(sorted).find(15)).isEmpty();
  }

  @Test
  public void binarySearchRotated_ratated() {
    int[] rotated = {40, 50, 60, 70, 10, 20, 30};
    for (int i = 0; i < rotated.length; i++) {
      assertThat(inCircularSortedArray(rotated).find(rotated[i])).hasValue(i);
    }
    assertThat(inCircularSortedArray(rotated).find(0)).isEmpty();
    assertThat(inCircularSortedArray(rotated).find(80)).isEmpty();
    assertThat(inCircularSortedArray(rotated).find(15)).isEmpty();
  }

  @Test
  public void guessTheNumberGame(
      @TestParameter(valuesProvider = LongValues.class) long secret) {
    AtomicInteger times = new AtomicInteger();
    assertThat(BinarySearch.forLongs().find((low, mid, high) -> {
      times.incrementAndGet();
      return Long.compare(secret, mid);
    })).hasValue(secret);
    assertThat(times.get()).isAtMost(65);
  }

  @Test
  public void guessTheDoubleNumberGame(
      @TestParameter(valuesProvider = DoubleValues.class) double secret) {
    AtomicInteger times = new AtomicInteger();
    ImmutableMap.Builder<Double, Integer> builder = ImmutableMap.builder();
    assertThat(BinarySearch.forDoubles().find((low, mid, high) -> {
      builder.put(mid, times.incrementAndGet());
      return Double.compare(secret, mid);
    })).hasValue(secret);
    assertThat(builder.build().size()).isAtMost(65);
    assertThat(times.get()).isAtMost(65);
  }

  @Test
  public void guessTheDoubleNumberWithMostlyPositive(
      @TestParameter({"-0.5", "-0.1", "0", "0.001", "0.1", "1"}) double secret) {
    AtomicInteger times = new AtomicInteger();
    ImmutableMap.Builder<Double, Integer> builder = ImmutableMap.builder();
    assertThat(BinarySearch.forDoubles(atLeast(-1.0)).find((low, mid, high) -> {
      builder.put(mid, times.incrementAndGet());
      return Double.compare(secret, mid);
    })).hasValue(secret);
    assertThat(builder.build().size()).isAtMost(65);
    assertThat(times.get()).isAtMost(65);
  }

  @Test public void binarySearch_findMinParabola() {
    InsertionPoint<Integer> point = BinarySearch.forInts()
        .insertionPointFor((low, mid, high) -> Double.compare(parabola(mid - 1), parabola(mid)));
    int solution = point.floor();
    assertThat(solution).isEqualTo(-2);
  }

  @Test public void median_betweenNegatives() {
    double d1 = -1;
    double d2 = -0.5;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.open(d1, d2));
  }

  @Test public void median_betweenNegativeAndNegativeZero() {
    double d1 = -1;
    double d2 = -0.0;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.open(d1, d2));
  }

  @Test public void median_betweenNegativeAndPositiveZero() {
    double d1 = -1;
    double d2 = 0;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.closed(d1, d2));
  }

  @Test public void median_betweePositiveZeroAndPositive() {
    double d1 = 0;
    double d2 = 1.5;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.open(d1, d2));
  }

  @Test public void median_betweeNegativeZeroAndPositive() {
    double d1 = -0.0;
    double d2 = 1.5;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.open(d1, d2));
  }

  @Test public void median_betweeNegaetiveZeroAndPositiveZero() {
    double d1 = -0.0;
    double d2 = 0.0;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.closed(d1, d2));
  }

  @Test public void median_betweePositives() {
    double d1 = 1;
    double d2 = 1.5;
    double mean = BinarySearch.median(d1, d2);
    assertThat(mean).isIn(Range.open(d1, d2));
  }

  @Test public void find_betweenNegativeAndPositiveZero(
      @TestParameter({"-1", "-0.5", "-0.1", "0"}) double secret) {
    assertThat(
          BinarySearch.forDoubles(Range.closed(-1D, 0D))
              .find((lo, mid, hi) -> Double.compare(secret, mid)))
        .hasValue(secret);
  }

  @Test public void find_betweenNegativeZeroAndPositiveZero(
      @TestParameter({"0"}) double secret) {
    assertThat(
          BinarySearch.forDoubles(Range.closed(-0D, 0D))
              .find((lo, mid, hi) -> Double.compare(secret, mid)))
        .hasValue(secret);
  }

  @Test public void find_betweenNegativeAndPositive(
      @TestParameter({"-1", "-0.5", "-0.1", "0", "0.5", "1"}) double secret) {
    assertThat(
          BinarySearch.forDoubles(Range.closed(-1D, 1D))
              .find((lo, mid, hi) -> Double.compare(secret, mid)))
        .hasValue(secret);
  }

  private static double parabola(int x) {
    return Math.pow(x, 2) + 4 * x - 3;
  }

  // Demo how binarySearch() can be used to implement more advanced binary search algorithms
  // such as searching within a rotated array.
  private static BinarySearch.Table<Integer, Integer> inCircularSortedArray(int[] rotated) {
    return BinarySearch.forInts(Range.closedOpen(0, rotated.length))
        .by(key -> (low, mid, high) -> {
          int probe = rotated[mid];
          if (key < probe) {
            // target < mid value.
            // [low] <= probe means we are in the left half of [4, 5, 6, 1, 2, 3].
            // If we are in the first ascending half, it's in the right side if key <
            // rotated[lower].
            // If we are in the second ascending half, the right half is useless. Look left.
            return rotated[low] <= probe && key < rotated[low] ? 1 : -1;
          } else if (key > probe) {
            // key > mid value.
            // probe <= [high] means we are in the right half of [4, 5, 6, 1, 2, 3].
            // If we are in the second ascending half, it's in the left side if key >
            // rotated[high].
            // If we are in the first ascending half, the left side is useless. Look right.
            return probe <= rotated[high] && key > rotated[high] ? -1 : 1;
          } else {
            return 0;
          }
        });
  }

  private static BinarySearch.Table<Long, Integer> intSqrt() {
    return BinarySearch.forInts(atLeast(0))
        .by(square -> (low, mid, high) -> Long.compare(square, (long) mid * mid));
  }

  private static BinarySearch.Table<Double, Double> squareRoot() {
    return BinarySearch.forDoubles(atLeast(0D))
        .by(square -> (low, mid, high) -> Double.compare(square, mid * mid));
  }

  private static BinarySearch.Table<Double, Double> cubeRoot() {
    return BinarySearch.forDoubles()
      .by(cube -> (low, mid, high) -> Double.compare(cube, mid * mid * mid));
  }

  private static Range<Integer> belowAllInts() {
    return Range.lessThan(Integer.MIN_VALUE).canonical(DiscreteDomain.integers());
  }

  private static Range<Long> belowAllLongs() {
    return Range.lessThan(Long.MIN_VALUE).canonical(DiscreteDomain.longs());
  }

  private static Range<Integer> aboveAllInts() {
    return Range.openClosed(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  private static Range<Long> aboveAllLongs() {
    return Range.openClosed(Long.MAX_VALUE, Long.MAX_VALUE);
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

  static class DoubleValues implements TestParameter.TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return ImmutableList.of(
          -Long.MAX_VALUE,
          -Long.MAX_VALUE / 2,
          -Long.MAX_VALUE / 3,
          -3D,
          -2.5D,
          -1D,
          0D,
          0.5,
          0.123456789,
          1D,
          2D,
          3D,
          Double.MAX_VALUE,
          Double.MAX_VALUE / 2,
          Double.MAX_VALUE / 3);
    }
  }
}

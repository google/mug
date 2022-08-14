package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Algorithms.binarySearch;
import static com.google.mu.util.Algorithms.binarySearchInsertionPoint;

import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class AlgorithmsTest {

  @Test
  public void binarySearch_empty() {
    assertThat(binarySearch(0, -1, i -> 0)).isEmpty();
    assertThat(binarySearch(Integer.MAX_VALUE, Integer.MIN_VALUE, i -> 0)).isEmpty();
  }

  @Test
  public void binarySearch_singleCandidateRange_found() {
    assertThat(binarySearch(1, 1, i -> Integer.compare(i, 1)))
        .hasValue(1);
  }

  @Test
  public void binarySearch_singleCandidateRange_notFound() {
    assertThat(binarySearch(1, 1, i -> Integer.compare(i, 0)))
        .isEmpty();
  }

  @Test
  public void binarySearch_noUnderflow_notFound() {
    assertThat(binarySearch(Integer.MIN_VALUE, Integer.MIN_VALUE, i -> 1)).isEmpty();
    assertThat(binarySearch(Integer.MIN_VALUE, Integer.MIN_VALUE, i-> -1)).isEmpty();
  }

  @Test
  public void binarySearch_noOverflo_notFoundw() {
    assertThat(binarySearch(Integer.MAX_VALUE, Integer.MAX_VALUE, i -> 1)).isEmpty();
    assertThat(binarySearch(Integer.MAX_VALUE, Integer.MAX_VALUE, i-> -1)).isEmpty();
  }

  @Test
  public void binarySearch_maxRange_notFound() {
    assertThat(binarySearch(Integer.MIN_VALUE, Integer.MAX_VALUE, i -> 1)).isEmpty();
    assertThat(binarySearch(Integer.MIN_VALUE, Integer.MAX_VALUE, i-> -1)).isEmpty();
  }

  @Test
  public void binarySearch_maxPositiveRange_notFound() {
    assertThat(binarySearch(0, Integer.MAX_VALUE, i -> 1)).isEmpty();
    assertThat(binarySearch(0, Integer.MAX_VALUE, i-> -1)).isEmpty();
  }

  @Test
  public void binarySearch_maxNegativeRange_notFound() {
    assertThat(binarySearch(Integer.MIN_VALUE, -1, i -> 1)).isEmpty();
    assertThat(binarySearch(Integer.MIN_VALUE, -1, i-> -1)).isEmpty();
  }

  @Test
  public void binarySearch_maxRange_found(
      @TestParameter(valuesProvider = IntValues.class) int target) {
    assertThat(binarySearch(Integer.MIN_VALUE, Integer.MAX_VALUE, i -> Integer.compare(target, i)))
        .hasValue(target);
  }

  @Test
  public void binarySearch_maxNegativeRange_found(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(binarySearch(Integer.MIN_VALUE, -1, i -> Integer.compare(target, i)))
        .hasValue(target);
  }

  @Test
  public void binarySearch_maxNegativeRange_notFound(
      @TestParameter(valuesProvider = NonNegativeValues.class) int target) {
    assertThat(binarySearch(Integer.MIN_VALUE, -1, i -> Integer.compare(target, i)))
        .isEmpty();
  }

  @Test
  public void binarySearch_maxNonNegativeRange_found(
      @TestParameter(valuesProvider = NonNegativeValues.class) int target) {
    assertThat(binarySearch(0, Integer.MAX_VALUE, i -> Integer.compare(target, i)))
        .hasValue(target);
  }

  @Test
  public void binarySearch_maxNonNegativeRange_notFound(
      @TestParameter(valuesProvider = NegativeValues.class) int target) {
    assertThat(binarySearch(0, Integer.MAX_VALUE, i -> Integer.compare(target, i)))
        .isEmpty();
  }

  @Test
  public void binarySearch_array() {
    int[] sorted = new int[] {10, 20, 30, 40};
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(20, sorted[i]))).hasValue(1);
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(19, sorted[i]))).isEmpty();
  }

  @Test
  public void binarySearch_array_withDuplicates() {
    int[] sorted = new int[] {10, 20, 20, 30, 40, 40};
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(10, sorted[i]))).hasValue(0);
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(20, sorted[i])).get())
        .isIn(ImmutableSet.of(1, 2));
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(30, sorted[i]))).hasValue(3);
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(40, sorted[i])).get())
        .isIn(ImmutableSet.of(4, 5));
    assertThat(binarySearch(0, sorted.length - 1, i -> Integer.compare(0, sorted[i]))).isEmpty();
  }

  @Test public void binarySearchRotated_empty() {
    int[] sorted = {};
    assertThat(binarySearchRotated(sorted, 1)).isEmpty();
  }

  @Test public void binarySearchRotated_singleElement() {
    int[] sorted = {1};
    assertThat(binarySearchRotated(sorted, 1)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 2)).isEmpty();
  }

  @Test public void binarySearchRotated_twoElements() {
    int[] sorted = {1, 2};
    assertThat(binarySearchRotated(sorted, 1)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 2)).hasValue(1);
    assertThat(binarySearchRotated(sorted, 3)).isEmpty();
  }

  @Test public void binarySearchRotated_twoElementsReversed() {
    int[] sorted = {20, 10};
    assertThat(binarySearchRotated(sorted, 10)).hasValue(1);
    assertThat(binarySearchRotated(sorted, 20)).hasValue(0);
    assertThat(binarySearchRotated(sorted, 30)).isEmpty();
  }

  @Test public void binarySearchRotated_notRatated() {
    int[] sorted = {10, 20, 30, 40, 50, 60, 70};
    for (int i = 0; i < sorted.length; i++) {
      assertThat(binarySearchRotated(sorted, sorted[i])).hasValue(i);
    }
    assertThat(binarySearchRotated(sorted, 0)).isEmpty();
    assertThat(binarySearchRotated(sorted, 80)).isEmpty();
    assertThat(binarySearchRotated(sorted, 15)).isEmpty();
  }

  @Test public void binarySearchRotated_ratated() {
    int[] rotated = {40, 50, 60, 70, 10, 20, 30};
    for (int i = 0; i < rotated.length; i++) {
      assertThat(binarySearchRotated(rotated, rotated[i])).hasValue(i);
    }
    assertThat(binarySearchRotated(rotated, 0)).isEmpty();
    assertThat(binarySearchRotated(rotated, 80)).isEmpty();
    assertThat(binarySearchRotated(rotated, 15)).isEmpty();
  }

  @Test public void binarySearchSqrt_smallNumbers() {
    assertThat(sqrt(4)).isEqualTo(2);
    assertThat(sqrt(1)).isEqualTo(1);
    assertThat(sqrt(0)).isEqualTo(0);
    assertThat(sqrt(5)).isEqualTo(2);
    assertThat(sqrt(101)).isEqualTo(10);
    assertThat(sqrt(4097)).isEqualTo(64);
  }

  @Test public void binarySearchSqrt_largeNumbers() {
    int[] numbers = {
        Integer.MAX_VALUE,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE - 2,
        Integer.MAX_VALUE / 2,
        Integer.MAX_VALUE / 10};
    for (int n : numbers) {
      long square = ((long) n) * n;
      assertThat(sqrt(square)).isEqualTo(n);
      assertThat(sqrt(square + 1)).isEqualTo(n);
      assertThat(sqrt(square - 1)).isEqualTo(n - 1);
    }
  }

  @Test public void binarySearchInsertionPoint_empty() {
    assertThat(binarySearchInsertionPoint(0, -1, (low, mid, high) -> 0)).isEqualTo(-0.5);
    assertThat(
            binarySearchInsertionPoint(Integer.MIN_VALUE + 1, Integer.MIN_VALUE, (low, mid, high) -> 0))
        .isEqualTo(Integer.MIN_VALUE + 0.5);
    assertThat(
            binarySearchInsertionPoint(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, (low, mid, high) -> 0))
        .isEqualTo(Integer.MAX_VALUE - 0.5);
  }

  @Test public void binarySearchInsertionPoint_singleElement_found() {
    assertThat(binarySearchInsertionPoint(11, 11, (low, mid, high) -> 0)).isEqualTo(11.0);
  }

  @Test public void binarySearchInsertionPoint_singleElement_notFound() {
    assertThat(binarySearchInsertionPoint(11, 11, (low, mid, high) -> -1)).isEqualTo(10.5);
    assertThat(binarySearchInsertionPoint(11, 11, (low, mid, high) -> 1)).isEqualTo(11.5);
  }

  @Test public void binarySearchInsertionPoint_twoElements_found() {
    int[] arr = {10, 20};
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(10, arr[mid])))
        .isEqualTo(0.0);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(20, arr[mid])))
        .isEqualTo(1.0);
  }

  @Test public void binarySearchInsertionPoint_twoElements_notFound() {
    int[] arr = {10, 20};
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(9, arr[mid])))
        .isEqualTo(-0.5);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(Integer.MIN_VALUE, arr[mid])))
        .isEqualTo(-0.5);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(11, arr[mid])))
        .isEqualTo(0.5);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(19, arr[mid])))
        .isEqualTo(0.5);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(21, arr[mid])))
        .isEqualTo(1.5);
    assertThat(
            binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(Integer.MAX_VALUE, arr[mid])))
        .isEqualTo(1.5);
  }

  @Test public void binarySearchInsertionPoint_multipleElements() {
    int[] arr = {10, 20, 30, 40, 50};
    for (int i = 0; i < arr.length; i++) {
      int target = arr[i];
      assertThat(binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(target - 5, arr[mid])))
          .isEqualTo(i - 0.5);
      assertThat(binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(target, arr[mid])))
          .isEqualTo(i + 0.0);
    }
    assertThat(binarySearchInsertionPoint(0, arr.length - 1, (low, mid, high) -> Integer.compare(Integer.MAX_VALUE, arr[mid])))
        .isEqualTo(4.5);
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(Algorithms.class);
  }

  // Demo how binarySearch() can be used to implement more advanced binary search algorithms
  // such as searching within a rotated array.
  private static Optional<Integer> binarySearchRotated(int[] rotated, int target) {
    return binarySearch(0, rotated.length - 1, (low, mid, high) -> {
      int probe = rotated[mid];
      if (target < probe) {
        // target < mid value.
        // If we are in the first ascending half, it's in the right side if target < rotated[lower].
        // If we are in the second ascending half, the right half is useless. Look left.
        return rotated[low] <= probe && target < rotated[low] ? 1 : -1;
      } else if (target > probe) {
        // target > mid value.
        // If we are in the first ascending half, the left side is useless. Look right.
        // If we are in the second ascending half, it's in the left side if target > rotated[high].
        return probe <= rotated[high] && target > rotated[high] ? -1 : 1;
      } else {
        return 0;
      }
    });
  }

  private static int sqrt(long square) {
    return (int) Math.floor(
        binarySearchInsertionPoint(
            0, Integer.MAX_VALUE, (low, mid, high) -> Long.compare(square, (long) mid * mid)));
  }


  static class NegativeValues implements TestParameter.TestParameterValuesProvider {
    @Override public List<?> provideValues() {
      return ImmutableList.of(
          Integer.MIN_VALUE,
          Integer.MIN_VALUE + 1,
          Integer.MIN_VALUE / 2,
          Integer.MIN_VALUE / 3,
          -3, -2, -1);
    }
  }

  static class NonNegativeValues implements TestParameter.TestParameterValuesProvider {
    @Override public List<?> provideValues() {
      return ImmutableList.of(
          0, 1, 2, 3,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE - 1,
          Integer.MAX_VALUE - 2,
          Integer.MAX_VALUE - 3,
          Integer.MAX_VALUE / 2,
          Integer.MAX_VALUE / 3);
    }
  }

  static class IntValues implements TestParameter.TestParameterValuesProvider {
    @Override public List<?> provideValues() {
      return ImmutableList.builder().addAll(new NegativeValues().provideValues()).addAll(new NonNegativeValues().provideValues()).build();
    }
  }
}

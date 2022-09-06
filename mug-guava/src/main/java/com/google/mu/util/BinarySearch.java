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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.collect.DiscreteDomain.longs;
import static com.google.common.collect.Range.all;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.BoundType;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;

/**
 * Flexible binary search algorithm in a fluent API.
 *
 * <p>For example: <pre>{@code
 * // Most common: search within a sorted array
 * BinarySearch.inSortedArray([10, 20, 30, 40]).find(20)
 *     => Optional.of(1)
 *
 * // Find the insertion point if not found
 * BinarySearch.inSortedList([10, 20, 30, 40]).insertionPointFor(22)
 *     => InsertionPoint.before(2)
 *
 * // Search for double with a tolerance factor
 * // And find the range of all matches
 * BinarySearch.inSortedArrayWithTolerance([1.1, 2.1, 2.2, 2.3, 3.3, 4.4], 0.5).rangeOf(2)
 *     => Range.closed(1, 3)
 *
 * // Guess The Number Game
 * BinarySearch.forInts().find((lo, mid, hi) -> tooHighOrTooLow(mid))
 *     => Optional.of(theGuessedNumber)
 * }</pre>
 *
 * <p>The {@link #forInts}, {@link #forLongs} and the primitive array search methods perform no
 * boxing in the O(logn) search operation.
 *
 * <p>Note that except {@link #inSortedList(List, Comparator)}, which may support null search
 * targets if the comparator supports nulls, no other {@code BinarySearch} implementations
 * allow null queries.
 *
 * @param <Q> the search query, usually a target value, but can also be a target locator object
 *     like {@link IntSearchTarget}.
 * @param <R> the binary search result, usually the index in the source array or list, but can also
 *     be the optimal solution in non-array based bisection algorithms.
 * @since 6.4
 */
public abstract class BinarySearch<Q, R extends Comparable<R>> {
  /**
   * Returns a {@link BinarySearch} for indexes in the given sorted {@code list}.
   *
   * <p>For example: {@code inSortedList(numbers).find(20)}.
   */
  public static <E extends Comparable<E>> BinarySearch<E, Integer> inSortedList(
      List<? extends E> list) {
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> {
          checkNotNull(target);
          return (l, i, h) -> target.compareTo(list.get(i));
        });
  }

  /**
   * Returns a {@link BinarySearch} for indexes in the given sorted {@code list} according to
   * {@code comparator}.
   *
   * <p>For example: {@code inSortedList(timestamps, nullsFirst(naturalOrder())).find(timestamp)}.
   */
  public static <E> BinarySearch<E, Integer> inSortedList(
      List<? extends E> list, Comparator<? super E> sortedBy) {
    checkNotNull(sortedBy);
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> (l, i, h) -> sortedBy.compare(target, list.get(i)));
  }

  /**
   * Returns a {@link BinarySearch} for indexes in the given {@code list} sorted by the
   * {@code sortBy} function.
   *
   * <p>For example: {@code inSortedList(employees, Employee::age).rangeOf(20)}.
   */
  public static <Q extends Comparable<Q>, E> BinarySearch<Q, Integer> inSortedList(
      List<? extends E> list, Function<? super E, ? extends Q> sortedBy) {
    return inSortedList(Lists.transform(list, sortedBy::apply));
  }

  /**
   * Returns a {@link BinarySearch} for indexes in the given sorted int {@code array}.
   *
   * <p>For example: {@code inSortedArray(numbers).find(20)}.
   */
  public static BinarySearch<Integer, Integer> inSortedArray(int[] array) {
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          int intValue = target.intValue();
          return (l, i, h) -> Integer.compare(intValue, array[i]);
        });
  }

  /** Returns a {@link BinarySearch} for indexes in the given sorted long {@code array}.
   *
   * <p>For example: {@code inSortedArray(largeNumbers).find(1000000000000L)}.
   */
  public static BinarySearch<Long, Integer> inSortedArray(long[] array) {
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          long longValue = target.longValue();
          return (l, i, h) -> Long.compare(longValue, array[i]);
        });
  }

  /**
   * Returns a {@link BinarySearch} for indexes in the given sorted double {@code array}.
   * The positive {@code tolerance} is respected when comparing double values.
   *
   * <p>For example: {@code inSortedListWithTolerance(temperatures, 0.1).find(30)}.
   */
  public static BinarySearch<Double, Integer> inSortedListWithTolerance(
      List<Double> list, double tolerance) {
    checkNotNegative(tolerance);
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> {
          double v = target.doubleValue();
          return (l, i, h) -> DoubleMath.fuzzyCompare(v, list.get(i), tolerance);
        });
  }

  /**
   * Returns a {@link BinarySearch} for indexes in the given sorted double {@code array}.
   * The positive {@code tolerance} is respected when comparing double values.
   *
   * <p>For example: {@code inSortedArrayWithTolerance(temperatures, 0.1).find(30)}.
   */
  public static BinarySearch<Double, Integer> inSortedArrayWithTolerance(
      double[] array, double tolerance) {
    checkNotNegative(tolerance);
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          double v = target.doubleValue();
          return (l, i, h) -> DoubleMath.fuzzyCompare(v, array[i], tolerance);
        });
  }

  /**
   * Returns a {@link BinarySearch} over all integers.
   *
   * <p>Callers can search by an {@link IntSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * @see {@link #forInts(Range)} for examples
   */
  public static BinarySearch<IntSearchTarget, Integer> forInts() {
    return forInts(all());
  }

  /**
   * Returns a {@link BinarySearch} over the given {@code range}.
   *
   * <p>Callers can search by an {@link IntSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>While the common use cases of binary search is to search in sorted arrays and
   * lists, there are diverse contexts where the algorithm is also applicable
   * (think of the Guess the Number game). As a more realistic example, you can
   * binary search a rotated, otherwise strictly-ordered array, using the
   * following code:
   *
   * <pre>
   * {@code
   * Optional<Integer> binarySearchRotated(int[] rotated, int target) {
   *   return BinarySearch.forInts(Range.closedOpen(0, rotated.length))
   *       find((low, mid, high) -> {
   *         int probe = rotated[mid];
   *         if (target < probe) {
   *           return rotated[low] <= probe && target < rotated[low] ? 1 : -1;
   *         } else if (target > probe) {
   *            return probe <= rotated[high] && target > rotated[high] ? -1 : 1;
   *         } else {
   *           return 0;
   *         }
   *       });
   * }
   * }
   * </pre>
   */
  public static BinarySearch<IntSearchTarget, Integer> forInts(Range<Integer> range) {
    Integer low = low(range, integers());
    if (low == null) {
      return always(InsertionPoint.before(range.lowerEndpoint()));
    }
    Integer high = high(range, integers());
    if (high == null) {
      return always(InsertionPoint.after(range.upperEndpoint()));
    }
    return inRangeInclusive(low, high);
  }

  /**
   * Similar to {@link #forInts()}, but returns a {@link BinarySearch} over all {@code long} integers.
   *
   * <p>Callers can search by a {@link LongSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>For example, if we are to emulate the "Guess The Number" game, there will be a secret number
   * and we can bisect the full long integer domain to find the number as in:
   *
   * <pre>{@code
   * Optional<Long> guessed = forLongs().find((low, mid, high) -> Integer.compare(secret, mid));
   * assertThat(guessed).hasValue(secret);
   * }</pre>
   */
  public static BinarySearch<LongSearchTarget, Long> forLongs() {
    return forLongs(all());
  }

  /**
   * Similar to {@link #forInts(Range)}, but returns a {@link BinarySearch} over the given
   * {@code range} of {@code long} integers.
   *
   * <p>Callers can search by a {@link LongSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   */
  public static BinarySearch<LongSearchTarget, Long> forLongs(Range<Long> range) {
    Long low = low(range, longs());
    if (low == null) {
      return always(InsertionPoint.before(range.lowerEndpoint()));
    }
    Long high = high(range, longs());
    if (high == null) {
      return always(InsertionPoint.after(range.upperEndpoint()));
    }
    return inRangeInclusive(low, high);
  }

  /**
   * Returns a {@link BinarySearch} over double values.
   *
   * <p>Callers can search by an {@link DoubleSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>Different from {@link #inSortedArrayWithTolerance}, which is to search within
   * the int indexes of a double array, this method searches through double values.
   */
  public static BinarySearch<DoubleSearchTarget, Double> forDoubles() {
    return forDoubles(all());
  }

  /**
   * Similar to {@link #forInts(Range)}, but returns a {@link BinarySearch} over the given
   * {@code range} of {@code double} precision numbers.
   *
   * <p>Callers can search by a {@link DoubleSearchTarget} object that will be called at each iteration
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>For example you can implement {@code sqrt(double)} through binary search:
   *
   * <pre>{@code
   * BinarySearch<Double, Double> sqrt() {
   *   return forDoubles(atLeast(0D))
   *       .by(square -> (low, mid, high) -> Double.compare(square, mid * mid));
   * }
   *
   * sqrt().insertionPointFor(4096) => InsertionPoint.at(64)
   * }</pre>
   *
   * <p>Infinite endpoints are disallowed.
   */
  public static BinarySearch<DoubleSearchTarget, Double> forDoubles(Range<Double> range) {
    final double low;
    if (range.hasLowerBound()) {
      checkArgument(
          Double.isFinite(range.lowerEndpoint()),
          "Range with infinite endpoint not supported: %s", range);
      low =
          range.lowerBoundType() == BoundType.OPEN
              ? Math.nextAfter(range.lowerEndpoint(), Double.POSITIVE_INFINITY)
              : range.lowerEndpoint();
    } else {
      low = -Double.MAX_VALUE;
    }
    final double high;
    if (range.hasUpperBound()) {
      checkArgument(
          Double.isFinite(range.upperEndpoint()),
          "Range with infinite endpoint not supported: %s", range);
      high =
          range.upperBoundType() == BoundType.OPEN
              ? Math.nextAfter(range.upperEndpoint(), Double.NEGATIVE_INFINITY)
              : range.upperEndpoint();
    } else {
      high = Double.MAX_VALUE;
    }
    return inRangeInclusive(low, high);
  }

  /**
   * Searches for the index of {@code target}.
   *
   * <p>If target is found, returns the matching integer; otherwise returns empty.
   *
   * <p>This is an O(logn) operation.
   */
  public Optional<R> find(Q target) {
    return insertionPointFor(target).exact();
  }

  /**
   * Finds the range of elements that match {@code target}.
   *
   * <p>If there is a single match at index `i`, {@code [i, i]} is returned. For more
   * than one matches, the returned range is closed at both ends. If no match is
   * found, an empty range is returned with the open {@link Range#upperEndpoint}
   * being the insertion point, except if the insertion point should have been
   * after {@code MAX_VALUE}, in which case the open upper bound is saturated at
   * {@code MAX_VALUE} even though it's not the correct insertion point.
   *
   * <p>Callers can check {@link Range#isEmpty} to find out if {@code target} is found at all.
   *
   * <p>This is an O(logn) operation.
   */
  public Range<R> rangeOf(Q target) {
    InsertionPoint<R> left = insertionPointBefore(target);
    InsertionPoint<R> right = insertionPointAfter(target);
    if (!left.equals(right)) {
      return Range.closed(left.ceiling(), right.floor());
    }
    R insertAt = right.isAboveAll() ? right.floor() : right.ceiling();
    return Range.closedOpen(insertAt, insertAt);
  }

  /**
   * Finds the {@link InsertionPoint} if {@code target} were to be added <em>in order</em>.
   *
   * <p>Specifically, if {@code target} is found, the insertion point is at the its index;
   * while if not found, the insertion point is between the two adjacent indexes where
   * it could be inserted.
   *
   * <p>
   * Imagine in a Google Doc page, if you have two columns of
   * texts to be rendered into a two-column table, and you want to split the two
   * columns as evenly as possible such that it takes the fewest number of lines
   * overall. you can implement it with binary search:
   *
   * <pre>{@code
   *   InsertionPoint optimal = BinarySearch.forInts(Range.closedOpen(1, tableWidth))
   *       .insertionPointFor(
   *           (low, w, high) ->
   *               Integer.compare(
   *                   renderWithColumnWidth(text1, w),
   *                   renderWithColumnWidth(text2, tableWidth - w)));
   *   return optimal.exact()
   *       .orElseGet(
   *           () -> {
   *             int lines1 = max(
   *                 renderWithColumnWidth(text1, optimal.floor()),
   *                 renderWithColumnWidth(text2, tableWidth - optimal.floor()));
   *             int lines2 = max(
   *                 renderWithColumnWidth(text1, optimal.ceiling()),
   *                 renderWithColumnWidth(text2, tableWidth - optimal.ceiling()));
   *             return lines1 < lines2 ? floor : ceiling;
   *           });
   * }
   * </pre>
   *
   * <p>This is an O(logn) operation.
   */
  public abstract InsertionPoint<R> insertionPointFor(Q target);

  /**
   * Finds the insertion point immediately before the first element that's greater than or equal to the target.
   *
   * <p>If {@code target} is absent, {@link #insertionPointBefore} and {@link #insertionPointAfter} will be
   * the same point, where is after the last element less than the target and the first element greater than it.
   *
   * <p>{@code insertionPointBefore(target).exact()} will always return empty.
   *
   * <p>This is an O(logn) operation.
   */
  public abstract InsertionPoint<R> insertionPointBefore(Q target);

  /**
   * Finds the insertion point immediately after the last element that's less than or equal to the target.
   *
   * <p>If {@code target} is absent, {@link #insertionPointBefore} and {@link #insertionPointAfter} will be
   * the same point.
   *
   * <p>{@code insertionPointAfter(target).exact()} will always return empty.
   *
   * <p>This is an O(logn) operation.
   */
  public abstract InsertionPoint<R> insertionPointAfter(Q target);

  /**
   * Returns a new {@link BinarySearch} over the same source but transforms
   * the search target using the given {@code keyFunction} first.
   *
   * <p>Useful for creating a facade in front of a lower-level backing data source.
   *
   * <p>This is an O(1) operation.
   */
  public final <K> BinarySearch<K, R> by(Function<K, ? extends Q> keyFunction) {
    checkNotNull(keyFunction);
    BinarySearch<Q, R> underlying = this;
    return new BinarySearch<K, R>() {
      @Override public Optional<R> find(@Nullable K target) {
        return super.find(target);
      }
      @Override public Range<R> rangeOf(@Nullable K target) {
        return super.rangeOf(target);
      }
      @Override public InsertionPoint<R> insertionPointFor(@Nullable K target) {
        return underlying.insertionPointFor(keyFunction.apply(target));
      }
      @Override public InsertionPoint<R> insertionPointBefore(@Nullable K target) {
        return underlying.insertionPointBefore(keyFunction.apply(target));
      }
      @Override public InsertionPoint<R> insertionPointAfter(@Nullable K target) {
        return underlying.insertionPointAfter(keyFunction.apply(target));
      }
    };
  }

  /** Represents the search target that can be found through bisecting the integer domain. */
  public interface IntSearchTarget {
    /**
     * Given a range of {@code [low, high]} inclusively with {@code mid} as the
     * middle point of the binary search, locates the target.
     *
     * <p>
     * Returns 0 if {@code mid} is the target; negative to find it in the lower
     * range of {@code [low, mid)}; or positive to find it in the upper range of
     * {@code (mid, high]}.
     *
     * <p>
     * It's guaranteed that {@code low <= mid <= high}.
     */
    int locate(int low, int mid, int high);
  }

  /** Represents the search target that can be found through bisecting the long integer domain. */
  public interface LongSearchTarget {
    /**
     * Given a range of {@code [low, high]} inclusively with {@code mid} as the
     * middle point of the binary search, locates the target.
     *
     * <p>
     * Returns 0 if {@code mid} is the target; negative to find it in the lower
     * range of {@code [low, mid)}; or positive to find it in the upper range of
     * {@code (mid, high]}.
     *
     * <p>
     * It's guaranteed that {@code low <= mid <= high}.
     */
    int locate(long low, long mid, long high);
  }

  /** Represents the search target that can be found through bisecting the double domain. */
  public interface DoubleSearchTarget {
    /**
     * Given a range of {@code [low, high]} inclusively with {@code mid} as the
     * middle point of the binary search, locates the target.
     *
     * <p>
     * Returns 0 if {@code mid} is the target; negative to find it in the lower
     * range of {@code [low, mid)}; or positive to find it in the upper range of
     * {@code (mid, high]}.
     *
     * <p>
     * It's guaranteed that {@code low <= mid <= high}.
     */
    int locate(double low, double mid, double high);
  }

  private static BinarySearch<IntSearchTarget, Integer> inRangeInclusive(int from, int to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new BinarySearch<IntSearchTarget, Integer>() {
      @Override public InsertionPoint<Integer> insertionPointFor(IntSearchTarget target) {
        checkNotNull(target);
        for (int low = from, high = to; ;) {
          int mid = safeMid(low, high);
          int where = target.locate(low, mid, high);
          if (where > 0) {
            if (mid == high) { // mid is the floor
              return InsertionPoint.after(mid);
            }
            low = mid + 1;
          } else if (where < 0) {
            if (mid == low) { // mid is the ceiling
              return InsertionPoint.before(mid);
            }
            high = mid - 1;
          } else {
            return InsertionPoint.at(mid);
          }
        }
      }
      @Override public InsertionPoint<Integer> insertionPointBefore(IntSearchTarget target) {
        return insertionPointFor(before(target));
      }
      @Override public InsertionPoint<Integer> insertionPointAfter(IntSearchTarget target) {
        return insertionPointFor(after(target));
      }
    };
  }

  private static BinarySearch<LongSearchTarget, Long> inRangeInclusive(long from, long to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new BinarySearch<LongSearchTarget, Long>() {
      @Override public InsertionPoint<Long> insertionPointFor(LongSearchTarget target) {
        checkNotNull(target);
        for (long low = from, high = to; ;) {
          long mid = safeMidForLong(low, high);
          int where = target.locate(low, mid, high);
          if (where > 0) {
            if (mid == high) { // mid is the floor
              return InsertionPoint.after(mid);
            }
            low = mid + 1;
          } else if (where < 0) {
            if (mid == low) { // mid is the ceiling
              return InsertionPoint.before(mid);
            }
            high = mid - 1;
          } else {
            return InsertionPoint.at(mid);
          }
        }
      }
      @Override public InsertionPoint<Long> insertionPointBefore(LongSearchTarget target) {
        return insertionPointFor(before(target));
      }
      @Override public InsertionPoint<Long> insertionPointAfter(LongSearchTarget target) {
        return insertionPointFor(after(target));
      }
    };
  }

  private static BinarySearch<DoubleSearchTarget, Double> inRangeInclusive(
      final double from, final double to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new BinarySearch<DoubleSearchTarget, Double>() {
      @Override public InsertionPoint<Double> insertionPointFor(DoubleSearchTarget target) {
        checkNotNull(target);
        double floor = Double.NEGATIVE_INFINITY;
        double ceiling = Double.POSITIVE_INFINITY;
        for (double low = from, high = to; low <= high ;) {
          double mid = safeMidForDouble(low, high);
          int where = target.locate(low, mid, high);
          if (where > 0) {
            low = Math.nextAfter(mid, Double.POSITIVE_INFINITY);
            floor = mid;
          } else if (where < 0) {
            high = Math.nextAfter(mid, Double.NEGATIVE_INFINITY);
            ceiling = mid;
          } else {
            return InsertionPoint.at(mid);
          }
        }
        return InsertionPoint.between(
            floor < from && target.locate(from, from, from) >= 0 ? from : floor,
            ceiling > to && target.locate(to, to, to) <= 0 ? to : ceiling);
      }
      @Override public InsertionPoint<Double> insertionPointBefore(DoubleSearchTarget target) {
        return insertionPointFor(before(target));
      }
      @Override public InsertionPoint<Double> insertionPointAfter(DoubleSearchTarget target) {
        return insertionPointFor(after(target));
      }
    };
  }

  private static int safeMid(int low, int high) {
    return (int) (((long) low + high) / 2);
  }

  private static long safeMidForLong(long low, long high) {
    boolean sameSign = (low >= 0) == (high >= 0);
    return sameSign ? low + (high - low) / 2 : (low + high) / 2;
  }

  private static double safeMidForDouble(double low, double high) {
    boolean sameSign = (low >= 0) == (high >= 0);
    return sameSign ? low + (high - low) / 2 : (low + high) / 2;
  }

  private static IntSearchTarget before(IntSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) <= 0 ? -1 : 1;
  }

  private static LongSearchTarget before(LongSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) <= 0 ? -1 : 1;
  }

  private static DoubleSearchTarget before(DoubleSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) <= 0 ? -1 : 1;
  }

  private static IntSearchTarget after(IntSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) < 0 ? -1 : 1;
  }

  private static LongSearchTarget after(LongSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) < 0 ? -1 : 1;
  }

  private static DoubleSearchTarget after(DoubleSearchTarget target) {
    checkNotNull(target);
    return (low, mid, high) -> target.locate(low, mid, high) < 0 ? -1 : 1;
  }

  private static <Q, R extends Comparable<R>> BinarySearch<Q, R> always(InsertionPoint<R> point) {
    return new BinarySearch<Q, R>() {
      @Override public InsertionPoint<R> insertionPointFor(Q target) {
        checkNotNull(target);
        return point;
      }
      @Override public InsertionPoint<R> insertionPointBefore(Q target) {
        checkNotNull(target);
        return point;
      }
      @Override public InsertionPoint<R> insertionPointAfter(Q target) {
        checkNotNull(target);
        return point;
      }
    };
  }

  private static void checkNotNegative(double tolerance) {
    checkArgument(tolerance >= 0.0, "tolerance (%s) cannot be negative", tolerance);
  }

  /**
   * Returns the effective low endpoint of {@code range} in {@code domain}, or null if the endpoint
   * is impossible as in {@code lessThan(MIN_VALUE)}.
   */
  @Nullable
  private static <R extends Comparable<R>> R low(Range<R> range, DiscreteDomain<R> domain) {
    if (range.hasLowerBound()) {
      return range.lowerBoundType() == BoundType.CLOSED
          ? range.lowerEndpoint()
          : domain.next(range.lowerEndpoint());
    }
    return domain.minValue();
  }

  /**
   * Returns the effective upper endpoint of {@code range} in {@code domain}, or null if the endpoint
   * is impossible as in {@code greaterThan(MAX_VALUE)}.
   */
  @Nullable
  private static <R extends Comparable<R>> R high(Range<R> range, DiscreteDomain<R> domain) {
    if (range.hasUpperBound()) {
      return range.upperBoundType() == BoundType.CLOSED
          ? range.upperEndpoint()
          : domain.previous(range.upperEndpoint());
    }
    return domain.maxValue();
  }

  BinarySearch() {}
}

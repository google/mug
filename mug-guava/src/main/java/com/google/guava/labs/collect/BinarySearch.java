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
package com.google.guava.labs.collect;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.collect.DiscreteDomain.longs;
import static com.google.common.collect.Range.all;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;

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
import com.google.common.math.IntMath;
import com.google.common.math.LongMath;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Generic binary search algorithm with support for and <b>beyond</b> sorted lists and arrays,
 * in a fluent API.
 *
 * <p>For sorted lists and arrays:
 *
 * <pre>{@code
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
 * }</pre>
 *
 * To solve a monotonic polynomial function:
 *
 * <pre>{@code
 * double polynomial(double x) {
 *   return pow(x, 3) + 3 * x;
 * }
 *
 * InsertionPoint<Double> solve(double y) {
 *   return BinarySearch.forDoubles()
 *       .insertionPointFor((low, mid, high) -> Double.compare(y, polynomial(mid)));
 * }
 * }</pre>
 *
 * To find the minimum int value of a parabola:
 *
 * <pre>{@code
 * double parabola(int x) {
 *   return pow(x, 2) + 4 * x - 3;
 * }
 *
 * int minimum = BinarySearch.forInts()
 *     .insertionPointFor(
 *         (low, mid, high) -> Double.compare(parabola(mid - 1), parabola(mid)))
 *     .floor();
 *     => -2
 * }</pre>
 *
 * To emulate the Guess The Number game:
 *
 * <pre>{@code
 * BinarySearch.forInts()
 *     .find((lo, mid, hi) -> tooHighOrTooLow(mid))
 *     => Optional.of(theGuessedNumber)
 * }</pre>
 *
 * <p>The {@link #forInts}, {@link #forLongs}, {@link #forDoubles} and the primitive array search
 * methods perform no boxing in the O(logn) search operation.
 *
 * <p>Note that except {@link #inSortedList(List, Comparator)}, which may support null search
 * targets if the comparator supports nulls, no other {@code BinarySearch.Table} implementations
 * allow null search targets.
 *
 * @since 8.0
 */
@CheckReturnValue
public final class BinarySearch {
  /**
   * Returns a {@link Table} to search for element indexes in the given sorted {@code list}.
   *
   * <p>For example: {@code inSortedList(numbers).find(20)}.
   *
   * <p>This is an O(1) operation.
   *
   * @param list expected to support random access (not {@code LinkedList} for instance),
   *     or else performance will suffer.
   */
  public static <E extends Comparable<E>> Table<E, Integer> inSortedList(
      List<? extends E> list) {
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> {
          checkNotNull(target);
          return (l, i, h) -> target.compareTo(list.get(i));
        });
  }

  /**
   * Returns a {@link Table} to search for element indexes in the given sorted {@code list}
   * according to {@code comparator}.
   *
   * <p>For example: {@code inSortedList(timestamps, nullsFirst(naturalOrder())).find(timestamp)}.
   *
   * <p>This is an O(1) operation.
   *
   * @param list expected to support random access (not {@code LinkedList} for instance),
   *     or else performance will suffer.
   */
  public static <E> Table<E, Integer> inSortedList(
      List<? extends E> list, Comparator<? super E> sortedBy) {
    checkNotNull(sortedBy);
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> (l, i, h) -> sortedBy.compare(target, list.get(i)));
  }

  /**
   * Returns a {@link Table} to search for element indexes in the given {@code list} sorted by
   * the {@code sortBy} function.
   *
   * <p>For example: {@code inSortedList(employees, Employee::age).rangeOf(20)}.
   *
   * <p>This is an O(1) operation.
   *
   * @param list expected to support random access (not {@code LinkedList} for instance),
   *     or else performance will suffer.
   */
  public static <Q extends Comparable<Q>, E> Table<Q, Integer> inSortedList(
      List<? extends E> list, Function<? super E, ? extends Q> sortedBy) {
    return inSortedList(Lists.transform(list, sortedBy::apply));
  }

  /**
   * Returns a {@link Table} to search for array element indexes in the given sorted int
   * {@code array}.
   *
   * <p>For example: {@code inSortedArray(numbers).find(20)}.
   *
   * <p>This is an O(1) operation.
   */
  public static Table<Integer, Integer> inSortedArray(int[] array) {
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          int intValue = target.intValue();
          return (l, i, h) -> Integer.compare(intValue, array[i]);
        });
  }

  /** Returns a {@link Table} to search for array element indexes in the given sorted long
   * {@code array}.
   *
   * <p>For example: {@code inSortedArray(largeNumbers).find(1000000000000L)}.
   *
   * <p>This is an O(1) operation.
   */
  public static Table<Long, Integer> inSortedArray(long[] array) {
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          long longValue = target.longValue();
          return (l, i, h) -> Long.compare(longValue, array[i]);
        });
  }

  /**
   * Returns a {@link Table} to search for element indexes in the given list of sorted
   * {@code double} values. The positive {@code tolerance} is respected when comparing double
   * values.
   *
   * <p>For example: {@code inSortedListWithTolerance(temperatures, 0.1).find(30)}.
   *
   * <p>This is an O(1) operation.
   *
   * @param list expected to support random access (not {@code LinkedList} for instance),
   *     or else performance will suffer.
   * @param tolerance an inclusive upper bound on the difference between the search target
   *     and elements in the list, which must be a non-negative finite value, i.e. not {@link
   *     Double#NaN}, {@link Double#POSITIVE_INFINITY}, or negative, including {@code -0.0}
   */
  public static Table<Double, Integer> inSortedListWithTolerance(
      List<Double> list, double tolerance) {
    checkNotNegative(tolerance);
    return inRangeInclusive(0, list.size() - 1)
        .by(target -> {
          double v = target.doubleValue();
          return (l, i, h) -> DoubleMath.fuzzyCompare(v, list.get(i), tolerance);
        });
  }

  /**
   * Returns a {@link Table} to search for array element indexes in the given sorted double
   * {@code array}. The positive {@code tolerance} is respected when comparing double values.
   *
   * <p>For example: {@code inSortedArrayWithTolerance(temperatures, 0.1).find(30)}.
   *
   * <p>This is an O(1) operation.
   */
  public static Table<Double, Integer> inSortedArrayWithTolerance(
      double[] array, double tolerance) {
    checkNotNegative(tolerance);
    return inRangeInclusive(0, array.length - 1)
        .by(target -> {
          double v = target.doubleValue();
          return (l, i, h) -> DoubleMath.fuzzyCompare(v, array[i], tolerance);
        });
  }

  /**
   * Returns a {@link Table} over all integers.
   *
   * <p>Callers can search by an {@link IntSearchTarget} object that will be called at each probe
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>This is an O(1) operation.
   *
   * @see {@link #forInts(Range)} for examples
   */
  public static Table<IntSearchTarget, Integer> forInts() {
    return forInts(all());
  }

  /**
   * Returns a {@link Table} over the given {@code range}.
   *
   * <p>Callers can search by an {@link IntSearchTarget} object that will be called at each probe
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
   *
   * <p>This is an O(1) operation.
   */
  public static Table<IntSearchTarget, Integer> forInts(Range<Integer> range) {
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
   * Similar to {@link #forInts()}, but returns a {@link Table} over all {@code long} integers.
   *
   * <p>Callers can search by a {@link LongSearchTarget} object that will be called at each probe
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
   *
   * <p>This is an O(1) operation.
   */
  public static Table<LongSearchTarget, Long> forLongs() {
    return forLongs(all());
  }

  /**
   * Similar to {@link #forInts(Range)}, but returns a {@link Table} over the given
   * {@code range} of {@code long} integers.
   *
   * <p>Callers can search by a {@link LongSearchTarget} object that will be called at each probe
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>This is an O(1) operation.
   */
  public static Table<LongSearchTarget, Long> forLongs(Range<Long> range) {
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
   * Returns a {@link Table} over all finite double values (except {@link Double#NaN},
   * {@link Double#NEGATIVE_INFINITY} and {@link Double#POSITIVE_INFINITY}).
   *
   * <p>Callers can search by an {@link DoubleSearchTarget} object that will be called at each probe
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>Different from {@link #inSortedArrayWithTolerance}, which is to search within
   * the int indexes of a double array, this method searches through double values.
   *
   * <p>You can use binary search to solve monotonic functions.
   * For example the following method solves cube root:
   *
   * <pre>{@code
   * BinarySearch.Table<Double, Double> cubeRoot() {
   *   return forDoubles()
   *       .by(cube -> (low, mid, high) -> Double.compare(cube, mid * mid * mid));
   * }
   *
   * cubeRoot().insertionPointFor(125.0) => InsertionPoint.at(5.0)
   * }</pre>
   *
   * <p>This is an O(1) operation.
   */
  public static Table<DoubleSearchTarget, Double> forDoubles() {
    return forDoubles(all());
  }

  /**
   * Similar to {@link #forInts(Range)}, but returns a {@link Table} over the given
   * {@code range} of {@code double} precision numbers.
   *
   * <p>Callers can search by a {@link DoubleSearchTarget} object that will be called at each probe
   * to determine whether the target is already found at the current mid-point, to the left half of the
   * current subrange, or to the right half of the current subrange.
   *
   * <p>For example you can implement square root through binary search:
   *
   * <pre>{@code
   * BinarySearch.Table<Double, Double> sqrt() {
   *   return forDoubles(atLeast(0D))
   *       .by(square -> (low, mid, high) -> Double.compare(square, mid * mid));
   * }
   *
   * sqrt().insertionPointFor(25.0) => InsertionPoint.at(5.0)
   * }</pre>
   *
   * <p>Infinite endpoints are disallowed.
   *
   * <p>This is an O(1) operation.
   */
  public static Table<DoubleSearchTarget, Double> forDoubles(Range<Double> range) {
    final double low;
    if (range.hasLowerBound()) {
      checkArgument(
          Double.isFinite(range.lowerEndpoint()),
          "Range with infinite endpoint not supported: %s", range);
      low =
          range.lowerBoundType() == BoundType.OPEN
              ? Math.nextUp(range.lowerEndpoint())
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
              ? Math.nextDown(range.upperEndpoint())
              : range.upperEndpoint();
    } else {
      high = Double.MAX_VALUE;
    }
    return inRangeInclusive(low, high);
  }

  /**
   * Like a hash table, allows looking up comparable values by a key, except the lookup is through
   * binary search instead of hashing.
   *
   * @param <K> the search key, usually a target value, but can also be a target locator object
   *     like {@link IntSearchTarget}.
   * @param <C> the binary search result, usually the index in the source array or list, but can also
   *     be the optimal solution in non-array based bisection algorithms such as the minimum value of
   *     a parabola function.
   */
  public static abstract class Table<K, C extends Comparable<C>> {
    /**
     * Searches for {@code target} and returns the result if found; or else returns empty.
     *
     * <p>This is an O(logn) operation.
     */
    public Optional<C> find(K target) {
      return insertionPointFor(target).exact();
    }

    /**
     * Finds the range of elements that match {@code target}.
     *
     * <p>If the target is found at index {@code i}, the single-element range <b>{@code [i..i]}</b>
     * is returned.
     *
     * <p>If there are ties from index {@code i} to {@code j}, the closed range <b>{@code [i..j]}</b>
     * is returned.
     *
     * <p>If the target isn't found, an {@link Range#isEmpty empty} range is returned whose endpoint
     * is the "insertion point" (where the target would have been inserted without breaking order).
     * The direction of the open endpoint determines whether to insert before or after the point.
     *
     * <p>Invariants for insertion points:
     * <ul>
     * <li>An open lower endpoint {@code (i..} means {@code target > i} so the insertion point
     *   should be after {@code i} in order not to break order.
     * <li>An open upper endpoint {@code ..j)} means {@code target < j} so the insertion point
     *   should be before {@code j} in order not to break order.
     * <li>A closed lower or upper endpoint means {@code target >= i} or {@code target <= j}
     *   respectively, so the insertion point can be either before or after without breaking order.
     * </ul>
     *
     * <p>Therefore:
     *
     * <ul>
     * <li>For all insertion points except the last one after {@code MAX_VALUE}, the returned range
     *   is {@link Range#closedOpen closed-open} <b>{@code [i..i)}</b>, indicating that the insertion
     *   point is immediately <em>before</em> endpoint {@code i}.
     * <li>While if the insertion point is after {@code MAX_VALUE}, the returned range is {@link
     *   Range#openClosed open-closed} <b>{@code (MAX_VALUE..MAX_VALUE]}</b>, indicating that the
     *   insertion point is immediately <em>after</em> endpoint {@code MAX_VALUE}.
     * </ul>
     *
     * <p>If your code needs the insertion point when not found, but doesn't need to find the range of
     * elements otherwise, use {@link #insertionPointFor} instead, which is more intuitive and also
     * faster.
     *
     * <p>Realistically though, unless {@code target > MAX_VALUE} is meaningful to your use case,
     * it's okay to simply insert the target before {@code rangeOf().lowerEndpoint()}. This invariant
     * holds regardless whether the target is found or not.
     *
     * <p>This is an O(logn) operation.
     */
    public Range<C> rangeOf(K target) {
      InsertionPoint<C> left = insertionPointBefore(target);
      InsertionPoint<C> right = insertionPointAfter(target);
      if (left.equals(right)) {
        return left.isAboveAll()
            ? Range.openClosed(left.floor(), left.floor())
            : Range.closedOpen(left.ceiling(), right.ceiling());
      }
      return Range.closed(left.ceiling(), right.floor());
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
    public abstract InsertionPoint<C> insertionPointFor(K target);

    /**
     * Finds the insertion point immediately before the first element that's greater than or equal to the target.
     *
     * <p>If {@code target} is absent, {@link #insertionPointBefore} and {@link #insertionPointAfter} will be
     * the same point, after the last element less than the target and the first element greater than it.
     *
     * <p>{@code insertionPointBefore(target).exact()} will always return empty.
     *
     * <p>This is an O(logn) operation.
     */
    public abstract InsertionPoint<C> insertionPointBefore(K target);

    /**
     * Finds the insertion point immediately after the last element that's less than or equal to the target.
     *
     * <p>If {@code target} is absent, {@link #insertionPointBefore} and {@link #insertionPointAfter} will be
     * the same point, after the last element less than the target and the first element greater than it.
     *
     * <p>{@code insertionPointAfter(target).exact()} will always return empty.
     *
     * <p>This is an O(logn) operation.
     */
    public abstract InsertionPoint<C> insertionPointAfter(K target);

    /**
     * Returns a new {@link Table} over the same source but transforms
     * the search target using the given {@code keyFunction} first.
     *
     * <p>Useful for creating a facade in front of a lower-level backing data source.
     *
     * <p>For example, if you have epoch millisecond timestamps stored in a sorted {@code long[]}
     * array, you can wrap it as a {@code BinarySearch.Table<Instant, Integer>} for your code to be able
     * to search by {@link java.time.Instant} repeatedly:
     *
     * <pre>{@code
     * class Timeline {
     *   private final long[] timestamps;
     *
     *   BinarySearch.Table<Instant, Integer> instants() {
     *     return BinarySearch.inSortedArray(timestamps).by(Instant::toEpochMilli);
     *   }
     * }
     * }</pre>
     *
     * <p>This is an O(1) operation.
     *
     * @param <Q> the logical search key type of the returned {@link Table}.
     */
    public final <Q> Table<Q, C> by(Function<Q, ? extends K> keyFunction) {
      checkNotNull(keyFunction);
      Table<K, C> underlying = this;
      return new Table<Q, C>() {
        @Override public Optional<C> find(@Nullable Q query) {
          return super.find(query);
        }
        @Override public Range<C> rangeOf(@Nullable Q query) {
          return super.rangeOf(query);
        }
        @Override public InsertionPoint<C> insertionPointFor(@Nullable Q query) {
          return underlying.insertionPointFor(keyFunction.apply(query));
        }
        @Override public InsertionPoint<C> insertionPointBefore(@Nullable Q query) {
          return underlying.insertionPointBefore(keyFunction.apply(query));
        }
        @Override public InsertionPoint<C> insertionPointAfter(@Nullable Q query) {
          return underlying.insertionPointAfter(keyFunction.apply(query));
        }
      };
    }

    Table() {}
  }

  /** Represents the search target that can be found through bisecting the integer domain. */
  @FunctionalInterface
  public interface IntSearchTarget {
    /**
     * Given a range of {@code [low..high]} inclusively with {@code mid} as the
     * middle point of the binary search, locates the target.
     *
     * <p>
     * Returns 0 if {@code mid} is the target; negative to find it in the lower
     * range of {@code [low..mid)}; or positive to find it in the upper range of
     * {@code (mid..high]}.
     *
     * <p>
     * It's guaranteed that {@code low <= mid <= high}.
     */
    int locate(int low, int mid, int high);
  }

  /** Represents the search target that can be found through bisecting the long integer domain. */
  @FunctionalInterface
  public interface LongSearchTarget {
    /**
     * Given a range of {@code [low..high]} inclusively with {@code mid} as the
     * middle point of the binary search, locates the target.
     *
     * <p>
     * Returns 0 if {@code mid} is the target; negative to find it in the lower
     * range of {@code [low..mid)}; or positive to find it in the upper range of
     * {@code (mid..high]}.
     *
     * <p>
     * It's guaranteed that {@code low <= mid <= high}.
     */
    int locate(long low, long mid, long high);
  }

  /** Represents the search target that can be found through bisecting the double domain. */
  @FunctionalInterface
  public interface DoubleSearchTarget {
    /**
     * Given a range of {@code [low..high]} inclusively with {@code median} as the
     * mid point of the binary search, locates the target.
     *
     * <p>Returns 0 if {@code median} is the target; negative to find it in the lower
     * range of {@code [low..median)}; or positive to find it in the upper range of
     * {@code (median..high]}.
     *
     * <p>The {@code (low, median, high)} parameters are finite values.
     * And it's guaranteed that {@code low <= median <= high}.
     *
     * <p>Different from {@link IntSearchTarget} and {@link LongSearchTarget}, the
     * {@code (low, median, high)} parameters don't necessarily split the range in half.
     * Instead, {@code median} is picked such that the number of distinct double precision
     * values in the lower subrange is close to that of the higher subrange.
     */
    int locate(double low, double median, double high);
  }

  private static Table<IntSearchTarget, Integer> inRangeInclusive(int from, int to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new Table<IntSearchTarget, Integer>() {
      @Override public InsertionPoint<Integer> insertionPointFor(IntSearchTarget target) {
        checkNotNull(target);
        for (int low = from, high = to; ;) {
          int mid = IntMath.mean(low, high);
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

  private static Table<LongSearchTarget, Long> inRangeInclusive(long from, long to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new Table<LongSearchTarget, Long>() {
      @Override public InsertionPoint<Long> insertionPointFor(LongSearchTarget target) {
        checkNotNull(target);
        for (long low = from, high = to; ;) {
          long mid = LongMath.mean(low, high);
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

  private static Table<DoubleSearchTarget, Double> inRangeInclusive(
      final double from, final double to) {
    if (from > to) {
      return always(InsertionPoint.before(from));
    }
    return new Table<DoubleSearchTarget, Double>() {
      @Override public InsertionPoint<Double> insertionPointFor(DoubleSearchTarget target) {
        checkNotNull(target);
        double floor = Double.NEGATIVE_INFINITY;
        double ceiling = Double.POSITIVE_INFINITY;
        for (double low = from, high = to; low <= high; ) {
          double mid = median(low, high);
          int where = target.locate(low, mid, high);
          if (where > 0) {
            low = Math.nextUp(mid);
            floor = mid;
          } else if (where < 0) {
            high = Math.nextDown(mid);
            ceiling = mid;
          } else {
            return InsertionPoint.at(mid);
          }
        }
        return InsertionPoint.between(floor, ceiling);
      }
      @Override public InsertionPoint<Double> insertionPointBefore(DoubleSearchTarget target) {
        return insertionPointFor(before(target));
      }
      @Override public InsertionPoint<Double> insertionPointAfter(DoubleSearchTarget target) {
        return insertionPointFor(after(target));
      }
    };
  }

  static double median(double low, double high) {
    // use doubleToRawLongBits() because we've already checked that low/high cannot be NaN.
    long lowBits = doubleToRawLongBits(low);
    long highBits = doubleToRawLongBits(high);
    return lowBits >= 0 == highBits >= 0
        ? longBitsToDouble(LongMath.mean(lowBits, highBits))
        : 0;
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

  private static <Q, R extends Comparable<R>> Table<Q, R> always(InsertionPoint<R> point) {
    return new Table<Q, R>() {
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

  private BinarySearch() {}
}

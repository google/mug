package com.google.mu.util;

import static com.google.mu.util.Optionals.optional;
import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * Some basic and common algoriths.
 *
 * @since 6.4
 */
public final class Algorithms {
  /**
   * Binary searches within the range of {@code [low, high]) inclusively using {@code mapper} to evaluate
   * each probe, and to dictate which half of the sub-range to further search for the target.
   *
   * <p>The {@code mapper} function is called with each probed mid point value. If the mid point value
   * matches the target, 0 is returned; if the target could be found in the left side sub-range, -1 is
   * returned; otherwise 1 is returned. If you use a comparator or static methods like {@link
   * Integer#compare}, be sure to pass the target value as the first parameter such as {@code
   * Integer.compare(target, arr[mid])} or else binary search may return unexpected result.
   *
   * <p>If target is found, returns the matching integer; otherwise returns empty.
   *
   * <p>Prefer using {@link java.util.Arrays#binarySearch} and {@link java.util.Collections#binarySearch}
   * when possible. In fact, {@code Arrays.binarySearch(arr, target)} could have been implemented as:
   * <pre>{@code
   * return Algorithms.binarySearch(
   *     0, arr.length - 1,
   *     index -> Integer.compare(target, arr[index]));
   * }</pre>
   *
   * <p>While the common use cases of binary search is to search in sorted arrays and lists,
   * there are diverse context where the algorithm is also applicable (think of the Guess the
   * Number game). As a more realistic example, you can binary search a circular, otherwise
   * strictly-ordered array, using the following code:
   * <pre>{@code
   *
   * Optional<Integer> binarySearchRotated(int[] rotated, int target) {
   *   return binarySearch(0, rotated.length - 1, (low, mid, high) -> {
   *     int probe = rotated[mid];
   *     if (target < probe) {
   *       return rotated[low] <= probe && target < rotated[low] ? 1 : -1;
   *     } else if (target > probe) {
   *        return probe <= rotated[high] && target > rotated[high] ? -1 : 1;
   *     } else {
   *       return 0;
   *     }
   *   });
   * }
   * }</pre>
   */
  public static Optional<Integer> binarySearch(int low, int high, IntUnaryOperator mapper) {
    requireNonNull(mapper);
    return binarySearch(low, high, (l, mid, h) -> mapper.applyAsInt(mid));

  }

  /**
   * Binary searches within the range of {@code [low, high]) inclusively using {@code mapper} to evaluate
   * each sub-range and mid-point, and to dictate which half of the sub-range to further search for
   * the target.
   *
   * <p>If target is found, returns the matching integer; otherwise returns empty.
   *
   * <p>Prefer using {@link java.util.Arrays#binarySearch} and {@link java.util.Collections#binarySearch}
   * when possible.
   *
   * <p>While the common use cases of binary search is to search in sorted arrays and lists,
   * there are diverse context where the algorithm is also applicable (think of the Guess the
   * Number game). As a more realistic example, you can binary search a rotated, otherwise
   * strictly-ordered array, using the following code:
   * <pre>{@code
   *
   * Optional<Integer> binarySearchRotated(int[] rotated, int target) {
   *   return Algorithms.binarySearch(0, rotated.length - 1, (low, mid, high) -> {
   *     int probe = rotated[mid];
   *     if (target < probe) {
   *       return rotated[low] <= probe && target < rotated[low] ? 1 : -1;
   *     } else if (target > probe) {
   *        return probe <= rotated[high] && target > rotated[high] ? -1 : 1;
   *     } else {
   *       return 0;
   *     }
   *   });
   * }
   * }</pre>
   */
  public static Optional<Integer> binarySearch(int low, int high, BinarySearchProber mapper) {
    double index = binarySearchInsertionPoint(low, high, mapper);
    return optional(Math.rint(index) == index, (int) index);
  }

  /**
   * Binary searches within the range of {@code [low, high]) inclusively using {@code mapper} to evaluate
   * each sub-range and mid-point, and to dictate which half of the sub-range to further search for
   * the target.
   *
   * <p>If target is found, returns the matching integer; otherwise returns the insertion point,
   * which is the {@code .5} floating point number between the two most close numbers. For example,
   * the following code:
   *
   * <pre>{@code
   * int[] arr = {2, 4, 6, 8, 10};
   * binarySearchInsertionPoint(
   *     0, arr.length - 1,
   *     (low, mid, high) -> Integer.compare(5, arr[mid]));
   * }</pre>
   *
   * will return 1.5, pointing to the index after {@code 4} and before {@code 6}.
   *
   * <p>You can use {@code Math.rint(index) == index} to check if the returned value is an insertion
   * point or a found index. If you don't need the insertion point, use {@link #binarySearch} instead.
   *
   * <p>In a sorted array with potentially duplicate methods, the following code can find the index range
   *  of potentially duplicate elements matching the target:
   *  <pre>{@code
   *  int from = (int) Math.ceil(
   *      binarySearchInsertionPoint(
   *          0, arr.length - 1, (low, mid, high) -> target <= arr[mid] ? -1 : 1));
   *  int to = (int) Math.floor(
   *      binarySearchInsertionPoint(
   *          0, arr.length - 1, (low, mid, high) -> target < arr[mid] ? -1 : 1));
   *  }</pre>
   *
   * <p>For another example, imagine in a Google Doc page, if you have two columns of texts to be rendered into a two-column
   * table, and you want to split the two columns as evenly as possible such that it takes the fewest
   * number of lines overall. you can implement it with binary search:
   * <pre>{@code
   * double optimal = binarySearchInsertionPoint(
   *     1, tableWidth - 1, (low, w, high) -> {
   *       int lines1 = renderWithColumnWidth(text1, w);
   *       int lines2 = renderWithColumnWidth(text2, tableWidth - w);
   *       if (lines1 == lines2) return 0;
   *       return lines1 > lines2 ? -1 : 1;
   *     }).get();
   * if (Math.rint(optimal) == optimal) {
   *   // We have a perfect split.
   *   return (int) optimal;
   * } else {
   *   // No perfect split, compare between ceiling and floor.
   *   int floor = (int) Math.floor(optimal);
   *   int ceiling = (int) Math.ceil(optimal);
   *   int lines1 =
   *       max(renderWithColumnWidth(text1, floor), renderWithColumnWidth(text2, tableWidth - floor));
   *   int lines2 =
   *       max(renderWithColumnWidth(text1, ceiling), renderWithColumnWidth(text2, tableWidth - ceiling));
   *   return lines1 < lines2 ? floor : ceiling;
   * }
   * }</pre>
   */
  public static double binarySearchInsertionPoint(int low, int high, BinarySearchProber mapper) {
    requireNonNull(mapper);
    if (low > high) {
      return low - 0.5;
    }
    for (; ;) {
      int mid = safeMid(low, high);
      int where = mapper.map(low, mid, high);
      if (where > 0) {
        if (mid == high) {  // mid is the floor
          return mid + 0.5;
        }
        low = mid + 1;
      } else if (where < 0) {
        if (mid == low) {  // mid is the ceiling
          return mid - 0.5;
        }
        high = mid - 1;
      } else {
        return mid;
      }
    }
  }

  /** Callback upon each binary search probe to locate the search target. */
  public interface BinarySearchProber {
    /**
     * Given a range of {@code [low, high]} inclusively with {@code mid} as the middle point of
     * the binary search, maps out the location of the target.
     *
     * <p>Returns 0 if {@code mid} is the target; -1 to find it in the lower range of
     * {@code [low, mid)}; or 1 to find it in the upper range of {@code (mid, high]}.
     *
     * <p>It's guaranteed that {@code low <= mid <= high}.
     */
    int map(int low, int mid, int high);
  }

  private static int safeMid(int low, int high) {
    return (int) (((long) low + high) / 2);
  }

  private Algorithms() {}
}

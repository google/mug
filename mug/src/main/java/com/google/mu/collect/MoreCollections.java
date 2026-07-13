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
package com.google.mu.collect;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.google.mu.function.MapFrom3;
import com.google.mu.function.MapFrom4;
import com.google.mu.function.MapFrom5;
import com.google.mu.function.MapFrom6;
import com.google.mu.function.MapFrom7;
import com.google.mu.function.MapFrom8;

/**
 * Utilities pertaining to {@link Collection}.
 *
 * @since 8.0
 */
public final class MoreCollections {
  /**
   * If {@code collection} has at least two elements, passes the first two elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, BiFunction<? super T, ? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 2) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 3 elements, passes the first 3 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom3<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 3) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 4 elements, passes the first 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom4<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 4) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 5 elements, passes the first 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom5<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 5) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 6 elements, passes the first 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom6<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 6) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 7 elements, passes the first 6 elements to {@code found}
   * function and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   * @since 7.2
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom7<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 7) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(
          found.map(
              list.get(0),
              list.get(1),
              list.get(2),
              list.get(3),
              list.get(4),
              list.get(5),
              list.get(6)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(
        found.map(it.next(), it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 8 elements, passes the first 6 elements to {@code found}
   * function and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   * @since 7.2
   */
  public static <T, R> Optional<R> findFirstElements(
      Collection<T> collection, MapFrom8<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 8) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(
          found.map(
              list.get(0),
              list.get(1),
              list.get(2),
              list.get(3),
              list.get(4),
              list.get(5),
              list.get(6),
              list.get(7)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(
        found.map(
            it.next(), it.next(), it.next(), it.next(), it.next(), it.next(), it.next(),
            it.next()));
  }

  /**
   * If {@code collection} has exactly two elements, passes the two elements to {@code found}
   * function and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, BiFunction<? super T, ? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 2) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 3 elements, passes the 3 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom3<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 3) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 4 elements, passes the 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom4<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 4) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 5 elements, passes the 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom5<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 5) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 6 elements, passes the 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom6<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 6) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.map(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.map(it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 7 elements, passes the 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   * @since 7.2
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom7<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 7) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(
          found.map(
              list.get(0),
              list.get(1),
              list.get(2),
              list.get(3),
              list.get(4),
              list.get(5),
              list.get(6)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(
        found.map(it.next(), it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 8 elements, passes the 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   * @since 7.2
   */
  public static <T, R> Optional<R> findOnlyElements(
      Collection<T> collection, MapFrom8<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 8) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(
          found.map(
              list.get(0),
              list.get(1),
              list.get(2),
              list.get(3),
              list.get(4),
              list.get(5),
              list.get(6),
              list.get(7)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(
        found.map(
            it.next(), it.next(), it.next(), it.next(), it.next(), it.next(), it.next(),
            it.next()));
  }

  /**
   * Returns a list containing the elements of the given list that match the given predicate.
   *
   * <p>This method optimizes for small lists: Java stream performs well for medium and large
   * lists but for small lists (in reality, lists with {@code size() <= 64} happen pretty frequently),
   * the streaming overhead often dominates the cost of {@code smallList.filter(...).toList()}. So
   * if you have a small list to filter, consider using this method to significantly optimize for
   * the common case.
   *
   * <ul>
   *   <li>For empty lists (n = 0), returns the original list with zero allocation.
   *   <li>For size {@code <= 64}, if all elements match, returns the original list directly (zero allocation).
   *   <li>If only one element matches (or none match), returns a singleton list or empty list (extremely low allocation).
   *   <li>If only some elements match, returns an unmodifiable list constructed without stream overhead.
   * </ul>
   *
   * <p>Benchmark results (JVM: JDK 24.0.1, Throughput in ops/sec):
   *
   * <pre>{@code
   *   Size | Match Rate | MoreCollections.filter | stream().toList() | Speedup
   *   -----+------------+------------------------+-------------------+--------
   *      0 |       0%   |         1,806,612,304  |       59,190,909  |  30.5x
   *      1 |       0%   |           566,995,008  |       54,823,907  |  10.3x
   *      1 |     100%   |           495,301,391  |       39,685,261  |  12.5x
   *      2 |       0%   |           505,644,734  |       53,630,247  |   9.4x
   *      2 |      50%   |           283,244,290  |       39,977,345  |   7.1x
   *      2 |     100%   |           442,565,514  |       39,739,454  |  11.1x
   *      3 |       0%   |           387,272,751  |       51,906,625  |   7.5x
   *      3 |      67%   |           109,216,394  |       39,212,813  |   2.8x
   *      3 |     100%   |           360,620,020  |       38,529,116  |   9.4x
   *      5 |      60%   |            90,403,444  |       37,834,798  |   2.4x
   *      5 |     100%   |           302,455,379  |       35,991,628  |   8.4x
   *     10 |      50%   |            67,698,128  |       32,944,167  |   2.1x
   *     10 |     100%   |           214,023,158  |       31,440,482  |   6.8x
   *     32 |      25%   |            32,202,952  |       21,422,676  |   1.5x
   *     32 |      50%   |            24,473,961  |       20,212,588  |   1.2x
   *     32 |     100%   |            95,754,817  |        7,102,417  |  13.5x
   *     64 |      25%   |            17,305,317  |       13,270,806  |   1.3x
   *     64 |      50%   |            10,408,078  |        5,498,968  |   1.9x
   *     64 |     100%   |            53,600,935  |        3,990,547  |  13.4x
   *   -----+------------+------------------------+-------------------+--------
   *     70 |      25%   |             9,052,802  |        8,118,966  |  1.11x
   *     70 |      50%   |             6,929,459  |        3,924,459  |  1.76x
   *     70 |     100%   |             4,994,682  |        3,949,838  |  1.26x
   *     80 |      25%   |             8,485,679  |        8,046,057  |  1.05x
   *     80 |      50%   |             6,346,297  |        3,569,342  |  1.77x
   *     80 |     100%   |             4,481,216  |        3,506,878  |  1.27x
   *    100 |      25%   |             6,868,235  |        7,036,688  |  0.98x
   *    100 |      50%   |             5,238,215  |        3,114,895  |  1.68x
   *    100 |     100%   |             3,449,274  |        2,909,303  |  1.18x
   * }</pre>
   *
   * <p><strong>Note:</strong> You should almost always pass immutable {@code list} as the parameter
   * because for max efficiency this method will attempt to return the {@code list} instance as is
   * when all elements match the predicate, making it an <em>accidental</em> "view";
   * but if you later change {@code list}'s content, it may break expectations from your code
   * that all elements in the {@code filter()}'ed list shall match the {@code predicate}.
   *
   * @since 10.7
   */
  public static <T> List<T> filter(List<T> list, Predicate<? super T> predicate) {
    requireNonNull(predicate);
    int size = list.size();
    if (size == 0)  return list;
    if (size <= 64 && list instanceof RandomAccess) {
      long mask = 0L;
      for (int i = 0; i < size; i++) {
        if (predicate.test(list.get(i))) {
          mask |= (1L << i);
        }
      }
      if (mask == 0L) return emptyList();
      final int matchCount = Long.bitCount(mask);
      if (matchCount == size) return list;
      if (matchCount == 1) {
        return singletonList(list.get(Long.numberOfTrailingZeros(mask)));
      }
      Object[] result = new Object[matchCount];
      long m = mask;
      for (int i = 0; i < matchCount; i++) {
        int index = Long.numberOfTrailingZeros(m);
        result[i] = list.get(index);
        m &= ~(1L << index);
      }
      @SuppressWarnings("unchecked")
      List<T> wrapped = (List<T>) asList(result);
      return unmodifiableList(wrapped);
    }
    List<T> buffer = new ArrayList<>(size);
    list.stream().filter(predicate).forEach(buffer::add);
    if (buffer.size() == size) return list;
    @SuppressWarnings("unchecked")
    List<T> result = (List<T>) asList(buffer.toArray());
    return unmodifiableList(result);
  }

  private MoreCollections() {}
}

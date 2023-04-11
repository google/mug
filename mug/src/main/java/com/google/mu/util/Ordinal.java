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

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This class provides type-safe transition between 1-based Ordinal and 0-based indexes that are
 * commonly used to index arrays and lists. This is useful especially to translate between
 * end-user friendly numbers and machine-friendly index numbers, like for example, to report error
 * messages.
 *
 * <p>Users should immediately wrap 1-based numbers as {@code Ordinal} instances to take advantage
 * of the static type safety, to avoid 1-off errors and to use the extra utilities in this class.
 *
 * <p>Small ordinal numbers are pre-cached to avoid incurring allocation cost.
 *
 * @since 4.6
 */
public final class Ordinal implements Comparable<Ordinal> {
  private static final Ordinal[] FIRST = IntStream.iterate(1, n -> n + 1)
      .limit(100)
      .mapToObj(Ordinal::new)
      .toArray(Ordinal[]::new);

  /**
   * The maximum ordinal.
   *
   * @since 6.7
   */
  public static final Ordinal MAX_VALUE = of(Integer.MAX_VALUE);

  private final int num;

  private Ordinal(int num) {
    if (num <= 0) throw new IllegalArgumentException(num + " <= 0");
    this.num = num;
  }

  /** Returns the first ordinal. */
  public static Ordinal first() {
    return FIRST[0];
  }

  /**
   * Returns the second ordinal.
   *
   * @since 6.7
   */
  public static Ordinal second() {
    return FIRST[1];
  }

  /** Returns the infinite stream of natural ordinals starting from "1st". */
  public static Stream<Ordinal> natural() {
    return Stream.iterate(first(), Ordinal::next);
  }

  /**
   * Returns instance corresponding to the {@code oneBased} number.
   * Small integer numbers in the range of {@code [1, 100]} are cached.
   *
   * @throws IllegalArgumentException if {@code num} is not positive.
   */
  public static Ordinal of(int oneBased) {
    return oneBased > 0 && oneBased <= FIRST.length ? FIRST[oneBased - 1] : new Ordinal(oneBased);
  }

  /**
   * Returns instance corresponding to the ordinal of the Enum object {@code e}.
   *
   * <p>Note that given {@link Enum#ordinal} is 0-based, an enum with {@code ordinal() == 0}
   * maps to {@link #first}, or {@code of(1)}.
   *
   * @since 6.7
   */
  public static Ordinal of(Enum<?> e) {
    return fromIndex(e.ordinal());
  }

  /**
   * Returns instance corresponding to the {@code zeroBased} index. That is:
   * index {@code 0} corresponds to {@code "1st"} and index {@code 1} for {@code "2nd"} etc.
   *
   * @throws IllegalArgumentException if {@code num} is negative.
   */
  public static Ordinal fromIndex(int zeroBased) {
    return of(zeroBased + 1);
  }

  /**
   * Returns the 0-based index, such that {@code "1st"} will map to 0, thus can be used to
   * read and write elements in arrays and lists.
   */
  public int toIndex() {
    return num - 1;
  }

  /** Returns the next ordinal. Overflows to {@link #first}. */
  public Ordinal next() {
    return num == Integer.MAX_VALUE ? first() : of(num + 1);
  }

  /**
   * Returns the previous ordinal. Underflows to MAX_VALUE.
   *
   * @since 6.7
   */
  public Ordinal previous() {
    return num == 1 ? MAX_VALUE : of(num - 1);
  }


  /**
   * Returns the distance between {@code this} and {@code that}.
   *
   * <p>Some examples:
   * <pre>{@code
   *   1st.minus(2nd) => -1
   *   5th.minus(2nd) -> 3
   * }</pre>
   *
   * @since 6.7
   */
  public int minus(Ordinal that) {
    return num - that.num;
  }

  /** Compares to {@code that} according to natural order. */
  @Override public int compareTo(Ordinal that) {
    return Integer.compare(num, that.num);
  }

  @Override public int hashCode() {
    return num;
  }

  @Override public boolean equals(Object obj) {
    if (obj instanceof Ordinal) {
      return num == ((Ordinal) obj).num;
    }
    return false;
  }

  /**
   * Returns the string representation of this ordinal. For example,
   * {@code Ordinal.of(1).toString()} returns "1st".
   */
  @Override public String toString() {
    switch (num % 100) {
      case 11:
      case 12:
      case 13:
        return num + "th";
      default:
        switch (num % 10) {
          case 1:
            return num + "st";
          case 2:
            return num + "nd";
          case 3:
            return num + "rd";
          default:
            return num + "th";
        }
      }
  }
}

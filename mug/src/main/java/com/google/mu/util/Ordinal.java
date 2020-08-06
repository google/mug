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
 * An {@code Ordinal} represents a 1-based natural number, used in natural language such that the
 * first element is called {@code "1st"} and the second called {@code "2nd"} etc.
 *
 * <p>This class provides type-safe transition between 1-based Ordinal and 0-based indexes that are
 * commonly used to index arrays and lists. This is useful especially for translating between
 * end-user friendly numbers and machine-native index numbers, like for example, to report error
 * messages.
 *
 * @since 4.6
 */
public final class Ordinal implements Comparable<Ordinal> {
  private static final Ordinal[] INTERNED = IntStream.iterate(1, n -> n + 1)
      .limit(128)
      .mapToObj(Ordinal::new)
      .toArray(Ordinal[]::new);

  private final int num;
  
  private Ordinal(int num) {
    this.num = num;
  }
  
  /**
   * Returns instance corresponding to {@code num}. Small integer numbers in the range of {@code [1, 128]}
   * are cached.
   *
   * @throws IllegalArgumentException if {@code num} is not positive.
   */
  public static Ordinal of(int num) {
    if (num <= 0) throw new IllegalArgumentException(num + " <= 0");
    return num > 0 && num <= INTERNED.length ? INTERNED[num - 1] : new Ordinal(num);
  }
  
  /**
   * Returns instance corresponding to the 0-based {@code index}. That is:
   * index {@code 0} corresponds to {@code "1st"} and index {@code 1} for {@code "2nd"} etc.
   *
   * @throws IllegalArgumentException if {@code num} is negative.
   */
  public static Ordinal fromIndex(int index) {
    return of(index + 1);
  }

  /** Returns the infinite stream of natural ordinals starting from "1st". */
  public static Stream<Ordinal> natural() {
    return IntStream.iterate(1, n -> n + 1).mapToObj(Ordinal::of);
  }

  /**
   * Returns the 0-based index, such that {@code "1st"} will map to 0, thus can be used to
   * read and write elements in arrays and lists.
   */
  public int toIndex() {
    return num - 1;
  }

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

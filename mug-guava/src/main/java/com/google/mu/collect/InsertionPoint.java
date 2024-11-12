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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.mu.util.Optionals.optional;
import static java.util.Comparator.naturalOrder;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.annotations.RequiresGuava;

/**
 * Represents a result of {@link BinarySearch}. An insertion point in a sequence of elements
 * (normally of indexes) is either an exact element, or in between two adjacent elements
 * in a discrete domain, or before or after all elements of the discrete domain.
 *
 * <p>If representing an exact point, {@link #exact}, {@link #floor()} and {@link #ceiling()} all
 * return the element.
 *
 * <p>If it's between two elements, {@link #exact} will return empty, and {@link #floor()} and {@link
 * #ceiling()} will return the two adjacent elements respectively.
 *
 * <p>If it's below all possible elements, {@link #isBelowAll} will return true and {@link #floor()}
 * will throw. The {@link #ceiling()} method will return the min element of the domain (if present).
 *
 * <p>If it's above all possible elements, {@link #isAboveAll} will return true and {@link #ceiling()}
 * will throw. The {@link #floor()} method will return the max element of the domain (if present).
 *
 * <p>Over a discrete domain with N discrete elements, there are {@code 2 * N + 1} distinct insertion
 * points, including all the elements, the points between each two adjacent elements, the point before
 * all elements and the point after all elements.
 *
 * @param <C> the domain type
 * @since 8.0
 */
@RequiresGuava
@Immutable(containerOf = "C")
@CheckReturnValue
public final class InsertionPoint<C extends Comparable<C>> implements Comparable<InsertionPoint<C>> {
  @SuppressWarnings("unchecked") // Curiously recursive generics doesn't play nicely with wildcard.
  private static final Comparator<Comparable<?>> NULL_FIRST = (Comparator<Comparable<?>>) Comparator
      .nullsFirst(naturalOrder());

  @SuppressWarnings("unchecked") // Curiously recursive generics doesn't play nicely with wildcard.
  private static final Comparator<Comparable<?>> NULL_LAST = (Comparator<Comparable<?>>) Comparator
      .nullsLast(naturalOrder());

  private final @Nullable C floor;
  private final @Nullable C ceiling;

  private InsertionPoint(@Nullable C floor, @Nullable C ceiling) {
    this.floor = floor;
    this.ceiling = ceiling;
  }

  /**
   * Returns an insertion point exactly at {@code element} such that {@link #exact},
   * {@link #floor} and {@link #ceiling} all return the same element.
   */
  public static <C extends Comparable<C>> InsertionPoint<C> at(C element) {
    checkNotNull(element);
    return new InsertionPoint<>(element, element);
  }

  /**
   * Returns an insertion point immediately before the given {@code ceiling} and
   * after the previous integer (if {@code ceiling} isn't
   * {@link Integer#MIN_VALUE}).
   */
  public static InsertionPoint<Integer> before(int ceiling) {
    return before(ceiling, DiscreteDomain.integers());
  }

  /**
   * Returns an insertion point immediately before the given {@code ceiling} and
   * after the previous integer (if {@code ceiling} isn't
   * {@link Long#MIN_VALUE}).
   */
  public static InsertionPoint<Long> before(long ceiling) {
    return before(ceiling, DiscreteDomain.longs());
  }

  /** Returns an insertion point immediately before {@code ceiling}. */
  public static InsertionPoint<Double> before(double ceiling) {
    checkArgument(
        ceiling != Double.NEGATIVE_INFINITY, "before(NEGATIVE_INFINITY) not supported.");
    return new InsertionPoint<>(Math.nextDown(ceiling), normalize(ceiling));
  }

  /**
   * Returns an insertion point immediately before the given {@code ceiling} and
   * after the previous element in the given discrete {@code domain} (if a previous
   * element exists).
   */
  public static <C extends Comparable<C>> InsertionPoint<C> before(
      C ceiling, DiscreteDomain<C> domain) {
    return new InsertionPoint<>(domain.previous(ceiling), ceiling);
  }

  /**
   * Returns an insertion point immediately after the given {@code floor} and
   * before the next integer (if {@code ceiling} isn't {@link Integer#MAX_VALUE}).
   */
  public static InsertionPoint<Integer> after(int floor) {
    return after(floor, DiscreteDomain.integers());
  }

  /**
   * Returns an insertion point immediately after the given {@code floor} and
   * before the next integer (if {@code ceiling} isn't {@link Long#MAX_VALUE}).
   */
  public static InsertionPoint<Long> after(long floor) {
    return after(floor, DiscreteDomain.longs());
  }

  /** Returns an insertion point immediately after {@code floor}. */
  public static InsertionPoint<Double> after(double floor) {
    checkArgument(
        floor != Double.POSITIVE_INFINITY, "after(POSITIVE_INFINITY) not supported.");
    return new InsertionPoint<>(normalize(floor), Math.nextUp(floor));
  }

  /**
   * Returns an insertion point immediately after the given {@code floor} and
   * before the next element in the given discrete {@code domain} (if a next element
   * exists).
   */
  public static <C extends Comparable<C>> InsertionPoint<C> after(
      C floor, DiscreteDomain<C> domain) {
    return new InsertionPoint<>(floor, domain.next(floor));
  }

  /** Returns an insertion point in the open range of {@code (floor, ceiling)}. */
  public static InsertionPoint<Double> between(double floor, double ceiling) {
    checkArgument(floor < ceiling, "Not true that floor(%s) < ceiling(%s)", floor, ceiling);
    return new InsertionPoint<>(normalize(floor), normalize(ceiling));
  }

  /**
   * If this represents an exact point (not between two adjacent values), returns
   * the element at the point; else returns empty.
   */
  public Optional<C> exact() {
    // floor == ceiling is safe because at() always uses the same reference
    // for floor and ceiling.
    // floor and ceiling will never both be nulls.
    return optional(floor == ceiling, floor);
  }

  /**
   * Returns the floor element such that this insertion point is immediately
   * {@code >=} the floor.
   *
   * @throws NoSuchElementException if this represents a point below all possible
   *                                values in the discrete domain. Users can use
   *                                {@link #isBelowAll} to guard this condition.
   */
  public C floor() {
    if (floor == null) {
      throw new NoSuchElementException("InsertionPoint " + this + " has no floor");
    }
    return floor;
  }

  /**
   * Returns the ceiling element such that this insertion point is immediately
   * {@code <=} the ceiling.
   *
   * @throws NoSuchElementException if this represents a point above all possible
   *                                values in the discrete domain. Users can use
   *                                {@link #isAboveAll} to guard this condition.
   */
  public C ceiling() {
    if (ceiling == null) {
      throw new NoSuchElementException("InsertionPoint " + this + " has no ceiling");
    }
    return ceiling;
  }

  /**
   * Returns true if this is a point above the max possible element in the domain.
   */
  public boolean isAboveAll() {
    return ceiling == null;
  }

  /**
   * Returns true if this is a point below the min possible element in the domain.
   */
  public boolean isBelowAll() {
    return floor == null;
  }

  @Override
  public int compareTo(InsertionPoint<C> that) {
    return ComparisonChain.start().compare(floor, that.floor, NULL_FIRST).compare(ceiling, that.ceiling, NULL_LAST)
        .result();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(floor) * 31 + Objects.hashCode(ceiling);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof InsertionPoint) {
      InsertionPoint<?> that = ((InsertionPoint<?>) obj);
      return Objects.equals(floor, that.floor) && Objects.equals(ceiling, that.ceiling);
    }
    return false;
  }

  /** Returns a human-readable string representation of this insertion point. */
  @Override
  public String toString() {
    if (floor == null) {
      return Range.lessThan(ceiling).toString();
    }
    if (ceiling == null) {
      return Range.greaterThan(floor).toString();
    }
    return exact().map(Object::toString).orElseGet(() -> Range.open(floor, ceiling).toString());
  }

  private static double normalize(double v) {
    checkArgument(!Double.isNaN(v), "NaN not supported");
    return v == -0.0 ? 0.0 : v;
  }
}
package com.google.mu.util;

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
import com.google.errorprone.annotations.Immutable;

/**
 * An insertion point in a sequence (normally of indexes), that's either an exact value, or in
 * between two adjacent values in a discrete domain, or before or after all values of the discrete
 * domain.
 *
 * <p>If representing an exact point, the {@link #exact} method will return the point's value, and
 * both {@link #floor} and {@link #ceiling} return the same value.
 *
 * <p>If it's between two values, {@link #exact} will return empty, and {@link #floor} and {@code
 * #ceiling} will return the two values respectively.
 *
 * <p>If it's below all possible values, {@link #isBelowAll} will return true and {@link #floor}
 * will throw. The {@link #ceiling} method will return the min value of the domain (if present).
 *
 * <p>If it's above all possible values, {@link #isAboveAll} will return true and {@link #ceiling}
 * will throw. The {@link #floor} method will return the max value of the domain (if present).
 *
 * <p>Over a discrete domain with N discrete values, there are {@code 2 * N + 1} distinct insertion
 * points, including all the values, the points between each two adjacent values, the point before
 * all values and the point after all values.
 *
 * @param <T> the domain type
 * @since 6.4
 */
@Immutable(containerOf = "T")
public final class InsertionPoint<T extends Comparable<T>> implements Comparable<InsertionPoint<T>> {
  @SuppressWarnings("unchecked") // Curiously recursive generics doesn't play nicely with wildcard.
  private static final Comparator<Comparable<?>> NULL_FIRST = (Comparator<Comparable<?>>) Comparator
      .nullsFirst(naturalOrder());

  @SuppressWarnings("unchecked") // Curiously recursive generics doesn't play nicely with wildcard.
  private static final Comparator<Comparable<?>> NULL_LAST = (Comparator<Comparable<?>>) Comparator
      .nullsLast(naturalOrder());

  private final @Nullable T floor;
  private final @Nullable T ceiling;

  private InsertionPoint(@Nullable T floor, @Nullable T ceiling) {
    this.floor = floor;
    this.ceiling = ceiling;
  }

  /**
   * Returns an insertion point exactly at {@code value} such that {@link #exact},
   * {@link #floor} and {@link #ceiling} all return the same value.
   */
  public static <T extends Comparable<T>> InsertionPoint<T> at(T value) {
    checkNotNull(value);
    return new InsertionPoint<>(value, value);
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

  /**
   * Returns an insertion point immediately before the given {@code ceiling} and
   * after the previous value in the given discrete {@code domain} (if a previous
   * value exists).
   */
  public static <T extends Comparable<T>> InsertionPoint<T> before(T ceiling, DiscreteDomain<T> domain) {
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

  /**
   * Returns an insertion point immediately after the given {@code floor} and
   * before the next value in the given discrete {@code domain} (if a next value
   * exists).
   */
  public static <T extends Comparable<T>> InsertionPoint<T> after(T floor, DiscreteDomain<T> domain) {
    return new InsertionPoint<>(floor, domain.next(floor));
  }

  /**
   * If this represents an exact point (not between two adjacent values), returns
   * the value of the point; else returns empty.
   */
  public Optional<T> exact() {
// floor == ceiling is safe because at() always uses the same reference
// for floor and ceiling.
// floor and ceiling will never both be nulls.
    return optional(floor == ceiling, floor);
  }

  /**
   * Returns the floor value such that this insertion point is immediately
   * {@code >=} the floor.
   *
   * @throws NoSuchElementException if this represents a point below all possible
   *                                values in the discrete domain. Users can use
   *                                {@link #isBelowAll} to guard this condition.
   */
  public T floor() {
    if (floor == null) {
      throw new NoSuchElementException("InsertionPoint " + this + " has no floor");
    }
    return floor;
  }

  /**
   * Returns the ceiling value such that this insertion point is immediately
   * {@code <=} the ceiling.
   *
   * @throws NoSuchElementException if this represents a point above all possible
   *                                values in the discrete domain. Users can use
   *                                {@link #isAboveAll} to guard this condition.
   */
  public T ceiling() {
    if (ceiling == null) {
      throw new NoSuchElementException("InsertionPoint " + this + " has no ceiling");
    }
    return ceiling;
  }

  /**
   * Returns true if this is a point above the max possible value in the domain.
   */
  public boolean isAboveAll() {
    return ceiling == null;
  }

  /**
   * Returns true if this is a point below the min possible value in the domain.
   */
  public boolean isBelowAll() {
    return floor == null;
  }

  @Override
  public int compareTo(InsertionPoint<T> that) {
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
}
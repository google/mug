package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.function.BiFunction;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;

/**
 * Utilities pertaining to {@link Collection}.
 *
 * @since 5.3
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
  public static <T, R> Optional<R> findFirst(
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
  public static <T, R> Optional<R> findFirst(Collection<T> collection, Ternary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 3) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 4 elements, passes the first 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(Collection<T> collection, Quarternary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 4) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 5 elements, passes the first 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(Collection<T> collection, Quinary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 5) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has at least 6 elements, passes the first 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(Collection<T> collection, Senary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() < 6) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly two elements, passes the two elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(Collection<T> collection, BiFunction<? super T, ? super T, ? extends R> found) {
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
  public static <T, R> Optional<R> findOnly(Collection<T> collection, Ternary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 3) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 4 elements, passes the 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(Collection<T> collection, Quarternary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 4) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 5 elements, passes the 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(Collection<T> collection, Quinary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 5) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  /**
   * If {@code collection} has exactly 6 elements, passes the 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code collection} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(Collection<T> collection, Senary<? super T, ? extends R> found) {
    requireNonNull(found);
    if (collection.size() != 6) return Optional.empty();
    if (collection instanceof List && collection instanceof RandomAccess) {
      List<T> list = (List<T>) collection;
      return Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)));
    }
    Iterator<T> it = collection.iterator();
    return Optional.of(found.apply(it.next(), it.next(), it.next(), it.next(), it.next(), it.next()));
  }

  private MoreCollections() {}
}

package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;

/**
 * Utilities pertaining to {@link List}.
 *
 * @since 5.3
 */
public final class MoreLists {
  /**
   * If {@code list} has at least two elements, pass the first two elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(List<T> list, BiFunction<? super T, ? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() >= 2 ? Optional.of(found.apply(list.get(0), list.get(1))) : Optional.empty();
  }

  /**
   * If {@code list} has at least 3 elements, pass the first 3 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(List<T> list, Ternary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() >= 3
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2)))
        : Optional.empty();
  }

  /**
   * If {@code list} has at least 4 elements, pass the first 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(List<T> list, Quarternary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() >= 4
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3)))
        : Optional.empty();
  }

  /**
   * If {@code list} has at least 5 elements, pass the first 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(List<T> list, Quinary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() >= 5
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)))
        : Optional.empty();
  }

  /**
   * If {@code list} has at least 6 elements, pass the first 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findFirst(List<T> list, Senary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() >= 6
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)))
        : Optional.empty();
  }
  /**
   * If {@code list} has exactly two elements, pass the first two elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(List<T> list, BiFunction<? super T, ? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() == 2 ? Optional.of(found.apply(list.get(0), list.get(1))) : Optional.empty();
  }

  /**
   * If {@code list} has exactly 3 elements, pass the first 3 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(List<T> list, Ternary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() == 3
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2)))
        : Optional.empty();
  }

  /**
   * If {@code list} has exactly 4 elements, pass the first 4 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(List<T> list, Quarternary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() == 4
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3)))
        : Optional.empty();
  }

  /**
   * If {@code list} has exactly 5 elements, pass the first 5 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(List<T> list, Quinary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() == 5
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4)))
        : Optional.empty();
  }

  /**
   * If {@code list} has exactly 6 elements, pass the first 6 elements to {@code found} function
   * and returns the non-null result wrapped in an {@link Optional}, or else returns {@code
   * Optional.empty()}.
   *
   * @throws NullPointerException if {@code list} or {@code found} function is null, or if
   *     {@code found} function returns null.
   */
  public static <T, R> Optional<R> findOnly(List<T> list, Senary<? super T, ? extends R> found) {
    requireNonNull(found);
    return list.size() == 6
        ? Optional.of(found.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)))
        : Optional.empty();
  }

  private MoreLists() {}
}

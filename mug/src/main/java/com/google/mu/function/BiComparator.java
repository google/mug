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
package com.google.mu.function;

import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;

import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Similar to {@link java.util.Comparator}, but compares between two pairs of objects. */
@FunctionalInterface
public interface BiComparator<K, V> {
  /**
   * Returns a {@code BiComparator] that first transforms the pairs using {@code function} and then
   * compares the result of {@code function} using the given {@code ordering} comparator.
   */
  static <K, V, T> BiComparator<K, V> comparing(
      BiFunction<? super K, ? super V, ? extends T> function, Comparator<? super T> ordering) {
    requireNonNull(function);
    requireNonNull(ordering);
    return (k1, v1, k2, v2) -> ordering.compare(function.apply(k1, v1), function.apply(k2, v2));
  }

  /**
   * Returns a {@code BiComparator] that first transforms the pairs using {@code function} and then
   * compares the result of {@code function}.
   */
  static <K, V, T extends Comparable<T>> BiComparator<K, V> comparing(
      BiFunction<? super K, ? super V, ? extends T> function) {
    return comparing(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that first transforms the key element of type {@code K} using
   * {@code function} and then compares the result of {@code function}.
   */
  static <K, T extends Comparable<T>> BiComparator<K, Object> comparingKey(
      Function<? super K, ? extends T> function) {
    return comparingKey(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that first transforms the key element of type {@code K} using
   * {@code function} and then compares the result of {@code function} using the given
   * {@code ordering} comparator.
   */
  static <K, T> BiComparator<K, Object> comparingKey(
      Function<? super K, ? extends T> function, Comparator<? super T> ordering) {
    return comparingKey(Comparator.comparing(function, ordering));
  }

  /**
   * Returns a {@code BiComparator] that compares the pairs by the key element of type {@code K}.
   */
  static <K> BiComparator<K, Object> comparingKey(Comparator<? super K> ordering) {
    return comparing((k, v) -> k, ordering);
  }

  /**
   * Returns a {@code BiComparator] that first transforms the value element of type {@code V} using
   * {@code function} and then compares the result of {@code function}.
   */
  static <V, T extends Comparable<T>> BiComparator<Object, V> comparingValue(
      Function<? super V, ? extends T> function) {
    return comparingValue(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that first transforms the value element of type {@code V} using
   * {@code function} and then compares the result of {@code function} using the given
   * {@code ordering} comparator.
   */
  static <V, T> BiComparator<Object, V> comparingValue(
      Function<? super V, ? extends T> function, Comparator<? super T> ordering) {
    return comparingValue(Comparator.comparing(function, ordering));
  }

  /**
   * Returns a {@code BiComparator] that compares the pairs by the value element of type {@code V}.
   */
  static <V> BiComparator<Object, V> comparingValue(Comparator<? super V> ordering) {
    return comparing((k, v) -> v, ordering);
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), transforms the pairs using {@code function} to compare the results
   * for tie-break.
   */
  default <K2 extends K, V2 extends V, T extends Comparable<T>> BiComparator<K2, V2> thenComparing(
      BiFunction<? super K2, ? super V2, ? extends T> function) {
    return thenComparing(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), first transforms the pairs using {@code function} and then
   * compares the result of {@code function} using the given {@code ordering} comparator.
   */
  default <K2 extends K, V2 extends V, T> BiComparator<K2, V2> thenComparing(
      BiFunction<? super K2, ? super V2, ? extends T> function, Comparator<? super T> ordering) {
    return thenComparing(comparing(function, ordering));
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), transforms the key elements of type {@code K} using {@code function]
   * and finally compares the transformed result for tie-break.
   */
  default <K2 extends K, T extends Comparable<T>> BiComparator<K2, V> thenComparingKey(
      Function<? super K2, ? extends T> function) {
    return thenComparingKey(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), transforms the key elements of type {@code K} using {@code function]
   * and finally delegates to the {@code secondary} comparator to break the tie.
   */
  default <K2 extends K, T> BiComparator<K2, V> thenComparingKey(
      Function<? super K2, ? extends T> function, Comparator<? super T> secondary) {
    return thenComparingKey(Comparator.comparing(function, secondary));
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), delegates the key elements of type {@code K} to the {@code secondary}
   * comparator to break the tie.
   */
  default <K2 extends K> BiComparator<K2, V> thenComparingKey(Comparator<? super K2> secondary) {
    return thenComparing((k, v) -> k, secondary);
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), transforms the value elements of type {@code V} using {@code function]
   * and finally compares the transformed result for tie-break.
   */
  default <V2 extends V, T extends Comparable<T>> BiComparator<K, V2> thenComparingValue(
      Function<? super V2, ? extends T> function) {
    return thenComparingValue(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), transforms the value elements of type {@code V} using {@code function]
   * and finally delegates to the {@code secondary} comparator to break the tie.
   */
  default <V2 extends V, T> BiComparator<K, V2> thenComparingValue(
      Function<? super V2, ? extends T> function, Comparator<? super T> secondary) {
    return thenComparingValue(Comparator.comparing(function, secondary));
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), delegates the value elements of type {@code V} to the {@code secondary}
   * comparator to break the tie.
   */
  default <V2 extends V> BiComparator<K, V2> thenComparingValue(Comparator<? super V2> secondary) {
    return thenComparing((k, v) -> v, secondary);
  }

  /**
   * Returns a {@code BiComparator] that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), delegates to the {@code secondary} BiComparator to break the tie.
   */
  default <K2 extends K, V2 extends V, T> BiComparator<K2, V2> thenComparing(
      BiComparator<? super K2, ? super V2> secondary) {
    requireNonNull(secondary);
    return (k1, v1, k2, v2) -> {
      int result = compare(k1, v1, k2, v2);
      return result == 0 ? secondary.compare(k1, v1, k2, v2) : result;
    };
  }

  /*
   * Returns negative if {@code (k1, v1) < (k2, v2)}; positive if {@code (k1, v1) > (k2, v2)};
   * otherwise 0.
   */
  int compare(K k1, V v1, K k2, V v2);

  /**
   * Returns a {@code Comparator} for type {@code E} that can be converted into a pair
   * using the given {@code toKey} and {@code toValue} functions.
   */
  default <E> Comparator<E> asComparator(
      Function<? super E, ? extends K> toKey, Function<? super E, ? extends V> toValue) {
    requireNonNull(toKey);
    requireNonNull(toValue);
    return (e1, e2) -> compare(toKey.apply(e1), toValue.apply(e1), toKey.apply(e2), toValue.apply(e2));
  }
}

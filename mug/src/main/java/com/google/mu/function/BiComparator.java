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
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToLongBiFunction;

/**
 * Similar to {@link java.util.Comparator}, but compares between two pairs of objects.
 *
 * <p>Note: For ease of reference, this interface uses 'key' and 'value' to refer to the two
 * parts of a pair respectively. However, both 'key' and 'value' can be of any type, or null.
 * There is no implication that the 'key' or 'value' must implement {@link Object#equals}.
 * You may equivalently read them as 'left' and 'right', or 'night' and 'day'.
 *
 * @since 4.7
 * @deprecated
 */
@Deprecated
@FunctionalInterface
public interface BiComparator<K, V> {
  /**
   * Returns a {@code BiComparator} that first transforms the pairs using {@code function} and then
   * compares the result of {@code function} using the given {@code ordering} comparator.
   */
  static <K, V, T> BiComparator<K, V> comparing(
      BiFunction<? super K, ? super V, ? extends T> function, Comparator<? super T> ordering) {
    requireNonNull(function);
    requireNonNull(ordering);
    return (k1, v1, k2, v2) -> ordering.compare(function.apply(k1, v1), function.apply(k2, v2));
  }

  /**
   * Returns a {@code BiComparator} that first transforms the pairs using {@code function} and then
   * compares the result of {@code function}.
   */
  static <K, V, T extends Comparable<T>> BiComparator<K, V> comparing(
      BiFunction<? super K, ? super V, ? extends T> function) {
    return comparing(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator} that compares by the int return value of {@code function}.
   *
   * @since 6.5
   */
  static <K, V> BiComparator<K, V> comparingInt(
      ToIntBiFunction<? super K, ? super V> function) {
    requireNonNull(function);
    return (k1, v1, k2, v2) -> Integer.compare(function.applyAsInt(k1, v1), function.applyAsInt(k2, v2));
  }

  /**
   * Returns a {@code BiComparator} that compares by the long return value of {@code function}.
   *
   * @since 6.5
   */
  static <K, V> BiComparator<K, V> comparingLong(
      ToLongBiFunction<? super K, ? super V> function) {
    requireNonNull(function);
    return (k1, v1, k2, v2) -> Long.compare(function.applyAsLong(k1, v1), function.applyAsLong(k2, v2));
  }

  /**
   * Returns a {@code BiComparator} that compares by the double return value of {@code function}.
   *
   * @since 6.5
   */
  static <K, V> BiComparator<K, V> comparingDouble(
      ToDoubleBiFunction<? super K, ? super V> function) {
    requireNonNull(function);
    return (k1, v1, k2, v2) -> Double.compare(function.applyAsDouble(k1, v1), function.applyAsDouble(k2, v2));
  }

  /**
   * Returns a {@code BiComparator} that first transforms the key element of type {@code K} using
   * {@code function} and then compares the result of {@code function}.
   */
  static <K, T extends Comparable<T>> BiComparator<K, Object> comparingKey(
      Function<? super K, ? extends T> function) {
    return comparingKey(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator} that first transforms the key element of type {@code K} using
   * {@code function} and then compares the result of {@code function} using the given
   * {@code ordering} comparator.
   */
  static <K, T> BiComparator<K, Object> comparingKey(
      Function<? super K, ? extends T> function, Comparator<? super T> ordering) {
    return comparingKey(Comparator.comparing(function, ordering));
  }

  /**
   * Returns a {@code BiComparator} that compares the pairs by the key element of type {@code K}.
   */
  static <K> BiComparator<K, Object> comparingKey(Comparator<? super K> ordering) {
    return comparing((k, v) -> k, ordering);
  }

  /**
   * Returns a {@code BiComparator} that first transforms the value element of type {@code V} using
   * {@code function} and then compares the result of {@code function}.
   */
  static <V, T extends Comparable<T>> BiComparator<Object, V> comparingValue(
      Function<? super V, ? extends T> function) {
    return comparingValue(function, naturalOrder());
  }

  /**
   * Returns a {@code BiComparator} that first transforms the value element of type {@code V} using
   * {@code function} and then compares the result of {@code function} using the given
   * {@code ordering} comparator.
   */
  static <V, T> BiComparator<Object, V> comparingValue(
      Function<? super V, ? extends T> function, Comparator<? super T> ordering) {
    return comparingValue(Comparator.comparing(function, ordering));
  }

  /**
   * Returns a {@code BiComparator} that compares the pairs by the value element of type {@code V}.
   */
  static <V> BiComparator<Object, V> comparingValue(Comparator<? super V> ordering) {
    return comparing((k, v) -> v, ordering);
  }

  /**
   * Returns a {@code BiComparator} that compares the input pairs using the {@code primary}
   * comparator, and then {@code secondaries} in the given order until tie is broken.
   *
   * @since 6.5
   */
  @SuppressWarnings("unchecked")
  @SafeVarargs
  static <K, V> BiComparator<K, V> comparingInOrder(
      BiComparator<? super K, ? super V> primary,
      BiComparator<? super K, ? super V>... secondaries) {
    BiComparator<K, V> comparator = (BiComparator<K, V>) requireNonNull(primary);
    for (BiComparator<? super K, ? super V> secondary : secondaries) {
      requireNonNull(secondary);
      comparator = comparator.then(secondary);
    }
    return comparator;
  }

  /**
   * Returns a {@code BiComparator} that upon comparing two pairs, if {@code this} BiComparator
   * returns 0 (tie), delegates to the {@code secondary} BiComparator for tie-break.
   */
  default <K2 extends K, V2 extends V, T> BiComparator<K2, V2> then(
      BiComparator<? super K2, ? super V2> secondary) {
    requireNonNull(secondary);
    return (k1, v1, k2, v2) -> {
      int result = compare(k1, v1, k2, v2);
      return result == 0 ? secondary.compare(k1, v1, k2, v2) : result;
    };
  }

  /**
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

  /**
   * Returns a {@code BiComparator} that reverses the order specified by this comparator.
   *
   * @since 6.0
   */
  default BiComparator<K, V> reversed() {
    BiComparator<K, V> self = this;
    return new BiComparator<K, V>() {
      @Override public int compare(K k1, V v1, K k2, V v2) {
        return self.compare(k2, v2, k1, v1);
      }
      @Override public BiComparator<K, V> reversed() {
        return self;
      }
    };
  }
}

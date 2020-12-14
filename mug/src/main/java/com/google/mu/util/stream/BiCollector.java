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
package com.google.mu.util.stream;

import java.util.function.Function;
import java.util.stream.Collector;

/**
 * Logically, a {@code BiCollector} collects "pairs of things", just as a {@link Collector} collects
 * "things".
 *
 * <p>{@code BiCollector} is usually passed to {@link BiStream#collect}. For example, to collect the
 * input pairs to a {@code ConcurrentMap}:
 *
 * <pre>{@code
 * ConcurrentMap<String, Integer> map = BiStream.of("a", 1).collect(Collectors::toConcurrentMap);
 * }</pre>
 *
 * <p>In addition to the common implementations provided by {@link BiCollectors},
 * many {@code Collector}-returning factory methods can be directly "method referenced" as {@code
 * BiCollector} if the method accepts two {@code Function} parameters corresponding to "key" and
 * "value" respectively. For example: {@code Collectors::toConcurrentMap}, {@code
 * ImmutableSetMultimap::toImmutableSetMultimap}, {@code Maps::toImmutableEnumMap} and {@code
 * ImmutableBiMap::toImmutableBiMap} etc.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @param <R> the result type
 */
@FunctionalInterface
public interface BiCollector<K, V, R> {
  /**
   * Returns a {@code Collector} that will first split the input elements using {@code toKey} and
   * {@code toValue} and subsequently collect the bisected parts through this {@code BiCollector}.
   *
   * @param toKey
   *        The function to read the key from the input entry.
   *        May be applied on the same input entry multiple times.
   *        Because input entries could be ephemeral like {@link java.util.Map.Entry},
   *        applying the function on a previous input entry has undefined result.
   * @param toValue
   *        The function to read the value from the input entry.
   *        May be applied on the same input entry multiple times.
   *        Because input entries could be ephemeral like {@link java.util.Map.Entry},
   *        applying the function on a previous input entry has undefined result.
   * @param <E> used to abstract away the underlying pair/entry type used by {@link BiStream}.
   */
  // Deliberately avoid wildcards for toKey and toValue, because we don't expect
  // users to call this method. Instead, users will typically provide method references matching
  // this signature.
  // Signatures with or without wildcards should both match.
  // In other words, this signature optimizes flexibility for implementors, not callers.
  <E> Collector<E, ?, R> splitting(Function<E, K> toKey, Function<E, V> toValue);
}

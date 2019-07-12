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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Common utilities pertaining to {@link BiCollector}.
 */

/**
 * Common utilities pertaining to {@link BiCollector}.
 *
 * <p>Don't forget that you can directly "method reference" a {@code Collector}-returning
 * factory method as a {@code BiCollector} as long as it accepts two {@code Function} parameters
 * corresponding to the "key" and the "value" parts respectively. For example: {@code
 * collect(ImmutableMap::toImmutableMap)}, {@code collect(Collectors::toConcurrentMap)}.
 *
 * @since 3.0
 */
public final class BiCollectors {

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an immutable {@link Map}.
   *
   * <p>Normally calling {@code biStream.toMap()} is more convenient but for example when you've got
   * a {@code BiStream<K, LinkedList<V>>} and need to collect it into {@code Map<K, List<V>>},
   * you'll need to call {@code collect(toMap())} instead of {@link BiStream#toMap()}.
   */
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap() {
    return Collectors::toMap;
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an immutable {@link Map}
   * using {@code valueMerger} to merge values of duplicate keys.
   */
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap(
      BinaryOperator<V> valueMerger) {
    requireNonNull(valueMerger);
    return new BiCollector<K, V, Map<K, V>>() {
      @Override
      public <E> Collector<E, ?, Map<K, V>> bisecting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.toMap(toKey, toValue, valueMerger);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an immutable {@link Map}
   * using {@code valueCollector} to collect values of identical keys into a final value of type
   * {@code V}.
   *
   * <p>For example, the following calculates total population per state from city demographic data:
   *
   * <pre>{@code
   *  Map<StateId, Integer> statePopulations = BiStream.from(cities, City::getState, c -> c)
   *     .collect(toMap(summingInt(City::getPopulation)));
   * }</pre>
   *
   * <p>Entries are collected in encounter order.
   */
  public static <K, V1, V> BiCollector<K, V1, Map<K, V>> toMap(
      Collector<V1, ?, V> valueCollector) {
    requireNonNull(valueCollector);
    return new BiCollector<K, V1, Map<K, V>>() {
      @Override
      public <E> Collector<E, ?, Map<K, V>> bisecting(
          Function<E, K> toKey, Function<E, V1> toValue) {
        return Collectors.collectingAndThen(
            Collectors.groupingBy(
                toKey, LinkedHashMap::new, Collectors.mapping(toValue, valueCollector)),
            Collections::unmodifiableMap);
      }
    };
  }

  private BiCollectors() {}
}

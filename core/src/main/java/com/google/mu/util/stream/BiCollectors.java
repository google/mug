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
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Common utilities pertaining to {@link BiCollector}.
 *
 * <p>Don't forget that you can directly "method reference" a {@code Collector}-returning
 * factory method as a {@code BiCollector} as long as it accepts two {@code Function} parameters
 * corresponding to the "key" and the "value" parts respectively. For example: {@code
 * collect(ImmutableMap::toImmutableMap)}, {@code collect(Collectors::toConcurrentMap)}.
 *
 * <p>Most of the factory methods in this class are deliberately named after their {@code Collector}
 * counterparts. This is a <em>feature</em>. Static imports can be overloaded by method arity, so
 * you already static import, for example, {@code Collectors.toMap}, simply adding {@code static import
 * com.google.mu.util.stream.BiCollectors.toMap} will allow both the {@code BiCollector} and the
 * {@code Collector} to be used in the same file without ambiguity or confusion.
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
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap(BinaryOperator<V> valueMerger) {
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
  public static <K, V1, V> BiCollector<K, V1, Map<K, V>> toMap(Collector<V1, ?, V> valueCollector) {
    requireNonNull(valueCollector);
    return new BiCollector<K, V1, Map<K, V>>() {
      @Override
      public <E> Collector<E, ?, Map<K, V>> bisecting(
          Function<E, K> toKey, Function<E, V1> toValue) {
        return Collectors.collectingAndThen(
            Collectors.groupingBy(
                toKey,
                LinkedHashMap::new, Collectors.mapping(toValue, valueCollector)),
            Collections::unmodifiableMap);
      }
    };
  }

  /**
   * Returns a counting {@link BiCollector} that counts the number of input entries.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Long> counting() {
    return mapping((k, v) -> k, Collectors.counting());
  }

  /**
   * Groups input entries by {@code classifier} and collects entries belonging to the same group
   * using {@code groupCollector}.
   *
   * <p>Useful for grouping entries by some binary relationship. For example, the following code groups
   * {@code [begin, end)} endpoints by their distance: <pre>{@code
   *   ImmutableMap<Integer, ImmtableSetMultimap<Integer, Integer>> endpointsByDistance =
   *       BiStream.from(endpoints)
   *           .collect(
   *               groupingBy((begin, end) -> end - begin, ImmutableSetMultimap::toImmutableSetMultimap))
   *           .collect(ImmutableMap::toImmutableMap);
   * }</pre>
   *
   * @since 3.2
   */
  public static <K, V, G, R> BiCollector<K, V, BiStream<G, R>> groupingBy(
      BiFunction<? super K, ? super V, ? extends G> classifier,
      BiCollector<? super K, ? super V, R> groupCollector) {
    requireNonNull(classifier);
    requireNonNull(groupCollector);
    return new BiCollector<K, V, BiStream<G, R>>() {
      @Override
      public <E> Collector<E, ?, BiStream<G, R>> bisecting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return BiStream.groupingBy(
            e -> classifier.apply(toKey.apply(e), toValue.apply(e)),
            groupCollector.bisecting(toKey::apply, toValue::apply));
      }
    };
  }

  /**
   * Groups input entries by {@code classifier} and collects entries belonging to the same group
   * using {@code groupCollector}.
   *
   * <p>For example, the following code creates a {@code row -> column -> sum} two-level map:
   * <pre>{@code
   *   Map<Coordinate, Integer> cells = ...;
   *   ImmutableMap<Integer, Map<Integer, Value>> cellValuesByRowAndColumn =
   *       BiStream.from(cells)
   *           .collect(
   *               groupingBy(Coordinate::row, toMap(Coordinate::column, summingInt(Integer::intValue)))
   *           .collect(ImmutableMap::toImmutableMap);
   * }</pre>
   *
   * @since 3.2
   */
  public static <K, V, G, R> BiCollector<K, V, BiStream<G, R>> groupingBy(
      Function<? super K, ? extends G> classifier,
      BiCollector<? super K, ? super V, R> groupCollector) {
    requireNonNull(classifier);
    return groupingBy((k, v) -> classifier.apply(k), groupCollector);
  }

  /**
   * Groups input entries by {@code classifier} and collects values belonging to the same group
   * using {@code groupCollector}.
   *
   * @since 3.2
   */
  public static <K, V, G, R> BiCollector<K, V, BiStream<G, R>> groupingBy(
      Function<? super K, ? extends G> classifier,
      Collector<? super V, ?, R> groupCollector) {
    return groupingBy(classifier, mapping((k, v) -> v, groupCollector));
  }

  /**
   * Returns a {@link BiCollector} that maps the result of {@code collector} using {@code finisher}.
   *
   * @since 3.2
   */
  public static <K, V, T, R> BiCollector<K, V, R> collectingAndThen(
      BiCollector<K, V, T> collector, Function<? super T, ? extends R> finisher) {
    requireNonNull(collector);
    requireNonNull(finisher);
    return new BiCollector<K, V, R>() {
      @Override
      public <E> Collector<E, ?, R> bisecting(Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.collectingAndThen(collector.bisecting(toKey, toValue), finisher::apply);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that first maps the input pair using {@code mapper} and then collects the
   * results using {@code collector}.
   *
   * @since 3.2
   */
  public static <K, V, T, R> BiCollector<K, V, R> mapping(
      BiFunction<? super K, ? super V, ? extends T> mapper, Collector<? super T, ?, R> collector) {
    requireNonNull(mapper);
    requireNonNull(collector);
    return new BiCollector<K, V, R>() {
      @Override public <E> Collector<E, ?, R> bisecting(Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.mapping(e -> mapper.apply(toKey.apply(e), toValue.apply(e)), collector);
      }
    };
  }

  private BiCollectors() {}
}

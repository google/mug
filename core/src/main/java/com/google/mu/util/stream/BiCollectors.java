package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;
import java.util.stream.Collectors;

/**
 * Common utilities pertaining to {@link BiCollector}.
 *
 * @since 2.3
 */
public final class BiCollectors {

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into a {@link Map}.
   *
   * <p>Entries are collected in encounter order.
   */
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap() {
    return toMap((a, b) -> {
      throw new IllegalArgumentException("Duplicate values encountered");
    });
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into a {@link Map}
   * using {@code valueMerger} to merge values of duplicate keys.
   *
   * <p>Entries are collected in encounter order.
   */
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap(BinaryOperator<V> valueMerger) {
    requireNonNull(valueMerger);
    return new BiCollector<K, V, Map<K, V>>() {
      @Override public <E> Collector<E, ?, Map<K, V>> bisecting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.collectingAndThen(
            Collectors.toMap(toKey, toValue, valueMerger, LinkedHashMap::new),
            Collections::unmodifiableMap);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableMap}
   * using {@code valueCollector} to collect values of identical keys into a final value of type
   * {@code V}.
   *
   * <p>For example, the following calculates total population per state from city demographic data:
   *
   * <pre>{@code
   * Map<StateId, Integer> statePopulations = BiStream.from(cityToDemographicDataMap)
   *     .mapKeys(City::getState)
   *     .collect(toMap(summingInt(DemographicData::getPopulation)));
   * }</pre>
   *
   * <p>Entries are collected in encounter order.
   */
  public static <K, V1, V> BiCollector<K, V1, Map<K, V>> toMap(Collector<V1, ?, V> valueCollector) {
    requireNonNull(valueCollector);
    return new BiCollector<K, V1, Map<K, V>>() {
      @Override public <E> Collector<E, ?, Map<K, V>> bisecting(
          Function<E, K> toKey, Function<E, V1> toValue) {
        return Collectors.collectingAndThen(
            Collectors.groupingBy(
                toKey, LinkedHashMap::new, Collectors.mapping(toValue, valueCollector)),
            Collections::unmodifiableMap);
      }
    };
  }

  /**
   * Returns a {@link Collector} that will flatten the map entries from the input {@code Map}s and
   * pass each key-value pair to {@code downstream} collector. For example, the following code
   * flattens each (employee, task) entry to collect the sum of task hours per employee:
   *
   * <pre>{@code
   * Map<Employee, Integer> employeeTotalTaskHours = projects.stream()
   *   .map(Project::getTaskAssignmentsMap)  // stream of Map<Employee, Task>
   *   .collect(flattening(toMap(summingInt(Task::getHours))));
   * }</pre>
   */
  public static <K, V, R> Collector<Map<K, V>, ?, R> flattening(
      BiCollector<? super K, ? super V, R> downstream) {
    return flattening(Map::entrySet, downstream);
  }

  /**
   * Returns a {@link Collector} that will flatten the map entries derived from the
   * input elements using {@code toEntries} function and then pass each key-value pair to
   * {@code downstream} collector. For example, the following code flattens each (employee, task)
   * entry to collect the sum of task hours per employee:
   *
   * <pre>{@code
   * Map<Employee, Integer> employeeTotalTaskHours = projects.stream()
   *   .map(Project::getTaskAssignmentsMultimap)  // stream of Multimap<Employee, Task>
   *   .collect(flattening(Multimap::entries, toMap(summingInt(Task::getHours))));
   * }</pre>
   */
  public static <E, K, V, R> Collector<E, ?, R> flattening(
      Function<? super E, ? extends Collection<Map.Entry<K, V>>> toEntries,
      BiCollector<? super K, ? super V, R> downstream) {
    return Collectors.flatMapping(
        toEntries.andThen(Collection::stream),
        downstream.bisecting(Map.Entry::getKey, Map.Entry::getValue));
  }

  private BiCollectors() {}
}

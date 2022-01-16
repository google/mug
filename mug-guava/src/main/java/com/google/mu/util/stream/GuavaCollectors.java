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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.mu.util.stream.MoreCollectors.mapping;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.Comparator;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Tables;
import com.google.mu.util.Both;

/**
 * Guava-specific Collectors and BiCollectors.
 *
 * @since 4.7
 */
public final class GuavaCollectors {
  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableMap}.
   *
   * <p>Normally calling {@code biStream.toMap()} is more convenient, but for example when you've
   * got a {@code BiStream<K, LinkedList<V>>}, and need to collect it into {@code ImmutableMap<K,
   * List<V>>}, you'll need to call {@code collect(toImmutableMap())} instead of {@code toMap()}.
   */
  public static <K, V> BiCollector<K, V, ImmutableMap<K, V>> toImmutableMap() {
    return ImmutableMap::toImmutableMap;
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableMap}
   * using {@code valueMerger} to merge values of duplicate keys.
   */
  public static <K, V> BiCollector<K, V, ImmutableMap<K, V>> toImmutableMap(
      BinaryOperator<V> valueMerger) {
    requireNonNull(valueMerger);
    return new BiCollector<K, V, ImmutableMap<K, V>>() {
      @Override
      public <E> Collector<E, ?, ImmutableMap<K, V>> splitting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return ImmutableMap.toImmutableMap(toKey, toValue, valueMerger);
      }
    };
  }

  /**
   * Returns a collector that counts the number of occurrences for each unique bucket as determined
   * by the {@code bucketer} function. The result counts are stored in an {@link ImmutableMultiset},
   * hence implying that {@code bucketer} cannot return null.
   *
   * <p>{@code stream.collect(countingBy(User::id))} is equivalent to {@code
   * stream.map(User::id).collect(toImmutableMultiset())}, but reads more intuitive when you are
   * trying to count occurrences (as opposed to building Multiset as the end goal).
   *
   * <p>Alternatively, one can use {@code groupingBy(bucketer, Collectors.counting())} to collect
   * the equivalent counts in an {@code ImmutableMap<B, Long>}. Which of the two types to use
   * depends on whether you need to handle very large counts (potentially exceeding {@code
   * Integer.MAX_VALUE}), or whether the {@link Multiset} API is more useful (particularly, {@link
   * Multiset#count}). The memory footprint of {@code ImmutableMultiset<B>} is also more compact
   * than {@code ImmutableMap<B, Long>}.
   *
   * @since 5.6
   */
  public static <T, B> Collector<T, ?, ImmutableMultiset<B>> countingBy(
      Function<? super T, ? extends B> bucketer) {
    return Collectors.mapping(requireNonNull(bucketer), ImmutableMultiset.toImmutableMultiset());
  }

  /**
   * Returns a {@link Collector} that collects to an {@code ImmutableMap} with the input elements
   * <em>uniquely</em> indexed by the return value of {@code indexingFunction}.
   */
  public static <K, V> Collector<V, ?, ImmutableMap<K, V>> indexingBy(
      Function<? super V, ? extends K> indexingFunction) {
    return ImmutableMap.toImmutableMap(indexingFunction, identity());
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableMap}
   * using {@code valueCollector} to collect values of identical keys into a final value of type
   * {@code V}.
   *
   * <p>For example, the following calculates total population per state from city demographic data:
   *
   * <pre>{@code
   * ImmutableMap<StateId, Integer> statePopulations = BiStream.from(cities, City::getState, c -> c)
   *     .collect(toImmutableMap(summingInt(City::getPopulation)));
   * }</pre>
   *
   * <p>Entries are collected in encounter order.
   */
  public static <K, V1, V> BiCollector<K, V1, ImmutableMap<K, V>> toImmutableMap(
      Collector<V1, ?, V> valueCollector) {
    return BiCollectors.collectingAndThen(
        BiCollectors.groupingBy(identity(), valueCollector),
        stream -> stream.collect(toImmutableMap()));
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableSortedMap} according to {@code comparator}, using {@code valueMerger} to merge values
   * of duplicate keys.
   */
  public static <K, V> BiCollector<K, V, ImmutableSortedMap<K, V>> toImmutableSortedMap(
      Comparator<? super K> comparator, BinaryOperator<V> valueMerger) {
    requireNonNull(comparator);
    requireNonNull(valueMerger);
    return new BiCollector<K, V, ImmutableSortedMap<K, V>>() {
      @Override
      public <E> Collector<E, ?, ImmutableSortedMap<K, V>> splitting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return ImmutableSortedMap.toImmutableSortedMap(comparator, toKey, toValue, valueMerger);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableSortedMap} according to {@code comparator}.
   */
  public static <K, V> BiCollector<K, V, ImmutableSortedMap<K, V>> toImmutableSortedMap(
      Comparator<? super K> comparator) {
    requireNonNull(comparator);
    return new BiCollector<K, V, ImmutableSortedMap<K, V>>() {
      @Override
      public <E> Collector<E, ?, ImmutableSortedMap<K, V>> splitting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return ImmutableSortedMap.toImmutableSortedMap(comparator, toKey, toValue);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into a {@link Multimap} created
   * with {@code multimapSupplier}.
   */
  public static <K, V, M extends Multimap<K, V>> BiCollector<K, V, M> toMultimap(
      Supplier<M> multimapSupplier) {
    requireNonNull(multimapSupplier);
    return new BiCollector<K, V, M>() {
      @Override
      public <E> Collector<E, ?, M> splitting(Function<E, K> toKey, Function<E, V> toValue) {
        return Multimaps.toMultimap(toKey, toValue, multimapSupplier);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableListMultimap}. Equivalent to {@code ImmutableListMultimap::toImmutableListMultimap}.
   */
  public static <K, V> BiCollector<K, V, ImmutableListMultimap<K, V>> toImmutableListMultimap() {
    return ImmutableListMultimap::toImmutableListMultimap;
  }

  /**
   * Returns a {@link BiCollector} that first flattens each value of the input pair with {@code
   * flattener}, and then collects the flattened pairs into an {@link ImmutableListMultimap}.
   *
   * <p>For example, you can collect {@code groupingBy()} results into a multimap using:
   *
   * <pre>{@code
   * Map<PhoneNumber, Contact> phoneBook = ...;
   * ImmutableListMultimap<AreaCode, Contact> contactsByAreaCode = BiStream.from(phoneBook)
   *     .collect(BiCollectors.groupingBy(PhoneNumber::areaCode, mergingContacts()))
   *     .collect(BiCollectors.flatteningToImmutableListMultimap(Collection::stream));
   * }</pre>
   */
  public static <K, T, V>
      BiCollector<K, T, ImmutableListMultimap<K, V>> flatteningToImmutableListMultimap(
          Function<? super T, ? extends Stream<? extends V>> flattener) {
    return mappingValues(flattener, ImmutableListMultimap::flatteningToImmutableListMultimap);
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableSetMultimap}. Equivalent to {@code ImmutableSetMultimap::toImmutableSetMultimap}.
   */
  public static <K, V> BiCollector<K, V, ImmutableSetMultimap<K, V>> toImmutableSetMultimap() {
    return ImmutableSetMultimap::toImmutableSetMultimap;
  }

  /**
   * Returns a {@link BiCollector} that first flattens each value of the input pair with {@code
   * flattener}, and then collects the flattened pairs into an {@link ImmutableSetMultimap}.
   *
   * <p>For example, you can collect {@code groupingBy()} results into a multimap using:
   *
   * <pre>{@code
   * Map<PhoneNumber, Contact> phoneBook = ...;
   * ImmutableSetMultimap<AreaCode, Contact> contactsByAreaCode = BiStream.from(phoneBook)
   *     .collect(BiCollectors.groupingBy(PhoneNumber::areaCode, mergingContacts()))
   *     .collect(BiCollectors.flatteningToImmutableSetMultimap(Collection::stream));
   * }</pre>
   */
  public static <K, T, V>
      BiCollector<K, T, ImmutableSetMultimap<K, V>> flatteningToImmutableSetMultimap(
          Function<? super T, ? extends Stream<? extends V>> flattener) {
    return mappingValues(flattener, ImmutableSetMultimap::flatteningToImmutableSetMultimap);
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableMultiset} whose elements are the keys, with counts equal to the result of applying
   * {@code countFunction} to the values.
   *
   * <p>For duplicate keys (according to {@link Object#equals}), the first occurrence in encounter
   * order appears in the resulting multiset, with count equal to the sum of the outputs of {@code
   * countFunction.applyAsInt(value)} for each {@code value} mapped to that key.
   *
   * <p>{@code biStream.collect(toImmutableMultiset(countFunction))} is logically equivalent to
   * {@code biStream.collect(toImmutableMap(summingInt(countFunction)))}, except that it collects to
   * {@code ImmutableMultiset<K>} while the latter collects to {@code ImmutableMap<K, Integer>}.
   */
  public static <K, V> BiCollector<K, V, ImmutableMultiset<K>> toImmutableMultiset(
      ToIntFunction<? super V> countFunction) {
    requireNonNull(countFunction);
    return new BiCollector<K, V, ImmutableMultiset<K>>() {
      @Override
      public <E> Collector<E, ?, ImmutableMultiset<K>> splitting(
          Function<E, K> toKey, Function<E, V> toValue) {
        return ImmutableMultiset.toImmutableMultiset(
            toKey, input -> countFunction.applyAsInt(toValue.apply(input)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableBiMap}.
   * Equivalent to {@code ImmutableBimap::toImmutableBiMap}.
   */
  public static <K, V> BiCollector<K, V, ImmutableBiMap<K, V>> toImmutableBiMap() {
    return ImmutableBiMap::toImmutableBiMap;
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link
   * ImmutableTable<R, C, V>}, where each input key (of type {@code R}) is mapped to a row in the
   * table, and each input value is a {@code BiStream<C, V>} whose keys (of type {@code C}) are
   * mapped to columns in the table, and whose values (of type {@code V}) are mapped to the cell
   * values.
   *
   * <p>Typically useful in combination with a nested {@link BiStream#groupingBy}. For example:
   *
   * <pre>{@code
   * import static com.google.common.labs.collect.BiStream.groupingBy;
   *
   * List<Contact> contacts = ...;
   * ImmutableTable<LastName, FirstName, Long> nameCounts = contacts.stream()
   *     .collect(groupingBy(Contact::lastName, groupingBy(Contact::firstName, counting())))
   *     .collect(toImmutableTable());
   * }</pre>
   *
   * <p>Similarly, cascading group-by can be performed on a {@code Map} or {@code Multimap} through
   * {@code groupingBy()}, and then reduced in the same way using {@link #toImmutableTable}:
   *
   * <pre>{@code
   * import static com.google.common.labs.collect.BiCollectors.groupingBy;
   *
   * Multimap<Address, PhoneNumber> phoneBook = ...;
   * ImmutableTable<State, City, ImmutableSet<PhoneNumber>> phoneNumbersByLocation =
   *     BiStream.from(phoneBook)
   *         .collect(groupingBy(Address::state, groupingBy(Address::city, toImmutableSet())))
   *         .collect(toImmutableTable());
   * }</pre>
   *
   * <p>Cells are collected in encounter order.
   *
   * <p>The returned {@code BiCollector} is not optimized for parallel reduction.
   */
  public static <R, C, V>
      BiCollector<R, BiStream<? extends C, ? extends V>, ImmutableTable<R, C, V>>
          toImmutableTable() {
    return BiCollectors.flatMapping(
        (R r, BiStream<? extends C, ? extends V> columns) ->
            columns.mapToObj((c, v) -> Tables.immutableCell(r, c, v)),
        Collector.of(
            ImmutableTable.Builder<R, C, V>::new,
            ImmutableTable.Builder::put,
            (builder, that) -> builder.putAll(that.build()),
            ImmutableTable.Builder::build));
  }
  /**
   * Returns a collector that first maps each input into a key-value pair, and then collects them
   * into a {@link ImmutableMap}.
   *
   * @since 5.1
   */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, ? extends Both<? extends K, ? extends V>> mapper) {
    return mapping(mapper, toImmutableMap());
  }

  /**
   * Returns a collector that first maps each input into a key-value pair, and then collects them
   * into a {@link ImmutableListMultimap}.
   *
   * @since 5.1
   */
  public static <T, K, V> Collector<T, ?, ImmutableListMultimap<K, V>> toImmutableListMultimap(
      Function<? super T, ? extends Both<? extends K, ? extends V>> mapper) {
    return mapping(mapper, toImmutableListMultimap());
  }

  /**
   * Returns a collector that first maps each input into a key-value pair, and then collects them
   * into a {@link ImmutableSetMultimap}.
   *
   * @since 5.1
   */
  public static <T, K, V> Collector<T, ?, ImmutableSetMultimap<K, V>> toImmutableSetMultimap(
      Function<? super T, ? extends Both<? extends K, ? extends V>> mapper) {
    return mapping(mapper, toImmutableSetMultimap());
  }

  /**
   * Returns a collector that first maps each input into a key-value pair, and then collects them
   * into a {@link ImmutableBiMap}.
   *
   * @since 5.1
   */
  public static <T, K, V> Collector<T, ?, ImmutableBiMap<K, V>> toImmutableBiMap(
      Function<? super T, ? extends Both<? extends K, ? extends V>> mapper) {
    return mapping(mapper, toImmutableBiMap());
  }

  /**
   * Returns a collector that partitions the incoming elements into two groups: elements that
   * match {@code predicate}, and those that don't.
   *
   * <p>For example: <pre>{@code
   * candidates
   *     .collect(partitioningBy(Candidate::isElegible))
   *     .andThen((eligible, ineligible) -> ...);
   * }</pre>
   *
   * @since 6.0
   */
  public static <T> Collector<T, ?, Both<ImmutableList<T>, ImmutableList<T>>> partitioningBy(
      Predicate<? super T> predicate) {
    requireNonNull(predicate);
    return Collectors.collectingAndThen(
        Collectors.partitioningBy(predicate, toImmutableList()),
        m -> Both.of(m.get(true), m.get(false)));
  }

  private static <K, V, T, R> BiCollector<K, V, R> mappingValues(
      Function<? super V, ? extends T> mapper, BiCollector<K, T, R> downstream) {
    requireNonNull(mapper);
    requireNonNull(downstream);
    return new BiCollector<K, V, R>() {
      @Override
      public <E> Collector<E, ?, R> splitting(Function<E, K> toKey, Function<E, V> toValue) {
        return downstream.splitting(toKey, toValue.andThen(mapper));
      }
    };
  }

  private GuavaCollectors() {}
}

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
import static java.util.function.Function.identity;

import java.util.Comparator;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Tables;

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
   * Returns a {@link BiCollector} that collects the key-value pairs into an {@link ImmutableBimap}.
   * Equivalent to {@code ImmutableBimap::toImmutableBimap}.
   */
  public static <K, V> BiCollector<K, V, ImmutableBiMap<K, V>> toImmutableBimap() {
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
   * {@link #groupingBy}, and then reduced in the same way using {@link #toImmutableTable}:
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

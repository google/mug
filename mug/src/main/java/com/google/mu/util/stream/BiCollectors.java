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

import static java.util.Map.Entry.comparingByKey;
import static java.util.Map.Entry.comparingByValue;
import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToLongBiFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.mu.util.BiOptional;
import com.google.mu.util.Both;

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
    return collectingAndThen(toMap(() -> new LinkedHashMap<>()), Collections::unmodifiableMap);
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into a mutable {@code Map}
   * created by {@code mapSupplier}.
   *
   * <p>Duplicate keys will cause {@link IllegalArgumentException} to be thrown, with the offending
   * key reported in the error message. If instead of throwing exception, you need to merge the
   * values mapped to the same key, consider to use {@code biStream.collect(new CustomMap<>(),
   * Map::put)} for overwriting semantics; {@code biStream.collect(new CustomMap<>(),
   * Map::putIfAbsent)} for no overwrites; or {@code biStream.collect(new CustomMap<>(), (m, k, v)
   * -> m.merge(k, v, ...)} for other merge logic.
   *
   * <p>Note that due to constructor overload ambiguity, {@code toMap(CustomMapType::new)} may not
   * compile because many mutable {@code Map} types such as {@link LinkedHashMap} expose
   * both 0-arg and 1-arg constructors. You may need to use a lambda instead of
   * constructor reference to work around the compiler ambiguity, such as {@code
   * toMap(() -> new LinkedHashMap<>())}.
   *
   * <p>Null keys and values are discouraged but supported as long as the result {@code Map}
   * supports them. Thus this method can be used as a workaround of the
   * <a href="https://bugs.openjdk.java.net/browse/JDK-8148463">toMap(Supplier) JDK bug</a> that
   * fails to support null values.
   *
   * @since 5.9
   */
  public static <K, V, M extends Map<K, V>> BiCollector<K, V, M> toMap(
      Supplier<? extends M> mapSupplier) {
    requireNonNull(mapSupplier);
    return new BiCollector<K, V, M>() {
      @Override public <E> Collector<E, ?, M> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return MoreCollectors.toMap(toKey, toValue, mapSupplier);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that collects the key-value pairs into an immutable {@link Map}
   * using {@code valueMerger} to merge values of duplicate keys.
   */
  public static <K, V> BiCollector<K, V, Map<K, V>> toMap(BinaryOperator<V> valueMerger) {
    requireNonNull(valueMerger);
    return new BiCollector<K, V, Map<K, V>>() {
      @Override
      public <E> Collector<E, ?, Map<K, V>> collectorOf(
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
      public <E> Collector<E, ?, Map<K, V>> collectorOf(
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
   * Returns a counting {@link BiCollector} that counts the number of distinct input entries
   * according to {@link Object#equals} for both keys and values.
   *
   * <p>Unlike {@link #counting}, this collector should not be used on very large (for example,
   * larger than {@code Integer.MAX_VALUE}) streams because it internally needs to keep track of
   * all distinct entries in memory.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Integer> countingDistinct() {
    return mapping(
        AbstractMap.SimpleImmutableEntry::new,
        Collectors.collectingAndThen(Collectors.toSet(), Set::size));
  }

  /**
   * Returns a {@link BiCollector} that produces the sum of an integer-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Integer> summingInt(
      ToIntBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Integer>() {
      @Override
      public <E> Collector<E, ?, Integer> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summingInt(e -> mapper.applyAsInt(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that produces the sum of a long-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Long> summingLong(
      ToLongBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Long>() {
      @Override
      public <E> Collector<E, ?, Long> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summingLong(e -> mapper.applyAsLong(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that produces the sum of a double-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Double> summingDouble(
      ToDoubleBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Double>() {
      @Override
      public <E> Collector<E, ?, Double> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summingDouble(e -> mapper.applyAsDouble(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that produces the arithmetic mean of an integer-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Double> averagingInt(
      ToIntBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Double>() {
      @Override
      public <E> Collector<E, ?, Double> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.averagingInt(e -> mapper.applyAsInt(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that produces the arithmetic mean of a long-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Double> averagingLong(
      ToLongBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Double>() {
      @Override
      public <E> Collector<E, ?, Double> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.averagingLong(e -> mapper.applyAsLong(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that produces the arithmetic mean of a double-valued
   * function applied to the input pair.  If no input entries are present,
   * the result is 0.
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, Double> averagingDouble(
      ToDoubleBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, Double>() {
      @Override
      public <E> Collector<E, ?, Double> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.averagingDouble(e -> mapper.applyAsDouble(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} which applies an {@code int}-producing
   * mapping function to each input pair, and returns summary statistics
   * for the resulting values.
   *
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, IntSummaryStatistics> summarizingInt(
      ToIntBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, IntSummaryStatistics>() {
      @Override
      public <E> Collector<E, ?, IntSummaryStatistics> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summarizingInt(e -> mapper.applyAsInt(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} which applies an {@code long}-producing
   * mapping function to each input pair, and returns summary statistics
   * for the resulting values.
   *
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, LongSummaryStatistics> summarizingLong(
      ToLongBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, LongSummaryStatistics>() {
      @Override
      public <E> Collector<E, ?, LongSummaryStatistics> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summarizingLong(e -> mapper.applyAsLong(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} which applies an {@code double}-producing
   * mapping function to each input pair, and returns summary statistics
   * for the resulting values.
   *
   *
   * @since 3.2
   */
  public static <K, V> BiCollector<K, V, DoubleSummaryStatistics> summarizingDouble(
      ToDoubleBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return new BiCollector<K, V, DoubleSummaryStatistics>() {
      @Override
      public <E> Collector<E, ?, DoubleSummaryStatistics> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.summarizingDouble(e -> mapper.applyAsDouble(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Groups input pairs by {@code classifier} and collects entries belonging to the same group into
   * a nested {@link BiStream}. For example, you can break a {@link Map} into an {@code
   * ImmutableTable} with:
   *
   * <pre>{@code
   * Map<City, Long> cityPopulations = ...;
   * ImmutableTable<State, City, Long> stateCityPoulations =
   *     BiStream.from(cityPopulations)
   *         .collect(groupingBy((city, population) -> city.getState()))
   *         .collect(GuavaCollectors.toImmutableTable());
   * }</pre>
   *
   * @since 6.1
   */
  public static <R, C, V> BiCollector<C, V, BiStream<R, BiStream<C, V>>> groupingBy(
      BiFunction<? super C, ? super V, ? extends R> classifier) {
    return groupingBy(classifier, BiStream::toBiStream);
  }

  /**
   * Groups input entries by {@code classifier} and collects entries belonging to the same group
   * using {@code groupCollector}. For example, the following code splits a phone book by area code:
   *
   * <pre>{@code
   * Multimap<Address, PhoneNumber> phoneBook = ...;
   * ImmutableMap<AreaCode, ImmutableSetMultimap<Address, PhoneNumber>> areaPhoneBooks =
   *     BiStream.from(phoneBook)
   *         .collect(
   *             groupingBy(
   *                 (addr, phone) -> phone.areaCode(),
   *                 ImmutableSetMultimap::toImmutableSetMultimap))
   *         .collect(ImmutableMap::toImmutableMap);
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
      public <E> Collector<E, ?, BiStream<G, R>> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return BiStream.groupingBy(
            e -> classifier.apply(toKey.apply(e), toValue.apply(e)),
            groupCollector.collectorOf(toKey::apply, toValue::apply));
      }
    };
  }

  /**
   * Groups input entries by {@code classifier} and collects values belonging to the same group
   * using {@code groupCollector}. For example, the following code collects unique area codes for
   * each state:
   *
   * <pre>{@code
   * Multimap<Address, PhoneNumber> phoneBook = ...;
   * ImmutableMap<State, ImmutableSet<AreaCode>> stateAreaCodes =
   *     BiStream.from(phoneBook)
   *         .mapValues(PhoneNumber::areaCode)
   *         .collect(groupingBy(Address::state, toImmutableSet()))
   *         .collect(ImmutableMap::toImmutableMap);
   * }</pre>
   *
   * @since 3.2
   */
  public static <K, V, G, R> BiCollector<K, V, BiStream<G, R>> groupingBy(
      Function<? super K, ? extends G> classifier,
      Collector<? super V, ?, R> groupCollector) {
    requireNonNull(classifier);
    return groupingBy((k, v) -> classifier.apply(k), mapping((k, v) -> v, groupCollector));
  }

  /**
   * Groups input pairs by {@code classifier} and reduces values belonging to the same group using
   * {@code groupReducer}. For example, the following code calculates total household income for
   * each state:
   *
   * <pre>{@code
   * Map<Address, Household> households = ...;
   * ImmutableMap<State, Money> stateHouseholdIncomes =
   *     BiStream.from(households)
   *         .mapValues(Household::income)
   *         .collect(groupingBy(Address::state, Money::add))
   *         .collect(ImmutableMap::toImmutableMap);
   * }</pre>
   *
   * @since 3.3
   */
  public static <K, V, G> BiCollector<K, V, BiStream<G, V>> groupingBy(
      Function<? super K, ? extends G> classifier, BinaryOperator<V> groupReducer) {
    requireNonNull(classifier);
    requireNonNull(groupReducer);
    return new BiCollector<K, V, BiStream<G, V>>() {
      @Override
      public <E> Collector<E, ?, BiStream<G, V>> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return BiStream.groupingBy(toKey.andThen(classifier), toValue, groupReducer);
      }
    };
  }

/**
 * Returns a BiCollector that partitions the incoming pairs into two groups: elements that match
 * {@code predicate}, and those that don't. Both groups are stored in a BiStream.
 *
 * <p>For example:
 *
 * <pre>{@code
 * timeSeries
 *     .collect(partitioningBy((time, event) -> event.isImportant()))
 *     .andThen((importantEvents, unimportantEvents) -> ...);
 * }</pre>
 *
 * @since 8.1
 */
public static <K, V> BiCollector<K, V, Both<BiStream<K, V>, BiStream<K, V>>> partitioningBy(
    BiPredicate<? super K, ? super V> predicate) {
  return partitioningBy(predicate, BiStream::toBiStream);
}

/**
 * Returns a BiCollector that partitions the incoming pairs into two groups: elements that match
 * {@code predicate}, and those that don't, and use {@code downstream} collector to collect the
 * pairs.
 *
 * <p>For example:
 *
 * <pre>{@code
 * timeSeries
 *     .collect(partitioningBy((time, event) -> event.isImportant(), toSortedImmutableMap()))
 *     .andThen((importantEvents, unimportantEvents) -> ...);
 * }</pre>
 *
 * @param <K> the input key type
 * @param <V> the input value type
 * @param <R> the result type of the downstream collector
 * @since 8.1
 */
public static <K, V, R> BiCollector<K, V, Both<R, R>> partitioningBy(
    BiPredicate<? super K, ? super V> predicate,
    BiCollector<? super K, ? super V, ? extends R> downstream) {
  return partitioningBy(predicate, downstream, downstream);
}

/**
 * Returns a BiCollector that partitions the incoming pairs into two groups: elements that match
 * {@code predicate}, and those that don't, and use {@code ifTrue} and {@code ifFalse} downstream
 * collectors respectively to collect the pairs.
 *
 * <p>For example:
 *
 * <pre>{@code
 * timeSeries
 *     .collect(
 *         partitioningBy((time, event) -> event.isImportant(), toImmutableMap(), counting()))
 *     .andThen((importantEvents, unimportantCount) -> ...);
 * }</pre>
 *
 * @param <K> the input key type
 * @param <V> the input value type
 * @param <T> the result type for the pairs that evaluate to true
 * @param <F> the result type for the pairs that evaluate to false
 * @since 8.1
 */
public static <K, V, T, F> BiCollector<K, V, Both<T, F>> partitioningBy(
    BiPredicate<? super K, ? super V> predicate,
    BiCollector<? super K, ? super V, ? extends T> ifTrue,
    BiCollector<? super K, ? super V, ? extends F> ifFalse) {
  requireNonNull(predicate);
  return mapping(
      AbstractMap.SimpleImmutableEntry<K, V>::new,
      MoreCollectors.partitioningBy(
          (Map.Entry<K, V> e) -> predicate.test(e.getKey(), e.getValue()),
          ifTrue.collectorOf(Map.Entry::getKey, Map.Entry::getValue),
          ifFalse.collectorOf(Map.Entry::getKey, Map.Entry::getValue)));
}

  /**
   * Returns a {@link BiCollector} that maps the result of {@code upstream} collector using
   * {@code finisher}.
   *
   * @since 3.2
   */
  public static <K, V, T, R> BiCollector<K, V, R> collectingAndThen(
      BiCollector<K, V, T> upstream, Function<? super T, ? extends R> finisher) {
    requireNonNull(upstream);
    requireNonNull(finisher);
    return new BiCollector<K, V, R>() {
      @Override
      public <E> Collector<E, ?, R> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.collectingAndThen(upstream.collectorOf(toKey, toValue), finisher::apply);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that maps the result of {@code collector} using the {@code
   * finisher} BiFunction. Useful when combined with BiCollectors like {@link #partitioningBy}.
   *
   * @since 8.1
   */
  public static <K, V, A, B, R> BiCollector<K, V, R> collectingAndThen(
      BiCollector<K, V, ? extends Both<? extends A, ? extends B>> collector,
      BiFunction<? super A, ? super B, ? extends R> finisher) {
    requireNonNull(finisher);
    return collectingAndThen(collector, ab -> ab.andThen(finisher));
  }

  /**
   * Returns a {@link BiCollector} that first collects the input pairs into a {@link BiStream} and then applies
   * {@code finisher} on the intermediary BiStream.
   *
   * <p>This method makes it easier to create BiCollector using a lambda. For example, you may want to apply
   * some stream operations for every group of pairs when using the {@link #groupingBy(BiFunction,
   * BiCollector) groupingBy} method:
   *
   * <pre>{@code
   *     BiStream.from(phoneBook)
   *         .collect(
   *             groupingBy(
   *                 (addr, phone) -> phone.areaCode(),
   *                 collectingAndThen(group -> group.flatMapKeys(...).mapIfPresent(...)...))
   *         .collect(ImmutableMap::toImmutableMap);
   * }</pre>
   *
   * @since 5.4
   */
  public static <K, V, R> BiCollector<K, V, R> collectingAndThen(
      Function<? super BiStream<K, V>, ? extends R> finisher) {
    return collectingAndThen(BiStream::toBiStream, finisher);
  }

  /**
   * Returns a {@link BiCollector} that first maps the input pair using {@code mapper} and then collects the
   * results using {@code downstream} collector.
   *
   * @since 3.2
   */
  public static <K, V, T, R> BiCollector<K, V, R> mapping(
      BiFunction<? super K, ? super V, ? extends T> mapper, Collector<T, ?, R> downstream) {
    requireNonNull(mapper);
    requireNonNull(downstream);
    return new BiCollector<K, V, R>() {
      @Override public <E> Collector<E, ?, R> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.mapping(e -> mapper.apply(toKey.apply(e), toValue.apply(e)), downstream);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that first maps the input pair using {@code keyMapper} and {@code valueMapper}
   * respectively, then collects the results using {@code downstream} collector.
   *
   * @since 3.6
   */
  public static <K, V, K1, V1, R> BiCollector<K, V, R> mapping(
      BiFunction<? super K, ? super V, ? extends K1> keyMapper,
      BiFunction<? super K, ? super V, ? extends V1> valueMapper,
      BiCollector<K1, V1, R> downstream) {
    requireNonNull(keyMapper);
    requireNonNull(valueMapper);
    requireNonNull(downstream);
    return new BiCollector<K, V, R>() {
      @Override public <E> Collector<E, ?, R> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return downstream.collectorOf(
            e -> keyMapper.apply(toKey.apply(e), toValue.apply(e)),
            e -> valueMapper.apply(toKey.apply(e), toValue.apply(e)));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that first maps the input pair into another pair using {@code mapper}.
   * and then collects the results using {@code downstream} collector.
   *
   * @since 5.2
   */
  public static <K, V, K1, V1, R> BiCollector<K, V, R> mapping(
      BiFunction<? super K, ? super V, ? extends Both<? extends K1, ? extends V1>> mapper,
      BiCollector<K1, V1, R> downstream) {
    requireNonNull(mapper);
    requireNonNull(downstream);
    return new BiCollector<K, V, R>() {
      @Override public <E> Collector<E, ?, R> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.mapping(
            e -> mapper.apply(toKey.apply(e), toValue.apply(e)),
            downstream.collectorOf(BiStream::left, BiStream::right));
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that first flattens the input pair using {@code flattener}
   * and then collects the results using {@code downstream} collector.
   *
   * <p>For example, you may use several levels of {@code groupingBy()} to aggregate metrics along a
   * few dimensions, and then flatten them into a histogram. This could be done using {@code
   * BiStream#flatMapToObj}, like:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.BiStream.groupingBy;
   *
   *   List<HistogramBucket> histogram = events.stream()
   *       .collect(groupingBy(Event::cell, groupingBy(Event::hour, counting())))
   *       .flatMapToObj((cell, cellEvents) ->
   *           cellEvents.mapToObj((hour, count) ->
   *               HistogramBucket.newBuilder()
   *                   .addDimension(cell)
   *                   .addDimension(hour)
   *                   .setCount(count)
   *                   .build()))
   *       .collect(toList());
   * }</pre>
   *
   * It works. But if you need to do this kind of histogram creation along different dimensions
   * repetitively, the {@code flatMapToObj() + mapToObj()} boilerplate becomes tiresome to read and
   * write. Instead, you could use {@code BiCollectors.flatMapping()} to encapsulate and reuse the
   * boilerplate:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.BiStream.groupingBy;
   *
   *   List<HistogramBucket> byCellHourly = events.stream()
   *       .collect(groupingBy(Event::cell, groupingBy(Event::hour, counting())))
   *       .collect(toHistogram());
   *
   *   List<HistogramBucket> byUserHourly = events.stream()
   *       .collect(groupingBy(Event::user, groupingBy(Event::hour, counting())))
   *       .collect(toHistogram());
   *
   *   private static BiCollector<Object, BiStream<?, Long>, List<HistogramBucket>> toHistogram() {
   *     return BiCollectors.flatMapping(
   *         (d1, events) ->
   *               events.mapToObj((d2, count) ->
   *                   HistogramBucket.newBuilder()
   *                       .addDimension(d1)
   *                       .addDimension(d2)
   *                       .setCount(count)
   *                       .build()),
   *         .collect(List());
   *   }
   * }</pre>
   *
   * @since 3.4
   */
  public static <K, V, T, R> BiCollector<K, V, R> flatMapping(
      BiFunction<? super K, ? super V, ? extends Stream<? extends T>> flattener,
      Collector<T, ?, R> downstream) {
    requireNonNull(flattener);
    requireNonNull(downstream);
    return new BiCollector<K, V, R>() {
      @Override public <E> Collector<E, ?, R> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return Java9Collectors.flatMapping(e -> flattener.apply(toKey.apply(e), toValue.apply(e)), downstream);
      }
    };
  }

  /**
   * Returns a {@link BiCollector} that first flattens the input pair using {@code flattener}
   * and then collects the result pairs using {@code downstream} collector.
   *
   * @since 3.4
   */
  public static <K, V, K1, V1, R> BiCollector<K, V, R> flatMapping(
      BiFunction<? super K, ? super V, ? extends BiStream<? extends K1, ? extends V1>> flattener,
      BiCollector<K1, V1, R> downstream) {
    return flatMapping(
        flattener.andThen(BiStream::mapToEntry),
        downstream.<Map.Entry<? extends K1, ? extends V1>>collectorOf(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Returns a BiCollector that inverses the input pairs of {@code (a, b)} into {@code (b, a)}
   * before passing it to {@code downstream} collector.
   *
   * @since 8.1
   */
  public static <A, B, R> BiCollector<A, B, R> inverse(BiCollector<B, A, R> downstream) {
    return mapping((k, v) -> v, (k, v) -> k, downstream);
  }

  /**
   * Returns a {@link BiCollector} that finds the pair with the maximum key according to {@code
   * comparator}.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> maxByKey(
      Comparator<? super K> comparator) {
    return maxBy(comparingByKey(comparator));
  }

  /**
   * Returns a {@link BiCollector} that finds the pair with the minimum key according to {@code
   * comparator}.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> minByKey(
      Comparator<? super K> comparator) {
    return maxByKey(comparator.reversed());
  }

  /**
   * Returns a {@link BiCollector} that finds the pair with the maximum value according to {@code
   * comparator}.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> maxByValue(
      Comparator<? super V> comparator) {
    return maxBy(comparingByValue(comparator));
  }

  /**
   * Returns a {@link BiCollector} that finds the pair with the minimum value according to {@code
   * comparator}.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> minByValue(
      Comparator<? super V> comparator) {
    return maxByValue(comparator.reversed());
  }

  /**
   * Returns a {@link BiCollector} that finds the minimum pair according to {@code keyComparator}
   * and then {@code valueComparator} for equal keys.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> minBy(
      Comparator<? super K> keyComparator, Comparator<? super V> valueComparator) {
    return minBy(
        Map.Entry.<K, V>comparingByKey(keyComparator)
            .thenComparing(comparingByValue(valueComparator)));
  }

  private static <K, V> BiCollector<K, V, BiOptional<K, V>> minBy(
      Comparator<? super Map.Entry<K, V>> comparator) {
    return maxBy(comparator.reversed());
  }

  /**
   * Returns a {@link BiCollector} that finds the maximum pair according to {@code keyComparator}
   * and then {@code valueComparator} for equal keys.
   *
   * <p>Null keys and values are not supported.
   *
   * @since 6.6
   */
  public static <K, V> BiCollector<K, V, BiOptional<K, V>> maxBy(
      Comparator<? super K> keyComparator, Comparator<? super V> valueComparator) {
    return maxBy(
        Map.Entry.<K, V>comparingByKey(keyComparator)
            .thenComparing(comparingByValue(valueComparator)));
  }

  private static <K,V> BiCollector<K, V, BiOptional<K, V>> maxBy(
      Comparator<? super Map.Entry<K, V>> comparator) {
    requireNonNull(comparator);
    return new BiCollector<K, V, BiOptional<K, V>>() {
      @Override
      public <E> Collector<E, ?, BiOptional<K, V>> collectorOf(
          Function<E, K> toKey, Function<E, V> toValue) {
        return Collectors.collectingAndThen(
            Collectors.mapping(
                (E e) -> BiStream.kv(toKey.apply(e), toValue.apply(e)),
                Collectors.maxBy(comparator)),
            BiCollectors::asBiOptional);
      }
    };
  }

  private static <K, V> BiOptional<K, V> asBiOptional(
      Optional<? extends Map.Entry<? extends K, ? extends V>> optional) {
    return BiOptional.from(optional).map(Map.Entry::getKey, Map.Entry::getValue);
  }

  private BiCollectors() {}
}

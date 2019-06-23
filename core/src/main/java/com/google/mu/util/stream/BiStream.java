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

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.ORDERED;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static  java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.doubleStream;
import static java.util.stream.StreamSupport.intStream;
import static java.util.stream.StreamSupport.longStream;
import static java.util.stream.StreamSupport.stream;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators.AbstractDoubleSpliterator;
import java.util.Spliterators.AbstractIntSpliterator;
import java.util.Spliterators.AbstractLongSpliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToLongBiFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A class similar to {@link Stream}, but operating over a sequence of pairs of objects.
 *
 * <p>Note: For ease of reference, this class uses 'key' and 'value' to refer to each of the two
 * parts of each pair in the sequence. However, both 'key' and 'value' can be any object of the
 * appropriate type, or null. There is no implication that keys or key-value pairs are unique, or
 * that keys can be compared for equality. You may equivalently read them as 'left' and 'right', or
 * 'first' and 'second'.
 *
 * <p>If the contents of the stream aren't identifiably 'keys' or 'values', and the methods with
 * 'key' or 'value' in their name are distracting, consider using the pair-wise operations. For
 * instance, instead of:
 *
 * <pre>{@code
 * BiStream.from(cities.stream(), City::population, City::latitude)
 *     .filterKeys(p -> p > 10000);
 * }</pre>
 *
 * you might use:
 *
 * <pre>{@code
 * BiStream.from(cities.stream(), City::population, City::latitude)
 *     .filter((population, lat) -> population > 10000);
 * }</pre>
 *
 * <p>Keys and values are allowed to be null by default unless explicitly documented otherwise.
 *
 * <p>Some methods (e.g. {@code mapKeys()}) come in two versions, one taking {@link BiFunction}
 * (which will receive both key and value) and another taking {@link Function} (which in this case
 * will receive only the key) . They operate equivalently otherwise.
 */
public abstract class BiStream<K, V> {
  /**
   * Returns a {@code Collector} that groups the input elements by {@code keyFunction} and collects
   * the values mapping to the same key into a {@link List}. Similar but different from
   * {@link Collectors#groupingBy(Function)}, this method collects the groups into {@link #BiStream}
   * instead, allowing fluent method chaining. For example:
   *
   * <pre>{@code
   * Map<EmployeeId, List<Task>> employeesWithMultipleTasks = tasks.stream()
   *     .collect(BiStream.groupingBy(Task::assignedTo))
   *     .filterValues(tasks -> tasks.size() > 1)
   *     .toMap();
   * }</pre>
   *
   * Even if you don't need to chain more methods, using this collector allows you to fluently
   * collect the results into the desired container type. For example {@link #toMap} collects to an
   * immutable {@code Map}; or {@code collect(Collectors::toConcurrentMap)} if concurrency is needed.
   *
   * <p>Entries are collected in encounter order.
   *
   * @since 3.0
   */
  public static <T, K> Collector<T, ?, BiStream<K, List<T>>> groupingBy(
      Function<? super T, ? extends K> keyFunction) {
    return groupingBy(keyFunction, Collectors.toList());
  }

  /**
   * Returns a {@code Collector} that groups the input elements by {@code keyFunction} and collects
   * the values mapping to the same key using {@code valueCollector}. Similar but different from
   * {@link Collectors#groupingBy(Function, Collector)}, this method collects the groups into {@link
   * #BiStream} instead, allowing fluent method chaining. For example:
   *
   * <pre>{@code
   * Map<EmployeeId, Integer> topTenEmployeesByWorkHour = projects.stream()
   *     .flatMap(project -> project.getMembers().stream())  // Stream<TeamMember>
   *     .collect(BiStream.groupingBy(TeamMember::employeeId, summingInt(TeamMember::hours)))
   *     .sortedByValues(Comparator.reverseOrder())
   *     .limit(10)
   *     .toMap();
   * }</pre>
   *
   * Even if you don't need to chain more methods, using this collector allows you to fluently
   * collect the results into the desired container type. For example {@link #toMap} collects to an
   * immutable {@code Map}; or you could supply {@code collect(ImmutableBiMap::toImmutableBiMap)}
   * if {@code BiMap} is needed.
   *
   * <p>Entries are collected in encounter order.
   *
   * @since 3.0
   */
  public static <T, K, V> Collector<T, ?, BiStream<K, V>> groupingBy(
      Function<? super T, ? extends K> keyFunction, Collector<? super T, ?, V> valueCollector) {
    Collector<T, ?, Map<K, V>> grouping =
        Collectors.groupingBy(keyFunction, LinkedHashMap::new, valueCollector);
    return collectingAndThen(grouping, BiStream::from);
  }

  /**
   * Returns a {@code Collector} that concatenates {@code BiStream} objects derived from the input
   * elements using the given {@code toBiStream} function.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Map<EmployeeId, Task> billableTaskAssignments = projects.stream()
   *     .collect(concatenating(p -> BiStream.from(p.getTaskAssignments())))
   *     .filterValues(Task::billable)
   *     .toMap();
   * }</pre>
   *
   * @since 3.0
   */
  public static <T, K, V> Collector<T, ?, BiStream<K, V>> concatenating(
      Function<? super T, ? extends BiStream<? extends K, ? extends V>> toBiStream) {
    return collectingAndThen(toList(), list -> concat(list.stream().map(toBiStream)));
  }

  /**
   * Returns a {@code Collector} that groups {@link Map.Entry#getValue map values} that are mapped
   * to the same key using {@code valueCollector}. For example:
   *
   * <pre>{@code
   * Map<EmployeeId, List<Task>> employeesWithMultipleTasks = projects.stream()
   *     .map(Project::getTaskAssignments)  // Stream<Map<EmployeeId, Task>>
   *     .collect(groupingValuesFrom(Map::entrySet))
   *     .filterValues(tasks -> tasks.size() > 1)
   *     .toMap();
   * }</pre>
   *
   * <p>This idiom is applicable even if {@code getTaskAssignments()} returns {@code Multimap}:
   *
   * <pre>{@code
   * Map<EmployeeId, List<Task>> employeesWithMultipleTasks = projects.stream()
   *     .map(Project::getTaskAssignments)  // Stream<Multimap<EmployeeId, Task>>
   *     .collect(groupingValuesFrom(Multimap::entries))
   *     .filterValues(tasks -> tasks.size() > 1)
   *     .toMap();
   * }</pre>
   *
   * <p>Entries are collected in encounter order.
   *
   * @since 3.0
   */
  public static <T, K, V> Collector<T, ?, BiStream<K, List<V>>> groupingValuesFrom(
      Function<? super T, ? extends Collection<Map.Entry<K, V>>> entrySource) {
    return groupingValuesFrom(entrySource, toList());
  }

  /**
   * Returns a {@code Collector} that groups {@link Map.Entry#getValue map values} that are mapped
   * to the same key using {@code valueCollector}. For example:
   *
   * <pre>{@code
   * Map<EmployeeId, Integer> employeeWorkHours = projects.stream()
   *     .map(Project::getTaskAssignments)  // Stream<Map<EmployeeId, Task>>
   *     .collect(groupingValuesFrom(Map::entrySet, summingInt(Task::hours)))
   *     .toMap();
   * }</pre>
   *
   * <p>This idiom is applicable even if {@code getTaskAssignments()} returns {@code Multimap}:
   *
   * <pre>{@code
   * Map<EmployeeId, Integer> employeeWorkHours = projects.stream()
   *     .map(Project::getTaskAssignments)  // Stream<Multimap<EmployeeId, Task>>
   *     .collect(groupingValuesFrom(Multimap::entries, summingInt(Task::hours)))
   *     .toMap();
   * }</pre>
   *
   * <p>Entries are collected in encounter order.
   *
   * @since 3.0
   */
  public static <T, K, V, R> Collector<T, ?, BiStream<K, R>> groupingValuesFrom(
      Function<? super T, ? extends Collection<Map.Entry<K, V>>> entrySource,
      Collector<? super V, ?, R> valueCollector) {
    return Collectors.flatMapping(
        requireNonNull(entrySource.andThen(Collection::stream)),
        groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, valueCollector)));
  }

  /**
   * Returns a {@code Collector} that will pair each input element with each element from {@code
   * right} into a new {@code BiStream}. For example:
   *
   * <pre>{@code
   * ImmutableList<QuarterlyReport> allQuarterlyReports = quarters.stream()
   *     .collect(crossJoining(departments))
   *     .mapToObj(QuarterlyReport::new)
   *     .collect(toImmutableList());
   * }</pre>
   *
   * <p>The input elements are repeated once per element from {@code right}. For example: {@code [1,
   * 2, 3].collect(crossJoining([a, b]))} will generate {@code [{1, a}, {2, a}, {3, a}, {1, b}, {2,
   * b}, {3, b}]}.
   *
   * <p>The returned {@code BiStream} takes {@code O(n)} space where {@code n} is the size of the
   * input elements. The "cross-joining" with the {@code right} stream is computed on-the-fly with
   * {@code O(1)} memory cost.
   *
   * @since 3.0
   */
  public static <L, R> Collector<L, ?, BiStream<L, R>> crossJoining(Stream<R> right) {
    requireNonNull(right);
    return collectingAndThen(
        toList(),
        left ->
            // If `right` is infinite, even limit(1) will result in infinite loop otherwise.
            left.isEmpty() ? empty() : concat(right.map(r -> from(left, identity(), l -> r))));
  }

  /** Returns an empty {@code BiStream}. */
  public static <K, V> BiStream<K, V> empty() {
    return from(Stream.empty());
  }

  /** Returns a {@code BiStream} of a single pair containing {@code key} and {@code value}. */
  public static <K, V> BiStream<K, V> of(K key, V value) {
    return from(Stream.of(kv(key, value)));
  }

  /** Returns a {@code BiStream} of two pairs, containing the supplied keys and values. */
  public static <K, V> BiStream<K, V> of(
      K key1, V value1, K key2, V value2) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2)));
  }

  /** Returns a {@code BiStream} of three pairs, containing the supplied keys and values. */
  public static <K, V> BiStream<K, V> of(
      K key1,
      V value1,
      K key2,
      V value2,
      K key3,
      V value3) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3)));
  }

  /**
   * Returns a {@code BiStream} of the entries from {@code m1}, {@code m2} then {@code rest} in
   * encounter order. For example:
   *
   * <pre>{@code
   * Map<AccountId, Account> allAccounts = concat(primaryAccounts, secondaryAccounts).toMap();
   * }</pre>
   *
   * @since 3.0
   */
  @SafeVarargs
  public static <K, V> BiStream<K, V> concat(
      Map<? extends K, ? extends V> m1,
      Map<? extends K, ? extends V> m2,
      Map<? extends K, ? extends V>... rest) {
    Stream.Builder<Map<? extends K, ? extends V>> builder = Stream.builder();
    builder.add(requireNonNull(m1)).add(requireNonNull(m2));
    for (Map<? extends K, ? extends V> m : rest) {
      builder.add(requireNonNull(m));
    }
    return concat(builder.build().map(BiStream::from));
  }

  /**
   * Returns a {@code BiStream} of pairs from {@code biStreams} concatenated in encounter order.
   *
   * @since 3.0
   */
  public static <K, V> BiStream<K, V> concat(
      Stream<? extends BiStream<? extends K, ? extends V>> biStreams) {
    return from(biStreams.flatMap(BiStream::mapToEntry));
  }

  /**
   * Returns a {@code BiStream} in which the first element in {@code left} is paired with the first
   * element in {@code right}; the second paired with the corresponding second and the third with
   * the corresponding third etc. For example: {@code BiStream.zip(asList(1, 2, 3), asList("one",
   * "two"))} will return {@code BiStream.of(1, "one", 2, "two")}.
   *
   * <p>The resulting stream will only be as long as the shorter of the two iterables; if one is
   * longer, its extra elements will be ignored.
   *
   * @since 3.0
   */
  public static <L, R> BiStream<L, R> zip(Collection<L> left, Collection<R> right) {
    return zip(left.stream(), right.stream());
  }

  /**
   * Returns a {@code BiStream} in which the first element in {@code left} is paired with the first
   * element in {@code right}; the second paired with the corresponding second and the third with
   * the corresponding third etc. For example: {@code BiStream.zip(Stream.of(1, 2, 3),
   * Stream.of("one", "two"))} will return {@code BiStream.of(1, "one", 2, "two")}.
   *
   * <p>The resulting stream will only be as long as the shorter of the two input streams; if one
   * stream is longer, its extra elements will be ignored.
   *
   * <p>The resulting stream by default runs sequentially regardless of the input streams. This is
   * because the implementation is not <a
   * href="http://gee.cs.oswego.edu/dl/html/StreamParallelGuidance.html">efficiently splittable</a>.
   * and may not perform well if run in parallel.
   */
  public static <L, R> BiStream<L, R> zip(Stream<L> left, Stream<R> right) {
    return new ZippingStream<>(left, right);
  }

  /**
   * Short-hand for {@code from(elements, identity(), identity())}. Typically followed by {@link
   * #mapKeys} or {@link #mapValues}. For example:
   *
   * <pre>{@code
   * static import com.google.common.labs.collect.BiStream.biStream;
   *
   * Map<EmployeeId, Employee> employeesById = biStream(employees)
   *     .mapKeys(Employee::id)
   *     .toMap();
   * }</pre>
   *
   * @since 3.0
   */
  public static <T> BiStream<T, T> biStream(Collection<T> elements) {
    return from(elements, identity(), identity());
  }

  /** Returns a {@code BiStream} of the entries in {@code map}. */
  public static <K, V> BiStream<K, V> from(Map<K, V> map) {
    return from(map.entrySet().stream());
  }

  /**
   * Returns a {@code BiStream} of {@code elements}, each transformed to a pair of values with
   * {@code toKey} and {@code toValue}.
   */
  public static <T, K, V> BiStream<K, V> from(
      Collection<T> elements,
      Function<? super T, ? extends K> toKey,
      Function<? super T, ? extends V> toValue) {
    return from(elements.stream(), toKey, toValue);
  }

  /**
   * Returns a {@code BiStream} of the elements from {@code stream}, each transformed to a pair of
   * values with {@code toKey} and {@code toValue}.
   */
  public static <T, K, V> BiStream<K, V> from(
      Stream<T> stream,
      Function<? super T, ? extends K> toKey,
      Function<? super T, ? extends V> toValue) {
    return new GenericEntryStream<>(stream, toKey, toValue);
  }

  static <K, V, E extends Map.Entry<? extends K, ? extends V>> BiStream<K, V> from(
      Stream<E> entryStream) {
    return new GenericEntryStream<E, K, V>(entryStream, Map.Entry::getKey, Map.Entry::getValue) {
      @Override
      public <K2, V2> BiStream<K2, V2> map(
          BiFunction<? super K, ? super V, ? extends K2> keyMapper,
          BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
        return from(entryStream, forEntry(keyMapper), forEntry(valueMapper));
      }

      @Override
      public <K2> BiStream<K2, V> mapKeys(
          BiFunction<? super K, ? super V, ? extends K2> keyMapper) {
        return from(entryStream, forEntry(keyMapper), Map.Entry::getValue);
      }

      @Override
      public <V2> BiStream<K, V2> mapValues(
          BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
        return from(entryStream, Map.Entry::getKey, forEntry(valueMapper));
      }

      @Override
      public BiStream<K, V> limit(int maxSize) { // Stick to this impl where mapToEntry() is cheap
        return from(entryStream.limit(maxSize));
      }

      @Override
      public BiStream<K, V> skip(int n) { // Stick to this impl where mapToEntry() is cheap
        return from(entryStream.skip(n));
      }

      @Override
      Stream<E> mapToEntry() { // Reuse the same Entry objects. Don't allocate new ones
        return entryStream;
      }
    };
  }

  Stream<? extends Map.Entry<? extends K, ? extends V>> mapToEntry() {
    return mapToObj(BiStream::kv);
  }

  /**
   * Returns a {@code Stream} consisting of the results of applying {@code mapper} to each pair in
   * this {@code BiStream}.
   */
  public abstract <T> Stream<T> mapToObj(BiFunction<? super K, ? super V, ? extends T> mapper);

  /**
   * Returns a {@code BiStream} consisting of the results of applying {@code keyMapper} and {@code
   * valueMapper} to the pairs in this {@code BiStream}.
   */
  public <K2, V2> BiStream<K2, V2> map(
      BiFunction<? super K, ? super V, ? extends K2> keyMapper,
      BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    requireNonNull(keyMapper);
    requireNonNull(valueMapper);
    return from(mapToObj((k, v) -> kv(keyMapper.apply(k, v), valueMapper.apply(k, v))));
  }

  /**
   * Returns a {@link DoubleStream} consisting of the results of applying {@code mapper} to the
   * pairs in this {@code BiStream}.
   */
  public abstract DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper);

  /**
   * Returns an {@link IntStream} consisting of the results of applying {@code mapper} to the pairs
   * in this {@code BiStream}.
   */
  public abstract IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper);

  /**
   * Returns a {@link LongStream} consisting of the results of applying {@code mapper} to the pairs
   * in this {@code BiStream}.
   */
  public abstract LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper);

  /**
   * Returns a {@code BiStream} of pairs whose keys are the result of applying {@code keyMapper} to
   * the key of each pair in this {@code BiStream}, and whose values are unchanged.
   */
  public <K2> BiStream<K2, V> mapKeys(BiFunction<? super K, ? super V, ? extends K2> keyMapper) {
    return map(keyMapper, (k, v) -> v);
  }

  /** Maps each key to another key of type {@code K2}. */
  public abstract <K2> BiStream<K2, V> mapKeys(Function<? super K, ? extends K2> keyMapper);

  /** Maps each value to another value of type {@code V2}. */
  public <V2> BiStream<K, V2> mapValues(
      BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    return map((k, v) -> k, valueMapper);
  }

  /** Maps each value to another value of type {@code V2}. */
  public abstract <V2> BiStream<K, V2> mapValues(Function<? super V, ? extends V2> valueMapper);

  /**
   * Maps a single pair to zero or more objects of type {@code T}.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <T> Stream<T> flatMapToObj(
      BiFunction<? super K, ? super V, ? extends Stream<? extends T>> mapper) {
    return mapToObj(mapper).flatMap(identity());
  }

  /**
   * Maps a single pair to zero or more {@code double}s.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final DoubleStream flatMapToDouble(
      BiFunction<? super K, ? super V, ? extends DoubleStream> mapper) {
    return mapToObj(mapper).flatMapToDouble(identity());
  }

  /**
   * Maps a single pair to zero or more {@code int}s.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final IntStream flatMapToInt(
      BiFunction<? super K, ? super V, ? extends IntStream> mapper) {
    return mapToObj(mapper).flatMapToInt(identity());
  }

  /**
   * Maps a single pair to zero or more {@code long}s.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final LongStream flatMapToLong(
      BiFunction<? super K, ? super V, ? extends LongStream> mapper) {
    return mapToObj(mapper).flatMapToLong(identity());
  }

  /**
   * Maps each pair in this stream to zero or more pairs in another {@code BiStream}. For example
   * the following code snippet repeats each pair in a {@code BiStream} for 3 times:
   *
   * <pre>{@code
   * BiStream<K, V> repeated = stream.flatMap((k, v) -> BiStream.of(k, v, k, v, k, v));
   * }</pre>
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2, V2> BiStream<K2, V2> flatMap(
      BiFunction<? super K, ? super V, ? extends BiStream<? extends K2, ? extends V2>> mapper) {
    return from(mapToObj(mapper).filter(s -> s != null).flatMap(BiStream::mapToEntry));
  }

  /**
   * Maps each key to zero or more keys of type {@code K2}.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2> BiStream<K2, V> flatMapKeys(
      BiFunction<? super K, ? super V, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return from(
        this.<Map.Entry<K2, V>>flatMapToObj( // j2cl compiler needs help with type inference
            (k, v) -> nullToEmpty(keyMapper.apply(k, v)).map(k2 -> kv(k2, v))));
  }

  /**
   * Maps each key to zero or more keys of type {@code K2}.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2> BiStream<K2, V> flatMapKeys(
      Function<? super K, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return flatMapKeys((k, v) -> keyMapper.apply(k));
  }

  /**
   * Maps each value to zero or more values of type {@code V2}.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <V2> BiStream<K, V2> flatMapValues(
      BiFunction<? super K, ? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return from(
        this.<Map.Entry<K, V2>>flatMapToObj( // j2cl compiler needs help with type inference
            (k, v) -> nullToEmpty(valueMapper.apply(k, v)).map(v2 -> kv(k, v2))));
  }

  /**
   * Maps each value to zero or more values of type {@code V2}.
   *
   * <p>If a mapped stream is null, an empty stream is used instead.
   */
  public final <V2> BiStream<K, V2> flatMapValues(
      Function<? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return flatMapValues((k, v) -> valueMapper.apply(v));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs of this stream, additionally invoking {@code
   * action} on each pair as pairs are consumed from the resulting stream.
   */
  public final BiStream<K, V> peek(BiConsumer<? super K, ? super V> action) {
    requireNonNull(action);
    return from(mapToEntry().peek(e -> action.accept(e.getKey(), e.getValue())));
  }

  /** Filter this stream to only pairs matching {@code predicate}. */
  public final BiStream<K, V> filter(BiPredicate<? super K, ? super V> predicate) {
    requireNonNull(predicate);
    return from(mapToEntry().filter(kv -> predicate.test(kv.getKey(), kv.getValue())));
  }

  /** Filter this stream to only pairs whose key matches {@code predicate}. */
  public final BiStream<K, V> filterKeys(Predicate<? super K> predicate) {
    requireNonNull(predicate);
    return filter((k, v) -> predicate.test(k));
  }

  /** Filter this stream to only pairs whose value matches {@code predicate}. */
  public final BiStream<K, V> filterValues(Predicate<? super V> predicate) {
    requireNonNull(predicate);
    return filter((k, v) -> predicate.test(v));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, followed by the pairs in
   * {@code other}.
   *
   * @implNote This method is implemented using {@link Stream#concat}; therefore, the same warnings
   *     about deeply-nested combined streams also apply to this method. In particular, avoid
   *     calling this method in a loop to combine many streams together.
   */
  public final BiStream<K, V> append(BiStream<? extends K, ? extends V> other) {
    return from(Stream.concat(mapToEntry(), other.mapToEntry()));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, followed by the pair of
   * {@code key} and {@code value}.
   *
   * @implNote This method is implemented using {@link Stream#concat}; therefore, the same warnings
   *     about deeply-nested combined streams also apply to this method. In particular, avoid
   *     calling this method in a loop to combine many streams together.
   */
  public final BiStream<K, V> append(K key, V value) {
    return append(of(key, value));
  }

  /** Returns a {@code Stream} consisting of only the keys from each pair in this stream. */
  public final Stream<K> keys() {
    return mapToObj((k, v) -> k);
  }

  /** Returns a {@code Stream} consisting of only the values from each pair in this stream. */
  public final Stream<V> values() {
    return mapToObj((k, v) -> v);
  }

  /**
   * Returns a {@code BiStream} where each pair is a pair from this stream with the key and value
   * swapped.
   */
  public abstract BiStream<V, K> inverse();

  /** Performs {@code action} for each pair in this stream. */
  public abstract void forEach(BiConsumer<? super K, ? super V> action);

  /** Performs {@code action} for each pair in this stream, in order. */
  public abstract void forEachOrdered(BiConsumer<? super K, ? super V> consumer);

  /** Returns true if all pairs in this stream match {@code predicate}. */
  public abstract boolean allMatch(BiPredicate<? super K, ? super V> predicate);

  /** Returns true if any pair in this stream matches {@code predicate}. */
  public abstract boolean anyMatch(BiPredicate<? super K, ? super V> predicate);

  /** Returns true if no pairs in this stream match {@code predicate}. */
  public final boolean noneMatch(BiPredicate<? super K, ? super V> predicate) {
    return !anyMatch(predicate);
  }

  /**
   * Returns a {@code BiStream} consisting of the only the first {@code maxSize} pairs of this
   * stream.
   */
  public abstract BiStream<K, V> limit(int maxSize);

  /**
   * Returns a {@code BiStream} consisting of the remaining pairs from this stream, after discarding
   * the first {@code n} pairs.
   */
  public abstract BiStream<K, V> skip(int n);

  /**
   * Returns a {@code BiStream} consisting of only the distinct pairs (according to {@code
   * Object.equals(Object)} for both key and value).
   */
  public final BiStream<K, V> distinct() {
    return from(mapToEntry().distinct());
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, in the order produced by
   * applying {@code keyComparator} on the keys of each pair, and then for equal keys,
   * applying {@code valueComparator} on the values of each pair.
   */
  public final BiStream<K, V> sorted(
      Comparator<? super K> keyComparator, Comparator<? super V> valueComparator) {
    Comparator<Map.Entry<? extends K, ? extends V>> byKey =
        comparing(Map.Entry::getKey, keyComparator);
    Comparator<Map.Entry<? extends K, ? extends V>> byValue =
        comparing(Map.Entry::getValue, valueComparator);
    return from(mapToEntry().sorted(byKey.thenComparing(byValue)));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, in the order produced by
   * applying {@code comparator} on the keys of each pair.
   */
  public final BiStream<K, V> sortedByKeys(Comparator<? super K> comparator) {
    requireNonNull(comparator);
    return from(mapToEntry().sorted(Comparator.comparing(Map.Entry::getKey, comparator)));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, in the order produced by
   * applying {@code comparator} on the values of each pair.
   */
  public final BiStream<K, V> sortedByValues(Comparator<? super V> comparator) {
    requireNonNull(comparator);
    return from(mapToEntry().sorted(Comparator.comparing(Map.Entry::getValue, comparator)));
  }

  /** Returns the count of pairs in this stream. */
  public final long count() {
    return keys().count();
  }

  /**
   * Returns an immutable {@link Map} that is the result of collecting the pairs in this stream. If a
   * duplicate key is encountered, throws an {@link IllegalStateException}.
   *
   * <p>While this is a convenient shortcut of {@code collect(Collectors::toMap)}, if you have a
   * {@code BiStream<SubFoo, SubBar>}, the return type of {@code toMap()} will be
   * {@code Map<SubFoo, SubBar>}. To collect to {@code Map<Foo, Bar>}, use the equivalent
   * {@code collect(Collectors::toMap)} or {@code collect(BiCollectors.toMap())}.
   */
  public final Map<K, V> toMap() {
    return collect(BiCollectors.toMap());
  }

  /**
   * Returns an object of type {@code R} that is the result of collecting the pairs in this stream
   * using {@code collector}.
   *
   * <p>Please note that any {@code Collector}-returning factory method can be directly
   * "method referenced" as {@link BiCollector} if it accepts two {@code Function} parameters
   * corresponding to the "key" and the "value" parts respectively. For example: {@code
   * collect(Collectors::toConcurrentMap)}, {@code
   * collect(ImmutableSetMultimap::toImmutableSetMultimap)}, {@code
   * collect(Maps::toImmutableEnumMap)}, {@code collect(ImmutableBiMap::toImmutableBiMap)}.
   *
   * <p>In addition, check out {@link BiCollectors} for some other useful {@link BiCollector}
   * implementations.
   */
  public abstract <R> R collect(BiCollector<? super K, ? super V, R> collector);

  static <K, V> Map.Entry<K, V> kv(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  private static <T> Stream<T> nullToEmpty(Stream<T> stream) {
    return stream == null ? Stream.empty() : stream;
  }

  /**
   * An implementation that operates on a generic entry type {@code <E>} using two functions to
   * extract the 'key' and 'value' from each entry.
   *
   * <p>Because the {@code toKey} and {@code toValue} functions could be arbitrary custom functions
   * that are expensive or even with side-effects, it is strictly guaranteed that for any single
   * entry in the stream, each function is invoked exactly once.
   *
   * <p>Common methods like {@link #mapKeys(Function)}, {@link #mapValues(Function)}, {@link #keys},
   * {@link #values}, {@link #forEach} etc. can avoid allocating intermediary {@link Map.Entry}
   * instances by either invoking {@code toKey} and {@code toValue} then using the results directly,
   * or composing the functions to be invoked later.
   *
   * <p>Doing so isn't always feasible. For example {@link #filter} and {@link #peek} both need to
   * evaluate the entry by invoking {@code toKey} and {@code toValue} and the return values need to
   * be stored to avoid invoking the functions again. For these cases, the stream will be
   * degeneralized into {@code Stream<Map.Entry<K, V>>} so as to guarantee the "at-most-once"
   * semantic.
   */
  private static class GenericEntryStream<E, K, V> extends BiStream<K, V> {
    private final Stream<E> underlying;
    private final Function<? super E, ? extends K> toKey;
    private final Function<? super E, ? extends V> toValue;

    GenericEntryStream(
        Stream<E> underlying,
        Function<? super E, ? extends K> toKey,
        Function<? super E, ? extends V> toValue) {
      this.underlying = requireNonNull(underlying);
      this.toKey = requireNonNull(toKey);
      this.toValue = requireNonNull(toValue);
    }

    @Override
    public final <T> Stream<T> mapToObj(BiFunction<? super K, ? super V, ? extends T> mapper) {
      return underlying.map(forEntry(mapper));
    }

    @Override
    public final DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return underlying.mapToDouble(e -> mapper.applyAsDouble(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return underlying.mapToInt(e -> mapper.applyAsInt(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return underlying.mapToLong(e -> mapper.applyAsLong(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final <K2> BiStream<K2, V> mapKeys(Function<? super K, ? extends K2> keyMapper) {
      return from(underlying, toKey.andThen(keyMapper), toValue);
    }

    @Override
    public final <V2> BiStream<K, V2> mapValues(Function<? super V, ? extends V2> valueMapper) {
      return from(underlying, toKey, toValue.andThen(valueMapper));
    }

    @Override
    public final BiStream<V, K> inverse() {
      return from(underlying, toValue, toKey);
    }

    @Override
    public final void forEach(BiConsumer<? super K, ? super V> action) {
      requireNonNull(action);
      underlying.forEach(e -> action.accept(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final void forEachOrdered(BiConsumer<? super K, ? super V> action) {
      requireNonNull(action);
      underlying.forEachOrdered(e -> action.accept(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final boolean allMatch(BiPredicate<? super K, ? super V> predicate) {
      requireNonNull(predicate);
      return underlying.allMatch(e -> predicate.test(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public final boolean anyMatch(BiPredicate<? super K, ? super V> predicate) {
      requireNonNull(predicate);
      return underlying.anyMatch(e -> predicate.test(toKey.apply(e), toValue.apply(e)));
    }

    @Override
    public BiStream<K, V> limit(int maxSize) {
      return from(underlying.limit(maxSize), toKey, toValue);
    }

    @Override
    public BiStream<K, V> skip(int n) {
      return from(underlying.skip(n), toKey, toValue);
    }

    @Override
    public final <R> R collect(BiCollector<? super K, ? super V, R> collector) {
      return underlying.collect(collector.bisecting(toKey::apply, toValue::apply));
    }

    final <T> Function<E, T> forEntry(BiFunction<? super K, ? super V, T> function) {
      requireNonNull(function);
      return e -> function.apply(toKey.apply(e), toValue.apply(e));
    }
  }

  private static final class ZippingStream<K, V> extends BiStream<K, V> {
    private final Stream<K> left;
    private final Stream<V> right;

    ZippingStream(Stream<K> left, Stream<V> right) {
      this.left = requireNonNull(left);
      this.right = requireNonNull(right);
    }

    @Override
    public <T> Stream<T> mapToObj(BiFunction<? super K, ? super V, ? extends T> mapper) {
      requireNonNull(mapper);
      return stream(() -> new Spliteration().<T>ofObj(mapper), ORDERED, /*parallel=*/ false)
          .onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return doubleStream(() -> new Spliteration().ofDouble(mapper), ORDERED, /*parallel=*/ false)
          .onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return intStream(() -> new Spliteration().ofInt(mapper), ORDERED, /*parallel=*/ false)
          .onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return longStream(() -> new Spliteration().ofLong(mapper), ORDERED, /*parallel=*/ false)
          .onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public <K2> BiStream<K2, V> mapKeys(Function<? super K, ? extends K2> keyMapper) {
      return zip(left.map(keyMapper), right);
    }

    @Override
    public <V2> BiStream<K, V2> mapValues(Function<? super V, ? extends V2> valueMapper) {
      return zip(left, right.map(valueMapper));
    }

    @Override
    public BiStream<V, K> inverse() {
      return zip(right, left);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
      forEachOrdered(action);
    }

    @Override
    public void forEachOrdered(BiConsumer<? super K, ? super V> action) {
      requireNonNull(action);
      new Spliteration().forEach(action);
    }

    @Override
    public boolean allMatch(BiPredicate<? super K, ? super V> predicate) {
      requireNonNull(predicate);
      return new Spliteration().any(false, predicate); // any false means false
    }

    @Override
    public boolean anyMatch(BiPredicate<? super K, ? super V> predicate) {
      requireNonNull(predicate);
      return new Spliteration().any(true, predicate); // any true means true
    }

    @Override
    public BiStream<K, V> limit(int maxSize) {
      return zip(left.limit(maxSize), right.limit(maxSize));
    }

    @Override
    public BiStream<K, V> skip(int n) {
      return zip(left.skip(n), right.skip(n));
    }

    @Override
    public <R> R collect(BiCollector<? super K, ? super V, R> collector) {
      requireNonNull(collector);
      return new Spliteration().collectWith(collector);
    }

    private final class Spliteration {
      private final Temp<K> currentLeft = new Temp<>();
      private final Temp<V> currentRight = new Temp<>();
      private final Spliterator<K> leftIt = left.spliterator();
      private final Spliterator<V> rightIt = right.spliterator();

      /**
       * Returns {@code dominatingResult} if {@code predicate} evaluates to {@code dominatingResult}
       * for any pair, or else returns {@code !dominatingResult}.
       */
      boolean any(boolean dominatingResult, BiPredicate<? super K, ? super V> predicate) {
        while (advance()) {
          if (predicate.test(currentLeft.value, currentRight.value) == dominatingResult) {
            return dominatingResult;
          }
        }
        return !dominatingResult;
      }

      void forEach(BiConsumer<? super K, ? super V> consumer) {
        while (advance()) {
          consumer.accept(currentLeft.value, currentRight.value);
        }
      }

      <T> Spliterator<T> ofObj(BiFunction<? super K, ? super V, ? extends T> mapper) {
        return new AbstractSpliterator<T>(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(Consumer<? super T> consumer) {
            return advance() && emit(mapper.apply(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      Spliterator.OfInt ofInt(ToIntBiFunction<? super K, ? super V> mapper) {
        return new AbstractIntSpliterator(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(IntConsumer consumer) {
            return advance()
                && emit(mapper.applyAsInt(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      Spliterator.OfLong ofLong(ToLongBiFunction<? super K, ? super V> mapper) {
        return new AbstractLongSpliterator(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(LongConsumer consumer) {
            return advance()
                && emit(mapper.applyAsLong(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      Spliterator.OfDouble ofDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
        return new AbstractDoubleSpliterator(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(DoubleConsumer consumer) {
            return advance()
                && emit(mapper.applyAsDouble(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      <R> R collectWith(BiCollector<? super K, ? super V, R> collector) {
        return collectWith(collector.bisecting(x -> currentLeft.value, x -> currentRight.value));
      }

      /** {@code collector} internally reads from {@link #currentLeft} and {@link #currentRight}. */
      private <A, R> R collectWith(Collector<Void, A, R> collector) {
        A container = collector.supplier().get();
        BiConsumer<A, Void> accumulator = collector.accumulator();
        while (advance()) {
          accumulator.accept(container, null);
        }
        return collector.finisher().apply(container);
      }

      private boolean advance() {
        return leftIt.tryAdvance(currentLeft) && rightIt.tryAdvance(currentRight);
      }

      private long estimateSize() {
        return Math.min(leftIt.estimateSize(), rightIt.estimateSize());
      }
    }

    private static boolean emit(int result, IntConsumer consumer) {
      consumer.accept(result);
      return true;
    }

    private static boolean emit(long result, LongConsumer consumer) {
      consumer.accept(result);
      return true;
    }

    private static boolean emit(double result, DoubleConsumer consumer) {
      consumer.accept(result);
      return true;
    }

    private static <T> boolean emit(T result, Consumer<? super T> consumer) {
      consumer.accept(result);
      return true;
    }

    private static final class Temp<T> implements Consumer<T> {
      T value;

      @Override
      public void accept(T value) {
        this.value = value;
      }
    }
  }
}

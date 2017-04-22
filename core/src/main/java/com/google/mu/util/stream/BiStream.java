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

import static com.google.mu.util.stream.MoreStreams.iterateThrough;
import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToLongBiFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.mu.function.CheckedBiConsumer;

/**
 * A {@code Stream}-like object making it easier to handle pairs of objects.
 *
 * <p>Throughout this class, "key-value" metaphor is adopted for method names and type names.
 * This naming convention however does not imply uniqueness in terms of type {@code <K>} nor does
 * it require {@link Object#equals} (except for {@link #distinct}).
 * Technically a key-value pair is nothing but two arbitrary objects.
 *
 * <p>This "key-value" metaphor doesn't always make sense in the problem domain. For example,
 * you may be looking at a pair of doctor and patient; neither is a "key" therefore using methods
 * like {@link #filterKeys filterKeys()} may introduce noise to the code. It may improve
 * readability in such cases to avoid these {@code *Keys()}, {@code *Values()} convenience methods
 * and prefer the pair-wise methods. Like, instead of {@code
 * doctorsAndPatients.filterKeys(Doctor::isInNetwork)},
 * consider to use {@code doctorsAndPatients.filter((doctor, patient) -> doctor.isInNetwork())}.
 *
 * <p>Keys and values are allowed to be null.
 *
 * @since 1.1
 */
public final class BiStream<K, V> implements AutoCloseable {
  final Stream<? extends Map.Entry<? extends K, ? extends V>> underlying;

  private BiStream(Stream<? extends Entry<? extends K, ? extends V>> underlying) {
    this.underlying = requireNonNull(underlying);
  }

  /** Returns an empty stream. */
  public static <K, V> BiStream<K, V> empty() {
    return from(Stream.empty());
  }

  /** Returns a stream for {@code key} and {@code value}. */
  public static <K, V> BiStream<K, V> of(K key, V value) {
    return from(Stream.of(kv(key, value)));
  }

  /** Returns a stream for two pairs. */
  public static <K, V> BiStream<K, V> of(K key1, V value1, K key2, V value2) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2)));
  }

  /** Returns a stream for three pairs. */
  public static <K, V> BiStream<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3)));
  }

  /** Returns a stream for four pairs. */
  public static <K, V> BiStream<K, V> of(
      K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3), kv(key4, value4)));
  }

  /** Returns a stream for five pairs. */
  public static <K, V> BiStream<K, V> of(
      K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4, K key5, V value5) {
    return from(Stream.of(
        kv(key1, value1), kv(key2, value2), kv(key3, value3), kv(key4, value4), kv(key5, value5)));
  }

  /**
   * Wraps {@code stream} as a {@link BiStream}. Users will typically chain
   * {@link #mapKeys mapKeys()} or {@link #mapValues mapValues()}. For example:   <pre>{@code
   * BiStream<UserId, Profile> profilesByUserId = biStream(users.stream())
   *     .mapKeys(User::getId)
   *     .mapValues(User::getProfile);
   * }</pre>
   */
  public static <T> BiStream<T, T> biStream(Stream<? extends T> stream) {
    return from(stream.map(t -> kv(t, t)));
  }

  /** Wraps {@code entries} in a {@code BiStream}. */
  public static <K, V> BiStream<K, V> from(
      Stream<? extends Map.Entry<? extends K, ? extends V>> entries) {
    return new BiStream<>(entries);
  }

  /** Wraps entries from {@code map} in a {@code BiStream}. */
  public static <K, V> BiStream<K, V> from(Map<? extends K, ? extends V> map) {
    return from(map.entrySet().stream());
  }

  /**
   * Zips up {@code keys} and {@code values} into a {@code BiStream} in order.
   * If one streams is longer than the other, the extra elements in the longer stream are
   * silently ignored.
   *
   * <p>For example:   <pre>{@code
   * BiStream.zip(Stream.of("a", "b", "c"), Stream.of(1, 2))
   *     .map((x, y) -> x + ":" + y)
   * }</pre>
   * will return a stream equivalent to {@code Stream.of("a:1", "b:2")}.
   */
  public static <K, V> BiStream<K, V> zip(
      Stream<? extends K> keys, Stream<? extends V> values) {
    Stream<Map.Entry<K, V>> paired = StreamSupport.stream(
            () -> new PairedUpSpliterator<>(keys.spliterator(), values.spliterator()),
            Spliterator.NONNULL, keys.isParallel() || values.isParallel());
    return from(paired.onClose(keys::close).onClose(values::close));
  }

  /**
   * Returns a {@link BiStream} where each element in {@code values} is keyed by its
   * corresponding 0-based index. For example, the following code transforms a list
   * of inputs into a pre-sized output list:   <pre>{@code
   * List<T> output = ...;
   * BiStream.indexed(inputs.stream())
   *     .mapValues(this::convertInput)
   *     .forEach(output::set);
   * }</pre>
   */
  public static <V> BiStream<Integer, V> indexed(Stream<? extends V> values) {
    return zip(IntStream.iterate(0, i -> i + 1).boxed(), values);
  }

  /** Maps the pair to a new object of type {@code T}. */
  public <T> Stream<T> map(BiFunction<? super K, ? super V, ? extends T> mapper) {
    return underlying.map(forEntries(mapper));
  }

  /** Maps each pair to an {@code int}. */
  public IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return underlying.mapToInt(kv -> mapper.applyAsInt(kv.getKey(), kv.getValue()));
  }

  /** Maps each pair to a {@code long}. */
  public LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return underlying.mapToLong(kv -> mapper.applyAsLong(kv.getKey(), kv.getValue()));
  }

  /** Maps each pair to a {@code double}. */
  public DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
    requireNonNull(mapper);
    return underlying.mapToDouble(kv -> mapper.applyAsDouble(kv.getKey(), kv.getValue()));
  }

  /** Maps a single pair to zero or more objects of type {@code T}. */
  public <T> Stream<T> flatMap(BiFunction<? super K, ? super V, ? extends Stream<T>> mapper) {
    return underlying.flatMap(forEntries(mapper));
  }

  /** Maps a single pair to zero or more {@code int}s. */
  public IntStream flatMapToInt(BiFunction<? super K, ? super V, ? extends IntStream> mapper) {
    return underlying.flatMapToInt(forEntries(mapper));
  }

  /** Maps a single pair to zero or more {@code long}s. */
  public LongStream flatMapToLong(BiFunction<? super K, ? super V, ? extends LongStream> mapper) {
    return underlying.flatMapToLong(forEntries(mapper));
  }

  /** Maps a single pair to zero or more {@code double}s. */
  public DoubleStream flatMapToDouble(BiFunction<? super K, ? super V, ? extends DoubleStream> mapper) {
    return underlying.flatMapToDouble(forEntries(mapper));
  }

  /** Maps a single pair to zero or more pairs in another {@code BiStream}. */
  public <K2, V2> BiStream<K2, V2> flatMap2(
      BiFunction<? super K, ? super V, ? extends BiStream<? extends K2, ? extends V2>> mapper) {
    requireNonNull(mapper);
    return from(underlying.flatMap(kv -> mapper.apply(kv.getKey(), kv.getValue()).underlying));
  }

  /** Maps the pair to a new pair of type {@code K2} and {@code V2}. */
  public <K2, V2> BiStream<K2, V2> map(
      BiFunction<? super K, ? super V, ? extends K2> keyMapper,
      BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    requireNonNull(keyMapper);
    requireNonNull(valueMapper);
    return map2((k, v) -> kv(keyMapper.apply(k, v), valueMapper.apply(k, v)));
  }

  /** Maps each key to another key of type {@code K2}. */
  public <K2> BiStream<K2, V> mapKeys(BiFunction<? super K, ? super V, ? extends K2> keyMapper) {
    requireNonNull(keyMapper);
    return map2((k, v) -> kv(keyMapper.apply(k, v), v));
  }

  /** Maps each key to another key of type {@code K2}. */
  public <K2> BiStream<K2, V> mapKeys(Function<? super K, ? extends K2> keyMapper) {
    requireNonNull(keyMapper);
    return map2((k, v) -> kv(keyMapper.apply(k), v));
  }

  /** Maps each key to zero or more keys of type {@code K2}. */
  public <K2> BiStream<K2, V> flatMapKeys(
      BiFunction<? super K, ? super V, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return flatMap2((k, v) -> from(keyMapper.apply(k, v).map(k2 -> kv(k2, v))));
  }

  /** Maps each key to zero or more keys of type {@code K2}. */
  public <K2> BiStream<K2, V> flatMapKeys(
      Function<? super K, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return flatMapKeys((k, v) -> keyMapper.apply(k));
  }

  /** Maps each value to another value of type {@code V2}. */
  public <V2> BiStream<K, V2> mapValues(
      BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    requireNonNull(valueMapper);
    return map2((k, v) -> kv(k, valueMapper.apply(k, v)));
  }

  /** Maps each value to another value of type {@code V2}. */
  public <V2> BiStream<K, V2> mapValues(Function<? super V, ? extends V2> valueMapper) {
    requireNonNull(valueMapper);
    return map2((k, v) -> kv(k, valueMapper.apply(v)));
  }

  /** Maps each value to zero ore more values of type {@code V2}. */
  public <V2> BiStream<K, V2> flatMapValues(
      BiFunction<? super K, ? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return flatMap2((k, v) -> from(valueMapper.apply(k, v).map(v2 -> kv(k, v2))));
  }

  /** Maps each value to zero ore more values of type {@code V2}. */
  public <V2> BiStream<K, V2> flatMapValues(
      Function<? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return flatMapValues((k, v) -> valueMapper.apply(v));
  }

  /** Peeks each pair. */
  public BiStream<K, V> peek(BiConsumer<? super K, ? super V> peeker) {
    return from(underlying.peek(forEntries(peeker)));
  }

  /** Filter using {@code predicate}. */
  public BiStream<K, V> filter(BiPredicate<? super K, ? super V> predicate) {
    return from(underlying.filter(forEntries(predicate)));
  }

  /** Filter keys using {@code predicate}. */
  public BiStream<K, V> filterKeys(Predicate<? super K> predicate) {
    return filter(forKeys(predicate));
  }

  /** Filter values using {@code predicate}. */
  public BiStream<K, V> filterValues(Predicate<? super V> predicate) {
    return filter(forValues(predicate));
  }

  /** Returns a new stream with {@code suffix} appended. */
  public BiStream<K, V> append(BiStream<? extends K, ? extends V> suffix) {
    return from(Stream.concat(underlying, suffix.underlying));
  }

  /** Returns a new stream with {@code key} and {@code value} appended. */
  public BiStream<K, V> append(K key, V value) {
    return append(of(key, value));
  }

  /** Returns the key stream. */
  public Stream<K> keys() {
    return underlying.map(Map.Entry::getKey);
  }

  /** Returns the value stream. */
  public Stream<V> values() {
    return underlying.map(Map.Entry::getValue);
  }

  /**
   * Collects the stream into a {@code Map<K, V>}.
   * Duplicate keys results in {@link IllegalStateException}.
   *
   * <p>Equivalent to {@code collect(Collectors::toMap)}.
   *
   * @since 1.2
   */
  public Map<K, V> toMap() {
    // TODO: collect(Collectors::toMap) compiles in Eclipse but not in current javac.
    BiCollector<K, V, Map<K, V>> collector = Collectors::toMap;
    return collect(collector);
  }

  /**
   * Collects the stream into a {@code ConcurrentMap<K, V>}.
   * Duplicate keys results in {@link IllegalStateException}.
   *
   * <p>Equivalent to {@code collect(Collectors::toConcurrentMap)}.
   *
   * @since 1.2
   */
  public ConcurrentMap<K, V> toConcurrentMap() {
    // TODO: collect(Collectors::toConcurrentMap) compiles in Eclipse but not in current javac.
    BiCollector<K, V, ConcurrentMap<K, V>> collector = Collectors::toConcurrentMap;
    return collect(collector);
  }

  /**
   * Collects the stream into type {@code R} using {@code collector}.
   *
   * @since 1.2
   */
  public <R> R collect(BiCollector<? super K, ? super V, ? extends R> collector) {
    return underlying.collect(collector.asCollector(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Iterates through each pair sequentially.
   * {@code consumer} is allowed to throw a checked exception.
   */
  public <E extends Throwable> void forEachSequentially(
      CheckedBiConsumer<? super K, ? super V, E> consumer) throws E {
    requireNonNull(consumer);
    iterateThrough(underlying, kv -> consumer.accept(kv.getKey(), kv.getValue()));
  }

  /** Iterates over each pair. */
  public void forEach(BiConsumer<? super K, ? super V> consumer) {
    underlying.forEach(forEntries(consumer));
  }

  /** Iterates over each pair in order. */
  public void forEachOrdered(BiConsumer<? super K, ? super V> consumer) {
    underlying.forEachOrdered(forEntries(consumer));
  }

  /** Do all pairs match {@code predicate}? */
  public boolean allMatch(BiPredicate<? super K, ? super V> predicate) {
    return underlying.allMatch(forEntries(predicate));
  }

  /** Does any pair match {@code predicate}? */
  public boolean anyMatch(BiPredicate<? super K, ? super V> predicate) {
    return underlying.anyMatch(forEntries(predicate));
  }

  /** Do no pairs match {@code predicate}? */
  public boolean noneMatch(BiPredicate<? super K, ? super V> predicate) {
    return underlying.noneMatch(forEntries(predicate));
  }

  /** Limit the number of pairs. */
  public BiStream<K, V> limit(int num) {
    return from(underlying.limit(num));
  }

  /** Skips the first {@code n} pairs. */
  public BiStream<K, V> skip(int n) {
    return from(underlying.skip(n));
  }

  /** Keep only distinct pairs. */
  public BiStream<K, V> distinct() {
    return from(underlying.distinct());
  }

  /** Returns an instance forcing parallel computation. */
  public BiStream<K, V> parallel() {
    return from(underlying.parallel());
  }

  /** Returns an instance forcing sequential computation. */
  public BiStream<K, V> sequential() {
    return from(underlying.sequential());
  }

  /** Returns a sorted stream based on {@code keyOrdering} and {@code valueOrdering}. */
  public BiStream<K, V> sorted(
      Comparator<? super K> keyOrdering, Comparator<? super V> valueOrdering) {
    Comparator<Map.Entry<? extends K, ? extends V>> byKey =
        Comparator.comparing(Map.Entry::getKey, keyOrdering);
    Comparator<Map.Entry<? extends K, ? extends V>> byValue =
        Comparator.comparing(Map.Entry::getValue, valueOrdering);
    return from(underlying.sorted(byKey.thenComparing(byValue)));
  }

  /** Returns an instance with keys sorted according to {@code order}. */
  public BiStream<K, V> sortedByKeys(Comparator<? super K> ordering) {
    return from(underlying.sorted(forKeys(ordering)));
  }

  /** Returns an instance with values sorted according to {@code order}. */
  public BiStream<K, V> sortedByValues(Comparator<? super V> ordering) {
    return from(underlying.sorted(forValues(ordering)));
  }

  /** Returns the number of pairs in this stream. */
  public long count() {
    return underlying.count();
  }

  /** Is this stream parallel? */
  public boolean isParellel() {
    return underlying.isParallel();
  }

  /** Closes this stream, if any. */
  @Override public void close() {
    underlying.close();
  }

  /** Builds {@link BiStream}. */
  public static final class Builder<K, V> {
    private final Stream.Builder<Map.Entry<K, V>> entries = Stream.builder();

    /** Puts a new pair of {@code key} and {@code value}. */
    public Builder<K, V> put(K key, V value) {
      entries.add(kv(key, value));
      return this;
    }

    /** Puts all key-value pairs from {@code map} into this builder. */
    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
        entries.add(kv(entry.getKey(), entry.getValue()));
      }
      return this;
    }

    /**
     * Returns a new {@link BiStream} encapsulating the snapshot of pairs in this builder
     * at the time {@code build()} is invoked.
     */
    public BiStream<K, V> build() {
      return from(entries.build());
    }
  }

  private <K2, V2> BiStream<K2, V2> map2(
      BiFunction<? super K, ? super V, ? extends Map.Entry<? extends K2, ? extends V2>> mapper) {
    return from(underlying.map(forEntries(mapper)));
  }

  private static <K, V> Map.Entry<K, V> kv(K key, V value) {
    return new AbstractMap.SimpleEntry<>(key, value);
  }

  private static <K, V, T> Function<Map.Entry<? extends K, ? extends V>, T> forEntries(
      BiFunction<? super K, ? super V, ? extends T> function) {
    requireNonNull(function);
    return kv -> function.apply(kv.getKey(), kv.getValue());
  }

  private static <K, V> Predicate<Map.Entry<? extends K, ? extends V>> forEntries(
      BiPredicate<? super K, ? super V> predicate) {
    requireNonNull(predicate);
    return kv -> predicate.test(kv.getKey(), kv.getValue());
  }

  private static <K, V> Consumer<Map.Entry<? extends K, ? extends V>> forEntries(
      BiConsumer<? super K, ? super V> consumer) {
    requireNonNull(consumer);
    return kv -> consumer.accept(kv.getKey(), kv.getValue());
  }

  private static <K> BiPredicate<K, Object> forKeys(Predicate<? super K> predicate) {
    requireNonNull(predicate);
    return (k, v) -> predicate.test(k);
  }

  private static <V> BiPredicate<Object, V> forValues(Predicate<? super V> predicate) {
    requireNonNull(predicate);
    return (k, v) -> predicate.test(v);
  }

  private Comparator<Entry<? extends K, ? extends V>> forKeys(Comparator<? super K> ordering) {
    return Comparator.comparing(Map.Entry::getKey, ordering);
  }

  private Comparator<Entry<? extends K, ? extends V>> forValues(Comparator<? super V> ordering) {
    return Comparator.comparing(Map.Entry::getValue, ordering);
  }

  private static final class PairedUpSpliterator<K, V> implements Spliterator<Map.Entry<K, V>> {
    private final Spliterator<? extends K> keys;
    private final Spliterator<? extends V> values;
  
    PairedUpSpliterator(Spliterator<? extends K> keys, Spliterator<? extends V> values) {
      this.keys = requireNonNull(keys);
      this.values = requireNonNull(values);
    }

    @Override public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
      requireNonNull(action);
      KV<K, V> kv = new KV<>();
      boolean advanced = keys.tryAdvance(k -> kv.key = k) && values.tryAdvance(v -> kv.value = v);
      if (advanced) action.accept(kv(kv.key, kv.value));
      return advanced;
    }

    @Override public Spliterator<Map.Entry<K, V>> trySplit() {
      return null;
    }

    @Override public long estimateSize() {
      return Math.min(keys.estimateSize(), values.estimateSize());
    }

    @Override public int characteristics() {
      return Spliterator.NONNULL;
    }
  }

  private static final class KV<K, V> {
    K key;
    V value;
  }
}

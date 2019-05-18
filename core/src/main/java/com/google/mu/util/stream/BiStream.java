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
import static java.util.Spliterator.ORDERED;
import static java.util.function.Function.identity;
import static java.util.stream.StreamSupport.doubleStream;
import static java.util.stream.StreamSupport.intStream;
import static java.util.stream.StreamSupport.longStream;
import static java.util.stream.StreamSupport.stream;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A {@code Stream}-like object making it easier to handle pairs of objects.
 *
 * <p>
 * Throughout this class, "key-value" metaphor is adopted for method names and
 * type names. This naming convention however does not imply uniqueness in terms
 * of type {@code <K>} nor does it require {@link Object#equals} (except for
 * {@link #distinct}). Technically a key-value pair is nothing but two arbitrary
 * objects.
 *
 * <p>
 * This "key-value" metaphor doesn't always make sense in the problem domain.
 * For example, you may be looking at a pair of doctor and patient; neither is a
 * "key" therefore using methods like {@link #filterKeys filterKeys()} may
 * introduce noise to the code. It may improve readability in such cases to
 * avoid these {@code *Keys()}, {@code *Values()} convenience methods and prefer
 * the pair-wise methods. Like, instead of {@code
 * doctorsAndPatients.filterKeys(Doctor::isInNetwork)}, consider to use
 * {@code doctorsAndPatients.filter((doctor, patient) -> doctor.isInNetwork())}.
 *
 * <p>
 * Keys and values are allowed to be null.
 *
 * @since 1.1
 */
public abstract class BiStream<K, V> {

  /** Returns an empty {@code BiStream}. */
  public static <K, V> BiStream<K, V> empty() {
    return from(Stream.empty());
  }

  /**
   * Returns a {@code BiStream} of a single pair containing {@code key} and
   * {@code value}.
   */
  public static <K, V> BiStream<K, V> of(K key, V value) {
    return from(Stream.of(kv(key, value)));
  }

  /**
   * Returns a {@code BiStream} of two pairs, containing the supplied keys and
   * values.
   */
  public static <K, V> BiStream<K, V> of(K key1, V value1, K key2, V value2) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2)));
  }

  /**
   * Returns a {@code BiStream} of three pairs, containing the supplied keys and
   * values.
   */
  public static <K, V> BiStream<K, V> of(K key1, V value1, K key2, V value2, K key3, V value3) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3)));
  }

  /**
   * Returns a {@code BiStream} of four pairs, containing the supplied keys and
   * values.
   */
  public static <K, V> BiStream<K, V> of(
      K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3), kv(key4, value4)));
  }

  /**
   * Returns a {@code BiStream} of five pairs, containing the supplied keys and
   * values.
   */
  public static <K, V> BiStream<K, V> of(
      K key1, V value1, K key2, V value2, K key3, V value3, K key4, V value4, K key5, V value5) {
    return from(Stream.of(kv(key1, value1), kv(key2, value2), kv(key3, value3), kv(key4, value4), kv(key5, value5)));
  }

  /**
   * Returns a {@code BiStream} in which the first element in {@code left} is
   * paired with the first element in {@code right}; the second paired with the
   * corresponding second and the third with the corresponding third etc. For
   * example: {@code BiStream.zip(asList(1, 2, 3), asList("one",
   * "two"))} will return {@code BiStream.of(1, "one", 2, "two")}.
   *
   * <p>
   * The resulting stream will only be as long as the shorter of the two
   * iterables; if one is longer, its extra elements will be ignored.
   */
  public static <L, R> BiStream<L, R> zip(Collection<L> left, Collection<R> right) {
    return zip(left.stream(), right.stream());
  }

  /**
   * Returns a {@code BiStream} in which the first element in {@code left} is
   * paired with the first element in {@code right}; the second paired with the
   * corresponding second and the third with the corresponding third etc. For
   * example: {@code BiStream.zip(Stream.of(1, 2, 3),
   * Stream.of("one", "two"))} will return
   * {@code BiStream.of(1, "one", 2, "two")}.
   *
   * <p>
   * The resulting stream will only be as long as the shorter of the two input
   * streams; if one stream is longer, its extra elements will be ignored.
   *
   * <p>
   * The resulting stream by default runs sequentially regardless of the input
   * streams. This is because the implementation is not <a href=
   * "http://gee.cs.oswego.edu/dl/html/StreamParallelGuidance.html">efficiently
   * splittable</a>. and may not perform well if run in parallel.
   */
  public static <L, R> BiStream<L, R> zip(Stream<L> left, Stream<R> right) {
    return new ZippingStream<>(left, right);
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
   *
   * <p>Because Java runtime typically caches {@code Integer} instances for the range of
   * {@code [0, 128]}, auto-boxing cost is negligible for small streams.
   */
  public static <V> BiStream<Integer, V> indexed(Stream<V> values) {
    return zip(IntStream.iterate(0, i -> i + 1).boxed(), values);
  }

  /** Returns a {@code BiStream} of the entries in {@code map}. */
  public static <K, V> BiStream<K, V> from(Map<K, V> map) {
    return from(map.entrySet().stream());
  }

  /**
   * Returns a {@code BiStream} of {@code elements}, each transformed to a pair of
   * values with {@code toKey} and {@toValue}.
   */
  public static <T, K, V> BiStream<K, V> from(Collection<T> elements, Function<? super T, ? extends K> toKey,
      Function<? super T, ? extends V> toValue) {
    return from(elements.stream(), toKey, toValue);
  }

  /**
   * Returns a {@code BiStream} of the elements from {@code stream}, each
   * transformed to a pair of values with {@code toKey} and {@toValue}.
   */
  public static <T, K, V> BiStream<K, V> from(Stream<T> stream, Function<? super T, ? extends K> toKey,
      Function<? super T, ? extends V> toValue) {
    return new GenericEntryStream<>(stream, toKey, toValue);
  }

  static <K, V, E extends Map.Entry<? extends K, ? extends V>> BiStream<K, V> from(Stream<E> entryStream) {
    return new GenericEntryStream<E, K, V>(entryStream, Map.Entry::getKey, Map.Entry::getValue) {
      @Override
      public <K2, V2> BiStream<K2, V2> map(BiFunction<? super K, ? super V, ? extends K2> keyMapper,
          BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
        return from(entryStream, forEntry(keyMapper), forEntry(valueMapper));
      }

      @Override
      public <K2> BiStream<K2, V> mapKeys(BiFunction<? super K, ? super V, ? extends K2> keyMapper) {
        return from(entryStream, forEntry(keyMapper), Map.Entry::getValue);
      }

      @Override
      public <V2> BiStream<K, V2> mapValues(BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
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
   * Returns a {@code Stream} consisting of the results of applying {@code mapper}
   * to each pair in this {@code BiStream}.
   *
   * <p>
   * To simply collect mapped pairs to a list or set, use {@link #toList} or
   * {@link #toSet}.
   */
  public abstract <T> Stream<T> mapToObj(BiFunction<? super K, ? super V, ? extends T> mapper);

  /**
   * Returns a {@code BiStream} consisting of the results of applying
   * {@code keyMapper} and {@code
   * valueMapper} to the pairs in this {@code BiStream}.
   */
  public <K2, V2> BiStream<K2, V2> map(BiFunction<? super K, ? super V, ? extends K2> keyMapper,
      BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    requireNonNull(keyMapper);
    requireNonNull(valueMapper);
    return from(mapToObj((k, v) -> kv(keyMapper.apply(k, v), valueMapper.apply(k, v))));
  }

  /**
   * Returns a {@link DoubleStream} consisting of the results of applying
   * {@code mapper} to the pairs in this {@code BiStream}.
   */
  public abstract DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper);

  /**
   * Returns an {@link IntStream} consisting of the results of applying
   * {@code mapper} to the pairs in this {@code BiStream}.
   */
  public abstract IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper);

  /**
   * Returns a {@link LongStream} consisting of the results of applying
   * {@code mapper} to the pairs in this {@code BiStream}.
   */
  public abstract LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper);

  /**
   * Returns a {@code BiStream} of pairs whose keys are the result of applying
   * {@code keyMapper} to the key of each pair in this {@code BiStream}, and whose
   * values are unchanged.
   */
  public <K2> BiStream<K2, V> mapKeys(BiFunction<? super K, ? super V, ? extends K2> keyMapper) {
    return map(keyMapper, (k, v) -> v);
  }

  /** Maps each key to another key of type {@code K2}. */
  public abstract <K2> BiStream<K2, V> mapKeys(Function<? super K, ? extends K2> keyMapper);

  /** Maps each value to another value of type {@code V2}. */
  public <V2> BiStream<K, V2> mapValues(BiFunction<? super K, ? super V, ? extends V2> valueMapper) {
    return map((k, v) -> k, valueMapper);
  }

  /** Maps each value to another value of type {@code V2}. */
  public abstract <V2> BiStream<K, V2> mapValues(Function<? super V, ? extends V2> valueMapper);

  /**
   * Maps a single pair to zero or more objects of type {@code T}.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <T> Stream<T> flatMapToObj(BiFunction<? super K, ? super V, ? extends Stream<? extends T>> mapper) {
    return mapToObj(mapper).flatMap(identity());
  }

  /**
   * Maps a single pair to zero or more {@code double}s.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final DoubleStream flatMapToDouble(BiFunction<? super K, ? super V, ? extends DoubleStream> mapper) {
    return mapToObj(mapper).flatMapToDouble(identity());
  }

  /**
   * Maps a single pair to zero or more {@code int}s.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final IntStream flatMapToInt(BiFunction<? super K, ? super V, ? extends IntStream> mapper) {
    return mapToObj(mapper).flatMapToInt(identity());
  }

  /**
   * Maps a single pair to zero or more {@code long}s.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final LongStream flatMapToLong(BiFunction<? super K, ? super V, ? extends LongStream> mapper) {
    return mapToObj(mapper).flatMapToLong(identity());
  }

  /**
   * Maps each pair in this stream to zero or more pairs in another
   * {@code BiStream}. For example the following code snippet repeats each pair in
   * a {@code BiStream} for 3 times:
   *
   * <pre>
   * {
   *   &#64;code
   *   BiStream<K, V> repeated = stream.flatMap((k, v) -> BiStream.of(k, v, k, v, k, v));
   * }
   * </pre>
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2, V2> BiStream<K2, V2> flatMap(
      BiFunction<? super K, ? super V, ? extends BiStream<? extends K2, ? extends V2>> mapper) {
    return from(mapToObj(mapper).filter(s -> s != null).flatMap(BiStream::mapToEntry));
  }

  /**
   * Maps each key to zero or more keys of type {@code K2}.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2> BiStream<K2, V> flatMapKeys(
      BiFunction<? super K, ? super V, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return from(this.<Map.Entry<K2, V>>flatMapToObj( // j2cl compiler needs help with type inference
        (k, v) -> nullToEmpty(keyMapper.apply(k, v)).map(k2 -> kv(k2, v))));
  }

  /**
   * Maps each key to zero or more keys of type {@code K2}.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <K2> BiStream<K2, V> flatMapKeys(Function<? super K, ? extends Stream<? extends K2>> keyMapper) {
    requireNonNull(keyMapper);
    return flatMapKeys((k, v) -> keyMapper.apply(k));
  }

  /**
   * Maps each value to zero or more values of type {@code V2}.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <V2> BiStream<K, V2> flatMapValues(
      BiFunction<? super K, ? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return from(this.<Map.Entry<K, V2>>flatMapToObj( // j2cl compiler needs help with type inference
        (k, v) -> nullToEmpty(valueMapper.apply(k, v)).map(v2 -> kv(k, v2))));
  }

  /**
   * Maps each value to zero or more values of type {@code V2}.
   *
   * <p>
   * If a mapped stream is null, an empty stream is used instead.
   */
  public final <V2> BiStream<K, V2> flatMapValues(Function<? super V, ? extends Stream<? extends V2>> valueMapper) {
    requireNonNull(valueMapper);
    return flatMapValues((k, v) -> valueMapper.apply(v));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs of this stream,
   * additionally invoking {@code
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
   * Returns a {@code BiStream} consisting of the pairs in this stream, followed
   * by the pairs in {@code other}.
   *
   * @implNote This method is implemented using {@link Stream#concat}; therefore,
   *           the same warnings about deeply-nested combined streams also apply
   *           to this method. In particular, avoid calling this method in a loop
   *           to combine many streams together.
   */
  public final BiStream<K, V> append(BiStream<? extends K, ? extends V> other) {
    return from(Stream.concat(mapToEntry(), other.mapToEntry()));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, followed
   * by the pair of {@code key} and {@code value}.
   *
   * @implNote This method is implemented using {@link Stream#concat}; therefore,
   *           the same warnings about deeply-nested combined streams also apply
   *           to this method. In particular, avoid calling this method in a loop
   *           to combine many streams together.
   */
  public final BiStream<K, V> append(K key, V value) {
    return append(of(key, value));
  }

  /**
   * Returns a {@code Stream} consisting of only the keys from each pair in this
   * stream.
   */
  public final Stream<K> keys() {
    return mapToObj((k, v) -> k);
  }

  /**
   * Returns a {@code Stream} consisting of only the values from each pair in this
   * stream.
   */
  public final Stream<V> values() {
    return mapToObj((k, v) -> v);
  }

  /**
   * Returns a {@code BiStream} where each pair is a pair from this stream with
   * the key and value swapped.
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
   * Returns a {@code BiStream} consisting of the only the first {@code maxSize}
   * pairs of this stream.
   */
  public abstract BiStream<K, V> limit(int maxSize);

  /**
   * Returns a {@code BiStream} consisting of the remaining pairs from this
   * stream, after discarding the first {@code n} pairs.
   */
  public abstract BiStream<K, V> skip(int n);

  /**
   * Returns a {@code BiStream} consisting of only the distinct pairs (according
   * to {@code
   * Object.equals(Object)} for both key and value).
   */
  public final BiStream<K, V> distinct() {
    return from(mapToEntry().distinct());
  }

  /** Returns a sorted stream based on {@code keyOrdering} and {@code valueOrdering}. */
  public BiStream<K, V> sorted(
      Comparator<? super K> keyOrdering, Comparator<? super V> valueOrdering) {
    Comparator<Map.Entry<? extends K, ? extends V>> byKey =
        Comparator.comparing(Map.Entry::getKey, keyOrdering);
    Comparator<Map.Entry<? extends K, ? extends V>> byValue =
        Comparator.comparing(Map.Entry::getValue, valueOrdering);
    return from(mapToEntry().sorted(byKey.thenComparing(byValue)));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, in the
   * order produced by applying {@code comparator} on the keys of each pair.
   */
  public final BiStream<K, V> sortedByKeys(Comparator<? super K> comparator) {
    requireNonNull(comparator);
    return from(mapToEntry().sorted(Comparator.comparing(Map.Entry::getKey, comparator)));
  }

  /**
   * Returns a {@code BiStream} consisting of the pairs in this stream, in the
   * order produced by applying {@code comparator} on the values of each pair.
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
   * Returns an object of type {@code R} that is the result of collecting the
   * pairs in this stream using {@code collector}. For example:
   *
   * <pre>
   * {
   *   &#64;code
   *   ConcurrentMap<String, Integer> map = BiStream.of("a", 1).collect(Collectors::toConcurrentMap);
   * }
   * </pre>
   */
  public abstract <R> R collect(BiCollector<? super K, ? super V, R> collector);

  static <K, V> Map.Entry<K, V> kv(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  private static <T> Stream<T> nullToEmpty(Stream<T> stream) {
    return stream == null ? Stream.empty() : stream;
  }

  /**
   * An implementation that operates on a generic entry type {@code <E>} using two
   * functions to extract the 'key' and 'value' from each entry.
   *
   * <p>
   * Because the {@code toKey} and {@code toValue} functions could be arbitrary
   * custom functions that are expensive or even with side-effects, it is strictly
   * guaranteed that for any single entry in the stream, each function is invoked
   * exactly once. 1
   * <p>
   * Common methods like {@link #mapKeys(Function)}, {@link #mapValues(Function)},
   * {@link #keys}, {@link #values}, {@link #forEach} etc. can avoid allocating
   * intermediary {@link Map.Entry} instances by either invoking {@code toKey} and
   * {@code toValue} then using the results directly, or composing the functions
   * to be invoked later.
   *
   * <p>
   * Doing so isn't always feasible. For example {@link #filter} and {@link #peek}
   * both need to evaluate the entry by invoking {@code toKey} and {@code toValue}
   * and the return values need to be stored to avoid invoking the functions
   * again. For these cases, the stream will be degeneralized into
   * {@code Stream<Map.Entry<K, V>>} so as to guarantee the "at-most-once"
   * semantic.
   */
  private static class GenericEntryStream<E, K, V> extends BiStream<K, V> {
    private final Stream<E> underlying;
    private final Function<? super E, ? extends K> toKey;
    private final Function<? super E, ? extends V> toValue;

    GenericEntryStream(Stream<E> underlying, Function<? super E, ? extends K> toKey,
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
      return stream(() -> new Spliteration().<T>ofObj(mapper), ORDERED, /* parallel= */ false).onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return doubleStream(() -> new Spliteration().ofDouble(mapper), ORDERED, /* parallel= */ false)
          .onClose(left::close).onClose(right::close);
    }

    @Override
    public IntStream mapToInt(ToIntBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return intStream(() -> new Spliteration().ofInt(mapper), ORDERED, /* parallel= */ false).onClose(left::close)
          .onClose(right::close);
    }

    @Override
    public LongStream mapToLong(ToLongBiFunction<? super K, ? super V> mapper) {
      requireNonNull(mapper);
      return longStream(() -> new Spliteration().ofLong(mapper), ORDERED, /* parallel= */ false).onClose(left::close)
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
       * Returns {@code dominatingResult} if {@code predicate} evaluates to
       * {@code dominatingResult} for any pair, or else returns
       * {@code !dominatingResult}.
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
            return advance() && emit(mapper.applyAsInt(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      Spliterator.OfLong ofLong(ToLongBiFunction<? super K, ? super V> mapper) {
        return new AbstractLongSpliterator(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(LongConsumer consumer) {
            return advance() && emit(mapper.applyAsLong(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      Spliterator.OfDouble ofDouble(ToDoubleBiFunction<? super K, ? super V> mapper) {
        return new AbstractDoubleSpliterator(estimateSize(), ORDERED) {
          @Override
          public boolean tryAdvance(DoubleConsumer consumer) {
            return advance() && emit(mapper.applyAsDouble(currentLeft.value, currentRight.value), consumer);
          }
        };
      }

      <R> R collectWith(BiCollector<? super K, ? super V, R> collector) {
        return collectWith(collector.bisecting(x -> currentLeft.value, x -> currentRight.value));
      }

      /**
       * {@code collector} internally reads from {@link #currentLeft} and
       * {@link #currentRight}.
       */
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

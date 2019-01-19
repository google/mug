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
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.testing.NullPointerTester;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.MultimapSubject;

@RunWith(JUnit4.class)
public class BiStreamTest {

  @Test public void testEmpty() {
    assertKeyValues(BiStream.empty()).isEmpty();
  }

  @Test public void testOf() {
    assertKeyValues(BiStream.of("one", 1))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1, "two", 2))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "two", 2))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "two", 2, "three", 3))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3, "four", 4))
        .containsExactlyEntriesIn(ImmutableMultimap.of(
            "one", 1, "two", 2, "three", 3, "four", 4))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5))
        .containsExactlyEntriesIn(ImmutableMultimap.of(
            "one", 1, "two", 2, "three", 3, "four", 4, "five", 5))
        .inOrder();
  }

  @Test public void testBiStream() {
    assertKeyValues(BiStream.biStream(Stream.of(1, 2)).mapKeys(Object::toString))
        .containsExactlyEntriesIn(ImmutableMultimap.of("1", 1, "2", 2))
        .inOrder();
    assertKeyValues(BiStream.biStream(Stream.of(1, 2).parallel()).mapKeys(Object::toString))
        .containsExactlyEntriesIn(ImmutableMultimap.of("1", 1, "2", 2))
        .inOrder();
  }

  @Test public void testBiStreamWithKeyAndValueFunctions() {
    assertKeyValues(BiStream.biStream(Stream.of(1, 2), Object::toString, v -> v))
        .containsExactlyEntriesIn(ImmutableMultimap.of("1", 1, "2", 2))
        .inOrder();
    assertKeyValues(BiStream.biStream(Stream.of(1, 2).parallel(), Object::toString, v -> v))
        .containsExactlyEntriesIn(ImmutableMultimap.of("1", 1, "2", 2))
        .inOrder();
  }

  @Test public void testFromMap() {
    assertKeyValues(BiStream.from(ImmutableMap.of("one", 1)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
  }

  @Test public void testMapKeys() {
    assertKeyValues(BiStream.of("one", 1).mapKeys((k, v) -> k + v))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one1", 1))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1).mapKeys(k -> k + k))
        .containsExactlyEntriesIn(ImmutableMultimap.of("oneone", 1))
        .inOrder();
  }

  @Test public void testMapValues() {
    assertKeyValues(BiStream.of("one", 1).mapValues((k, v) -> k + v))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", "one1"))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1).mapValues(v -> v * 10))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 10))
        .inOrder();
  }

  @Test public void testMapValues_parallel() {
    Stream<Integer> source = Stream.iterate(1, i -> i + 1).limit(1000);
    assertKeyValues(BiStream.biStream(source.parallel()).mapValues(Object::toString))
        .containsExactlyEntriesIn(
            Stream.iterate(1, i -> i + 1).limit(1000).collect(toImmutableMultimap(i -> i, Object::toString)));
  }

  @Test public void testMapValues_parallel_distinct() {
    Stream<Integer> source = Stream.iterate(1, i -> i + 1).limit(1000).map(i -> i / 2);
    assertKeyValues(BiStream.biStream(source.parallel()).mapValues(Object::toString).distinct())
        .containsExactlyEntriesIn(
            Stream.iterate(0, i -> i + 1).limit(501).collect(toImmutableMultimap(i -> i, Object::toString)));
  }

  @Test public void testDistinct_byKey() {
    BiStream<Integer, ?> distinct =
        BiStream.biStream(Stream.of(1, 1, 2, 2, 3)).mapValues(x -> null).distinct();
    Multimap<Integer, Object> expected = ArrayListMultimap.create();
    expected.put(1, null);
    expected.put(2, null);
    expected.put(3, null);
    assertKeyValues(distinct).containsExactlyEntriesIn(expected);
  }

  @Test public void testDistinct_byValue() {
    BiStream<?, Integer> distinct =
        BiStream.biStream(Stream.of(1, 1, 2, 2, 3)).mapKeys(k -> null).distinct();
    Multimap<Integer, Object> expected = ArrayListMultimap.create();
    expected.put(null, 1);
    expected.put(null, 2);
    expected.put(null, 3);
    assertKeyValues(distinct).containsExactlyEntriesIn(expected);
  }

  @Test public void testFlatMap2() {
    assertKeyValues(
        BiStream.of("one", 1).flatMap2((k, v) -> BiStream.of(k, v * 10, k, v * 11)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 10, "one", 11))
        .inOrder();
  }

  @Test public void testFlatMapKeys() {
    assertKeyValues(BiStream.of("one", 1).flatMapKeys((k, v) -> Stream.of(k, v)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, 1, 1))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1).flatMapKeys(k -> Stream.of(k, k + k)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "oneone", 1))
        .inOrder();
  }

  @Test public void testFlatMapValues() {
    assertKeyValues(BiStream.of("one", 1).flatMapValues((k, v) -> Stream.of(k, v)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", "one", "one", 1))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1).flatMapValues(v -> Stream.of(v, v * 10)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "one", 10))
        .inOrder();
  }

  @Test public void testFilter() {
    assertKeyValues(BiStream.of("one", 1, "two", "two").filter((k, v) -> k.equals(v)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("two", "two"))
        .inOrder();
  }

  @Test public void testFilterKeys() {
    assertKeyValues(BiStream.of("one", 1, "two", 2).filterKeys("one"::equals))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
  }

  @Test public void testFilterValues() {
    assertKeyValues(BiStream.of("one", 1, "two", 2).filterValues(v -> v == 2))
        .containsExactlyEntriesIn(ImmutableMultimap.of("two", 2))
        .inOrder();
  }

  @Test public void testAppend() {
    assertKeyValues(BiStream.of("one", 1).append(BiStream.of("two", 2, "three", 3)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "two", 2, "three", 3))
        .inOrder();
    assertKeyValues(BiStream.of("one", 1).append("two", 2).append("three", 3))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "two", 2, "three", 3))
        .inOrder();
  }

  @Test public void testPeek() {
    AtomicInteger sum = new AtomicInteger();
    assertKeyValues(BiStream.of(1, 2, 3, 4).peek((k, v) -> sum.addAndGet(k + v)))
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 3, 4))
        .inOrder();
    assertThat(sum.get()).isEqualTo(10);
  }

  @Test public void testAllMatch() {
    assertThat(BiStream.of("one", 1, "two", 2).allMatch((k, v) -> k.equals("one") && v == 1))
        .isFalse();
    assertThat(BiStream.of("one", 1, "two", 2).allMatch((k, v) -> k != null && v != null))
        .isTrue();
  }

  @Test public void testAnyMatch() {
    assertThat(BiStream.of("one", 1, "two", 2).anyMatch((k, v) -> k.equals("one") && v == 1))
        .isTrue();
    assertThat(BiStream.of("one", 1, "two", 2).anyMatch((k, v) -> k == null && v == null))
        .isFalse();
  }

  @Test public void testNoneMatch() {
    assertThat(BiStream.of("one", 1, "two", 2).noneMatch((k, v) -> k.equals("one") && v == 1))
        .isFalse();
    assertThat(BiStream.of("one", 1, "two", 2).noneMatch((k, v) -> k == null && v == null))
        .isTrue();
  }

  @Test public void testKeys() {
    assertStream(BiStream.of("one", 1, "two", 2).keys()).containsExactly("one", "two").inOrder();
  }

  @Test public void testValues() {
    assertStream(BiStream.of("one", 1, "two", 2).values()).containsExactly(1, 2).inOrder();
  }

  @Test public void testToMap() {
    assertThat(BiStream.of("one", 1, "two", 2).toMap())
        .containsExactly("one", 1, "two", 2);
  }

  @Test public void testToConcurrentMap() {
    assertThat(BiStream.of("one", 1, "two", 2).toConcurrentMap())
        .containsExactly("one", 1, "two", 2);
  }

  @Test public void testCollect() {
    assertThat(BiStream.of("one", 1, "two", 2)
            .<ImmutableMap<String, Integer>>collect(ImmutableMap::toImmutableMap))
        .containsExactly("one", 1, "two", 2);
  }

  @Test public void testCollect_toImmutableListMultimapWithInflexibleMapperTypes() {
    assertThat(BiStream.of("one", 1, "one", 10, "two", 2)
            .<ImmutableMultimap<String, Integer>>collect(BiStreamTest::toImmutableMultimap))
        .containsExactlyEntriesIn(ImmutableListMultimap.of("one", 1, "one", 10, "two", 2));
  }

  @Test public void testParallel() {
    assertThat(BiStream.of("one", 1, "two", 2).parallel().isParellel()).isTrue();
    assertThat(BiStream.of("one", 1, "two", 2).parallel().keys().isParallel()).isTrue();
    assertThat(BiStream.of("one", 1, "two", 2).parallel().values().isParallel()).isTrue();
  }

  @Test public void testSequential() {
    assertThat(BiStream.of("one", 1, "two", 2).parallel().sequential().isParellel()).isFalse();
    assertThat(BiStream.of("one", 1, "two", 2).parallel().sequential().keys().isParallel())
        .isFalse();
    assertThat(BiStream.of("one", 1, "two", 2).parallel().sequential().values().isParallel())
        .isFalse();
  }

  @Test public void testForEach() {
    AtomicInteger sum = new AtomicInteger();
    BiStream.of(1, 2, 3, 4).forEach((k, v) -> sum.addAndGet(k + v));
    assertThat(sum.get()).isEqualTo(10);
  }

  @Test public void testForEachOrdered() {
    List<Integer> list = new ArrayList<>();
    BiStream.of(1, 2, 3, 4).forEachOrdered((k, v) -> {list.add(k); list.add(v);});
    assertThat(list).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test public void testForEachSequentially() {
    List<Integer> list = new ArrayList<>();
    BiStream.of(1, 2, 3, 4).forEachSequentially((k, v) -> {list.add(k); list.add(v);});
    assertThat(list).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test public void testCount() {
    assertThat(BiStream.of(1, 2, 3, 4).count()).isEqualTo(2);
  }

  @Test public void testMap() {
    assertStream(BiStream.of(1, 2, 3, 4).map((k, v) -> k * 10 + v))
        .containsExactly(12, 34)
        .inOrder();
  }

  @Test public void testMapToInt() {
    assertStream(BiStream.of(1, 2, 3, 4).mapToInt((k, v) -> k * 10 + v).boxed())
        .containsExactly(12, 34)
        .inOrder();
  }

  @Test public void testMapToLong() {
    assertStream(BiStream.of(1, 2, 3, 4).mapToLong((k, v) -> k * 10 + v).boxed())
        .containsExactly(12L, 34L)
        .inOrder();
  }

  @Test public void testMapToDouble() {
    assertStream(BiStream.of(1, 2, 3, 4).mapToDouble((k, v) -> k * 10 + v).boxed())
        .containsExactly(12D, 34D)
        .inOrder();
  }

  @Test public void testFlatMap() {
    assertStream(BiStream.of(1, 2, 3, 4).flatMap((k, n) -> Collections.nCopies(n, k).stream()))
        .containsExactly(1, 1, 3, 3, 3, 3)
        .inOrder();
  }

  @Test public void testFlatMapToInt() {
    assertStream(
            BiStream.of(1, 2, 3, 4)
                .flatMapToInt((k, n) -> Collections.nCopies(n, k).stream().mapToInt(i -> i))
                .boxed())
        .containsExactly(1, 1, 3, 3, 3, 3)
        .inOrder();
  }

  @Test public void testFlatMapToLong() {
    assertStream(
            BiStream.of(1, 2, 3, 4)
                .flatMapToLong((k, n) -> Collections.nCopies(n, k).stream().mapToLong(i -> i))
                .boxed())
        .containsExactly(1L, 1L, 3L, 3L, 3L, 3L)
        .inOrder();
  }

  @Test public void testFlatMapToDouble() {
    assertStream(
            BiStream.of(1, 2, 3, 4)
                .flatMapToDouble((k, n) -> Collections.nCopies(n, k).stream().mapToDouble(i -> i))
                .boxed())
        .containsExactly(1D, 1D, 3D, 3D, 3D, 3D)
        .inOrder();
  }

  @Test public void testZip_leftIsShorter() {
    assertKeyValues(BiStream.zip(Stream.of("a", "b"), Stream.of(1, 2, 3)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testZip_leftIsEmpty() {
    assertKeyValues(BiStream.zip(Stream.empty(), Stream.of(1, 2, 3))).isEmpty();
  }

  @Test public void testZip_rightIsEmpty() {
    assertKeyValues(BiStream.zip(Stream.of(1, 2, 3), Stream.empty())).isEmpty();
  }

  @Test public void testZip_bothAreEmpty() {
    assertKeyValues(BiStream.zip(Stream.empty(), Stream.empty())).isEmpty();
  }

  @Test public void testZip_rightIsShorter() {
    assertKeyValues(BiStream.zip(Stream.of("a", "b", "c"), Stream.of(1, 2)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testZip_equalSize() {
    assertKeyValues(BiStream.zip(Stream.of("a", "b"), Stream.of(1, 2)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testZip_estimatedSize() {
    assertThat(
            BiStream.zip(Stream.of("a", "b"), Stream.of(1, 2, 3))
                .keys().spliterator().estimateSize())
        .isEqualTo(2);
  }

  @Test public void testZip_close() {
    Stream<?> left = Stream.of("a");
    Stream<?> right = Stream.of(1);
    AtomicBoolean leftClosed = new AtomicBoolean();
    AtomicBoolean rightClosed = new AtomicBoolean();
    left.onClose(() -> leftClosed.set(true));
    right.onClose(() -> rightClosed.set(true));
    try (BiStream<?, ?> stream = BiStream.zip(left, right)) {}
    assertThat(leftClosed.get()).isTrue();
    assertThat(rightClosed.get()).isTrue();
  }

  @Test public void testNeighbors_emptyStream() {
    assertKeyValues(BiStream.neighbors(Stream.empty()))
        .isEmpty();
  }

  @Test public void testNeighbors_oneElement() {
    assertKeyValues(BiStream.neighbors(Stream.of("a")))
        .isEmpty();
  }

  @Test public void testNeighbors_multipleElements() {
    assertKeyValues(BiStream.neighbors(Stream.of("a", "b")))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", "b"));
    assertKeyValues(BiStream.neighbors(Stream.of("a", "b", "c")))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", "b", "b", "c"));
    assertKeyValues(BiStream.neighbors(Stream.of("a", "b", "c", "d")))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", "b", "b", "c", "c", "d"));
  }

  @Test public void testNeighbors_infiniteStream() {
    assertKeyValues(BiStream.neighbors(Stream.iterate(1, i -> i + 1)).limit(3))
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 2, 3, 3, 4));
  }

  @Test public void testNeighbors_estimatedSize() {
    assertThat(BiStream.neighbors(Stream.of(1, 2, 3, 4)).keys().spliterator().estimateSize())
        .isEqualTo(4);
  }

  @Test public void testNeighbors_close() {
    Stream<?> elements = Stream.of(1);
    AtomicBoolean closed = new AtomicBoolean();
    elements.onClose(() -> closed.set(true));
    try (BiStream<?, ?> stream = BiStream.neighbors(elements)) {}
    assertThat(closed.get()).isTrue();
  }

  @Test public void testNeighbors_parallelStream() {
    Stream<Integer> parallel = Stream.iterate(1, i -> i + 1).limit(6).parallel();
    BiStream<Integer, Integer> neighbors = BiStream.neighbors(parallel);
    assertKeyValues(neighbors)
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 2, 3, 3, 4, 4, 5, 5, 6));
  }

  @Test public void testIndexed() {
    List<String> elements = asList(new String[2]);
    BiStream.indexed(Stream.of("a", "b")).forEach(elements::set);
    assertThat(elements).containsExactly("a", "b").inOrder();
  }

  @Test public void testIndexed_close() {
    Stream<?> stream = Stream.of("a");
    AtomicBoolean closed = new AtomicBoolean();
    stream.onClose(() -> closed.set(true));
    try (BiStream<?, ?> indexed = BiStream.indexed(stream).distinct()) {}
    assertThat(closed.get()).isTrue();
  }

  @Test public void testLimit() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3).limit(2))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1, "two", 2))
        .inOrder();
  }

  @Test public void testSkip() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3).skip(1))
        .containsExactlyEntriesIn(ImmutableMultimap.of("two", 2, "three", 3))
        .inOrder();
  }

  @Test public void testSortedByKeys() {
    assertKeyValues(BiStream.of("a", 1, "c", 2, "b", 3).sortedByKeys(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 3, "c", 2))
        .inOrder();
  }

  @Test public void testSortedByValues() {
    assertKeyValues(BiStream.of("a", 3, "b", 1, "c", 2).sortedByValues(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("b", 1, "c", 2, "a", 3))
        .inOrder();
  }

  @Test public void testSorted() {
    assertKeyValues(
            BiStream.of("b", 10, "a", 11, "a", 22)
                .sorted(Comparator.naturalOrder(), Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 11, "a", 22, "b", 10))
        .inOrder();
  }

  @Test public void testDistinct() {
    assertKeyValues(BiStream.of("a", 1, "b", 2, "a", 1, "b", 3).distinct())
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2, "b", 3))
        .inOrder();
  }

  @Test public void testInverse() {
    assertKeyValues(BiStream.of("a", 1).inverse())
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, "a"))
        .inOrder();
  }

  @Test public void toBiCollectionWithoutCollectorStrategy() {
    BiCollection<String, Integer> biCollection = BiStream.of("a", 1).toBiCollection();
    assertKeyValues(biCollection.stream())
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1))
        .inOrder();
    assertKeyValues(biCollection.stream())
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1))
        .inOrder();
  }

  @Test public void testIndicesCached() {
    int max = 100;
    ImmutableList<Integer> indices1 =
        BiStream.indexed(Collections.nCopies(max, "x").stream()).keys().collect(toImmutableList());
    ImmutableList<Integer> indices2 =
        BiStream.indexed(Collections.nCopies(max, "x").stream()).keys().collect(toImmutableList());
    IntStream.range(0, max).forEach(index -> {
      assertThat(indices1.get(index)).isSameAs(indices2.get(index));
    });
  }

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester();
    asList(BiStream.class.getDeclaredMethods()).stream()
        .filter(m -> m.getName().equals("of")
            || m.getName().equals("append") && m.getParameterTypes().length == 2)
        .forEach(tester::ignore);
    tester.testAllPublicStaticMethods(BiStream.class);
    tester.testAllPublicInstanceMethods(BiStream.empty());
  }

  static <K, V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    Multimap<K, V> multimap = stream.<Multimap<K, V>>collect(BiStreamTest::toLinkedListMultimap);
    return assertThat(multimap);
  }

  private static <K, V> IterableSubject assertStream(Stream<?> stream) {
    return assertThat(stream.collect(toList()));
  }

  // Intentionally declare the parameter types without wildcards, to make sure
  // BiCollector can still work with such naive method references.
  private static <T, K, V> Collector<T, ?, ImmutableListMultimap<K, V>> toImmutableMultimap(
      Function<T, K> keyMapper, Function<T, V> valueMapper) {
    return ImmutableListMultimap.toImmutableListMultimap(keyMapper, valueMapper);
  }

  private static <T, K, V> Collector<T, ?, LinkedListMultimap<K, V>> toLinkedListMultimap(
      Function<? super T, ? extends K> toKey, Function<? super T, ? extends V> toValue) {
    return Collector.of(
        LinkedListMultimap::create,
        (m, e) -> m.put(toKey.apply(e), toValue.apply(e)),
        (m1, m2) -> {
          m1.putAll(m2);
          return m1;
        });
  }
}

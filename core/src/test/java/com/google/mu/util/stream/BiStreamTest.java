package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.testing.ClassSanityTester;
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

  @Test public void testBuilder() {
    assertKeyValues(new BiStream.Builder<>().put("one", 1).build())
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
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

  @Test public void testFlatMap() {
    assertKeyValues(
        BiStream.of("one", 1).flatMap((k, v) -> BiStream.of(k, v * 10, k, v * 11)))
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

  @Test public void testPeek() {
    AtomicInteger sum = new AtomicInteger();
    assertKeyValues(BiStream.of(1, 2, 3, 4).peek((k, v) -> sum.addAndGet(k + v)))
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 3, 4))
        .inOrder();
    assertThat(sum.get()).isEqualTo(10);
  }

  @Test public void testPeekKeys() {
    AtomicInteger sum = new AtomicInteger();
    assertKeyValues(BiStream.of(1, 2, 3, 4).peekKeys(sum::addAndGet))
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 3, 4))
        .inOrder();
    assertThat(sum.get()).isEqualTo(4);
  }

  @Test public void testPeekValues() {
    AtomicInteger sum = new AtomicInteger();
    assertKeyValues(BiStream.of(1, 2, 3, 4).peekValues(sum::addAndGet))
        .containsExactlyEntriesIn(ImmutableMultimap.of(1, 2, 3, 4))
        .inOrder();
    assertThat(sum.get()).isEqualTo(6);
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

  @Test public void testCount() {
    assertThat(BiStream.of(1, 2, 3, 4).count()).isEqualTo(2);
  }

  @Test public void testMap() {
    assertStream(BiStream.of(1, 2, 3, 4).map((k, v) -> k * 10 + v))
        .containsExactly(12, 34)
        .inOrder();
  }

  @Test public void testPairUp_leftIsShorter() {
    assertKeyValues(BiStream.pairUp(Stream.of("a", "b"), Stream.of(1, 2, 3)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testPairUp_leftIsEmpty() {
    assertKeyValues(BiStream.pairUp(Stream.empty(), Stream.of(1, 2, 3))).isEmpty();
  }

  @Test public void testPairUp_rightIsEmpty() {
    assertKeyValues(BiStream.pairUp(Stream.of(1, 2, 3), Stream.empty())).isEmpty();
  }

  @Test public void testPairUp_bothAreEmpty() {
    assertKeyValues(BiStream.pairUp(Stream.empty(), Stream.empty())).isEmpty();
  }

  @Test public void testPairUp_rightIsShorter() {
    assertKeyValues(BiStream.pairUp(Stream.of("a", "b", "c"), Stream.of(1, 2)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testPairUp_equalSize() {
    assertKeyValues(BiStream.pairUp(Stream.of("a", "b"), Stream.of(1, 2)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 2));
  }

  @Test public void testPairUp_estimatedSize() {
    assertThat(
            BiStream.pairUp(Stream.of("a", "b"), Stream.of(1, 2, 3))
                .keys().spliterator().estimateSize())
        .isEqualTo(2);
  }

  @Test public void testForEachOrdered() {
    List<Integer> list = new ArrayList<>();
    BiStream.of(1, 2, 3, 4).forEachOrdered((k, v) -> {list.add(k); list.add(v);});
    assertThat(list).containsExactly(1, 2, 3, 4).inOrder();
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

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester();
    asList(BiStream.class.getDeclaredMethods()).stream()
        .filter(m -> m.getName().equals("of"))
        .forEach(tester::ignore);
    tester.testAllPublicStaticMethods(BiStream.class);
    tester.testAllPublicInstanceMethods(BiStream.empty());
  }

  private static <K, V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    ImmutableListMultimap.Builder<K, V> builder = ImmutableListMultimap.builder();
    stream.forEachOrdered(builder::put);
    return assertThat(builder.build());
  }

  private static <K, V> IterableSubject assertStream(Stream<?> stream) {
    return assertThat(stream.collect(toList()));
  }
}

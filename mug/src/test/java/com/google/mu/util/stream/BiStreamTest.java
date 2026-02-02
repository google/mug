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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.BiStream.adjacentPairsFrom;
import static com.google.mu.util.stream.BiStream.biStream;
import static com.google.mu.util.stream.BiStream.concatenating;
import static com.google.mu.util.stream.BiStream.crossJoining;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.BiStream.toAdjacentPairs;
import static com.google.mu.util.stream.BiStream.toBiStream;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.MultimapSubject;
import com.google.mu.util.BiOptional;
import com.google.mu.util.Both;
import com.google.mu.util.Substring;

@RunWith(JUnit4.class)
public class BiStreamTest {

  @Test public void testOf_onePair() {
    assertKeyValues(BiStream.of("one", 1)).containsExactly("one", 1);
  }

  @Test public void testOf_twoPairs() {
    assertKeyValues(BiStream.of("one", 1, "two", 2)).containsExactly("one", 1, "two", 2).inOrder();
  }

  @Test public void testOf_threePairs() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3))
        .containsExactly("one", 1, "two", 2, "three", 3)
        .inOrder();
  }

  @Test public void testOf_fourPairs() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3, "four", 4))
        .containsExactly("one", 1, "two", 2, "three", 3, "four", 4)
        .inOrder();
  }

  @Test public void testOf_fivePairs() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5))
        .containsExactly("one", 1, "two", 2, "three", 3, "four", 4, "five", 5)
        .inOrder();
  }

  @Test public void testOf_sixPairs() {
    assertKeyValues(BiStream.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5, "six", 6))
        .containsExactly("one", 1, "two", 2, "three", 3, "four", 4, "five", 5, "six", 6)
        .inOrder();
  }

  @Test public void testOf_sevenPairs() {
    assertKeyValues(BiStream.of("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7))
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7)
        .inOrder();
  }

  @Test public void testOf_eightPairs() {
    assertKeyValues(BiStream.of("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8))
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8)
        .inOrder();
  }

  @Test public void testOf_ninePairs() {
    assertKeyValues(
            BiStream.of("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8, "9", 9))
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8, "9", 9)
        .inOrder();
  }

  @Test public void testOf_tenPairs() {
    assertKeyValues(
            BiStream.of(
                "0", 0, "1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8, "9", 9))
        .containsExactly(
            "0", 0, "1", 1, "2", 2, "3", 3, "4", 4, "5", 5, "6", 6, "7", 7, "8", 8, "9", 9)
        .inOrder();
  }

  @Test public void testBiStreamWithKeyAndValueFunctions() {
    assertKeyValues(biStream(Object::toString, Stream.of(1, 2)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("1", 1, "2", 2))
        .inOrder();
  }

  @Test public void testFromMap() {
    assertKeyValues(BiStream.from(ImmutableMap.of("one", 1)))
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
  }

  @Test public void testGroupConsecutive_emptyStream() {
    assertKeyValues(BiStream.empty().groupConsecutiveBy(identity(), toList())).isEmpty();
  }

  @Test public void testGroupConsecutive_singleElement() {
    assertKeyValues(BiStream.of("1", 1).groupConsecutiveBy(identity(), toList()))
        .containsExactly("1", asList(1));
  }

  @Test public void testGroupConsecutive_twoElementsSameRun() {
    assertKeyValues(BiStream.of("k", "a", "k", "b").groupConsecutiveBy(identity(), toList()))
        .containsExactly("k", asList("a", "b"));
  }

  @Test public void testGroupConsecutive_singleRunManyElements() {
    assertKeyValues(
            biStream(Collections.nCopies(100, 'x')).groupConsecutiveBy(identity(), counting()))
        .containsExactly('x', 100L);
  }

  @Test public void testGroupConsecutive_twoElementsDifferentRuns() {
    assertKeyValues(BiStream.of("k1", 1, "k2", 2).groupConsecutiveBy(identity(), toList()))
        .containsExactly("k1", asList(1), "k2", asList(2))
        .inOrder();
  }

  @Test public void testGroupConsecutive_oneElementPerRun() {
    Stream<Integer> data = Stream.of(1, 2, 3, 4, 5);
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly(1, 1L, 2, 1L, 3, 1L, 4, 1L, 5, 1L)
        .inOrder();
  }

  @Test public void testGroupConsecutive_twoElementsPerRun() {
    Stream<Integer> data = Stream.of(1, 1, 2, 2, 3, 3, 2, 2, 1, 1);
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly(1, 2L, 2, 2L, 3, 2L, 2, 2L, 1, 2L)
        .inOrder();
  }

  @Test public void testGroupConsecutive_threeElementsPerRun() {
    Stream<Integer> data = Stream.of(1, 1, 1, 2, 2, 2, 3, 3, 3, 2, 2, 2);
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly(1, 3L, 2, 3L, 3, 3L, 2, 3L)
        .inOrder();
  }

  @Test public void testGroupConsecutive_alternatingElements() {
    Stream<?> data = Stream.of(1, 2, 1, 2, 1);
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly(1, 1L, 2, 1L, 1, 1L, 2, 1L, 1, 1L)
        .inOrder();
  }

  @Test public void testGroupConsecutive_equalElementsNotAdjacent() {
    Stream<?> data = Stream.of(1, "2", 2, 1);
    assertKeyValues(biStream(data).groupConsecutiveBy(Object::toString, toList()))
        .containsExactly("1", asList(1), "2", asList("2", 2), "1", asList(1))
        .inOrder();
  }

  @Test public void testGroupConsecutive_multipleRuns() {
    Stream<?> data = Stream.of(1, "2", 2, "3", 3, 3);
    assertKeyValues(biStream(data).groupConsecutiveBy(Object::toString, toList()))
        .containsExactly("1", asList(1), "2", asList("2", 2), "3", asList("3", 3, 3))
        .inOrder();
  }

  @Test public void testGroupConsecutive_longerRuns() {
    Stream<?> data = Stream.of('a', 'b', 'b', 'b', 'b', 'b');
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly('a', 1L, 'b', 5L)
        .inOrder();
  }

  @Test public void testGroupConsecutive_nullAsRuns() {
    Stream<?> data = Stream.of(null, null, "foo", "foo", "foo");
    assertKeyValues(biStream(data).groupConsecutiveBy(identity(), counting()))
        .containsExactly(null, 2L, "foo", 3L)
        .inOrder();
  }

  @Test public void testGroupConsecutiveIf_withBiCollector() {
    Map<String, Integer> prices =
        ImmutableMap.of("day1", 10, "day2", 20, "day3", 10000, "day4", 9999);
    Stream<Map<String, Integer>> result =
        BiStream.from(prices)
            .groupConsecutiveIf((d1, p1, d2, p2) -> Math.abs(p1 - p2) < 1000, Collectors::toMap);
    assertThat(result)
        .containsExactly(
            ImmutableMap.of("day1", 10, "day2", 20), ImmutableMap.of("day3", 10000, "day4", 9999));
  }

  /**
   * This test confirms that stateful classifier function can implement more sophisticated grouping.
   */
  @Test public void testGroupConsecutiveIf() {
    // Addinig "." to the end of an element doesn't change its logical value, but forces to conclude
    // the current consecutive group even if the next element would otherwise be considered "equal".
    Substring.Suffix period = Substring.suffix('.');
    Stream<String> data = Stream.of("foo", "foo", "foo.", "foo.", "foo", "bar");
    Stream<List<String>> groups = biStream(data)
        .groupConsecutiveIf(
            (t1, t2) ->
                !period.from(t1).isPresent()
                    && period.removeFrom(t1).equals(period.removeFrom(t2)),
            toList());
    assertThat(groups)
        .containsExactly(asList("foo", "foo", "foo."), asList("foo."), asList("foo"), asList("bar"))
        .inOrder();
  }

  /** Groups not by equal key, but by proximity. */
  @Test public void testGroupConsecutive_proximityGrouping_withReducer() {
    // Make sure nulls are grouped properly
    Stream<Integer> data = Stream.of(1, 3, 3, 2, 13, 15, 100);
    final int proximity = 10;
    Stream<Integer> groupSizes = biStream(data)
        .groupConsecutiveIf((d1, d2) -> Math.abs(d1 - d2) <= proximity, Integer::sum);
    assertThat(groupSizes).containsExactly(1 + 3 + 3 + 2, 13 + 15, 100).inOrder();
  }

  /** Groups not by equal key, but by proximity. */
  @Test public void testGroupConsecutive_proximityGrouping_nullElementsGrouped() {
    // Make sure nulls are grouped properly
    Stream<Integer> data = Stream.of(1, 3, 3, 2, 13, 15, 100, null, null);
    final int proximity = 10;
    Stream<Long> groupSizes = biStream(data)
        .groupConsecutiveIf(
            (d1, d2) ->
                d1 == null && d2 == null
                    || d1 != null && d2 != null && Math.abs(d1 - d2) <= proximity,
            counting());
    assertThat(groupSizes).containsExactly(4L, 2L, 1L, 2L).inOrder();
  }

  @Test public void testZip_bothEmpty() {
    assertKeyValues(BiStream.zip(ImmutableList.of(), ImmutableList.of())).isEmpty();
  }

  @Test public void testZip_leftIsEmpty() {
    assertKeyValues(BiStream.zip(ImmutableList.of(), ImmutableList.of("one"))).isEmpty();
  }

  @Test public void testZip_rightIsEmpty() {
    assertKeyValues(BiStream.zip(ImmutableList.of(1), ImmutableList.of())).isEmpty();
  }

  @Test public void testZip_leftIsShorter() {
    assertKeyValues(BiStream.zip(ImmutableList.of(1), ImmutableList.of("one", "two")))
        .containsExactly(1, "one");
  }

  @Test public void testZip_rightIsShorter() {
    assertKeyValues(BiStream.zip(ImmutableList.of(1, 2), ImmutableList.of("one")))
        .containsExactly(1, "one");
  }

  @Test public void testZip_leftAndRightSameSize() {
    assertKeyValues(BiStream.zip(ImmutableList.of(1, 2), ImmutableList.of("one", "two")))
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void testZip_infiniteWithFinite() {
    assertKeyValues(BiStream.zip(indexesFrom(1), Stream.of("one")))
        .containsExactly(1, "one");
  }

  @Test public void testZip_finiteWithInfinite() {
    assertKeyValues(BiStream.zip(Stream.of("one"), indexesFrom(1)))
        .containsExactly("one", 1);
  }

  @Test public void testZip_infiniteWithInfinite() {
    assertKeyValues(BiStream.zip(indexesFrom(1), indexesFrom(2)).limit(3))
        .containsExactly(1, 2, 2, 3, 3, 4)
        .inOrder();
  }

  @Test public void testZip_mapToObj() {
    Stream<?> zipped =
        BiStream.zip(asList(1, 2), asList("one", "two")).mapToObj(Joiner.on(':')::join);
    assertThat(zipped.isParallel()).isFalse();
    assertThat(zipped).containsExactly("1:one", "2:two").inOrder();
  }

  @Test public void testZip_mapToObj_leftIsParallel() {
    Stream<String> zipped =
        BiStream.zip(asList(1, 2, 3).parallelStream(), Stream.of("one", "two", "three"))
            .mapToObj(Joiner.on(':')::join);
    assertThat(zipped).containsExactly("1:one", "2:two", "3:three").inOrder();
  }

  @Test public void testZip_mapToObj_rightIsParallel() {
    Stream<String> zipped =
        BiStream.zip(Stream.of(1, 2, 3), asList("one", "two", "three").parallelStream())
            .mapToObj(Joiner.on(':')::join);
    assertThat(zipped).containsExactly("1:one", "2:two", "3:three").inOrder();
  }

  @Test public void testZip_mapToObj_bothLeftAndRightClosedUponClosing() {
    AtomicBoolean leftClosed = new AtomicBoolean();
    AtomicBoolean rightClosed = new AtomicBoolean();
    Stream<Integer> left = Stream.of(1, 2).onClose(() -> leftClosed.set(true));
    Stream<String> right = Stream.of("one", "two").onClose(() -> rightClosed.set(true));
    try (Stream<String> zipped =
        BiStream.zip(left, right)
            .mapToObj((java.lang.Integer i, java.lang.String s) -> i + ":" + s)) {
      assertThat(leftClosed.get()).isFalse();
      assertThat(rightClosed.get()).isFalse();
    }
    assertThat(leftClosed.get()).isTrue();
    assertThat(rightClosed.get()).isTrue();
  }

  @Test public void testZip_mapToObj_lateBindingConsistentWithJdk() {
    Map<Integer, String> dict = new HashMap<>();
    Stream<String> jdk = dict.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue());
    Stream<String> zipped =
        BiStream.zip(dict.keySet(), dict.values()).mapToObj(Joiner.on(':')::join);
    dict.put(1, "one");
    assertThat(zipped).containsExactlyElementsIn(jdk.collect(toImmutableList()));
  }

  @Test public void testZip_mapToDouble() {
    DoubleStream zipped = BiStream.zip(asList(1, 2), asList(10, 20)).mapToDouble((l, r) -> l + r);
    assertThat(zipped.isParallel()).isFalse();
    assertThat(zipped.boxed()).containsExactly(11D, 22D).inOrder();
  }

  @Test public void testZip_mapToDouble_leftIsParallel() {
    DoubleStream zipped =
        BiStream.zip(asList(1, 2).parallelStream(), Stream.of(10, 20)).mapToDouble((l, r) -> l + r);
    assertThat(zipped.boxed()).containsExactly(11D, 22D).inOrder();
  }

  @Test public void testZip_mapToDouble_rightIsParallel() {
    DoubleStream zipped =
        BiStream.zip(Stream.of(1, 2), asList(10, 20).parallelStream()).mapToDouble((l, r) -> l + r);
    assertThat(zipped.boxed()).containsExactly(11D, 22D).inOrder();
  }

  @Test public void testZip_mapToDouble_bothLeftAndRightClosedUponClosing() {
    AtomicBoolean leftClosed = new AtomicBoolean();
    AtomicBoolean rightClosed = new AtomicBoolean();
    Stream<Integer> left = Stream.of(1, 2).onClose(() -> leftClosed.set(true));
    Stream<Integer> right = Stream.of(10, 20).onClose(() -> rightClosed.set(true));
    try (DoubleStream zipped =
        BiStream.zip(left, right)
            .mapToDouble((java.lang.Integer l, java.lang.Integer r) -> l + r)) {
      assertThat(leftClosed.get()).isFalse();
      assertThat(rightClosed.get()).isFalse();
    }
    assertThat(leftClosed.get()).isTrue();
    assertThat(rightClosed.get()).isTrue();
  }

  @Test public void testZip_mapToDouble_lateBindingConsistentWithJdk() {
    Map<Integer, Integer> dict = new HashMap<>();
    DoubleStream jdk = dict.entrySet().stream().mapToDouble(e -> e.getKey() + e.getValue());
    DoubleStream zipped = BiStream.zip(dict.keySet(), dict.values()).mapToDouble((l, r) -> l + r);
    dict.put(1, 10);
    assertThat(zipped.boxed()).containsExactlyElementsIn(jdk.boxed().collect(toImmutableList()));
  }

  @Test public void testZip_mapToInt() {
    IntStream zipped = BiStream.zip(asList(1, 2), asList(10, 20)).mapToInt((l, r) -> l + r);
    assertThat(zipped.isParallel()).isFalse();
    assertThat(zipped).containsExactly(11, 22).inOrder();
  }

  @Test public void testZip_mapToInt_leftIsParallel() {
    IntStream zipped =
        BiStream.zip(asList(1, 2).parallelStream(), Stream.of(10, 20)).mapToInt((l, r) -> l + r);
    assertThat(zipped).containsExactly(11, 22).inOrder();
  }

  @Test public void testZip_mapToInt_rightIsParallel() {
    IntStream zipped =
        BiStream.zip(Stream.of(1, 2), asList(10, 20).parallelStream()).mapToInt((l, r) -> l + r);
    assertThat(zipped).containsExactly(11, 22).inOrder();
  }

  @Test public void testZip_mapToInt_bothLeftAndRightClosedUponClosing() {
    AtomicBoolean leftClosed = new AtomicBoolean();
    AtomicBoolean rightClosed = new AtomicBoolean();
    Stream<Integer> left = Stream.of(1, 2).onClose(() -> leftClosed.set(true));
    Stream<Integer> right = Stream.of(10, 20).onClose(() -> rightClosed.set(true));
    try (IntStream zipped =
        BiStream.zip(left, right).mapToInt((java.lang.Integer l, java.lang.Integer r) -> l + r)) {
      assertThat(leftClosed.get()).isFalse();
      assertThat(rightClosed.get()).isFalse();
    }
    assertThat(leftClosed.get()).isTrue();
    assertThat(rightClosed.get()).isTrue();
  }

  @Test public void testZip_mapToInt_lateBindingConsistentWithJdk() {
    Map<Integer, Integer> dict = new HashMap<>();
    IntStream jdk = dict.entrySet().stream().mapToInt(e -> e.getKey() + e.getValue());
    IntStream zipped = BiStream.zip(dict.keySet(), dict.values()).mapToInt((l, r) -> l + r);
    dict.put(1, 10);
    assertThat(zipped).containsExactlyElementsIn(jdk.boxed().collect(toImmutableList()));
  }

  @Test public void testZip_mapToLong() {
    LongStream zipped = BiStream.zip(asList(1, 2), asList(10, 20)).mapToLong((l, r) -> l + r);
    assertThat(zipped.isParallel()).isFalse();
    assertThat(zipped).containsExactly(11L, 22L).inOrder();
  }

  @Test public void testZip_mapToLong_leftIsParallel() {
    LongStream zipped =
        BiStream.zip(asList(1, 2).parallelStream(), Stream.of(10, 20)).mapToLong((l, r) -> l + r);
    assertThat(zipped).containsExactly(11L, 22L).inOrder();
  }

  @Test public void testZip_mapToLong_rightIsParallel() {
    LongStream zipped =
        BiStream.zip(Stream.of(1, 2), asList(10, 20).parallelStream()).mapToLong((l, r) -> l + r);
    assertThat(zipped).containsExactly(11L, 22L).inOrder();
  }

  @Test public void testZip_mapToLong_bothLeftAndRightClosedUponClosing() {
    AtomicBoolean leftClosed = new AtomicBoolean();
    AtomicBoolean rightClosed = new AtomicBoolean();
    Stream<Integer> left = Stream.of(1, 2).onClose(() -> leftClosed.set(true));
    Stream<Integer> right = Stream.of(10, 20).onClose(() -> rightClosed.set(true));
    try (LongStream zipped =
        BiStream.zip(left, right).mapToLong((java.lang.Integer l, java.lang.Integer r) -> l + r)) {
      assertThat(leftClosed.get()).isFalse();
      assertThat(rightClosed.get()).isFalse();
    }
    assertThat(leftClosed.get()).isTrue();
    assertThat(rightClosed.get()).isTrue();
  }

  @Test public void testZip_mapToLong_lateBindingConsistentWithJdk() {
    Map<Integer, Integer> dict = new HashMap<>();
    LongStream jdk = dict.entrySet().stream().mapToLong(e -> e.getKey() + e.getValue());
    LongStream zipped = BiStream.zip(dict.keySet(), dict.values()).mapToLong((l, r) -> l + r);
    dict.put(1, 10);
    assertThat(zipped).containsExactlyElementsIn(jdk.boxed().collect(toImmutableList()));
  }

  @Test public void testZip_mapKeys() {
    assertKeyValues(BiStream.zip(asList(1, 2), asList("one", "two")).mapKeys(i -> i * 10))
        .containsExactly(10, "one", 20, "two")
        .inOrder();
  }

  @Test public void testZip_mapValues() {
    assertKeyValues(BiStream.zip(asList("one", "two"), asList(1, 2)).mapValues(Object::toString))
        .containsExactly("one", "1", "two", "2")
        .inOrder();
  }

  @Test public void testZip_keys() {
    assertThat(BiStream.zip(asList("one", "two"), asList(1, 2)).keys())
        .containsExactly("one", "two")
        .inOrder();
    assertThat(BiStream.zip(asList("one", "two"), asList(1, 2, 3)).keys())
        .containsExactly("one", "two")
        .inOrder();
    assertThat(BiStream.zip(asList("one", "two", "three"), asList(1, 2)).keys())
        .containsExactly("one", "two")
        .inOrder();
  }

  @Test public void testZip_values() {
    assertThat(BiStream.zip(asList("one", "two"), asList(1, 2)).values())
        .containsExactly(1, 2)
        .inOrder();
    assertThat(BiStream.zip(asList("one", "two", "three"), asList(1, 2)).values())
        .containsExactly(1, 2)
        .inOrder();
    assertThat(BiStream.zip(asList("one", "two"), asList(1, 2, 3)).values())
        .containsExactly(1, 2)
        .inOrder();
  }

  @Test public void testZip_inverse() {
    assertKeyValues(BiStream.zip(asList("one", "two"), asList(1, 2)).inverse())
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void testZip_allMatch() {
    assertThat(BiStream.zip(asList(1, 2), asList(3, 4)).allMatch((a, b) -> a < b)).isTrue();
    assertThat(BiStream.zip(Stream.of(1, 2).parallel(), Stream.of(3, 4)).allMatch((a, b) -> a < b))
        .isTrue();
    assertThat(BiStream.zip(asList(1, 2), asList(3, 2)).allMatch((a, b) -> a < b)).isFalse();
    assertThat(BiStream.zip(Stream.of(1, 2), Stream.of(3, 2).parallel()).allMatch((a, b) -> a < b))
        .isFalse();
  }

  @Test public void testZip_anyMatch() {
    assertThat(BiStream.zip(asList(1, 2), asList(3, 1)).anyMatch((a, b) -> a < b)).isTrue();
    assertThat(BiStream.zip(Stream.of(1, 2), Stream.of(3, 1).parallel()).anyMatch((a, b) -> a < b))
        .isTrue();
    assertThat(BiStream.zip(asList(1, 2), asList(1, 1)).allMatch((a, b) -> a < b)).isFalse();
    assertThat(BiStream.zip(Stream.of(1, 2).parallel(), Stream.of(1, 1)).allMatch((a, b) -> a < b))
        .isFalse();
  }

  @Test public void testZip_noneMatch() {
    assertThat(BiStream.zip(asList(1, 2), asList(3, 4)).noneMatch((a, b) -> a > b)).isTrue();
    assertThat(BiStream.zip(Stream.of(1, 2), Stream.of(3, 4).parallel()).noneMatch((a, b) -> a > b))
        .isTrue();
    assertThat(BiStream.zip(asList(1, 2), asList(0, 3)).noneMatch((a, b) -> a > b)).isFalse();
    assertThat(BiStream.zip(Stream.of(1, 2).parallel(), Stream.of(0, 3)).noneMatch((a, b) -> a > b))
        .isFalse();
  }

  @Test public void testZip_findAny() {
    assertThat(BiStream.zip(ImmutableList.of(), ImmutableList.of()).findAny())
        .isEqualTo(BiOptional.empty());
    assertThat(BiStream.zip(ImmutableList.of(1), ImmutableList.of("one")).findAny())
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test public void testZip_findFirst() {
    assertThat(BiStream.zip(ImmutableList.of(), ImmutableList.of()).findFirst())
        .isEqualTo(BiOptional.empty());
    assertThat(BiStream.zip(ImmutableList.of(1, 2), ImmutableList.of("one", "two")).findFirst())
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test public void testZip_count() {
    assertThat(BiStream.zip(asList(1, 2), asList(10, 20, 30)).count()).isEqualTo(2);
    assertThat(BiStream.zip(asList(1, 2, 3), asList(10, 20)).count()).isEqualTo(2);
  }

  @Test public void testZip_count_finiteWithInfinite() {
    assertThat(BiStream.zip(Stream.of(1, 2), Stream.iterate(10, i -> 2 * i)).count()).isEqualTo(2);
    assertThat(BiStream.zip(Stream.iterate(10, i -> 2 * i), Stream.of(1, 2)).count()).isEqualTo(2);
  }

  @Test public void testZip_skip() {
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).skip(0))
        .containsExactly(1, 10, 2, 20, 3, 30);
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).skip(1))
        .containsExactly(2, 20, 3, 30);
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).skip(3)).isEmpty();
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).skip(4)).isEmpty();
  }

  @Test public void testZip_skip_finiteWithInfinite() {
    assertKeyValues(BiStream.zip(Stream.of(1, 2, 4), Stream.iterate(10, i -> i * 2)).skip(1))
        .containsExactly(2, 20, 4, 40);
    assertKeyValues(BiStream.zip(indexesFrom(1), Stream.of(10, 20)).skip(1))
        .containsExactly(2, 20);
  }

  @Test public void testZip_limit() {
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).limit(2))
        .containsExactly(1, 10, 2, 20);
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).limit(3))
        .containsExactly(1, 10, 2, 20, 3, 30);
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).limit(4))
        .containsExactly(1, 10, 2, 20, 3, 30);
    assertKeyValues(BiStream.zip(asList(1, 2, 3), asList(10, 20, 30, 40)).limit(0)).isEmpty();
  }

  @Test public void testZip_limit_finiteWithInfinite() {
    assertKeyValues(BiStream.zip(indexesFrom(1), Stream.of(10, 20, 30, 40)).limit(2))
        .containsExactly(1, 10, 2, 20);
    assertKeyValues(BiStream.zip(Stream.of(10, 20, 30, 40), indexesFrom(1)).limit(2))
        .containsExactly(10, 1, 20, 2);
  }

  @Test public void testZip_collect() {
    assertThat(BiStream.zip(asList(1, 2, 3, 4), asList("one", "two", "three"))
            .collect(toMap()))
        .containsExactly(1, "one", 2, "two", 3, "three");
    assertThat(BiStream.zip(indexesFrom(1), Stream.of("one", "two")).collect(toMap()))
        .containsExactly(1, "one", 2, "two");
    assertThat(
            BiStream.zip(asList(1, 2, 3, 4).parallelStream(), Stream.of("one", "two", "three"))
                .collect(toMap()))
        .containsExactly(1, "one", 2, "two", 3, "three");
    assertThat(
            BiStream.zip(Stream.of(1, 2, 3, 4), asList("one", "two", "three").parallelStream())
                .collect(toMap()))
        .containsExactly(1, "one", 2, "two", 3, "three");
  }

  @Test public void testZip_forEach() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.of(1, 2, 3), Stream.of("one", "two", "three")).forEach(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two", 3, "three").inOrder();
  }

  @Test public void testZip_forEach_leftIsEmpty() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.empty(), indexesFrom(1)).forEach(all::put);
    assertThat(all).isEmpty();
  }

  @Test public void testZip_forEach_rightIsEmpty() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1), Stream.empty()).forEach(all::put);
    assertThat(all).isEmpty();
  }

  @Test public void testZip_forEach_leftIsShorter() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.of("one", "two"), indexesFrom(1)).forEach(all::put);
    assertThat(all).containsExactly("one", 1, "two", 2).inOrder();
  }

  @Test public void testZip_forEach_rightIsShorter() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1), Stream.of("one", "two")).forEach(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two").inOrder();
  }

  @Test public void testZip_forEach_leftAndRightSameSize() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1).limit(3), indexesFrom(11).limit(3))
        .forEach(all::put);
    assertThat(all).containsExactly(1, 11, 2, 12, 3, 13).inOrder();
  }

  @Test public void testZip_forEach_parallel() {
    ConcurrentMap<Object, Object> all = new ConcurrentHashMap<>();
    BiStream.zip(indexesFrom(1).parallel(), Stream.of("one", "two", "three").parallel())
        .forEach(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two", 3, "three");
  }

  @Test public void testZip_forEachOrdered() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.of(1, 2, 3), Stream.of("one", "two", "three")).forEachOrdered(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two", 3, "three").inOrder();
  }

  @Test public void testZip_forEachOrdered_leftIsEmpty() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.empty(), indexesFrom(1)).forEachOrdered(all::put);
    assertThat(all).isEmpty();
  }

  @Test public void testZip_forEachOrdered_rightIsEmpty() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1), Stream.empty()).forEachOrdered(all::put);
    assertThat(all).isEmpty();
  }

  @Test public void testZip_forEachOrdered_leftIsShorter() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(Stream.of("one", "two"), indexesFrom(1)).forEachOrdered(all::put);
    assertThat(all).containsExactly("one", 1, "two", 2).inOrder();
  }

  @Test public void testZip_forEachOrdered_rightIsShorter() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1), Stream.of("one", "two")).forEachOrdered(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two").inOrder();
  }

  @Test public void testZip_forEachOrdered_leftAndRightSameSize() {
    Map<Object, Object> all = new LinkedHashMap<>();
    BiStream.zip(indexesFrom(1).limit(3), indexesFrom(11).limit(3))
        .forEachOrdered(all::put);
    assertThat(all).containsExactly(1, 11, 2, 12, 3, 13).inOrder();
  }

  @Test public void testZip_forEachOrdered_parallel() {
    Map<Object, Object> all = Collections.synchronizedMap(new LinkedHashMap<>());
    BiStream.zip(indexesFrom(1).parallel(), Stream.of("one", "two", "three").parallel())
        .forEachOrdered(all::put);
    assertThat(all).containsExactly(1, "one", 2, "two", 3, "three").inOrder();
  }

  @Test public void testZip_leftIsParallel_isSequential() {
    BiStream<?, ?> zipped =
        BiStream.zip(indexesFrom(1).parallel(), Stream.of("one", "two", "three"));
    assertSequential(zipped.mapToObj(Joiner.on(':')::join))
        .containsExactly("1:one", "2:two", "3:three")
        .inOrder();
  }

  @Test public void testZip_rightIsParallel_isSequential() {
    BiStream<?, ?> zipped =
        BiStream.zip(indexesFrom(1), Stream.of("one", "two", "three").parallel());
    assertSequential(zipped.mapToObj(Joiner.on(':')::join))
        .containsExactly("1:one", "2:two", "3:three")
        .inOrder();
  }

  @Test public void testToBiStreamFromSplit() {
    assertThat(Stream.of("name=joe", "age=10")
            .map(Substring.first('=')::split)
            .collect(concatenating(BiOptional::stream))
            .collect(toImmutableListMultimap()))
        .containsExactly("name", "joe", "age", "10")
        .inOrder();
  }

  @Test public void testToBiStream_exceptionThrownEagerly() {
    assertThrows(
        NumberFormatException.class,
        () -> Stream.of("1", "b").collect(toBiStream(identity(), v -> Integer.parseInt(v))));
    assertThrows(
        NumberFormatException.class,
        () -> Stream.of("1", "b").collect(toBiStream(v -> Both.of(v, Integer.parseInt(v)))));
  }

  @Test public void testGroupingBy() {
    Map<Integer, List<Integer>> groups =
        Stream.of(0, 1, 2).collect(BiStream.groupingBy(n -> n / 2)).toMap();
    assertThat(groups).containsExactly(0, ImmutableList.of(0, 1), 1, ImmutableList.of(2)).inOrder();
  }

  @Test public void testGroupingBy_withReducer_empty() {
    Stream<String> inputs = Stream.empty();
    assertThat(inputs.collect(BiStream.groupingBy(s -> s.charAt(0), String::concat)).toMap())
        .isEmpty();
  }

  @Test public void testGroupingBy_withReducer_singleElement() {
    Stream<String> inputs = Stream.of("foo");
    assertThat(inputs.collect(BiStream.groupingBy(s -> s.charAt(0), String::concat)).toMap())
        .containsExactly('f', "foo");
  }

  @Test public void testGroupingBy_withReducer_twoElementsSameGroup() {
    Stream<String> inputs = Stream.of("foo", "fun");
    assertThat(inputs.collect(BiStream.groupingBy(s -> s.charAt(0), String::concat)).toMap())
        .containsExactly('f', "foofun");
  }

  @Test public void testGroupingBy_withReducer_twoElementsDifferentGroups() {
    Stream<String> inputs = Stream.of("foo", "blah");
    assertThat(inputs.collect(BiStream.groupingBy(s -> s.charAt(0), String::concat)).toMap())
        .containsExactly('f', "foo", 'b', "blah");
  }

  @Test public void testGroupingBy_withMapperAndReducer_empty() {
    Stream<String> inputs = Stream.empty();
    assertKeyValues(
            inputs
                .collect(BiStream.groupingBy(s -> s.charAt(0), String::length, Integer::sum)))
        .isEmpty();
  }

  @Test public void testGroupingBy_withMapperAndReducer_singleElement() {
    Stream<String> inputs = Stream.of("foo");
    assertKeyValues(
            inputs
                .collect(BiStream.groupingBy(s -> s.charAt(0), String::length, Integer::sum)))
        .containsExactly('f', 3);
  }

  @Test public void testGroupingBy_withMapperAndReducer_twoElementsSameGroup() {
    Stream<String> inputs = Stream.of("foo", "feed");
    assertKeyValues(
            inputs
                .collect(BiStream.groupingBy(s -> s.charAt(0), String::length, Integer::sum)))
        .containsExactly('f', 7);
  }

  @Test public void testGroupingBy_withMapperAndReducer_twoElementsDifferentGroups() {
    Stream<String> inputs = Stream.of("foo", "blah");
    assertKeyValues(
            inputs
                .collect(BiStream.groupingBy(s -> s.charAt(0), String::length, Integer::sum)))
        .containsExactly('f', 3, 'b', 4)
        .inOrder();
  }

  @Test public void testGroupingByEach_withCollector() {
    Map<Character, Long> groups =
        Stream.of("dog", "food", "fog")
            .collect(groupingByEach(s -> charactersOf(s), Collectors.counting()))
            .toMap();
    assertThat(groups).containsExactly('d', 2L, 'o', 4L, 'g', 2L, 'f', 2L).inOrder();
  }

  @Test public void testGroupingByEach_withMapperAndReducer() {
    AtomicInteger index = new AtomicInteger();
    Map<Character, String> groups =
        Stream.of("dog", "food", "fog")
            .collect(groupingByEach(s -> charactersOf(s), s -> index.incrementAndGet() + s, String::concat))
            .toMap();
    assertThat(groups)
        .containsExactly(
            'd', "1dog2food",
            'o', "1dog2food2food3fog",
            'g', "1dog3fog",
            'f', "2food3fog")
        .inOrder();
  }

  @Test public void testGroupingByEach_withMapperAndCollector() {
    AtomicInteger index = new AtomicInteger();
    Map<Character, List<String>> groups =
        Stream.of("dog", "food", "fog")
            .collect(groupingByEach(s -> charactersOf(s), s -> index.incrementAndGet() + s, toList()))
            .toMap();
    assertThat(groups)
        .containsExactly(
            'd', ImmutableList.of("1dog", "2food"),
            'o', ImmutableList.of("1dog", "2food", "2food", "3fog"),
            'g', ImmutableList.of("1dog", "3fog"),
            'f', ImmutableList.of("2food", "3fog"))
        .inOrder();
  }

  @Test public void testGroupingBy_withCollector() {
    Map<String, Long> groups =
        Stream.of(1, 1, 2, 3, 3)
            .collect(BiStream.groupingBy(Object::toString, Collectors.counting()))
            .toMap();
    assertThat(groups).containsExactly("1", 2L, "2", 1L, "3", 2L).inOrder();
  }

  @Test public void testConcatMap() {
    assertThat(BiStream.concat(ImmutableMap.of(1, "one"), ImmutableMap.of(2, "two")).toMap())
        .containsExactly(1, "one", 2, "two")
        .inOrder();
    assertThat(
            BiStream.concat(
                    ImmutableMap.of(1, "one"),
                    ImmutableMap.of(2, "two"),
                    ImmutableMap.of(3, "three"))
                .toMap())
        .containsExactly(1, "one", 2, "two", 3, "three")
        .inOrder();
  }

  @Test public void testConcatMap_nullNotAllowed() {
    Map<?, ?> third = null;
    assertThrows(
        NullPointerException.class,
        () -> BiStream.concat(ImmutableMap.of(), ImmutableMap.of(), third));
  }

  @Test public void testConcatBiStream() {
    assertThat(BiStream.concat(ImmutableMap.of(1, "one"), ImmutableMap.of(2, "two")).toMap())
        .containsExactly(1, "one", 2, "two")
        .inOrder();
    assertThat(
            BiStream.concat(
                    BiStream.of(1, "one"),
                    BiStream.of(2, "two"),
                    BiStream.of(3, "three"))
                .toMap())
        .containsExactly(1, "one", 2, "two", 3, "three")
        .inOrder();
  }

  @Test public void testConcatBiStream_nullNotAllowed() {
    BiStream<?, ?> third = null;
    assertThrows(
        NullPointerException.class,
        () -> BiStream.concat(BiStream.empty(), BiStream.empty(), third));
  }

  @Test public void testConcatStreamOfBiStreams() {
    assertThat(BiStream.concat(Stream.of(BiStream.of(1, "one"), BiStream.of(2, "two"))).toMap())
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void testFlattening_emptyStream() {
    assertThat(
            Stream.<ImmutableMap<Integer, String>>empty()
                .collect(concatenating(BiStream::from))
                .toMap())
        .isEmpty();
  }

  @Test public void testFlattening_nestedBiStreamsNotConsumed() {
    BiStream<Integer, String> nested = BiStream.of(1, "one");
    assertThat(Stream.of(nested).collect(BiStream.concatenating(identity()))).isNotNull();
    assertKeyValues(nested).containsExactly(1, "one").inOrder();
  }

  @Test public void testFlattening() {
    assertThat(
            Stream.of(ImmutableMap.of(1, "one"), ImmutableMap.of(2, "two"))
                .collect(concatenating(BiStream::from))
                .toMap())
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void testCrossJoining() {
    assertKeyValues(Stream.of(1, null, 2).collect(crossJoining(Stream.of("foo", null))))
        .containsExactly(1, "foo", null, "foo", 2, "foo", 1, null, null, null, 2, null)
        .inOrder();
  }

  @Test public void testCrossJoining_emptyLeft() {
    assertKeyValues(Stream.empty().collect(crossJoining(Stream.of("foo", "bar")))).isEmpty();
  }

  @Test public void testCrossJoining_emptyRight() {
    assertKeyValues(Stream.of(1, 2).collect(crossJoining(Stream.empty()))).isEmpty();
  }

  @Test public void testCrossJoining_infiniteRight_thenLimit() {
    assertKeyValues(Stream.empty().collect(crossJoining(indexesFrom(0))).limit(2))
        .isEmpty();
    assertKeyValues(
            Stream.of("foo", "bar")
                .collect(crossJoining(indexesFrom(0)))
                .limit(4))
        .containsExactly("foo", 0, "bar", 0, "foo", 1, "bar", 1)
        .inOrder();
  }

  @Test public void testBiStream_withValueMapper() {
    assertKeyValues(biStream(Stream.of(1, 2), Object::toString))
        .containsExactly(1, "1", 2, "2")
        .inOrder();
    assertKeyValues(biStream(asList(1, 2), Object::toString))
        .containsExactly(1, "1", 2, "2")
        .inOrder();
  }

  @Test public void testBiStream_withKeyMapper() {
    assertKeyValues(biStream(Object::toString, Stream.of(1, 2)))
        .containsExactly("1", 1, "2", 2)
        .inOrder();
    assertKeyValues(biStream(Object::toString, asList(1, 2)))
        .containsExactly("1", 1, "2", 2)
        .inOrder();
  }

  @Test public void testToAdjacentPairs_empty() {
    Stream<String> stream = Stream.of().collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).isEmpty();
  }

  @Test public void testToAdjacentPairs_oneElement() {
    Stream<String> stream = Stream.of(1).collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).isEmpty();
  }

  @Test public void testToAdjacentPairs_twoElements() {
    Stream<String> stream =
        Stream.of(1, 2).collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2").inOrder();
  }

  @Test public void testToAdjacentPairs_threeElements() {
    Stream<String> stream =
        Stream.of(1, 2, 3).collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2", "2:3").inOrder();
  }

  @Test public void testToAdjacentPairs_fourElements() {
    Stream<String> stream =
        Stream.of(1, 2, 3, 4).collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2", "2:3", "3:4").inOrder();
  }

  @Test public void testToAdjacentPairs_nullPadding() {
    Stream<String> stream =
        Stream.of(null, 1, 2, 3, null).collect(toAdjacentPairs()).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("null:1", "1:2", "2:3", "3:null").inOrder();
  }

  @Test public void adjacentPairsFrom_empty() {
    Stream<String> stream = adjacentPairsFrom().mapToObj(Joiner.on(':')::join);
    assertThat(stream).isEmpty();
  }

  @Test public void adjacentPairsFrom_oneElement() {
    Stream<String> stream = adjacentPairsFrom(1).mapToObj(Joiner.on(':')::join);
    assertThat(stream).isEmpty();
  }

  @Test public void adjacentPairsFrom_twoElements() {
    Stream<String> stream = adjacentPairsFrom(1, 2).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2").inOrder();
  }

  @Test public void adjacentPairsFrom_threeElements() {
    Stream<String> stream = adjacentPairsFrom(1, 2, 3).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2", "2:3").inOrder();
  }

  @Test public void adjacentPairsFrom_fourElements() {
    Stream<String> stream = adjacentPairsFrom(1, 2, 3, 4).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("1:2", "2:3", "3:4").inOrder();
  }

  @Test public void adjacentPairs_nullPadding() {
    Stream<String> stream =
        adjacentPairsFrom(null, 1, 2, 3, null).mapToObj(Joiner.on(':')::join);
    assertThat(stream).containsExactly("null:1", "1:2", "2:3", "3:null").inOrder();
  }

  @Test public void testBuilder_cannotAddAfterBuild() {
    BiStream.Builder<String, String> builder = BiStream.builder();
    assertKeyValues(builder.build()).isEmpty();
    assertThrows(IllegalStateException.class, () -> builder.add("foo", "bar"));
  }

  @Test public void testCollect_mutableReduction() {
    ImmutableMap<String, Integer> result = BiStream.of("one", 1, "two", 2)
        .collect(
            ImmutableMap.<String, Integer>builder(),
            ImmutableMap.Builder::put)
        .build();
    assertThat(result).containsExactly("one", 1, "two", 2);
  }

  @Test public void testCollect_concurrentMutableReduction() {
    BiStream<String, Integer> parallel =
        biStream(Object::toString, Stream.of(1, 2, 3, 4, 5).parallel());
    Map<String, Integer> result = parallel
        .collect(
            ImmutableMap.<String, Integer>builder(),
            ImmutableMap.Builder::put)
        .build();
    assertThat(result).containsExactly("1", 1, "2", 2, "3", 3, "4", 4, "5", 5).inOrder();
  }

  @Test public void testCollect_withFinisher() {
    ImmutableBiMap<String, Integer> biMap = BiStream.of("one", 1)
        .collect(toMap(), com.google.common.collect.ImmutableBiMap::copyOf);
    assertThat(biMap)
        .containsExactly("one", 1);
  }

  @Test public void testPaginatedStream_singlePage() {
    PaginationService<String> service = spy(new PaginationService<>("hello", "world"));
    assertThat(service.paginate(0, 2)).containsExactly("hello", "world").inOrder();
    verify(service).getPage(0, 2);
    verifyNoMoreInteractions(service);
  }

  @Test public void testPaginatedStream_exhaustTwoPages() {
    PaginationService<String> service = spy(new PaginationService<>("hello", "world"));
    assertThat(service.paginate(0, 1)).containsExactly("hello", "world").inOrder();
    Mockito.verify(service).getPage(0, 1);
    verify(service).getPage(1, 1);
    verifyNoMoreInteractions(service);
  }

  @Test public void testPaginatedStream_secondPageSkipped() {
    PaginationService<String> service = spy(new PaginationService<>("hello", "world"));
    assertThat(service.paginate(0, 1).limit(1)).containsExactly("hello");
    verify(service).getPage(0, 1);
    verifyNoMoreInteractions(service);
  }

  @Test public void testPaginatedStream_secondPageNotExhausted() {
    PaginationService<String> service = spy(new PaginationService<>("a", "b", "c", "d", "e"));
    assertThat(service.paginate(0, 2).limit(3)).containsExactly("a", "b", "c");
    verify(service).getPage(0, 2);
    verify(service).getPage(2, 2);
    verifyNoMoreInteractions(service);
  }

  private static class PaginationService<T> {
    private final ImmutableList<T> data;

    @SafeVarargs
    PaginationService(T... data) {
      this.data = ImmutableList.copyOf(data);
    }

    final PaginationResponse<T> getPageResponse(PaginationRequest request) {
      return getPage(request.from, request.pageSize);
    }

    PaginationResponse<T> getPage(int from, int pageSize) {
      int next = from + pageSize;
      return new PaginationResponse<>(
          data.stream().skip(from).limit(pageSize).collect(toImmutableList()),
          next >= data.size() ? -1 : next);
    }

    /** A typical paginated stream based off of Iteration. */
    final Stream<T> paginate(int startingIndex, int pageSize) {
      return BiStream.repeat(
              this::getPageResponse,
              new PaginationRequest(startingIndex, pageSize),
              (req, resp) -> optional(resp.next >= 0, new PaginationRequest(resp.next, pageSize)))
          .flatMapToObj((req, resp) -> resp.rows.stream());
    }
  }

  private static final class PaginationRequest {
    final int from;
    final int pageSize;

    PaginationRequest(int from, int pageSize) {
      this.from = from;
      this.pageSize = pageSize;
    }
  }

  private static final class PaginationResponse<T> {
    final ImmutableList<T> rows;
    final int next; // -1 means no more

    PaginationResponse(List<T> rows, int next) {
      this.rows = ImmutableList.copyOf(rows);
      this.next = next;
    }
  }

  static<K,V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    Multimap<?, ?> multimap = stream.collect(new BiCollector<K, V, Multimap<K, V>>() {
      @Override
      public <E> Collector<E, ?, Multimap<K, V>> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return BiStreamTest.toLinkedListMultimap(toKey,toValue);
      }
    });
    return assertThat(multimap);
  }

  private static <T, K, V> Collector<T, ?, Multimap<K, V>> toLinkedListMultimap(
      Function<? super T, ? extends K> toKey, Function<? super T, ? extends V> toValue) {
    return Collector.of(
        LinkedListMultimap::create,
        (m, e) -> m.put(toKey.apply(e), toValue.apply(e)),
        (m1, m2) -> {
          m1.putAll(m2);
          return m1;
        });
  }

  private static IterableSubject assertSequential(Stream<?> stream) {
    assertThat(stream.isParallel()).isFalse();
    ConcurrentMap<Long, Object> threads = new ConcurrentHashMap<>();
    List<?> list =
        stream
            .peek(
                v -> {
                  threads.put(Thread.currentThread().getId(), v);
                })
            .collect(toList());
    assertThat(threads).hasSize(1);
    return assertThat(list);
  }

  private static <K, V> BiCollector<K, V, ImmutableListMultimap<K, V>> toImmutableListMultimap() {
    return ImmutableListMultimap::toImmutableListMultimap;
  }

  private static Stream<Character> charactersOf(String s) {
    return s.chars().mapToObj(c -> (char) c);
  }
}

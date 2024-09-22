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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.stream.BiCollectors.collectingAndThen;
import static com.google.mu.util.stream.BiCollectors.counting;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.BiCollectors.inverse;
import static com.google.mu.util.stream.BiCollectors.maxByKey;
import static com.google.mu.util.stream.BiCollectors.maxByValue;
import static com.google.mu.util.stream.BiCollectors.minByKey;
import static com.google.mu.util.stream.BiCollectors.minByValue;
import static com.google.mu.util.stream.BiCollectors.partitioningBy;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.BiStream.biStream;
import static com.google.mu.util.stream.BiStreamTest.assertKeyValues;
import static java.util.Collections.nCopies;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.mu.util.BiOptional;

@RunWith(JUnit4.class)
public class BiCollectorsTest {

  @Test public void testToMap_nullKey() {
    ImmutableList<Integer> list = ImmutableList.of(1, 2, 3, 4);
    assertThat(biStream(t -> null, list).collect(toMap(Integer::sum)))
        .containsExactly(null, 10)
        .inOrder();
  }

  @Test public void testToMap_valuesCollected() {
    ImmutableList<Town> towns =
        ImmutableList.of(new Town("WA", 100), new Town("WA", 50), new Town("IL", 200));
    assertThat(biStream(Town::getState, towns).collect(toMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 150, "IL", 200)
        .inOrder();
  }

  @Test public void testToMap_keyEncounterOrderRetainedThroughValueCollector() {
    ImmutableList<Town> towns =
        ImmutableList.of(
            new Town("WA", 1),
            new Town("FL", 2),
            new Town("WA", 3),
            new Town("IL", 4),
            new Town("AZ", 5),
            new Town("OH", 6),
            new Town("IN", 7),
            new Town("CA", 8),
            new Town("CA", 9));
    assertThat(biStream(Town::getState, towns).collect(toMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 4, "FL", 2, "IL", 4, "AZ", 5, "OH", 6, "IN", 7, "CA", 17)
        .inOrder();
  }

  @Test public void testToMap_empty() {
    ImmutableList<Town> towns = ImmutableList.of();
    assertThat(biStream(Town::getState, towns).collect(toMap(summingInt(Town::getPopulation))))
        .isEmpty();
  }

  @Test public void testToMap_withSupplier() {
    LinkedHashMap<String, Integer> map =
        BiStream.of("one", 1, "two", 2).collect(toLinkedHashMap());
    assertThat(map).containsExactly("one", 1, "two", 2).inOrder();
  }

  @Test public void testToMap_withSupplier_empty() {
    assertThat(BiStream.empty().collect(toLinkedHashMap())).isEmpty();
  }

  @Test public void testToMap_withSupplier_nullKey() {
    LinkedHashMap<String, String> map =
        BiStream.of((String) null, "nonnull").collect(toLinkedHashMap());
    assertThat(map).containsExactly(null, "nonnull").inOrder();
  }

  @Test public void testToMap_withSupplier_nullKey_orderPreserved() {
    LinkedHashMap<String, String> map =
        BiStream.of("foo", "x", (String) null, "nonnull", "bar", "y").collect(toLinkedHashMap());
    assertThat(map).containsExactly("foo", "x", null, "nonnull", "bar", "y").inOrder();
  }

  @Test public void testToMap_withSupplier_nullValue() {
    LinkedHashMap<String, String> map =
        BiStream.of("foo", (String) null).collect(toLinkedHashMap());
    assertThat(map).containsExactly("foo", null).inOrder();
  }

  @Test public void testToMap_withSupplier_nullValue_orderPreserved() {
    LinkedHashMap<String, String> map =
        BiStream.of("foo", "x", "bar", (String) null, "zoo", "y").collect(toLinkedHashMap());
    assertThat(map).containsExactly("foo", "x", "bar", null, "zoo", "y").inOrder();
  }

  @Test public void testToMap_withSupplier_duplicateKey() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("foo", 1, "foo", 2).collect(toLinkedHashMap()));
    assertThat(thrown).hasMessageThat().contains("Duplicate key: [foo]");
  }

  @Test public void testToMap_withSupplier_duplicateNullKey() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of(null, 1, null, 2).collect(toLinkedHashMap()));
    assertThat(thrown).hasMessageThat().contains("Duplicate key: [null]");
  }

  @Test public void testToMap_withSupplier_duplicateKeys_nullThenNull() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("foo", null, "foo", null).collect(toLinkedHashMap()));
    assertThat(thrown).hasMessageThat().contains("Duplicate key: [foo]");
  }

  @Test public void testToMap_withSupplier_duplicateKeys_nullThenNonNull() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("foo", null, "foo", "nonnull").collect(toLinkedHashMap()));
    assertThat(thrown).hasMessageThat().contains("Duplicate key: [foo]");
  }

  @Test public void testToMap_withSupplier_duplicateKeys_nonNullThenNull() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("foo", "nonnull", "foo", null).collect(toLinkedHashMap()));
    assertThat(thrown).hasMessageThat().contains("Duplicate key: [foo]");
  }

  @Test public void testToMap_withSupplier_mapSupplierReturnsNull() {
    assertThrows(
        NullPointerException.class,
        () -> BiStream.of("foo", "nonnull", "foo", null).collect(toMap(() -> null)));
  }

  @Test public void testToImmutableMap_covariance() {
    Map<Object, String> map = BiStream.of(1, "one").collect(toMap());
    assertThat(map).containsExactly(1, "one");
  }

  @Test public void testCounting() {
    assertThat(BiStream.of(1, "one", 2, "two").collect(BiCollectors.counting())).isEqualTo(2L);
  }

  @Test public void testCountingDistinct_distinctEntries() {
    assertThat(BiStream.of(1, "one", 2, "two").collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
    assertThat(BiStream.of(1, "one", 1, "uno").collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
    assertThat(BiStream.of(1, "one", 2, "one").collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
  }

  @Test public void testCountingDistinct_duplicateEntries() {
    assertThat(BiStream.of(1, "one", 1, "one").collect(BiCollectors.countingDistinct()))
        .isEqualTo(1);
  }

  @Test public void testCountingDistinct_duplicateEntries_withNulls() {
    assertThat(BiStream.of(1, null, 1, null).collect(BiCollectors.countingDistinct()))
        .isEqualTo(1);
    assertThat(BiStream.of(null, null, null, null).collect(BiCollectors.countingDistinct()))
        .isEqualTo(1);
    assertThat(BiStream.of(null, "one", null, "one").collect(BiCollectors.countingDistinct()))
        .isEqualTo(1);
  }

  @Test public void testCountingDistinct_distinctEntries_withNulls() {
    assertThat(BiStream.of(1, "one", 1, null).collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
    assertThat(BiStream.of(1, "one", null, "one").collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
    assertThat(BiStream.of(1, "one", null, null).collect(BiCollectors.countingDistinct()))
        .isEqualTo(2);
  }

  @Test public void testSummingInt() {
    assertThat(BiStream.of(1, 10, 2, 20).collect(BiCollectors.summingInt((a, b) -> a + b))).isEqualTo(33);
  }

  @Test public void testSummingLong() {
    assertThat(BiStream.of(1L, 10, 2L, 20).collect(BiCollectors.summingLong((a, b) -> a + b))).isEqualTo(33L);
  }

  @Test public void testSummingDouble() {
    assertThat(BiStream.of(1, 10D, 2, 20D).collect(BiCollectors.summingDouble((a, b) -> a + b))).isEqualTo(33D);
  }

  @Test public void testAveragingInt() {
    assertThat(BiStream.of(1, 3, 2, 4).collect(BiCollectors.averagingInt((Integer a, Integer b) -> a + b)))
        .isEqualTo(5D);
  }

  @Test public void testAveragingLong() {
    assertThat(BiStream.of(1L, 3, 2L, 4).collect(BiCollectors.averagingLong((Long a, Integer b) -> a + b)))
        .isEqualTo(5D);
  }

  @Test public void testAveragingDouble() {
    assertThat(BiStream.of(1L, 3, 2L, 4).collect(BiCollectors.averagingDouble((Long a, Integer b) -> a + b)))
        .isEqualTo(5D);
  }

  @Test public void testSummarizingInt() {
    assertThat(BiStream.of(1, 10, 2, 20).collect(BiCollectors.summarizingInt((a, b) -> a + b)).getMin())
        .isEqualTo(11);
  }

  @Test public void testSummarizingLong() {
    assertThat(BiStream.of(1, 10, 2, 20).collect(BiCollectors.summarizingLong((a, b) -> a + b)).getMin())
        .isEqualTo(11L);
  }

  @Test public void testSummarizingDouble() {
    assertThat(BiStream.of(1, 10, 2, 20).collect(BiCollectors.summarizingDouble((a, b) -> a + b)).getMin())
        .isEqualTo(11D);
  }

  @Test public void testGroupingBy_empty() {
    assertKeyValues(BiStream.empty().collect(groupingBy(Object::toString, toList()))).isEmpty();
  }

  @Test public void testGroupingBy_singleEntry() {
    assertKeyValues(BiStream.of(1, "one").collect(groupingBy(Object::toString, toList())))
        .containsExactly("1", ImmutableList.of("one"));
  }

  @Test public void testGroupingBy_distinctEntries() {
    assertKeyValues(BiStream.of(1, "one", 2, "two").collect(groupingBy(Object::toString, toList())))
        .containsExactly("1", ImmutableList.of("one"), "2", ImmutableList.of("two"));
  }

  @Test public void testGroupingBy_multipleValuesGrouped() {
    assertKeyValues(BiStream.of(1, "one", 1L, "uno").collect(groupingBy(Object::toString, toList())))
        .containsExactly("1", ImmutableList.of("one", "uno"));
  }

  @Test public void testGroupingBy_groupedByDiff() {
    assertKeyValues(
            BiStream.of(1, 3, 2, 4, 11, 111)
                .collect(groupingBy((a, b) -> b - a, ImmutableSetMultimap::toImmutableSetMultimap)))
        .containsExactly(2, ImmutableSetMultimap.of(1, 3, 2, 4), 100, ImmutableSetMultimap.of(11, 111));
  }

  @Test public void testGroupingBy_toNestedBiStream() {
    Map<Integer, Map<Integer, Integer>> nested =
        BiStream.of(1, 3, 2, 4, 11, 111)
            .collect(groupingBy((a, b) -> b - a))
            .mapValues(BiStream::toMap)
            .toMap();
    assertThat(nested)
        .containsExactly(
            2, ImmutableMap.of(1, 3, 2, 4),
            100, ImmutableMap.of(11, 111));
  }

  @Test public void testGroupingBy_withReducer_empty() {
    BiStream<String, Integer> salaries = BiStream.empty();
    assertKeyValues(salaries.collect(groupingBy(s -> s.charAt(0), (a, b) -> a + b))).isEmpty();
  }

  @Test public void testGroupingBy_withReducer_singleEntry() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100);
    assertKeyValues(salaries.collect(groupingBy(s -> s.charAt(0), Integer::sum)))
        .containsExactly('J', 100);
  }

  @Test public void testGroupingBy_withReducer_twoEntriesSameGroup() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100, "John", 200);
    assertKeyValues(salaries.collect(groupingBy(s -> s.charAt(0), Integer::sum)))
        .containsExactly('J', 300);
  }

  @Test public void testGroupingBy_withReducer_twoEntriesDifferentGroups() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100, "Tom", 200);
    assertKeyValues(salaries.collect(groupingBy(s -> s.charAt(0), Integer::sum)))
        .containsExactly('J', 100, 'T', 200)
        .inOrder();
  }

  @Test public void testPartitioningBy_sameDownstreamCollector() {
    String result =
        BiStream.of(1, "one", 2, "two", 3, "three", 4, "four", 5, "five")
            .collect(partitioningBy((i, n) -> i % 2 == 1))
            .andThen((odds, evens) -> "odd:" + odds.toMap() + "; even:" + evens.toMap());
    assertThat(result).isEqualTo("odd:{1=one, 3=three, 5=five}; even:{2=two, 4=four}");
  }

  @Test public void testPartitioningBy_differentDownstreamCollectors() {
    String result =
        BiStream.of(1, "one", 2, "two", 3, "three", 4, "four", 5, "five")
            .collect(partitioningBy((i, n) -> i % 2 == 1, toMap(), counting()))
            .andThen((odds, evens) -> "odd:" + odds + "; count of even:" + evens);
    assertThat(result).isEqualTo("odd:{1=one, 3=three, 5=five}; count of even:2");
  }

  @Test public void testMapping_downstreamCollector() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100, "Tom", 200);
    assertThat(salaries.collect(BiCollectors.mapping(Joiner.on(':')::join, toList())))
        .containsExactly("Joe:100", "Tom:200")
        .inOrder();
  }

  @Test public void testMapping_downstreamBiCollector() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100, "Tom", 200);
    BiCollector<String, Integer, ImmutableMap<Integer, String>> toReverseMap =
        BiCollectors.mapping((k, v) -> v, (k, v) -> k, ImmutableMap::toImmutableMap);
    assertThat(salaries.collect(toReverseMap))
        .containsExactly(100, "Joe", 200, "Tom")
        .inOrder();
  }

  @Test public void testMapping_pairWise() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 100, "Tom", 200);
    BiCollector<String, Integer, ImmutableMap<Integer, String>> toReverseMap =
        BiCollectors.mapping(
            (k, v) -> BiOptional.of(v, k).orElseThrow(),
            ImmutableMap::toImmutableMap);
    assertThat(salaries.collect(toReverseMap))
        .containsExactly(100, "Joe", 200, "Tom")
        .inOrder();
  }

  @Test public void testFlatMapping_toStream() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 1, "Tom", 2);
    assertThat(salaries.collect(BiCollectors.flatMapping((k, c) -> nCopies(c, k).stream(), toList())))
        .containsExactly("Joe", "Tom", "Tom")
        .inOrder();
  }

  @Test public void testFlatMapping_toBiStream() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 1, "Tom", 2);
    ImmutableListMultimap<String, Integer> result = salaries.collect(
        BiCollectors.flatMapping(
            (String k, Integer c) -> biStream(nCopies(c, k)).mapValues(u -> c),
            ImmutableListMultimap::toImmutableListMultimap));
    assertThat(result)
        .containsExactly("Joe", 1, "Tom", 2, "Tom", 2)
        .inOrder();
  }

  @Test public void testCollectingAndThen() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 1, "Tom", 2);
    Stream<String> result = salaries
        .collect(collectingAndThen(
            stream -> stream.mapToObj((name, salary) -> name + ":" + salary)));
    assertThat(result)
        .containsExactly("Joe:1", "Tom:2")
        .inOrder();
  }

  @Test public void testInverse_toStream() {
    BiStream<String, Integer> salaries = BiStream.of("Joe", 1, "Tom", 2);
    assertThat(salaries.collect(inverse(toMap())))
        .containsExactly(1, "Joe", 2, "Tom")
        .inOrder();
  }

  @Test public void testMaxByKey_found() {
    assertThat(BiStream.of(1, "y", 2, "x").collect(maxByKey(naturalOrder())))
        .isEqualTo(BiOptional.of(2, "x"));
  }

 @Test public void testMaxByKey_multipleMax_firstWins() {
    assertThat(BiStream.of(1, "y", 2, "x", 2, "a").collect(maxByKey(naturalOrder())))
        .isEqualTo(BiOptional.of(2, "x"));
  }

 @Test public void testMaxByKey_notFound() {
    assertThat(BiStream.<String, Integer>empty().collect(maxByKey(naturalOrder())))
        .isEqualTo(BiOptional.empty());
  }

 @Test public void testMinByKey_found() {
    assertThat(BiStream.of(1, "y", 2, "x").collect(minByKey(naturalOrder())))
        .isEqualTo(BiOptional.of(1, "y"));
  }

 @Test public void testMinByKey_multipleMin_firstWins() {
    assertThat(BiStream.of(1, "y", 2, "x", 1, "a").collect(minByKey(naturalOrder())))
        .isEqualTo(BiOptional.of(1, "y"));
  }

 @Test public void testMinByKey_notFound() {
    assertThat(BiStream.<String, Integer>empty().collect(minByKey(naturalOrder())))
        .isEqualTo(BiOptional.empty());
  }

 @Test public void testMaxByValue_found() {
    assertThat(BiStream.of(1, "y", 2, "x").collect(maxByValue(naturalOrder())))
        .isEqualTo(BiOptional.of(1, "y"));
  }

 @Test public void testMaxByValue_multipleMax_firstWins() {
    assertThat(BiStream.of(1, "y", 2, "x", 3, "y").collect(maxByValue(naturalOrder())))
        .isEqualTo(BiOptional.of(1, "y"));
  }

 @Test public void testMaxByValue_notFound() {
    assertThat(BiStream.<String, Integer>empty().collect(maxByValue(naturalOrder())))
        .isEqualTo(BiOptional.empty());
  }

 @Test public void testMinByValue_found() {
    assertThat(BiStream.of(1, "y", 2, "x").collect(minByValue(naturalOrder())))
        .isEqualTo(BiOptional.of(2, "x"));
  }

 @Test public void testMinByValue_multipleMin_firstWins() {
    assertThat(BiStream.of(1, "y", 2, "x", 3, "x").collect(minByValue(naturalOrder())))
        .isEqualTo(BiOptional.of(2, "x"));
  }

 @Test public void testMinByValue_notFound() {
    assertThat(BiStream.<String, Integer>empty().collect(minByValue(naturalOrder())))
        .isEqualTo(BiOptional.empty());
  }

  private static <K, V> BiCollector<K, V, LinkedHashMap<K, V>> toLinkedHashMap() {
    return BiCollectors.toMap(() -> new LinkedHashMap<>());
  }

  private static final class Town {
    private final String state;
    private final int population;

    Town(String state, int population) {
      this.state = state;
      this.population = population;
    }

    int getPopulation() {
      return population;
    }

    String getState() {
      return state;
    }
  }
}

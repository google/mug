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
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.GuavaCollectors.flatteningToImmutableListMultimap;
import static com.google.mu.util.stream.GuavaCollectors.flatteningToImmutableSetMultimap;
import static com.google.mu.util.stream.GuavaCollectors.indexingBy;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableBiMap;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableListMultimap;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableMap;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableMultiset;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableSetMultimap;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableSortedMap;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableTable;
import static com.google.mu.util.stream.GuavaCollectors.toMultimap;
import static java.util.Arrays.asList;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.TreeMultimap;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class GuavaCollectorsTest {
  @Test public void testToImmutableMap_valuesCollected() {
    ImmutableList<Town> towns =
        ImmutableList.of(new Town("WA", 100), new Town("WA", 50), new Town("IL", 200));
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toImmutableMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 150, "IL", 200)
        .inOrder();
  }

  @Test public void testToImmutableMap_keyEncounterOrderRetainedThroughValueCollector() {
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
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toImmutableMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 4, "FL", 2, "IL", 4, "AZ", 5, "OH", 6, "IN", 7, "CA", 17)
        .inOrder();
  }

  @Test public void testToImmutableMap_empty() {
    ImmutableList<Town> towns = ImmutableList.of();
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toImmutableMap(summingInt(Town::getPopulation))))
        .isEmpty();
  }

  @Test public void testToImmutableSortedMap() {
    assertThat(BiStream.of(2, "two", 1, "one").collect(GuavaCollectors.toImmutableSortedMap(naturalOrder())))
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void testToImmutableSortedMap_withDuplicateKeys() {
    assertThat(
            BiStream.of(2, "two", 1, "one", 1, "uno")
                .collect(toImmutableSortedMap(naturalOrder(), (a, b) -> b)))
        .containsExactly(1, "uno", 2, "two")
        .inOrder();
  }

  @Test public void testToImmutableMap_covariance() {
    ImmutableMap<Object, String> map = BiStream.of(1, "one").collect(toImmutableMap());
    assertThat(map).containsExactly(1, "one");
  }

  @Test public void testToMultimap_treeMultimap() {
    TreeMultimap<Integer, String> map =
        BiStream.of(2, "b", 1, "a").collect(toMultimap(TreeMultimap::create));
    assertThat(map.keySet()).containsExactly(1, 2).inOrder();
  }

  @Test public void testToImmutableListMultimap_covariance() {
    ImmutableListMultimap<Object, String> map =
        BiStream.of(1, "one", 1, "uno").collect(toImmutableListMultimap());
    assertThat(map).containsExactly(1, "one", 1, "uno");
  }

  @Test public void testToImmutableSetMultimap_covariance() {
    ImmutableSetMultimap<Object, String> map =
        BiStream.of(1, "one", 1, "uno").collect(toImmutableSetMultimap());
    assertThat(map).containsExactly(1, "one", 1, "uno");
  }

  @Test public void testToListMultimap() {
    assertThat(BiStream.of("one", 1, "two", 2).collect(toImmutableListMultimap()))
        .containsExactly("one", 1, "two", 2);
  }

  @Test public void testToSetMultimap() {
    assertThat(BiStream.of("one", 1, "two", 2).collect(toImmutableSetMultimap()))
        .containsExactly("one", 1, "two", 2);
  }

  @Test public void testFlatteningToImmutableListMultimap() {
    ImmutableListMultimap<Object, String> map =
        BiStream.of(1, asList("one", "uno"))
            .collect(flatteningToImmutableListMultimap(List::stream));
    assertThat(map).containsExactly(1, "one", 1, "uno");
  }

  @Test public void testFlatteningToImmutableSetMultimap() {
    ImmutableSetMultimap<Object, String> map =
        BiStream.of(1, asList("one", "one", "uno"))
            .collect(flatteningToImmutableSetMultimap(List::stream));
    assertThat(map).containsExactly(1, "one", 1, "uno");
  }

  @Test public void testToImmutableMultiset() {
    assertThat(BiStream.of(".", 3, "?", 1).collect(toImmutableMultiset(Integer::intValue)))
        .containsExactly(".", ".", ".", "?")
        .inOrder();
  }

  @Test public void testTwoDimensionalGroupingBy_collectToImmutableTable() {
    ImmutableTable<Character, Character, List<Integer>> table =
        BiStream.of("ab", 1, "bc", 2)
            .collect(groupingBy((s, i) -> s.charAt(0), groupingBy(s -> s.charAt(1), toList())))
            .collect(toImmutableTable());
    assertThat(table.rowMap())
        .containsExactly('a', ImmutableMap.of('b', asList(1)), 'b', ImmutableMap.of('c', asList(2)))
        .inOrder();
  }

  @Test public void testCascadingGroupingBy_collectToImmutableTable() {
    ImmutableTable<Character, Character, List<Integer>> table =
        BiStream.of("ab", 1, "bc", 2)
            .collect(groupingBy((s, i) -> s.charAt(0), groupingBy(s -> s.charAt(1), toList())))
            .collect(toImmutableTable());
    assertThat(table.rowMap())
        .containsExactly('a', ImmutableMap.of('b', asList(1)), 'b', ImmutableMap.of('c', asList(2)))
        .inOrder();
  }

  @Test public void testToImmutableTable_covariance() {
    ImmutableTable<Object, Object, Iterable<?>> table =
        BiStream.of("ab", 1, "bc", 2)
            .collect(groupingBy((s, i) -> s.charAt(0), groupingBy(s -> s.charAt(1), toList())))
            .collect(toImmutableTable());
    assertThat(table.rowMap())
        .containsExactly('a', ImmutableMap.of('b', asList(1)), 'b', ImmutableMap.of('c', asList(2)))
        .inOrder();
  }

  @Test public void testToImmutableTable_rowKeysDontHaveToBePreGrouped() {
    ImmutableTable<?, ?, ?> table =
        BiStream.of(
                "r1", BiStream.of("c1", 11),
                "r1", BiStream.of("c2", 12))
            .collect(toImmutableTable());
    assertThat(table.rowMap()).containsExactly("r1", ImmutableMap.of("c1", 11, "c2", 12)).inOrder();
  }

  @Test public void testToImmutableMap_fromPairs() {
    String input = "k1=v1,k2=v2";
    ImmutableMap<String, String> kvs =
        first(',').repeatedly().split(input).collect(toImmutableMap(s -> first('=').split(s).orElseThrow()));
    assertThat(kvs).containsExactly("k1", "v1", "k2", "v2").inOrder();
  }

  @Test public void testToImmutableListMultimap_fromPairs() {
    String input = "k1=v1,k2=v2,k2=v2";
    ImmutableListMultimap<String, String> kvs =
        first(',').repeatedly().split(input)
            .collect(toImmutableListMultimap(s -> first('=').split(s).orElseThrow()));
    assertThat(kvs).containsExactly("k1", "v1", "k2", "v2", "k2", "v2").inOrder();
  }

  @Test public void testToImmutableSetMultimap_fromPairs() {
    String input = "k1=v1,k2=v2,k2=v3";
    ImmutableSetMultimap<String, String> kvs =
        first(',').repeatedly().split(input)
            .collect(toImmutableSetMultimap(s -> first('=').split(s).orElseThrow()));
    assertThat(kvs).containsExactly("k1", "v1", "k2", "v2", "k2", "v3").inOrder();
  }

  @Test public void testToImmutableBiMap_fromPairs() {
    String input = "k1=v1,k2=v2";
    ImmutableBiMap<String, String> kvs =
        first(',').repeatedly().split(input)
            .collect(toImmutableBiMap(s -> first('=').split(s).orElseThrow()));
    assertThat(kvs).containsExactly("k1", "v1", "k2", "v2").inOrder();
  }

  @Test public void testIndexingBy() {
    assertThat(Stream.of(1, 2).collect(indexingBy(Object::toString)))
        .containsExactly("1", 1, "2", 2)
        .inOrder();
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(GuavaCollectors.class);
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

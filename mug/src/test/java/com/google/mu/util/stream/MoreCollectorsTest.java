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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.allMax;
import static com.google.mu.util.stream.MoreCollectors.allMin;
import static com.google.mu.util.stream.MoreCollectors.collectingAndThen;
import static com.google.mu.util.stream.MoreCollectors.combining;
import static com.google.mu.util.stream.MoreCollectors.flatMapping;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;
import static com.google.mu.util.stream.MoreCollectors.mapping;
import static com.google.mu.util.stream.MoreCollectors.minMax;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static com.google.mu.util.stream.MoreCollectors.onlyElements;
import static com.google.mu.util.stream.MoreCollectors.partitioningBy;
import static com.google.mu.util.stream.MoreCollectors.switching;
import static com.google.mu.util.stream.MoreCollectors.toListAndThen;
import static com.google.mu.util.stream.MoreCollectors.toMap;
import static java.util.Comparator.naturalOrder;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.summingInt;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.BiOptional;

@RunWith(JUnit4.class)
public class MoreCollectorsTest {
  @Test public void mappingFromPairs() {
    String input = "k1=v1,k2=v2";
    Map<String, String> kvs = first(',').repeatedly().split(input)
        .collect(mapping(s -> first('=').split(s).orElseThrow(), Collectors::toMap));
    assertThat(kvs).containsExactly("k1", "v1", "k2", "v2");
  }

  @Test public void mergingValues_emptyMaps() {
    ImmutableList<Translation> translations =
        ImmutableList.of(new Translation(ImmutableMap.of()), new Translation(ImmutableMap.of()));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, Joiner.on(',')::join)))
        .collect(ImmutableMap::toImmutableMap);
    assertThat(merged).isEmpty();
  }

  @Test public void mergingValues_uniqueKeys() {
    ImmutableList<Translation> translations = ImmutableList.of(
        new Translation(ImmutableMap.of(1, "one")), new Translation(ImmutableMap.of(2, "two")));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, Joiner.on(',')::join)))
        .collect(ImmutableMap::toImmutableMap);
    assertThat(merged)
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void mergingValues_duplicateValues() {
    ImmutableList<Translation> translations = ImmutableList.of(
        new Translation(ImmutableMap.of(1, "one")),
        new Translation(ImmutableMap.of(2, "two", 1, "1")));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, Joiner.on(',')::join)))
        .collect(ImmutableMap::toImmutableMap);
    assertThat(merged)
        .containsExactly(1, "one,1", 2, "two")
        .inOrder();
  }

  @Test public void flatteningToMap_emptyMaps() {
    ImmutableList<Translation> translations =
        ImmutableList.of(new Translation(ImmutableMap.of()), new Translation(ImmutableMap.of()));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatteningMaps(toMap()));
    assertThat(merged).isEmpty();
  }

  @Test public void flatteningToMap_unique() {
    ImmutableList<Translation> translations = ImmutableList.of(
        new Translation(ImmutableMap.of(1, "one")), new Translation(ImmutableMap.of(2, "two")));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatteningMaps(toMap()));
    assertThat(merged)
        .containsExactly(1, "one", 2, "two")
        .inOrder();
  }

  @Test public void flatteningToMap_withDuplicates() {
    ImmutableList<Translation> translations = ImmutableList.of(
        new Translation(ImmutableMap.of(1, "one")),
        new Translation(ImmutableMap.of(2, "two", 1, "1")));
    assertThrows(
        IllegalArgumentException.class,
        () -> translations.stream()
            .map(Translation::dictionary)
            .collect(flatteningMaps(toMap())));
  }

  @Test public void testToMap_withMapSupplier() {
    LinkedHashMap<String, Integer> map =
        Stream.of(1, 2).collect(toMap(Object::toString, i -> i * 10, LinkedHashMap::new));
    assertThat(map).containsExactly("1", 10, "2", 20).inOrder();
  }

  @Test public void toListAndThen_reversed() {
    assertThat(Stream.of(1, 2, 3).collect(toListAndThen(Collections::reverse)))
        .containsExactly(3, 2, 1)
        .inOrder();
  }

  @Test public void toListAndThen_nullRejected() {
    assertThrows(
        NullPointerException.class,
        () -> Stream.of(1, null).collect(toListAndThen(Collections::reverse)));
  }

  @Test public void toListAndThen_immutable() {
    List<Integer> list = Stream.of(1, 2).collect(toListAndThen(Collections::reverse));
    assertThrows(UnsupportedOperationException.class, list::clear);
  }

  @Test public void testCollectingAndThen_fromPair() {
    String result =
        Stream.of(1, 2, 3, 4, 5)
            .collect(
                collectingAndThen(
                    partitioningBy(
                        n -> n % 2 == 1, toImmutableList(), summingInt(Integer::intValue)),
                    (odd, even) -> "odd:" + odd + "; sum of even:" + even));
    assertThat(result).isEqualTo("odd:[1, 3, 5]; sum of even:6");
  }

  @Test
  public void testCombining_two() {
    assertThat(Stream.of(1, 10).collect(combining(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(combining((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2, 3]> has 2 elements.");
  }

  @Test
  public void testCombining_three() {
    assertThat(Stream.of(1, 3, 5).collect(combining((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(combining((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2]> has 3 elements.");
  }

  @Test
  public void testCombining_four() {
    assertThat(Stream.of(1, 3, 5, 7).collect(combining((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(combining((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2]> has 4 elements.");
  }

  @Test
  public void testCombining_five() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(combining((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(combining((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2]> has 5 elements.");
  }

  @Test
  public void testCombining_six() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(combining((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(combining((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2]> has 6 elements.");
  }

  @Test
  public void testCombining_seven() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9, 11, 13)
                .collect(combining((a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
                .intValue())
        .isEqualTo(49);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> Stream.of(1, 2).collect(combining((a, b, c, d, e, f, g) -> "ok")));
    assertThat(thrown.getMessage()).isEqualTo("Not true that input <[1, 2]> has 7 elements.");
  }

  @Test
  public void testCombining_eight() {
    assertThat(
            Stream.of(1, 3, 5, 7, 9, 11, 13, 15)
                .collect(combining((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
                .intValue())
        .isEqualTo(64);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> Stream.of(1, 2).collect(combining((a, b, c, d, e, f, g, h) -> "ok")));
    assertThat(thrown.getMessage()).isEqualTo("Not true that input <[1, 2]> has 8 elements.");
  }

  @Test public void testCombining_parallel_success() {
    String result =
        Stream.of(1, 3, 5, 7, 9, 11)
            .parallel()
            .map(Object::toString)
            .collect(combining((a, b, c, d, e, f) -> a + b + c + d + e + f));
    assertThat(result).isEqualTo("1357911");
  }

  @Test public void testCombining_parallel_failure() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8).parallel().collect(combining((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input of size = 8 <[1, 2, 3, 4, 5, 6, 7, ...]> has 6 elements.");
  }

  @Test public void testLongListInErrorMessage() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(combining((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input of size = 10 <[1, 2, 3, ...]> has 2 elements.");
  }

  @Test public void testSwitching_singleCaseApplies() {
    int result = Stream.of(1).collect(switching(onlyElement(i -> i * 10)));
    assertThat(result).isEqualTo(10);
  }

  @Test public void testSwitching_singleCaseNotApply() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(switching(onlyElement(a -> "ok"))));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input <[1, 2]> has 1 elements.");
  }

  @Test public void testSwitching_twoCases_firstCaseApplies() {
    String result =
        Stream.of(1).collect(switching(onlyElement(i -> "one"), onlyElements((a, b) -> "two")));
    assertThat(result).isEqualTo("one");
  }

  @Test public void testSwitching_twoCases_secondCaseApplies() {
    String result =
        Stream.of(1, 2).collect(switching(onlyElement(i -> "one"), onlyElements((a, b) -> "two")));
    assertThat(result).isEqualTo("two");
  }

  @Test public void testSwitching_twoCases_neitherApplies() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4).collect(switching(onlyElement(a -> "one"), onlyElements((a, b) -> "two"))));
    assertThat(thrown.getMessage())
        .isEqualTo("Unexpected input elements of size = 4 <[1, 2, 3, ...]>.");
  }

  @Test public void testAllMax_empty() {
    assertThat(Stream.<Integer>empty().collect(allMax(naturalOrder(), toImmutableList())))
        .isEmpty();
  }

  @Test public void testAllMax_toOnlyElement() {
    assertThat(Stream.of(1, 1, 1, 1, 1, 1, 2).collect(allMax(naturalOrder(), onlyElement())))
        .isEqualTo(2);
  }

  @Test public void testAllMax_multiple() {
    assertThat(Stream.of(1, 1, 2, 1, 2).collect(allMax(naturalOrder(), toImmutableList())))
        .containsExactly(2, 2);
  }

  @Test public void testAllMin_empty() {
    assertThat(Stream.<String>empty().collect(MoreCollectors.allMin(naturalOrder(), toImmutableSet())))
        .isEmpty();
  }

  @Test public void testAllMin_toOnlyElement() {
    assertThat(Stream.of(2, 2, 2, 2, 2, 2, 1).collect(allMin(naturalOrder(), onlyElement())))
        .isEqualTo(1);
  }

  @Test public void testAllMin_multiple() {
    assertThat(Stream.of(1, 1, 2, 1, 2).collect(allMin(naturalOrder(), toImmutableList())))
        .containsExactly(1, 1, 1);
  }

  @Test public void testPartitioningBy_sameDownstreamCollector() {
    String result =
        Stream.of(1, 2, 3, 4, 5)
            .collect(partitioningBy(n -> n % 2 == 1, toImmutableList()))
            .andThen((odd, even) -> "odd:" + odd + "; even:" + even);
    assertThat(result).isEqualTo("odd:[1, 3, 5]; even:[2, 4]");
  }

  @Test public void testPartitioningBy_differentDownstreamCollectors() {
    String result =
        Stream.of(1, 2, 3, 4, 5)
            .collect(
                partitioningBy(n -> n % 2 == 1, toImmutableList(), summingInt(Integer::intValue)))
            .andThen((odd, even) -> "odd:" + odd + "; sum of even:" + even);
    assertThat(result).isEqualTo("odd:[1, 3, 5]; sum of even:6");
  }

  @Test public void testMinMax_empty() {
    assertThat(Stream.<String>empty().collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void testMinMax_singleElement() {
    assertThat(Stream.of("foo").collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.of("foo", "foo"));
  }

  @Test public void testMinMax_twoUnequalElements() {
    assertThat(Stream.of("foo", "bar").collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.of("bar", "foo"));
  }

  @Test public void testMinMax_twoEqualElements() {
    assertThat(Stream.of("foo", "foo").collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.of("foo", "foo"));
  }

  @Test public void testMinMax_threeEqualElements() {
    assertThat(Stream.of("foo", "foo", "foo").collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.of("foo", "foo"));
  }

  @Test public void testMinMax_threeUnequalElements() {
    assertThat(Stream.of("foo", "bar", "zoo").collect(minMax(naturalOrder())))
        .isEqualTo(BiOptional.of("bar", "zoo"));
  }

  @Test public void testMinMax_withNullElements() {
    BiOptional<String, String> minMax =
        Stream.of("foo", null, "zoo").collect(minMax(Comparator.nullsFirst(naturalOrder())));
    assertThat(minMax.map((a, b) -> a)).isEmpty();
    assertThat(minMax.map((a, b) -> b)).hasValue("zoo");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester()
        .setDefault(FixedSizeCollector.class, MoreCollectors.onlyElement(identity()))
        .testAllPublicStaticMethods(MoreCollectors.class);
  }

  private static class Translation {
    private final ImmutableMap<Integer, String> dictionary;

    Translation(ImmutableMap<Integer, String> dictionary) {
      this.dictionary = dictionary;
    }

    ImmutableMap<Integer, String> dictionary() {
      return dictionary;
    }
  }
}

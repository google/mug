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
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatMapping;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;
import static com.google.mu.util.stream.MoreCollectors.mapping;
import static com.google.mu.util.stream.MoreCollectors.onlyElements;
import static com.google.mu.util.stream.MoreCollectors.toListAndThen;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
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
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, (a, b) -> a + "," + b)))
        .collect(ImmutableMap::toImmutableMap);
    assertThat(merged).isEmpty();
  }

  @Test public void mergingValues_uniqueKeys() {
    ImmutableList<Translation> translations = ImmutableList.of(
        new Translation(ImmutableMap.of(1, "one")), new Translation(ImmutableMap.of(2, "two")));
    Map<Integer, String> merged = translations.stream()
        .map(Translation::dictionary)
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, (a, b) -> a + "," + b)))
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
        .collect(flatMapping(BiStream::from, groupingBy((Integer k) -> k, (a, b) -> a + "," + b)))
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
        IllegalStateException.class,
        () -> translations.stream()
            .map(Translation::dictionary)
            .collect(flatteningMaps(toMap())));
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

  @Test public void testOnlyElements_Two() {
    assertThat(Stream.of(1, 10).collect(onlyElements(Integer::sum)).intValue()).isEqualTo(11);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3).collect(onlyElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input ([1, 2, 3]) has 2 elements.");
  }

  @Test public void testOnlyElements_Three() {
    assertThat(Stream.of(1, 3, 5).collect(onlyElements((a, b, c) -> a + b + c)).intValue()).isEqualTo(9);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input ([1, 2]) has 3 elements.");
  }

  @Test public void testOnlyElements_Four() {
    assertThat(Stream.of(1, 3, 5, 7).collect(onlyElements((a, b, c, d) -> a + b + c + d)).intValue())
        .isEqualTo(16);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input ([1, 2]) has 4 elements.");
  }

  @Test public void testOnlyElements_Five() {
    assertThat(Stream.of(1, 3, 5, 7, 9).collect(onlyElements((a, b, c, d, e) -> a + b + c + d + e)).intValue())
        .isEqualTo(25);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d, e) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input ([1, 2]) has 5 elements.");
  }

  @Test public void testOnlyElements_Six() {
    assertThat(Stream.of(1, 3, 5, 7, 9, 11).collect(onlyElements((a, b, c, d, e, f) -> a + b + c + d + e + f)).intValue())
        .isEqualTo(36);
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2).collect(onlyElements((a, b, c, d, e, f) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input ([1, 2]) has 6 elements.");
  }

  @Test public void testLongListInErrorMessage() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).collect(onlyElements((a, b) -> "ok")));
    assertThat(thrown.getMessage())
        .isEqualTo("Not true that input of size = 10 ([1, 2, 3, ...]) has 2 elements.");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(MoreCollectors.class);
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

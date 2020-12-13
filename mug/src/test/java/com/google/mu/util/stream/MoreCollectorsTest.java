package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.MoreCollectors.flatMapping;
import static com.google.mu.util.stream.MoreCollectors.flatteningMaps;
import static com.google.mu.util.stream.MoreCollectors.mapping;
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

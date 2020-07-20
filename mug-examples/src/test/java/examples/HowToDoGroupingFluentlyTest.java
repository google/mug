package examples;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.BiStream.grouping;
import static com.google.mu.util.stream.BiStream.groupingBy;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;

/** Some examples to show fluent grouping using {@link BiStream}. */
@RunWith(JUnit4.class)
public class HowToDoGroupingFluentlyTest {

  @Test public void how_to_fluently_group_elements_into_guava_collections() {
    Map<Integer, ImmutableSet<Integer>> byLeastSignificantDigit = Stream.of(1, 2, 11, 32)
        .collect(groupingBy(n -> n % 10, toImmutableSet()))
        .collect(new BiCollector<Integer, ImmutableSet<Integer>, ImmutableMap<Integer, ImmutableSet<Integer>>>() {
          @Override
          public <E> Collector<E, ?, ImmutableMap<Integer, ImmutableSet<Integer>>> splitting(Function<E, Integer> toKey, Function<E, ImmutableSet<Integer>> toValue) {
            return ImmutableMap.toImmutableMap(toKey,toValue);
          }
        });
    assertThat(byLeastSignificantDigit)
        .containsExactly(1, ImmutableSet.of(1, 11), 2, ImmutableSet.of(2, 32));
  }

  @Test public void how_to_fluently_group_elements_then_filter() {
    Map<Integer, List<Integer>> groupsWithAtLeastTwo = Stream.of(1, 2, 11)
        .collect(groupingBy(n -> n % 10))
        .filterValues(values -> values.size() > 1)
        .toMap();
    assertThat(groupsWithAtLeastTwo).containsExactly(1, asList(1, 11));
  }

  @Test public void how_to_concisely_group_map_entries() {
    Stream<Map<Integer, String>> numbers = Stream.of(
        ImmutableMap.of(1, "one", 2, "two"),
        ImmutableMap.of(1, "uno", 2, "dos"));
    Map<Integer, List<String>> numberTranslations = numbers
        .collect(grouping(BiStream::from, toList()))
        .toMap();
    assertThat(numberTranslations)
        .containsExactly(1, asList("one", "uno"), 2, asList("two", "dos"));
  }
}

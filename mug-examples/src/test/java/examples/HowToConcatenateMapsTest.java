package examples;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.BiStream.concat;
import static com.google.mu.util.stream.BiStream.concatenating;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;

/** Some examples to show how to easily concatenate {@code Map}s and {@code Multimap}s. */
@RunWith(JUnit4.class)
public class HowToConcatenateMapsTest {

  @Test public void how_to_concatenate_two_or_more_maps_to_map() {
    ImmutableMap<String, Integer> english = ImmutableMap.of("one", 1, "two", 2);
    ImmutableMap<String, Integer> spanish = ImmutableMap.of("uno", 1, "dos", 2);
    Map<String, Integer> combined = concat(english, spanish).toMap();
    assertThat(combined).containsExactly("one", 1, "uno", 1, "two", 2, "dos", 2);
  }

  @Test public void how_to_concatenate_two_or_more_maps_to_multimap() {
    ImmutableMap<Integer, String> english = ImmutableMap.of(1, "one", 2, "two");
    ImmutableMap<Integer, String> spanish = ImmutableMap.of(1, "uno", 2, "dos");
    ImmutableSetMultimap<Integer, String> combined = concat(english, spanish)
        .collect(new BiCollector<Integer, String, ImmutableSetMultimap<Integer, String>>() {
          @Override
          public <E> Collector<E, ?, ImmutableSetMultimap<Integer, String>> collectorOf(Function<E, Integer> toKey, Function<E, String> toValue) {
            return ImmutableSetMultimap.toImmutableSetMultimap(toKey,toValue);
          }
        });
    assertThat(combined).containsExactly(1, "one", 1, "uno", 2, "two", 2, "dos");
  }

  @Test public void how_to_concatenate_stream_of_maps() {
    Stream<Map<String, Integer>> stream = Stream.of(
        ImmutableMap.of("one", 1, "two", 2), ImmutableMap.of("uno", 1, "dos", 2));
    Map<String, Integer> combined = concat(stream.map(BiStream::from)).toMap();
    assertThat(combined).containsExactly("one", 1, "uno", 1, "two", 2, "dos", 2);
  }

  @Test public void how_to_concatenate_stream_of_maps_in_a_stream_chain() {
    Stream<Map<String, Integer>> stream = Stream.of(
        ImmutableMap.of("one", 1, "two", 2), ImmutableMap.of("uno", 1, "dos", 2));
    Map<String, Integer> combined = stream
        .collect(concatenating(BiStream::from))
        .toMap();
    assertThat(combined).containsExactly("one", 1, "uno", 1, "two", 2, "dos", 2);
  }
}

package examples;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.mu.util.stream.BiStream;

/** Some examples to show how {@link BiStream#collect} can be used to collect to custom types. */
@RunWith(JUnit4.class)
public class HowToCollectToCustomTypesTest {

  @Test public void how_to_collect_to_immutableMap() {
    BiStream<String, Integer> biStream = BiStream.of("one", 1, "two", 2);
    ImmutableMap<String, Integer> map = biStream.collect(ImmutableMap::toImmutableMap);
    assertThat(map).containsExactly("one", 1, "two", 2);
  }

  @Test public void how_to_collect_to_immutableListMultimap() {
    BiStream<String, Integer> biStream = BiStream.of("one", 1, "two", 2);
    ImmutableListMultimap<String, Integer> map =
        biStream.collect(ImmutableListMultimap::toImmutableListMultimap);
    assertThat(map).containsExactly("one", 1, "two", 2);
  }

  @Test public void how_to_collect_to_immutableRangeMap() {
    BiStream<Range<Integer>, String> biStream =
        BiStream.of(Range.closed(10, 19), "ten", Range.closed(20, 29), "twenty");
    ImmutableRangeMap<Integer, String> map =
        biStream.collect(ImmutableRangeMap::toImmutableRangeMap);
    assertThat(map.get(12)).isEqualTo("ten");
  }
}

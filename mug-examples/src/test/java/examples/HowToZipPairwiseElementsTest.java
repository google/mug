package examples;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.Arrays.asList;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.util.stream.BiStream;

/** Some examples to show use cases of {@link BiStream#zip}. */
@RunWith(JUnit4.class)
public class HowToZipPairwiseElementsTest {
  @Test public void how_to_handle_requests_and_corresponding_response_objects() {
    List<String> requests = asList("first request", "second request");
    List<String> responses = asList("first response", "second response");
    assertThat(BiStream.zip(requests, responses).toMap())
        .containsExactly("first request", "first response", "second request", "second response");
  }

  @Test public void how_to_stream_with_indices() {
    Stream<String> stream = Stream.of("foo", "bar");
    List<String> indexed = BiStream.zip(indexesFrom(0), stream)
        .mapToObj((i, s) -> i + ": " + s)
        .collect(toImmutableList());
    assertThat(indexed).containsExactly("0: foo", "1: bar");
  }
}

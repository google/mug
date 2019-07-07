package examples;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.BiStream.crossJoining;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Some examples to show type safe cartesian product of two streams. */
@RunWith(JUnit4.class)
public class HowToCreateTypeSafeCartesianProductTest {

  @Test public void how_to_make_cartesian_product() {
    Stream<String> students = Stream.of("Chloe", "Amy");
    Stream<String> parents = Stream.of("mom", "dad");
    List<String> joined = students
        .collect(crossJoining(parents))
        .mapToObj((student, parent) -> student + "'s " + parent)
        .collect(toList());
    assertThat(joined).containsExactly("Chloe's dad", "Chloe's mom", "Amy's dad", "Amy's mom");
  }

  @Test public void how_to_use_cartesian_product_with_infinite_stream() {
    Stream<String> testSpecs = Stream.of("testMap", "testReduce");
    IntStream inputSizes = IntStream.iterate(1, s -> s * 2);
    Stream<String> testCases = testSpecs  // testSpecs must no be infinite because collect() is eager.
        .collect(crossJoining(inputSizes.boxed()))
        .mapToObj((spec, inputSize) -> spec + " using input size " + inputSize);

    // While testCases is an infinite stream, you can later pick an input size.
    assertThat(testCases.limit(4).collect(toList()))
        .containsExactly(
            "testMap using input size 1",
            "testMap using input size 2",
            "testReduce using input size 1",
            "testReduce using input size 2");
  }
}

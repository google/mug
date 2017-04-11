package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Spliterator;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.ClassSanityTester;

@RunWith(JUnit4.class)
public class MoreStreamsTest {

  @Test public void diceSpliteratorIsNonNull() {
    Spliterator<?> spliterator = asList(1).spliterator();
    assertThat(spliterator.hasCharacteristics(Spliterator.NONNULL)).isFalse();
    assertThat(MoreStreams.dice(spliterator, 2).hasCharacteristics(Spliterator.NONNULL)).isTrue();
  }

  @Test public void diceSpliteratorIsNotSized() {
    Spliterator<?> spliterator = asList(1).spliterator();
    assertThat(spliterator.hasCharacteristics(Spliterator.SIZED)).isTrue();
    assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(1);
    assertThat(MoreStreams.dice(spliterator, 2).hasCharacteristics(Spliterator.SIZED)).isFalse();
    assertThat(MoreStreams.dice(spliterator, 2).getExactSizeIfKnown()).isEqualTo(-1);
    assertThat(MoreStreams.dice(spliterator, 2).estimateSize()).isEqualTo(1);
    assertThat(MoreStreams.dice(asList(1, 2, 3).spliterator(), 2).estimateSize()).isEqualTo(2);
    assertThat(MoreStreams.dice(asList(1, 2, 3, 4, 5, 6).spliterator(), 2).estimateSize())
        .isEqualTo(3);
  }

  @Test public void diceSpliteratorIsNotSubsized() {
    Spliterator<?> spliterator = asList(1).spliterator();
    assertThat(spliterator.hasCharacteristics(Spliterator.SUBSIZED)).isTrue();
    assertThat(MoreStreams.dice(spliterator, 2).hasCharacteristics(Spliterator.SUBSIZED))
        .isFalse();
  }

  @Test public void diceSpliteratorIsNotSorted() {
    Spliterator<?> spliterator = new TreeSet<>(asList(1)).spliterator();
    assertThat(spliterator.hasCharacteristics(Spliterator.SORTED)).isTrue();
    assertThat(spliterator.getComparator()).isNull();
    assertThat(MoreStreams.dice(spliterator, 2).hasCharacteristics(Spliterator.SORTED)).isFalse();
    assertThrows(
        IllegalStateException.class, () -> MoreStreams.dice(spliterator, 2).getComparator());
  }

  @Test public void nullElementsAreOk() {
    assertThat(MoreStreams.dice(asList(null, null).stream(), 2).collect(toList()))
        .containsExactly(asList(null, null));
  }

  @Test public void maxSizeIsLargerThanDataSize() {
    assertThat(MoreStreams.dice(asList(1, 2).stream(), Integer.MAX_VALUE).collect(toList()))
        .containsExactly(asList(1, 2));
  }

  @Test public void largeMaxSizeWithUnknownSize() {
    assumeTrue(Stream.generate(() -> 1).limit(3).spliterator().getExactSizeIfKnown() == -1);
    assertThat(MoreStreams.dice(Stream.generate(() -> 1).limit(3), Integer.MAX_VALUE).
            limit(3).collect(toList()))
        .containsExactly(asList(1, 1, 1));
  }

  @Test public void invalidMaxSize() {
    assertThrows(IllegalArgumentException.class, () -> MoreStreams.dice(asList(1).stream(), -1));
    assertThrows(IllegalArgumentException.class, () -> MoreStreams.dice(asList(1).stream(), 0));
  }

  @Test public void testNulls() throws Exception {
    new ClassSanityTester().testNulls(MoreStreams.class);
    new ClassSanityTester().forAllPublicStaticMethods(MoreStreams.class).testNulls();
  }
}

package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StreamSideEffectsTest {
  @Test public void withSideEffect_orderingGuaranteed() {
    Set<Object> set = new HashSet<>();
    Stream<Integer> source = Stream.iterate(1, i -> i + 1).limit(20);
    MoreStreams.withSideEffect(Stream.of(source).flatMap(s -> s), set::add)
        .limit(10)
        .forEach(i -> {});
    assertThat(set).hasSize(10);
  }
}

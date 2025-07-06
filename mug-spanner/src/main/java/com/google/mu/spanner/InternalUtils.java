package com.google.mu.spanner;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiCollectors;

final class InternalUtils {
  @FormatMethod static void checkArgument(
      boolean good, @FormatString String message, Object... args) {
    if (!good) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  @FormatMethod static void checkState(
      boolean good, @FormatString String message, Object... args) {
    if (!good) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  static <Q, R> Collector<Q, ?, R> skippingEmpty(Collector<Q, ?, R> downstream) {
    return filtering(q -> !q.toString().isEmpty(), downstream);
  }

  static <K extends Comparable<K>, V> BiCollector<K, V, NavigableMap<K, V>> toImmutableNavigableMap() {
    return BiCollectors.collectingAndThen(
        BiCollectors.toMap(TreeMap::new), Collections::unmodifiableNavigableMap);
  }

  private static <T, A, R> Collector<T, A, R> filtering(
      Predicate<? super T> filter, Collector<? super T, A, R> collector) {
    BiConsumer<A, ? super T> accumulator = collector.accumulator();
    return Collector.of(
        collector.supplier(),
        (a, input) -> {if (filter.test(input)) {accumulator.accept(a, input);}},
        collector.combiner(),
        collector.finisher(),
        collector.characteristics().toArray(new Characteristics[0]));
  }
}

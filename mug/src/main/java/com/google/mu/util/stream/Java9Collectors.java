package com.google.mu.util.stream;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;
import java.util.stream.Stream;

// TODO: switch to Java 9 Collectors.flatMapping() when we can.
final class Java9Collectors {
  static <T, E, A, R> Collector<T, A, R> flatMapping(
      Function<? super T, ? extends Stream<? extends E>> mapper, Collector<E, A, R> collector) {
    BiConsumer<A, E> accumulator = collector.accumulator();
    return Collector.of(
        collector.supplier(),
        (a, input) -> mapper.apply(input).forEachOrdered(e -> accumulator.accept(a, e)),
        collector.combiner(),
        collector.finisher(),
        collector.characteristics().toArray(new Characteristics[0]));
  }

  static <T, A, R> Collector<T, A, R> filtering(
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

package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class MutableReduction<K, V, A, R> {
  private final Supplier<? extends A> factory;
  private final BiAccumulator<? super A, ? super K, ? super V> accumulator;
  private final Function<? super A, ? extends R> finisher;

  MutableReduction(
      Supplier<? extends A> factory,
      BiAccumulator<? super A, ? super K, ? super V> accumulator,
      Function<? super A, ? extends R> finisher) {
    this.factory = requireNonNull(factory);
    this.accumulator = requireNonNull(accumulator);
    this.finisher = requireNonNull(finisher);
  }

  static <K, V, A, R> MutableReduction<K, V, A, R> fromEntries(
      Collector<Map.Entry<K, V>, A, R> collector) {
    BiConsumer<A, Map.Entry<K, V>> entryAccumulator = collector.accumulator();
    return new MutableReduction<>(
        collector.supplier(),
        (a, k, v) -> entryAccumulator.accept(a, new AbstractMap.SimpleImmutableEntry<>(k, v)),
        collector.finisher());
  }

  A newContainer() {
    return factory.get();
  }

  void accumulate(A container, K k, V v) {
    accumulator.accumulate(container, k, v);
  }

  R finish(A container) {
    return finisher.apply(container);
  }
}

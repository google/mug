package com.google.mu.util.stream;

import java.util.function.Function;
import java.util.stream.Collector;

/**
 * A collector passed to {@link BiStream#collect}. For example:  <pre>{@code
 *   Map<String, Integer> map = BiStream.of("a", 1).collect(Collectors::toMap);
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @param <R> the result type
 * @since 1.2
 */
@FunctionalInterface
public interface BiCollector<K, V, R> {
  /**
   * Adapts to a {@code Collector<T, ? R>}, by mapping the inputs through {@code keyMapper}
   * and {@code valueMapper} and subsequently collecting the key-value pairs with this
   * {@code BiCollector}.
   */
  <T> Collector<T, ?, R> asCollector(
      Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper);
}

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
 */
@FunctionalInterface
public interface BiCollector<K, V, R> {
  /**
   * Returns a collector that takes any input of type {@code T},
   * which when mapped through {@code keyFunction} and {@code valueFunction} to key-value pairs,
   * will collect into an object of type {@code R}.
   */
  <T> Collector<T, ?, R> collector(
      Function<? super T, ? extends K> keyFunction,
      Function<? super T, ? extends V> valueFunction);
}

package com.google.mu.util.stream;

import java.util.List;
import java.util.stream.Collector;

/**
 * A collector that can be passed as one of the multiple conditional cases to
 * {@link MoreCollectors#switching}.
 *
 * @since 5.4
 */
public abstract class ConditionalCollector<T, A, R> implements Collector<T, A, R> {
  boolean appliesTo(List<T> list) {
    return list.size() == arity();
  }

  abstract int arity();
  abstract R reduce(List<? extends T> list);
}

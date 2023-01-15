package com.google.mu.util.stream;

import java.util.List;
import java.util.stream.Collector;

/**
 * A collector that expects a fixed number of input elements.
 *
 * <p>In addition to being used as regular Collector, can also be passed as one of the
 * multiple conditional cases to {@link MoreCollectors#switching}.
 *
 * @since 5.5
 */
public abstract class FixedSizeCollector<T, A, R> implements Collector<T, A, R> {
  /**
   * Use this collector to collect elements from {@code list} if it's of the expected size,
   * or else throws {@link IllegalArgumentException}.
   */
  public abstract R collect(List<? extends T> list);

  final boolean appliesTo(List<? extends T> list) {
    return list.size() == arity();
  }

  abstract int arity();
  abstract R reduce(List<? extends T> list);
}

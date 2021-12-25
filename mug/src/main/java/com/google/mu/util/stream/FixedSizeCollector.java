package com.google.mu.util.stream;

import java.util.List;

/**
 * A collector that expects a fixed number of input elements.
 *
 * <p>In addition to being used as regular Collector, can also be passed as one of the
 * multiple conditional cases to {@link MoreCollectors#switching}.
 *
 * @since 5.5
 */
public abstract class FixedSizeCollector<T, A, R> extends Unpacker<T, A, R> {
  @Override boolean appliesTo(List<? extends T> list) {
    return list.size() == arity();
  }

  abstract int arity();
}

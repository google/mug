package com.google.mu.util.stream;

import static java.lang.Math.max;

import java.util.List;
import java.util.stream.Collector;

/**
 * A collector that uses a function to unpack incoming elements.
 *
 * @since 5.8
 */
public abstract class Unpacker<T, A, R> implements Collector<T, A, R> {
  abstract boolean appliesTo(List<? extends T> list);
  abstract R reduce(List<? extends T> list);
  abstract int arity();


  /** Unpacks {@code list} using the first from {@code cases} that applies. */
  @SafeVarargs
  public static <T, R> R unpack(List<? extends T> list, Unpacker<? super T, ?, ? extends R>... cases) {
    int elementsToShow = 1;
    for (Unpacker<? super T, ?, ? extends R> c : cases) {
      if (c.appliesTo(list)) {
        return c.reduce(list);
      }
      elementsToShow = max(elementsToShow, c.arity() + 1);
    }
    throw new IllegalArgumentException(
        "Unexpected input elements " + ShortListCollector.showShortList(list, elementsToShow) + '.');
  }
}

package com.google.mu.util.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

abstract class MinSizeCollector<T, R>  extends Unpacker<T, List<T>, R> {
  @Override public final BiConsumer<List<T>, T> accumulator() {
    return List::add;
  }

  @Override public final BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      return l1;
    };
  }

  @Override public final Function<List<T>, R> finisher() {
    return l -> {
      if (appliesTo(l)) return reduce(l);
      throw new IllegalArgumentException(
          "Not true that input " + l + " has " + this + ".");
    };
  }

  @Override public final Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  @Override public final Supplier<List<T>> supplier() {
    return ArrayList::new;
  }

  @Override public String toString() {
    return "at least " + arity() + " elements";
  }

  @Override boolean appliesTo(List<? extends T> list) {
    return list.size() >= arity();
  }

  final List<T> tail(List<? extends T> list) {
    return Collections.unmodifiableList(list.subList(arity(), list.size()));
  }
}

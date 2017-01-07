package org.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.AbstractList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Some relatively trivial re-invented wheels. */
final class Utils {
  /** Only need it because we don't have Guava Lists.transform(). */
  static <F, T> List<T> mapList(List<? extends F> list, Function<? super F, ? extends T> mapper) {
    requireNonNull(list);
    requireNonNull(mapper);
    return new AbstractList<T>() {
      @Override public int size() {
        return list.size();
      }
      @Override public T get(int index) {
        return mapper.apply(list.get(index));
      }
    };
  }

  static <T> Predicate<Object> typed(Class<T> type, Predicate<? super T> condition) {
    requireNonNull(type);
    requireNonNull(condition);
    return x -> type.isInstance(x) && condition.test(type.cast(x));
  }
}

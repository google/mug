/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util.patternmatch;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility class to perform n-ary functional pattern matching on a list or a stream of input elements.
 *
 * <p>A {@code NaryCollector} object can be used as a {@link Collector} for a stream.
 * For example:
 *
 * <pre>{@code
 *   stream.collect(exactly((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible patterns passed to the static {@link #matching matching()} method.
 * For example:
 * <pre>{@code
 *   Path path = pathComponents.stream()
 *       .filter(...)
 *       .collect(
 *           matching(
 *               exactly((parent, child) -> ...),
 *               exactly(fileName -> ...),
 *               atLeast(root -> ...),
 *               orElse(l -> ...)));
 * }</pre>
 *
 * In the above example, if you have a {@link List} instead of a stream, you can use the static
 * {@link #match match()} method instead to avoid iterating through every list elements:
 * <pre>{@code
 *   Path path = match(
 *       pathComponents,
 *       exactly((parent, child) -> ...),
 *       exactly(fileName -> ...),
 *       atLeast(root -> ...),
 *       orElse(l -> ...));
 * }</pre>
 *
 * @since 5.3
 */
public abstract class NaryCollector<T, R> implements Collector<T, List<T>, R> {
  private static final NaryCollector<Object, ?> ONLY_ELEMENT = exactly(Function.identity());
  private static final NaryCollector<Object, ?> FIRST_ELEMENT = atLeast(Function.identity());
  private static final NaryCollector<Object, Object> LAST_ELEMENT = new NaryCollector<Object, Object>() {
    @Override boolean matches(List<?> list) {
      return list.size() >= 1;
    }
    @Override Object map(List<?> list) {
      return list.get(list.size() - 1);
    }
    @Override public String toString() {
      return "at least 1 element";
    }
    @Override BoundedBuffer<Object> newBuffer() {
      return BoundedBuffer.retainingLastElementOnly();
    }
  };

  /**
   * Returns a {@code Collector} that will expand the input elements and transform them using the
   * first from {@code patterns} that matches. If no pattern matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example if a string could be in the form of {@code <resource_name>} with no qualifier,
   * or in the form of {@code <project>.<resource_name>} with project as the qualifier,
   * or in the form of {@code <project>.<location>.<resource_name>}, with both project and
   * location qualifiers, you can handle all 3 cases using:
   * <pre>{@code
   *    Substring.first('.').repeatedly().split(string)
   *        .collect(
   *            matching(
   *                exactly(resourceName -> ...),
   *                exactly((project, resourceName) -> ...),
   *                exactly(((project, location, resourceName) -> ...))));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> matching(
      NaryCollector<? super T, ? extends R>... patterns) {
    List<NaryCollector<? super T, ? extends R>> patternsCopy = copyOf(patterns);
    return collectingAndThen(toList(), list -> match(list, patternsCopy));
  }

  /**
   * Expands the input elements in {@code list} and transforms them using the
   * first from {@code patterns} that matches. If no pattern matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example, to switch among multiple possible cases:
   * <pre>{@code
   * match(
   *     list,
   *     exactly((a, b) -> ...),
   *     atLeast((a, b, c) -> ...),
   *     empty(() -> ...));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> R match(
      List<T> list, NaryCollector<? super T, ? extends R>... patterns) {
    return match(list, copyOf(patterns));
  }

  private static <T, R> R match(
      List<T> list, Iterable<? extends NaryCollector<? super T, ? extends R>> patterns) {
    requireNonNull(list);
    for (NaryCollector<? super T, ? extends R> pattern : patterns) {
      if (pattern.matches(list)) {
        return pattern.map(list);
      }
    }
    throw new IllegalArgumentException(
        showShortList(list) + " matches no pattern from " + patterns + ".");
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are zero input elements,
   * in which case, {@code supplier} is invoked whose return value is used as the pattern matching
   * result.
   */
  public static <T, R> NaryCollector<T, R> empty(Supplier<? extends R> supplier) {
    requireNonNull(supplier);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.isEmpty();
      }
      @Override R map(List<? extends T> list) {
        return supplier.get();
      }
      @Override public String toString() {
        return "empty";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(1);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly one input element.
   * The element will be the result of the matcher. For example, you can get the only element
   * from a stream using {@code stream.collect(onlyElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> NaryCollector<T, T> onlyElement() {
    return (NaryCollector<T, T>) ONLY_ELEMENT;
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly one input element,
   * which will be passed to {@code mapper} and the return value is used as the pattern matching
   * result.
   */
  public static <T, R> NaryCollector<T, R> exactly(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 1;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override public String toString() {
        return "exactly 1 element";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(2);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly two input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> exactly(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 2;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override public String toString() {
        return "exactly 2 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(3);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly three input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> exactly(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 3;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2));
      }
      @Override public String toString() {
        return "exactly 3 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(4);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly four input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> exactly(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 4;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3));
      }
      @Override public String toString() {
        return "exactly 4 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(5);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly five input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> exactly(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 5;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
      }
      @Override public String toString() {
        return "exactly 5 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(6);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly six input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> exactly(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 6;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override public String toString() {
        return "exactly 6 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(7);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly one input elements
   * that satisfies {@code condition}. Upon match, the single element is passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> when(
      Predicate<? super T> condition, Function<? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 1 && condition.test(list.get(0));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override public String toString() {
        return "exactly 1 element that satisfies " + condition;
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(2);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are exactly two input elements
   * that satisfy {@code condition}. Upon match, the two elements are passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> when(
      BiPredicate<? super T, ? super T> condition,
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() == 2 && condition.test(list.get(0), list.get(1));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override public String toString() {
        return "exactly 2 elements that satisfies " + condition;
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(3);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least one input element.
   * The first element will be the result of the matcher. For example, you can get the first
   * element from a non-empty stream using {@code stream.collect(firstElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> NaryCollector<T, T> firstElement() {
    return (NaryCollector<T, T>) FIRST_ELEMENT;
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least one input element.
   * The last element will be the result of the matcher. For example, you can get the last
   * element from a non-empty stream using {@code stream.collect(lastElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> NaryCollector<T, T> lastElement() {
    return (NaryCollector<T, T>) LAST_ELEMENT;
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least one input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 1;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override public String toString() {
        return "at least 1 element";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(1);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least two input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 2;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override public String toString() {
        return "at least 2 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(2);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least three input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 3;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2));
      }
      @Override public String toString() {
        return "at least 3 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(3);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least four input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 4;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3));
      }
      @Override public String toString() {
        return "at least 4 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(4);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least five input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 5;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
      }
      @Override public String toString() {
        return "at least 5 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(5);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches when there are at least six input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> NaryCollector<T, R> atLeast(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return list.size() >= 6;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override public String toString() {
        return "at least 6 elements";
      }
      @Override List<T> newBuffer() {
        return BoundedBuffer.retaining(6);
      }
    };
  }

  /**
   * Returns a {@code NaryCollector} that matches any input. Pass it in as the last parameter
   * of the {@link #match match()} or {@link #matching matching()} method to perform a catch-all default.
   *
   * <p>For example:
   *
   * <pre>{@code
   * match(
   *     list,
   *     exactly((a, b) -> ...),
   *     atLeast((a, b, c) -> ...),
   *     orElse(l -> ...));
   * }</pre>
   */
  public static <T, R> NaryCollector<T, R> orElse(Function<? super List<T>, ? extends R> mapper) {
    requireNonNull(mapper);
    return new NaryCollector<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        requireNonNull(list);
        return true;
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(Collections.unmodifiableList(list));
      }
      @Override public String toString() {
        return "default";
      }
      @Override List<T> newBuffer() {
        return new ArrayList<>();
      }
    };
  }

  /**
   * Returns a collector that optionally collects and wraps the non-null result of this collector
   * inside an {@link Optional} object, provided the input pattern matches.
   * If the input pattern doesn't match, it will collect to {@code Optional.empty()}.
   *
   * <p>For example, to handle the unexpected input case gracefully without throwing exception, you can:
   *
   * <pre>{@code
   *   Optional<JobId> kv = ids.stream().collect(exactly(JobId::new).orNot());
   * }</pre>
   *
   * <p>If this collector results in null, {@link NullPointerException} will be thrown.
   */
  public final Collector<T, ? ,Optional<R>> orNot() {
    return Collector.of(
        this::newBuffer,
        List::add,
        (l, r) -> {l.addAll(r); return l;},
        this::tryMatch);
  }

  abstract boolean matches(List<? extends T> list);
  abstract R map(List<? extends T> list);

  /**
   * Returns the buffer to hold temporary elements for the {@link #orNot} collector.
   *
   * <p>Because the {@code orNot()} case is expected (not necessarily an error), this
   * allows implementations to use a fixed-size buffer to avoid consuming excessive memory.
   *
   * <p>Not used for {@code this} collector because we need the full list of elements and the size
   * in the error message; plus the no-match case is "exceptional" (unlikely worth optimizing).
   */
  abstract List<T> newBuffer();

  private Optional<R> tryMatch(List<? extends T> list) {
    return matches(list) ? Optional.of(map(list)) : Optional.empty();
  }

  /** Returns the string representation of this pattern. */
  @Override public abstract String toString();

  @Override public Supplier<List<T>> supplier() {
    return ArrayList::new;
  }

  @Override public BiConsumer<List<T>, T> accumulator() {
    return List::add;
  }

  @Override public BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      l1.addAll(l2);
      return l1;
    };
  }

  @Override public Function<List<T>, R> finisher() {
    return l -> {
      if (matches(l)) {
        return map(l);
      }
      throw new IllegalArgumentException(
          "Input " + showShortList(l) + " doesn't match pattern <" + this + ">.");
    };
  }

  @Override public Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  @SafeVarargs
  private static <T> List<T> copyOf(T... values) {
    List<T> copy = new ArrayList<>(values.length);
    for (T v : values) {
      copy.add(requireNonNull(v));
    }
    return copy;
  }

  private static String showShortList(List<?> list) {
    return list.size() <= 8
        ? "(" + list + ")"
        : "of size = " + list.size() + " (["
            + list.stream().limit(8).map(Object::toString).collect(Collectors.joining(", "))
            + ", ...])";
  }

  private NaryCollector() {}
}

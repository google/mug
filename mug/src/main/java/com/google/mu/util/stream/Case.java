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
package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

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
 * <p>A {@code Case} object can be used as a stand-alone {@link Collector} for a stream. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.exactly;
 *
 * stream.collect(exactly((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible cases passed to the static {@link #match match()} method.
 * For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.*;
 *
 * Optional<Path> path = Case.match(
 *     pathComponents,
 *     exactly((parent, child) -> ...),
 *     exactly(fileName -> ...),
 *     atLeast(root -> ...));
 * }</pre>
 *
 * @since 5.3
 */
public abstract class Case<T, R> implements Collector<T, List<T>, R> {
  private static final int MAX_CARDINALITY = 8;

  /**
   * Returns a {@code Case} that matches when there are exactly two input elements
   * that satisfy {@code condition}. Upon match, the two elements are passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> Case<T, R> when(
      BiPredicate<? super T, ? super T> condition,
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new ExactSize<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return super.matches(list) && condition.test(list.get(0), list.get(1));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override public String toString() {
        return "exactly 2 elements that satisfies " + condition;
      }
      @Override int arity() {
        return 2;
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly one input elements
   * that satisfies {@code condition}. Upon match, the single element is passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> Case<T, R> when(
      Predicate<? super T> condition, Function<? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new ExactSize<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return super.matches(list) && condition.test(list.get(0));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override public String toString() {
        return "exactly 1 element that satisfies " + condition;
      }
      @Override int arity() {
        return 1;
      }
    };
  }

  /**
   * Expands the input elements in {@code list} and transforms them using the
   * first from {@code cases} that matches. If no case matches the input elements,
   * {@code Optional.empty()} is returned.
   *
   * <p>For example, to switch among multiple possible cases:
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.*;
   *
   * Optional<R> result =
   *     Case.match(
   *         list,
   *         exactly((a, b) -> ...),
   *         atLeast((a, b, c) -> ...),
   *         empty(() -> ...));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Optional<R> match(
      List<T> list, Case<? super T, ? extends R>... cases) {
    return match(list, copyOf(cases));
  }

  static <T, R> Optional<R> match(
      List<T> list, Iterable<? extends Case<? super T, ? extends R>> cases) {
    requireNonNull(list);
    for (Case<? super T, ? extends R> pattern : cases) {
      if (pattern.matches(list)) {
        return Optional.of(pattern.map(list));
      }
    }
    return Optional.empty();
  }

  abstract boolean matches(List<? extends T> list);
  abstract R map(List<? extends T> list);

  /**
   * Returns the buffer to hold temporary elements.
   *
   * <p>The returned list fails fast if the number of input elements exceeds max siae.
   */
  abstract List<T> newBuffer();

  abstract int arity();

  /** Returns the string representation of this {@code Case}. */
  @Override public abstract String toString();

  @Override public final Supplier<List<T>> supplier() {
    return this::newBuffer;
  }

  @Override public final BiConsumer<List<T>, T> accumulator() {
    return List::add;
  }

  @Override public final BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      l1.addAll(l2);
      return l1;
    };
  }

  @Override public final Function<List<T>, R> finisher() {
    return l -> {
      if (matches(l)) {
        return map(l);
      }
      throw new IllegalArgumentException(
          "Input " + showShortList(l) + " doesn't match pattern <" + this + ">.");
    };
  }

  @Override public final Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  private static String showShortList(List<?> list) {
    return list.size() <= MAX_CARDINALITY  // If small enough, just show it.
        ? "(" + list + ")"
        : "of size = " + list.size() + " (["
            + list.stream().limit(8).map(Object::toString).collect(Collectors.joining(", "))
            + ", ...])";
  }

  static abstract class ExactSize<T, R> extends Case<T, R> {
    @Override boolean matches(List<? extends T> list) {
      return list.size() == arity();
    }

    @Override public String toString() {
      return "exactly " + arity() + " elements";
    }

    @Override List<T> newBuffer() {
      return BoundedBuffer.atMost(arity() + 1);
    }
  }

  static abstract class MinSize<T, R> extends Case<T, R> {
    @Override boolean matches(List<? extends T> list) {
      return list.size() >= arity();
    }

    @Override public String toString() {
      return "at least " + arity() + " elements";
    }

    @Override List<T> newBuffer() {
      return BoundedBuffer.atMost(arity());
    }
  }

  @SafeVarargs
  private static <T> List<T> copyOf(T... values) {
    List<T> copy = new ArrayList<>(values.length);
    for (T v : values) {
      copy.add(requireNonNull(v));
    }
    return copy;
  }

  Case() {}
}

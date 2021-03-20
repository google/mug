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

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;

/**
 * Utility class to perform n-ary functional pattern matching on a list or a stream of input elements.
 *
 * <p>A {@code ListPattern} object can be used as a stand-alone {@link Collector} for a stream. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.onlyElements;
 *
 * stream.collect(onlyElements((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible patterns passed to the static {@link #findFrom findFrom()} method.
 * For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.*;
 *
 * Optional<Path> path = ListPattern.findFrom(
 *     pathComponents,
 *     onlyElements((parent, child) -> ...),
 *     onlyElements(fileName -> ...),
 *     firstElements(root -> ...));
 * }</pre>
 *
 * @since 5.3
 */
public abstract class ListPattern<T, A, R> implements Collector<T, A, R> {
  private static final int MAX_CARDINALITY = 8;
  private static final ListPattern<Object, ?, ?> FIRST_ELEMENT = firstElement(Function.identity());

  /**
   * Inspects the elements of {@code list} and transforms them using the first {@link ListPattern} object
   * in {@code patterns} that matches. If no case matches the input elements, {@code Optional.empty()}
   * is returned.
   *
   * <p>For example, to switch among multiple possible patterns:
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.*;
   *
   * Optional<R> result =
   *     ListPattern.findFrom(
   *         list,
   *         onlyElements((a, b) -> ...),
   *         firstElements((a, b, c) -> ...));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Optional<R> findFrom(
      List<T> list, ListPattern<? super T, ?, ? extends R>... patterns) {
    requireNonNull(list);
    for (ListPattern<?, ?, ?> pattern : patterns) {
      requireNonNull(pattern);
    }
    for (ListPattern<? super T, ?, ? extends R> pattern : patterns) {
      if (pattern.matches(list)) {
        return Optional.of(pattern.map(list));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns a {@code ListPattern} that attempts to find the first element from the input.
   *
   * <p>For example: <pre>{@code
   * ListPattern.findFrom(list, firstElement())
   * }</pre> is equivalent to <pre>{@code
   * list.isEmpty() ? Optional.empty() : Optional.of(list.get(0))
   * }</pre>
   */
  @SuppressWarnings("unchecked")  // This ListPattern takes any T and returns T.
  public static <T> ListPattern<T, ?, T> firstElement() {
    return (ListPattern<T, ?, T>) FIRST_ELEMENT;
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first element from the input
   * using the {@code mapper} function.
   *
   * <p>Usually you want to use {@link #firstElement()} instead to get the first element from the
   * stream or list. This method is useful when you have multiple potential patterns passed to the
   * {@link #findFrom} method.
   */
  public static <T, R> ListPattern<T, ?, R> firstElement(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override int arity() {
        return 1;
      }
      @Override public String toString() {
        return "at least 1 element";
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find the first input element,
   * but only if the element satisfies {@code condition}.
   */
  public static <T> ListPattern<T, ?, T> firstElementIf(Predicate<? super T> condition) {
    return firstElementIf(condition, Function.identity());
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first input element,
   * but only if the element satisfies {@code condition}.
   */
  public static <T, R> ListPattern<T, ?, R> firstElementIf(
      Predicate<? super T> condition, Function<? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return super.matches(list) && condition.test(list.get(0));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override int arity() {
        return 1;
      }
      @Override public String toString() {
        return "the first element and it satisfies " + condition;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first two elements
   * from the input using the {@code mapper} function.
   *
   * <p>For example, <pre>{@code
   * ListPattern.findFrom(list, firstElements(String::concat))
   * }</pre>
   * is equivalent to <pre>{@code
   * list.size() < 2
   *     ? Optional.empty()
   *     : Optional.of(list.get(0) + list.get(1))
   * }</pre>
   */
  public static <T, R> ListPattern<T, ?, R> firstElements(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override int arity() {
        return 2;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first three elements
   * from the input using the {@code mapper} function.
   *
   * <p>For example, <pre>{@code
   * ListPattern.findFrom(list, firstElements((a, b, c) -> a + b + c))
   * }</pre>
   * is equivalent to <pre>{@code
   * list.size() < 3
   *     ? Optional.empty()
   *     : Optional.of(list.get(0) + list.get(1) + list.get(2))
   * }</pre>
   */
  public static <T, R> ListPattern<T, ?, R> firstElements(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2));
      }
      @Override int arity() {
        return 3;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first four elements
   * from the input using the {@code mapper} function.
   *
   * <p>For example, <pre>{@code
   * ListPattern.findFrom(list, firstElements((a, b, c, d) -> a + b + c + d))
   * }</pre> is equivalent to <pre>{@code
   * list.size() < 4
   *     ? Optional.empty()
   *     : Optional.of(list.get(0) + list.get(1) + list.get(2) + list.get(3))
   * }</pre>
   */
  public static <T, R> ListPattern<T, ?, R> firstElements(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3));
      }
      @Override int arity() {
        return 4;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first five elements
   * from the input using the {@code mapper} function.
   *
   * <p>For example, <pre>{@code
   * ListPattern.findFrom(list, firstElements((a, b, c, d, e) -> a + b + c + d + e))
   * }</pre> is equivalent to <pre>{@code
   * list.size() < 5
   *     ? Optional.empty()
   *     : Optional.of(list.get(0) + list.get(1) + list.get(2) + list.get(3) + list.get(4))
   * }</pre>
   */
  public static <T, R> ListPattern<T, ?, R> firstElements(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
      }
      @Override int arity() {
        return 5;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first six elements
   * from the input using the {@code mapper} function.
   *
   * <p>For example, <pre>{@code
   * ListPattern.findFrom(list, firstElements((a, b, c, d, e, f) -> a + b + c + d + e + f))
   * }</pre> is equivalent to <pre>{@code
   * list.size() < 6
   *     ? Optional.empty()
   *     : Optional.of(list.get(0) + list.get(1) + list.get(2) + list.get(3) + list.get(4) + list.get(5))
   * }</pre>
   */
  public static <T, R> ListPattern<T, ?, R> firstElements(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override int arity() {
        return 6;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that attempts to find and transform the first two input elements,
   * but only if the two elements satisfy {@code condition}.
   */
  public static <T, R> ListPattern<T, ?, R> firstElementsIf(
      BiPredicate<? super T, ? super T> condition,
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new MinSize<T, R>() {
      @Override boolean matches(List<? extends T> list) {
        return super.matches(list) && condition.test(list.get(0), list.get(1));
      }
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override int arity() {
        return 2;
      }
      @Override public String toString() {
        return "the first two elements and they satisfy " + condition;
      }
    };
  }

  /**
   * Returns a {@code ListPattern} that matches when there are zero input elements,
   * in which case, {@code supplier} is invoked whose return value is the result.
   */
  public static <T, R> ListPattern<T, ?, R> empty(Supplier<? extends R> supplier) {
    requireNonNull(supplier);
    return new ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return supplier.get();
      }
      @Override int arity() {
        return 0;
      }
      @Override public String toString() {
        return "empty";
      }
    };
  }

  abstract boolean matches(List<? extends T> list);
  abstract R map(List<? extends T> list);

  /** Returns the string representation of this {@code ListPattern}. */
  @Override public abstract String toString();

  private static abstract class ShortListCase<T, R> extends ListPattern<T, List<T>, R> {
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
  }

  static abstract class ExactSize<T, R> extends ShortListCase<T,  R> {
    @Override boolean matches(List<? extends T> list) {
      return list.size() == arity();
    }

    @Override public final Supplier<List<T>> supplier() {
      return () -> new BoundedBuffer<>(arity() + 1);
    }

    @Override public String toString() {
      return "only " + arity() + " elements";
    }

    abstract int arity();
  }

  private static abstract class MinSize<T, R> extends ShortListCase<T,  R> {
    @Override boolean matches(List<? extends T> list) {
      return list.size() >= arity();
    }

    @Override public final Supplier<List<T>> supplier() {
      return () -> new BoundedBuffer<>(arity());
    }

    @Override public String toString() {
      return "at least " + arity() + " elements";
    }

    abstract int arity();
  }

  ListPattern() {}
}

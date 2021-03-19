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
import java.util.function.BinaryOperator;
import java.util.function.Function;
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
 * <p>A {@code Case} object can be used as a stand-alone {@link Collector} for a stream. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.exactly;
 *
 * stream.collect(onlyElements((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible cases passed to the static {@link #findFrom findFrom()} method.
 * For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.*;
 *
 * Optional<Path> path = Case.findFrom(
 *     pathComponents,
 *     onlyElements((parent, child) -> ...),
 *     onlyElements(fileName -> ...),
 *     firstElements(root -> ...));
 * }</pre>
 *
 * @since 5.3
 */
public abstract class Case<T, A, R> implements Collector<T, A, R> {
  private static final int MAX_CARDINALITY = 8;
  private static final Case<Object, ?, ?> FIRST_ELEMENT = firstElement(Function.identity());

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
   *     Case.findFrom(
   *         list,
   *         exactly((a, b) -> ...),
   *         firstElements((a, b, c) -> ...));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Optional<R> findFrom(
      List<T> list, Case<? super T, ?, ? extends R>... cases) {
    requireNonNull(list);
    for (Case<?, ?, ?> pattern : cases) {
      requireNonNull(pattern);
    }
    for (Case<? super T, ?, ? extends R> pattern : cases) {
      if (pattern.matches(list)) {
        return Optional.of(pattern.map(list));
      }
    }
    return Optional.empty();
  }

  /** Returns a {@code Case} that finds the first element from the input. */
  @SuppressWarnings("unchecked")  // This Case takes any T and returns T.
  public static <T> Case<T, ?, T> firstElement() {
    return (Case<T, ?, T>) FIRST_ELEMENT;
  }

  /**
   * Returns a {@code Case} that matches when there are at least one input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElement(Function<? super T, ? extends R> mapper) {
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
   * Returns a {@code Case} that matches when there are at least two input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElements(
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
   * Returns a {@code Case} that matches when there are at least three input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElements(Ternary<? super T, ? extends R> mapper) {
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
   * Returns a {@code Case} that matches when there are at least four input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElements(Quarternary<? super T, ? extends R> mapper) {
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
   * Returns a {@code Case} that matches when there are at least five input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElements(Quinary<? super T, ? extends R> mapper) {
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
   * Returns a {@code Case} that matches when there are at least six input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, ?, R> firstElements(Senary<? super T, ? extends R> mapper) {
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
   * Returns a {@code Case} that matches when there are zero input elements,
   * in which case, {@code supplier} is invoked whose return value is used as the pattern matching
   * result.
   */
  public static <T, R> Case<T, ?, R> empty(Supplier<? extends R> supplier) {
    requireNonNull(supplier);
    return new ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return supplier.get();
      }
      @Override public String toString() {
        return "empty";
      }
      @Override int arity() {
        return 0;
      }
    };
  }

  abstract boolean matches(List<? extends T> list);
  abstract R map(List<? extends T> list);

  /** Returns the string representation of this {@code Case}. */
  @Override public abstract String toString();

  private static abstract class ShortListCase<T, R> extends Case<T, List<T>, R> {
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

    @Override public String toString() {
      return "only " + arity() + " elements";
    }

    @Override public final Supplier<List<T>> supplier() {
      return () -> new BoundedBuffer<>(arity() + 1);
    }

    abstract int arity();
  }

  private static abstract class MinSize<T, R> extends ShortListCase<T,  R> {
    @Override boolean matches(List<? extends T> list) {
      return list.size() >= arity();
    }

    @Override public String toString() {
      return "at least " + arity() + " elements";
    }

    @Override public final Supplier<List<T>> supplier() {
      return () -> new BoundedBuffer<>(arity());
    }

    abstract int arity();
  }

  Case() {}
}

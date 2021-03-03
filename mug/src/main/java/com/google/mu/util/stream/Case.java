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
 * <p>A {@code Case} object can be used as a stand-alone {@link Collector} for a stream. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.exactly;
 *
 * stream.collect(exactly((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible cases passed to the static {@link #matching matching()} method.
 * For example:
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.*;
 * import static com.google.mu.util.stream.Case.orElse;
 *
 * Path path = pathComponents.stream()
 *     .filter(...)
 *     .collect(
 *         Case.matching(
 *             exactly((parent, child) -> ...),
 *             exactly(fileName -> ...),
 *             atLeast(root -> ...),
 *             orElse(l -> ...)));
 * }</pre>
 *
 * In the above example, if you have a {@link List} instead of a stream, you can use the static
 * {@link #match match()} method instead to avoid iterating through every list elements:
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.*;
 * import static com.google.mu.util.stream.Case.orElse;
 *
 * Path path = Case.match(
 *     pathComponents,
 *     exactly((parent, child) -> ...),
 *     exactly(fileName -> ...),
 *     atLeast(root -> ...),
 *     orElse(l -> ...));
 * }</pre>
 *
 * @since 5.3
 */
public abstract class Case<T, R> implements Collector<T, List<T>, R> {
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
   * Returns a {@code Collector} that will expand the input elements and transform them using the
   * first from {@code cases} that matches. If no case matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example if a string could be in the form of {@code <resource_name>} with no qualifier,
   * or in the form of {@code <project>.<resource_name>} with project as the qualifier,
   * or in the form of {@code <project>.<location>.<resource_name>}, with both project and
   * location qualifiers, you can handle all 3 cases using:
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.exactly;
   *
   * Substring.first('.').repeatedly().split(string)
   *     .collect(
   *         Case.matching(
   *             exactly(resourceName -> ...),
   *             exactly((project, resourceName) -> ...),
   *             exactly(((project, location, resourceName) -> ...))));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> matching(Case<? super T, ? extends R>... cases) {
    List<Case<? super T, ? extends R>> caseList = copyOf(cases);
    return collectingAndThen(toList(), list -> match(list, caseList));
  }

  /**
   * Expands the input elements in {@code list} and transforms them using the
   * first from {@code cases} that matches. If no case matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example, to switch among multiple possible cases:
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.*;
   *
   * Case.match(
   *     list,
   *     exactly((a, b) -> ...),
   *     atLeast((a, b, c) -> ...),
   *     empty(() -> ...));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> R match(
      List<T> list, Case<? super T, ? extends R>... cases) {
    return match(list, copyOf(cases));
  }

  static <T, R> R match(
      List<T> list, Iterable<? extends Case<? super T, ? extends R>> cases) {
    requireNonNull(list);
    for (Case<? super T, ? extends R> pattern : cases) {
      if (pattern.matches(list)) {
        return pattern.map(list);
      }
    }
    throw new IllegalArgumentException(
        showShortList(list) + " matches no pattern from " + cases + ".");
  }

  /**
   * Returns a {@code Case} that matches any input. Pass it in as the last parameter
   * of the {@link #match match()} or {@link MoreCollectors#matching matching()} method to perform a catch-all default.
   *
   * <p>For example:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.*;
   * import static com.google.mu.util.stream.Case.orElse;
   *
   * match(
   *     list,
   *     exactly((a, b) -> ...),
   *     atLeast((a, b, c) -> ...),
   *     orElse(l -> ...));
   * }</pre>
   */
  public static <T, R> Case<T, R> orElse(Function<? super List<T>, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case<T, R>() {
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
      @Override int arity() {
        return Integer.MAX_VALUE;
      }
    };
  }

  /**
   * Returns a collector that optionally collects and wraps the non-null result of the
   * {@code caseCollector} inside an {@link Optional} object, provided the input pattern
   * matches the precondition of {@code caseCollector}.
   * If the input case doesn't match, it will collect to {@code Optional.empty()}.
   *
   * <p>For example, to handle the unexpected input case gracefully without throwing exception, you can:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.Case.maybe;
   * import static com.google.mu.util.stream.MoreCollectors.exactly;
   *
   * Optional<JobId> jobId = ids.stream().collect(maybe(exactly(JobId::new)));
   * }</pre>
   *
   * <p>If {@code caseCollector} results in null, {@link NullPointerException} will be thrown.
   *
   * <p>Usually, if you pass a method reference to one of the factory methods like {@link
   * MoreCollectors#exactly exactly()} or {@link MoreCollectors#atLeast atLeast()}, it's more
   * readable to use the {@link #orNot} method like: <pre>{@code
   * collect(exactly(JobId::new).orNot())
   * }</pre>
   *
   * But when you need to use a lambda, {@code orNot()} could defeat the Java type inferencer,
   * in which case, you can use this equivalent static method to work around the
   * type inference limitation.
   */
  public static <T, R> Collector<T, ?, Optional<R>> maybe(Case<T, R> caseCollector) {
    return caseCollector.orNot();
  }

  /**
   * Returns a collector that optionally collects and wraps the non-null result of this collector
   * inside an {@link Optional} object, provided the input pattern matches this {@code Case}.
   * If the input case doesn't match, it will collect to {@code Optional.empty()}.
   *
   * <p>For example, to handle the unexpected input case gracefully without throwing exception, you can:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.MoreCollectors.exactly;
   *
   * Optional<JobId> kv = ids.stream().collect(exactly(JobId::new).orNot());
   * }</pre>
   *
   * <p>If this collector results in null, {@link NullPointerException} will be thrown.
   *
   * <p>If you run into compilation errors when using lambda like {@code
   * collect(exactly((a, b) -> ...).orNot())}, it could be because Java type inference doesn't
   * work for chained method invocations. In such case, you can use the equivalent static {@link
   * #maybe} method as in {@code collect(maybe(exactly((a, b) -> ...)))}. This works around
   * the Java type inference limitation.
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
   */
  abstract List<T> newBuffer();

  abstract int arity();

  private Optional<R> tryMatch(List<? extends T> list) {
    return matches(list) ? Optional.of(map(list)) : Optional.empty();
  }

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
    return list.size() <= 8  // If small enough, just show it.
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
      return BoundedBuffer.retaining(arity() + 1);
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
      return BoundedBuffer.retaining(arity());
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

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
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility class to perform n-ary functional pattern matching on a list or a stream of input elements.
 *
 * <p>A {@code Case} object can be used as a {@link Collector} for a stream. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.moreCollectors.exactly;
 *
 * stream.collect(exactly((a, b, c) -> ...));
 * }</pre>
 *
 * Or as one of several possible patterns passed to the static {@link #matching matching()} method.
 * For example:
 * <pre>{@code
 * import static com.google.mu.util.stream.moreCollectors.*;
 *
 * Path path = pathComponents.stream()
 *     .filter(...)
 *     .collect(
 *         matching(
 *             exactly((parent, child) -> ...),
 *             exactly(fileName -> ...),
 *             atLeast(root -> ...),
 *             orElse(l -> ...)));
 * }</pre>
 *
 * In the above example, if you have a {@link List} instead of a stream, you can use the static
 * {@link #match match()} method instead to avoid iterating through every list elements:
 * <pre>{@code
 * import static com.google.mu.util.stream.moreCollectors.*;
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
   * Expands the input elements in {@code list} and transforms them using the
   * first from {@code patterns} that matches. If no pattern matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example, to switch among multiple possible cases:
   * <pre>{@code
   * import static com.google.mu.util.stream.moreCollectors.*;
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
      List<T> list, Case<? super T, ? extends R>... patterns) {
    return match(list, Utils.copyOf(patterns));
  }

  static <T, R> R match(
      List<T> list, Iterable<? extends Case<? super T, ? extends R>> patterns) {
    requireNonNull(list);
    for (Case<? super T, ? extends R> pattern : patterns) {
      if (pattern.matches(list)) {
        return pattern.map(list);
      }
    }
    throw new IllegalArgumentException(
        showShortList(list) + " matches no pattern from " + patterns + ".");
  }

  /**
   * Returns a collector that optionally collects and wraps the non-null result of this collector
   * inside an {@link Optional} object, provided the input pattern matches.
   * If the input pattern doesn't match, it will collect to {@code Optional.empty()}.
   *
   * <p>For example, to handle the unexpected input case gracefully without throwing exception, you can:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.moreCollectors.exactly;
   *
   * Optional<JobId> kv = ids.stream().collect(exactly(JobId::new).orNot());
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
   */
  abstract List<T> newBuffer();

  abstract int arity();

  private Optional<R> tryMatch(List<? extends T> list) {
    return matches(list) ? Optional.of(map(list)) : Optional.empty();
  }

  /** Returns the string representation of this pattern. */
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

  Case() {}
}

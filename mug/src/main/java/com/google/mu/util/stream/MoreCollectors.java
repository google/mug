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

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;
import com.google.mu.util.Both;
import com.google.mu.util.MoreCollections;

/**
 * Static utilities pertaining to {@link Collector} in addition to relevant utilities in JDK and Guava.
 *
 * @since 5.2
 */
public final class MoreCollectors {
  /**
   * Analogous to {@link Collectors#mapping Collectors.mapping()}, applies a mapping function to
   * each input element before accumulation, except that the {@code mapper} function returns a
   * <em><b>pair of elements</b></em>, which are then accumulated by a <em>BiCollector</em>.
   *
   * <p>For example, you can parse key-value pairs in the form of "k1=v1,k2=v2" with:
   *
   * <pre>{@code
   * Substring.first(',')
   *     .repeatedly()
   *     .split("k1=v2,k2=v2")
   *     .collect(
   *         mapping(
   *             s -> first('=').split(s).orElseThrow(...),
   *             toImmutableSetMultimap()));
   * }</pre>
   */
  public static <T, A, B, R> Collector<T, ?, R> mapping(
      Function<? super T, ? extends Both<? extends A, ? extends B>> mapper,
      BiCollector<A, B, R> downstream) {
    return Collectors.mapping(
        requireNonNull(mapper), downstream.splitting(BiStream::left, BiStream::right));
  }

  /**
   * Similar but slightly different than {@link Collectors#flatMapping}, returns a {@link Collector}
   * that first flattens the input stream of <em>pairs</em> (as opposed to single elements) and then
   * collects the flattened pairs with the {@code downstream} BiCollector.
   */
  public static <T, K, V, R> Collector<T, ?, R> flatMapping(
      Function<? super T, ? extends BiStream<? extends K, ? extends V>> flattener,
      BiCollector<K, V, R> downstream) {
    return BiStream.flatMapping(
        flattener.andThen(BiStream::mapToEntry),
        downstream.<Map.Entry<? extends K, ? extends V>>splitting(
            Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Returns a {@code Collector} that flattens the input {@link Map} entries and collects them using
   * the {@code downstream} BiCollector.
   *
   * <p>For example, you can flatten a list of multimaps:
   *
   * <pre>{@code
   * ImmutableMap<EmployeeId, Task> billableTaskAssignments = projects.stream()
   *     .map(Project::getTaskAssignments)
   *     .collect(flatteningMaps(ImmutableMap::toImmutableMap)));
   * }</pre>
   *
   * <p>To flatten a stream of multimaps, use {@link #flattening}.
   */
  public static <K, V, R> Collector<Map<K, V>, ?, R> flatteningMaps(
      BiCollector<K, V, R> downstream) {
    return flatMapping(BiStream::from, downstream);
  }

  /**
   * Returns a collector that collects input elements into a list, which is then arranged by the
   * {@code arranger} function before being wrapped as <em>immutable</em> list result.
   * List elements are not allowed to be null.
   *
   * <p>Example usages: <ul>
   * <li>{@code stream.collect(toListAndThen(Collections::reverse))} to collect to reverse order.
   * <li>{@code stream.collect(toListAndThen(Collections::shuffle))} to collect and shuffle.
   * <li>{@code stream.collect(toListAndThen(Collections::sort))} to collect and sort.
   * </ul>
   */
  public static <T> Collector<T, ?, List<T>> toListAndThen(Consumer<? super List<T>> arranger) {
    requireNonNull(arranger);
    Collector<T, ?, List<T>> rejectingNulls =
        Collectors.mapping(Objects::requireNonNull, Collectors.toCollection(ArrayList::new));
    return Collectors.collectingAndThen(rejectingNulls, list -> {
      arranger.accept(list);
      return Collections.unmodifiableList(list);
    });
  }

  /**
   * Returns a collector that collects the only one element from the input and transforms it
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>Can be used together with the other {@code onlyElements()} {@link FixedSizeCollector}
   * as one of the multiple cases passed to {@link #switching}.
   *
   * @since 5.4
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElement(
      Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override int arity() {
        return 1;
      }
    };
  }

  /**
   * Returns a collector that collects the only two elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 2} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, BiFunction)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override int arity() {
        return 2;
      }
    };
  }

  /**
   * Returns a collector that collects the only three elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 3} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, Ternary)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2));
      }
      @Override int arity() {
        return 3;
      }
    };
  }

  /**
   * Returns a collector that collects the only four elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 4} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, Quarternary)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3));
      }
      @Override int arity() {
        return 4;
      }
    };
  }

  /**
   * Returns a collector that collects the only five elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 5} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, Quinary)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
      }
      @Override int arity() {
        return 5;
      }
    };
  }

  /**
   * Returns a collector that collects the only six elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 6} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, Senary)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.apply(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override int arity() {
        return 6;
      }
    };
  }

  /**
   * Returns a {@link Collector} that will collect the input elements using the first of
   * {@code [firstCase, moreCases...]} that matches the input elements.
   *
   * <p>For example, you may have a table name that could be in one of several formats:
   * <ul>
   * <li>{@code database.schema.table};
   * <li>{@code schema.table} in the current database;
   * <li>{@code table} in the current database and current schema;
   * </ul>
   *
   * To handle these different cases, you can do:
   * <pre>{@code
   *   Substring.first('.').repeatedly().split(tableName)
   *       .collect(
   *           switching(
   *               onlyElements((db, schema, table) -> ...),
   *               onlyElements((schema, table) -> ...),
   *               onlyElement(table -> ...)));
   * }</pre>
   *
   * @since 5.4
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> switching(
      FixedSizeCollector<T, ?, R> firstCase, FixedSizeCollector<T, ?, R>... moreCases) {
    List<FixedSizeCollector<T, ?, R>> caseList = new ArrayList<>(1 + moreCases.length);
    caseList.add(requireNonNull(firstCase));
    for (FixedSizeCollector<T, ?, R> c : moreCases) {
      caseList.add(requireNonNull(c));
    }
    return switching(caseList);
  }

  /**
   * Returns a {@code Collector} that collects all of the least (relative to the specified {@code
   * Comparator}) input elements, in encounter order, using the {@code downstream} collector.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Stream.of("foo", "bar", "banana", "papaya")
   *     .collect(least(comparingInt(String::length), toImmutableList()))
   * // returns {"foo", "bar"}
   * }</pre>
   *
   * @since 5.6
   */
  public static <T, R> Collector<T, ?, R> least(
      Comparator<? super T> comparator, Collector<? super T, ?, R> downstream) {
    return greatest(comparator.reversed(), downstream);
  }

  /**
   * Returns a {@code Collector} that collects all of the greatest (relative to the specified {@code
   * Comparator}) input elements, in encounter order, using the {@code downstream} collector.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Stream.of("foo", "quux", "banana", "papaya")
   *     .collect(greatest(comparingInt(String::length), toImmutableList()))
   * // returns {"banana", "papaya"}
   * }</pre>
   *
   * @since 5.6
   */
  public static <T, R> Collector<T, ?, R> greatest(
      Comparator<? super T> comparator, Collector<? super T, ?, R> downstream) {
    requireNonNull(downstream);
    return collectingAndThen(greatest(comparator), tie -> tie.collect(downstream));
  }

  private static <T> Collector<T, ?, Stream<T>> greatest(Comparator<? super T> comparator) {
    requireNonNull(comparator);
    class Builder {
      private final List<T> tie = new ArrayList<>();

      void add(T element) {
        if (tie.isEmpty()) {
          tie.add(element);
        } else {
          int comparisonResult = comparator.compare(tie.get(0), element);
          if (comparisonResult < 0) { // current < element
            tie.clear();
            tie.add(element);
          } else if (comparisonResult == 0) { // current == element
            tie.add(element);
          } // else current > element, discard.
        }
      }

      Builder merge(Builder that) {
        that.build().forEach(this::add);
        return this;
      }

      Stream<T> build() {
        return tie.stream();
      }
    }
    return Collector.of(Builder::new, Builder::add, Builder::merge, Builder::build);
  }

  private static <T, R> Collector<T, ?, R> switching(List<FixedSizeCollector<T, ?, R>> cases) {
    if (cases.size() == 1) {
      return cases.get(0);
    }
    return collectingAndThen(toList(), list -> {
      int elementsToShow = 1;
      for (FixedSizeCollector<T, ?, R> c : cases) {
        if (c.appliesTo(list)) {
          return c.reduce(list);
        }
        elementsToShow = max(elementsToShow, c.arity() + 1);
      }
      throw new IllegalArgumentException(
          "Unexpected input elements " + ShortListCollector.showShortList(list, elementsToShow) + '.');
    });
  }

  private MoreCollectors() {}
}

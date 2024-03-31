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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.mu.collect.MoreCollections;
import com.google.mu.function.MapFrom3;
import com.google.mu.function.MapFrom4;
import com.google.mu.function.MapFrom5;
import com.google.mu.function.MapFrom6;
import com.google.mu.function.MapFrom7;
import com.google.mu.function.MapFrom8;
import com.google.mu.util.BiOptional;
import com.google.mu.util.Both;

/**
 * Static utilities pertaining to {@link Collector} in addition to relevant utilities in JDK and Guava.
 *
 * @since 5.2
 */
public final class MoreCollectors {
  /**
   * Returns a collector that uses {@code inputMapper} function to transform each input, and finally
   * uses {@code outputMapper} to transform the output of the given {@code collector}.
   *
   * @since 6.0
   */
  public static <F, T, T1, R> Collector<F, ?, R> mapping(
      Function<? super F, ? extends T> inputMapper,
      Collector<T, ?, T1> collector,
      Function<? super T1, ? extends R> outputMapper) {
    return Collectors.collectingAndThen(
        Collectors.mapping(requireNonNull(inputMapper), collector), outputMapper::apply);
  }

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
        requireNonNull(mapper), downstream.collectorOf(BiStream::left, BiStream::right));
  }

  /**
   * Similar but slightly different than {@link Collectors#flatMapping}, returns a {@link Collector}
   * that first flattens the input stream of <em>pairs</em> (as opposed to single elements) and then
   * collects the flattened pairs with the {@code downstream} BiCollector.
   */
  public static <T, K, V, R> Collector<T, ?, R> flatMapping(
      Function<? super T, ? extends BiStream<? extends K, ? extends V>> flattener,
      BiCollector<K, V, R> downstream) {
    return Java9Collectors.flatMapping(
        flattener.andThen(BiStream::mapToEntry),
        downstream.<Map.Entry<? extends K, ? extends V>>collectorOf(
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
   */
  public static <K, V, R> Collector<Map<K, V>, ?, R> flatteningMaps(
      BiCollector<K, V, R> downstream) {
    return flatMapping(BiStream::from, downstream);
  }

  /**
   * Returns a {@link Collector} that extracts the keys and values through the given {@code
   * keyFunction} and {@code valueFunction} respectively, and then collects them into a mutable
   * {@code Map} created by {@code mapSupplier}.
   *
   * <p>Duplicate keys will cause {@link IllegalArgumentException} to be thrown, with the offending
   * key reported in the error message.
   *
   * <p>Null keys and values are discouraged but supported as long as the result {@code Map}
   * supports them. Thus this method can be used as a workaround of the <a
   * href="https://bugs.openjdk.java.net/browse/JDK-8148463">toMap(Supplier) JDK bug</a> that fails
   * to support null values.
   *
   * @since 5.9
   */
  public static <T, K, V, M extends Map<K, V>> Collector<T, ?, M> toMap(
      Function<? super T, ? extends K> keyFunction,
      Function<? super T, ? extends V> valueFunction,
      Supplier<? extends M> mapSupplier) {
    requireNonNull(keyFunction);
    requireNonNull(valueFunction);
    requireNonNull(mapSupplier);
    final class Builder {
      private final M map = requireNonNull(mapSupplier.get(), "mapSupplier must not return null");
      private boolean hasNull;

      void add(K key, V value) {
        if (hasNull) { // Existence of null values requires 2 lookups to check for duplicates.
          if (map.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate key: [" + key + "]");
          }
          map.put(key, value);
        } else { // The Map doesn't have null. putIfAbsent() == null means no duplicates.
          if (map.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Duplicate key: [" + key + "]");
          }
          if (value == null) {
            hasNull = true;
          }
        }
      }

      void add(T input) {
        add(keyFunction.apply(input), valueFunction.apply(input));
      }

      Builder addAll(Builder that) {
        return BiStream.from(that.map).collect(this, Builder::add);
      }

      M build() {
        return map;
      }
    }
    return Collector.of(Builder::new, Builder::add, Builder::addAll, Builder::build);
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
   * Returns a collector that collects the only two elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 2} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, BiFunction)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 6.6
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(
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
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom3)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 6.6
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(MapFrom3<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.map(list.get(0), list.get(1), list.get(2));
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
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom4)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 6.6
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(MapFrom4<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.map(list.get(0), list.get(1), list.get(2), list.get(3));
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
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom5)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 6.6
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(MapFrom5<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.map(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
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
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom6)
   * MoreCollections.findOnlyElements()} method,
   * which returns {@link Optional}.
   *
   * @since 6.6
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(MapFrom6<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R reduce(List<? extends T> list) {
        return mapper.map(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override int arity() {
        return 6;
      }
    };
  }

  /**
   * Returns a collector that collects the only seven elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 7} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom7)
   * MoreCollections.findOnlyElements()} method, which returns {@link Optional}.
   *
   * @since 7.2
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(
      MapFrom7<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override
      R reduce(List<? extends T> list) {
        return mapper.map(
            list.get(0),
            list.get(1),
            list.get(2),
            list.get(3),
            list.get(4),
            list.get(5),
            list.get(6));
      }

      @Override
      int arity() {
        return 7;
      }
    };
  }

  /**
   * Returns a collector that collects the only eight elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>To handle the {@code size() != 8} case, consider to use the {@link
   * MoreCollections#findOnlyElements(java.util.Collection, MapFrom8)
   * MoreCollections.findOnlyElements()} method, which returns {@link Optional}.
   *
   * @since 7.2
   */
  public static <T, R> FixedSizeCollector<T, ?, R> combining(
      MapFrom8<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override
      R reduce(List<? extends T> list) {
        return mapper.map(
            list.get(0),
            list.get(1),
            list.get(2),
            list.get(3),
            list.get(4),
            list.get(5),
            list.get(6),
            list.get(7));
      }

      @Override
      int arity() {
        return 8;
      }
    };
  }

  /**
   * Returns collector that collects the single element from the input. It will throw otherwise.
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
   * Same as {@link #combining(BiFunction)}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom3)}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom3<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom4)}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom4<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom5)}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom5<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom6)}.
   *
   * @since 5.3
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom6<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom7)}.
   *
   * @since 7.2
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom7<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Same as {@link #combining(MapFrom7)}.
   *
   * @since 7.2
   */
  public static <T, R> FixedSizeCollector<T, ?, R> onlyElements(
      MapFrom8<? super T, ? extends R> mapper) {
    return combining(mapper);
  }

  /**
   * Returns a {@link Collector} that will collect the input elements using the first of {@code
   * [firstCase, moreCases...]} that matches the input elements.
   *
   * <p>For example, you may have a table name that could be in one of several formats:
   *
   * <ul>
   *   <li>{@code database.schema.table};
   *   <li>{@code schema.table} in the current database;
   *   <li>{@code table} in the current database and current schema;
   * </ul>
   *
   * To handle these different cases, you can do:
   *
   * <pre>{@code
   * Substring.first('.').repeatedly().split(tableName)
   *     .collect(
   *         switching(
   *             onlyElements((db, schema, table) -> ...),
   *             onlyElements((schema, table) -> ...),
   *             onlyElements(table -> ...)));
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
   * Returns a collector that partitions the incoming elements into two groups: elements that match
   * {@code predicate}, and those that don't.
   *
   * <p>For example:
   *
   * <pre>{@code
   * candidates
   *     .collect(partitioningBy(Candidate::isEligible, toImmutableList()))
   *     .andThen((eligible, ineligible) -> ...);
   * }</pre>
   *
   * <p>Compared to {@link Collectors#partitioningBy}, which returns a {@code Map<Boolean, V>}, the
   * syntax is easier to be chained without needing an intermediary {@code Map} local variable.
   *
   * @param <E> the input element type
   * @param <R> the result type of {@code downstream} collector.
   *
   * @since 6.0
   */
  public static <E, R> Collector<E, ?, Both<R, R>> partitioningBy(
      Predicate<? super E> predicate, Collector<E, ?, R> downstream) {
    return partitioningBy(predicate, downstream, downstream);
  }

  /**
   * Returns a collector that partitions the incoming elements into two groups: elements that match
   * {@code predicate}, and those that don't, and use {@code downstreamIfTrue} and {@code
   * downstreamIfFalse} respectively to collect the elements.
   *
   * <p>For example:
   *
   * <pre>{@code
   * candidates
   *     .collect(partitioningBy(Candidate::isPrimary, toOptional(), toImmutableList()))
   *     .andThen((primary, secondaries) -> ...);
   * }</pre>
   *
   * <p>Compared to {@link Collectors#partitioningBy}, which returns a {@code Map<Boolean, V>}, the
   * syntax is easier to be chained without needing an intermediary {@code Map} local variable; and
   * you can collect the two partitions to different types.
   *
   * @param <E> the input type
   * @param <A1> the accumulator type of the {@code downstreamIfTrue} collector
   * @param <A2> the accumulator type of the {@code downstreamIfFalse} collector
   * @param <T> the result type of the {@code downstreamIfTrue} collector
   * @param <F> the result type of the {@code downstreamIfFalse} collector
   * @since 6.5
   */
  public static <E, A1, A2, T, F> Collector<E, ?, Both<T, F>> partitioningBy(
      Predicate<? super E> predicate,
      Collector<E, A1, T> downstreamIfTrue,
      Collector<E, A2, F> downstreamIfFalse) {
    requireNonNull(predicate);
    Supplier<A1> factory1 = downstreamIfTrue.supplier();
    Supplier<A2> factory2 = downstreamIfFalse.supplier();
    BiConsumer<A1, E> accumulator1 = downstreamIfTrue.accumulator();
    BiConsumer<A2, E> accumulator2 = downstreamIfFalse.accumulator();
    final class Builder {
      private A1 container1 = factory1.get();
      private A2 container2 = factory2.get();

      void add(E input) {
        if (predicate.test(input)) {
          accumulator1.accept(container1, input);
        } else {
          accumulator2.accept(container2, input);
        }
      }

      Builder addAll(Builder that) {
        container1 = downstreamIfTrue.combiner().apply(container1, that.container1);
        container2 = downstreamIfFalse.combiner().apply(container2, that.container2);
        return this;
      }

      Both<T, F> build() {
        return Both.of(
            downstreamIfTrue.finisher().apply(container1),
            downstreamIfFalse.finisher().apply(container2));
      }
    }
    return Collector.of(Builder::new, Builder::add, Builder::addAll, Builder::build);
  }

  /**
   * Returns a collector that collects the minimum and maximum elements from the input elements.
   * the result {@code BiOptional}, if present, contains the pair of {@code (min, max)}.
   *
   * <p>Null elements are supported as long as {@code comparator} supports them.
   *
   * @since 6.0
   */
  public static <T> Collector<T, ?, BiOptional<T, T>> minMax(Comparator<? super T> comparator) {
    requireNonNull(comparator);
    class MinMax {
      private boolean empty = true;
      private T min;
      private T max;

      void add(T element) {
        if (empty) {
          min = max = element;
          empty = false;
        } else {
          int againstMin = comparator.compare(element, min);
          if (againstMin < 0) {
            min = element;
          } else if (againstMin > 0 && comparator.compare(element, max) > 0) {
            // If equal to min, we don't need to compare with max.
            max = element;
          }
        }
      }

      MinMax merge(MinMax that) {
        that.get().ifPresent((a, b) -> { add(a); add(b); });
        return this;
      }

      BiOptional<T, T> get() {
        if (empty) {
          return BiOptional.empty();
        }
        if (min == null || max == null) {
          return Both.of(min, max).filter((x, y) -> true);
        }
        return BiOptional.of(min, max);
      }
    }

    return Collector.of(MinMax::new, MinMax::add, MinMax::merge, MinMax::get);
  }

  /**
   * Returns a {@code Collector} that collects all of the least (relative to the specified {@code
   * Comparator}) input elements, in encounter order, using the {@code downstream} collector.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Stream.of("foo", "bar", "banana", "papaya")
   *     .collect(allMin(comparingInt(String::length), toImmutableList()))
   * // returns {"foo", "bar"}
   * }</pre>
   *
   * @since 5.6
   */
  public static <T, R> Collector<T, ?, R> allMin(
      Comparator<? super T> comparator, Collector<? super T, ?, R> downstream) {
    return allMax(comparator.reversed(), downstream);
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
  public static <T, R> Collector<T, ?, R> allMax(
      Comparator<? super T> comparator, Collector<? super T, ?, R> downstream) {
    requireNonNull(comparator);
    requireNonNull(downstream);
    class Builder {
      private final ArrayList<T> tie = new ArrayList<>();

      void add(T element) {
        if (tie.isEmpty()) {
          tie.add(element);
        } else {
          int comparisonResult = comparator.compare(tie.get(0), element);
          if (comparisonResult == 0) { // current == element
            tie.add(element);
          } else if (comparisonResult < 0) { // current < element
            tie.clear();
            tie.add(element);
          } // else current > element, discard.
        }
      }

      Builder merge(Builder that) {
        that.tie.stream().forEach(this::add);
        return this;
      }

      R build() {
        return tie.stream().collect(downstream);
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

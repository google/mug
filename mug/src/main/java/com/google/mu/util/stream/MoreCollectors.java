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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;
import com.google.mu.util.Both;
import com.google.mu.util.MoreLists;

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
   * Returns a collector that collects the only two elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>If you need to handle the {@code size() != 2} case, consider to use the {@link
   * MoreLists#findOnly(java.util.List, BiFunction) MoreLists.findOnly()} method, which
   * returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> Collector<T, ?, R> onlyElements(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R map(List<? extends T> list) {
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
   * <p>If you need to handle the {@code size() != 3} case, consider to use the {@link
   * MoreLists#findOnly(java.util.List, Ternary) MoreLists.findOnly()} method, which
   * returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> Collector<T, ?, R> onlyElements(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R map(List<? extends T> list) {
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
   * <p>If you need to handle the {@code size() != 4} case, consider to use the {@link
   * MoreLists#findOnly(java.util.List, Quarternary) MoreLists.findOnly()} method, which
   * returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> Collector<T, ?, R> onlyElements(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R map(List<? extends T> list) {
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
   * <p>If you need to handle the {@code size() != 5} case, consider to use the {@link
   * MoreLists#findOnly(java.util.List, Quinary) MoreLists.findOnly()} method, which
   * returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> Collector<T, ?, R> onlyElements(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R map(List<? extends T> list) {
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
   * <p>If you need to handle the {@code size() != 6} case, consider to use the {@link
   * MoreLists#findOnly(java.util.List, Senary) MoreLists.findOnly()} method, which
   * returns {@link Optional}.
   *
   * @since 5.3
   */
  public static <T, R> Collector<T, ?, R> onlyElements(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new ShortListCollector<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(
            list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5));
      }
      @Override int arity() {
        return 6;
      }
    };
  }

  private MoreCollectors() {}
}

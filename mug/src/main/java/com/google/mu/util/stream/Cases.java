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

import static com.google.mu.util.stream.Cases.TinyContainer.toTinyContainer;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Collectors for short streams with 0, 1 or 2 elements using a {@link Supplier}, {@link Function}
 * or {@link BiFunction} respectively.
 *
 * <p>For example, if the input collection must have only 2 elements:
 *
 * <pre>{@code
 * Foo result = input.stream().collect(onlyElements((a, b) -> ...));
 * }</pre>
 *
 * <p>Or, if the input may or may not have two elements:
 *
 * <pre>{@code
 * Optional<Foo> result = input.stream().collect(when((a, b) -> ...));
 * }</pre>
 *
 * <p>Or, if the input could have zero, one or two elements:
 *
 * <pre>{@code
 * Foo result = input.stream().collect(
 *     cases(
 *         when(() -> ...),
 *         when(a -> ...),
 *         when((a, b) -> ...)));
 * }</pre>
 *
 * @since 3.6
 * @deprecated Use {@link com.google.mu.util.patternmatch.MatchingCollector} instead.
 */
@Deprecated
public final class Cases {
  /**
   * A collector that collects the only element from the input,
   * or else throws {@link IllegalArgumentException}.
   */
  public static <T> Collector<T, ?, T> onlyElement() {
    return collectingAndThen(toTinyContainer(), TinyContainer::onlyOne);
  }

  /**
   * A collector that collects the only two elements from the input and returns the result of
   * applying the given {@code twoElements} function.
   *
   * <p>The returned collector throws {@link IllegalArgumentException} otherwise.
   */
  public static <T, R> Collector<T, ?, R> onlyElements(
      BiFunction<? super T, ? super T, ? extends R> twoElements) {
    requireNonNull(twoElements);
    return collectingAndThen(toTinyContainer(), c -> c.only(twoElements));
  }

  /**
   * A collector that wraps the result of {@code noElement.get()} in an {@code Optional} when the
   * input is empty; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   * return usageErrors.stream()
   *     .collect(when(() -> OK))
   *     .orElseThrow(() -> new UsageErrorException(usageErrors));
   * }</pre>
   */
  public static <R> Collector<Object, ?, Optional<R>> when(Supplier<? extends R> noElement) {
    requireNonNull(noElement);
    return collectingAndThen(
        counting(), c -> c == 0 ? Optional.of(noElement.get()) : Optional.empty());
  }

  /**
   * A collector that wraps the result of applying the {@code onlyOne} function in an {@code
   * Optional} when the input has only one element; or else returns {@code Optional.empty()}. For
   * example:
   *
   * <pre>{@code
   * return shards.stream()
   *     .collect(when(shard -> shard))
   *     .orElseGet(() -> flatten(shards));
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(Function<? super T, ? extends R> onlyOne) {
    return when(x -> true, onlyOne);
  }

  /**
   * A collector that wraps the result of applying the {@code onlyOne} function in an {@code
   * Optional} when the input has only one element and it satisfies {@code condition} in terms of
   * {@link Predicate#test}; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   * return statuses.stream()
   *     .collect(when(OK::equals, s -> result))
   *     .orElseThrow(...);
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(
      Predicate<? super T> condition, Function<? super T, ? extends R> onlyOne) {
    requireNonNull(condition);
    requireNonNull(onlyOne);
    return collectingAndThen(toTinyContainer(), c -> c.when(condition, onlyOne));
  }

  /**
   * A collector that wraps the result of applying the {@code onlyTwo} function in an {@code
   * Optional} when the input has only two elements; or else returns {@code Optional.empty()}. For
   * example:
   *
   * <pre>{@code
   * return nameParts.stream()
   *     .collect(when(QualifiedName::new))  // (namespace, name) ->
   *     .orElseThrow(() -> new SyntaxException(...));
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(
      BiFunction<? super T, ? super T, ? extends R> onlyTwo) {
    return when((x, y) -> true, onlyTwo);
  }

  /**
   * A collector that wraps the result of applying the {@code onlyTwo} function in an {@code
   * Optional} when the input has only two elements and the two elements satify {@code condition} in
   * terms of {@link BiPredicate#test}; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   * return nameParts.stream()
   *     .collect(when((namespace, name) -> !invalid.contains(namespace), QualifiedName::new))
   *     .orElseThrow(() -> new SyntaxException(...));
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(
      BiPredicate<? super T, ? super T> condition,
      BiFunction<? super T, ? super T, ? extends R> onlyTwo) {
    requireNonNull(condition);
    requireNonNull(onlyTwo);
    return collectingAndThen(toTinyContainer(), c -> c.when(condition, onlyTwo));
  }

  /**
   * Combines multiple {@code Optional-}returning collector cases, to collect to the first non-empty
   * result. For example:
   *
   * <pre>{@code
   * Name name = nameParts.stream()
   *     .collect(
   *         cases(
   *             when(QualifiedName::new),                // (namespace, name) ->
   *             when(keywords::contains, Keyword::new),  // (keyword) ->
   *             when(UnqualifiedName::new)               // (name) ->
   *             when(Anonymous::new)));                  // () ->
   * }</pre>
   *
   * @since 5.3
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> cases(
      Collector<? super T, ?, ? extends Optional<? extends R>>... cases) {
    List<Collector<? super T, ?, ? extends Optional<? extends R>>> caseList =
        Arrays.stream(cases).peek(Objects::requireNonNull).collect(toList());
    return collectingAndThen(
        toList(),
        input ->
            caseList.stream()
                .map(c -> input.stream().collect(c).orElse(null))
                .filter(v -> v != null)
                .findFirst()
                .orElseThrow(() -> unexpectedSize(input.size())));
  }

  /**
   * @deprecated Use {@link #cases} instead.
   */
  @Deprecated
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> switching(
      Collector<? super T, ?, ? extends Optional<? extends R>>... cases) {
    return cases(cases);
  }

  /** Stores up to 2 elements with zero dynamic memory allocation. */
  static final class TinyContainer<T> {
    private T first;
    private T second;
    private int size = 0;

    static <T> Collector<T, ?, TinyContainer<T>> toTinyContainer() {
      return Collector.of(TinyContainer::new, TinyContainer::add, TinyContainer::addAll);
    }

    void add(T value) {
      if (size == 0) {
        first = value;
      } else if (size == 1) {
        second = value;
      }
      size++;
    }

    // Hate to write this code! But a combiner is upon us whether we want parallel or not.
    TinyContainer<T> addAll(TinyContainer<? extends T> that) {
      int newSize = size + that.size;
      if (that.size > 0) {
        add(that.first);
      }
      if (that.size > 1) {
        add(that.second);
      }
      size = newSize;
      return this;
    }

    int size() {
      return size;
    }

    <R> Optional<R> when(
        Predicate<? super T> condition, Function<? super T, ? extends R> oneElement) {
      return size == 1 && condition.test(first)
          ? Optional.of(oneElement.apply(first))
          : Optional.empty();
    }

    <R> Optional<R> when(
        BiPredicate<? super T, ? super T> condition,
        BiFunction<? super T, ? super T, ? extends R> twoElements) {
      return size == 2 && condition.test(first, second)
          ? Optional.of(twoElements.apply(first, second))
          : Optional.empty();
    }

    <R> R only(BiFunction<? super T, ? super T, ? extends R> twoElements) {
      return when((x, y) -> true, twoElements).orElseThrow(() -> unexpectedSize(size));
    }

    T onlyOne() {
      return when(x -> true, identity()).orElseThrow(() -> unexpectedSize(size));
    }
  }

  private static IllegalArgumentException unexpectedSize(int size) {
    return new IllegalArgumentException("Unexpected input size: " + size);
  }

  private Cases() {}
}

package com.google.mu.util.stream;

import static com.google.mu.util.stream.Case.TinyContainer.toTinyContainer;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Collectors for short streams with arity-based overloads.
 *
 * <p>For example, if the input collection must have only 2 elements:
 *
 * <pre>{@code
 * Foo result = input.stream().collect(only((a, b) -> ...));
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
 *     switching(
 *         when(() -> ...),
 *         when(a -> ...),
 *         when((a, b) -> ...)));
 * }</pre>
 *
 * @since 3.6
 */
public final class Case {
  /** If the collection must have only one element. */
  public static <T, R> Collector<T, ?, R> only(Function<? super T, ? extends R> onlyOne) {
    return switching(when(onlyOne));
  }

  /** If the collection must have only two elements. */
  public static <T, R> Collector<T, ?, R> only(BiFunction<? super T, ? super T, ? extends R> onlyTwo) {
    return switching(when(onlyTwo));
  }

  /** If the collection may have zero element. */
  public static <R> Collector<Object, ?, Optional<R>> when(Supplier<? extends R> noElement) {
    return collectingAndThen(
        counting(), c -> c == 0 ? Optional.of(noElement.get()) : Optional.empty());
  }

  /** If the collection may have one element. */
  public static <T, R> Collector<T, ?, Optional<R>> when(Function<? super T, ? extends R> onlyOne) {
    return collectingAndThen(toTinyContainer(), c -> c.when(onlyOne));
  }

  /** If the collection may have two elements. */
  public static <T, R> Collector<T, ?, Optional<R>> when(
      BiFunction<? super T, ? super T, ? extends R> onlyTwo) {
    return collectingAndThen(toTinyContainer(), c -> c.when(onlyTwo));
  }

  /**
   * Combines multiple {@code Optional-}returning collector cases, to collect to the first non-empty
   * result.
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> switching(
      Collector<? super T, ?, ? extends Optional<? extends R>>... cases) {
    List<Collector<? super T, ?, ? extends Optional<? extends R>>> caseList =
        Arrays.stream(cases).peek(Objects::requireNonNull).collect(toList());
    return collectingAndThen(
        toList(),
        input -> caseList.stream()
            .map(c -> input.stream().collect(c))
            .filter(Optional::isPresent)
            .findFirst()
            .flatMap(identity())
            .orElseThrow(() -> new IllegalArgumentException("Unexpected input size = " + input.size())));
  }

  // Stores up to 2 elements.
  static final class TinyContainer<T> {
    private T first;
    private T second;
    private int size = 0;

    void add(T value) {
      if (size == 0) {
        first = value;
      } else if (size == 1) {
        second = value;
      }
      size++;
    }

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

    <R> Optional<R> when(Function<? super T, ? extends R> then) {
      return size == 1 ? Optional.of(then.apply(first)) : Optional.empty();
    }

    <R> Optional<R> when(BiFunction<? super T, ? super T, ? extends R> then) {
      return size == 2 ? Optional.of(then.apply(first, second)) : Optional.empty();
    }

    static <T> Collector<T, ?, TinyContainer<T>> toTinyContainer() {
      return Collector.of(
          TinyContainer::new,
          TinyContainer::add,
          TinyContainer::addAll,
          identity(),
          Collector.Characteristics.IDENTITY_FINISH);
    }
  }

  private Case() {}
}

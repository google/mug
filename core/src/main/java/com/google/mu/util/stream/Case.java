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
 *     switching(
 *         when(() -> ...),
 *         when(a -> ...),
 *         when((a, b) -> ...)));
 * }</pre>
 *
 * @since 3.6
 */
public final class Case {
  /**
   * A collector that collects the only element from the input,
   * or else throws {@link IllegalArgumentException}. 
   */
  public static <T> Collector<T, ?, T> onlyElement() {
    return collectingAndThen(toTinyContainer(), TinyContainer::onlyOne);
  }

  /**
   * A collector that collects the only two elements from the input and returns the
   * result of applying the given {@code twoElements} function.
   *
   * <p>The returned collector throws {@link IllegalArgumentException} otherwise.
   */
  public static <T, R> Collector<T, ?, R> onlyElements(
      BiFunction<? super T, ? super T, ? extends R> twoElements) {
    return collectingAndThen(toTinyContainer(), c -> c.only(twoElements));
  }

  /**
   * A collector that wraps the result of {@code noElement.get()} in an {@code Optional}
   * when the input is empty; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   *   return usageErrors.stream()
   *       .collect(when(() -> OK))
   *       .orElseThrow(() -> new UsageErrorException(usageErrors));
   * }</pre>
   */
  public static <R> Collector<Object, ?, Optional<R>> when(Supplier<? extends R> noElement) {
    return collectingAndThen(
        counting(), c -> c == 0 ? Optional.of(noElement.get()) : Optional.empty());
  }

  /**
   * A collector that wraps the result of applying {@code onlyOne} in an {@code Optional}
   * when the input has only one element; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   *   return shards.stream()
   *       .collect(when(shard -> shard))
   *       .orElseGet(() -> flatten(shards));
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(Function<? super T, ? extends R> onlyOne) {
    return collectingAndThen(toTinyContainer(), c -> c.when(onlyOne));
  }

  /**
   * A collector that wraps the result of applying {@code onlyOne} in an {@code Optional}
   * when the input has only one element; or else returns {@code Optional.empty()}. For example:
   *
   * <pre>{@code
   *   return nameParts.stream()
   *       .collect(when(QualifiedName::new))  // (namespace, name) ->
   *       .orElseThrow(() -> new SyntaxException(...));
   * }</pre>
   */
  public static <T, R> Collector<T, ?, Optional<R>> when(
      BiFunction<? super T, ? super T, ? extends R> onlyTwo) {
    return collectingAndThen(toTinyContainer(), c -> c.when(onlyTwo));
  }

  /**
   * Combines multiple {@code Optional-}returning collector cases, to collect to the first non-empty
   * result. For example:
   *
   * <pre>{@code
   *   Name name = nameParts.stream()
   *       .collect(switching(
   *           when(QualifiedName::new),    // (namespace, name) ->
   *           when(UnqualifiedName::new),  // (name) ->
   *           when(Anonymous::new)));      // () ->
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> switching(
      Collector<? super T, ?, ? extends Optional<? extends R>>... cases) {
    List<Collector<? super T, ?, ? extends Optional<? extends R>>> caseList =
        Arrays.stream(cases).peek(Objects::requireNonNull).collect(toList());
    return collectingAndThen(
        toList(),  // can't use toTinyContainer() because `cases` could need more than 2 elements.
        input -> caseList.stream()
            .map(c -> input.stream().collect(c))
            .filter(Optional::isPresent)
            .findFirst()
            .flatMap(identity())
            .orElseThrow(() -> unexpectedSize(input.size())));
  }

  /** Stores up to 2 elements. */
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

    <R> Optional<R> when(Function<? super T, ? extends R> oneElement) {
      return size == 1 ? Optional.of(oneElement.apply(first)) : Optional.empty();
    }

    <R> Optional<R> when(BiFunction<? super T, ? super T, ? extends R> twoElements) {
      return size == 2 ? Optional.of(twoElements.apply(first, second)) : Optional.empty();
    }

    T onlyOne() {
      return when(identity()).orElseThrow(() -> unexpectedSize(size));
    }

    <R> R only(BiFunction<? super T, ? super T, ? extends R> twoElements) {
      return when(twoElements).orElseThrow(() -> unexpectedSize(size));
    }
  }

  private static IllegalArgumentException unexpectedSize(int size) {
    return new IllegalArgumentException("Unexpected input size = " + size);
  }

  private Case() {}
}

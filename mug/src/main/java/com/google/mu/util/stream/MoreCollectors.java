package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;
import com.google.mu.util.Both;

/**
 * Static utilities pertaining to {@link Collector} in addition to relevant utilities in JDK and Guava.
 *
 * @since 5.2
 */
public final class MoreCollectors {
  private static final Case<Object, ?> ONLY_ELEMENT = exactly(Function.identity());
  private static final Case<Object, ?> FIRST_ELEMENT = atLeast(Function.identity());
  private static final Case<Object, Object> LAST_ELEMENT = new Case.MinSize<Object, Object>() {
    @Override Object map(List<?> list) {
      return list.get(list.size() - 1);
    }
    @Override List<Object> newBuffer() {
      return BoundedBuffer.retainingLastElementOnly();
    }
    @Override int arity() {
      return 1;
    }
  };

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
   * Returns a {@code Collector} that will expand the input elements and transform them using the
   * first from {@code patterns} that matches. If no pattern matches the input elements,
   * {@code IllegalArgumentException} is thrown.
   *
   * <p>For example if a string could be in the form of {@code <resource_name>} with no qualifier,
   * or in the form of {@code <project>.<resource_name>} with project as the qualifier,
   * or in the form of {@code <project>.<location>.<resource_name>}, with both project and
   * location qualifiers, you can handle all 3 cases using:
   * <pre>{@code
   *    Substring.first('.').repeatedly().split(string)
   *        .collect(
   *            matching(
   *                exactly(resourceName -> ...),
   *                exactly((project, resourceName) -> ...),
   *                exactly(((project, location, resourceName) -> ...))));
   * }</pre>
   */
  @SafeVarargs
  public static <T, R> Collector<T, ?, R> matching(Case<? super T, ? extends R>... patterns) {
    List<Case<? super T, ? extends R>> patternsCopy = Utils.copyOf(patterns);
    return collectingAndThen(toList(), list -> Case.match(list, patternsCopy));
  }

  /**
   * Returns a {@code Case} that matches when there are zero input elements,
   * in which case, {@code supplier} is invoked whose return value is used as the pattern matching
   * result.
   */
  public static <T, R> Case<T, R> empty(Supplier<? extends R> supplier) {
    requireNonNull(supplier);
    return new Case.ExactSize<T, R>() {
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

  /**
   * Returns a {@code Case} that matches when there are exactly one input element.
   * The element will be the result of the matcher. For example, you can get the only element
   * from a stream using {@code stream.collect(onlyElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> Case<T, T> onlyElement() {
    return (Case<T, T>) ONLY_ELEMENT;
  }

  /**
   * Returns a {@code Case} that matches when there are exactly one input element,
   * which will be passed to {@code mapper} and the return value is used as the pattern matching
   * result.
   */
  public static <T, R> Case<T, R> exactly(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override int arity() {
        return 1;
      }
      @Override public String toString() {
        return "exactly 1 element";
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly two input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> exactly(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1));
      }
      @Override int arity() {
        return 2;
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly three input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> exactly(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2));
      }
      @Override int arity() {
        return 3;
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly four input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> exactly(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3));
      }
      @Override int arity() {
        return 4;
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly five input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> exactly(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4));
      }
      @Override int arity() {
        return 5;
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly six input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> exactly(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
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
   * Returns a {@code Case} that matches when there are exactly one input elements
   * that satisfies {@code condition}. Upon match, the single element is passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> Case<T, R> when(
      Predicate<? super T> condition, Function<? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
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
   * Returns a {@code Case} that matches when there are exactly two input elements
   * that satisfy {@code condition}. Upon match, the two elements are passed to {@code mapper} and
   * the return value will be the result.
   */
  public static <T, R> Case<T, R> when(
      BiPredicate<? super T, ? super T> condition,
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(condition);
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
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
   * Returns a {@code Case} that matches when there are at least one input element.
   * The first element will be the result of the matcher. For example, you can get the first
   * element from a non-empty stream using {@code stream.collect(firstElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> Case<T, T> firstElement() {
    return (Case<T, T>) FIRST_ELEMENT;
  }

  /**
   * Returns a {@code Case} that matches when there are at least one input element.
   * The last element will be the result of the matcher. For example, you can get the last
   * element from a non-empty stream using {@code stream.collect(lastElement())}.
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> Case<T, T> lastElement() {
    return (Case<T, T>) LAST_ELEMENT;
  }

  /**
   * Returns a {@code Case} that matches when there are at least one input elements,
   * which will be passed to {@code mapper} and the return value will be the result.
   */
  public static <T, R> Case<T, R> atLeast(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
  public static <T, R> Case<T, R> atLeast(
      BiFunction<? super T, ? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
  public static <T, R> Case<T, R> atLeast(Ternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
  public static <T, R> Case<T, R> atLeast(Quarternary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
  public static <T, R> Case<T, R> atLeast(Quinary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
  public static <T, R> Case<T, R> atLeast(Senary<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.MinSize<T, R>() {
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
   * Returns a {@code Case} that matches any input. Pass it in as the last parameter
   * of the {@link #match match()} or {@link #matching matching()} method to perform a catch-all default.
   *
   * <p>For example:
   *
   * <pre>{@code
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

  private MoreCollectors() {}
}

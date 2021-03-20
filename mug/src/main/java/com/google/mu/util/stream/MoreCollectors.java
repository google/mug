package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

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
  private static final Case<Object, ?, ?> ONLY_ELEMENT = onlyElement(Function.identity());

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
   * Returns a collector that collects to the only one element from the input, or else throws
   * IllegalArgumentExceptioin. For example: {@code stream.collect(onlyElement())}.
   *
   * <p>You can also pass the returned {@code Case} collector to the {@link Case#findFrom} method,
   * which returns {@code Optional<T>}, allowing you to handle the
   * "what if there's zero or more than one elements?" case. For example:
   *
   * <pre>{@code
   * Optional<LogRecord> logRecord = findFrom(logRecords, onlyElement());
   * }</pre>
   *
   * <p>More than one cases can be passed to {@code findFrom()} when there are multiple possible
   * cases. Say, if the list could contain one or two elements:
   *
   * <pre>{@code
   * Optional<LogRecord> logRecord = findFrom(
   *     logRecords,
   *     onlyElement(),  // If there is only one record, use it as is.
   *     onlyElements((online, offline) -> mergeLogRecords(online, offline)));
   * }</pre>
   *
   * <p>There are also conditional {@link #onlyElementIf(Predicate) onlyElementIf()},
   * and non-exact cases such as {@link Case#firstElement(Function) firstElement()} and friends.
   *
   * @since 5.3
   */
  @SuppressWarnings("unchecked")  // This collector takes any T and returns as is.
  public static <T> Case<T, ?, T> onlyElement() {
    return (Case<T, ?, T>) ONLY_ELEMENT;
  }

  /**
   * Returns a collector that collects the only one element from the input and transforms it
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>Usually you want to use {@link #onlyElement()} instead to get the only element from the
   * stream or list. This method is useful when you have multiple potential cases passed to the
   * {@link Case#findFrom} method. For example, you may want to parse either a qualified
   * resource name in the format of "foo.bar", or an unqualified name "bar":
   *
   * <pre>{@code
   * Optional<ResourceName> resourceName = Case.findFrom(
   *     Splitter.on('.').split(resourceNameString),
   *     onlyElements((parent, name) -> ResourceName.qualified(parent, name)),
   *     onlyElement(name -> ResourceName.unqualified(name)));
   * }</pre>
   *
   * <p>There are also conditional {@link #onlyElementIf(Predicate, Function) onlyElementIf()},
   * and non-exact cases such as {@link Case#firstElement(Function) firstElement()} and friends.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElement(Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    return new Case.ExactSize<T, R>() {
      @Override R map(List<? extends T> list) {
        return mapper.apply(list.get(0));
      }
      @Override int arity() {
        return 1;
      }
      @Override public String toString() {
        return "only 1 element";
      }
    };
  }

  /**
   * Returns a {@code Case} that matches when there are exactly one input element,
   * and the element satisfies {@code condition}.
   *
   * <p>This method is usually used as one of the multiple potential cases passed to
   * the {@link Case#findFrom} method so that you can use a guard {@link Predicate} to
   * constrain a particular case.
   *
   * @since 5.3
   */
  public static <T> Case<T, ?, T> onlyElementIf(Predicate<? super T> condition) {
    return onlyElementIf(condition, Function.identity());
  }

  /**
   * Returns a {@code Case} that matches when there are exactly one input element,
   * and the element satisfies {@code condition}. Upon match, the element is passed to
   * {@code mapper} and the return value will be the result.
   *
   * <p>This method is usually used as one of the multiple potential cases passed to
   * the {@link Case#findFrom} method so that you can use a guard {@link Predicate} to
   * constrain a particular case.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElementIf(
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
        return "exactly 1 element and it satisfies " + condition;
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
   * <p>You can also pass the returned {@code Case} collector to the {@link Case#findFrom} method,
   * which returns {@code Optional<T>}, allowing you to handle the
   * "what if there are fewer or more than two elements?" case. For example:
   *
   * <pre>{@code
   * Optional<LogRecord> logRecord = findFrom(
   *     logRecords,
   *     onlyElements((online, offline) -> mergeLogRecords(online, offline)));
   * }</pre>
   *
   * <p>More than one cases can be passed to {@code findFrom()} when there are multiple possible
   * cases. Say, if the list could contain one or two elements:
   *
   * <pre>{@code
   * Optional<LogRecord> logRecord = findFrom(
   *     logRecords,
   *     onlyElement(),  // If there is only one record, use it as is.
   *     onlyElements((online, offline) -> mergeLogRecords(online, offline)));
   * }</pre>
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElements(
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
   * Returns a {@code Case} that matches when there are exactly two input elements,
   * and the two elements satisfy {@code condition}. Upon match, the two elements are passed to
   * {@code mapper} and the return value will be the result.
   *
   * <p>This method is usually used as one of the multiple potential cases passed to
   * the {@link Case#findFrom} method so that you can use a guard {@link BiPredicate} to
   * constrain a particular case.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElementsIf(
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
        return "exactly 2 elements and they satisfy " + condition;
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
   * Case#findFrom Case.findFrom(List, Case...)} method, which allows you to pass more than one possible
   * cases, and returns {@code Optional.empty()} if none of the provided cases match.
   *
   * <p>There are also conditional {@link #onlyElementsIf(BiPredicate, BiFunction) onlyElementsIf()},
   * and non-exact cases such as {@link Case#firstElements(Ternary) firstElements()} and friends.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElements(Ternary<? super T, ? extends R> mapper) {
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
   * Returns a collector that collects the only four elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>If you need to handle the {@code size() != 4} case, consider to use the {@link
   * Case#findFrom Case.findFrom(List, Case...)} method, which allows you to pass more than one possible
   * cases, and returns {@code Optional.empty()} if none of the provided cases match.
   *
   * <p>There are also conditional {@link #onlyElementsIf(BiPredicate, BiFunction) onlyElementsIf()},
   * and non-exact cases such as {@link Case#firstElements(Quarternary) firstElements()} and friends.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElements(Quarternary<? super T, ? extends R> mapper) {
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
   * Returns a collector that collects the only five elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>If you need to handle the {@code size() != 5} case, consider to use the {@link
   * Case#findFrom Case.findFrom(List, Case...)} method, which allows you to pass more than one possible
   * cases, and returns {@code Optional.empty()} if none of the provided cases match.
   *
   * <p>There are also conditional {@link #onlyElementsIf(BiPredicate, BiFunction) onlyElementsIf()},
   * and non-exact cases such as {@link Case#firstElements(Quinary) firstElements()} and friends.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElements(Quinary<? super T, ? extends R> mapper) {
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
   * Returns a collector that collects the only six elements from the input and transforms them
   * using the {@code mapper} function. If there are fewer or more elements in the input,
   * IllegalArgumentExceptioin is thrown.
   *
   * <p>If you need to handle the {@code size() != 6} case, consider to use the {@link
   * Case#findFrom Case.findFrom(List, Case...)} method, which allows you to pass more than one possible
   * cases, and returns {@code Optional.empty()} if none of the provided cases match.
   *
   * <p>There are also conditional {@link #onlyElementsIf(BiPredicate, BiFunction) onlyElementsIf()},
   * and non-exact cases such as {@link Case#firstElements(Senary) firstElements()} and friends.
   *
   * @since 5.3
   */
  public static <T, R> Case<T, ?, R> onlyElements(Senary<? super T, ? extends R> mapper) {
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

  private MoreCollectors() {}
}

package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Represents two unrelated or loosely-related things of type {@code A} and {@code B}.
 *
 * <p>Usually used as the return type of a function that needs to return two things. For example:
 *
 * <pre>{@code
 * first('=')
 *     .split("k=v")             // BiOptional<String, String>
 *     .orElseThrow(...)         // Both<String, String>
 *     .andThen(KeyValue::new);  // KeyValue
 * }</pre>
 *
 * Or:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.partitioningBy;
 *
 * contacts.stream()
 *     .collect(partitioningBy(Contact::isPrimary, toOptional(), toImmutableList()))
 *     .andThen((primary, secondaries) -> ...);
 * ...
 * }</pre>
 *
 * <p>If you have a stream of {@code Both} objects, the following turns it into a {@code BiStream}:
 * <pre>{@code
 * BiStream<String, String> keyValues =
 *     BiStream.from(
 *         first(',')
 *             .repeatedly()
 *             .split("k1=v1,k2=v2,k3=v3")
 *             .map(s -> first('=').split(s).orElseThrow(...)));
 * }</pre>
 *
 * Or in a single chained expression:
 * <pre>{@code
 * import static com.google.mu.util.stream.BiStream.toBiStream;
 *
 * BiStream<String, String> keyValues =
 *     first(',')
 *         .repeatedly()
 *         .split("k1=v1,k2=v2,k3=v3")
 *         .collect(toBiStream(s -> first('=').split(s).orElseThrow(...)));
 * }</pre>
 *
 * <p>A stream of {@code Both} can also be collected using a {@code BiCollector}:
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreStreams.mapping;
 *
 * ImmutableListMultimap<String, String> keyValues =
 *     first(',')
 *         .repeatedly()
 *         .split("k1=v1,k2=v2,k3=v3")
 *         .collect(
 *             mapping(
 *                 s -> first('=').split(s).orElseThrow(...),
 *                 toImmutableListMultimap()));
 * }</pre>
 *
 * <p>Intended as a short-lived intermediary type in a fluent expression (e.g. from {@link
 * com.google.mu.util.stream.MoreCollectors#partitioningBy}, {@link
 * Substring.Pattern#split}), it's expected that you can either chain fluently using {@link
 * #andThen}, {@link #filter}, or directly pass it to common libraries without needing to
 * extract the two values. If you really have to extract the two values individually though,
 * consider using {@link #toEntry} and then call the {@link Map.Entry#getKey} and {@link
 * Map.Entry#getValue} methods to access them. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreCollectors.partitioningBy;
 *
 * var primaryAndSecondaries =
 *     contacts.stream()
 *         .collect(partitioningBy(Contact::isPrimary, toOptional(), toImmutableList()))
 *         .toEntry();
 * Optional<Contact> primary = primaryAndSecondaries.getKey();
 * ImmutableList<Contact> secondaries = primaryAndSecondaries.getValue();
 * ...
 * }</pre>
 *
 * @since 5.1
 */
@FunctionalInterface
public interface Both<A, B> {
  /**
   * Returns an instance with both {@code a} and {@code b}.
   *
   * @since 5.8
   */
  public static <A, B> Both<A, B> of(A a, B b) {
    return new BiOptional.Present<>(a, b);
  }

  /**
   * Applies the {@code mapper} function with this pair of two things as arguments.
   *
   * @throws NullPointerException if {@code mapper} is null
   */
  <T> T andThen(BiFunction<? super A, ? super B, T> mapper);

  /**
   * If the pair {@link #matches matches()} {@code condition}, returns a {@link BiOptional} containing
   * the pair, or else returns empty.
   *
   * @throws NullPointerException if {@code condition} is null, or if {@code condition} matches but
   *     either value in this pair is null
   */
  default BiOptional<A, B> filter(BiPredicate<? super A, ? super B> condition) {
    requireNonNull(condition);
    return andThen((a, b) -> condition.test(a, b) ? BiOptional.of(a, b) : BiOptional.empty());
  }

  /**
   * If the pair matches {@code predicate}, it's skipped (returns empty).
   *
   * @throws NullPointerException if {@code predicate} is null
   * @since 6.6
   */
  default BiOptional<A, B> skipIf(BiPredicate<? super A, ? super B> predicate) {
    return filter(predicate.negate());
  }

  /**
   * Returns true if the pair matches {@code condition}.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  default boolean matches(BiPredicate<? super A, ? super B> condition) {
    return andThen(condition::test);
  }

  /**
   * Invokes {@code consumer} with this pair and returns this object as is.
   *
   * @throws NullPointerException if {@code consumer} is null
   */
  default Both<A, B> peek(BiConsumer<? super A, ? super B> consumer) {
    requireNonNull(consumer);
    return andThen((a, b) -> {
      consumer.accept(a, b);
      return this;
    });
  }

  /**
   * Returns an immutable {@link Map.Entry} holding the pair of values.
   *
   * @since 6.5
   */
  default Map.Entry<A, B> toEntry() {
    return andThen(AbstractMap.SimpleImmutableEntry::new);
  }
}

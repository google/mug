package com.google.mu.util;

import static java.util.Objects.requireNonNull;

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
 *     .split("k=v")        // BiOptional<String, String>
 *     .orElseThrow(...)    // Both<String, String>
 *     .to(KeyValue::new):  // KeyValue
 * }</pre>
 *
 * <p>If you have a stream of {@code Both} objects, the following turns it into a {@code BiStream}:
 * <pre>{@code
 * BiStream<String, String> keyValues =
 *     BiStream.from(
 *         first(',')
 *             .delimit("k1=v1,k2=v2")
 *             .map(s -> first('=').split(s).orElseThrow(...)));
 * }</pre>
 *
 * Or in a single chained expression:
 * <pre>{@code
 * import static com.google.mu.util.stream.BiStream.toBiStream;
 *
 * BiStream<String, String> keyValues =
 *     first(',')
 *         .delimit("k1=v1,k2=v2")
 *         .collect(toBiStream(s -> first('=').split(s).orElseThrow(...)));
 * }</pre>
 *
 * <p>A stream of {@code Both} can also be collected using a {@code BiCollector}:
 * <pre>{@code
 * import static com.google.mu.util.stream.MoreStreams.mapping;
 *
 * ImmutableListMultimap<String, String> keyValues =
 *     first(',')
 *         .delimit("k1=v1,k2=v2")
 *         .collect(
 *             mapping(
 *                 s -> first('=').split(s).orElseThrow(...),
 *                 toImmutableListMultimap()));
 * }</pre>
 *
 * @since 5.1
 */
@FunctionalInterface
public interface Both<A, B> {
  /**
   * Maps the pair to a single object using the {@code mapper} function.
   *
   * @throws NullPointerException if {@code mapper} is null
   */
  <T> T to(BiFunction<? super A, ? super B, T> mapper);

  /**
   * If the pair {@link #matches matches()} {@code condition}, returns a {@link BiOptional} containing
   * the pair, or else returns empty.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  default BiOptional<A, B> filter(BiPredicate<? super A, ? super B> condition) {
    requireNonNull(condition);
    return to((a, b) -> condition.test(a, b) ? BiOptional.of(a, b) : BiOptional.empty());
  }

  /**
   * Returns true if the pair matches {@code condition}.
   *
   * @throws NullPointerException if {@code condition} is null
   */
  default boolean matches(BiPredicate<? super A, ? super B> condition) {
    return to(condition::test);
  }

  /**
   * Invokes {@code consumer} with the pair and returns this object as is.
   *
   * @throws NullPointerException if {@code consumer} is null
   */
  default Both<A, B> peek(BiConsumer<? super A, ? super B> consumer) {
    requireNonNull(consumer);
    return to((a, b) -> {
      consumer.accept(a, b);
      return this;
    });
  }
}

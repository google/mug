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
 *     .split("k=v")             // BiOptional<String, String>
 *     .orElseThrow(...)         // Both<String, String>
 *     .andThen(KeyValue::new);  // KeyValue
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
}

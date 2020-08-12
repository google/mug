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
package com.google.mu.function;

import static java.util.Objects.requireNonNull;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A function with two results.
 *
 * @since 4.6
 */
@FunctionalInterface
public interface DualValuedFunction<F, V1, V2> {
  /**
   * Invokes this function, passes the two results to {@code then} and returns the final result.
   *
   * <p>Useful when the caller has some domain-specific object to create off of the two result, for
   * example, {@link com.google.mu.util.Substring.Pattern#split(String, BiFunction)} returns two
   * results following this pattern:
   *
   * <pre>{@code
   * first('=').split(string, (name, val) -> Var.newBuilder().setName(name).setValue(val).build());
   * }</pre>
   *
   * The {@code split()} method (and its friend {@code splitThenTrim()}) can then be
   * method-referenced as a {@code DualValuedFunction} and be used in a {@link
   * com.google.mu.util.stream.BiStream} chain, like:
   *
   * <pre>{@code
   * ImmutableSetMultimap<String, String> keyValues = lines.stream()
   *     .map(String::trim)
   *     .filter(l -> l.length() > 0)                     // not empty
   *     .filter(l -> !l.startsWith("//"))                // not comment
   *     .collect(toBiStream(first('=')::splitThenTrim))  // split each line to a key-value pair
   *     .collect(ImmutableSetMultimap::toImmutableSetMultimap);
   * }</pre>
   *
   * @throws NullPointerException if {@code then} is null.
   */
  // No wildcard on V1/V2 so that private methods not using PECS can still be referenced.
  // Users should rarely need to call apply() directly.
  <R> R apply(F input, BiFunction<V1, V2, R> then);

  /**
   * Returns a composed function that first applies this function to its input,
   * and then applies the {@code after} function to the two results. If evaluation of either
   * function throws an exception, it is propagated to the caller of the composed function.
   *
   * @throws NullPointerException if {@code after} is null.
   */
  default <R> Function<F, R> andThen(BiFunction<? super V1, ? super V2, ? extends R> after) {
    @SuppressWarnings("unchecked")  // function is PECS, safe to cast.
    BiFunction<V1, V2, R> then = (BiFunction<V1, V2, R>) requireNonNull(after);
    return input -> apply(input, then);
  }
}

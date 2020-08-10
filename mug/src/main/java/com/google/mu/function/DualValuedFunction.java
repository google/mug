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
   * <p>Useful when the caller has some domain-specific object to create off of the two result,
   * for example: <pre>{@code
   *   splitFunction.apply(string, KeyValue::new);
   * }</pre>
   */
  // No wildcard on V1, V2 to optimize flexibility for implementations (method-ref).
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

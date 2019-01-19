/*****************************************************************************
 * Copyright (C) google.com                                                *
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

/** A binary consumer that can throw checked exceptions. */
@FunctionalInterface
public interface CheckedBiConsumer<A, B, E extends Throwable> {
  void accept(A a, B b) throws E;

  /**
   * Returns a new {@code CheckedBiConsumer} that also passes the inputs to {@code that}.
   */
  default <R> CheckedBiConsumer<A, B, E> andThen(
      CheckedBiConsumer<? super A, ? super B, ? extends E> that) {
    requireNonNull(that);
    return (a, b) -> {
      accept(a, b);
      that.accept(a,  b);
    };
  }
}

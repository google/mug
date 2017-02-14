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

/** A supplier that can throw checked exceptions. */
@FunctionalInterface
public interface CheckedSupplier<T, E extends Throwable> {
  T get() throws E;

  /**
   * Returns a new {@code CheckedSupplier} that maps the return value using {@code mapper}.
   * For example: {@code (x -> 1).andThen(Object::toString).get() => "1"}.
   */
  default <R> CheckedSupplier<R, E> andThen(
      CheckedFunction<? super T, ? extends R, ? extends E> mapper) {
    requireNonNull(mapper);
    return () -> mapper.apply(get());
  }
}

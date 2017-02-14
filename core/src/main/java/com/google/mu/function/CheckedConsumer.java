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

/** A consumer that can throw checked exceptions. */
public interface CheckedConsumer<T, E extends Throwable> {
  void accept(T input) throws E;

  /**
   * Returns a new {@code CheckedConsumer} that also passes the input to {@code that}.
   * For example: {@code out::writeObject.andThen(logger::info).accept("message")}.
   */
  default CheckedConsumer<T, E> andThen(CheckedConsumer<? super T, E> that) {
    requireNonNull(that);
    return input -> {
      accept(input);
      that.accept(input);
    };
  }
}

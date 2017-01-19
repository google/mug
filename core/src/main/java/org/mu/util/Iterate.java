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
package org.mu.util;

import java.util.stream.Stream;

/**
 * WARNING: you should never see a variable or parameter of this type; nor should an instance of it
 * be passed to any method that expects {@link Iterable}. The sole use case this class aims for
 * is to be able to fluently write a {@code for()} loop through a {@link Stream}.
 *
 * <p>The {@code Iterable} returned by {@link #through} is single-use-only!
 */
public interface Iterate<T> extends Iterable<T> {

  /**
   * Iterates through {@code stream}. For example:
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.through(fooStream)) {
   *     ...
   *   }
   * }</pre>
   */
  static <T> Iterate<T> through(Stream<T> stream) {
    return stream::iterator;
  }
}

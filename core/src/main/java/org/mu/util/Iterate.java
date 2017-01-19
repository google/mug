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

/** Helper to make it easier to iterate through {@link Stream}s. */
public final class Iterate {

  /**
   * With due care, iterates through {@code stream} <em>only once</em>. It's strongly recommended
   * to keep it restricted to the scope of a single {@code for} loop because the returned {@code
   * Iterable}'s {@link Iterable#iterator() iterator()} method cannot be called more than once.
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.once(stream)) {
   *     ...
   *   }
   * }</pre>
   */
  public static <T> Iterable<T> once(Stream<T> stream) {
    return stream::iterator;
  }

  private Iterate() {}
}

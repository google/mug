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

import static java.util.Objects.requireNonNull;

import java.util.function.Supplier;
import java.util.stream.Stream;

/** Helpers to make it easier to iterate over {@link Stream}s. */
public final class Iterate {

  /**
   * Iterates through {@code streamProvider}. For example:
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.over(() -> foos.stream().map(...))) {
   *     ...
   *   }
   * }</pre>
   *
   * With due care, this can also be used to iterate through an existing stream instance,
   * as long as it's restricted to the scope of a single {@code for} loop:
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.over(() -> stream)) {
   *     ...
   *   }
   * }</pre>
   */
  public static <T> Iterable<T> over(Supplier<? extends Stream<T>> streamSupplier) {
    requireNonNull(streamSupplier);
    return () -> streamSupplier.get().iterator();
  }

  /**
   * With due care, iterate through {@code stream} <em>once only</em>. It's strongly recommended
   * to keep it restricted to the scope of a single {@code for} loop:
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

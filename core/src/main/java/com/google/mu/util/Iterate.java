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

import java.util.stream.Stream;

import com.google.mu.function.CheckedConsumer;

/** Helper to make it easier to iterate through {@link Stream}s. */
public final class Iterate {

  /**
   * Iterates through {@code stream} <em>only once</em>. It's strongly recommended
   * to avoid assigning the return value to a variable or passing it to any other method because
   * the returned {@code Iterable}'s {@link Iterable#iterator iterator()} method can only be called
   * once. Instead, always use it together with a for-each loop, as in:
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.once(stream)) {
   *     ...
   *     if (...) continue;
   *     if (...) break;
   *     ...
   *   }
   * }</pre>
   *
   * The above is equivalent to manually doing:
   *
   * <pre>{@code
   *   Iterable<Foo> foos = stream::iterator;
   *   for (Foo foo : foos) {
   *     ...
   *   }
   * }</pre>
   * except using this API eliminates the need for a named variable that escapes the scope of the
   * for-each loop. And code is more readable too.
   *
   * <p>Note that {@link #through through()} should be preferred whenever possible due to the
   * caveats mentioned above. This method is still useful when the for-each loop body needs to use
   * control flows such as {@code break} or {@code return}.
   */
  public static <T> Iterable<T> once(Stream<T> stream) {
    return stream::iterator;
  }

  /**
   * Iterates through {@code stream} sequentially and passes each element to {@code consumer}
   * with exceptions propagated. For example: 
   *
   * <pre>{@code
   *   void writeAll(Stream<?> stream, ObjectOutput out) throws IOException {
   *     Iterate.through(stream, out::writeObject);
   *   }
   * }</pre>
   */
  public static <T, E extends Throwable> void through(
      Stream<? extends T> stream, CheckedConsumer<? super T, E> consumer) throws E {
    requireNonNull(consumer);
    for (T element : once(stream)) {
      consumer.accept(element);
    }
  }

  private Iterate() {}
}

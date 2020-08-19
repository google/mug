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
package com.google.mu.util.stream;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This class aims to simplify the common "lookup-then-filter" boilerplate in stream chains:
 *
 * <pre>{@code
 * Map<K, V> map = ...;
 * collection.stream()
 *     .map(map::get)
 *     .filter(v -> v != null)
 *     ...;
 * }</pre>
 *
 * <p>Using {@code FluentLookup}, it can be simplified to:
 *
 * <pre>{@code
 * Map<K, V> map = ...;
 * collection.stream()
 *     .flatMap(FluentLookup.in(map)::findOrEmpty)
 *     ...;
 * }</pre>
 *
 * <p>When used in a {@code Collector}:
 *
 * <pre>{@code
 * ImmutableMap<K, V> map = ...;
 * return Collectors.flatMapping(FluentLookup.in(map)::findOrEmpty, downstream);
 * }</pre>
 *
 * <p>In a slightly different variant, you may need to retain both the original element and the
 * looked-up result, you can keep them in a {@link BiStream} by using the {@link #by} method.
 * For example:
 *
 * <pre>{@code
 * Map<StudentId, Score> testResults = ...;
 * BiStream<Student, Score> studentsWithScore =
 *     BiStream.concat(students.stream().map(FluentLookup.in(testResults).findOrEmpty(Student::id)));
 * }</pre>
 *
 * Or, it can be composed with {@link BiStream#concatenating} to make syntax more fluent:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.BiStream.concatenating;
 *
 * Map<StudentId, Score> testResults = ...;
 * BiStream<Student, Score> studentsWithScore =
 *     students.stream()
 *         .collect(concatenating(FluentLookup.in(testResults).findOrEmpty(Student::id)));
 * }</pre>
 *
 * @since 4.7
 */
public final class FluentLookup<K, V> {
  private final Map<? extends K, ? extends V> map;

  private FluentLookup(Map<? extends K, ? extends V> map) {
    this.map = requireNonNull(map);
  }

  /**
   * Returns an instance backed by {@code map}.
   * The returned instance can then be passed to {@link flatMap()} for a stream.
   */
  public static <K, V> FluentLookup<K, V> in(Map<K, V> map) {
    return new FluentLookup<>(map);
  }

  /**
   * Returns an function that for any given {@code input}, applies {@code keyFunction} and then
   * looks up the value mapped to the key from the backing {@code Map}.
   * If a value is found, the pair of {@code (input, looked-up-value)} is returned; otherwise empty.
   *
   * <p>Can be composed with {@link BiStream#concat(Stream)} or {@link BiStream#concatenating()}
   * to create {@code BiStream}s:
   *
   * <pre>{@code
   * Map<StudentId, Score> testResults = ...;
   * BiStream<Student, Score> studentsWithScore = BiStream.concat(
   *     students.stream().map(FluentLookup.in(testResults).findOrEmpty(Student::id)));
   * }</pre>
   */
  public final <T> Function<T, BiStream<T, V>> findOrEmpty(
      Function<? super T, ? extends K> keyFunction) {
    requireNonNull(keyFunction);
    return input -> {
      V value = map.get(keyFunction.apply(input));
      return value == null ? BiStream.empty() : BiStream.of(input, value);
    };
  }

  /**
   * Looks up the backing map for {@code key} and returns a singleton stream with the value, or
   * empty stream if not found. Useful as a method reference passed to {@code stream.flatMap()}.
   */
  public final Stream<V> findOrEmpty(K key) {
    V value = map.get(key) ;
    return value == null ? Stream.empty() : Stream.of(value);
  }
}

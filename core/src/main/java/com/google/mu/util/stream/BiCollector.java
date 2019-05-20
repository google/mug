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

import java.util.function.Function;
import java.util.stream.Collector;

/**
 * Logically, a {@code BiCollector} collects "pairs of things", just as a {@link Collector} collects
 * "things".
 *
 * <p>{@code BiCollector} is usually passed to {@link BiStream#collect}. For example, to collect the
 * input pairs to a {@code ConcurrentMap}:
 *
 * <pre>{@code
 * ConcurrentMap<String, Integer> map = BiStream.of("a", 1).collect(Collectors::toConcurrentMap);
 * }</pre>
 *
 * <p>In addition, {@code BiCollector} can be used to directly collect a stream of "pair-wise" data
 * structures, such as from {@code Stream<Map>}:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.BiCollectors.flattening;
 * import static com.google.mu.util.stream.BiCollectors.toMap;
 * import static java util.stream.Collectors.summingInt;
 *
 * ImmutableMap<Employee, Integer> employeeTotalTaskHours = projects.stream()
 *   .map(Project::getTaskAssignmentsMap)  // stream of Map<Employee, Task>
 *   .collect(flattening(Map::entrySet, toMap(summingInt(Task::getHours))));
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @param <R> the result type
 */
@FunctionalInterface
public interface BiCollector<K, V, R> {
  /**
   * Returns a {@code Collector} that will first bisect the input elements using {@code toKey} and
   * {@code toValue} and subsequently collect the bisected parts through this {@code BiCollector}.
   *
   * @param <E> used to abstract away the underlying pair/entry type used by {@link BiStream}.
   */
  // Deliberately avoid wildcards for toKey and toValue, because we don't expect
  // users to call this method. Instead, users will typically provide method references matching
  // this signature.
  // Signatures with or without wildcards should both match.
  // In other words, this signature optimizes flexibility for implementors, not callers.
  <E> Collector<E, ?, R> bisecting(Function<E, K> toKey, Function<E, V> toValue);
}

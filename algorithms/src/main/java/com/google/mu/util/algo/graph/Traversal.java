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
package com.google.mu.util.algo.graph;

import static com.google.mu.util.stream.MoreStreams.generate;
import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.mu.util.stream.MoreStreams;

/**
 * Implements genertic graph traversal algorithms ({@link #preOrderFrom pre-order},
 * and {@link #postOrderFrom post-order}).
 *
 * <p>Only depth-first because {@link MoreStreams#generate} is trivially breadth-first.
 *
 * @since 3.9
 */
public final class Traversal {

  /**
   * Starts from {@code initial} and traverse depth first in pre-order by
   * using {@code getChildren} function iteratively.
   */
  public static <T> Stream<T> preOrderFrom(
      T initial,
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    requireNonNull(initial);
    requireNonNull(getChildren);
    Set<T> seen = new HashSet<>();
    seen.add(initial);
    return preOrderFrom(initial, getChildren, seen);
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order by
   * using {@code getChildren} function iteratively.
   */
  public static <T> Stream<T> postOrderFrom(
      T initial,
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    requireNonNull(initial);
    requireNonNull(getChildren);
    Set<T> seen = new HashSet<>();
    seen.add(initial);
    Deque<T> stack = new ArrayDeque<>(seen);
    return postOrderFrom(stack, getChildren, seen);
  }

  private static <T> Stream<T> preOrderFrom(
      T initial,
      Function<? super T, ? extends Stream<? extends T>> getChildren,
      Set<T> seen) {
    return generate(
        initial,
        n -> getChildren.apply(n)
            .peek(Objects::requireNonNull)
            .filter(seen::add)
            .flatMap(child -> preOrderFrom(child, getChildren, seen)));
  }

  private static <T> Stream<T> postOrderFrom(
      Deque<T> stack,
      Function<? super T, ? extends Stream<? extends T>> getChildren,
      Set<T> seen) {
    return whileNotEmpty(stack)
        .map(Deque::pop)
        .flatMap(seed ->
            Stream.concat(
                getChildren.apply(seed)
                    .peek(Objects::requireNonNull)
                    .filter(seen::add)
                    .peek(stack::push)
                    .flatMap(c -> postOrderFrom(stack, getChildren, seen)),
                Stream.of(seed)));
  }

  public static <T> Stream<T> preOrderFrom(
      Stream<? extends T> initial,
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    requireNonNull(initial);
    requireNonNull(getChildren);
    Deque<Stream<? extends T>> stack = new ArrayDeque<>();
    Set<T> seen = new HashSet<>();
    stack.push(initial);
    return whileNotEmpty(stack)
        .map(Deque::pop)
        .peek(seeds -> seeds.map(getChildren).peek(Objects::requireNonNull).forEach(stack::push))
        .flatMap(seeds -> seeds.filter(seen::add));
  }
}

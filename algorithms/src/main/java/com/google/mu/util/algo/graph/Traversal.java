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

import static com.google.mu.util.stream.MoreStreams.flatten;
import static com.google.mu.util.stream.MoreStreams.generate;
import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implements generic graph traversal algorithms ({@link #preOrderFrom pre-order},
 * and {@link #postOrderFrom post-order}).
 *
 * @since 3.9
 */
public abstract class Traversal<T> {
  private final Set<T> seen = new HashSet<>();

  Traversal() {}

  /**
   * Starts from {@code initial} and traverse depth first in pre-order by
   * using {@code getSuccessors} function iteratively.
   */
  public static <T> Stream<T> preOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> getSuccessors) {
    requireNonNull(getSuccessors);
    Queue<Stream<? extends T>> queue = new ArrayDeque<>();
    return new Traversal<T>() {
      @Override Stream<T> traverse(T node) {
        queue.add(Stream.of(node));
        return whileNotEmpty(queue)
            .map(Queue::remove)
            .flatMap(nodes -> nodes.peek(this::enqueueChildren));
      }

      private void enqueueChildren(T node) {
        queue.add(flatten(getSuccessors.apply(node).map(this::startingFrom)));
      }
    }.startingFrom(initial);
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order by
   * using {@code getSuccessors} function iteratively.
   */
  public static <T> Stream<T> postOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> getSuccessors) {
    requireNonNull(getSuccessors);
    return new Traversal<T>() {
      @Override Stream<T> traverse(T node) {
        return Stream.concat(
            flatten(getSuccessors.apply(node).map(this::startingFrom)),
            Stream.of(node));
      }
    }.startingFrom(initial);
  }

  /**
   * Starts from {@code initial} and traverse breadth first by using {@code getSuccessors}
   * function iteratively.
   */
  public static <T> Stream<T> breadthFirstFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> getSuccessors) {
    requireNonNull(initial);
    requireNonNull(getSuccessors);
    Set<T> seen = new HashSet<>();
    seen.add(initial);
    return generate(
        initial,
        n -> getSuccessors.apply(n).peek(Objects::requireNonNull).filter(seen::add));
  }

  final Stream<T> startingFrom(T node) {
    return seen.add(requireNonNull(node)) ? traverse(node) : Stream.empty();
  }

  abstract Stream<T> traverse(T node);
}

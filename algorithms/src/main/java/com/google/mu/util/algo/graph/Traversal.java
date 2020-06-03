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
import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Queue;
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
public abstract class Traversal<T> {
  private final Set<T> seen = new HashSet<>();

  Traversal() {}

  /**
   * Starts from {@code initial} and traverse depth first in pre-order by
   * using {@code getChildren} function iteratively.
   */
  public static <T> Stream<T> preOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> getChildren) {
    return new PreOrder<>(getChildren).startingFrom(requireNonNull(initial));
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order by
   * using {@code getChildren} function iteratively.
   */
  public static <T> Stream<T> postOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> getChildren) {
    return new PostOrder<>(getChildren).startingFrom(initial);
  }

  final Stream<T> startingFrom(T node) {
    requireNonNull(node);
    if (!seen.add(node)) {
      return Stream.empty();
    }
    return traverse(node);
  }

  abstract Stream<T> traverse(T node);

  private static final class PreOrder<T> extends Traversal<T> {
    private final Queue<Stream<? extends T>> queue = new ArrayDeque<>();
    private final Function<? super T, ? extends Stream<? extends T>> getChildren;

    PreOrder(Function<? super T, ? extends Stream<? extends T>> getChildren) {
      this.getChildren = requireNonNull(getChildren);
    }

    @Override Stream<T> traverse(T node) {
      queue.add(Stream.of(node));
      return whileNotEmpty(queue)
          .map(Queue::remove)
          .flatMap(nodes -> nodes.peek(this::enqueueChildren));
    }

    private void enqueueChildren(T node) {
      queue.add(flatten(getChildren.apply(node).map(this::startingFrom)));
    }
  }

  private static final class PostOrder<T> extends Traversal<T> {
    private final Deque<T> stack = new ArrayDeque<>();
    private final Function<? super T, ? extends Stream<? extends T>> getChildren;

    PostOrder(Function<? super T, ? extends Stream<? extends T>> getChildren) {
      this.getChildren = requireNonNull(getChildren);
    }

    @Override Stream<T> traverse(T node) {
      stack.push(node);
      return whileNotEmpty(stack).map(Deque::pop).flatMap(this::postOrder);
    }

    private Stream<T> postOrder(T node) {
      return Stream.concat(
          flatten(getChildren.apply(node).map(this::startingFrom)), Stream.of(node));
    }
  }
}

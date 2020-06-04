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
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implements generic graph traversal algorithms ({@link #preOrderFrom pre-order},
 * and {@link #postOrderFrom post-order}).
 *
 * <p>None of these streams are safe to run in parallel.
 *
 * @since 3.9
 */
public final class Traversal {
  /**
   * Starts from {@code initial} and traverse depth first in pre-order by
   * using {@code findSuccessors} function iteratively.
   */
  public static <T> Stream<T> preOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    return new PreOrder<>(findSuccessors).startingFrom(requireNonNull(initial));
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order by
   * using {@code findSuccessors} function iteratively.
   */
  public static <T> Stream<T> postOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    return new PostOrder<>(findSuccessors).startingFrom(requireNonNull(initial));
  }

  /**
   * Starts from {@code initial} and traverse breadth first by using {@code findSuccessors}
   * function iteratively.
   */
  public static <T> Stream<T> breadthFirstFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    requireNonNull(initial);
    requireNonNull(findSuccessors);
    Set<T> seen = new HashSet<>();
    seen.add(initial);
    return generate(
        initial,
        n -> findSuccessors.apply(n).peek(Objects::requireNonNull).filter(seen::add));
  }

  private static final class PreOrder<T> {
    private final Queue<Stream<? extends T>> queue = new ArrayDeque<>();
    private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;
    private final Set<T> seen = new HashSet<>();

    private PreOrder(Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
      this.findSuccessors = requireNonNull(findSuccessors);
    }

    Stream<T> startingFrom(T node) {
      return seen.add(requireNonNull(node)) ? traverse(node) : null;
    }

    private Stream<T> traverse(T node) {
      queue.add(Stream.of(node));
      return whileNotEmpty(queue)
          .map(Queue::remove)
          .flatMap(nodes -> nodes.peek(this::enqueueSuccessors));
    }

    private void enqueueSuccessors(T node) {
      queue.add(findSuccessors.apply(node).flatMap(this::startingFrom));
    }
  }

  private static final class PostOrder<T> implements Consumer<T> {
    private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;
    private final Deque<Family> stack = new ArrayDeque<>();
    private final Set<T> seen = new HashSet<>();
    private T advancedResult;

    PostOrder(Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
      this.findSuccessors = requireNonNull(findSuccessors);
    }

    Stream<T> startingFrom(T node) {
      stack.push(new Family(node));
      seen.add(node);
      return whileNotEmpty(stack)
          .map(Deque::pop)
          .map(Family::removeFirst);
    }

    @Override public void accept(T value) {
      this.advancedResult = requireNonNull(value);
    }

    private final class Family {
      private final T head;
      private final Spliterator<? extends T> successors;

      Family(T head) {
        this.head = head;
        this.successors = findSuccessors.apply(head).spliterator();
      }

      T removeFirst() {
        for (Family family = this;;) {
          if (family.successors.tryAdvance(PostOrder.this)) {
            if (seen.add(advancedResult)) {
              stack.push(family);
              family = new Family(advancedResult);
            }
          } else {
            return family.head;
          }
        }
      }
    }
  }

  private Traversal() {}
}

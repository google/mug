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

public class Traversal<T> {
  private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;

  Traversal(Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    this.findSuccessors = requireNonNull(findSuccessors);
  }

  /**
   * Returns a {@code Traversal} object assuming tree structure (no cycles), using {@code
   * getChildren} to find children of any given tree node.
   *
   * <p>The returned object is idempotent, stateless and immutable as long as {@code getChildren} is
   * idempotent, stateless and immutable.
   */
  public static <T> Traversal<T> forTree(
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    return new Traversal<T>(getChildren);
  }

  /**
   * Returns a {@code Traversal} object assuming graph structure (with cycles), using {@code
   * findSuccessors} to find successor nodes of any given graph node.
   *
   * <p>The returned object remembers which nodes have been traversed, thus if you call for example
   * {@link #preOrderFrom} again, already visited nodes will be skipped. This is useful if you need
   * to imperatively and dynamically decide which node to traverse. If you'd rather re-traverse
   * everything, recreate the {@code Traversal} object again.
   *
   * <p>Because the {@code Traversal} object keeps memory of traversal history, the memory usage is
   * linear to the number of traversed nodes.
   */
  public static <T> Traversal<T> forGraph(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    Set<T> seen = new HashSet<>();
    return new Traversal<T>(findSuccessors) {
      @Override boolean visit(T node) {
        return seen.add(node);
      }
    };
  }

  /**
   * Starts from {@code initial} and traverse depth first in pre-order by using {@code
   * findSuccessors} function iteratively.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> preOrderFrom(T initial) {
    return preOrderFrom(nonNullStream(initial));
  }

  /**
   * Starts from {@code initials} and traverse depth first in pre-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> preOrderFrom(Stream<? extends T> initials) {
    return new DepthFirst().preOrder(initials.spliterator());
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite breadth. The stream can still
   * be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when it traversing through a node with infinite
   * depth.
   */
  public final Stream<T> postOrderFrom(T initial) {
    return postOrderFrom(nonNullStream(initial));
  }

  /**
   * Starts from {@code initials} and traverse depth first in post-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite breadth. The stream can still
   * be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when it traversing through a node with infinite
   * depth.
   */
  public final Stream<T> postOrderFrom(Stream<? extends T> initials) {
    return new DepthFirst().postOrder(initials.spliterator());
  }

  /**
   * Starts from {@code initial} and traverse in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> breadthFirstFrom(T initial) {
    return breadthFirstFrom(nonNullStream(initial));
  }

  /**
   * Starts from {@code initials} and traverse in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> breadthFirstFrom(Stream<? extends T> initials) {
    Queue<Stream<? extends T>> queue = new ArrayDeque<>();
    queue.add(initials);
    return whileNotEmpty(queue)
        .map(Queue::remove)
        .flatMap(
            seeds ->
                seeds
                    .peek(Objects::requireNonNull)
                    .filter(this::visit)
                    .peek(
                        v -> {
                          Stream<? extends T> successors = findSuccessors.apply(v);
                          if (successors != null) {
                            queue.add(successors);
                          }
                        }));
  }

  /** Is this node okay to visit? */
  boolean visit(T node) {
    return true;
  }

  private final class DepthFirst implements Consumer<T> {
    private T advancedResult;

    Stream<T> preOrder(Spliterator<? extends T> initials) {
      Deque<Spliterator<? extends T>> stack = new ArrayDeque<>();
      stack.add(initials);
      return whileNotEmpty(stack).map(this::removeInPreOrder).filter(n -> n != null);
    }

    private T removeInPreOrder(Deque<Spliterator<? extends T>> stack) {
      while (!stack.isEmpty()) {
        Spliterator<? extends T> top = stack.getFirst();
        while (top.tryAdvance(this)) {
          T next = advancedResult;
          if (!visit(next)) {
            continue;
          }
          Stream<? extends T> successors = findSuccessors.apply(next);
          if (successors != null) {
            stack.push(successors.spliterator());
          }
          return next;
        }
        stack.pop();
      }
      return null; // no more element
    }

    Stream<T> postOrder(Spliterator<? extends T> initials) {
      Deque<Node> stack = new ArrayDeque<>();
      stack.push(new Node(initials));
      return whileNotEmpty(stack).map(this::removeInPostOrder).filter(n -> n != null);
    }

    private T removeInPostOrder(Deque<Node> stack) {
      for (Node node = stack.getFirst(); ; ) {
        T next = node.next();
        if (next == null) {
          stack.pop();
          return node.head;
        } else {
          node = new Node(next);
          stack.push(node);
        }
      }
    }

    @Override public void accept(T value) {
      this.advancedResult = requireNonNull(value);
    }

    private final class Node {
      final T head;
      private Spliterator<? extends T> successors;

      Node(T head) {
        this.head = head;
      }

      Node(Spliterator<? extends T> initials) {
        // special sentinel to be filtered.
        // Because successors is non-null so we'll never call findSuccessors(head).
        this.head = null;
        this.successors = initials;
      }

      T next() {
        if (successors == null) {
          Stream<? extends T> children = findSuccessors.apply(head);
          if (children == null) {
            return null;
          }
          successors = children.spliterator();
        }
        while (successors.tryAdvance(DepthFirst.this)) {
          if (visit(advancedResult)) {
            return advancedResult;
          }
        }
        return null;
      }
    }
  }

  private static <T> Stream<T> nonNullStream(T value) {
    return Stream.of(requireNonNull(value));
  }
}

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

import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implements generic graph and tree traversal algorithms ({@link #preOrderFrom pre-order},
 * {@link #postOrderFrom post-order} and {@link #breadthFirstFrom breadth-first}) as lazily
 * evaluated streams, allowing infinite-size graphs.
 *
 * <p>None of these streams are safe to run in parallel.
 *
 * @since 3.9
 */
public class Walker<T> {
  private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;

  Walker(Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    this.findSuccessors = requireNonNull(findSuccessors);
  }

  /**
   * Returns a {@code Traversal} object assuming tree structure (no cycles), using {@code
   * getChildren} to find children of any given tree node.
   *
   * <p>The returned object is idempotent, stateless and immutable as long as {@code getChildren} is
   * idempotent, stateless and immutable.
   */
  public static <T> Walker<T> forTree(
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    return new Walker<T>(getChildren);
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
  public static <T> Walker<T> forGraph(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    Set<T> traversed = new HashSet<>();
    return new Walker<T>(findSuccessors) {
      @Override boolean visit(T node) {
        return traversed.add(node);
      }
    };
  }

  /**
   * Starts from {@code initials} and traverse depth first in pre-order by using {@code
   * findSuccessors} function iteratively.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  @SafeVarargs
  public final Stream<T> preOrderFrom(T... initials) {
    return preOrderFrom(nonNullStream(initials));
  }

  /**
   * Starts from {@code initials} and traverse depth first in pre-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> preOrderFrom(Stream<? extends T> initials) {
    return new Traversal().preOrder(initials.spliterator());
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
  @SafeVarargs
  public final Stream<T> postOrderFrom(T... initials) {
    return postOrderFrom(nonNullStream(initials));
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
    return new Traversal().postOrder(initials.spliterator());
  }

  /**
   * Starts from {@code initials} and traverse in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  @SafeVarargs
  public final Stream<T> breadthFirstFrom(T... initials) {
    return breadthFirstFrom(nonNullStream(initials));
  }

  /**
   * Starts from {@code initials} and traverse in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> breadthFirstFrom(Stream<? extends T> initials) {
    return new Traversal().breadthFirst(initials.spliterator());
  }

  /** Is this node okay to visit? */
  boolean visit(T node) {
    return true;
  }

  private final class Traversal implements Consumer<T> {
    private T visited;

    @Override public void accept(T value) {
      this.visited = requireNonNull(value);
    }

    Stream<T> breadthFirst(Spliterator<? extends T> initials) {
      return topDown(initials, Queue::add);
    }

    Stream<T> preOrder(Spliterator<? extends T> initials) {
      return topDown(initials, Deque::push);
    }

    Stream<T> postOrder(Spliterator<? extends T> initials) {
      Deque<PostOrderNode> stack = new ArrayDeque<>();
      stack.push(new PostOrderNode(initials));
      return whileNotEmpty(stack).map(this::removeFromBottom).filter(n -> n != null);
    }

    /** Consuming nodes from top to bottom, for both depth-first pre-order and breadth-first. */
    private Stream<T> topDown(
        Spliterator<? extends T> initials, InsertionOrder nodeInsertionOrder) {
      Deque<Spliterator<? extends T>> deque = new ArrayDeque<>();
      nodeInsertionOrder.insertInto(deque, initials);
      return whileNotEmpty(deque)
          .map(d -> removeFromTop(d, nodeInsertionOrder))
          .filter(n -> n != null);
    }

    private T removeFromTop(
        Deque<Spliterator<? extends T>> deque, InsertionOrder successorInsertionOrder) {
      do {
        if (visitNext(deque.getFirst())) {
          T next = visited;
          Stream<? extends T> successors = findSuccessors.apply(next);
          if (successors != null) {
            successorInsertionOrder.insertInto(deque, successors.spliterator());
          }
          return next;
        }
        deque.removeFirst();
      } while (!deque.isEmpty());
      return null; // no more element
    }

    private T removeFromBottom(Deque<PostOrderNode> stack) {
      for (PostOrderNode node = stack.getFirst(); ; ) {
        T next = node.next();
        if (next == null) {
          stack.pop();
          return node.head;
        } else {
          node = new PostOrderNode(next);
          stack.push(node);
        }
      }
    }

    private boolean visitNext(Spliterator<? extends T> spliterator) {
      while (spliterator.tryAdvance(this)) {
        if (visit(visited)) {
          return true;
        }
      }
      return false;
    }

    private final class PostOrderNode {
      final T head;
      private Spliterator<? extends T> successors;

      PostOrderNode(T head) {
        this.head = head;
      }

      PostOrderNode(Spliterator<? extends T> initials) {
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
        return visitNext(successors) ? visited : null;
      }
    }
  }

  @SafeVarargs
  private static <T> Stream<T> nonNullStream(T... values) {
    Stream.Builder<T> builder = Stream.builder();
    for (T value : values) {
      builder.add(requireNonNull(value));
    }
    return builder.build();
  }

  private interface InsertionOrder {
    <T> void insertInto(Deque<T> deque, T value);
  }
}

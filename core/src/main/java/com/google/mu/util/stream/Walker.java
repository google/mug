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
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
public final class Walker<T> {
  private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;
  private final Predicate<? super T> tracker;

  private Walker(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors,
      Predicate<? super T> tracker) {
    this.findSuccessors = requireNonNull(findSuccessors);
    this.tracker = requireNonNull(tracker);
  }

  /**
   * Returns a {@code Traversal} object assuming tree structure (no cycles), using {@code
   * getChildren} to find children of any given tree node.
   *
   * <p>The returned object is idempotent, stateless and immutable as long as {@code getChildren} is
   * idempotent, stateless and immutable.
   */
  public static <T> Walker<T> newTreeWalker(
      Function<? super T, ? extends Stream<? extends T>> getChildren) {
    return newWalker(getChildren, n -> true);
  }

  /**
   * Returns a {@code Traversal} object assuming graph structure (with cycles), using {@code
   * findSuccessors} to find successor nodes of any given graph node.
   *
   * <p>The returned object remembers which nodes have been traversed, thus if you call for example
   * {@link #preOrderFrom} again, already visited nodes will be skipped. This is useful if you need
   * to imperatively and dynamically decide which node to traverse. For example, the SHIELD and
   * Avengers may need to collaborately raid a building from multiple entry points:
   *
   * <pre>{@code
   * Walker<Room> walker = Walker.newGraphWalker(buildingMap);
   * Stream<Room> shield = walker.preOrderFrom(roof);
   * Stream<Room> avengers = walker.breadthFirstFrom(mainEntrance);
   * // Now the two teams collaborate while raiding, no room is traversed twice...
   * }</pre>
   *
   * In the normal case though, you'd likely always want to start from the beginning, in which case,
   * just recreate the {@code Walker} object.
   *
   * <p>Because the {@code Traversal} object keeps memory of traversal history, the memory usage is
   * linear to the number of traversed nodes.
   */
  public static <T> Walker<T> newGraphWalker(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    return newWalker(findSuccessors, new HashSet<>()::add);
  }

  /**
   * Similar to {@link #newGraphWalker(Function)}, returns a {@code Walker} that can be used to
   * traverse a graph of nodes. {@code tracker} is used to track every node being traversed. When
   * {@code Walker} is about to traverse a node, {@code tracker.test(node)} will be called and the
   * node will be skipped if false is returned.
   *
   * <p>This is useful for custom node tracking. For example, the caller could use a {@link
   * java.util.TreeSet} or some {@code EquivalenceSet} to compare nodes using custom equality or
   * equivalence; or, use a {@link java.util.ConcurrentHashMap} if multiple threads need to walk the
   * same graph concurrently and collaboratively:
   *
   * <pre>{@code
   * Walker<Room> concurrentWalker =
   *     Walker.newWalker(buildingMap, ConcurrentHashMap.newKeySet()::add);
   *
   * // thread 1:
   * Stream<Room> shield = concurrentWalker.preOrderFrom(roof);
   * // iterate through rooms raided by the SHIELD agents.
   *
   * // thread 2:
   * Stream<Room> avengers = concurrentWalker.breadthFirstFrom(mainEntrance);
   * // iterate through rooms raided by Avengers.
   * }</pre>
   */
  public static <T> Walker<T> newWalker(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors,
      Predicate<? super T> tracker) {
    return new Walker<>(findSuccessors, tracker);
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
      Deque<PostOrderNode<T>> stack = new ArrayDeque<>();
      stack.push(new PostOrderNode<>(initials));
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

    private T removeFromBottom(Deque<PostOrderNode<T>> stack) {
      for (PostOrderNode<T> node = stack.getFirst(); ; ) {
        T next = visitNextInPostOrder(node);
        if (next == null) {
          stack.pop();
          return node.head;
        } else {
          node = new PostOrderNode<>(next);
          stack.push(node);
        }
      }
    }

    private T visitNextInPostOrder(PostOrderNode<T> node) {
      if (node.successors == null) {
        Stream<? extends T> children = findSuccessors.apply(node.head);
        if (children == null) {
          return null;
        }
        node.successors = children.spliterator();
      }
      return visitNext(node.successors) ? visited : null;
    }

    private boolean visitNext(Spliterator<? extends T> spliterator) {
      while (spliterator.tryAdvance(this)) {
        if (tracker.test(visited)) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class PostOrderNode<T> {
    final T head;
    Spliterator<? extends T> successors;

    PostOrderNode(T head) {
      this.head = head;
    }

    PostOrderNode(Spliterator<? extends T> initials) {
      // special sentinel to be filtered.
      // Because successors is non-null so we'll never call findSuccessors(head).
      this.head = null;
      this.successors = initials;
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

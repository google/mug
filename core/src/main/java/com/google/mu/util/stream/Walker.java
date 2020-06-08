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

import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
  private final Supplier<Traversal<T>> newTraversal;

  Walker(Supplier<Traversal<T>> newTraversal) {
    this.newTraversal = newTraversal;
  }

  /**
   * Returns a {@code Walker} to walk the tree structure (no cycles) as observed by the {@code
   * findChildren} function, which finds children of any given tree node.
   *
   * <p>The returned object is idempotent, stateless and immutable as long as {@code findChildren} is
   * idempotent, stateless and immutable.
   *
   * <p>WARNING: the returned {@code Walker} can walk in cycles if {@code findChildren} turns out
   * cyclic (like, any undirected graph).
   *
   * @param findChildren Function to get the child nodes for a given node.
   *        No children if empty stream or null is returned,
   */
  public static <T> Walker<T> inTree(
      Function<? super T, ? extends Stream<? extends T>> findChildren) {
    return inGraph(findChildren, n -> true);
  }

  /**
   * Returns a {@code Walker} to walk the graph structure (possibly with cycles) as observed by
   * the {@code findSuccessors} function, which finds successors of any given graph node.
   *
<<<<<<< HEAD
   * <pre>{@code
   * Walker<Room> walker = Walker.newGraphWalker(buildingMap);
   * Stream<Room> shield = walker.preOrderFrom(roof);
   * Stream<Room> avengers = walker.breadthFirstFrom(mainEntrance);
   *
   * // Now the two teams collaborate while raiding, no room is traversed twice...
   * BiStream.zip(shield, avengers)
   *     .forEachOrdered((raidedByShield, raidedByAvengers) -> ...);
   * }</pre>
   *
   * In the normal case though, you'd likely always want to start clean, in which case,
   * just recreate the {@code Walker} object.
   *
   * <p>Because the {@code Traversal} object keeps memory of traversal history, the memory usage is
=======
   * <p>Because the traversal needs to remember which node(s) have been traversed, memory usage is
>>>>>>> master
   * linear to the number of traversed nodes.
   *
   * @param findSuccessors Function to get the successor nodes for a given node.
   *        No successor if empty stream or null is returned,
   */
  public static <T> Walker<T> inGraph(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    requireNonNull(findSuccessors);
    return new Walker<>(() -> new Traversal<>(findSuccessors, new HashSet<>()::add));
  }

  /**
   * Similar to {@link #inGraph(Function)}, returns a {@code Walker} that can be used to
   * traverse a graph of nodes. {@code tracker} is used to track every node being traversed. When
   * {@code Walker} is about to traverse a node, {@code tracker.test(node)} will be called and the
   * node will be skipped if false is returned.
   *
   * <p>This is useful for custom node tracking. For example, the caller could use a {@link
   * java.util.TreeSet} or some functional equivalence to compare nodes using custom equality or
   * equivalence; or, use a {@link java.util.ConcurrentHashMap} if multiple threads need to walk the
   * same graph concurrently and collaboratively:
   *
   * <pre>{@code
   * Walker<Room> concurrentWalker =
   *     Walker.inGraph(buildingMap, ConcurrentHashMap.newKeySet()::add);
   *
   * // thread 1:
   * Stream<Room> shield = concurrentWalker.preOrderFrom(roof);
   * shield.forEachOrdered(room -> ...);
   *
   * // thread 2:
   * Stream<Room> avengers = concurrentWalker.breadthFirstFrom(mainEntrance);
   * avengers.forEachOrdered(room -> ...);
   * }</pre>
   *
   * <p>In the case of walking a very large graph with more nodes than can fit in memory, it's
   * conceivable to use {@link com.google.common.hash.BloomFilter#put Bloom filter} to track visited
   * nodes, as long as you are okay with probabilistically missing a fraction of the graph nodes due
   * to Bloom filter's inherent false-positive rates. Because Bloom filters have zero
   * false-negatives, it's guaranteed that the walker will never walk in cycles.
   *
   * @param findSuccessors Function to get the successor nodes for a given node.
   *        No successor if empty stream or null is returned,
   * @param tracker Tracks each node being visited during traversal. Returns false if the node
   *        should be skipped for traversal (for example because it has already been traversed).
   *        Despite being a {@link Predicate}, the tracker typically carries side-effects like
   *        storing the tracked node in a set ({@code set::add} will do).
   */
  public static <T> Walker<T> inGraph(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors,
      Predicate<? super T> tracker) {
    requireNonNull(findSuccessors);
    requireNonNull(tracker);
    return new Walker<>(() -> new Traversal<>(findSuccessors, tracker));
  }

  /**
   * Starts from {@code startNodes} and walks depth first in pre-order by using {@code
   * findSuccessors} function iteratively.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  @SafeVarargs
  public final Stream<T> preOrderFrom(T... startNodes) {
    return newTraversal.get().preOrder(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks depth first in pre-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> preOrderFrom(Iterable<? extends T> startNodes) {
    return newTraversal.get().preOrder(startNodes);
  }

  /**
   * Starts from {@code startNodes} and walks depth first in post-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite breadth. The stream can still
   * be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when it traversing through a node with infinite
   * depth.
   */
  @SafeVarargs
  public final Stream<T> postOrderFrom(T... startNodes) {
    return newTraversal.get().postOrder(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks depth first in post-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite breadth. The stream can still
   * be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when it traversing through a node with infinite
   * depth.
   */
  public final Stream<T> postOrderFrom(Iterable<? extends T> startNodes) {
    return newTraversal.get().postOrder(startNodes);
  }

  /**
   * Starts from {@code startNodes} and walks in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  @SafeVarargs
  public final Stream<T> breadthFirstFrom(T... startNodes) {
    return newTraversal.get().breadthFirst(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<T> breadthFirstFrom(Iterable<? extends T> startNodes) {
    return newTraversal.get().breadthFirst(startNodes);
  }

  /**
   * Floyd's <a href="https://en.wikipedia.org/wiki/Cycle_detection#Floyd's_Tortoise_and_Hare">
   * Tortoise and Hare</a> algorithm. Detects whether the graph structure as observed by the
   * {@code findSuccessors} function has cycles, by walking from {@code startNode}.
   *
   * <p>This method will hang if the given graph is infinite without cycle (the sequence of natural
   * numbers for instance).
   * @param findSuccessors The function to find successors of any given node. This function is
   *        expected to be deterministic and idempotent.
   * @param startNode the node to start walking the graph.
   *
   * @return an infinite stream of the nodes forming a detected cycle if there is any, or else
   *         {@link Optional#empty}.
   */
  public static <T> Optional<Stream<T>> detectCycleInGraph(
      Function<? super T, ? extends Stream<? extends T>> findSuccessors, T startNode) {
    Walker<T> walker = inTree(findSuccessors);
    Stream<T> slower = walker.preOrderFrom(startNode);
    Stream<T> faster = BiStream.zip(indexesFrom(0), walker.preOrderFrom(startNode))
        .filterKeys(i -> i % 2 == 1)
        .values();
    return BiStream.zip(slower, faster)
        .filter(Object::equals)
        .keys()
        .findFirst()
        .map(walker::preOrderFrom);
  }

  private static final class Traversal<T> implements Consumer<T> {
    private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;
    private final Predicate<? super T> tracker;
    private final Deque<Spliterator<? extends T>> horizon = new ArrayDeque<>();
    private T visited;

    Traversal(
        Function<? super T, ? extends Stream<? extends T>> findSuccessors, Predicate<? super T> tracker) {
      this.findSuccessors = findSuccessors;
      this.tracker = tracker;
    }

    @Override
    public void accept(T value) {
      this.visited = requireNonNull(value);
    }

    Stream<T> breadthFirst(Iterable<? extends T> startNodes) {
      horizon.add(startNodes.spliterator());
      return topDown(Queue::add);
    }

    Stream<T> preOrder(Iterable<? extends T> startNodes) {
      horizon.push(startNodes.spliterator());
      return topDown(Deque::push);
    }

    Stream<T> postOrder(Iterable<? extends T> startNodes) {
      horizon.push(startNodes.spliterator());
      Deque<T> roots = new ArrayDeque<>();
      return whileNotEmpty(horizon).map(h -> removeFromBottom(roots)).filter(Objects::nonNull);
    }

    private Stream<T> topDown(InsertionOrder order) {
      return whileNotEmpty(horizon).map(h -> removeFromTop(order)).filter(Objects::nonNull);
    }

    private T removeFromTop(InsertionOrder traversalOrder) {
      do {
        if (visitNext()) {
          T next = visited;
          Stream<? extends T> successors = findSuccessors.apply(next);
          if (successors != null) {
            traversalOrder.insertInto(horizon, successors.spliterator());
          }
          return next;
        }
      } while (!horizon.isEmpty());
      return null; // no more element
    }

    private T removeFromBottom(Deque<T> roots) {
      while (visitNext()) {
        T next = visited;
        Stream<? extends T> successors = findSuccessors.apply(next);
        if (successors == null) {
          return next;
        }
        horizon.push(successors.spliterator());
        roots.push(next);
      }
      return roots.pollFirst();
    }

    private boolean visitNext() {
      Spliterator<? extends T> top = horizon.getFirst();
      while (top.tryAdvance(this)) {
        if (tracker.test(visited)) {
          return true;
        }
      }
      horizon.removeFirst();
      return false;
    }
  }

  @SafeVarargs
  private static <T> List<T> nonNullList(T... values) {
    return Arrays.stream(values).peek(Objects::requireNonNull).collect(toList());
  }

  private interface InsertionOrder {
    <T> void insertInto(Deque<T> deque, T value);
  }
}

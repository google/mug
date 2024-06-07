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
package com.google.mu.util.graph;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Implements generic graph and tree traversal algorithms ({@link #preOrderFrom pre-order},
 * {@link #postOrderFrom post-order} and {@link #breadthFirstFrom breadth-first}) as lazily
 * evaluated streams, allowing infinite-size graphs.
 *
 * <p>The following example code explores all islands to find the treasure island:
 *
 * <pre>{@code
 * Optional<Island> treasureIsland =
 *     Walker.inGraph(Island::nearbyIslands)
 *         .preOrderFrom(homeIsland)
 *         .filter(Island::hasTreasure)
 *         .findFirst();
 * }</pre>
 *
 * <p>None of these streams are safe to run in parallel. Although, multiple threads could traverse
 * the same graph collaboratively by sharing a concurrent node tracker. See
 * {@link #inGraph(Function, Predicate) inGraph(findSuccessors, nodeTracker)} for details.
 *
 * <p>Tree nodes are not allowed to be null.
 *
 * <p>For binary trees, use {@link #inBinaryTree inBinaryTree(Tree::left, Tree::right)}.
 *
 * @since 4.0
 */
public abstract class Walker<N> {
  Walker() {}

  /**
   * Returns a {@code BinaryTreeWalker} for walking in the binary tree topology
   * as observed by {@code getLeft} and {@code getRight} functions. Both functions
   * return null to indicate that there is no left or right child.
   *
   * <p>It's guaranteed that for any given node, {@code getLeft} and {@code getRight}
   * are called lazily, only when the left or the right child is traversed. They are called at
   * most once for each node.
   *
   * @since 4.2
   */
  public static <N> BinaryTreeWalker<N> inBinaryTree(
      UnaryOperator<N> getLeft, UnaryOperator<N> getRight) {
    return new BinaryTreeWalker<>(getLeft, getRight);
  }

  /**
   * Returns a {@code Walker} to walk the tree topology (no cycles) as observed by the {@code
   * findChildren} function, which finds children of any given tree node.
   *
   * <p>{@code inTree()} is more efficient than {@link #inGraph inGraph()} because it doesn't need
   * to remember nodes that are already visited. On the other hand, the returned {@code Walker} can
   * walk in cycles if the {@code findChildren} function unexpectedly represents a cyclic graph.
   * If you need to guard against cycles just in case, you can use {@link
   * inGraph(Function, Predicate) inGraph()} with a custom node tracker to check for the critical
   * precondition:
   *
   * <pre>{@code
   * Set<N> visited = new HashSet<>();
   * Walker<N> walker = Walker.inGraph(successorFunction, n -> {
   *   checkArgument(visited.add(n), "Node with multiple parents: %s", n);
   *   return true;
   * });
   * }</pre>
   *
   * <p>The returned object is idempotent, stateless and immutable as long as {@code findChildren} is
   * idempotent, stateless and immutable.
   *
   * @param findChildren Function to get the child nodes for a given node.
   *        No children if empty stream or null is returned,
   */
  public static <N> Walker<N> inTree(
      Function<? super N, ? extends Stream<? extends N>> findChildren) {
    return inGraph(findChildren, n -> true);
  }

  /**
   * Returns a {@code Walker} to walk the graph topology (possibly with cycles) as observed by
   * the {@code findSuccessors} function, which finds successors of any given graph node.
   *
   * <p>Because the traversal needs to remember which node(s) have been traversed, memory usage is
   * linear to the number of traversed nodes.
   *
   * @param findSuccessors Function to get the successor nodes for a given node.
   *        No successor if empty stream or null is returned,
   */
  public static <N> GraphWalker<N> inGraph(
      Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
    requireNonNull(findSuccessors);
    return new GraphWalker<N>() {
      @Override Walk<N> start() {
        return new Walk<>(findSuccessors, new HashSet<>()::add);
      }
    };
  }

  /**
   * Similar to {@link #inGraph(Function)}, returns a {@code Walker} that can be used to
   * traverse a graph of nodes. {@code tracker} is used to track every node being traversed. When
   * {@code Walker} is about to traverse a node, {@code tracker.test(node)} will be called and the
   * node (together with its edges) will be skipped if false is returned.
   *
   * <p>This is useful for custom node tracking. For example, using a {@code ConcurrentHashMap},
   * multiple threads can traverse a large graph concurrently and collaboratively:
   *
   * <pre>{@code
   * Walker<Room> concurrentWalker =
   *     Walker.inGraph(buildingMap, ConcurrentHashMap.newKeySet()::add);
   *
   * // thread 1:
   * Stream<Room> shield = concurrentWalker.preOrderFrom(roof);
   * shield.forEachOrdered(room -> System.out.println("Raided by SHIELD from roof: " + room);
   *
   * // thread 2:
   * Stream<Room> avengers = concurrentWalker.breadthFirstFrom(mainEntrance);
   * avengers.forEachOrdered(room -> System.out.println("Raided by Avengers: " + room);
   * }</pre>
   *
   * <p>Or, nodes can be tracked by functional equivalence. What gives?
   * Imagine in a pirate treasure hunt, we start from an island and scavenge from island to island.
   * Considering the islands as nodes, we can use any traversal strategy. Say, we picked pre-order:
   *
   * <pre>{@code
   * Optional<Island> treasureIsland =
   *     Walker.inGraph(Island::nearbyIslands)
   *         .preOrderFrom(startIsland)
   *         .filter(Island::hasTreasure)
   *         .findFirst();
   * }</pre>
   *
   * That gives us the treasure island. But what if upon finding the treasure island, we also want
   * to make our own treasure map? It requires not just finding the island, but also recording
   * how we got there. To do this, we can start by defining a class that encodes the route:
   *
   * <pre>{@code
   * class Route {
   *   private final Island island;
   *   private final Route predecessor;
   *
   *   // End of the route.
   *   Island end() {
   *     return island;
   *   }
   *
   *   // Returns a new Route with this Route as the predecessor.
   *   Route extendTo(Island newIsland) {
   *     return new Route(newIsland, this);
   *   }
   *
   *   Stream<Route> nearbyRoutes() {
   *     return island.nearbyIslands().map(this::extendTo);
   *   }
   *
   *   List<Island> islands() {
   *     // follow the `predecessor` chain to return all islands along the route.
   *   }
   * }
   * }</pre>
   *
   * And then we can modify the treasure hunt code to walk through a stream of {@code Route}
   * objects in place of islands. A caveat is that Route doesn't define {@code equals} ---
   * even if it did, it'd be recursive and not what we need anyway (we care about unique islands,
   * not unique routes).
   *
   * <p>Long story short, the trick is to use functional equivalence so that the {@code Walker}
   * still knows which islands have already been searched:
   *
   * <pre>{@code
   * Set<Island> searched = new HashSet<>();
   * Optional<Route> treasureIslandRoute =
   *     Walker.inGraph(Route::nearbyRoutes, route -> searched.add(route.end()))
   *         .preOrderFrom(new Route(startIsland))
   *         .filter(route -> route.end().hasTreasure())
   *         .findFirst();
   * }</pre>
   *
   * <p>In the case of walking a very large graph with more nodes than can fit in memory, it's
   * conceivable to use {@code com.google.common.hash.BloomFilter#put BloomFilter} to track visited
   * nodes, as long as you are okay with probabilistically missing a fraction of the graph nodes due
   * to Bloom filter's inherent false-positive rates. Because Bloom filters have zero
   * false-negatives, it's guaranteed that the walker will never walk in cycles.
   *
   * @param findSuccessors Function to get the successor nodes for a given node.
   *        No successor if empty stream or null is returned,
   * @param tracker Tracks each node being visited during traversal. Returns false if the node
   *        and its edges should be skipped for traversal (for example because it has already been
   *        traversed). Despite being a {@link Predicate}, the tracker usually carries
   *        side-effects like storing the tracked nodes in a set ({@code set::add},
   *        {@code bloomFilter::put} etc. will do).
   */
  public static <N> GraphWalker<N> inGraph(
      Function<? super N, ? extends Stream<? extends N>> findSuccessors,
      Predicate<? super N> tracker) {
    requireNonNull(findSuccessors);
    requireNonNull(tracker);
    return new GraphWalker<N>() {
      @Override Walk<N> start() {
        return new Walk<>(findSuccessors, tracker);
      }
    };
  }

  /**
   * Starts from {@code startNodes} and walks depth first in pre-order.
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite depth or infinite
   * breadth, or both. The stream can still be short-circuited to consume a limited number of nodes
   * during traversal.
   */
  @SafeVarargs public final Stream<N> preOrderFrom(N... startNodes) {
    return preOrderFrom(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks depth first in pre-order.
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite depth or infinite
   * breadth, or both. The stream can still be short-circuited to consume a limited number of
   * nodes during traversal.
   */
  public abstract Stream<N> preOrderFrom(Iterable<? extends N> startNodes);

  /**
   * Starts from {@code startNodes} and walks depth first in post-order
   * (the reverse of a topological sort).
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite breadth. The stream
   * can still be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when traversing through a node with infinite depth.
   */
  @SafeVarargs public final Stream<N> postOrderFrom(N... startNodes) {
    return postOrderFrom(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks depth first in post-order
   * (the reverse of a topological sort).
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite breadth. The stream
   * can still be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when traversing through a node with infinite depth.
   */
  public abstract Stream<N> postOrderFrom(Iterable<? extends N> startNodes);

  /**
   * Starts from {@code startNodes} and walks in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite depth or infinite
   * breadth, or both. The stream can still be short-circuited to consume a limited number of
   * nodes during traversal.
   */
  @SafeVarargs public final Stream<N> breadthFirstFrom(N... startNodes) {
    return breadthFirstFrom(nonNullList(startNodes));
  }

  /**
   * Starts from {@code startNodes} and walks in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph or tree has infinite depth or infinite
   * breadth, or both. The stream can still be short-circuited to consume a limited number of
   * nodes during traversal.
   */
  public abstract Stream<N> breadthFirstFrom(Iterable<? extends N> startNodes);

  @SafeVarargs static <N> List<N> nonNullList(N... values) {
    return Arrays.stream(values).peek(Objects::requireNonNull).collect(toList());
  }
}

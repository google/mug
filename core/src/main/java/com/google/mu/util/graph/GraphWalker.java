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

import static com.google.mu.util.stream.MoreStreams.toListAndThen;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Walker for graph topology ({@link Walker#inGraph Walker.inGraph()}).
 *
 * <p>Besides {@link #preOrderFrom pre-order}, {@link #postOrderFrom post-order} and {@link
 * #breadthFirstFrom breadth-first} traversals, also supports {@link #topologicalOrderFrom
 * topologicalOrderFrom()} and {@link #detectCycleFrom detectCycleFrom()}.
 *
 * @param <N> the graph node type
 * @since 4.3
 */
public abstract class GraphWalker<N> extends Walker<N> {
  @Override public final Stream<N> preOrderFrom(Iterable<? extends N> startNodes) {
    return start().preOrder(startNodes);
  }

  @Override public final Stream<N> postOrderFrom(Iterable<? extends N> startNodes) {
    return start().postOrder(startNodes);
  }

  @Override public final Stream<N> breadthFirstFrom(Iterable<? extends N> startNodes) {
    return start().breadthFirst(startNodes);
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>In the following cyclic graph, if starting from node {@code a}, the detected cyclic path
   * will be: {@code a -> b -> c -> e -> b}, with {@code b -> c -> e -> b} being the cycle, and
   * {@code a -> b} the prefix path leading to the cycle.
   *
   * <pre>{@code
   * a -> b -> c -> d
   *      ^  /
   *      | /
   *      |/
   *      e
   * }</pre>
   *
   * <p>This method will hang if the given graph is infinite without cycle (the sequence of natural
   * numbers for instance).
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *         cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   * @since 4.3
   */
  @SafeVarargs public final Optional<Stream<N>> detectCycleFrom(N... startNodes) {
    return detectCycleFrom(nonNullList(startNodes));
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>In the following cyclic graph, if starting from node {@code a}, the detected cyclic path
   * will be: {@code a -> b -> c -> e -> b}, with {@code b -> c -> e -> b} being the cycle, and
   * {@code a -> b} the prefix path leading to the cycle.
   *
   * <pre>{@code
   * a -> b -> c -> d
   *      ^  /
   *      | /
   *      |/
   *      e
   * }</pre>
   *
   * <p>This method will hang if the given graph is infinite with no cycles (the sequence of natural
   * numbers for instance).
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *         cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   * @since 4.3
   */
  public final Optional<Stream<N>> detectCycleFrom(Iterable<? extends N> startNodes) {
    return start().detectCycle(startNodes);
  }

  /**
   * Fully traverses the graph by starting from {@code startNodes}, and returns an immutable list of
   * nodes in topological order.
   *
   * <p>Unlike the other {@code Walker} utilities, this method is not lazy:
   * it has to traverse the entire graph in order to figure out the topological order.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @throws CyclicGraphException if the graph has cycles.
   * @since 4.3
   */
  @SafeVarargs public final List<N> topologicalOrderFrom(N... startNodes) {
    return topologicalOrderFrom(nonNullList(startNodes));
  }

  /**
   * Fully traverses the graph by starting from {@code startNodes}, and returns an immutable list of
   * nodes in topological order.
   *
   * <p>Unlike the other {@code Walker} utilities, this method is not lazy:
   * it has to traverse the entire graph in order to figure out the topological order.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @throws CyclicGraphException if the graph has cycles.
   * @since 4.3
   */
  public final List<N> topologicalOrderFrom(Iterable<? extends N> startNodes) {
    return start().topologicalOrder(startNodes);
  }

  abstract Walk<N> start();

  static final class Walk<N> implements Consumer<N> {
    private final Function<? super N, ? extends Stream<? extends N>> findSuccessors;
    private final Predicate<? super N> tracker;
    private final Deque<Spliterator<? extends N>> horizon = new ArrayDeque<>();
    private N visited;

    Walk(
        Function<? super N, ? extends Stream<? extends N>> findSuccessors,
        Predicate<? super N> tracker) {
      this.findSuccessors = findSuccessors;
      this.tracker = tracker;
    }

    @Override public void accept(N value) {
      this.visited = requireNonNull(value);
    }

    Stream<N> breadthFirst(Iterable<? extends N> startNodes) {
      horizon.add(startNodes.spliterator());
      return topDown(Queue::add);
    }

    Stream<N> preOrder(Iterable<? extends N> startNodes) {
      horizon.push(startNodes.spliterator());
      return topDown(Deque::push);
    }

    Stream<N> postOrder(Iterable<? extends N> startNodes) {
      horizon.push(startNodes.spliterator());
      Deque<N> roots = new ArrayDeque<>();
      return whileNotNull(() -> {
        while (visitNext()) {
          N next = visited;
          Stream<? extends N> successors = findSuccessors.apply(next);
          if (successors == null) return next;
          horizon.push(successors.spliterator());
          roots.push(next);
        }
        return roots.poll();
      });
    }

    private Stream<N> topDown(InsertionOrder order) {
      return whileNotNull(() -> {
        do {
          if (visitNext()) {
            N next = visited;
            Stream<? extends N> successors = findSuccessors.apply(next);
            if (successors != null) order.insertInto(horizon, successors.spliterator());
            return next;
          }
        } while (!horizon.isEmpty());
        return null; // no more element
      });
    }

    private boolean visitNext() {
      Spliterator<? extends N> top = horizon.getFirst();
      while (top.tryAdvance(this)) {
        if (tracker.test(visited)) return true;
      }
      horizon.removeFirst();
      return false;
    }

    List<N> topologicalOrder(Iterable<? extends N> startNodes) {
      CycleTracker cycleDetector = new CycleTracker();
      return cycleDetector.startPostOrder(startNodes, n -> {
        throw new CyclicGraphException(
            cycleDetector.currentPath().collect(toListAndThen(l -> l.add(n))));
      }).collect(toListAndThen(Collections::reverse));
    }

    Optional<Stream<N>> detectCycle(Iterable<? extends N> startNodes) {
      AtomicReference<N> cyclic = new AtomicReference<>();
      CycleTracker detector = new CycleTracker();
      return detector.startPostOrder(startNodes, n -> cyclic.compareAndSet(null, n))
          .filter(n -> cyclic.get() != null)
          .findFirst()
          .map(last ->
              Stream.concat(detector.currentPath(), Stream.of(last, cyclic.getAndSet(null))));
    }

    private final class CycleTracker {
      private final LinkedHashSet<N> currentPath = new LinkedHashSet<>();

      Stream<N> startPostOrder(Iterable<? extends N> startNodes, Consumer<N> foundCycle) {
        Walk<N> walk = new Walk<>(
            findSuccessors,
            node -> {
              boolean newNode = tracker.test(node);
              if (newNode) {
                currentPath.add(node);
              } else if (currentPath.contains(node)) {
                foundCycle.accept(node);
              }
              return newNode;
            });
        return walk.postOrder(startNodes).peek(currentPath::remove);
      }

      Stream<N> currentPath() {
        return currentPath.stream();
      }
    }
  }
}

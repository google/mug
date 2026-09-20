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

import static com.google.mu.util.stream.MoreCollectors.toListAndThen;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static com.google.mu.util.stream.MoreStreams.withSideEffect;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Walker for graph topology (see {@link Walker#inGraph Walker.inGraph()}).
 *
 * <p>Besides {@link #preOrderFrom pre-order}, {@link #postOrderFrom post-order} and {@link
 * #breadthFirstFrom breadth-first} traversals, also supports {@link #topologicalOrderFrom
 * topologicalOrderFrom()}, {@link #detectCycleFrom detectCycleFrom()} and {@link
 * #stronglyConnectedComponentsFrom stronglyConnectedComponentsFrom()}.
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
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *     cycle, ending with nodes along a cyclic path. The last node will also be the starting point
   *     of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends with {@code
   *     A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   * @since 4.3
   */
  @SafeVarargs
  public final Optional<Stream<N>> detectCycleFrom(N... startNodes) {
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
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *     cycle, ending with nodes along a cyclic path. The last node will also be the starting point
   *     of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends with {@code
   *     A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   * @since 4.3
   */
  public final Optional<Stream<N>> detectCycleFrom(Iterable<? extends N> startNodes) {
    return start().detectCycle(startNodes);
  }

  /**
   * Fully traverses the graph by starting from {@code startNodes}, and returns an immutable list of
   * nodes in topological order.
   *
   * <p>Unlike the other {@code Walker} utilities, this method is not lazy: it has to traverse the
   * entire graph in order to figure out the topological order.
   *
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @throws CyclicGraphException if the graph has cycles.
   * @since 4.3
   */
  @SafeVarargs
  public final List<N> topologicalOrderFrom(N... startNodes) {
    return topologicalOrderFrom(nonNullList(startNodes));
  }

  /**
   * Fully traverses the graph by starting from {@code startNodes}, and returns an immutable list of
   * nodes in topological order.
   *
   * <p>Unlike the other {@code Walker} utilities, this method is not lazy: it has to traverse the
   * entire graph in order to figure out the topological order.
   *
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @throws CyclicGraphException if the graph has cycles.
   * @since 4.3
   */
  public final List<N> topologicalOrderFrom(Iterable<? extends N> startNodes) {
    return start().topologicalOrder(startNodes);
  }

  /**
   * Walks the graph by starting from {@code startNodes}, and returns a lazy stream of <a
   * href="https://en.wikipedia.org/wiki/Strongly_connected_component">strongly connected
   * components</a> found in the graph.
   *
   * <p>Implements the <a
   * href="https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">Tarjan
   * algorithm</a> in linear time ({@code O(V + E)}).
   *
   * <p>The strongly connected components (represented by the list of nodes in each component) are
   * returned in a lazy stream, in depth-first post order. If you need topological order from the
   * start nodes, convert it using:
   *
   * <pre>{@code
   * List<List<N>> components = Walker.inGraph(...)
   *     .stronglyConnectedComponentsFrom(...)
   *     .peek(Collections::reverse)                      // reverse order within each component
   *     .collect(toListAndThen(Collections::reverse));   // reverse order of the components
   * }</pre>
   *
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @since 4.4
   */
  @SafeVarargs
  public final Stream<List<N>> stronglyConnectedComponentsFrom(N... startNodes) {
    return stronglyConnectedComponentsFrom(nonNullList(startNodes));
  }

  /**
   * Walks the graph by starting from {@code startNodes}, and returns a lazy stream of <a
   * href="https://en.wikipedia.org/wiki/Strongly_connected_component">strongly connected
   * components</a> found in the graph.
   *
   * <p>Implements the <a
   * href="https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">Tarjan
   * algorithm</a> in linear time ({@code O(V + E)}).
   *
   * <p>The strongly connected components (represented by the list of nodes in each component) are
   * returned in a lazy stream, in depth-first post order. If you need topological order from the
   * start nodes, convert it using:
   *
   * <pre>{@code
   * List<List<N>> components = Walker.inGraph(...)
   *     .stronglyConnectedComponentsFrom(...)
   *     .peek(Collections::reverse)                      // reverse order within each component
   *     .collect(toListAndThen(Collections::reverse));   // reverse order of the components
   * }</pre>
   *
   * <p>Cycles are detected by node {@link Object#equals equals()}; a tracker that deduplicates by
   * another key does not change that.
   *
   * @param startNodes the entry point nodes to start traversing the graph.
   * @since 4.4
   */
  public final Stream<List<N>> stronglyConnectedComponentsFrom(Iterable<? extends N> startNodes) {
    return start().new StronglyConnected().componentsFrom(startNodes);
  }

  abstract Walk<N> start();

  GraphWalker() {}

  static final class Walk<N> implements Consumer<N> {
    private final Function<? super N, ? extends Stream<? extends N>> findSuccessors;
    private final Predicate<? super N> tracker;
    private final Deque<Spliterator<? extends N>> horizon = new ArrayDeque<>();
    private final Deque<Stream<?>> openStreams = new ArrayDeque<>();
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
      addHorizon(Queue::add, StreamSupport.stream(startNodes.spliterator(), false));
      return topDown(Queue::add);
    }

    Stream<N> preOrder(Iterable<? extends N> startNodes) {
      addHorizon(Deque::push, StreamSupport.stream(startNodes.spliterator(), false));
      return topDown(Deque::push);
    }

    Stream<N> postOrder(Iterable<? extends N> startNodes) {
      return postOrder(startNodes, new ArrayDeque<>());
    }

    private Stream<N> postOrder(Iterable<? extends N> startNodes, Deque<N> roots) {
      addHorizon(Deque::push, StreamSupport.stream(startNodes.spliterator(), false));
      return whileNotNull(() -> {
        while (visitNext()) {
          N next = visited;
          Stream<? extends N> successors = findSuccessors.apply(next);
          if (successors == null) return next;
          addHorizon(Deque::push, successors);
          roots.push(next);
        }
        return roots.poll();
      })
          .onClose(this::closeOpenStreams);
    }

    private Stream<N> topDown(InsertionOrder order) {
      return whileNotNull(() -> {
        while (!horizon.isEmpty()) {
          if (visitNext()) {
            N next = visited;
            addHorizon(order, findSuccessors.apply(next));
            return next;
          }
        }
        return null; // no more element
      })
          .onClose(this::closeOpenStreams);
    }

    private void addHorizon(InsertionOrder order, Stream<? extends N> stream) {
      if (stream != null) {
        order.insertInto(horizon, stream.spliterator());
        order.insertInto(openStreams, stream);
      }
    }

    private void closeOpenStreams() {
      for (Stream<?> stream = openStreams.pollFirst();
          stream != null;
          stream = openStreams.pollFirst()) {
        stream.close();
      }
    }

    private boolean visitNext() {
      Spliterator<? extends N> top = horizon.peekFirst();
      if (top == null) {
        return false;
      }
      while (top.tryAdvance(this)) {
        if (tracker.test(visited)) return true;
      }
      horizon.removeFirst();
      openStreams.removeFirst().close();
      return false;
    }

    List<N> topologicalOrder(Iterable<? extends N> startNodes) {
      CycleTracker cycleDetector = new CycleTracker();
      try (Stream<N> postOrder = cycleDetector.startPostOrder(
          startNodes,
          path -> {
            throw new CyclicGraphException(path);
          })) {
        return postOrder.collect(toListAndThen(Collections::reverse));
      }
    }

    Optional<Stream<N>> detectCycle(Iterable<? extends N> startNodes) {
      CycleTracker detector = new CycleTracker();
      try (Stream<N> postOrder = detector.startPostOrder(
          startNodes,
          path -> {
            throw new CycleDetected(path);
          })) {
        postOrder.forEach(n -> {});
        return Optional.empty();
      } catch (CycleDetected e) {
        @SuppressWarnings("unchecked")
        List<N> cycle = (List<N>) e.cycle;
        return Optional.of(cycle.stream());
      }
    }

    private static final class CycleDetected extends Error {
      final List<?> cycle;

      CycleDetected(List<?> cycle) {
        super(null, null, false, false);
        this.cycle = cycle;
      }
    }

    private final class CycleTracker {
      private final LinkedHashSet<N> currentPath = new LinkedHashSet<>();

      Stream<N> startPostOrder(Iterable<? extends N> startNodes, Consumer<List<N>> foundCycle) {
        Walk<N> walk = new Walk<>(
            findSuccessors,
            node -> {
              boolean newNode = tracker.test(node);
              if (newNode) {
                currentPath.add(node);
              } else if (currentPath.contains(node)) {
                List<N> cycle = new ArrayList<>(currentPath);
                cycle.add(node);
                foundCycle.accept(cycle);
              }
              return newNode;
            });
        return withSideEffect(walk.postOrder(startNodes), currentPath::remove);
      }
    }

    final class StronglyConnected {
      private long index;
      private final Deque<N> roots = new ArrayDeque<>();
      private final Map<N, Tarjan<N>> currentPath = new HashMap<>();
      private final Deque<Tarjan<N>> connected = new ArrayDeque<>();

      Stream<List<N>> componentsFrom(Iterable<? extends N> startNodes) {
        return new Walk<>(findSuccessors, this::track)
            .postOrder(startNodes, roots)
            .map(currentPath::get)
            .filter(Tarjan::isComponentRoot)
            .map(this::toConnectedComponent);
      }

      private boolean track(N node) {
        if (tracker.test(node)) {
          push(node);
          return true;
        } else {
          Tarjan<N> back = currentPath.get(node);
          if (back != null) top().uponBackEdge(back);
          return false;
        }
      }

      private Tarjan<N> top() {
        return currentPath.get(roots.peek());
      }

      private void push(N node) {
        Tarjan<N> indexed = new Tarjan<>(top(), node, ++index);
        currentPath.put(node, indexed);
        connected.push(indexed);
      }

      private List<N> toConnectedComponent(Tarjan<N> root) {
        List<N> list = new ArrayList<>();
        for (; ;) {
          Tarjan<N> node = connected.pop();
          currentPath.remove(node.payload);
          list.add(node.payload);
          if (node == root) {
            return list;
          }
        }
      }
    }
  }

  private static final class Tarjan<N> {
    final N payload;
    private final long index;
    private final Tarjan<N> parent;
    private long lowlink;

    Tarjan(Tarjan<N> parent, N payload, long index) {
      this.parent = parent;
      this.payload = payload;
      this.index = index;
      this.lowlink = index;
    }

    boolean isComponentRoot() {
      if (parent != null) {
        parent.lowlink = Math.min(parent.lowlink, lowlink);
      }
      return lowlink == index;
    }

    void uponBackEdge(Tarjan<N> back) {
      this.lowlink = Math.min(lowlink, back.index);
    }
  }
}

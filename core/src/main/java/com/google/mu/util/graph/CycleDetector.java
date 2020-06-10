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

import static com.google.mu.util.graph.Walker.nonNullList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility to detect cycles in graphs.
 *
 * @since 3.9
 */
public final class CycleDetector<N> {
  private final Function<? super N, ? extends Stream<? extends N>> findSuccessors;

  private CycleDetector(Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
    this.findSuccessors = requireNonNull(findSuccessors);
  }

  /**
   * Returns a {@code CycleDetector} for the graph topology as observed by the
   * {@code findSuccessors} function.
   */
  public static <N> CycleDetector<N> forGraph(
      Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
    return new CycleDetector<>(findSuccessors);
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>This method will hang if the given graph is infinite without cycle (the sequence of natural
   * numbers for instance).
   *
   * @param startNode the node to start walking the graph.
   * @return The immutable list of nodes starting from the first of {@code startNodes} that leads to
   *         a cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Stream#empty} is returned.
   */
  @SafeVarargs
  public final List<N> detectCycleFrom(N... startNodes) {
    return detectCycleFrom(nonNullList(startNodes));
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>This method will hang if the given graph is infinite without cycle (the sequence of natural
   * numbers for instance).
   *
   * @param startNodes the nodes to start walking the graph.
   * @return The immutable list of nodes starting from the first of {@code startNodes} that leads to
   *         a cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Stream#empty} is returned.
   */
  public List<N> detectCycleFrom(Iterable<? extends N> startNodes) {
    return detectCyclesFrom(startNodes).findFirst().orElse(emptyList());
  }

  /**
   * Walking from {@code startNodes}, detects all simple cycles in the graph topology.
   *
   * @param startNodes the nodes to start walking the graph.
   * @return A lazy stream of cycles each starting from one of {@code startNodes} that leads to the
   *         cycle, ending with nodes along the cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Stream#empty} is returned.
   */
  @SafeVarargs
  public final Stream<List<N>> detectCyclesFrom(N... startNodes) {
    return detectCyclesFrom(nonNullList(startNodes));
  }

  /**
   * Walking from {@code startNodes}, detects all simple cycles in the graph topology.
   *
   * @param startNodes the nodes to start walking the graph.
   * @return A lazy stream of cycles each starting from one of {@code startNodes} that leads to the
   *         cycle, ending with nodes along the cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Stream#empty} is returned.
   */
  public Stream<List<N>> detectCyclesFrom(Iterable<? extends N> startNodes) {
    AtomicReference<N> cyclic = new AtomicReference<>();
    Deque<N> enclosingCycles = new ArrayDeque<>();
    Set<N> blocked = new HashSet<>();  // Always a superset of `currentPath`.
    LinkedHashSet<N> currentPath = new LinkedHashSet<>();
    Walker<N> walker = Walker.inGraph(findSuccessors, new Predicate<N>() {
      @Override public boolean test(N node) {
        boolean newNode = blocked.add(node);
        if (newNode) {
          currentPath.add(node);
        } else if (currentPath.contains(node) && cyclic.compareAndSet(null, node)) {
          // A cycle's found!
          enclosingCycles.push(node);
        }
        return newNode;
      }
    });
    return walker.postOrderFrom(startNodes)
        .peek(n -> {
          currentPath.remove(n);
          if (n.equals(enclosingCycles.peek())) {
            // Exiting a cycle.
            // In case of a self-cycle, we immediately pop it because nothing is enclosed.
            // `cyclic` still points to this cyclic node for the later map() call to use.
            enclosingCycles.pop();
          }
          if (!enclosingCycles.isEmpty()) {
            // If we are in a cycle, we want to come back in again in case it forms another cycle
            // from a different path. Otherwise, it's proved to be a dead end.
            blocked.remove(n);
          }
        })
        .filter(n -> cyclic.get() != null)
        .map(last ->
            Stream.concat(currentPath.stream(), Stream.of(last, cyclic.getAndSet(null)))
                .collect(toImmutableList()));
  }

  private static <T> Collector<T, ?, List<T>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
  }
}

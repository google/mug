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
package com.google.mu.util.algo;

import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Comparator.comparingLong;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.mu.util.stream.BiStream;

/**
 * The Dijkstra shortest path algorithm implemented as a lazy, on-the-fly stream,
 * using Mug utilities.
 *
 * @since 3.8
 */
public final class ShortestPaths {
 
  /**
   * Returns the lazy stream of shortest paths starting from {@code originalNode}, with each node's
   * adjacent nodes and their direct distances from the node returned by the {@code
   * adjacentNodesDiscoverer} function.
   *
   * <p>The {@code adjacentNodesDiscoverer} function is called on-demand as the returned stream is
   * being iterated.
   *
   * <p>{@code originalNode} will correspond to the first element in the returned stream, with
   * {@link Path#distance} equal to {@code 0}, followed by the next closest node, etc.
   */
  public static <N> Stream<Path<N>> shortestPaths(
      N originalNode, Function<N, BiStream<N, Long>> adjacentNodesDiscoverer) {
    requireNonNull(originalNode);
    requireNonNull(adjacentNodesDiscoverer);
    Map<N, Vertex<N>> vertices = new HashMap<>();
    Set<N> done = new HashSet<>();
    PriorityQueue<Vertex<N>> queue = new PriorityQueue<>(comparingLong(Vertex::distance));
    Vertex<N> v0 = new Vertex<>(originalNode);
    queue.add(v0);
    return whileNotEmpty(queue)
        .map(PriorityQueue::remove)
        .filter(v -> done.add(v.to()))
        .peek(v -> {
          adjacentNodesDiscoverer.apply(v.to())
              .peek((n, d) -> {
                requireNonNull(n);
                if (d < 0) {
                  throw new IllegalArgumentException("Distance cannot be negative: " + d);
                }
                if (v.distance() + d < 0) {
                  throw new ArithmeticException("Distance overflow: " + v.distance() + " + " + d);
                }
              })
              .filterKeys(n -> !done.contains(n))
              .filter((n, d) -> {
                Vertex<N> pending = vertices.get(n);
                return pending == null || v.distance() + d < pending.distance();
              })
              .forEach((n, d) -> {
                Vertex<N> shorter = new Vertex<N>(n, v, v.distance() + d);
                vertices.put(n, shorter);
                queue.add(shorter);
              });
        })
        .map(identity());
  }

  /** The path from the original node to a destination node. */
  public interface Path<N> {
    /** returns the last node of the path. */
    N to();
    
    /** Returns the total distance of this path. */
    long distance();

    /**
     * Returns all nodes from the original node along this path, with the <em>cumulative</em>
     * distances from the original node up to each node in the stream, respectively.
     */
    BiStream<N, Long> nodes();
  }
  
  private static final class Vertex<N> implements Path<N> {
    private final N node;
    private final Vertex<N> predecessor;
    private final long distance;
    
    Vertex(N node, Vertex<N> predecessor, long distance) {
      this.node = node;
      this.predecessor = predecessor;
      this.distance = distance;
    }
    
    Vertex(N node) {
      this(node, null, 0);
    }

    @Override public N to() {
      return node;
    }
    
    @Override public long distance() {
      return distance;
    }
    
    @Override public BiStream<N, Long> nodes() {
      List<Vertex<N>> nodes = new ArrayList<>();
      for (Vertex<N> v = this; v != null; v = v.predecessor) {
        nodes.add(v);
      }
      Collections.reverse(nodes);
      return BiStream.from(nodes, v -> v.node, v -> v.distance);
    }
    
    @Override public String toString() {
      return nodes().keys().map(Object::toString).collect(Collectors.joining("->"));
    }
  }

  private ShortestPaths() {}
}

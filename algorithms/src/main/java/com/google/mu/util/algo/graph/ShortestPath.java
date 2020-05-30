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
import static java.util.Comparator.comparingDouble;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.mu.util.stream.BiStream;

/**
 * The Dijkstra shortest path algorithm implemented as a lazy, incrementally-computed stream,
 * using Mug utilities.
 *
 * <p>Compared to traditional imperative implementations, this incremental algorithm supports more
 * flexible use cases that'd otherwise require either full traversal of the graph (which can be
 * large), or copying and tweaking the implementation code for each individual use case.
 * 
 * <p>For example, without traversing the entire map, find 3 nearest Sushi restaurants: <pre>{@code
 *   List<Location> sushiPlaces = shortestPathsFrom(myLocation, Location::locationsAroundMe)
 *       .map(ShortestPath::to)
 *       .filter(this::isSushiRestaurant)
 *       .limit(3)
 *       .collect(toList());
 * }</pre>
 *
 * Or, find all gas stations within 5 miles: <pre>{@code
 *   List<Location> gasStations = shortestPathsFrom(myLocation, Location::locationsAroundMe)
 *       .takeWhile(path -> path.distance() <= 5)
 *       .map(ShortestPath::to)
 *       .filter(this::isGasStation)
 *       .collect(toList());
 * }</pre>
 *
 * @since 3.8
 */
public final class ShortestPath<N> {
  private final N node;
  private final ShortestPath<N> predecessor;
  private final double distance;
  
  /**
   * Returns a lazy stream of shortest paths starting from {@code startingNode}.
   *
   * <p>The {@code findAdjacentNodes} function is called on-the-fly to find the direct neighbors
   * of the current node. This function is expected to return a {@code BiStream} with these direct
   * neighbor nodes and their distances from the passed-in current node, respectively.
   *
   * <p>{@code startingNode} will correspond to the first element in the returned stream, with
   * {@link ShortestPath#distance} equal to {@code 0}, followed by the next closest node, etc.
   *
   * @param <N> The node type. Must implement {@link Object#equals} and {@link Object#hashCode}.
   */
  public static <N> Stream<ShortestPath<N>> shortestPathsFrom(
      N startingNode, Function<N, BiStream<N, Double>> findAdjacentNodes) {
    requireNonNull(startingNode);
    requireNonNull(findAdjacentNodes);
    Map<N, ShortestPath<N>> seen = new HashMap<>();
    Set<N> done = new HashSet<>();
    PriorityQueue<ShortestPath<N>> queue = new PriorityQueue<>(comparingDouble(ShortestPath::distance));
    queue.add(new ShortestPath<>(startingNode));
    return whileNotEmpty(queue)
        .map(PriorityQueue::remove)
        .filter(path -> done.add(path.to()))
        .peek(path ->
            findAdjacentNodes.apply(path.to())
                .forEachOrdered((neighbor, distance) -> {
                  requireNonNull(neighbor);
                  if (distance < 0) {
                    throw new IllegalArgumentException("Distance cannot be negative: " + distance);
                  }
                  if (done.contains(neighbor)) return;
                  ShortestPath<?> old = seen.get(neighbor);
                  if (old == null || path.distance() + distance < old.distance()) {
                    ShortestPath<N> shorter = path.extendTo(neighbor, distance);
                    seen.put(neighbor, shorter);
                    queue.add(shorter);
                  }
                }));
  }
  
  ShortestPath(N node) {
    this(node, null, 0);
  }
  
  private ShortestPath(N node, ShortestPath<N> predecessor, double distance) {
    this.node = node;
    this.predecessor = predecessor;
    this.distance = distance;
  }

  /** returns the last node of this path. */
  public N to() {
    return node;
  }
  
  /**
   * Returns the distance between the starting node and the {@link #to last node} of this path.
   * Zero for the first path in the stream returned by {@link ShortestPath#shortestPathsFrom},
   * in which case {@link #to} will return the starting node.
   */
  public double distance() {
    return distance;
  }

  /**
   * Returns all nodes from the starting node along this path, with the <em>cumulative</em>
   * distances from the starting node up to each node in the stream, respectively.
   */
  public BiStream<N, Double> stream() {
    List<ShortestPath<N>> nodes = new ArrayList<>();
    for (ShortestPath<N> p = this; p != null; p = p.predecessor) {
      nodes.add(p);
    }
    Collections.reverse(nodes);
    return BiStream.from(nodes, ShortestPath::to, ShortestPath::distance);
  }
  
  @Override public String toString() {
    return stream().keys().map(Object::toString).collect(joining("->"));
  }

  ShortestPath<N> extendTo(N nextNode, double d) {
    return new ShortestPath<>(nextNode, this, distance + d);
  }
}

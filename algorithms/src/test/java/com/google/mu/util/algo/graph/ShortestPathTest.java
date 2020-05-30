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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.algo.graph.ShortestPath.shortestPathsFrom;
import static com.google.mu.util.stream.BiStream.biStream;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.algo.graph.ShortestPath;
import com.google.mu.util.stream.BiStream;

@RunWith(JUnit4.class)
public class ShortestPathTest {
  private final MutableGraph<String> graph = GraphBuilder.undirected().<String>build();
  private final Map<String, Double> distances = new HashMap<>();

  @Test public void oneNode() {
    graph.addNode("root");
    List<ShortestPath<String>> paths =
        shortestPathsFrom("root", this::neighbors).collect(toList());
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("root", 0D));
  }

  @Test public void twoNodes() {
    addEdge("foo", "bar", 10);
    List<ShortestPath<String>> paths = shortestPathsFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(2);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D));
    assertThat(paths.get(1).distance()).isEqualTo(10D);
    assertThat(paths.get(1).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D));
  }

  @Test public void threeNodesList() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    List<ShortestPath<String>> paths = shortestPathsFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D));
    assertThat(paths.get(1).distance()).isEqualTo(10D);
    assertThat(paths.get(1).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D));
    assertThat(paths.get(2).distance()).isEqualTo(15D);
    assertThat(paths.get(2).stream().toMap())
        .isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D, "baz", 15D));
  }

  @Test public void threeNodesCycle() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    addEdge("baz", "foo", 12);
    List<ShortestPath<String>> paths = shortestPathsFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D));
    assertThat(paths.get(1).distance()).isEqualTo(10D);
    assertThat(paths.get(1).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D));
    assertThat(paths.get(2).distance()).isEqualTo(12D);
    assertThat(paths.get(2).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "baz", 12D));
  }

  @Test public void zeroDistance() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 0);
    addEdge("baz", "foo", 12);
    List<ShortestPath<String>> paths = shortestPathsFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D));
    assertThat(paths.get(1).distance()).isEqualTo(10D);
    assertThat(paths.get(1).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D));
    assertThat(paths.get(2).distance()).isEqualTo(10D);
    assertThat(paths.get(2).stream().toMap())
        .isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D, "baz", 10D));
  }

  @Test public void negativeDistanceDisallowed() {
    addEdge("foo", "bar", -1);
    assertThrows(
        IllegalArgumentException.class,
        () -> shortestPathsFrom("foo", this::neighbors).collect(toList()));
  }

  @Test public void distanceOverflowDetected() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", Double.MAX_VALUE);
    List<ShortestPath<String>> paths = shortestPathsFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0D);
    assertThat(paths.get(0).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D));
    assertThat(paths.get(1).distance()).isEqualTo(10D);
    assertThat(paths.get(1).stream().toMap()).isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D));
    assertThat(paths.get(2).distance()).isEqualTo(Double.MAX_VALUE);
    assertThat(paths.get(2).stream().toMap())
        .isEqualTo(ImmutableMap.of("foo", 0D, "bar", 10D, "baz", Double.MAX_VALUE));
  }

  @Test public void wikipedia() {
    addEdge("a", "b", 7);
    addEdge("a", "c", 9);
    addEdge("a", "f", 14);
    addEdge("b", "c", 10);
    addEdge("b", "d", 15);
    addEdge("c", "f", 2);
    addEdge("c", "d", 11);
    addEdge("d", "e", 6);
    addEdge("5", "6", 9);
    assertThat(shortestPathsFrom("a", this::neighbors).map(Object::toString).collect(toList()))
        .containsExactly("a", "a->b", "a->c", "a->c->f", "a->c->d", "a->c->d->e");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(ShortestPath.class);
  }
  
  private BiStream<String, Double> neighbors(String node) {
    return biStream(graph.adjacentNodes(node)).mapValues(a -> edgeDistance(node, a));
  }
  
  private void addEdge(String from, String to, double distance) {
    graph.putEdge(from, to);
    assertThat(distances.put(edge(from, to), distance)).isNull();
    assertThat(distances.put(edge(to, from), distance)).isNull();
  }
  
  private double edgeDistance(String from, String to) {
    return distances.get(edge(from, to));
  }
  
  private static String edge(String from, String to) {
    return from + " -> " + to;
  }
}

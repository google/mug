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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.algo.ShortestPaths.shortestPaths;
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
import com.google.mu.util.algo.ShortestPaths.Path;
import com.google.mu.util.stream.BiStream;

@RunWith(JUnit4.class)
public class ShortestPathsTest {
  private final MutableGraph<String> graph = GraphBuilder.undirected().<String>build();
  private final Map<String, Long> distances = new HashMap<>();

  @Test public void oneNode() {
    graph.addNode("root");
    List<ShortestPaths.Path<String>> paths = shortestPaths("root", this::neighbors).collect(toList());
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).distance()).isEqualTo(0);
    assertThat(paths.get(0).nodes().toMap()).isEqualTo(ImmutableMap.of("root", 0L));
  }

  @Test public void twoNodes() {
    addEdge("foo", "bar", 10);
    List<Path<String>> paths = shortestPaths("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(2);
    assertThat(paths.get(0).distance()).isEqualTo(0);
    assertThat(paths.get(0).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L));
    assertThat(paths.get(1).distance()).isEqualTo(10);
    assertThat(paths.get(1).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L));
  }

  @Test public void threeNodesList() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    List<Path<String>> paths = shortestPaths("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0);
    assertThat(paths.get(0).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L));
    assertThat(paths.get(1).distance()).isEqualTo(10);
    assertThat(paths.get(1).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L));
    assertThat(paths.get(2).distance()).isEqualTo(15);
    assertThat(paths.get(2).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L, "baz", 15L));
  }

  @Test public void threeNodesCycle() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    addEdge("baz", "foo", 12);
    List<Path<String>> paths = shortestPaths("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0);
    assertThat(paths.get(0).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L));
    assertThat(paths.get(1).distance()).isEqualTo(10);
    assertThat(paths.get(1).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L));
    assertThat(paths.get(2).distance()).isEqualTo(12);
    assertThat(paths.get(2).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "baz", 12L));
  }

  @Test public void zeroDistance() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 0);
    addEdge("baz", "foo", 12);
    List<Path<String>> paths = shortestPaths("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(3);
    assertThat(paths.get(0).distance()).isEqualTo(0);
    assertThat(paths.get(0).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L));
    assertThat(paths.get(1).distance()).isEqualTo(10);
    assertThat(paths.get(1).nodes().toMap()).isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L));
    assertThat(paths.get(2).distance()).isEqualTo(10);
    assertThat(paths.get(2).nodes().toMap())
        .isEqualTo(ImmutableMap.of("foo", 0L, "bar", 10L, "baz", 10L));
  }

  @Test public void negativeDistanceDisallowed() {
    addEdge("foo", "bar", -1);
    assertThrows(
        IllegalArgumentException.class,
        () -> shortestPaths("foo", this::neighbors).collect(toList()));
  }

  @Test public void distanceOverflowDetected() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", Long.MAX_VALUE);
    assertThrows(
        ArithmeticException.class,
        () -> shortestPaths("foo", this::neighbors).collect(toList()));
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
    assertThat(shortestPaths("a", this::neighbors).map(Object::toString).collect(toList()))
        .containsExactly("a", "a->b", "a->c", "a->c->f", "a->c->d", "a->c->d->e");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(ShortestPaths.class);
  }
  
  private BiStream<String, Long> neighbors(String node) {
    return biStream(graph.adjacentNodes(node)).mapValues(a -> edgeDistance(node, a));
  }
  
  private void addEdge(String from, String to, long distance) {
    graph.putEdge(from, to);
    assertThat(distances.put(edge(from, to), distance)).isNull();
    assertThat(distances.put(edge(to, from), distance)).isNull();
  }
  
  private long edgeDistance(String from, String to) {
    return distances.get(edge(from, to));
  }
  
  private static String edge(String from, String to) {
    return from + " -> " + to;
  }
}

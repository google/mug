package com.google.mu.util.graph;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.graph.ShortestPath.shortestCyclesFrom;
import static com.google.mu.util.stream.BiStream.biStream;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.truth.MultimapSubject;
import com.google.mu.util.stream.BiStream;

@RunWith(JUnit4.class)
public class ShortestCyclesTest {
  private final MutableGraph<String> graph = GraphBuilder.directed().<String>build();
  private final Map<String, Double> distances = new HashMap<>();

  @Test public void oneNode() {
    graph.addNode("root");
    assertThat(shortestCyclesFrom("root", this::neighbors)).isEmpty();
  }

  @Test public void twoNodes() {
    addEdge("foo", "bar", 10);
    assertThat(shortestCyclesFrom("foo", this::neighbors)).isEmpty();
  }

  @Test public void threeNodesList() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    assertThat(shortestCyclesFrom("foo", this::neighbors)).isEmpty();
  }

  @Test public void threeNodesCycle() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 5);
    addEdge("baz", "foo", 12);
    List<ShortestPath<String>> paths = shortestCyclesFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).distance()).isEqualTo(27D);
    assertKeyValues(paths.get(0).stream())
        .isEqualTo(ImmutableListMultimap.of("foo", 0D, "bar", 10D, "baz", 15D, "foo", 27D));
  }

  @Test public void zeroDistance() {
    addEdge("foo", "bar", 10);
    addEdge("bar", "baz", 0);
    addEdge("baz", "foo", 12);
    List<ShortestPath<String>> paths = shortestCyclesFrom("foo", this::neighbors).collect(toList());
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).distance()).isEqualTo(22D);
    assertKeyValues(paths.get(0).stream())
        .isEqualTo(ImmutableListMultimap.of("foo", 0D, "bar", 10D, "baz", 10D, "foo", 22D));
  }

  @Test public void negativeDistanceDisallowed() {
    addEdge("foo", "bar", -1);
    assertThrows(
        IllegalArgumentException.class,
        () -> shortestCyclesFrom("foo", this::neighbors).collect(toList()));
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
    addEdge("e", "a", 13);
    addEdge("5", "6", 9);
    assertThat(shortestCyclesFrom("a", this::neighbors).map(Object::toString).collect(toList()))
        .containsExactly("a->c->d->e->a");
  }

  private BiStream<String, Double> neighbors(String node) {
    return biStream(graph.successors(node)).mapValues(a -> edgeDistance(node, a));
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

  static<K,V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    Multimap<?, ?> multimap = stream.collect(ImmutableListMultimap::toImmutableListMultimap);
    return assertThat(multimap);
  }
}

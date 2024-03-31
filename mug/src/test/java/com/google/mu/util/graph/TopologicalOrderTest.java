package com.google.mu.util.graph;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.mu.util.stream.BiStream;

public class TopologicalOrderTest {
  @Test public void topologicalOrder_noStartNode() {
    GraphWalker<String> walker = Walker.inGraph(Stream::of);
    assertThat(walker.topologicalOrderFrom()).isEmpty();
  }

  @Test public void topologicalOrder_noChildren() {
    assertThat(Walker.inGraph(n -> null).topologicalOrderFrom("root"))
        .containsExactly("root");
  }

  @Test public void topologicalOrder_oneEdge() {
    Graph<String> graph = toDirectedGraph(ImmutableListMultimap.of("foo", "bar"));
    assertThat(topologicalOrder(graph, "foo"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test public void topologicalOrder_twoEdges() {
    Graph<String> graph = toDirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(topologicalOrder(graph, "foo"))
        .containsExactly("foo", "bar", "baz")
        .inOrder();
  }

  @Test public void topologicalOrder_dag() {
    Graph<String> graph = toDirectedGraph(
        ImmutableListMultimap.of("foo", "baz", "foo", "bar", "bar", "zoo", "baz", "zoo"));
    assertThat(topologicalOrder(graph, "foo"))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void topologicalOrder_multipleStartNodes() {
    Graph<String> graph = toDirectedGraph(
        ImmutableListMultimap.of("foo", "baz", "foo", "bar", "bar", "zoo", "baz", "zoo"));
    assertThat(topologicalOrder(graph, "zoo", "foo"))
        .containsExactly("foo", "bar", "baz", "zoo")
        .inOrder();
  }

  @Test public void topologicalOrder_trivialCycle() {
    GraphWalker<String> walker = Walker.inGraph(Stream::of);
    CyclicGraphException thrown =
        assertThrows(CyclicGraphException.class, () -> walker.topologicalOrderFrom("root"));
    assertThat(thrown.cyclicPath()).containsExactly("root", "root");
  }

  @Test public void topologicalOrder_oneUndirectedEdge() {
    Graph<String> graph = toUndirectedGraph(ImmutableListMultimap.of("foo", "bar"));
    CyclicGraphException thrown =
        assertThrows(CyclicGraphException.class, () -> topologicalOrder(graph, "foo"));
    assertThat(thrown.cyclicPath()).containsExactly("foo", "bar", "foo").inOrder();
  }

  @Test public void topologicalOrder_backEdge() {
    Graph<String> graph =
        toDirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz", "baz", "foo"));
    CyclicGraphException thrown =
        assertThrows(CyclicGraphException.class, () -> topologicalOrder(graph, "foo"));
    assertThat(thrown.cyclicPath()).containsExactly("foo", "bar", "baz", "foo").inOrder();
  }

  @SafeVarargs
  private static <N> List<N> topologicalOrder(Graph<N> graph, N... startNodes) {
    return Walker.inGraph((N n) -> graph.successors(n).stream())
        .topologicalOrderFrom(startNodes);
  }

  private static <N> Graph<N> toDirectedGraph(Multimap<N, N> edges) {
    MutableGraph<N> graph =
        GraphBuilder.directed().incidentEdgeOrder(ElementOrder.stable()).build();
    BiStream.from(edges.asMap()).flatMapValues(Collection::stream).forEach(graph::putEdge);
    return graph;
  }

  private static <N> Graph<N> toUndirectedGraph(Multimap<N, N> edges) {
    MutableGraph<N> graph = GraphBuilder.undirected().<N>build();
    BiStream.from(edges.asMap()).flatMapValues(Collection::stream).forEach(graph::putEdge);
    return graph;
  }
}

package com.google.mu.util.graph;

import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;

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

public class StronglyConnectedComponentsTest {
  @Test public void stronglyConnectedComponents_noStartNode() {
    GraphWalker<String> walker = Walker.inGraph(Stream::of);
    assertThat(walker.stronglyConnectedComponentsFrom()).isEmpty();
  }

  @Test public void stronglyConnectedComponents_noChildren() {
    assertThat(Walker.inGraph(n -> null).stronglyConnectedComponentsFrom("root"))
        .containsExactly(asList("root"));
  }

  @Test public void stronglyConnectedComponents_oneEdge() {
    Graph<String> graph = toDirectedGraph(ImmutableListMultimap.of("foo", "bar"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("foo"), asList("bar"));
  }

  @Test public void stronglyConnectedComponents_twoEdges() {
    Graph<String> graph = toDirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("foo"), asList("bar"), asList("baz"));
  }

  @Test public void stronglyConnectedComponents_twoUndirectedEdges() {
    Graph<String> graph = toUndirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("baz", "bar", "foo"));
  }

  @Test public void stronglyConnectedComponents_dag() {
    Graph<String> graph = toDirectedGraph(
        ImmutableListMultimap.of("foo", "baz", "foo", "bar", "bar", "zoo", "baz", "zoo"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("foo"), asList("bar"), asList("baz"), asList("zoo"));
  }

  @Test public void stronglyConnectedComponents_trivialCycle() {
    GraphWalker<String> walker = Walker.inGraph(n -> Stream.of(new String(n)));
    assertThat(walker.stronglyConnectedComponentsFrom("foo"))
        .containsExactly(asList("foo"));
  }

  @Test public void stronglyConnectedComponents_oneUndirectedEdge() {
    Graph<String> graph = toUndirectedGraph(ImmutableListMultimap.of("foo", "bar"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("bar", "foo"));
  }

  @Test public void stronglyConnectedComponents_twoUndirectedConnectedEdges() {
    Graph<String> graph = toUndirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("baz", "bar", "foo"));
  }

  @Test public void stronglyConnectedComponents_backEdge() {
    Graph<String> graph =
        toDirectedGraph(ImmutableListMultimap.of("foo", "bar", "bar", "baz", "baz", "foo"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("baz", "bar", "foo"));
  }

  @Test public void stronglyConnectedComponents_twoComponents() {
    Graph<String> graph = toDirectedGraph(
        ImmutableListMultimap.of("foo", "bar", "bar", "baz", "baz", "foo", "foo", "zoo"));
    assertThat(stronglyConnectedFrom(graph, "foo"))
        .containsExactly(asList("baz", "bar", "foo"), asList("zoo"));
  }

  @Test public void stronglyConnectedComponents_wikipedia() {
    Graph<String> graph = GraphBuilder.directed()
        .incidentEdgeOrder(ElementOrder.stable())
        .<String>immutable()
        .putEdge("a", "b")
        .putEdge("b", "e")
        .putEdge("e", "a")
        .putEdge("b", "c")
        .putEdge("b", "f")
        .putEdge("f", "g")
        .putEdge("g", "f")
        .putEdge("c", "g")
        .putEdge("c", "d")
        .putEdge("d", "c")
        .putEdge("d", "h")
        .putEdge("h", "d")
        .putEdge("h", "g")
        .build();
    assertThat(stronglyConnectedFrom(graph, "a"))
        .containsExactly(asList("e", "b", "a"), asList("f", "g"), asList("h", "d", "c"));
  }
  @Test public void stronglyConnectedComponents_multipleStartNodes() {
    Graph<String> graph = toDirectedGraph(
        ImmutableListMultimap.of("foo", "bar", "bar", "baz", "baz", "foo", "foo", "zoo", "dog", "cat"));
    assertThat(stronglyConnectedFrom(graph, "zoo", "foo", "dog"))
        .containsExactly(asList("baz", "bar", "foo"), asList("zoo"), asList("dog"), asList("cat"));
  }

  @SafeVarargs
  private static <N> Stream<List<N>> stronglyConnectedFrom(Graph<N> graph, N... startNodes) {
    return Walker.inGraph((N n) -> graph.successors(n).stream())
        .stronglyConnectedComponentsFrom(startNodes);
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

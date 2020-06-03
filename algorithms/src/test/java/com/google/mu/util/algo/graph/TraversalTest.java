package com.google.mu.util.algo.graph;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

public class TraversalTest {
  private final MutableGraph<String> graph = GraphBuilder.undirected().<String>build();

  @Test
  public void preOrder_noChildren() {
    graph.addNode("root");
    assertThat(preOrder("root").collect(toList())).containsExactly("root");
  }

  @Test
  public void preOrder_oneEdge() {
    graph.putEdge("foo", "bar");
    assertThat(preOrder("foo").collect(toList())).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void preOrder_twoEdges() {
    graph.putEdge("foo", "bar");
    graph.putEdge("bar", "baz");
    assertThat(preOrder("foo").collect(toList())).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void preOrder_depthFirst() {
    graph.putEdge("foo", "bar");
    graph.putEdge("foo", "baz");
    graph.putEdge("bar", "dog");
    assertThat(preOrder("foo").collect(toList())).containsExactly("foo", "bar", "dog", "baz")
        .inOrder();
  }

  @Test
  public void postOrder_noChildren() {
    graph.addNode("root");
    assertThat(postOrder("root").collect(toList())).containsExactly("root");
  }

  @Test
  public void postOrder_oneEdge() {
    graph.putEdge("foo", "bar");
    assertThat(postOrder("foo").collect(toList())).containsExactly("bar", "foo").inOrder();
  }

  @Test
  public void postOrder_twoEdges() {
    graph.putEdge("foo", "bar");
    graph.putEdge("bar", "baz");
    assertThat(postOrder("foo").collect(toList())).containsExactly("baz", "bar", "foo").inOrder();
  }

  @Test
  public void postOrder_depthFirst() {
    graph.putEdge("foo", "bar");
    graph.putEdge("foo", "baz");
    graph.putEdge("bar", "dog");
    assertThat(postOrder("foo").collect(toList())).containsExactly("dog", "bar", "baz", "foo")
        .inOrder();
  }

  private Stream<String> preOrder(String firstNode) {
    return Traversal.preOrderFrom(firstNode, n -> graph.adjacentNodes(n).stream());
  }

  private Stream<String> postOrder(String firstNode) {
    return Traversal.postOrderFrom(firstNode, n -> graph.adjacentNodes(n).stream());
  }
}

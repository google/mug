package com.google.mu.util.graph;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.graph.ShortestPath.unweightedShortestCyclesFrom;

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

  @Test public void unnweighted_oneNode() {
    graph.addNode("root");
    assertThat(unweightedShortestCyclesFrom("root", n -> graph.successors(n).stream()))
        .isEmpty();
  }

  @Test public void unweighted_twoNodes() {
    graph.putEdge("foo", "bar");
    assertThat(unweightedShortestCyclesFrom("foo", n -> graph.successors(n).stream()))
        .isEmpty();
  }

  @Test public void unweighted_threeNodesList() {
    graph.putEdge("foo", "bar");
    graph.putEdge("bar", "baz");
    assertThat(unweightedShortestCyclesFrom("foo", n -> graph.successors(n).stream()))
        .isEmpty();
  }

  @Test public void unweighted_threeNodesCycle() {
    graph.putEdge("foo", "bar");
    graph.putEdge("bar", "baz");
    graph.putEdge("baz", "foo");
    assertThat(unweightedShortestCyclesFrom(
            "foo", n -> graph.successors(n).stream()).map(Object::toString))
        .containsExactly("foo->bar->baz->foo");
  }

  static<K,V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    Multimap<?, ?> multimap = stream.collect(ImmutableListMultimap::toImmutableListMultimap);
    return assertThat(multimap);
  }
}

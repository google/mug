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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.MoreStreams;

public class WalkerTest {
  // TODO: figure out parameterized test like @ParameterizedTestRunner
  private final DataType dataType = DataType.GRAPH;

  @Test
  public void preOrder_noChildren() {
    assertThat(dataType.newWalker().preOrderFrom("root")).containsExactly("root");
  }

  @Test
  public void preOrder_emptyStartNodes() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.preOrderFrom(ImmutableList.of())).isEmpty();
  }

  @Test
  public void preOrder_oneEdge() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.preOrderFrom("foo")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void preOrder_twoEdges() {
    Walker<String> walker =
        dataType.newWalker(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(walker.preOrderFrom("foo")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void preOrder_graph_depthFirst() {
    Walker<String> walker =
        DataType.GRAPH.newWalker(
            ImmutableMap.of(
                "foo", asList("bar", "baz", "cat"),
                "cat", asList("run"),
                "bar", asList("cat", "dog")));
    assertThat(walker.preOrderFrom("foo"))
        .containsExactly("foo", "bar", "cat", "run", "dog", "baz")
        .inOrder();
  }

  @Test
  public void preOrder_tree_depthFirst() {
    Walker<String> walker =
        DataType.TREE.newWalker(
            ImmutableMap.of(
                "foo", asList("bar", "baz", "cat"),
                "cat", asList("run"),
                "bar", asList("cat", "dog")));
    assertThat(walker.preOrderFrom("foo"))
        .containsExactly("foo", "bar", "cat", "run", "dog", "baz", "cat", "run")
        .inOrder();
  }

  @Test
  public void preOrder_disjointRoots() {
    Walker<String> walker =
        dataType.newWalker(
            ImmutableMap.of(
                "foo", asList("cat", "dog"),
                "dog", asList("run"),
                "bar", asList("lion"),
                "lion", asList("roar")));
    assertThat(walker.preOrderFrom("foo", "bar"))
        .containsExactly("foo", "cat", "dog", "run", "bar", "lion", "roar")
        .inOrder();
  }

  @Test
  public void preOrder_graph_connectedRoots() {
    Walker<String> walker =
        DataType.GRAPH.newWalker(
            ImmutableMap.of(
                "foo", asList("cat", "dog"),
                "dog", asList("bar", "run"),
                "bar", asList("lion"),
                "lion", asList("roar")));
    assertThat(walker.preOrderFrom("foo", "bar"))
        .containsExactly("foo", "cat", "dog", "bar", "lion", "roar", "run")
        .inOrder();
  }

  @Test
  public void preOrder_tree_connectedRoots() {
    Walker<String> walker =
        DataType.TREE.newWalker(
            ImmutableMap.of(
                "foo", asList("cat", "dog"),
                "dog", asList("bar", "run"),
                "bar", asList("lion"),
                "lion", asList("roar")));
    assertThat(walker.preOrderFrom("foo", "bar"))
        .containsExactly("foo", "cat", "dog", "bar", "lion", "roar", "run", "bar", "lion", "roar")
        .inOrder();
  }

  @Test
  public void preOrder_infiniteBreadth() {
    Walker<Integer> walker = dataType.newWalker(n -> indexesFrom(n + 1));
    assertThat(walker.preOrderFrom(1).limit(4)).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test
  public void preOrder_graph_infiniteDepth() {
    Walker<Integer> walker = Walker.inGraph(n -> Stream.of(n, n + 1, n + 2));
    assertThat(walker.preOrderFrom(1).limit(4)).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test
  public void preOrder_tree_infiniteDepth() {
    Walker<Integer> walker = Walker.inTree(n -> Stream.of(n, n + 1, n + 2));
    assertThat(walker.preOrderFrom(1).limit(4)).containsExactly(1, 1, 1, 1).inOrder();
  }

  @Test
  public void postOrder_noChildren() {
    assertThat(dataType.newWalker().postOrderFrom("root")).containsExactly("root");
  }

  @Test
  public void postOrder_emptyStartNodes() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.postOrderFrom(ImmutableList.of())).isEmpty();
  }

  @Test
  public void postOrder_oneEdge() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.postOrderFrom("foo")).containsExactly("bar", "foo").inOrder();
  }

  @Test
  public void postOrder_twoEdges() {
    Walker<String> walker =
        dataType.newWalker(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(walker.postOrderFrom("foo")).containsExactly("baz", "bar", "foo").inOrder();
  }

  @Test
  public void postOrder_depthFirst() {
    Walker<String> walker =
        dataType.newWalker(ImmutableListMultimap.of("foo", "bar", "foo", "baz", "bar", "dog"));
    assertThat(walker.postOrderFrom("foo")).containsExactly("dog", "bar", "baz", "foo").inOrder();
  }

  @Test
  public void postOrder_disjointRoots() {
    Walker<String> walker =
        dataType.newWalker(
            ImmutableListMultimap.of("foo", "bar", "bar", "dog", "zoo", "cat", "zoo", "lion"));
    assertThat(walker.postOrderFrom("foo", "zoo"))
        .containsExactly("dog", "bar", "foo", "cat", "lion", "zoo")
        .inOrder();
  }

  @Test
  public void postOrder_graph_connectedRoots() {
    Walker<String> walker =
        DataType.GRAPH.newWalker(
            ImmutableListMultimap.of("foo", "bar", "bar", "dog", "zoo", "dog", "zoo", "cat"));
    assertThat(walker.postOrderFrom("foo", "zoo"))
        .containsExactly("cat", "zoo", "dog", "bar", "foo")
        .inOrder();
  }

  @Test
  public void postOrder_tree_connectedRoots() {
    Walker<String> walker =
        DataType.TREE.newWalker(
            ImmutableListMultimap.of("foo", "bar", "bar", "dog", "zoo", "dog", "zoo", "cat"));
    assertThat(walker.postOrderFrom("foo", "zoo"))
        .containsExactly("dog", "bar", "foo", "cat", "dog", "zoo")
        .inOrder();
  }

  @Test
  public void postOrder_graph_infiniteBreadth() {
    ImmutableMap<Integer, Stream<Integer>> infiniteBreadth =
        ImmutableMap.of(1, indexesFrom(2), 2, Stream.of(3, 4), 3, Stream.of(4, 5));
    Walker<Integer> walker = Walker.inGraph(infiniteBreadth::get);
    assertThat(walker.postOrderFrom(1).limit(6)).containsExactly(4, 5, 3, 2, 6, 7).inOrder();
  }

  @Test
  public void postOrder_tree_infiniteBreadth() {
    ImmutableMap<Integer, Streamer<Integer>> infiniteBreadth =
        ImmutableMap.of(
            1, () -> indexesFrom(2), 2, asList(30, 40, 50)::stream, 30, asList(40, 50)::stream);
    Walker<Integer> walker = Walker.inTree(n -> findSuccessors(infiniteBreadth, n).stream());
    assertThat(walker.postOrderFrom(1).limit(8))
        .containsExactly(40, 50, 30, 40, 50, 2, 3, 4)
        .inOrder();
  }

  @Test
  public void breadthFirst_noChildren() {
    assertThat(dataType.newWalker().breadthFirstFrom("foo")).containsExactly("foo");
  }

  @Test
  public void breadthFirst_emptyStartNodes() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.breadthFirstFrom(ImmutableList.of())).isEmpty();
  }

  @Test
  public void breadthFirst_oneEdge() {
    Walker<String> walker = dataType.newWalker(ImmutableListMultimap.of("foo", "bar"));
    assertThat(walker.breadthFirstFrom("foo")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void breadthFirst_twoEdges() {
    Walker<String> walker =
        dataType.newWalker(ImmutableListMultimap.of("foo", "bar", "bar", "baz"));
    assertThat(walker.breadthFirstFrom("foo")).containsExactly("foo", "bar", "baz").inOrder();
  }

  @Test
  public void breadthFirst_breadthFirst() {
    Walker<String> walker =
        dataType.newWalker(ImmutableListMultimap.of("foo", "bar", "foo", "baz", "bar", "dog"));
    assertThat(walker.breadthFirstFrom("foo"))
        .containsExactly("foo", "bar", "baz", "dog")
        .inOrder();
  }

  @Test
  public void breadthFirst_disjointRoots() {
    Walker<String> walker =
        dataType.newWalker(
            ImmutableListMultimap.of("foo", "bar", "foo", "baz", "zoo", "dog", "zoo", "cat"));
    assertThat(walker.breadthFirstFrom("foo", "zoo"))
        .containsExactly("foo", "zoo", "bar", "baz", "cat", "dog")
        .inOrder();
  }

  @Test
  public void breadthFirst_graph_connectedRoots() {
    Walker<String> walker =
        DataType.GRAPH.newWalker(
            ImmutableListMultimap.of("foo", "bar", "foo", "cat", "zoo", "dog", "zoo", "cat"));
    assertThat(walker.breadthFirstFrom("foo", "zoo"))
        .containsExactly("foo", "zoo", "bar", "cat", "dog")
        .inOrder();
  }

  @Test
  public void breadthFirst_tree_connectedRoots() {
    Walker<String> walker =
        DataType.TREE.newWalker(
            ImmutableListMultimap.of("foo", "bar", "foo", "cat", "zoo", "dog", "zoo", "cat"));
    assertThat(walker.breadthFirstFrom("foo", "zoo"))
        .containsExactly("foo", "zoo", "bar", "cat", "cat", "dog")
        .inOrder();
  }

  @Test
  public void breadthFirst_graph_infiniteDepth() {
    Walker<Integer> walker = Walker.inGraph(n -> Stream.of(n + 1, n + 2));
    assertThat(walker.breadthFirstFrom(1).limit(6)).containsExactly(1, 2, 3, 4, 5, 6).inOrder();
  }

  @Test
  public void breadthFirst_tree_infiniteDepth() {
    Walker<Integer> walker = Walker.inTree(n -> Stream.of(n + 1, n + 2));
    assertThat(walker.breadthFirstFrom(1).limit(6)).containsExactly(1, 2, 3, 3, 4, 4).inOrder();
  }

  @Test
  public void breadthFirst_graph_infiniteBreadth() {
    Walker<Integer> walker = Walker.inGraph(MoreStreams::indexesFrom);
    assertThat(walker.breadthFirstFrom(1).limit(6)).containsExactly(1, 2, 3, 4, 5, 6).inOrder();
  }

  @Test
  public void breadthFirst_tree_infiniteBreadth() {
    Walker<Integer> walker = Walker.inTree(MoreStreams::indexesFrom);
    assertThat(walker.breadthFirstFrom(1).limit(6)).containsExactly(1, 1, 2, 3, 4, 5).inOrder();
  }

  @Test
  public void staticMethods_nullCheck() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(Walker.class);
    new ClassSanityTester().forAllPublicStaticMethods(Walker.class).testNulls();
  }

  @Test
  public void instanceMethods_nullCheck()
      throws Exception {
    new NullPointerTester().testAllPublicInstanceMethods(dataType.newWalker());
  }

  private static <N> Streamer<N> findSuccessors(Map<?, Streamer<N>> edges, N node) {
    Streamer<N> noSuccessors = ImmutableList.<N>of()::stream;
    return edges.getOrDefault(node, noSuccessors);
  }

  private static <N> Graph<N> toUndirectedGraph(Multimap<N, N> edges) {
    MutableGraph<N> graph = GraphBuilder.undirected().<N>build();
    BiStream.from(edges.asMap()).flatMapValues(Collection::stream).forEach(graph::putEdge);
    return graph;
  }

  private static <N> ImmutableListMultimap<N, N> toMultimap(
      Map<N, ? extends Collection<? extends N>> map) {
    return BiStream.from(map)
            .flatMapValues(Collection::stream)
            .collect(ImmutableListMultimap::toImmutableListMultimap);
  }

  private enum DataType {
    GRAPH {
      @Override
      <N> Walker<N> newWalker(Multimap<N, N> edges) {
        Graph<N> graph = toUndirectedGraph(edges);
        return newWalker((N n) -> graph.adjacentNodes(n).stream().sorted());
      }

      @Override
      <N> Walker<N> newWalker(Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
        return Walker.inGraph(findSuccessors);
      }
    },
    TREE {
      @Override
      <N> Walker<N> newWalker(Multimap<N, N> edges) {
        return newWalker((N n) -> edges.get(n).stream().sorted());
      }

      @Override
      <N> Walker<N> newWalker(Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
        return Walker.inTree(findSuccessors);
      }
    },
    ;

    abstract <N> Walker<N> newWalker(Multimap<N, N> edges);

    final <N> Walker<N> newWalker(Map<N, ? extends Collection<? extends N>> edges) {
      return newWalker(toMultimap(edges));
    }

    abstract <N> Walker<N> newWalker(
        Function<? super N, ? extends Stream<? extends N>> findSuccessors);

    final <N> Walker<N> newWalker() {
      return newWalker(n -> Stream.empty());
    }
  }

  private interface Streamer<T> {
    Stream<T> stream();
  }
}

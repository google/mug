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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.charactersOf;
import static com.google.common.collect.Streams.stream;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.NetworkBuilder;
import com.google.common.graph.SuccessorsFunction;
import com.google.common.graph.ValueGraphBuilder;
import com.google.common.primitives.Chars;

/**
 * To gain test parity with Traverser, copied the TraverserTest by drop-in replacing Traverser with
 * the WalkerAsTraverser adapter class.
 *
 * <p>All functional tests pass. With two exceptions:
 *
 * <ol>
 *   <li>Walker doesn't do early validation on inputs, because the input is a stream, not Iterable.
 *   <li>Walker can't do instanceof check on the SuccessorFunction because we have no Guava
 *       dependency.
 *   <li>As result, Walker calls the successor function more lazily (only when traversing the node).
 * </ol>
 */
@RunWith(JUnit4.class)
public class WalkerAsTraverserTest {

  /**
   * The undirected graph in the {@link WalkerAsTraverser#breadthFirst(Object)} javadoc:
   *
   * <pre>{@code
   * b ---- a ---- d
   * |      |
   * |      |
   * e ---- c ---- f
   * }</pre>
   */
  private static final SuccessorsFunction<Character> JAVADOC_GRAPH =
      createUndirectedGraph("ba", "ad", "be", "ac", "ec", "cf");

  /**
   * A diamond shaped directed graph (arrows going down):
   *
   * <pre>{@code
   *   a
   *  / \
   * b   c
   *  \ /
   *   d
   * }</pre>
   */
  private static final SuccessorsFunction<Character> DIAMOND_GRAPH =
      createDirectedGraph("ab", "ac", "bd", "cd");

  /**
   * Same as {@link #DIAMOND_GRAPH}, but with an extra c->a edge and some self edges:
   *
   * <pre>{@code
   *   a<>
   *  / \\
   * b   c
   *  \ /
   *   d<>
   * }</pre>
   *
   * {@code <>} indicates a self-loop
   */
  private static final SuccessorsFunction<Character> MULTI_GRAPH =
      createDirectedGraph("aa", "dd", "ab", "ac", "ca", "cd", "bd");

  /** A directed graph with a single cycle: a -> b -> c -> d -> a. */
  private static final SuccessorsFunction<Character> CYCLE_GRAPH =
      createDirectedGraph("ab", "bc", "cd", "da");

  /**
   * Same as {@link #CYCLE_GRAPH}, but with an extra a->c edge.
   *
   * <pre>{@code
   * |--------------|
   * v              |
   * a -> b -> c -> d
   * |         ^
   * |---------|
   * }</pre>
   */
  private static final SuccessorsFunction<Character> TWO_CYCLES_GRAPH =
      createDirectedGraph("ab", "ac", "bc", "cd", "da");

  /**
   * A tree-shaped graph that looks as follows (all edges are directed facing downwards):
   *
   * <pre>{@code
   *        h
   *       /|\
   *      / | \
   *     /  |  \
   *    d   e   g
   *   /|\      |
   *  / | \     |
   * a  b  c    f
   * }</pre>
   */
  private static final SuccessorsFunction<Character> TREE =
      createDirectedGraph("hd", "he", "hg", "da", "db", "dc", "gf");

  /**
   * Two disjoint tree-shaped graphs that look as follows (all edges are directed facing downwards):
   *
   * <pre>{@code
   * a   c
   * |   |
   * |   |
   * b   d
   * }</pre>
   */
  private static final SuccessorsFunction<Character> TWO_TREES = createDirectedGraph("ab", "cd");

  /**
   * A graph consisting of a single root {@code a}:
   *
   * <pre>{@code
   * a
   * }</pre>
   */
  private static final SuccessorsFunction<Character> SINGLE_ROOT = createSingleRootGraph();

  /**
   * A graph that is not a tree (for example, it has two antiparallel edge between {@code e} and
   * {@code f} and thus has a cycle) but is a valid input to {@link WalkerAsTraverser#forTree} when
   * starting e.g. at node {@code a} (all edges without an arrow are directed facing downwards):
   *
   * <pre>{@code
   *     a
   *    /
   *   b   e <----> f
   *  / \ /
   * c   d
   * }</pre>
   */
  private static final SuccessorsFunction<Character> CYCLIC_GRAPH_CONTAINING_TREE =
      createDirectedGraph("ab", "bc", "bd", "ed", "ef", "fe");

  /**
   * A graph that is not a tree (for example, {@code h} is reachable from {@code f} via both {@code
   * e} and {@code g}) but is a valid input to {@link WalkerAsTraverser#forTree} when starting e.g.
   * at node {@code a} (all edges are directed facing downwards):
   *
   * <pre>{@code
   *     a   f
   *    /   / \
   *   b   e   g
   *  / \ / \ /
   * c   d   h
   * }</pre>
   */
  private static final SuccessorsFunction<Character> GRAPH_CONTAINING_TREE_AND_DIAMOND =
      createDirectedGraph("ab", "fe", "fg", "bc", "bd", "ed", "eh", "gh");

  @Test
  public void forGraph_breadthFirst_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(JAVADOC_GRAPH).breadthFirst('a');

    assertEqualCharNodes(result, "abcdef");
    assertEqualCharNodes(result, "abcdef");
  }

  @Test
  public void forGraph_breadthFirstIterable_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(JAVADOC_GRAPH).breadthFirst(charactersOf("bf"));

    assertEqualCharNodes(result, "bfaecd");
    assertEqualCharNodes(result, "bfaecd");
  }

  @Test
  public void forGraph_breadthFirst_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bd");
    assertEqualCharNodes(traverser.breadthFirst('c'), "cd");
    assertEqualCharNodes(traverser.breadthFirst('d'), "d");
  }

  @Test
  public void forGraph_breadthFirstIterable_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("")), "");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bc")), "bcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("acdb")), "acdb");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("db")), "db");
  }

  @Test
  public void forGraph_breadthFirst_multiGraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bd");
    assertEqualCharNodes(traverser.breadthFirst('c'), "cadb");
    assertEqualCharNodes(traverser.breadthFirst('d'), "d");
  }

  @Test
  public void forGraph_breadthFirstIterable_multiGraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("ac")), "acbd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("cb")), "cbad");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("db")), "db");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("d")), "d");
  }

  @Test
  public void forGraph_breadthFirst_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bcda");
    assertEqualCharNodes(traverser.breadthFirst('c'), "cdab");
    assertEqualCharNodes(traverser.breadthFirst('d'), "dabc");
  }

  @Test
  public void forGraph_breadthFirstIterable_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bd")), "bdca");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("dc")), "dcab");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bc")), "bcda");
  }

  @Test
  public void forGraph_breadthFirst_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bcda");
    assertEqualCharNodes(traverser.breadthFirst('c'), "cdab");
    assertEqualCharNodes(traverser.breadthFirst('d'), "dabc");
  }

  @Test
  public void forGraph_breadthFirstIterable_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bd")), "bdca");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("dc")), "dcab");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bc")), "bcda");
  }

  @Test
  public void forGraph_breadthFirst_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.breadthFirst('h'), "hdegabcf");
    assertEqualCharNodes(traverser.breadthFirst('d'), "dabc");
    assertEqualCharNodes(traverser.breadthFirst('a'), "a");
  }

  @Test
  public void forGraph_breadthFirstIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.breadthFirst(charactersOf("hg")), "hgdefabc");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("gd")), "gdfabc");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bdgh")), "bdghacfe");
  }

  @Test
  public void forGraph_breadthFirst_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(TWO_TREES).breadthFirst('a');

    assertEqualCharNodes(result, "ab");
  }

  @Test
  public void forGraph_breadthFirstIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).breadthFirst(charactersOf("a")), "ab");
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).breadthFirst(charactersOf("ac")), "acbd");
  }

  @Test
  public void forGraph_breadthFirst_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(SINGLE_ROOT).breadthFirst('a');

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forGraph_breadthFirstIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(SINGLE_ROOT).breadthFirst(charactersOf("a"));

    assertEqualCharNodes(result, "a");
  }

  /**
   * Checks that the elements of the iterable are calculated on the fly. Concretely, that means that
   * {@link SuccessorsFunction#successors(Object)} can only be called for a subset of all nodes.
   */
  @Test
  public void forGraph_breadthFirstIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(createDirectedGraph()).breadthFirst(charactersOf("")), "");
  }

  /**
   * Checks that the elements of the iterable are calculated on the fly. Concretely, that means that
   * {@link SuccessorsFunction#successors(Object)} can only be called for a subset of all nodes.
   */
  @Test
  public void forGraph_breadthFirst_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).breadthFirst('a');

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('a', 'b');
  }

  @Test
  public void forGraph_breadthFirstIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).breadthFirst(charactersOf("ab"));

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('a', 'b');
  }

  @Test
  public void forGraph_depthFirstPreOrder_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(JAVADOC_GRAPH).depthFirstPreOrder('a');

    assertEqualCharNodes(result, "abecfd");
    assertEqualCharNodes(result, "abecfd");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(JAVADOC_GRAPH).depthFirstPreOrder(charactersOf("bc"));

    assertEqualCharNodes(result, "bacefd");
    assertEqualCharNodes(result, "bacefd");
  }

  @Test
  public void forGraph_depthFirstPreOrder_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('c'), "cd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "d");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("")), "");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bc")), "bdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "abdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("acdb")), "abdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("db")), "db");
  }

  @Test
  public void forGraph_depthFirstPreOrder_multigraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('c'), "cabd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "d");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_multigraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("ac")), "abdc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("cb")), "cabd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("db")), "db");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("d")), "d");
  }

  @Test
  public void forGraph_depthFirstPreOrder_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bcda");
    assertEqualCharNodes(traverser.depthFirstPreOrder('c'), "cdab");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "dabc");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bd")), "bcda");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("dc")), "dabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bc")), "bcda");
  }

  @Test
  public void forGraph_depthFirstPreOrder_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bcda");
    assertEqualCharNodes(traverser.depthFirstPreOrder('c'), "cdab");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "dabc");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bd")), "bcda");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("dc")), "dabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bc")), "bcda");
  }

  @Test
  public void forGraph_depthFirstPreOrder_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder('h'), "hdabcegf");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "dabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "a");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("hg")), "hdabcegf");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("gd")), "gfdabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bdgh")), "bdacgfhe");
  }

  @Test
  public void forGraph_depthFirstPreOrder_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPreOrder('a');

    assertEqualCharNodes(result, "ab");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPreOrder(charactersOf("a")), "ab");
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPreOrder(charactersOf("ac")), "abcd");
  }

  @Test
  public void forGraph_depthFirstPreOrder_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(SINGLE_ROOT).depthFirstPreOrder('a');

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(SINGLE_ROOT).depthFirstPreOrder(charactersOf("a"));

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(createDirectedGraph()).depthFirstPreOrder(charactersOf("")), "");
  }

  @Test
  public void forGraph_depthFirstPreOrder_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).depthFirstPreOrder('a');

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('a', 'b');
  }

  @Test
  public void forGraph_depthFirstPreOrderIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(graph).depthFirstPreOrder(charactersOf("ac"));

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('a', 'b');
  }

  @Test
  public void forGraph_depthFirstPostOrder_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(JAVADOC_GRAPH).depthFirstPostOrder('a');
    assertEqualCharNodes(result, "fcebda");
    assertEqualCharNodes(result, "fcebda");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_javadocExample_canBeIteratedMultipleTimes() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(JAVADOC_GRAPH).depthFirstPostOrder(charactersOf("bf"));
    assertEqualCharNodes(result, "efcdab");
    assertEqualCharNodes(result, "efcdab");
  }

  @Test
  public void forGraph_depthFirstPostOrder_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "dbca");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "db");
    assertEqualCharNodes(traverser.depthFirstPostOrder('c'), "dc");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "d");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_diamond() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(DIAMOND_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("")), "");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bc")), "dbc");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("a")), "dbca");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("acdb")), "dbca");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("db")), "db");
  }

  @Test
  public void forGraph_depthFirstPostOrder_multigraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "dbca");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "db");
    assertEqualCharNodes(traverser.depthFirstPostOrder('c'), "dbac");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "d");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_multigraph() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(MULTI_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("ac")), "dbca");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("cb")), "dbac");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("db")), "db");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("d")), "d");
  }

  @Test
  public void forGraph_depthFirstPostOrder_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "dcba");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "adcb");
    assertEqualCharNodes(traverser.depthFirstPostOrder('c'), "badc");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "cbad");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_cycle() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(CYCLE_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("a")), "dcba");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bd")), "adcb");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("dc")), "cbad");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bc")), "adcb");
  }

  @Test
  public void forGraph_depthFirstPostOrder_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "dcba");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "adcb");
    assertEqualCharNodes(traverser.depthFirstPostOrder('c'), "badc");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "cbad");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_twoCycles() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TWO_CYCLES_GRAPH);
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("a")), "dcba");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bd")), "adcb");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("dc")), "cbad");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bc")), "adcb");
  }

  @Test
  public void forGraph_depthFirstPostOrder_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder('h'), "abcdefgh");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "a");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forGraph(TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("hg")), "abcdefgh");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("gd")), "fgabcd");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bdgh")), "bacdfgeh");
  }

  @Test
  public void forGraph_depthFirstPostOrder_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPostOrder('a');

    assertEqualCharNodes(result, "ba");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPostOrder(charactersOf("a")), "ba");
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(TWO_TREES).depthFirstPostOrder(charactersOf("ac")), "badc");
  }

  @Test
  public void forGraph_depthFirstPostOrder_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forGraph(SINGLE_ROOT).depthFirstPostOrder('a');

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(SINGLE_ROOT).depthFirstPostOrder(charactersOf("a"));

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forGraph(createDirectedGraph()).depthFirstPostOrder(charactersOf("")),
        "");
  }

  @Test
  public void forGraph_depthFirstPostOrder_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).depthFirstPostOrder('a');

    assertEqualCharNodes(Iterables.limit(result, 2), "db");
    assertThat(graph.requestedNodes).containsExactly('a', 'b', 'd');
  }

  @Test
  public void forGraph_depthFirstPostOrderIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(DIAMOND_GRAPH);
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(graph).depthFirstPostOrder(charactersOf("ac"));

    assertEqualCharNodes(Iterables.limit(result, 2), "db");
    assertThat(graph.requestedNodes).containsExactly('a', 'b', 'd');
  }

  @Test
  public void forTree_acceptsDirectedGraph() {
    MutableGraph<String> graph = GraphBuilder.directed().build();
    graph.putEdge("a", "b");

    WalkerAsTraverser.forTree(graph); // Does not throw
  }

  @Test
  public void forTree_acceptsDirectedValueGraph() {
    MutableValueGraph<String, Integer> valueGraph = ValueGraphBuilder.directed().build();
    valueGraph.putEdgeValue("a", "b", 11);

    WalkerAsTraverser.forTree(valueGraph); // Does not throw
  }

  @Test
  public void forTree_acceptsDirectedNetwork() {
    MutableNetwork<String, Integer> network = NetworkBuilder.directed().build();
    network.addEdge("a", "b", 11);

    WalkerAsTraverser.forTree(network); // Does not throw
  }

  @Test
  public void forTree_breadthFirst_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.breadthFirst('h'), "hdegabcf");
    assertEqualCharNodes(traverser.breadthFirst('d'), "dabc");
    assertEqualCharNodes(traverser.breadthFirst('a'), "a");
  }

  @Test
  public void forTree_breadthFirstIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.breadthFirst(charactersOf("")), "");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("h")), "hdegabcf");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("gd")), "gdfabc");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("age")), "agef");
  }

  @Test
  public void forTree_breadthFirst_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bcd");
    assertEqualCharNodes(traverser.breadthFirst('d'), "d");
  }

  @Test
  public void forTree_breadthFirstIterable_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.breadthFirst(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("b")), "bcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("cd")), "cd");
  }

  @Test
  public void forTree_breadthFirst_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.breadthFirst('a'), "abcd");
    assertEqualCharNodes(traverser.breadthFirst('b'), "bcd");
    assertEqualCharNodes(traverser.breadthFirst('d'), "d");
  }

  @Test
  public void forTree_breadthFirstIterable_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.breadthFirst(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("bg")), "bgcdh");
    assertEqualCharNodes(traverser.breadthFirst(charactersOf("ga")), "gahbcd");
  }

  @Test
  public void forTree_breadthFirst_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forTree(TWO_TREES).breadthFirst('a');

    assertEqualCharNodes(result, "ab");
  }

  @Test
  public void forTree_breadthFirstIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).breadthFirst(charactersOf("a")), "ab");
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).breadthFirst(charactersOf("ca")), "cadb");
  }

  @Test
  public void forTree_breadthFirst_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forTree(SINGLE_ROOT).breadthFirst('a');

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_breadthFirstIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forTree(SINGLE_ROOT).breadthFirst(charactersOf("a"));

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_breadthFirstIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(createDirectedGraph()).breadthFirst(charactersOf("")), "");
  }

  @Test
  public void forTree_breadthFirst_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).breadthFirst('h');

    assertEqualCharNodes(Iterables.limit(result, 2), "hd");
    assertThat(graph.requestedNodes).containsExactly('h', 'd');
  }

  @Test
  public void forTree_breadthFirstIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).breadthFirst(charactersOf("dg"));

    assertEqualCharNodes(Iterables.limit(result, 3), "dga");
    assertThat(graph.requestedNodes).containsExactly('a', 'd', 'g');
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("h")), "hdabcegf");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("d")), "dabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "a");
  }

  @Test
  public void forTree_depthFirstPreOrderIterableIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("")), "");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("h")), "hdabcegf");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("gd")), "gfdabc");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("age")), "agfe");
  }

  @Test
  public void forTree_depthFirstPreOrder_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "d");
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("b")), "bcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("cd")), "cd");
  }

  @Test
  public void forTree_depthFirstPreOrder_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.depthFirstPreOrder('a'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('b'), "bcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder('d'), "d");
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("a")), "abcd");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("bg")), "bcdgh");
    assertEqualCharNodes(traverser.depthFirstPreOrder(charactersOf("ga")), "ghabcd");
  }

  @Test
  public void forTree_depthFirstPreOrder_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forTree(TWO_TREES).depthFirstPreOrder('a');

    assertEqualCharNodes(result, "ab");
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).depthFirstPreOrder(charactersOf("a")), "ab");
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).depthFirstPreOrder(charactersOf("ca")), "cdab");
  }

  @Test
  public void forTree_depthFirstPreOrder_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forTree(SINGLE_ROOT).depthFirstPreOrder('a');

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forTree(SINGLE_ROOT).depthFirstPreOrder(charactersOf("a"));

    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(createDirectedGraph()).depthFirstPreOrder(charactersOf("")), "");
  }

  @Test
  public void forTree_depthFirstPreOrder_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).depthFirstPreOrder('h');

    assertEqualCharNodes(Iterables.limit(result, 2), "hd");
    assertThat(graph.requestedNodes).containsExactly('h', 'd');
  }

  @Test
  public void forTree_depthFirstPreOrderIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(graph).depthFirstPreOrder(charactersOf("dg"));

    assertEqualCharNodes(Iterables.limit(result, 2), "da");
    assertThat(graph.requestedNodes).containsExactly('a', 'd');
  }

  @Test
  public void forTree_depthFirstPostOrder_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder('h'), "abcdefgh");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "abcd");
    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "a");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_tree() {
    WalkerAsTraverser<Character> traverser = WalkerAsTraverser.forTree(TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("")), "");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("h")), "abcdefgh");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("gd")), "fgabcd");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("age")), "afge");
  }

  @Test
  public void forTree_depthFirstPostOrder_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "cdba");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "cdb");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "d");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_cyclicGraphContainingTree() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(CYCLIC_GRAPH_CONTAINING_TREE);

    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("a")), "cdba");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("b")), "cdb");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("cd")), "cd");
  }

  @Test
  public void forTree_depthFirstPostOrder_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.depthFirstPostOrder('a'), "cdba");
    assertEqualCharNodes(traverser.depthFirstPostOrder('b'), "cdb");
    assertEqualCharNodes(traverser.depthFirstPostOrder('d'), "d");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_graphContainingTreeAndDiamond() {
    WalkerAsTraverser<Character> traverser =
        WalkerAsTraverser.forTree(GRAPH_CONTAINING_TREE_AND_DIAMOND);

    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("a")), "cdba");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("bg")), "cdbhg");
    assertEqualCharNodes(traverser.depthFirstPostOrder(charactersOf("ga")), "hgcdba");
  }

  @Test
  public void forTree_depthFirstPostOrder_twoTrees() {
    Iterable<Character> result = WalkerAsTraverser.forTree(TWO_TREES).depthFirstPostOrder('a');
    assertEqualCharNodes(result, "ba");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_twoTrees() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).depthFirstPostOrder(charactersOf("a")), "ba");
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(TWO_TREES).depthFirstPostOrder(charactersOf("ca")), "dcba");
  }

  @Test
  public void forTree_depthFirstPostOrder_singleRoot() {
    Iterable<Character> result = WalkerAsTraverser.forTree(SINGLE_ROOT).depthFirstPostOrder('a');
    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_singleRoot() {
    Iterable<Character> result =
        WalkerAsTraverser.forTree(SINGLE_ROOT).depthFirstPostOrder(charactersOf("a"));
    assertEqualCharNodes(result, "a");
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_emptyGraph() {
    assertEqualCharNodes(
        WalkerAsTraverser.forTree(createDirectedGraph()).depthFirstPostOrder(charactersOf("")), "");
  }

  @Test
  public void forTree_depthFirstPostOrder_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result = WalkerAsTraverser.forGraph(graph).depthFirstPostOrder('h');

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('h', 'd', 'a', 'b');
  }

  @Test
  public void forTree_depthFirstPostOrderIterable_iterableIsLazy() {
    RequestSavingGraph graph = new RequestSavingGraph(TREE);
    Iterable<Character> result =
        WalkerAsTraverser.forGraph(graph).depthFirstPostOrder(charactersOf("dg"));

    assertEqualCharNodes(Iterables.limit(result, 2), "ab");
    assertThat(graph.requestedNodes).containsExactly('a', 'b', 'd');
  }

  private static SuccessorsFunction<Character> createDirectedGraph(String... edges) {
    return createGraph(/* directed = */ true, edges);
  }

  private static SuccessorsFunction<Character> createUndirectedGraph(String... edges) {
    return createGraph(/* directed = */ false, edges);
  }

  /**
   * Creates a graph from a list of node pairs (encoded as strings, e.g. "ab" means that this graph
   * has an edge between 'a' and 'b').
   *
   * <p>The {@code successors} are always returned in alphabetical order.
   */
  private static SuccessorsFunction<Character> createGraph(boolean directed, String... edges) {
    ImmutableMultimap.Builder<Character, Character> graphMapBuilder = ImmutableMultimap.builder();
    for (String edge : edges) {
      checkArgument(
          edge.length() == 2, "Expecting each edge to consist of 2 characters but got %s", edge);
      char node1 = edge.charAt(0);
      char node2 = edge.charAt(1);
      graphMapBuilder.put(node1, node2);
      if (!directed) {
        graphMapBuilder.put(node2, node1);
      }
    }
    final ImmutableMultimap<Character, Character> graphMap = graphMapBuilder.build();

    return new SuccessorsFunction<Character>() {
      @Override
      public Iterable<? extends Character> successors(Character node) {
        checkArgument(
            graphMap.containsKey(node) || graphMap.containsValue(node),
            "Node %s is not an element of this graph",
            node);
        return Ordering.natural().immutableSortedCopy(graphMap.get(node));
      }
    };
  }

  private static ImmutableGraph<Character> createSingleRootGraph() {
    MutableGraph<Character> graph = GraphBuilder.directed().build();
    graph.addNode('a');
    return ImmutableGraph.copyOf(graph);
  }

  private static void assertEqualCharNodes(Iterable<Character> result, String expectedCharacters) {
    assertThat(ImmutableList.copyOf(result))
        .containsExactlyElementsIn(Chars.asList(expectedCharacters.toCharArray()))
        .inOrder();
  }

  private static class RequestSavingGraph implements SuccessorsFunction<Character> {
    private final SuccessorsFunction<Character> delegate;
    final Multiset<Character> requestedNodes = HashMultiset.create();

    RequestSavingGraph(SuccessorsFunction<Character> delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public Iterable<? extends Character> successors(Character node) {
      requestedNodes.add(node);
      return delegate.successors(node);
    }
  }

  private interface WalkerAsTraverser<N> {
    static <N> WalkerAsTraverser<N> forTree(SuccessorsFunction<N> fun) {
      return () -> Walker.inTree(n -> stream(fun.successors(n)));
    }

    static <N> WalkerAsTraverser<N> forGraph(SuccessorsFunction<N> fun) {
      return () -> Walker.inGraph(n -> stream(fun.successors(n)));
    }

    Walker<N> walker();

    default Iterable<N> breadthFirst(N startNode) {
      return () -> walker().breadthFirstFrom(startNode).iterator();
    }

    default Iterable<N> breadthFirst(Iterable<? extends N> startNodes) {
      return () -> walker().breadthFirstFrom(startNodes).iterator();
    }

    default Iterable<N> depthFirstPreOrder(N startNode) {
      return () -> walker().preOrderFrom(startNode).iterator();
    }

    default Iterable<N> depthFirstPreOrder(Iterable<? extends N> startNodes) {
      return () -> walker().preOrderFrom(startNodes).iterator();
    }

    default Iterable<N> depthFirstPostOrder(N startNode) {
      return () -> walker().postOrderFrom(startNode).iterator();
    }

    default Iterable<N> depthFirstPostOrder(Iterable<? extends N> startNodes) {
      return () -> walker().postOrderFrom(startNodes).iterator();
    }
  }
}

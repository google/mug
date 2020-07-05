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

import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.google.mu.util.stream.Iteration;

/**
 * Walker for binary tree topology (see {@link Walker#inBinaryTree Walker.inBinaryTree()}).
 *
 * <p>Besides {@link #preOrderFrom pre-order}, {@link #postOrderFrom post-order} and {@link
 * #breadthFirstFrom breadth-first} traversals, also supports {@link #inOrderFrom in-order}.
 *
 * @param <N> the tree node type
 * @since 4.2
 */
public final class BinaryTreeWalker<N> extends Walker<N> {
  private final UnaryOperator<N> getLeft;
  private final UnaryOperator<N> getRight;

  BinaryTreeWalker(UnaryOperator<N> getLeft, UnaryOperator<N> getRight) {
    this.getLeft = requireNonNull(getLeft);
    this.getRight = requireNonNull(getRight);
  }

  /**
   * Returns a lazy stream for breadth-first traversal from {@code root}.
   * Empty stream is returned if {@code roots} is empty.
   */
  public Stream<N> breadthFirstFrom(Iterable<? extends N> roots) {
    return topDown(roots, Queue::add);
  }

  /**
   * Returns a lazy stream for pre-order traversal from {@code roots}.
   * Empty stream is returned if {@code roots} is empty.
   */
  @Override public Stream<N> preOrderFrom(Iterable<? extends N> roots) {
    return inBinaryTree(getRight, getLeft).topDown(roots, Deque::push);
  }

  /**
   * Returns a lazy stream for post-order traversal from {@code root}.
   * Empty stream is returned if {@code roots} is empty.
   *
   * <p>For small or medium sized in-memory trees, it's equivalent and more efficient to first
   * collect the nodes into a list in "reverse post order", and then use {@code
   * Collections.reverse()}, as in:
   * <pre>{@code
   *   List<Node> nodes =
   *       Walker.inBinaryTree(Tree::right, Tree::left)    // 1. flip left to right
   *           .preOrderFrom(root)                         // 2. pre-order
   *           .collect(toCollection(ArrayList::new));     // 3. in reverse-post-order
   *   Collections.reverse(nodes);                         // 4. reverse to get post-order
   * }</pre>
   *
   * Or, use the {@link com.google.mu.util.stream.MoreStreams#toListAndThen toListAndThen()}
   * collector to do it in one-liner:
   * <pre>{@code
   *   List<Node> nodes =
   *       Walker.inBinaryTree(Tree::right, Tree::left)
   *           .preOrderFrom(root)
   *           .collect(toListAndThen(Collections::reverse));
   * }</pre>
   */
  public Stream<N> postOrderFrom(Iterable<? extends N> roots) {
    DepthFirst iteration = new DepthFirst();
    for (N root : roots) {
      requireNonNull(root);
      iteration.yield(() -> iteration.postOrder(root));
    }
    return iteration.stream();
  }

  /**
   * Returns a lazy stream for in-order traversal from {@code roots}.
   * Empty stream is returned if {@code roots} is empty.
   */
  @SafeVarargs public final Stream<N> inOrderFrom(N... roots) {
    return inOrderFrom(asList(roots));
  }

  /**
   * Returns a lazy stream for in-order traversal from {@code roots}.
   * Empty stream is returned if {@code roots} is empty.
   */
  public Stream<N> inOrderFrom(Iterable<? extends N> roots) {
    DepthFirst iteration = new DepthFirst();
    for (N root : roots) {
      requireNonNull(root);
      iteration.yield(() -> iteration.inOrder(root));
    }
    return iteration.stream();
  }

  private Stream<N> topDown(Iterable<? extends N> roots, InsertionOrder order) {
    Deque<N> horizon = toDeque(roots);
    return whileNotNull(horizon::poll)
        .peek(n -> {
          N left = getLeft.apply(n);
          N right = getRight.apply(n);
          if (left != null) order.insertInto(horizon, left);
          if (right != null) order.insertInto(horizon, right);
        });
  }

  private final class DepthFirst extends Iteration<N> {
    void inOrder(N root) {
      N left = getLeft.apply(root);
      N right = getRight.apply(root);
      if (left == null && right == null) {  // Minimize allocation for leaf nodes.
        yield(root);
      } else {
        yield(() -> {
          if (left != null) inOrder(left);
          yield(root);
          if (right != null) inOrder(right);
        });
      }
    }

    void postOrder(N root) {
      N left = getLeft.apply(root);
      N right = getRight.apply(root);
      if (left == null && right == null) {  // Minimize allocation for leaf nodes.
        yield(root);
      } else {
        yield(() -> {
          if (left != null) postOrder(left);
          if (right != null) postOrder(right);
          yield(root);
        });
      }
    }
  }

  private static <N> Deque<N> toDeque(Iterable<? extends N> nodes) {
    Deque<N> deque = new ArrayDeque<>();
    for (N node : nodes) deque.add(node);
    return deque;
  }
}

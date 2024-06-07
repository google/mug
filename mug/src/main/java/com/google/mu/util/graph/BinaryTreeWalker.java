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
import static com.google.mu.util.stream.MoreStreams.withSideEffect;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Queue;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

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
  @Override
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
   * Or, use the {@link com.google.mu.util.stream.MoreCollectors#toListAndThen toListAndThen()}
   * collector to do it in one-liner:
   * <pre>{@code
   *   List<Node> nodes =
   *       Walker.inBinaryTree(Tree::right, Tree::left)
   *           .preOrderFrom(root)
   *           .collect(toListAndThen(Collections::reverse));
   * }</pre>
   */
  @Override
  public Stream<N> postOrderFrom(Iterable<? extends N> roots) {
    return whileNotNull(new PostOrder(roots)::nextOrNull);
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
    return whileNotNull(new InOrder(roots)::nextOrNull);
  }

  private Stream<N> topDown(Iterable<? extends N> roots, InsertionOrder order) {
    Deque<N> horizon = toDeque(roots);
    return withSideEffect(
        whileNotNull(horizon::poll),
        n -> {
          N left = getLeft.apply(n);
          N right = getRight.apply(n);
          if (left != null) order.insertInto(horizon, left);
          if (right != null) order.insertInto(horizon, right);
        });
  }

  private final class InOrder {
    private final Queue<N> roots;
    private final Deque<N> leftPath = new ArrayDeque<>();
    private N right;  // if set, we traverse it in the next step.

    InOrder(Iterable<? extends N> roots) {
      this.roots = toDeque(roots);
    }

    N nextOrNull() {
      // 1. Each time we return the top of the `leftPath` stack.
      // 2. Before a node is returned, its right child is set to be traversed next.
      // 3. when stack is empty, traverse the next root.
      // 4. When either a root or `right` begins to be traversed,
      //    the node and its left-most descendants are pushed onto the `leftPath` stack.
      if (hasNextAsOf(right) || hasNextAsOf(roots.poll())) {
        N node = leftPath.pop();
        // Store right child in a field rather than expanding its left path immediately,
        // this way we avoid calling getRight until necessary. Expanding lazily allows us to be
        // short-circuitable in case the right node has infinite depth.
        right = getRight.apply(node);
        return node;
      }
      return null;
    }

    private boolean hasNextAsOf(final N node) {
      for (N n = node; n != null; n = getLeft.apply(n)) {
        leftPath.push(n);
      }
      return !leftPath.isEmpty();
    }
  }

  private final class PostOrder {
    private final Queue<N> roots;
    private final Deque<N> leftPath = new ArrayDeque<>();
    private final BitSet ready = new BitSet();

    PostOrder(Iterable<? extends N> roots) {
      this.roots = toDeque(roots);
    }

    N nextOrNull() {
      // 1. Keep extra `ready` state to remember whether a node's right child has been traversed.
      // 2. If the top of `leftPath` stack is `ready`, it's returned.
      // 3. If not ready, traverse the right child.
      // 4. when stack is empty, traverse the next root.
      // 5. When either a root or `right` begins to be traversed,
      //    the node and its left-most descendants are pushed onto the `leftPath` stack.
      for (N right = null;
          hasNextAsOf(right) || hasNextAsOf(roots.poll());
          right = getRight.apply(leftPath.getFirst()), ready.set(leftPath.size() - 1)) {
        // We could have just compared the previously returned node with the current top.right,
        // if we could depend on a concrete binary tree data structure, where the right child
        // is an idempotent field. But it'd be extra contractual burden to carry.
        // Using a BitSet accomplishes the post order, with minimal overhead.
        if (ready.get(leftPath.size() - 1)) return leftPath.pop();
      }
      return null;
    }

    private boolean hasNextAsOf(final N node) {
      for (N n = node; n != null; n = getLeft.apply(n)) {
        ready.clear(leftPath.size());
        leftPath.push(n);
      }
      return !leftPath.isEmpty();
    }
  }

  private static <N> Deque<N> toDeque(Iterable<? extends N> nodes) {
    Deque<N> deque = new ArrayDeque<>();
    for (N node : nodes) deque.add(node);
    return deque;
  }
}

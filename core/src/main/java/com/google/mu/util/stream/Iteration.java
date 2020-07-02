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
package com.google.mu.util.stream;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

/**
 * {@link #yield yield()} elements imperatively into a lazy stream.
 *
 * <p>While not required, users are expected to create a subclass and then
 * be able to call {@code yield()} as if it were a keyword.
 *
 * <p>For example, in-order traversing a binary tree recursively may look like:
 * <pre>{@code
 * void inOrder(Tree<T> tree) {
 *   if (tree == null) return;
 *   inOrder(tree.left);
 *   System.out.println(tree.value);
 *   inOrder(tree.right);
 * }
 * }</pre>
 *
 * Using {@code Iteration}, the above code can be intuitively transformed to iterative stream:
 * <pre>{@code
 * class DepthFirst<T> extends Iteration<T> {
 *   DepthFirst<T> inOrder(Tree<T> tree) {
 *     if (tree == null) return this;
 *     yield(() -> inOrder(tree.left));
 *     yield(tree.value);
 *     yield(() -> inOrder(tree.right));
 *   }
 * }
 *
 * static <T> Stream<T> inOrderFrom(Tree<T> root) {
 *   return new DepthFirst<>().inOrder(root).stream();
 * }
 * }</pre>
 *
 * <p>Similarly, the following recursive graph post-order traversal code:
 * <pre>{@code
 * class Traverser<N> {
 *   private final Set<N> visited = new HashSet<>();
 *
 *   void postOrder(N node) {
 *     if (!visited.add(node)) {
 *       return;
 *     }
 *     for (N successor : node.getSuccessors()) {
 *       postOrder(successor);
 *     }
 *     System.out.println("node: " + node);
 *   }
 * }
 * }</pre>
 *
 * can be transformed to an iterative stream using:
 * <pre>{@code
 * class DepthFirst<N> extends Iteration<N> {
 *   private final Set<N> visited = new HashSet<>();
 *
 *   DepthFirst<N> postOrder(N node) {
 *     if (!visited.add(node)) {
 *       return this;
 *     }
 *     for (N successor : node.getSuccessors()) {
 *       yield(() -> postOrder(successor));
 *     }
 *     yield(node);
 *     return this;
 *   }
 * }
 *
 * static <N> Stream<N> postOrderFrom(N node) {
 *   return new DepthFirst<>().postOrder(node).stream();
 * }
 * }</pre>
 *
 * <p>Keep in mind that, unlike {@code return} or {@code System.out.println()}, {@code yield()}
 * is lazy and does not evaluate until the stream iterates over it. So it's critical that
 * <em>all side effects</em> should be wrapped inside {@code Continuation} objects passed to
 * {@code yield()}.
 *
 * <p>This class and the generated streams are stateful and not safe to be used in multi-threads.
 *
 * <p>Nulls are not allowed.
 *
 * @since 4.4
 */
public class Iteration<T> {
  private final Deque<Object> stack = new ArrayDeque<>();
  private final Deque<Object> stackFrame = new ArrayDeque<>();

  /** Yields {@code element} to the result stream. */
  public final T yield(T element) {
    if (element instanceof Continuation) {
      throw new IllegalArgumentException("Do not stream Continuation objects");
    }
    stackFrame.add(element);
    return element;
  }

  /**
   * Yields to the stream a recursive iteration or lazy side-effect
   * wrapped in {@code continuation}.
   */
  public final Iteration<T> yield(Continuation continuation) {
    stackFrame.add(continuation);
    return this;
  }

  /** Returns the stream that iterates through the {@link #yield yielded} elements. */
  public final Stream<T> stream() {
    return whileNotNull(this::next);
  }

  /**
   * Encapsulates recursive iteration or a lazy block of code with side-effect.
   *
   * <p>Note that if after a {@link #yield(Continuation) yielded) recursive iteration, the
   * subsequent code expects state change (for example, the nodes being visited will keep changing
   * during graph traversal), the subsequent code also needs to be yielded to be able to observe
   * the expected state change.
   */
  public interface Continuation {
    /** Runs the continuation. It will be called at most once throughout the stream. */
    void run();
  }

  private T next() {
    for (; ;) {
      while (!stackFrame.isEmpty()) {
        stack.push(stackFrame.removeLast());
      }
      Object top = stack.pollFirst();
      if (top instanceof Continuation) {
        ((Continuation) top).run();
      } else {
        @SuppressWarnings("unchecked")  // we only put either T or Continuation in the stack.
        T element = (T) top;
        return element;
      }
    }
  }
}

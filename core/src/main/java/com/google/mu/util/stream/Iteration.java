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
 * Helper class to transform imperative loops into iterative streams intuitively, using
 * {@link #yield}. While not required, users are expected to create a subclass and then
 * be able to call {@code yield()} as if it were a keyword.
 *
 * <p>For example, post-order traversing a binary tree recursively may look like:
 * <pre>{@code
 * void postOrder(Tree<T> tree) {
 *   if (tree == null) return;
 *   postOrder(tree.left);
 *   postOrder(tree.right);
 *   System.out.println(tree.value);
 * }
 * }</pre>
 *
 * Using {@code Iteration}, the above code can be straight-forwardly transformed to an iterative
 * stream:
 * <pre>{@code
 * class TreeIteration<T> extends Iteration<T> {
 *   TreeIteration<T> postOrder(Tree<T> tree) {
 *     if (tree == null) return this;
 *     yield(() -> postOrder(tree.left));
 *     yield(() -> postOrder(tree.right));
 *     yield(tree.value);
 *   }
 * }
 *
 * static <T> Stream<T> postOrder(Tree<T> root) {
 *   return new TreeIteration<>().postOrder(root).stream();
 * }
 * }</pre>
 *
 * <p>Unlike {@code return} or {@code System.out.println()}, keep in mind that {@code yield()}
 * is lazy and does not evaluate until the stream iterates over it. So it's critical that
 * any <em>side-effects</em> should be wrapped inside the {@code Continuation} passed to
 * {@code yield].
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

  /** Returns the stream that iterates through the {@link #yield(Object} yielded} elements. */
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
      if (stack.isEmpty()) return null;
      Object top = stack.pop();
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

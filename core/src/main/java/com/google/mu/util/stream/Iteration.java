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
 * Iteratively {@link #yield yield()} elements into a lazy stream.
 *
 * <p>First and foremost, why "yield"? A C#-style yield return requires compiler support to be able
 * to create iterators or streams through imperative loops like:
 * <pre>{@code for (int i = 0; ; i++) yield(i);}</pre>.
 *
 * <b>For this kind of use cases, Java 8 and above have opted to answer with the Stream library.
 * One can use {@code IntStream.iterate(0, i -> i + 1)} or {@code MoreStreams.indexesFrom(0)} etc.
 * It's a non-goal for this library to solve the already-solved problem.
 *
 * <p>There's however a group of use cases not well supported by the Java Stream library: recursive
 * iteration. Imagine if you have a recursive code traversing a binary tree:
 *
 * <pre>{@code
 * void inOrder(Tree<T> tree) {
 *   if (tree == null) return;
 *   inOrder(tree.left);
 *   System.out.println(tree.value);
 *   inOrder(tree.right);
 * }
 * }</pre>
 *
 * The JDK offers no trivial Stream alternative should you need to provide iterative access or even
 * infinite stream. The {@code Iteration} class is designed to fill the gap by intuitively
 * transforming such recursive code to iterative (potentially infinite) stream:
 *
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
 *
 * <pre>{@code
 * class Traverser<N> {
 *   private final Set<N> visited = new HashSet<>();
 *
 *   void postOrder(N node) {
 *     if (visited.add(node)) {
 *       for (N successor : node.getSuccessors()) {
 *         postOrder(successor);
 *        }
 *       System.out.println("node: " + node);
 *     }
 *   }
 * }
 * }</pre>
 *
 * can be transformed to an iterative stream using:
 *
 * <pre>{@code
 * class DepthFirst<N> extends Iteration<N> {
 *   private final Set<N> visited = new HashSet<>();
 *
 *   DepthFirst<N> postOrder(N node) {
 *     if (visited.add(node)) {
 *       for (N successor : node.getSuccessors()) {
 *         yield(() -> postOrder(successor));
 *       }
 *       yield(node);
 *     }
 *     return this;
 *   }
 * }
 *
 * static <N> Stream<N> postOrderFrom(N node) {
 *   return new DepthFirst<>().postOrder(node).stream();
 * }
 * }</pre>
 *
 * <p>While not required, users are encouraged to create a subclass and then be able to call {@code
 * yield()} as if it were a keyword.
 *
 * <p>Keep in mind that, unlike {@code return} or {@code System.out.println()}, {@code yield()} is
 * lazy and does not evaluate until the stream iterates over it. So it's critical that <em>all side
 * effects</em> should be wrapped inside {@code Continuation} objects passed to {@code yield()}.
 *
 * <p>This class and the generated streams are stateful and not safe to be used in multi-threads.
 *
 * <p>Nulls are not allowed.
 *
 * @since 4.4
 */
public class Iteration<T> {
  private final Deque<Object> stack = new ArrayDeque<>();
  private final Deque<Object> stackFrame = new ArrayDeque<>(8);

  /** Yields {@code element} to the result stream. */
  public final Iteration<T> yield(T element) {
    if (element instanceof Continuation) {
      throw new IllegalArgumentException("Do not stream Continuation objects");
    }
    stackFrame.push(element);
    return this;
  }

  /**
   * Yields to the stream a recursive iteration or lazy side-effect
   * wrapped in {@code continuation}.
   */
  public final Iteration<T> yield(Continuation continuation) {
    stackFrame.push(continuation);
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
  @FunctionalInterface
  public interface Continuation {
    /** Runs the continuation. It will be called at most once throughout the stream. */
    void run();
  }

  private T next() {
    for (; ;) {
      while (!stackFrame.isEmpty()) {
        stack.push(stackFrame.pop());
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

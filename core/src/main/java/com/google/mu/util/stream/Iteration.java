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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Transforms (eagerly evaluated) recursive algorithms into <em>lazy</em> streams.
 *
 * <p>Imagine if you have a recursive binary tree traversal algorithm:
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
 * It can be intuitively transformed to a stream as in:
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
 * class DepthFirst<N> {
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
 * <p>And how about Fibonacci sequence as a lazy stream?
 *
 * <pre>{@code
 * class Fibonacci extends Iteration<Long> {
 *   Fibonacci from(long v0, long v1) {
 *     yield(v0);
 *     yield(() -> from(v1, v0 + v1));
 *     return this;
 *   }
 * }
 * Stream<Long> fibonacci = new Fibonacci().from(0, 1).stream();
 * }</pre>
 *
 * <p>Another potential use case may be to enhance the JDK {@link Stream#iterate} API with a
 * terminal condition. For example, we can simulate the <a
 * href="https://www.khanacademy.org/computing/computer-science/algorithms/intro-to-algorithms/a/a-guessing-game">
 * Guess the Number</a> game using binary search:
 *
 * <pre>{@code
 * class GuessTheNumber extends Iteration<Integer> {
 *   GuessTheNumber guess(int low, int high, int number) {
 *     if (low > high) {
 *       return this;
 *     }
 *     int mid = (low + high) / 2;
 *     yield(mid);               // yield the guess.
 *     if (guess < number) {
 *       yield(() -> guess(mid + 1, high, number));
 *     } else if (guess > number) {
 *       yield(() -> guess(low, mid - 1, number));
 *     }
 *     return this;
 *   }
 * }
 *
 * static Stream<Integer> play(int max, int number) {
 *   return new GuessTheNumber().guess(1, max, number).stream();
 * }
 * }</pre>
 *
 * Calling {@code play(9, 8)} will generate a stream of {@code [5, 7, 8]} each being a guess during
 * the binary search process, in order.
 *
 * <p>While not required, users are encouraged to create a subclass and then be able to call {@code
 * yield()} as if it were a keyword.
 *
 * <p>Keep in mind that, unlike {@code return} or {@code System.out.println()}, {@code yield()} is
 * lazy and does not evaluate until the stream iterates over it. So it's critical that <em>all side
 * effects</em> should be wrapped inside {@code Continuation} objects passed to {@code yield()}.
 *
 * <p>On the other hand, unlike C#'s "yield return" keyword, {@code yield()} is a normal Java method
 * and doesn't "return" the control to the Stream caller. Laziness is achieved by wrapping code
 * block inside the {@code Continuation} lambda.
 *
 * <p>This class and the generated streams are stateful and not safe to be used in multi-threads.
 *
 * <p>Like most iterative algorithms, yielding is implemented using a stack. No threads or
 * synchronization is used.
 *
 * <p>Nulls are not allowed.
 *
 * @since 4.4
 */
public class Iteration<T> {
  private final Deque<Object> stack = new ArrayDeque<>();
  private final Deque<Object> stackFrame = new ArrayDeque<>(8);
  private final AtomicBoolean streamed = new AtomicBoolean();

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

  /**
   * Returns the stream that iterates through the {@link #yield yielded} elements.
   *
   * <p>Because an {@code Iteration} instance is stateful and mutable, {@code stream()} can be
   * called at most once per instance.
   *
   * @throws IllegalStateException if {@code stream()} has already been called.
   */
  public final Stream<T> stream() {
    if (streamed.getAndSet(true)) {
      throw new IllegalStateException("Iteration already streamed.");
    }
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
    for (Object top = stackFrame.poll(); ; top = stackFrame.poll()) {
      if (top == null) {
        top = stack.poll();
      } else if (!stackFrame.isEmpty()) {
        stack.push(top);
        continue;
      }
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

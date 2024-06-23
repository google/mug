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
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Transforms eager, recursive algorithms into <em>lazy</em> streams. {@link #generate generate()}
 * is used to <a href="https://en.wikipedia.org/wiki/Generator_(computer_programming)">generate</a>
 * a sequence of values; and {@link #yield yield()} is used to yield control back to the stream,
 * with elements lazily generated on-demand.
 *
 * <p>{@code Iteration} can be used to adapt iterative or recursive algorithms to lazy streams. The
 * size of the stack is O(1) and execution is deferred.
 *
 * <p>For example, if you have a list API with pagination support, the following code retrieves all
 * pages eagerly:
 *
 * <pre>{@code
 * ImmutableList<Foo> listAllFoos() {
 *   ImmutableList.Builder<Foo> builder = ImmutableList.builder();
 *   ListFooRequest.Builder request = ListFooRequest.newBuilder()...;
 *     do {
 *       ListFooResponse response = service.listFoos(request.build());
 *       builder.addAll(response.getFoos());
 *       request.setPageToken(response.getNextPageToken());
 *     } while (!request.getPageToken().isEmpty());
 *   return builder.build();
 * }
 * }</pre>
 *
 * To turn the above code into a lazy stream using Iteration, the key is to wrap the recursive
 * calls into a lambda and pass it to {@link #yield(Continuation) yield(() -> recursiveCall())}.
 * This allows callers to short-circuit when they need to:
 *
 * <pre>{@code
 * Stream<Foo> listAllFoos() {
 *   class Pagination extends new Iteration<Foo>() {
 *     Pagination paginate(ListFooRequest request) {
 *       ListFooResponse response = service.listFoos(request);
 *       generateAll(response.getFoos());
 *       String nextPage = response.getNextPageToken();
 *       if (!nextPage.isEmpty()) {
 *         yield(() -> paginate(request.toBuilder().setNextPageToken(nextPage).build()));
 *       }
 *       return this;
 *     }
 *   }
 *   return new Pagination()
 *       .paginate(ListFooRequest.newBuilder()...build())
 *       .iterate();
 * }
 * }</pre>
 *
 * <p>Another common use case is to traverse recursive data structures lazily. Imagine if you have a
 * recursive binary tree traversal algorithm:
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
 * Instead of traversing eagerly and hard coding {@code System.out.println()}, it can be intuitively
 * transformed to a lazy stream, again, by wrapping the recursive {@code inOrder()} calls in a
 * lambda and passing it to {@code yeidl()}:
 *
 * <pre>{@code
 * class DepthFirst<T> extends Iteration<T> {
 *   DepthFirst<T> inOrder(Tree<T> tree) {
 *     if (tree == null) return this;
 *     yield(() -> inOrder(tree.left));
 *     generate(tree.value);
 *     yield(() -> inOrder(tree.right));
 *   }
 * }
 *
 * new DepthFirst<>()
 *     .inOrder(root)
 *     .iterate()
 *     .forEachOrdered(System.out::println);
 * }</pre>
 *
 * <p>One may ask why not use {@code flatMap()} like the following?
 *
 * <pre>{@code
 * <T> Stream<T> inOrder(Tree<T> tree) {
 *  if (tree == null) return Stream.empty();
 *  return Stream.of(inOrder(tree.left), Stream.of(tree.value), inOrder(tree.right))
 *      .flatMap(identity());
 * }
 * }</pre>
 *
 * This unfortunately doesn't scale, for two reasons: <ol>
 * <li>The code will recursively call {@code inOrder()} all the way from the root node to the leaf
 *     node. If the tree is deep, you may run into stack overflow error.
 * <li>{@code flatMap()} was not lazy in JDK 8. While it was later fixed in JDK 10 and backported
 *     to JDK 8, the JDK 8 you use may not carry the fix.
 * </ol>
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
 *       generate(node);
 *     }
 *     return this;
 *   }
 * }
 *
 * new DepthFirst<>()
 *     .postOrder(startNode)
 *     .iterate()
 *     .forEachOrdered(System.out::println);
 * }</pre>
 *
 * <p>If transforming tail-recursive algorithms, the space requirement is O(1) and execution is
 * deferred.
 *
 * <p>While not required, users are encouraged to create a subclass and then be able to call {@code
 * yield()} as if it were a keyword.
 *
 * <p>Keep in mind that, unlike {@code return} or {@code System.out.println()}, {@code yield()} is
 * lazy and does not evaluate until the stream iterates over it. So it's critical that <em>all side
 * effects</em> should be wrapped inside {@code Continuation} objects passed to {@code yield()}.
 *
 * <p>Unlike Python's yield statement or C#'s yield return, this {@code yield()} is a normal Java
 * method. It doesn't "return" execution to the caller. Laziness is achieved by wrapping code block
 * inside the {@code Continuation} lambda.
 *
 * <p>Like most manual iterative adaptation of recursive algorithms, yielding is implemented using
 * a stack. No threads or synchronization is used.
 *
 * <p>This class is not threadsafe.
 *
 * <p>Nulls are not allowed.
 *
 * @since 4.4
 */
public class Iteration<T> {
  private final Deque<Object> stack = new ArrayDeque<>();
  private final Deque<Object> stackFrame = new ArrayDeque<>(8);
  private final AtomicBoolean started = new AtomicBoolean();

  /**
   * Generates {@code element} to the result stream.
   *
   * @since 8.1
   */
  public final Iteration<T> generate(T element) {
    if (element instanceof Continuation) {
      throw new IllegalArgumentException("Do not stream Continuation objects");
    }
    stackFrame.push(element);
    return this;
  }

  /**
   * Generates all of {@code elements} to the result stream.
   *
   * @since 8.1
   */
  public final Iteration<T> generate(Iterable<? extends T> elements) {
    for (T element : elements) {
      generate(element);
    }
    return this;
  }

  /**
   * Yields {@code element} to the result stream.
   *
   * @deprecated use {@link #generate} instead
   */
  @Deprecated
  public final Iteration<T> yield(T element) {
    return generate(element);
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
   * Yields to the stream the result of {@code computation}. Upon evaluation, also passes the
   * computation result to {@code consumer}. Useful when the computation result of a recursive call
   * is needed. For example, if you have a recursive algorithm to sum all node values of a tree:
   *
   * <pre>{@code
   * int sumNodeValues(Tree tree) {
   *   if (tree == null) return 0;
   *   return tree.value + sumNodeValues(tree.left) + sumNodeValues(tree.right);
   * }
   * }</pre>
   *
   * It can be transformed to iterative stream as in:
   *
   * <pre>{@code
   * class SumNodeValues extends Iteration<Integer> {
   *   SumNodeValues sum(Tree tree, AtomicInteger result) {
   *     if (tree == null) return this;
   *     AtomicInteger leftSum = new AtomicInteger();
   *     AtomicInteger rightSum = new AtomicInteger();
   *     yield(() -> sum(tree.left, leftSum));
   *     yield(() -> sum(tree.right, rightSum));
   *     yield(() -> tree.value + leftSum.get() + rightSum.get(), result::set);
   *     return this;
   *   }
   * }
   *
   * Stream<Integer> sums =
   *     new SumNodeValues()
   *         .sum((root: 1, left: 2, right: 3), new AtomicInteger())
   *         .iterate();
   *
   *     => [2, 3, 6]
   * }</pre>
   *
   * @since 4.5
   */
  public final Iteration<T> yield(
      Supplier<? extends T> computation, Consumer<? super T> consumer) {
    requireNonNull(computation);
    requireNonNull(consumer);
    return this.yield(() -> {
      T result = computation.get();
      consumer.accept(result);
      generate(result);
    });
  }

  /**
   * Yields all of {@code elements} to the result stream.
   *
   * @since 5.4
   * @deprecated use {@link #generateAll} instead
   */
  @Deprecated
  public final Iteration<T> yieldAll(Iterable<? extends T> elements) {
    return generate(elements);
  }

  /**
   * Starts iteration over the {@link #generate generated} elements.
   *
   * <p>Because an {@code Iteration} instance is stateful and mutable, {@code iterate()} can be
   * called at most once per instance.
   *
   * @throws IllegalStateException if {@code iterate()} has already been called.
   * @since 4.5
   */
  public final Stream<T> iterate() {
    if (started.getAndSet(true)) {
      throw new IllegalStateException("Iteration already started.");
    }
    return whileNotNull(this::next);
  }

  /**
   * Encapsulates recursive iteration or a lazy block of code with side-effect.
   *
   * <p>Note that if after a {@link #yield(Continuation) yielded} recursive iteration, the
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
    for (; ; ) {
      Object top = poll();
      if (top instanceof Continuation) {
        ((Continuation) top).run();
      } else {
        @SuppressWarnings("unchecked") // we only put either T or Continuation in the stack.
        T element = (T) top;
        return element;
      }
    }
  }

  private Object poll() {
    Object top = stackFrame.poll();
    if (top == null) {
      return stack.poll();
    }
    for (Object second = stackFrame.poll(); second != null; second = stackFrame.poll()) {
      stack.push(top);
      top = second;
    }
    return top;
  }
}

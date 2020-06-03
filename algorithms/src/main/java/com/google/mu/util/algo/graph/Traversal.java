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
package com.google.mu.util.algo.graph;

import static com.google.mu.util.stream.MoreStreams.generate;
import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implements generic graph traversal algorithms ({@link #preOrderFrom pre-order},
 * and {@link #postOrderFrom post-order}).
 *
 * <p>None of these streams are safe to run in parallel.
 *
 * @since 3.9
 */
public final class Traversal {
  /**
   * Starts from {@code initial} and traverse depth first in pre-order by
   * using {@code findSuccessors} function iteratively.
   */
  public static <T> Stream<T> preOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    //return new PreOrder<T>(findSuccessors).startingFrom(initial);
    return new DepthFirst<>(findSuccessors).preOrder(initial);
  }

  /**
   * Starts from {@code initial} and traverse depth first in post-order by
   * using {@code findSuccessors} function iteratively.
   */
  public static <T> Stream<T> postOrderFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    return new DepthFirst<>(findSuccessors).postOrder(initial);
  }

  /**
   * Starts from {@code initial} and traverse breadth first by using {@code findSuccessors}
   * function iteratively.
   */
  public static <T> Stream<T> breadthFirstFrom(
      T initial, Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
    requireNonNull(initial);
    requireNonNull(findSuccessors);
    Set<T> seen = new HashSet<>();
    seen.add(initial);
    return generate(
        initial,
        n -> findSuccessors.apply(n).peek(Objects::requireNonNull).filter(seen::add));
  }

  private static final class DepthFirst<T> implements Consumer<T> {
    private final Function<? super T, ? extends Stream<? extends T>> findSuccessors;
    private final Deque<Group> stack = new ArrayDeque<>();
    private final Set<T> seen = new HashSet<>();
    private T advancedResult;

    DepthFirst(Function<? super T, ? extends Stream<? extends T>> findSuccessors) {
      this.findSuccessors = requireNonNull(findSuccessors);
    }

    Stream<T> preOrder(T node) {
      stack.push(new Group(node, Spliterators.emptySpliterator()));
      seen.add(node);
      return whileNotEmpty(stack)
          .map(Deque::pop)
          .map(Group::removeInPreOrder);
    }

    Stream<T> postOrder(T node) {
      stack.push(new Group(node));
      seen.add(node);
      return whileNotEmpty(stack)
          .map(Deque::pop)
          .map(Group::removeInPostOrder);
    }

    @Override public void accept(T value) {
      this.advancedResult = requireNonNull(value);
    }

    private final class Group {
      private final T first;
      private final Spliterator<? extends T> successors;

      Group(T head) {
        this(head, findSuccessors.apply(head).spliterator());
      }

      Group(T first, Spliterator<? extends T> successors) {
        this.first = requireNonNull(first);
        this.successors = requireNonNull(successors);
      }

      T removeInPostOrder() {
        for (Group family = this; ;) {
          if (family.nextSuccessor() == null) return family.first;
          stack.push(family);
          family = new Group(advancedResult);
        }
      }

      T removeInPreOrder() {
        T nextPeer = nextSuccessor();
        if (nextPeer != null) {
          stack.push(new Group(nextPeer, successors));
        }
        Spliterator<? extends T> sons = findSuccessors.apply(first).spliterator();
        T firstSon = next(sons);
        if (firstSon != null) {
          stack.push(new Group(firstSon, sons));
        }
        return first;
      }

      /** next successor or null if no more. */
      private T nextSuccessor() {
        return next(successors);
      }

      private T next(Spliterator<? extends T> spliterator) {
        while (spliterator.tryAdvance(DepthFirst.this)) {
          if (seen.add(advancedResult)) return advancedResult;
        }
        return null;
      }
    }
  }

  private Traversal() {}
}

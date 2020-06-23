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
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class GraphWalker<N> extends Walker<N> {
  private final Supplier<Walk<N>> newWalk;

  GraphWalker(Supplier<Walk<N>> newWalk) {
    this.newWalk = newWalk;
  }

  public Stream<N> preOrderFrom(Iterable<? extends N> startNodes) {
    return newWalk.get().preOrder(startNodes);
  }

  /**
   * Starts from {@code startNodes} and walks depth first in post-order.
   *
   * <p>The returned stream may be infinite if the graph has infinite breadth. The stream can still
   * be short-circuited to consume a limited number of nodes during traversal.
   *
   * <p>The stream may result in infinite loop when traversing through a node with infinite depth.
   */
  public Stream<N> postOrderFrom(Iterable<? extends N> startNodes) {
    return newWalk.get().postOrder(startNodes);
  }

  /**
   * Starts from {@code startNodes} and walks in breadth-first order.
   *
   * <p>The returned stream may be infinite if the graph has infinite depth or infinite breadth, or
   * both. The stream can still be short-circuited to consume a limited number of nodes during
   * traversal.
   */
  public final Stream<N> breadthFirstFrom(Iterable<? extends N> startNodes) {
    return newWalk.get().breadthFirst(startNodes);
  }

  static final class Walk<N> implements Consumer<N> {
    private final Function<? super N, ? extends Stream<? extends N>> findSuccessors;
    private final Predicate<? super N> tracker;
    private final Deque<Spliterator<? extends N>> horizon = new ArrayDeque<>();
    private N visited;

    Walk(
        Function<? super N, ? extends Stream<? extends N>> findSuccessors,
        Predicate<? super N> tracker) {
      this.findSuccessors = findSuccessors;
      this.tracker = tracker;
    }

    @Override
    public void accept(N value) {
      this.visited = requireNonNull(value);
    }

    Stream<N> breadthFirst(Iterable<? extends N> startNodes) {
      horizon.add(startNodes.spliterator());
      return topDown(Queue::add);
    }

    Stream<N> preOrder(Iterable<? extends N> startNodes) {
      horizon.push(startNodes.spliterator());
      return topDown(Deque::push);
    }

    Stream<N> postOrder(Iterable<? extends N> startNodes) {
      horizon.push(startNodes.spliterator());
      Deque<N> roots = new ArrayDeque<>();
      return whileNotNull(() -> {
        while (visitNext()) {
          N next = visited;
          Stream<? extends N> successors = findSuccessors.apply(next);
          if (successors == null) return next;
          horizon.push(successors.spliterator());
          roots.push(next);
        }
        return roots.poll();
      });
    }

    private Stream<N> topDown(InsertionOrder order) {
      return whileNotNull(() -> {
        do {
          if (visitNext()) {
            N next = visited;
            Stream<? extends N> successors = findSuccessors.apply(next);
            if (successors != null) order.insertInto(horizon, successors.spliterator());
            return next;
          }
        } while (!horizon.isEmpty());
        return null; // no more element
      });
    }

    private boolean visitNext() {
      Spliterator<? extends N> top = horizon.getFirst();
      while (top.tryAdvance(this)) {
        if (tracker.test(visited)) return true;
      }
      horizon.removeFirst();
      return false;
    }
  }

  private interface InsertionOrder {
    <N> void insertInto(Deque<N> deque, N value);
  }
}

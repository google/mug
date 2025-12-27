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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.stream.IterationTest.Tree.tree;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.testing.ClassSanityTester;

public class IterationTest {
  @Test public void iteration_empty() {
    assertThat(new Iteration<Object>().iterate()).isEmpty();
  }

  @Test public void emit_eagerElements() {
    assertThat(new Iteration<>().emit(1).emit(2).iterate()).containsExactly(1, 2).inOrder();
  }

  @Test public void emit_lazyElements() {
    Iteration<Integer> iteration = new Iteration<>();
    assertThat(iteration.forEachLazily(Stream.of(1, 2, 3), iteration::emit).iterate())
        .containsExactly(1, 2, 3).inOrder();
  }

  @Test public void preOrder_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h")));
    assertThat(preOrderFrom(tree))
        .containsExactly("a", "b", "c", "d", "e", "f", "g", "h")
        .inOrder();
  }

  @Test public void inOrder_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h")));
    assertThat(inOrderFrom(tree))
        .containsExactly("c", "b", "e", "d", "a", "g", "h", "f")
        .inOrder();
  }

  @Test public void postOrder_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h"))
            .setRight("i"));
    assertThat(postOrderFrom(tree))
        .containsExactly("c", "e", "d", "b", "h", "g", "i", "f", "a")
        .inOrder();
  }

  @Test public void oneTimeIteration() {
    DepthFirst<String> iteration = new DepthFirst<>();
    iteration.iterate();
    assertThrows(IllegalStateException.class, iteration::iterate);
  }

  @Test public void nullChecks() {
    new ClassSanityTester().testNulls(Iteration.class);
  }

  private static <T> Stream<T> preOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().preOrder(tree).iterate();
  }

  private static <T> Stream<T> inOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().inOrder(tree).iterate();
  }

  private static <T> Stream<T> postOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().postOrder(tree).iterate();
  }

  @Test public void sumStream() {
    assertThat(computeSum(tree(1))).containsExactly(1);
    assertThat(computeSum(tree(1).setLeft(tree(2)).setRight(tree(3).setLeft(5))))
        .containsExactly(2, 5, 8, 11)
        .inOrder();
  }

  private static Stream<Integer> computeSum(Tree<Integer> tree) {
    class SumNodes extends Iteration<Integer> {
      SumNodes sum(Tree<Integer> tree, AtomicInteger result) {
        if (tree == null) return this;
        AtomicInteger fromLeft = new AtomicInteger();
        AtomicInteger fromRight = new AtomicInteger();
       lazily(() -> sum(tree.left(), fromLeft));
       lazily(() -> sum(tree.right(), fromRight));
       lazily(() -> tree.value() + fromLeft.get() + fromRight.get(), result::set);
        return this;
      }
    }
    return new SumNodes().sum(tree, new AtomicInteger()).iterate();
  }

  private static final class DepthFirst<T> extends Iteration<T> {
    DepthFirst<T> preOrder(Tree<T> tree) {
      if (tree == null) return this;
      emit(tree.value());
     lazily(() -> preOrder(tree.left()));
     lazily(() -> preOrder(tree.right()));
      return this;
    }

    DepthFirst<T> inOrder(Tree<T> tree) {
      if (tree == null) return this;
     lazily(() -> inOrder(tree.left()));
      emit(tree.value());
     lazily(() -> inOrder(tree.right()));
      return this;
    }

    DepthFirst<T> postOrder(Tree<T> tree) {
      if (tree == null) return this;
     lazily(() -> postOrder(tree.left()));
     lazily(() -> postOrder(tree.right()));
      emit(tree.value());
      return this;
    }
  }

  interface Tree<T> {
    static <T> Node<T> tree(T value) {
      return new Node<>(value);
    }

    static Tree<Integer> leftFrom(int value) {
      return new Tree<Integer>() {
        @Override public Integer value() {
          return value;
        }
        @Override public Tree<Integer> left() {
          return leftFrom(value + 1);
        }
        @Override public Tree<Integer> right() {
          return null;
        }
      };
    }

    static Tree<Integer> rightFrom(int value) {
      return new Tree<Integer>() {
        @Override public Integer value() {
          return value;
        }
        @Override public Tree<Integer> left() {
          return null;
        }
        @Override public Tree<Integer> right() {
          return rightFrom(value + 1);
        }
      };
    }

    static Tree<Integer> leftThenRight(int value) {
      return new Tree<Integer>() {
        @Override public Integer value() {
          return value;
        }
        @Override public Tree<Integer> left() {
          return rightThenLeft(value + 1);
        }
        @Override public Tree<Integer> right() {
          return null;
        }
      };
    }

    static Tree<Integer> rightThenLeft(int value) {
      return new Tree<Integer>() {
        @Override public Integer value() {
          return value;
        }
        @Override public Tree<Integer> left() {
          return null;
        }
        @Override public Tree<Integer> right() {
          return leftThenRight(value + 1);
        }
      };
    }

    T value();

    Tree<T> left();

    Tree<T> right();
  }

  static class Node<T> implements Tree<T> {
    private Tree<T> left;
    private Tree<T> right;
    private final T value;

    Node(T value) {
      this.value = value;
    }

    @Override public T value() {
      return value;
    }

    @Override public String toString() {
      return String.valueOf(value);
    }

    Node<T> setLeft(Tree<T> left) {
      this.left = left;
      return this;
    }

    Node<T> setRight(Tree<T> right) {
      this.right = right;
      return this;
    }

    Node<T> setLeft(T left) {
      return setLeft(tree(left));
    }

    Node<T> setRight(T right) {
      return setRight(tree(right));
    }

    @Override public Tree<T> left() {
      return left;
    }

    @Override public Tree<T> right() {
      return right;
    }
  }

  @Test public void binarySearchTrials() {
    assertThat(guessTheNumber(9, 8))
        .containsExactly(5, 7, 8)
        .inOrder();
    assertThat(guessTheNumber(9, 1))
      .containsExactly(5, 2, 1)
      .inOrder();
    assertThat(guessTheNumber(100, 200))
      .containsExactly(50, 75, 88, 94, 97, 99, 100)
      .inOrder();
  }

  private static final class GuessTheNumber extends Iteration<Integer> {
    GuessTheNumber guess(int low, int high, int number) {
       if (low > high) {
         return this;
       }
       int mid = (low + high) / 2;
       emit(mid);
       if (mid < number) {
        lazily(() -> guess(mid + 1, high, number));
       } else if (mid > number) {
        lazily(() -> guess(low, mid - 1, number));
       }
       return this;
     }
   }

   static Stream<Integer> guessTheNumber(int max, int number) {
     return new GuessTheNumber().guess(1, max, number).iterate();
   }
}

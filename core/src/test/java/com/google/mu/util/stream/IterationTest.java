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

import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.testing.NullPointerTester;

public class IterationTest {
  @Test
  public void iteration_empty() {
    assertThat(new Iteration<Object>().stream()).isEmpty();
  }

  @Test
  public void preOrder_deep() {
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

  @Test
  public void inOrder_deep() {
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

  @Test
  public void postOrder_deep() {
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

  @Test
  public void nullChecks() {
    new NullPointerTester().testAllPublicInstanceMethods(new Iteration<>());
  }

  private static <T> Stream<T> preOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().preOrder(tree).stream();
  }

  private static <T> Stream<T> inOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().inOrder(tree).stream();
  }

  private static <T> Stream<T> postOrderFrom(Tree<T> tree) {
    return new DepthFirst<T>().postOrder(tree).stream();
  }

  private static final class DepthFirst<T> extends Iteration<T> {
    DepthFirst<T> preOrder(Tree<T> tree) {
      if (tree == null) return this;
      yield(tree.value());
      yield(() -> preOrder(tree.left()));
      yield(() -> preOrder(tree.right()));
      return this;
    }

    DepthFirst<T> inOrder(Tree<T> tree) {
      if (tree == null) return this;
      yield(() -> inOrder(tree.left()));
      yield(tree.value());
      yield(() -> inOrder(tree.right()));
      return this;
    }

    DepthFirst<T> postOrder(Tree<T> tree) {
      if (tree == null) return this;
      yield(() -> postOrder(tree.left()));
      yield(() -> postOrder(tree.right()));
      yield(tree.value());
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
}

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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.graph.BinaryTreeWalkerTest.Tree.tree;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

public class BinaryTreeWalkerTest {
  @Test public void preOrder_noRoot() {
    assertThat(Tree.<String>walker().preOrderFrom().map(Tree::value))
        .isEmpty();
  }

  @Test public void preOrder_singleNode() {
    assertThat(Tree.<String>walker().preOrderFrom(tree("foo")).map(Tree::value))
        .containsExactly("foo");
  }

  @Test public void preOrder_leftNodeIsNull() {
    Tree<String> tree = tree("foo").setRight("right");
    assertThat(Tree.<String>walker().preOrderFrom(tree).map(Tree::value))
        .containsExactly("foo", "right")
        .inOrder();
  }

  @Test public void preOrder_rightNodeIsNull() {
    Tree<String> tree = tree("foo").setLeft("left");
    assertThat(Tree.<String>walker().preOrderFrom(tree).map(Tree::value))
        .containsExactly("foo", "left")
        .inOrder();
  }

  @Test public void preOrder_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h")));
    assertThat(Tree.<String>walker().preOrderFrom(tree).map(Tree::value))
        .containsExactly("a", "b", "c", "d", "e", "f", "g", "h")
        .inOrder();
  }

  @Test public void preOrder_infiniteLeft() {
    assertThat(Tree.<Integer>walker().preOrderFrom(Tree.leftFrom(1)).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void preOrder_infiniteRight() {
    assertThat(Tree.<Integer>walker().preOrderFrom(Tree.rightFrom(1)).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void preOrder_infiniteAlternating() {
    assertThat(Tree.<Integer>walker().preOrderFrom(Tree.leftThenRight(1)).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void preOrder_twoRoots() {
    Tree<String> tree1 = tree("a")
        .setLeft("b")
        .setRight(tree("c").setRight("d"));
    Tree<String> tree2 = tree("e")
        .setLeft(tree("f")
            .setLeft("g")
            .setRight("h"));
    assertThat(Tree.<String>walker().preOrderFrom(tree1, tree2).map(Tree::value))
        .containsExactly("a", "b", "c", "d", "e", "f", "g", "h")
        .inOrder();
  }
  @Test public void inOrder_noRoot() {
    assertThat(Tree.<String>walker().inOrderFrom().map(Tree::value))
        .isEmpty();
  }

  @Test public void inOrder_singleNode() {
    assertThat(Tree.<String>walker().inOrderFrom(tree("foo")).map(Tree::value))
        .containsExactly("foo");
  }

  @Test public void inOrder_leftNodeIsNull() {
    Tree<String> tree = tree("foo").setRight("right");
    assertThat(Tree.<String>walker().inOrderFrom(tree).map(Tree::value))
        .containsExactly("foo", "right")
        .inOrder();
  }

  @Test public void inOrder_rightNodeIsNull() {
    Tree<String> tree = tree("foo").setLeft("left");
    assertThat(Tree.<String>walker().inOrderFrom(tree).map(Tree::value))
        .containsExactly("left", "foo")
        .inOrder();
  }

  @Test public void inOrder_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h")));
    assertThat(Tree.<String>walker().inOrderFrom(tree).map(Tree::value))
        .containsExactly("c", "b", "e", "d", "a", "g", "h", "f")
        .inOrder();
  }

  @Test public void inOrder_twoRoots() {
    Tree<String> tree1 = tree("a")
        .setLeft("b")
        .setRight(tree("c").setRight("d"));
    Tree<String> tree2 = tree("e")
        .setLeft(tree("f")
            .setLeft("h")
            .setRight("g"));
    assertThat(Tree.<String>walker().inOrderFrom(tree1, tree2).map(Tree::value))
        .containsExactly("b", "a", "c", "d", "h", "f", "g", "e")
        .inOrder();
  }

  @Test public void inOrder_infiniteLeft() {
    assertThat(Tree.<Integer>walker().inOrderFrom(Tree.leftFrom(1)).map(Tree::value).limit(0))
        .isEmpty();
  }

  @Test public void inOrder_infiniteRight() {
    assertThat(Tree.<Integer>walker().inOrderFrom(Tree.rightFrom(1)).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void inOrder_infiniteAlternating() {
    assertThat(Tree.<Integer>walker().inOrderFrom(Tree.leftThenRight(1)).map(Tree::value).limit(5))
        .containsExactly(2, 4, 6, 8, 10)
        .inOrder();
  }

  @Test public void postOrder_noRoot() {
    assertThat(Tree.<String>walker().postOrderFrom().map(Tree::value))
        .isEmpty();
  }

  @Test public void postOrder_singleNode() {
    assertThat(Tree.<String>walker().postOrderFrom(tree("foo")).map(Tree::value))
        .containsExactly("foo");
  }

  @Test public void postOrder_leftNodeIsNull() {
    Tree<String> tree = tree("foo").setRight("right");
    assertThat(Tree.<String>walker().postOrderFrom(tree).map(Tree::value))
        .containsExactly("right", "foo")
        .inOrder();
  }

  @Test public void postOrder_rightNodeIsNull() {
    Tree<String> tree = tree("foo").setLeft("left");
    assertThat(Tree.<String>walker().inOrderFrom(tree).map(Tree::value))
        .containsExactly("left", "foo")
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
    assertThat(Tree.<String>walker().postOrderFrom(tree).map(Tree::value))
        .containsExactly("c", "e", "d", "b", "h", "g", "i", "f", "a")
        .inOrder();
  }

  @Test public void postOrder_twoRoots() {
    Tree<String> tree1 = tree("a")
        .setLeft("b")
        .setRight(tree("c").setRight("d"));
    Tree<String> tree2 = tree("e")
        .setLeft(tree("f")
            .setLeft("g")
            .setRight("h"));
    assertThat(Tree.<String>walker().postOrderFrom(tree1, tree2).map(Tree::value))
        .containsExactly("b", "d", "c", "a", "g", "h", "f", "e")
        .inOrder();
  }

  @Test public void postOrder_infiniteLeft() {
    assertThat(Tree.<Integer>walker().postOrderFrom(Tree.leftFrom(1)).map(Tree::value).limit(0))
        .isEmpty();
  }

  @Test public void postOrder_infiniteRight() {
    assertThat(Tree.<Integer>walker().postOrderFrom(Tree.rightFrom(1)).map(Tree::value).limit(0))
        .isEmpty();
  }

  @Test public void postOrder_alternating() {
    assertThat(Tree.<Integer>walker().postOrderFrom(Tree.leftThenRight(1)).map(Tree::value).limit(0))
        .isEmpty();
  }

  @Test public void postOrder_finiteLeft_infiniteRight() {
    Tree<Integer> tree = tree(1).setLeft(2).setRight(Tree.leftFrom(3));
    assertThat(Tree.<Integer>walker().postOrderFrom(tree).map(Tree::value).limit(1))
        .containsExactly(2)
        .inOrder();
  }

  @Test public void breadthFirst_noRoot() {
    assertThat(Tree.<String>walker().breadthFirstFrom().map(Tree::value))
        .isEmpty();
  }

  @Test public void breadthFirst_singleNode() {
    Tree<String> tree = tree("foo");
    assertThat(Tree.<String>walker().breadthFirstFrom(tree).map(Tree::value))
        .containsExactly("foo");
  }

  @Test public void breadthFirst_leftNodeIsNull() {
    Tree<String> tree = tree("foo").setRight("right");
    assertThat(Tree.<String>walker().breadthFirstFrom(tree).map(Tree::value))
        .containsExactly("foo", "right")
        .inOrder();
  }

  @Test public void breadthFirst_rightNodeIsNull() {
    Tree<String> tree = tree("foo").setLeft("left");
    assertThat(Tree.<String>walker().breadthFirstFrom(tree).map(Tree::value))
        .containsExactly("foo", "left")
        .inOrder();
  }

  @Test public void breadthFirst_deep() {
    Tree<String> tree = tree("a")
        .setLeft(tree("b")
            .setLeft("c")
            .setRight(tree("d").setLeft("e")))
        .setRight(tree("f")
            .setLeft(tree("g").setRight("h"))
            .setRight("i"));
    assertThat(Tree.<String>walker().breadthFirstFrom(tree).map(Tree::value))
        .containsExactly("a", "b", "f", "c", "d", "g", "i", "e", "h")
        .inOrder();
  }

  @Test public void breadthFirst_twoRoots() {
    Tree<String> tree1 = tree("a")
        .setLeft("b")
        .setRight(tree("c").setRight("d"));
    Tree<String> tree2 = tree("e")
        .setLeft(tree("f")
            .setLeft("g")
            .setRight("h"));
    assertThat(Tree.<String>walker().breadthFirstFrom(tree1, tree2).map(Tree::value))
        .containsExactly("a", "e", "b", "c", "f", "d", "g", "h")
        .inOrder();
  }

  @Test public void breadthFirst_infiniteLeft() {
    Tree<Integer> tree = Tree.leftFrom(1);
    assertThat(Tree.<Integer>walker().breadthFirstFrom(tree).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void breadthFirst_infiniteRight() {
    Tree<Integer> tree = Tree.rightFrom(1);
    assertThat(Tree.<Integer>walker().breadthFirstFrom(tree).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void breadthFirst_alternating() {
    Tree<Integer> tree = Tree.leftThenRight(1);
    assertThat(Tree.<Integer>walker().breadthFirstFrom(tree).map(Tree::value).limit(3))
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test public void nullProhibited() {
    Tree<Integer> nullNode = null;
    assertThrows(NullPointerException.class, () -> Tree.<Integer>walker().preOrderFrom(nullNode));
    assertThrows(NullPointerException.class, () -> Tree.<Integer>walker().postOrderFrom(nullNode));
    assertThrows(NullPointerException.class, () -> Tree.<Integer>walker().inOrderFrom(nullNode));
    assertThrows(NullPointerException.class, () -> Tree.<Integer>walker().breadthFirstFrom(nullNode));
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

    static <T> BinaryTreeWalker<Tree<T>> walker() {
      return Walker.inBinaryTree(Tree::left, Tree::right);
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

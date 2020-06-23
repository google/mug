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

import org.junit.Test;

public class BinaryTreeWalkerTest {
  @Test
  public void preOrder_nullTree() {
    assertThat(BinaryTree.traverser().preOrderFrom(null)).isEmpty();
  }

  @Test
  public void preOrder_singleNode() {
    assertThat(BinaryTree.traverser().preOrderFrom(new BinaryTree("foo")).map(BinaryTree::toString))
        .containsExactly("foo");
  }

  @Test
  public void preOrder_leftNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setRight("right");
    assertThat(BinaryTree.traverser().preOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("foo", "right")
        .inOrder();
  }

  @Test
  public void preOrder_rightNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setLeft("left");
    assertThat(BinaryTree.traverser().preOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("foo", "left")
        .inOrder();
  }

  @Test
  public void preOrder_deep() {
    BinaryTree tree = new BinaryTree("a")
        .setLeft(new BinaryTree("b")
            .setLeft("c")
            .setRight(new BinaryTree("d").setLeft("e")))
        .setRight(new BinaryTree("f")
            .setLeft(new BinaryTree("g").setRight("h")));
    assertThat(BinaryTree.traverser().preOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("a", "b", "c", "d", "e", "f", "g", "h")
        .inOrder();
  }

  @Test
  public void inOrder_nullTree() {
    assertThat(BinaryTree.traverser().inOrderFrom(null)).isEmpty();
  }

  @Test
  public void inOrder_singleNode() {
    assertThat(BinaryTree.traverser().inOrderFrom(new BinaryTree("foo")).map(BinaryTree::toString))
        .containsExactly("foo");
  }

  @Test
  public void inOrder_leftNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setRight("right");
    assertThat(BinaryTree.traverser().inOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("foo", "right")
        .inOrder();
  }

  @Test
  public void inOrder_rightNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setLeft("left");
    assertThat(BinaryTree.traverser().inOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("left", "foo")
        .inOrder();
  }

  @Test
  public void inOrder_deep() {
    BinaryTree tree = new BinaryTree("a")
        .setLeft(new BinaryTree("b")
            .setLeft("c")
            .setRight(new BinaryTree("d").setLeft("e")))
        .setRight(new BinaryTree("f")
            .setLeft(new BinaryTree("g").setRight("h")));
    assertThat(BinaryTree.traverser().inOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("c", "b", "e", "d", "a", "g", "h", "f")
        .inOrder();
  }

  @Test
  public void postOrder_nullTree() {
    assertThat(BinaryTree.traverser().postOrderFrom(null)).isEmpty();
  }

  @Test
  public void postOrder_leftNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setRight("right");
    assertThat(BinaryTree.traverser().postOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("right", "foo")
        .inOrder();
  }

  @Test
  public void postOrder_rightNodeIsNull() {
    BinaryTree tree = new BinaryTree("foo").setLeft("left");
    assertThat(BinaryTree.traverser().inOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("left", "foo")
        .inOrder();
  }

  @Test
  public void postOrder_deep() {
    BinaryTree tree = new BinaryTree("a")
        .setLeft(new BinaryTree("b")
            .setLeft("c")
            .setRight(new BinaryTree("d").setLeft("e")))
        .setRight(new BinaryTree("f")
            .setLeft(new BinaryTree("g").setRight("h"))
            .setRight("i"));
    assertThat(BinaryTree.traverser().postOrderFrom(tree).map(BinaryTree::toString))
        .containsExactly("c", "e", "d", "b", "h", "g", "i", "f", "a")
        .inOrder();
  }

  private static class BinaryTree {
    private BinaryTree left;
    private BinaryTree right;
    private final String value;

    BinaryTree(String value) {
      this.value = value;
    }

    static BinaryTreeWalker<BinaryTree> traverser() {
      return BinaryTreeWalker.inTree(BinaryTree::left, BinaryTree::right);
    }

    @Override public String toString() {
      return value;
    }

    BinaryTree setLeft(BinaryTree left) {
      this.left = left;
      return this;
    }

    BinaryTree setRight(BinaryTree right) {
      this.right = right;
      return this;
    }

    BinaryTree setLeft(String left) {
      return setLeft(new BinaryTree(left));
    }

    BinaryTree setRight(String right) {
      return setRight(new BinaryTree(right));
    }

    BinaryTree left() {
      return left;
    }

    BinaryTree right() {
      return right;
    }
  }
}

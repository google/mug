package com.google.mu.util.graph;

import static com.google.mu.util.stream.MoreStreams.whileNotEmpty;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Traverser for binary tree topology.
 *
 * <p>More efficient than {@link Walker} for binary trees, and supports
 * {@link #inOrderFrom in-order} traversal.
 *
 * <p>Null nodes are treated as empty tree.
 */
public final class BinaryTreeTraverser<N> {
  private final UnaryOperator<N> getLeft;
  private final UnaryOperator<N> getRight;

  private BinaryTreeTraverser(UnaryOperator<N> getLeft, UnaryOperator<N> getRight) {
    this.getLeft = requireNonNull(getLeft);
    this.getRight = requireNonNull(getRight);
  }

  /**
   * Returns a {@code BinaryTreeTraverser} for traversing in the binary tree topology
   * as observed by {@code getLeft} and {@code getRight} functions. Both functions
   * return null to indicate that there is no left or right child.
   *
   * <p>It's guaranteed that for any given node, {@code getLeft} and {@code getRight}
   * are called at most once.
   */
  public static <N> BinaryTreeTraverser<N> inTree(
      UnaryOperator<N> getLeft, UnaryOperator<N> getRight) {
    return new BinaryTreeTraverser<>(getLeft, getRight);
  }

  /**
   * Returns a lazy stream for in-order traversal from {@code root}. Empty stream is returned if
   * {@code root} is null.
   */
  public Stream<N> inOrderFrom(N root) {
    if (root == null) return Stream.empty();
    return new InOrder().add(root).stream();
  }

  /**
   * Returns a lazy stream for pre-order traversal from {@code root}. Empty stream is returned if
   * {@code root} is null.
   */
  public Stream<N> preOrderFrom(N root) {
    if (root == null) return Stream.empty();
    Deque<N> horizon = new ArrayDeque<>();
    horizon.push(root);
    return whileNotNull(horizon::poll)
        .peek(n -> {
          N left = getLeft.apply(n);
          N right = getRight.apply(n);
          if (right != null) horizon.push(right);
          if (left != null) horizon.push(left);
        });
  }

  /**
   * Returns a lazy stream for post-order traversal from {@code root}. Empty stream is returned if
   * {@code root} is null.
   */
  public Stream<N> postOrderFrom(N root) {
    if (root == null) return Stream.empty();
    return new PostOrder().add(root).stream();
  }

  private final class InOrder {
    private final Deque<N> horizon = new ArrayDeque<>();

    InOrder add(N root) {
      for (N n = root; n != null; n = getLeft.apply(n)) {
        horizon.push(n);
      }
      return this;
    }

    private void exploreRight(N node) {
      N right = getRight.apply(node);
      if (right != null) add(right);
    }

    Stream<N> stream() {
      return whileNotNull(horizon::poll).peek(this::exploreRight);
    }
  };

  private final class PostOrder {
    private final Deque<N> horizon = new ArrayDeque<>();
    private final BitSet ready = new BitSet();

    PostOrder add(N root) {
      for (N n = root; n != null; n = getLeft.apply(n)) {
        ready.clear(horizon.size());
        horizon.push(n);
      }
      return this;
    }

    private N remove() {
      for (; ;) {
        if (ready.get(horizon.size() - 1)) {
          return horizon.pop();
        }
        ready.set(horizon.size() - 1);
        N right = getRight.apply(horizon.getFirst());
        if (right != null) add(right);
      }
    }

    Stream<N> stream() {
      return whileNotEmpty(horizon).map(u -> remove());
    }
  }
}

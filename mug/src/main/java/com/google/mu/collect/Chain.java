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
package com.google.mu.collect;

import static com.google.mu.collect.InternalUtils.toImmutableList;
import static java.util.Objects.requireNonNull;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.stream.Stream;

import com.google.mu.util.graph.Walker;

/**
 * Immutable {@link List} implementation that supports O(1) concatenation.
 *
 * <p>At high level, this class provides similar behavior as {@link Stream#concat Stream.concat()}
 * or {@link com.google.common.collect.Iterables#concat Guava Iterables.concat()}, except it's not
 * recursive. That is, if your Chain is the result of 1 million concatenations, you won't run
 * into stack overflow error because under the hood, it's a heap-allocated immutable tree structure.
 *
 * <p>The expected use case is to concatenate lots of smaller {@code Chain}s using the {@code
 * concat()} methods to create the final Chain. O(n) materialization cost will be (lazily) incurred
 * upon the first time accessing the elements of the final Chain through the {@link List}
 * interface such as {@link List#get}, {@link List#equals}, {@link #toString} etc. You may also
 * want to copy the final Chain into a more conventional List such as {@link
 * com.google.common.collect.ImmutableList#copyOf Guava ImmutableList}.
 *
 * <p>On the other hand, it's inefficient to materialize, concatenate then materialize the
 * concatenated Chain...
 *
 * <p>Unless explicitly documented as lazy or an O(1) operation, all {@link List} methods will
 * materialize the elements eagerly if not already.
 *
 * <p>Null elements are not allowed.
 *
 * <p>While bearing a bit of similarity, this class isn't a <a
 * href="https://en.wikipedia.org/wiki/Persistent_data_structure">persistent data structure</a>.
 * Besides the O(1) concatenation, it's a traditional immutable {@link java.util.List} supporting
 * no other functional updates. Concatenation is O(1) as opposed to O(logn) in persistent lists;
 * and random access is also O(1) (after one-time lazy materialization).
 *
 * @since 8.1
 */
public final class Chain<T> extends AbstractList<T> {
  private final T head;
  private final Tree<T> tail;
  private final int size;
  private List<T> materialized;

  private Chain(T head, Tree<T> tail, int size) {
    this.head = requireNonNull(head);
    this.tail = tail;
    this.size = size;
  }

  /** Returns a Chain with a single element. */
  public static <T> Chain<T> of(T element) {
    return new Chain<>(element, null, 1);
  }

  /** Returns a Chain with {@code first} followed by {@code remaining}. */
  @SafeVarargs // remaining are copied into immutable object
  public static <T> Chain<T> of(T first, T... remaining) {
    Tree<T> tail = null;
    for (int i = remaining.length - 1; i >= 0; i--) {
      tail = new Tree<>(null, remaining[i], tail);
    }
    return new Chain<>(first, tail, 1 + remaining.length);
  }

  /**
   * Returns a new Chain concatenating elements from the {@code left} Chain and the
   * {@code right} Chain, in <em>O(1)</em> time.
   *
   * <p>Encounter order of elements is preserved. That is, {@code concat([1, 2], [3, 4])}
   * returns {@code [1, 2, 3, 4]}.
   */
  public static <T> Chain<T> concat(Chain<? extends T> left, Chain<? extends T> right) {
    return new Chain<>(
        left.head, new Tree<>(left.tail, right.head, right.tail), left.size + right.size);
  }

  /**
   * Returns a new Chain concatenating elements from {@code this} Chain followed by {@code
   * lastElement}, in <em>O(1)</em> time.
   */
  public Chain<T> concat(T lastElement) {
    return concat(this, of(lastElement));
  }

  /** Returns the size of the chain. O(1) operation. */
  @Override public int size() {
    return size;
  }

  /** Always false. O(1) operation. */
  @Override public boolean isEmpty() {
    return false;
  }

  /**
   * Returns a <em>lazy</em> stream of the elements in this list.
   * The returned stream is lazy in that concatenated chains aren't consumed until the stream
   * reaches their elements.
   */
  @Override public Stream<T> stream() {
    List<T> elements = materialized;
    if (elements != null) {
      return elements.stream();
    }
    return tail == null
        ? Stream.of(head)
        : Stream.concat(Stream.of(head), tail.stream());
  }

  /** Returns the first element. Will override the SequencedCollection method. Takes O(1) time. */
  // @Override
  public T getFirst() {
    return head;
  }

  /** {@inheritDoc} */
  @Override public T get(int i) {
    return elements().get(i);
  }

  /** {@inheritDoc} */
  @Override public Iterator<T> iterator() {
    return elements().iterator();
  }

  /** {@inheritDoc} */
  @Override public Spliterator<T> spliterator() {
    return elements().spliterator();
  }

  /** {@inheritDoc} */
  @Override public ListIterator<T> listIterator() {
    return elements().listIterator();
  }

  /** {@inheritDoc} */
  @Override public ListIterator<T> listIterator(int index) {
    return elements().listIterator(index);
  }

  /** {@inheritDoc} */
  @Override public List<T> subList(int fromIndex, int toIndex) {
    return elements().subList(fromIndex, toIndex);
  }

  // Visible for idempotence test
  List<T> elements() {
    List<T> elements = materialized;
    if (elements == null) {
      materialized = elements = stream().collect(toImmutableList());
    }
    return elements;
  }

  private static final class Tree<T> {
    private final T value;
    private final Tree<? extends T> before;
    private final Tree<? extends T> after;

    Tree(Tree<? extends T> before, T value, Tree<? extends T> after) {
      this.value = requireNonNull(value);
      this.before = before;
      this.after = after;
    }

    Stream<T> stream() {
      return Walker.<Tree<? extends T>>inBinaryTree(t -> t.before, t -> t.after)
          .inOrderFrom(this)
          .map(t -> t.value);
    }
  }
}

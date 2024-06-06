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
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.mu.util.graph.Walker;

/**
 * Immutable {@link List} implementation that supports O(1) concatenation.
 *
 * <p>The expected use case is to perform frequent concatenations using the {@code concat()}
 * methods. O(n) materialization cost will be (lazily) paid before the first time accessing the
 * elements either through the {@link List} interface such as {@link List#get}, {@link List#equals},
 * {@link #toString}, or {@link #collect}.
 *
 * <p>On the other hand, it's inefficient to materialize and then concatenate (rinse and repeat).
 *
 * <p>Null elements are not allowed.
 *
 * @since 8.1
 */
public final class Sequence<T> extends AbstractList<T> {
  private final T head;
  private final Tree<T> tail;
  private List<T> lazyElements;

  /** Returns a Sequence with a single element. */
  public static <T> Sequence<T> of(T element) {
    return new Sequence<>(element, null);
  }

  /** Returns a Sequence with {@code first} followed by {@code remaining}. */
  @SafeVarargs // remaining are copied into immutable object
  public static <T> Sequence<T> of(T first, T... remaining) {
    Tree<T> tail = null;
    for (int i = remaining.length - 1; i >= 0; i--) {
      tail = new Tree<>(remaining[i], null, tail);
    }
    return new Sequence<>(first, tail);
  }

  /**
   * Returns a new Sequence concatenating elements from the {@code left} Sequence and the
   * {@code right} Sequence, in <em>O(1)</em> time.
   *
   * <p>Encounter order of elements is preserved. That is, {@code concat([1, 2], [3, 4])}
   * returns {@code [1, 2, 3, 4]}.
   */
  public static <T> Sequence<T> concat(Sequence<? extends T> left, Sequence<? extends T> right) {
    return new Sequence<>(left.head, new Tree<>(right.head, left.tail, right.tail));
  }

  /**
   * Returns a new Sequence concatenating elements from {@code this} Sequence followed by {@code
   * lastElement}, in <em>O(1)</em> time.
   */
  public Sequence<T> concat(T lastElement) {
    return concat(this, of(lastElement));
  }

  /**
   * Convenience method equivalent to {@code stream().collect(collector)}. In addition,
   * elements are materialized so after return this List can be efficiently accessed as
   * a regular immutable List without extra cost.
   */
  public <R> R collect(Collector<T, ?, R> collector) {
    return elements().stream().collect(collector);
  }

  /** Returns the size of the sequence. This is an O(1) operation. */
  @Override public int size() {
    return 1 + sizeOf(tail);
  }

  /**
   * Returns a <em>lazy</em> stream of the elements in this list.
   * The returned stream is lazy in that concatenated sequences aren't consumed until the stream
   * reaches their elements.
   */
  @Override public Stream<T> stream() {
    return tail == null
        ? Stream.of(head)
        : Stream.concat(Stream.of(head), tail.stream());
  }

  @Override public T get(int i) {
    return i == 0 ? head : elements().get(i);
  }

  @Override public ListIterator<T> listIterator() {
    return elements().listIterator();
  }

  @Override public ListIterator<T> listIterator(int index) {
    return elements().listIterator(index);
  }

  @Override public List<T> subList(int fromIndex, int toIndex) {
    return elements().subList(fromIndex, toIndex);
  }

  private Sequence(T head, Tree<T> tail) {
    this.head = requireNonNull(head);
    this.tail = tail;
  }

  // Visible for idempotence test
  List<T> elements() {
    List<T> elements = lazyElements;
    if (elements == null) {
      lazyElements = elements = stream().collect(toImmutableList());
    }
    return elements;
  }

  private static final class Tree<T> {
    private final T value;
    private final Tree<? extends T> before;
    private final Tree<? extends T> after;
    private final int size;

    Tree(T value, Tree<? extends T> before, Tree<? extends T> after) {
      this.value = requireNonNull(value);
      this.before = before;
      this.after = after;
      this.size = 1 + sizeOf(before) + sizeOf(after);
    }

    Stream<? extends T> stream() {
      return Walker.<Tree<? extends T>>inBinaryTree(t -> t.before, t -> t.after)
          .inOrderFrom(this)
          .map(t -> t.value);
    }
  }

  private static int sizeOf(Tree<?> tree) {
    return tree == null ? 0 : tree.size;
  }
}

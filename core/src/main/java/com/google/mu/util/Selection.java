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
package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableSet;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.reducing;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;

/**
 * An immutable selection of choices supporting both {@link #only limited} and {@link #all
 * unlimited} selections.
 *
 * <p>Useful when you need to disambiguate and enforce correct handling of the
 * <b>implicitly selected all</b> concept, in replacement of the common and error prone
 * <em>empty-means-all</em> hack. That is, instead of adding (or forgetting to add) special
 * handling like:
 *
 * <pre>{@code
 *   Set<Sting> choices = getChoices();
 *   if (choices.isEmpty() || choices.contains("foo")) {
 *     // foo is selected.
 *   }
 * }</pre>
 *
 * Use {@code Selection} so you code is intuitive and hard to get wrong:
 *
 * <pre>{@code
 *   Selection<Sting> choices = getChoices();
 *   if (choices.has("foo")) {
 *     // foo is selected.
 *   }
 * }</pre>
 *
 * <p>While an unlimited selection is conceptually close to a trivially-true predicate, the {@code
 * Selection} type provides access to the explicitly selected choices via the {@link #limited}
 * method. It also implements {@link Object#equals}, {@link Object#hashCode} and {@link
 * Object#toString} sensibly.
 *
 * <p>Nulls are prohibited throughout this class.
 *
 * @since 4.1
 */
public interface Selection<T> {
  /** Returns an unlimited selection of all (unspecified) choices. */
  @SuppressWarnings("unchecked")
  static <T> Selection<T> all() {
    return (Selection<T>) Selections.ALL;
  }

  /** Returns an empty selection. */
  @SuppressWarnings("unchecked")
  static <T> Selection<T> none() {
    return (Selection<T>) Selections.NONE;
  }

  /** Returns a selection of {@code choices}. Null is not allowed. */
  @SafeVarargs
  static <T> Selection<T> only(T... choices) {
    return Arrays.stream(choices).collect(toSelection());
  }

  /** Returns a collector that collects input elements into a limited selection. */
  static <T> Collector<T, ?, Selection<T>> toSelection() {
    return collectingAndThen(toImmutableSet(), Selections.Limited::new);
  }

  /** Returns a collector that intersects the input selections. */
  static <T> Collector<Selection<T>, ?, Selection<T>> toIntersection() {
    return reducing(all(), Selection::intersect);
  }

  /** Returns a collector that unions the input selections. */
  static <T> Collector<Selection<T>, ?, Selection<T>> toUnion() {
    return reducing(none(), Selection::union);
  }

  /**
   * Returns true if {@code candidate} is in this selection.
   *
   * @throws NullPointerException if {@code candidate} is null
   */
  boolean has(T candidate);

  /**
   * Returns true if this is a {@link #only limited} selection with zero elements included. For
   * example: {@code Selection.none()}.
   */
  boolean isEmpty();

  /**
   * Returns the limited choices if this selection is a {@link #only limited} instance; {@code
   * Optional.empty()} for {@link #all unlimited} instances.
   *
   * <p>Note that {@code limited()} returning {@code Optional.empty()} vs. returning
   * {@code Optional.of(emptySet())} have completely different semantics: the former means the
   * selection is <em>unlimited</em> (or, has no limit); while the latter indicates that the
   * selection has zero choices, i.e., {@link #none}.
   *
   * <p>The caller can use this method to optionally handle explicit choices, for example:
   *
   * <pre>{@code
   *   Selection<Region> requestedRegions = ...;
   *   requestedRegions.limited().ifPesent(this::checkValidRegions);
   * }</pre>
   */
  Optional<Set<T>> limited();

  /** Returns an intersection of this selection and {@code that}. */
  Selection<T> intersect(Selection<T> that);

  /** Returns an intersection of this selection and the elements from {@code set}. */
  Selection<T> intersect(Set<? extends T> set);

  /** Returns an union of this selection and {@code that}. */
  Selection<T> union(Selection<T> that);

  /** Returns a union of this selection and the elements from {@code set}. */
  Selection<T> union(Set<? extends T> set);

  /**
   * Returns true if {@code obj} is an equivalent {@code Selection} instance.
   * Specifically:
   * <ul>
   * <li>{@link #all unlimited} is always equal to itself, while never equal to any
   *     {@link #only limited} selections (even if the limited selection includes all known
   *     choices).
   * <li>Two limited selections are equal if they represent equal set of explicit choices.
   *     The set order doesn't matter, that is, {@code [a, b]} is considered to be equal to
   *     {@code [b, a]}.
   * </ul>
   */
  @Override boolean equals(Object obj);

  @Override int hashCode();
}
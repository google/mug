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

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Internal implementations of {@link Selection}. */
enum Selections implements Selection<Object> {
  ALL {
    @Override
    public boolean has(Object candidate) {
      return true;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public Optional<Set<Object>> limited() {
      return Optional.empty();
    }

    @Override
    public Selection<Object> intersect(Selection<Object> that) {
      return requireNonNull(that);
    }

    @Override
    public Selection<Object> intersect(Set<?> set) {
      return set.stream().collect(Selection.toSelection());
    }

    @Override
    public Selection<Object> union(Selection<Object> that) {
      requireNonNull(that);
      return this;
    }

    @Override
    public Selection<Object> union(Set<?> set) {
      requireNonNull(set);
      return this;
    }
  },
  NONE {
    @Override
    public boolean has(Object candidate) {
      return false;
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public Optional<Set<Object>> limited() {
      return Optional.of(emptySet());
    }

    @Override
    public Selection<Object> intersect(Selection<Object> that) {
      requireNonNull(that);
      return this;
    }

    @Override
    public Selection<Object> intersect(Set<?> set) {
      requireNonNull(set);
      return this;
    }

    @Override
    public Selection<Object> union(Selection<Object> that) {
      return requireNonNull(that);
    }

    @Override
    public Selection<Object> union(Set<?> set) {
      return set.stream().collect(Selection.toSelection());
    }

    @Override
    public String toString() {
      return "[]";
    }
  },
  ;

  static <T> Selection<T> explicit(Set<T> set) {
    return set.isEmpty() ? Selection.none() : new Limited<T>(set);
  }

  private static final class Limited<T> implements Selection<T> {
    private final Set<T> choices;

    Limited(Set<T> choices) {
      this.choices = choices;
    }

    @Override
    public boolean has(T candidate) {
      return choices.contains(candidate);
    }

    @Override
    public boolean isEmpty() {
      return choices.isEmpty();
    }

    @Override
    public Optional<Set<T>> limited() {
      return Optional.of(choices);
    }

    @Override
    public Selection<T> intersect(Selection<T> that) {
      return that.limited().map(this::intersect).orElse(this);
    }

    @Override
    public Selection<T> intersect(Set<? extends T> set) {
      return explicit(setIntersection(choices, set));
    }

    @Override
    public Selection<T> union(Selection<T> that) {
      return that.limited().map(this::union).orElse(that);
    }

    @Override
    public Selection<T> union(Set<? extends T> set) {
      return explicit(setUnion(choices, set));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Limited) {
        Limited<?> that = (Limited<?>) obj;
        return choices.equals(that.choices);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return choices.hashCode();
    }

    @Override
    public String toString() {
      return choices.toString();
    }
  }

  private static <T> Set<T> setIntersection(Set<? extends T> a, Set<? extends T> b) {
    requireNonNull(b);
    LinkedHashSet<T> intersection = new LinkedHashSet<>();
    for (T v : a) {
      if (b.contains(v)) intersection.add(v);
    }
    return unmodifiableSet(intersection);
  }

  private static <T> Set<T> setUnion(Set<? extends T> a, Set<? extends T> b) {
    LinkedHashSet<T> union = new LinkedHashSet<>(a);
    union.addAll(b);
    return unmodifiableSet(union);
  }
}
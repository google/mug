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

import static com.google.mu.collect.InternalUtils.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;

import com.google.mu.function.CheckedFunction;
import com.google.mu.util.Substring;

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
 *   Set<String> choices = getChoices();
 *   if (choices.isEmpty() || choices.contains("foo")) {
 *     // foo is selected.
 *   }
 * }</pre>
 *
 * Use {@code Selection} so your code is intuitive and hard to get wrong:
 *
 * <pre>{@code
 *   Selection<String> choices = getChoices();
 *   if (choices.has("foo")) {
 *     // foo is selected.
 *   }
 * }</pre>
 *
 * <p>To gradually migrate from legacy code where empty sets need to be special handled all over,
 * use {@link #nonEmptyOrAll nonEmptyOrAll(set)} to convert to a {@code Selection} object:
 *
 * <pre>{@code
 *   public class FoodDeliveryService {
 *     private final Selection<Driver> eligibleDrivers;
 *
 *     // Too much code churn to change this public constructor signature.
 *     public FoodDeliveryService(Set<Driver> eligibleDrivers) {
 *       // But we can migrate internal implementation to using Selection:
 *       this.eligibleDrivers = Selection.nonEmptyOrAll(eligibleDrivers);
 *     }
 *
 *     ...
 *   }
 * }</pre>
 *
 * <p>While an unlimited selection is conceptually close to a trivially-true predicate, the {@code
 * Selection} type provides access to the explicitly selected choices via the {@link #limited}
 * method. It also implements {@link #equals}, {@link #hashCode} and {@link #toString} sensibly.
 *
 * <p>Nulls are prohibited throughout this class.
 *
 * @since 8.0
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

  /**
   * Converts to {@code Selection} from legacy code where an empty collection means <b>all</b>.
   * After converted to {@code Selection}, user code no longer need special handling of the
   * <em>empty</em> case.
   *
   * <p>This method is mainly for migrating legacy code. If you have a set where empty just means
   * "none" with no special handling needed, you should use {@link #toSelection
   * set.stream().collect(toSelection())} instead.
   */
  static <T> Selection<T> nonEmptyOrAll(Collection<? extends T> choices) {
    return choices.isEmpty() ? all() : choices.stream().collect(toSelection());
  }

  /** Returns a collector that collects input elements into a limited selection. */
  static <T> Collector<T, ?, Selection<T>> toSelection() {
    return collectingAndThen(toImmutableSet(), Selections::explicit);
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
   * Returns the default parser, using {@code ','} as delimiter of elements.
   *
   * @since 4.7
   */
  static Parser parser() {
    return delimitedBy(',');
  }

  /**
   * Returns a parser for {@code Selection}, using the {@code delimiter} character to delimit
   * explicit selection elements.
   *
   * <p>Because {@code '*'} is used as special indicator of {@link #all}, it can't be used as
   * the delimiter.
   *
   * @since 4.7
   */
  static Parser delimitedBy(char delimiter) {
    return delimitedBy(Substring.first(delimiter));
  }

  /**
   * Returns a parser for {@code Selection}, using the {@code delimiter} patter to delimit explicit
   * selection elements.
   *
   * <p>Because {@code '*'} is used as special indicator of {@link #all}, it can't be used as
   * the delimiter.
   *
   * @since 4.7
   */
  static Parser delimitedBy(Substring.Pattern delimiter) {
    return new Parser(delimiter);
  }

  /**
   * Parser for {@link Selection}.
   *
   * @since 4.7
   */
  static final class Parser {
    private final Substring.Pattern delimiter;

    Parser(Substring.Pattern delimiter) {
      if (delimiter.in("*").isPresent()) {
        throw new IllegalArgumentException("Cannot use '*' as delimiter in a Selection.");
      }
      this.delimiter = delimiter;
    }

    /**
     * Parses {@code string} into a {@link Selection} by treating the single
     * {@code '*'} character as {@link Selection#all()}, and delegating to
     * {@code elementParser} to parse each explicit selection element.
     *
     * <p>Leading and trailing whitespaces are trimmed. Empty and duplicate elements are ignored.
     */
    public <T, E extends Throwable> Selection<T> parse(
        String string,
        CheckedFunction<String, ? extends T, E> elementParser) throws E {
      string = string.trim();
      requireNonNull(elementParser);
      if (string.equals("*")) {
        return all();
      }
      Set<String> parts = delimiter.repeatedly().splitThenTrim(string)
          .filter(m -> m.length() > 0)
          .map(Substring.Match::toString)
          .collect(toCollection(LinkedHashSet::new));
      List<T> elements = new ArrayList<>(parts.size());
      for (String part : parts) {
        elements.add(elementParser.apply(part));
      }
      return elements.stream().collect(toSelection());
    }

    /**
     * Parses {@code string} into a {@link Selection} of strings by treating the single
     * {@code '*'} character as {@link Selection#all()}.
     *
     * <p>Leading and trailing whitespaces are trimmed. Empty and duplicate elements are ignored.
     */
    public Selection<String> parse(String string) {
      return parse(string, s -> s);
    }
  }

  /**
   * Returns true if {@code candidate} is in this selection.
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

  /**
   * Returns {@code "ALL"} if {@link #all unlimited}, or else returns the string representation of
   * the set of the explicit choices. That is, {@code only("dog", "cat").toString()} will return
   * {@code "[dog, cat]"}.
   */
  @Override String toString();
}

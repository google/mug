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

import static com.google.mu.util.stream.BiStream.kv;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * {@code BiCollection} to {@link BiStream} is like {@code Iterable} to {@code Iterator}:
 * a re-streamable collection of pairs. Suitable when the pairs aren't logically a {@code Map}
 * or {@code Multimap}.
 *
 * @since 1.4
 * @deprecated too niche
 */
@Deprecated
public final class BiCollection<L, R> {
  private static final BiCollection<?, ?> EMPTY = new BiCollection<>(Collections.emptyList());

  private final Collection<? extends Map.Entry<L, R>> entries;

  private BiCollection(Collection<? extends Entry<L, R>> underlying) {
    this.entries = requireNonNull(underlying);
  }

  /** Returns an empty {@code BiCollection}. */
  @SuppressWarnings("unchecked")
  public static <L, R> BiCollection<L, R> of() {
    return (BiCollection<L, R>) EMPTY;
  }

  /** Returns a {@code BiCollection} for {@code left} and {@code right}. */
  public static <L, R> BiCollection<L, R> of(L left, R right) {
    return new BiCollection<>(asList(kv(left, right)));
  }

  /** Returns a {@code BiCollection} for two pairs. */
  public static <L, R> BiCollection<L, R> of(L left1, R right1, L left2, R right2) {
    return new BiCollection<>(asList(kv(left1, right1), kv(left2, right2)));
  }

  /** Returns a {@code BiCollection} for three pairs. */
  public static <L, R> BiCollection<L, R> of(L left1, R right1, L left2, R right2, L left3, R right3) {
    return new BiCollection<>(asList(kv(left1, right1), kv(left2, right2), kv(left3, right3)));
  }

  /** Returns a {@code BiCollection} for four pairs. */
  public static <L, R> BiCollection<L, R> of(
      L left1, R right1, L left2, R right2, L left3, R right3, L left4, R right4) {
    return new BiCollection<>(
        asList(kv(left1, right1), kv(left2, right2), kv(left3, right3), kv(left4, right4)));
  }

  /** Returns a {@code BiCollection} for five pairs. */
  public static <L, R> BiCollection<L, R> of(
      L left1, R right1, L left2, R right2, L left3, R right3, L left4, R right4, L left5, R right5) {
    return new BiCollection<>(asList(
        kv(left1, right1), kv(left2, right2), kv(left3, right3), kv(left4, right4), kv(left5, right5)));
  }

  /**
   * Returns a {@code Collector} that extracts the pairs from the input stream,
   * and then collects them into a {@code BiCollection}.
   *
   * @param leftFunction extracts the first element of each pair
   * @param rightFunction extracts the second element of each pair
   */
  public static <T, L, R> Collector<T, ?, BiCollection<L, R>> toBiCollection(
      Function<? super T, ? extends L> leftFunction,
      Function<? super T, ? extends R> rightFunction) {
    requireNonNull(leftFunction);
    requireNonNull(rightFunction);
    Function<T, Map.Entry<L, R>> toEntry = x -> kv(leftFunction.apply(x), rightFunction.apply(x));
    Collector<T, ?, ? extends Collection<Map.Entry<L, R>>> entryCollector =
        Collectors.mapping(toEntry, Collectors.toList());
    return Collectors.collectingAndThen(entryCollector, BiCollection::new);
  }

  /** Returns the size of the collection. */
  public int size() {
    return entries.size();
  }

  /** Streams over this {@code BiCollection}. */
  public BiStream<L, R> stream() {
    return BiStream.fromEntries(entries.stream());
  }

  /** @since 1.5 */
  @Override public int hashCode() {
    return entries.hashCode();
  }

  /** @since 1.5 */
  @Override public boolean equals(Object obj) {
    if (obj instanceof BiCollection<?, ?>) {
      BiCollection<?, ?> that = (BiCollection<?, ?>) obj;
      return entries.equals(that.entries);
    }
    return false;
  }

  /** @since 1.5 */
  @Override public String toString() {
    return entries.toString();
  }
}

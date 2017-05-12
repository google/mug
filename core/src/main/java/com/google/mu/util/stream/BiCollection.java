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
 * A re-streamable collection of pairs. Suitable when the pairs aren't logically a {@code Map}
 * or {@code Multimap}.
 *
 * @since 1.3
 */
public final class BiCollection<L, R> {

  private final Collection<? extends Map.Entry<? extends L, ? extends R>> entries;

  private BiCollection(Collection<? extends Entry<? extends L, ? extends R>> underlying) {
    this.entries = requireNonNull(underlying);
  }

  /** Returns an empty {@code BiCollection}. */
  public static <L, R> BiCollection<L, R> of() {
    return from(Collections.emptyList());
  }

  /** Returns a {@code BiCollection} for {@code left} and {@code right}. */
  public static <L, R> BiCollection<L, R> of(L left, R right) {
    return from(asList(kv(left, right)));
  }

  /** Returns a {@code BiCollection} for two pairs. */
  public static <L, R> BiCollection<L, R> of(L left1, R right1, L left2, R right2) {
    return from(asList(kv(left1, right1), kv(left2, right2)));
  }

  /** Returns a {@code BiCollection} for three pairs. */
  public static <L, R> BiCollection<L, R> of(L left1, R right1, L left2, R right2, L left3, R right3) {
    return from(asList(kv(left1, right1), kv(left2, right2), kv(left3, right3)));
  }

  /** Returns a {@code BiCollection} for four pairs. */
  public static <L, R> BiCollection<L, R> of(
      L left1, R right1, L left2, R right2, L left3, R right3, L left4, R right4) {
    return from(asList(kv(left1, right1), kv(left2, right2), kv(left3, right3), kv(left4, right4)));
  }

  /** Returns a {@code BiCollection} for five pairs. */
  public static <L, R> BiCollection<L, R> of(
      L left1, R right1, L left2, R right2, L left3, R right3, L left4, R right4, L left5, R right5) {
    return from(asList(
        kv(left1, right1), kv(left2, right2), kv(left3, right3), kv(left4, right4), kv(left5, right5)));
  }

  /** Wraps {@code entries} in a {@code BiCollection}. */
  public static <L, R> BiCollection<L, R> from(
      Collection<? extends Map.Entry<? extends L, ? extends R>> entries) {
    return new BiCollection<>(entries);
  }

  /**
   * Wraps entries in {@code map} as a {@code BiCollection}. Note that the returned
   * {@code BiCollection} is a view of the input map entries.
   */
  public static <L, R> BiCollection<L, R> from(Map<? extends L, ? extends R> map) {
    return from(map.entrySet());
  }

  /**
   * Returns a {@code Collector} that extracts the pairs from the input stream,
   * and then collects them into a {@code BiCollection}.
   *
   * @param leftFunction function to extract the first element of each pair
   * @param rightFunction function to extract the second element of each pair
   * @param collectorStrategy determines the kind of collection to use. For example:
   *        {@code Collectors::toList} or {@code ImmutableList::toImmutableList}.
   */
  public static <T, L, R> Collector<T, ?, BiCollection<L, R>> toBiCollection(
      Function<? super T, ? extends L> leftFunction,
      Function<? super T, ? extends R> rightFunction,
      CollectorStrategy collectorStrategy) {
    requireNonNull(leftFunction);
    requireNonNull(rightFunction);
    Function<T, Map.Entry<L, R>> toEntry = x -> kv(leftFunction.apply(x), rightFunction.apply(x));
    Collector<T, ?, ? extends Collection<? extends Map.Entry<? extends L, ? extends R>>> entryCollector =
        Collectors.mapping(toEntry, collectorStrategy.collector());
    return Collectors.collectingAndThen(entryCollector, BiCollection::from);
  }

  /**
   * Returns a {@code Collector} that extracts the pairs from the input stream,
   * and then collects them into a {@code BiCollection}.
   *
   * @param leftFunction function to extract the first element of each pair
   * @param rightFunction function to extract the second element of each pair
   */
  public static <T, L, R> Collector<T, ?, BiCollection<L, R>> toBiCollection(
      Function<? super T, ? extends L> leftFunction,
      Function<? super T, ? extends R> rightFunction) {
    return toBiCollection(leftFunction, rightFunction, Collectors::toList);
  }

  /** Returns the size of the collection. */
  public int size() {
    return entries.size();
  }

  /** Streams over this {@code BiCollection}. */
  public BiStream<L, R> stream() {
    return new BiStream<>(entries.stream());
  }
}

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

import static com.google.mu.util.stream.BiStream.biStream;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Some common {@link BiStream.Operation]s. */
public final class BiStreamOperations {
  /**
   * Returns a lazy {@code BiStream} of the consecutive groups of pairs from this stream.
   * Consecutive pairs that map to the same key according to {@code classifier} are grouped together
   * using {@code groupCollector}.
   *
   * <p>For example to lazily summarize a large, pre-sorted stock price data stream per day:
   *
   * <pre>{@code
   * biStream(stockPriceData)
   *     .then(groupConsecutiveBy(PriceDatum::day, summarizingDouble(PriceDatum::price)))
   *     ,toMap();
   * }</pre>
   *
   * <p>Unlike JDK {@link Collectors#groupingBy groupingBy()} collectors, the returned BiStream
   * consumes the input elements lazily and only requires {@code O(groupCollector)} space for the
   * current consecutive elements group. For instance the {@code groupConsecutiveByKey(counting())}
   * stream takes O(1) space. While this makes it more efficient to process large streams, the input
   * data often need to be pre-sorted for the grouping to be useful.
   *
   * <p>To apply grouping beyond consecutive elements, use {@link BiCollectors#groupingBy(Function,
   * Collector) collect(BiCollectors.groupingBy(classifier, groupCollector))} instead.
   *
   * <p>Consecutive null keys are grouped together.
   *
   * @param classifier The function to determine the group key. Because it's guaranteed to be
   *     invoked once and only once per entry, and that the returned BiStream is sequential and
   *     respects encounter order, this function is allowed to have side effects.
   * @since 5.4
   */
  public static <K, V, G, A, R> BiStream.Operation<K, V, BiStream<G, R>> groupConsecutiveBy(
      Function<? super K, ? extends G> classifier, Collector<? super V, A, R> groupCollector) {
    requireNonNull(classifier);
    BiConsumer<A, ? super V> accumulator = groupCollector.accumulator();
    return stream -> stream.groupConsecutiveBy(
        (k, v) -> classifier.apply(k),
        groupCollector.supplier(),
        (a, k, v) -> accumulator.accept(a, v),
        groupCollector.finisher());
  }

  /**
   * Returns a lazy {@code BiStream} of the consecutive groups of pairs from this stream.
   * Consecutive pairs that map to the same key according to {@code classifier} are reduced using
   * the {@code groupReducer} function.
   *
   * <p>For example to lazily find the daily opening stock price from a large, pre-sorted stock data
   * stream:
   *
   * <pre>{@code
   * biStream(stockPriceDataSortedByTime)
   *     .then(groupConsecutiveBy(PriceDatum::day, (a, b) -> a))
   *     ,toMap();
   * }</pre>
   *
   * <p>Unlike JDK {@link Collectors#groupingBy groupingBy()} collectors, the returned BiStream
   * consumes the input elements lazily and only requires {@code O(1)} space. While this makes it
   * more efficient to process large streams, the input data often need to be pre-sorted for the
   * grouping to be useful.
   *
   * <p>To apply grouping beyond consecutive elements, use {@link BiCollectors#groupingBy(Function,
   * BinaryOperator) collect(BiCollectors.groupingBy(classifier, groupReducer))} instead.
   *
   * <p>Consecutive null keys are grouped together.
   *
   * @param classifier The function to determine the group key. Because it's guaranteed to be
   *     invoked once and only once per entry, and that the returned BiStream is sequential and
   *     respects encounter order, this function is allowed to have side effects.
   * @since 5.4
   */
  public static <K, V, G> BiStream.Operation<K, V, BiStream<G, V>> groupConsecutiveBy(
      Function<? super K, ? extends G> classifier, BinaryOperator<V> groupReducer) {
    return groupConsecutiveBy(classifier, BiStream.reducingGroupMembers(groupReducer));
  }

  /**
   * Returns a lazy {@code BiStream} of the consecutive groups of pairs from this stream.
   * Consecutive pairs that map to the same key according to {@code classifier} are grouped into a
   * mutable container, which is created by the {@code newGroup} function and populated with the
   * {@code accumulator} function.
   *
   * <p>For example to lazily summarize a large, pre-sorted stock price data stream per day:
   *
   * <pre>{@code
   * ImmutableMap<Date, DailyReport> dailyReports =
   *     biStream(stockPriceData)
   *         .then(groupConsecutiveBy(PriceDatum::day, DailyReport::new, DailyReport::addPrice))
   *         ,toMap();
   * }</pre>
   *
   * <p>Consecutive null keys are grouped together.
   *
   * @param classifier The function to determine the group key. Because it's guaranteed to be
   *     invoked once and only once per entry, and that the returned BiStream is sequential and
   *     respects encounter order, this function is allowed to have side effects.
   * @param newGroup the factory function to create a new collection for each group
   * @param groupAccumulator the function to accumulate group members
   * @since 5.4
   */
  public static <K, V, G, A> BiStream.Operation<K, V, BiStream<G, A>> groupConsecutiveBy(
      Function<? super K, ? extends G> classifier,
      Supplier<? extends A> newGroup,
      BiConsumer<A, ? super V> groupAccumulator) {
    requireNonNull(classifier);
    requireNonNull(newGroup);
    requireNonNull(groupAccumulator);
    return stream -> stream.groupConsecutiveBy(
        (k, v) -> classifier.apply(k),
        newGroup,
        (container, k, v) -> groupAccumulator.accept(container, v),
        identity());
  }

  /**
   * Returns a lazy {@code BiStream} of the consecutive groups of pairs from this stream.
   * Consecutive pairs that map to the same key according to {@code classifier} are grouped together
   * using {@code groupCollector}.
   *
   * <p>Unlike JDK {@link Collectors#groupingBy groupingBy()} collectors, the returned BiStream
   * consumes the input elements lazily and only requires {@code O(groupCollector)} space for the
   * current consecutive elements group. While this makes it more efficient to process large
   * streams, the input data often need to be pre-sorted for the grouping to be useful.
   *
   * <p>To apply grouping beyond consecutive elements, use {@link
   * BiCollectors#groupingBy(BiFunction, BiCollector) collect(BiCollectors.groupingBy(classifier,
   * groupCollector))} instead.
   *
   * <p>Null elements are allowed as long as the {@code keyFunction} function and {@code
   * groupCollector} allow nulls.
   *
   * @param classifier The function to determine the group key. Because it's guaranteed to be
   *     invoked once and only once per entry, and that the returned BiStream is sequential and
   *     respects encounter order, this function is allowed to have side effects.
   * @since 5.4
   */
  public static <K, V, G, R> BiStream.Operation<K, V, BiStream<G, R>> groupConsecutiveBy(
      BiFunction<? super K, ? super V, ? extends G> classifier,
      BiCollector<? super K, ? super V, R> groupCollector) {
    requireNonNull(classifier);
    requireNonNull(groupCollector);
    return stream -> biStream(stream.mapToEntry())
        .then(groupConsecutiveBy(
            e -> classifier.apply(e.getKey(), e.getValue()),
            groupCollector.splitting(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private BiStreamOperations() {}
}

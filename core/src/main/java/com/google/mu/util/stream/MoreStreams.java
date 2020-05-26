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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.mu.function.CheckedConsumer;

/**
 * Static utilities pertaining to {@link Stream} in addition to relevant utilities in Jdk and Guava.
 *
 * @since 1.1
 */
public final class MoreStreams {
  /**
   * Returns a Stream produced by iterative application of {@code step} to the initial
   * {@code seed}, producing a Stream consisting of seed, elements of step(seed),
   * elements of step(x) for each x in step(seed), etc.
   *
   * <p>While {@code Stream.generate(supplier)} can be used to generate infinite streams,
   * it's not as easy to generate a <em>finite</em> stream unless the size can be pre-determined.
   * This method can be used to generate finite streams: just return an empty stream when the
   * {@code step} determines that there's no more elements to be generated.
   *
   * <p>At every step, 0, 1 or more elements can be generated into the resulting stream.
   * As discussed above, returning an empty stream leads to eventual termination of the stream;
   * returning 1-element stream is equivalent to {@code Stream.generate(supplier)};
   * while returning more than one elements allows a single element to fan out to multiple
   * elements.
   *
   * @since 1.9
   */
  public static <T> Stream<T> generate(
      T seed, Function<? super T, ? extends Stream<? extends T>> step) {
    // flatMap() here won't honor short-circuiting such as limit(), because it internally
    // uses forEach() on the passed-in stream. See https://bugs.openjdk.java.net/browse/JDK-8075939
    return Stream.concat(Stream.of(seed), flatten(step.apply(seed).map(n -> generate(n, step))));
  }

  /**
   * Flattens {@code streamOfStream} and returns an unordered sequential stream of the nested
   * elements.
   *
   * <p>Logically, {@code stream.flatMap(fanOut)} is equivalent to
   * {@code MoreStreams.flatten(stream.map(fanOut))}.
   * Due to this <a href="https://bugs.openjdk.java.net/browse/JDK-8075939">JDK bug</a>,
   * {@code flatMap()} uses {@code forEach()} internally and doesn't support short-circuiting for
   * the passed-in stream. {@code flatten()} supports short-circuiting and can be used to
   * flatten infinite streams.
   *
   * @since 1.9
   */
  public static <T> Stream<T> flatten(Stream<? extends Stream<? extends T>> streamOfStream) {
    return mapBySpliterator(streamOfStream.sequential(), 0, FlattenedSpliterator<T>::new);
  }

  /**
   * Iterates through {@code stream} <em>only once</em>. It's strongly recommended
   * to avoid assigning the return value to a variable or passing it to any other method because
   * the returned {@code Iterable}'s {@link Iterable#iterator iterator()} method can only be called
   * once. Instead, always use it together with a for-each loop, as in:
   *
   * <pre>{@code
   *   for (Foo foo : iterateOnce(stream)) {
   *     ...
   *     if (...) continue;
   *     if (...) break;
   *     ...
   *   }
   * }</pre>
   *
   * The above is equivalent to manually doing:
   *
   * <pre>{@code
   *   Iterable<Foo> foos = stream::iterator;
   *   for (Foo foo : foos) {
   *     ...
   *   }
   * }</pre>
   * except using this API eliminates the need for a named variable that escapes the scope of the
   * for-each loop. And code is more readable too.
   *
   * <p>Note that {@link #iterateThrough iterateThrough()} should be preferred whenever possible
   * due to the caveats mentioned above. This method is still useful when the loop body needs to
   * use control flows such as {@code break} or {@code return}.
   */
  public static <T> Iterable<T> iterateOnce(Stream<T> stream) {
    return stream::iterator;
  }

  /**
   * Iterates through {@code stream} sequentially and passes each element to {@code consumer}
   * with exceptions propagated. For example: 
   *
   * <pre>{@code
   *   void writeAll(Stream<?> stream, ObjectOutput out) throws IOException {
   *     iterateThrough(stream, out::writeObject);
   *   }
   * }</pre>
   */
  public static <T, E extends Throwable> void iterateThrough(
      Stream<? extends T> stream, CheckedConsumer<? super T, E> consumer) throws E {
    requireNonNull(consumer);
    for (T element : iterateOnce(stream)) {
      consumer.accept(element);
    }
  }

  /**
   * Dices {@code stream} into smaller chunks each with up to {@code maxSize} elements.
   *
   * <p>For a sequential stream, the first N-1 chunk's will contain exactly {@code maxSize}
   * elements and the last chunk may contain less (but never 0).
   * However for parallel streams, it's possible that the stream is split in roughly equal-sized
   * sub streams before being diced into smaller chunks, which then will result in more than one
   * chunks with less than {@code maxSize} elements.
   *
   * <p>This is an <a href="https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html#StreamOps">
   * intermediary operation</a>.
   *
   * @param stream the source stream to be diced
   * @param maxSize the maximum size for each chunk
   * @return Stream of diced chunks each being a list of size up to {@code maxSize}
   * @throws IllegalStateException if {@code maxSize <= 0}
   */
  public static <T> Stream<List<T>> dice(Stream<? extends T> stream, int maxSize) {
    requireNonNull(stream);
    if (maxSize <= 0) throw new IllegalArgumentException();
    return mapBySpliterator(stream, Spliterator.NONNULL, it -> dice(it, maxSize));
  }

  /**
   * Dices {@code spliterator} into smaller chunks each with up to {@code maxSize} elements.
   *
   * @param spliterator the source spliterator to be diced
   * @param maxSize the maximum size for each chunk
   * @return Spliterator of diced chunks each being a list of size up to {@code maxSize}
   * @throws IllegalStateException if {@code maxSize <= 0}
   */
  public static <T> Spliterator<List<T>> dice(Spliterator<? extends T> spliterator, int maxSize) {
    requireNonNull(spliterator);
    if (maxSize <= 0) throw new IllegalArgumentException();
    return new DicedSpliterator<T>(spliterator, maxSize);
  }

  /**
   * Returns a collector that collects {@link Map} entries into a combined map. Duplicate keys cause {@link
   * IllegalStateException}. For example: 
   *
   * <pre>{@code
   *   Map<FacultyId, Account> allFaculties = departments.stream()
   *       .map(Department::getFacultyMap)
   *       .collect(uniqueKeys());
   * }</pre>
   *
   * <p>Use {@link BiStream#groupingValuesFrom} if there are duplicate keys.
   *
   * @since 1.13
   */
  public static <K, V> Collector<Map<K, V>, ?, Map<K, V>> uniqueKeys() {
    return Collectors.collectingAndThen(
        BiStream.groupingValuesFrom(Map::entrySet,  (a, b) -> {
          throw new IllegalStateException("Duplicate keys not allowed: " + a);
        }),
        BiStream::toMap);
  }

  /**
   * Returns an infinite {@link IntStream} starting from {@code 0}.
   *
   * @deprecated Use {@link #indexesFrom}.
   */
  @Deprecated
  public static IntStream index() {
    return IntStream.iterate(0, i -> i + 1);
  }

  /**
   * Returns an infinite {@link Stream} starting from {@code firstIndex}.
   * Can be used together with {@link BiStream#zip} to iterate over a stream with index.
   * For example: {@code zip(indexesFrom(0), values)}.
   *
   * <p>To get a finite stream, use {@code indexesFrom(...).limit(size)}.
   *
   * <p>Note that while {@code indexesFrom(0)} will eventually incur boxing cost for every integer,
   * the JVM typically pre-caches small {@code Integer} instances (by default up to 127).
   *
   * @since 3.7
   */
  public static Stream<Integer> indexesFrom(int firstIndex) {
    return IntStream.iterate(firstIndex, i -> i + 1).boxed();
  }

  /**
   * Returns a lazy stream of elements from {@code queue} in first-in-first-out order.
   * Each stream element upon consumed is immediately {@link Queue#remove removed} from the queue.
   * The stream terminates as soon as the queue becomes empty.
   *
   * <p>The consumer code of the returned stream may modify the underlying queue through adding or
   * removing elements.
   *
   * <p>Can be used simplify the following common idiom of iterating and consuming queue:
   * <pre>{@code
   *   while (!queue.isEmpty()) {
   *     T element = queue.remove();
   *     ...
   *   }
   * }</pre>
   * to:
   * <pre>{@code
   *   removingFrom(queue).forEachOrdered(...);
   * }</pre>
   *
   * @since 3.8
   */
  public static <T> Stream<T> removingFrom(Queue<T> queue) {
    return streamingUntil(Stream.generate(queue::remove), queue::isEmpty);
  }

  /**
   * Returns a lazy stream of elements from {@code stack} in first-in-last-out order.
   * Each stream element upon consumed is immediately {@link Deque#pop popped} from the stack.
   * The stream terminates as soon as the stack becomes empty.
   *
   * <p>The consumer code of the returned stream may modify the underlying stack through adding or
   * removing elements.
   *
   * <p>Can be used to simplify the following common idiom of iterating and consuming stack:
   * <pre>{@code
   *   while (!stack.isEmpty()) {
   *     T element = stack.pop();
   *     ...
   *   }
   * }</pre>
   * to:
   * <pre>{@code
   *   poppingFrom(stack).forEachOrdered(...);
   * }</pre>
   *
   * @since 3.8
   */
  public static <T> Stream<T> poppingFrom(Deque<T> stack) {
    return streamingUntil(Stream.generate(stack::pop), stack::isEmpty);
  }

  /**
   * Wraps {@code stream} such that it will early-terminate as soon as {@code terminalCondition}
   * evaluates to true. Side-effect is expected between consumption the stream elements and
   * evaluation of {@code terminalCondition}. Particularly, {@code terminalCondition} will
   * be evaluated once and only once immediately before consuming a stream element.
   *
   * @since 3.8
   */
  public static <T> Stream<T> streamingUntil(
      Stream<T> stream, BooleanSupplier terminalCondition) {
    requireNonNull(stream);
    requireNonNull(terminalCondition);
    return StreamSupport.stream(
        () -> Spliterators.spliteratorUnknownSize(
            iteratingUntil(stream.iterator(), terminalCondition), 0),
        0, false);
  }
  
  private static <T> Iterator<T> iteratingUntil(
      Iterator<T> it, BooleanSupplier terminalCondition) {
    requireNonNull(it);
    requireNonNull(terminalCondition);
    return new Iterator<T>() {
      boolean peeked = false;
      boolean hasNext = false;
      @Override
      public boolean hasNext() {
        if (peeked) return hasNext;
        hasNext = !terminalCondition.getAsBoolean() && it.hasNext();
        peeked = true;
        return hasNext;
      }

      @Override
      public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        T next = it.next();
        peeked = false;
        return next;
      }
    };
  }

  /**
   * Returns a collector that first copies all input elements into a new {@code Stream} and then
   * passes the stream to {@code toSink} function, which translates it to the final result.
   *
   * @since 3.6
   */
  static <T, R> Collector<T, ?, R> copying(Function<Stream<T>, R> toSink) {
    return Collectors.collectingAndThen(toStream(), toSink);
  }

  static <F, T> Stream<T> mapBySpliterator(
      Stream<F> stream, int characteristics,
      Function<? super Spliterator<F>, ? extends Spliterator<T>> mapper) {
    requireNonNull(mapper);
    Stream<T> mapped = StreamSupport.stream(
        () -> mapper.apply(stream.spliterator()), characteristics, stream.isParallel());
    mapped.onClose(stream::close);
    return mapped;
  }

  /** Copying input elements into another stream. */
  private static <T> Collector<T, ?, Stream<T>> toStream() {
    return Collector.of(
        Stream::<T>builder,
        Stream.Builder::add,
        (b1, b2) -> {
          b2.build().forEachOrdered(b1::add);
          return b1;
        },
        Stream.Builder::build);
  }

  private static <F, T> T splitThenWrap(
      Spliterator<F> from, Function<? super Spliterator<F>, ? extends T> wrapper) {
    Spliterator<F> it = from.trySplit();
    return it == null ? null : wrapper.apply(it);
  }

  private static final class DicedSpliterator<T> implements Spliterator<List<T>> {
    private final Spliterator<? extends T> underlying;
    private final int maxSize;

    DicedSpliterator(Spliterator<? extends T> underlying, int maxSize) {
      this.underlying = requireNonNull(underlying);
      this.maxSize = maxSize;
    }

    @Override public boolean tryAdvance(Consumer<? super List<T>> action) {
      requireNonNull(action);
      List<T> chunk = new ArrayList<>(chunkSize());
      for (int i = 0; i < maxSize && underlying.tryAdvance(chunk::add); i++) {}
      if (chunk.isEmpty()) return false;
      action.accept(chunk);
      return true;
    }

    @Override public Spliterator<List<T>> trySplit() {
      return splitThenWrap(underlying, it -> new DicedSpliterator<>(it, maxSize));
    }

    @Override public long estimateSize() {
      long size = underlying.estimateSize();
      return size == Long.MAX_VALUE ? Long.MAX_VALUE : estimateChunks(size);
    }

    @Override public long getExactSizeIfKnown() {
      return -1;
    }

    @Override public int characteristics() {
      return Spliterator.NONNULL;
    }

    private int chunkSize() {
      long estimate = underlying.estimateSize();
      if (estimate <= maxSize) return (int) estimate;
      // The user could set a large chunk size for an unknown-size stream, don't blow up memory.
      return estimate == Long.MAX_VALUE ? Math.min(maxSize, 8192) : maxSize;
    }

    private long estimateChunks(long size) {
      long lower = size / maxSize;
      return lower + ((size % maxSize == 0) ? 0 : 1);
    }
  }

  private static final class FlattenedSpliterator<T> implements Spliterator<T> {
    private final Spliterator<? extends Stream<? extends T>> blocks;
    private Spliterator<? extends T> currentBlock;
    private final Consumer<Stream<? extends T>> nextBlock = block -> {
      currentBlock = block.spliterator();
    };

    FlattenedSpliterator(Spliterator<? extends Stream<? extends T>> blocks) {
      this.blocks = requireNonNull(blocks);
    }

    private FlattenedSpliterator(
        Spliterator<? extends Stream<? extends T>> blocks, Spliterator<? extends T> currentBlock) {
      this.blocks = requireNonNull(blocks);
      this.currentBlock = currentBlock;
    }

    @Override public boolean tryAdvance(Consumer<? super T> action) {
      requireNonNull(action);
      if (currentBlock == null && !tryAdvanceBlock()) {
        return false;
      }
      boolean advanced = false;
      while ((!(advanced = currentBlock.tryAdvance(action))) && tryAdvanceBlock()) {}
      return advanced;
    }

    @Override public Spliterator<T> trySplit() {
      return splitThenWrap(blocks, it -> {
        Spliterator<T> result = new FlattenedSpliterator<>(it, currentBlock);
        currentBlock = null;
        return result;
      });
    }

    @Override public long estimateSize() {
      return Long.MAX_VALUE;
    }

    @Override public long getExactSizeIfKnown() {
      return -1;
    }

    @Override public int characteristics() {
      // While we maintain encounter order as long as 'blocks' does, returning an ordered stream
      // (which can be infinite) could surprise users when the user does things like
      // "parallel().limit(n)". It's sufficient for normal use cases to respect encounter order
      // without reporting order-ness.
      return 0;
    }

    private boolean tryAdvanceBlock() {
      return blocks.tryAdvance(nextBlock);
    }
  }

  private MoreStreams() {}
}

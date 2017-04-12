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
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.mu.function.CheckedConsumer;

/** Static utilities pertaining to {@link Stream} in addition to relevant utilities in Jdk and Guava. */
public final class MoreStreams {

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
    if (maxSize <= 0) throw new IllegalArgumentException();
    Stream<List<T>> diced = StreamSupport.stream(
        () -> dice(stream.spliterator(), maxSize), Spliterator.NONNULL, stream.isParallel());
    return diced.onClose(stream::close);
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
    if (maxSize <= 0) throw new IllegalArgumentException();
    return new DicedSpliterator<T>(spliterator, maxSize);
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
      Spliterator<? extends T> split = underlying.trySplit();
      return split == null ? null : new DicedSpliterator<T>(split, maxSize);
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

  private MoreStreams() {}
}

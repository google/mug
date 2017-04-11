package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Static utilities pertaining to {@link Stream} in addition to relevant utilities in Jdk and Guava. */
public final class MoreStreams {

  /**
   * Dices {@code stream} into smaller chunks each with up to {@code maxSize} elements.
   *
   * <p>For a sequential stream, the first N-1 chunk's will contain exactly {@code maxSize}
   * elements and the last chunk may contain less (but never 0).
   * However for parallel streams, it's possible that the stream is split in roughly equal-sized
   * sub streams before being diced into smaller chunks, which then will result in more than one
   * chunks with less than {@code maxSize} elements.
   *
   * @param stream the source stream to be diced
   * @param maxSize the maximum size for each chunk
   * @return Stream of diced chunks each being a list of size up to {@code maxSize}
   * @throws IllegalStateException if {@code maxSize <= 0}
   */
  public static <T> Stream<List<T>> dice(Stream<? extends T> stream, int maxSize) {
    return StreamSupport.stream(dice(stream.spliterator(), maxSize), stream.isParallel());
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
    return new DicedSpliterator<T>(spliterator, maxSize);
  }

  private static final class DicedSpliterator<T> implements Spliterator<List<T>> {
    private final Spliterator<? extends T> underlying;
    private final int maxSize;

    DicedSpliterator(Spliterator<? extends T> underlying, int maxSize) {
      this.underlying = requireNonNull(underlying);
      this.maxSize = maxSize;
      if (maxSize <= 0) throw new IllegalArgumentException();
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
      return (underlying.characteristics() | Spliterator.NONNULL)
          & ((-1) ^ Spliterator.SORTED ^ Spliterator.SIZED ^ Spliterator.SUBSIZED);
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

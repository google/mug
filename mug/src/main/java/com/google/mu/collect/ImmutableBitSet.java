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

import static com.google.mu.collect.InternalUtils.checkArgument;
import static java.util.stream.Collector.Characteristics.UNORDERED;

import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * An immutable set of non-negative bit indices.
 *
 * <p>{@code ImmutableBitSet} is not a subclass of {@link BitSet}, but it emulates the read-only API
 * of {@code BitSet} ({@link #get get()}, {@link #cardinality cardinality()}, {@link #length
 * length()}, {@link #stream stream()}, {@link #toLongArray toLongArray()} etc.), so it can mostly
 * be a drop-in replacement for {@code BitSet} where immutability is desirable. Instances are safe
 * to share between threads. The differences from {@code BitSet}:
 *
 * <ul>
 *   <li>There are no mutating methods. Use {@link #builder}, {@link #valueOf(long...)} or {@link
 *       #toImmutableBitSet} to create a set, and {@link #toBuilder} to derive a modified copy.
 *   <li>{@link #intersects} takes another {@code ImmutableBitSet}.
 *   <li>An {@code ImmutableBitSet} is never {@link #equals equal} to a {@code BitSet}.
 * </ul>
 *
 * @since 11.4
 */
public abstract class ImmutableBitSet {
  /** The empty set. */
  public static final ImmutableBitSet EMPTY = new SingleWord(0);

  /** Returns a new builder. */
  public static Builder builder() {
    return new Builder(new BitSet());
  }

  /**
   * Returns a new builder with room for the bits below {@code expectedSize}, the expected {@link
   * #size} of the built set. Bits at or above {@code expectedSize} can still be set, and the built
   * set's {@link #size} depends only on the bits that are set.
   *
   * @throws IllegalArgumentException if {@code expectedSize} is negative
   */
  public static Builder builderWithExpectedSize(int expectedSize) {
    checkArgument(expectedSize >= 0, "expectedSize (%s) cannot be negative", expectedSize);
    return new Builder(new BitSet(expectedSize));
  }

  /**
   * Returns the set of bits that are set in {@code word}: bit {@code n} is in the set if {@code
   * (word & (1L << n)) != 0}, for all {@code n < 64}.
   */
  public static ImmutableBitSet valueOf(long word) {
    return word == 0 ? EMPTY : new SingleWord(word);
  }

  /**
   * Returns the set of bits that are set in {@code words}, in the same little-endian layout as
   * {@link BitSet#valueOf(long[]) BitSet.valueOf(long[])}: bit {@code n} is in the set if {@code
   * (words[n / 64] & (1L << (n % 64))) != 0}, for all {@code n < 64 * words.length}.
   */
  public static ImmutableBitSet valueOf(long... words) {
    int used = words.length;
    while (used > 0 && words[used - 1] == 0) {
      used--;
    }
    if (used == 0) {
      return EMPTY;
    }
    if (used == 1) {
      return new SingleWord(words[0]);
    }
    return new MultiWord(BitSet.valueOf(words));
  }

  /**
   * Returns a collector of bit indices, the opposite of {@link #stream}: {@code
   * set.stream().boxed().collect(toImmutableBitSet())} equals {@code set}. Duplicate indices are
   * set once, and a negative index throws {@link IndexOutOfBoundsException}.
   */
  public static Collector<Integer, ?, ImmutableBitSet> toImmutableBitSet() {
    return Collector.of(
        ImmutableBitSet::builder, Builder::set, Builder::or, Builder::build, UNORDERED);
  }

  /**
   * Returns a collector of the union of the input sets: a bit is in the result if it's set in any
   * input set. Collecting no sets returns the empty set.
   */
  public static Collector<ImmutableBitSet, ?, ImmutableBitSet> or() {
    return Collector.of(
        ImmutableBitSet::builder, Builder::or, Builder::or, Builder::build, UNORDERED);
  }

  /**
   * Returns a collector of the symmetric difference of the input sets: a bit is in the result if
   * it's set in an odd number of input sets. Collecting no sets returns the empty set.
   */
  public static Collector<ImmutableBitSet, ?, ImmutableBitSet> xor() {
    return Collector.of(
        ImmutableBitSet::builder, Builder::xor, Builder::xor, Builder::build, UNORDERED);
  }

  /**
   * Returns true if the bit at {@code index} is set. Returns false for any index past the highest
   * set bit.
   *
   * @throws IndexOutOfBoundsException if {@code index} is negative
   */
  public abstract boolean get(int index);

  /** Returns the number of set bits. */
  public abstract int cardinality();

  /**
   * Like {@link BitSet#size() BitSet.size()}, returns the number of bits of space used: 64 if every
   * set bit is below 64 (including the empty set), otherwise 64 times the number of 64-bit words up
   * to and including the one holding the highest set bit.
   */
  public abstract int size();

  /** Returns true if no bit is set. */
  public abstract boolean isEmpty();

  /**
   * Returns the index of the highest set bit plus one, or 0 if no bit is set, like {@link
   * BitSet#length()}.
   */
  public abstract int length();

  /** Returns true if any bit is set in both this set and {@code that}. */
  public abstract boolean intersects(ImmutableBitSet that);

  /**
   * Returns the indices of the set bits in ascending order, like {@link BitSet#stream()
   * BitSet.stream()}.
   */
  public abstract IntStream stream();

  /**
   * Returns the set bits as a new array of words, in the little-endian layout of {@link
   * #valueOf(long...)} and without trailing zero words, like {@link BitSet#toLongArray()}.
   */
  public abstract long[] toLongArray();

  /** Returns true if {@code obj} is an {@code ImmutableBitSet} with the same bits set. */
  @Override public abstract boolean equals(Object obj);

  @Override public abstract int hashCode();

  /**
   * Returns the set bits in ascending order, formatted like {@link BitSet#toString()}, for example
   * {@code {1, 3, 100}}.
   */
  @Override public String toString() {
    return asBitSet().toString();
  }

  abstract BitSet asBitSet();
  abstract boolean intersectsWord(long word);
  abstract boolean intersectsBitSet(BitSet bits);

  /**
   * Returns a new builder with the bits of this set. Changes to the builder don't affect this set.
   */
  public Builder toBuilder() {
    return builderWithExpectedSize(size()).or(this);
  }

  /** Builder of {@link ImmutableBitSet}. */
  public static final class Builder {
    private final BitSet bits;

    Builder(BitSet bits) {
      this.bits = bits;
    }

    /**
     * Sets the bit at {@code index}. Setting a bit that is already set has no effect.
     *
     * @return this builder
     * @throws IndexOutOfBoundsException if {@code index} is negative
     */
    public Builder set(int index) {
      checkNotNegative(index);
      bits.set(index);
      return this;
    }

    /**
     * Clears the bits that aren't set in {@code that}, like {@link BitSet#and(BitSet)}.
     *
     * @return this builder
     */
    public Builder and(ImmutableBitSet that) {
      bits.and(that.asBitSet());
      return this;
    }

    /**
     * Sets the bits that are set in {@code that}, like {@link BitSet#or(BitSet)}.
     *
     * @return this builder
     */
    public Builder or(ImmutableBitSet that) {
      bits.or(that.asBitSet());
      return this;
    }

    /**
     * Flips the bits that are set in {@code that}, like {@link BitSet#xor(BitSet)}.
     *
     * @return this builder
     */
    public Builder xor(ImmutableBitSet that) {
      bits.xor(that.asBitSet());
      return this;
    }

    /**
     * Clears the bits that are set in {@code that}, like {@link BitSet#andNot(BitSet)}.
     *
     * @return this builder
     */
    public Builder andNot(ImmutableBitSet that) {
      bits.andNot(that.asBitSet());
      return this;
    }

    private Builder or(Builder that) {
      bits.or(that.bits);
      return this;
    }

    private Builder xor(Builder that) {
      bits.xor(that.bits);
      return this;
    }

    /** Returns an immutable set of the bits set so far. */
    public ImmutableBitSet build() {
      int length = bits.length();
      if (length == 0) {
        return EMPTY;
      }
      if (length <= Long.SIZE) {
        return new SingleWord(bits.toLongArray()[0]);
      }
      BitSet trimmed = new BitSet(length);
      trimmed.or(bits);
      return new MultiWord(trimmed);
    }
  }

  private static final class SingleWord extends ImmutableBitSet {
    private final long word;

    SingleWord(long word) {
      this.word = word;
    }

    @Override public boolean get(int index) {
      checkNotNegative(index);
      return index < Long.SIZE && (word & (1L << index)) != 0;
    }

    @Override public int cardinality() {
      return Long.bitCount(word);
    }

    @Override public int size() {
      return Long.SIZE;
    }

    @Override public boolean isEmpty() {
      return word == 0;
    }

    @Override public IntStream stream() {
      PrimitiveIterator.OfInt iterator = new PrimitiveIterator.OfInt() {
        private long unvisitedBits = word;

        @Override public boolean hasNext() {
          return unvisitedBits != 0;
        }

        @Override public int nextInt() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          int bit = Long.numberOfTrailingZeros(unvisitedBits);
          unvisitedBits &= unvisitedBits - 1;
          return bit;
        }
      };
      return StreamSupport.intStream(
          Spliterators.spliterator(
              iterator,
              Long.bitCount(word),
              Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SORTED),
          false);
    }

    @Override public int length() {
      return Long.SIZE - Long.numberOfLeadingZeros(word);
    }

    @Override public boolean intersects(ImmutableBitSet that) {
      return that.intersectsWord(word);
    }

    @Override public long[] toLongArray() {
      return word == 0 ? new long[0] : new long[] {word};
    }

    @Override public boolean equals(Object obj) {
      // A set whose bits are all below 64, including the empty set, is always a SingleWord.
      return obj instanceof SingleWord && ((SingleWord) obj).word == word;
    }

    @Override public int hashCode() {
      return Long.hashCode(word);
    }

    @Override BitSet asBitSet() {
      return BitSet.valueOf(new long[] {word});
    }

    @Override boolean intersectsWord(long other) {
      return (word & other) != 0;
    }

    @Override boolean intersectsBitSet(BitSet bits) {
      return wordIntersects(word, bits);
    }
  }

  private static final class MultiWord extends ImmutableBitSet {
    private final BitSet bits;
    private final int cardinality;

    MultiWord(BitSet bits) {
      this.bits = bits;
      this.cardinality = bits.cardinality();
    }

    @Override public boolean get(int index) {
      checkNotNegative(index);
      return bits.get(index);
    }

    @Override public int cardinality() {
      return cardinality;
    }

    @Override public int size() {
      return bits.size();
    }

    @Override public boolean isEmpty() {
      return bits.isEmpty();
    }

    @Override public IntStream stream() {
      return bits.stream();
    }

    @Override public int length() {
      return bits.length();
    }

    @Override public boolean intersects(ImmutableBitSet that) {
      return that.intersectsBitSet(bits);
    }

    @Override public long[] toLongArray() {
      return bits.toLongArray();
    }

    @Override public boolean equals(Object obj) {
      return obj instanceof MultiWord && ((MultiWord) obj).bits.equals(bits);
    }

    @Override public int hashCode() {
      return bits.hashCode();
    }

    @Override BitSet asBitSet() {
      return bits;
    }

    @Override boolean intersectsWord(long word) {
      return wordIntersects(word, bits);
    }

    @Override boolean intersectsBitSet(BitSet other) {
      return bits.intersects(other);
    }
  }

  private static boolean wordIntersects(long word, BitSet bits) {
    for (long remaining = word; remaining != 0; remaining &= remaining - 1) {
      if (bits.get(Long.numberOfTrailingZeros(remaining))) {
        return true;
      }
    }
    return false;
  }

  private static void checkNotNegative(int index) {
    if (index < 0) {
      throw new IndexOutOfBoundsException("index (" + index + ") cannot be negative");
    }
  }

  private ImmutableBitSet() {}
}

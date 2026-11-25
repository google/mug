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
 *   <li>There are no mutating methods. Use {@link #builder}, {@link #valueOf(long[])} or {@link
 *       #toImmutableBitSet} to create a set, and {@link #toBuilder} to derive a modified copy.
 *   <li>There is no {@link BitSet#nextSetBit(int) BitSet.nextSetBit()}; in place of the legacy
 *       {@code nextSetBit()} loop, use {@link #stream stream()}{@code .forEach(...)}.
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
   * Returns the set of bits that are set in {@code words}, in the same little-endian layout as
   * {@link BitSet#valueOf(long[]) BitSet.valueOf(long[])}: bit {@code n} is in the set if {@code
   * (words[n / 64] & (1L << (n % 64))) != 0}, for all {@code n < 64 * words.length}.
   *
   * @throws IllegalArgumentException if any set bit would have an index above {@link
   *     Integer#MAX_VALUE}
   */
  public static ImmutableBitSet valueOf(long[] words) {
    for (int i = words.length - 1; i >= 0; i--) {
      long word = words[i];
      if (word != 0) {
        if (i == 0) {
          return new SingleWord(word);
        }
        checkArgument(
            i <= Integer.MAX_VALUE / 64,
            "bit index exceeds Integer.MAX_VALUE (non-zero word at index %s)", i);
        return new MultiWord(BitSet.valueOf(words));
      }
    }
    return EMPTY;
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
   * to and including the one holding the highest set bit. As in {@code BitSet.size()}, this
   * overflows to {@link Integer#MIN_VALUE} when any bit in {@code [Integer.MAX_VALUE - 63,
   * Integer.MAX_VALUE]} is set.
   */
  public abstract int size();

  /** Returns true if no bit is set. */
  public abstract boolean isEmpty();

  /**
   * Returns the index of the highest set bit plus one, or 0 if no bit is set, like {@link
   * BitSet#length()}. As in {@code BitSet.length()}, this overflows to {@link Integer#MIN_VALUE}
   * when {@link Integer#MAX_VALUE} is set.
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
   * #valueOf(long[])} and without trailing zero words, like {@link BitSet#toLongArray()}.
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

  /**
   * Returns a new builder with the bits of this set. Changes to the builder don't affect this set.
   */
  public abstract Builder toBuilder();

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
      bits.set(index);
      return this;
    }

    /**
     * Sets the bit at {@code index} to {@code value}, like {@link BitSet#set(int, boolean)}.
     *
     * @return this builder
     * @throws IndexOutOfBoundsException if {@code index} is negative
     */
    public Builder set(int index, boolean value) {
      bits.set(index, value);
      return this;
    }

    /**
     * Sets the bits from {@code fromIndex} (inclusive) to {@code toIndex} (exclusive), like {@link
     * BitSet#set(int, int)}.
     *
     * @return this builder
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, {@code toIndex} is
     *     negative, or {@code fromIndex > toIndex}
     */
    public Builder set(int fromIndex, int toIndex) {
      bits.set(fromIndex, toIndex);
      return this;
    }

    /**
     * Clears the bit at {@code index}. Clearing a bit that is already cleared has no effect.
     *
     * @return this builder
     * @throws IndexOutOfBoundsException if {@code index} is negative
     */
    public Builder clear(int index) {
      bits.clear(index);
      return this;
    }

    /**
     * Clears the bits from {@code fromIndex} (inclusive) to {@code toIndex} (exclusive), like
     * {@link BitSet#clear(int, int)}.
     *
     * @return this builder
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, {@code toIndex} is
     *     negative, or {@code fromIndex > toIndex}
     */
    public Builder clear(int fromIndex, int toIndex) {
      bits.clear(fromIndex, toIndex);
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

    /** Returns true if no bit is set. */
    public boolean isEmpty() {
      return bits.isEmpty();
    }

    /** Returns an immutable set of the bits set so far. */
    public ImmutableBitSet build() {
      // BitSet.length() overflows to Integer.MIN_VALUE when bit Integer.MAX_VALUE is set.
      int length = overflowSafe(bits.length());
      if (length == 0) {
        return EMPTY;
      }
      if (length <= 64) {
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

    // & rather than &&: faster when indices fall unpredictably inside and outside [0, 64).
    @SuppressWarnings("ShortCircuitBoolean")
    @Override public boolean get(int index) {
      if (index < 0) {
        throw new IndexOutOfBoundsException("bitIndex < 0: " + index);
      }
      return index < 64 & (word & (1L << index)) != 0;
    }

    @Override public int cardinality() {
      return Long.bitCount(word);
    }

    @Override public int size() {
      return 64;
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
      return 64 - Long.numberOfLeadingZeros(word);
    }

    @Override public boolean intersects(ImmutableBitSet that) {
      return that.intersects(word);
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

    @Override public Builder toBuilder() {
      return new Builder(BitSet.valueOf(new long[] {word}));
    }

    @Override BitSet asBitSet() {
      return BitSet.valueOf(new long[] {word});
    }

    @Override boolean intersects(long other) {
      return (word & other) != 0;
    }

    @Override boolean intersects(BitSet bits) {
      return wordIntersects(word, bits);
    }
  }

  private static final class MultiWord extends ImmutableBitSet {
    private final BitSet bits;
    // Lazily cached (0 means uncomputed). Benign data races on int.
    private int cardinality;
    private int hashCode;

    MultiWord(BitSet bits) {
      this.bits = bits;
    }

    @Override public boolean get(int index) {
      return bits.get(index);
    }

    @Override public int cardinality() {
      int result = cardinality;
      if (result == 0) {
        cardinality = result = bits.cardinality();
      }
      return result;
    }

    @Override public int size() {
      return bits.size();
    }

    @Override public boolean isEmpty() {
      return false;
    }

    @Override public IntStream stream() {
      return bits.stream();
    }

    @Override public int length() {
      return bits.length();
    }

    @Override public boolean intersects(ImmutableBitSet that) {
      return that.intersects(bits);
    }

    @Override public long[] toLongArray() {
      return bits.toLongArray();
    }

    @Override public boolean equals(Object obj) {
      return obj instanceof MultiWord && ((MultiWord) obj).bits.equals(bits);
    }

    @Override public int hashCode() {
      int result = hashCode;
      if (result == 0) {
        hashCode = result = bits.hashCode();
      }
      return result;
    }

    @Override public Builder toBuilder() {
      // Can't use bits.clone(): if its size isn't sticky, clone() may trim its array, an
      // unsynchronized write to a BitSet that other threads may be reading. or() only reads its
      // argument.
      // BitSet.size() overflows to Integer.MIN_VALUE when a bit >= Integer.MAX_VALUE - 63 is set.
      return builderWithExpectedSize(overflowSafe(size())).or(this);
    }

    @Override BitSet asBitSet() {
      return bits;
    }

    @Override boolean intersects(long word) {
      return wordIntersects(word, bits);
    }

    @Override boolean intersects(BitSet other) {
      return bits.intersects(other);
    }
  }

  abstract BitSet asBitSet();
  abstract boolean intersects(long word);
  abstract boolean intersects(BitSet bits);

  private static boolean wordIntersects(long word, BitSet bits) {
    for (long remaining = word; remaining != 0; remaining &= remaining - 1) {
      if (bits.get(Long.numberOfTrailingZeros(remaining))) {
        return true;
      }
    }
    return false;
  }

  private static int overflowSafe(int n) {
    return n < 0 ? Integer.MAX_VALUE : n;
  }

  private ImmutableBitSet() {}
}

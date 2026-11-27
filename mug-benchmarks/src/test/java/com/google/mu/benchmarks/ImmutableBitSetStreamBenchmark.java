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
package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.mu.collect.ImmutableBitSet;
import java.util.BitSet;
import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compares {@link ImmutableBitSet#stream} on a single-word set (all bits below 64) with streaming a
 * {@link BitSet} made from the same word, which is what {@code stream()} would run if it delegated
 * to {@code BitSet}.
 *
 * <p>{@code cardinality} bits of the 64-bit word are set, chosen with a fixed seed. Each method
 * creates a new stream and consumes it, either with {@code sum()}, which traverses through {@code
 * forEachRemaining()}, or with a loop over {@code iterator()}, which traverses through {@code
 * tryAdvance()}.
 *
 * <p>Results on JMH 1.37, JDK 24.0.1, arm64 macOS (Apple M3 Pro) with {@code -prof gc}; 5x1s
 * warmup, 5x1s measurement, 3 forks. Each cell is ns/op with its 99.9% confidence interval, and
 * B/op on the line below.
 *
 * <pre>
 * Benchmark                    1 bit           8 bits          32 bits      64 bits
 * immutableBitSet_sum          7.724 +- 0.046  10.7 +- 0.1     26.9 +- 0.3  52.1 +- 0.4
 *                              136             136             136          136
 * delegatingToBitSet_sum       9.005 +- 0.058  14.6 +- 0.2     44.5 +- 0.9  78.7 +- 2.9
 *                              160             160             160          160
 * immutableBitSet_iterator     1.901 +- 0.011  3.781 +- 0.046  13.9 +- 0.2  31.1 +- 0.9
 *                              56              56              56           56
 * delegatingToBitSet_iterator  2.365 +- 0.063  17.8 +- 0.2     95.5 +- 3.0  199 +- 2
 *                              80              104             104          104
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class ImmutableBitSetStreamBenchmark {
  @Param({"1", "8", "32", "64"})
  public int cardinality;

  private long word;
  private ImmutableBitSet set;

  @Setup
  public void chooseBits() {
    word = randomWord(cardinality, new Random(42));
    set = ImmutableBitSet.valueOf(new long[] {word});
  }

  @Benchmark
  public int immutableBitSet_sum() {
    return set.stream().sum();
  }

  @Benchmark
  public int delegatingToBitSet_sum() {
    return BitSet.valueOf(new long[] {word}).stream().sum();
  }

  @Benchmark
  public int immutableBitSet_iterator() {
    return sumByIterator(set.stream());
  }

  @Benchmark
  public int delegatingToBitSet_iterator() {
    return sumByIterator(BitSet.valueOf(new long[] {word}).stream());
  }

  @Test public void chooseBits_cardinality1_setsOneBit() {
    cardinality = 1;
    chooseBits();
    assertThat(set.cardinality()).isEqualTo(1);
  }

  @Test public void chooseBits_cardinality64_setsAllBits() {
    cardinality = 64;
    chooseBits();
    assertThat(word).isEqualTo(-1L);
  }

  @Test public void chooseBits_cardinality64_lengthIs64() {
    cardinality = 64;
    chooseBits();
    assertThat(set.length()).isEqualTo(64);
  }

  @Test public void immutableBitSet_streamsSameBitsAsBitSet() {
    cardinality = 32;
    chooseBits();
    assertThat(set.stream())
        .containsExactlyElementsIn(
            BitSet.valueOf(new long[] {word}).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void sum_sameForBothVariants() {
    cardinality = 32;
    chooseBits();
    assertThat(immutableBitSet_sum()).isEqualTo(delegatingToBitSet_sum());
  }

  @Test public void iterator_sameForBothVariants() {
    cardinality = 32;
    chooseBits();
    assertThat(immutableBitSet_iterator()).isEqualTo(delegatingToBitSet_iterator());
  }

  private static long randomWord(int cardinality, Random random) {
    long word = 0;
    while (Long.bitCount(word) < cardinality) {
      word |= 1L << random.nextInt(Long.SIZE);
    }
    return word;
  }

  private static int sumByIterator(IntStream stream) {
    int sum = 0;
    for (PrimitiveIterator.OfInt it = stream.iterator(); it.hasNext(); ) {
      sum += it.nextInt();
    }
    return sum;
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }
}

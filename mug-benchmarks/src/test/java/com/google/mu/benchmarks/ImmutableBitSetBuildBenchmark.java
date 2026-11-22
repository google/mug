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
import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
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
 * Compares the cost of building the same bits as an {@link ImmutableBitSet}, a {@link BitSet} and a
 * {@link BigInteger}.
 *
 * <p>About half of the indices in {@code [0, universe)} are set, plus the highest index, either in
 * ascending order or shuffled with a fixed seed. Each type is built two ways: without a size hint
 * ({@code ImmutableBitSet.builder()}, {@code new BitSet()}, chained {@code BigInteger.setBit()}),
 * and sized to the universe up front ({@code ImmutableBitSet.builderWithExpectedSize()}, {@code new
 * BitSet(universe)}, a big-endian {@code byte[]} passed to {@code new BigInteger(1, bytes)}).
 *
 * <p>Results on JMH 1.37, JDK 24.0.1, arm64 macOS (Apple M3 Pro) with {@code -prof gc}; 5x1s
 * warmup, 5x1s measurement, 3 forks. Each cell is ns/op with its 99.9% confidence interval, and
 * B/op on the line below. In the 64-bit {@code immutableBitSet_*} cells, individual iterations
 * measured either 47 to 58 ns or 77 to 107 ns, with the same B/op, which widens their intervals.
 * The cause is not known.
 *
 * <pre>
 * ASCENDING                                64 bits       1024 bits     65536 bits
 * immutableBitSet_builder                  81.0 +- 21.2  567 +- 93     30328 +- 773
 *                                          96            544           24832
 * immutableBitSet_builderWithExpectedSize  70.1 +- 25.7  407 +- 1      29672 +- 986
 *                                          96            360           16528
 * javaUtilBitSet_newBitSet                 37.6 +- 0.7   510 +- 58     29770 +- 169
 *                                          48            352           16576
 * javaUtilBitSet_newBitSetWithSize         24.5 +- 0.5   388 +- 1      29379 +- 138
 *                                          48            168           8232
 * bigInteger_setBit                        346 +- 5      10199 +- 158  16174563 +- 118415
 *                                          2760          111272        272869456
 * bigInteger_fromByteArray                 53.0 +- 2.1   1037 +- 16    52937 +- 896
 *                                          88            328           16456
 *
 * RANDOM                                   64 bits       1024 bits     65536 bits
 * immutableBitSet_builder                  73.1 +- 24.4  484 +- 4      25072 +- 1895
 *                                          96            536           29456
 * immutableBitSet_builderWithExpectedSize  77.4 +- 24.3  404 +- 1      23689 +- 52
 *                                          96            360           16528
 * javaUtilBitSet_newBitSet                 37.5 +- 0.8   418 +- 7      23713 +- 222
 *                                          48            344           21200
 * javaUtilBitSet_newBitSetWithSize         24.2 +- 0.0   390 +- 2      23269 +- 101
 *                                          48            168           8232
 * bigInteger_setBit                        336 +- 4      15187 +- 149  35908319 +- 173020
 *                                          2432          99144         396389733
 * bigInteger_fromByteArray                 50.9 +- 1.1   328 +- 4      19230 +- 114
 *                                          88            328           16456
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class ImmutableBitSetBuildBenchmark {
  public enum InsertionOrder {
    ASCENDING,
    RANDOM
  }

  @Param({"64", "1024", "65536"})
  public int universe;

  @Param({"ASCENDING", "RANDOM"})
  public InsertionOrder order = InsertionOrder.ASCENDING;

  private int[] indices;

  @Setup
  public void chooseIndices() {
    Random random = new Random(42);
    indices = IntStream.range(0, universe)
        .filter(i -> random.nextBoolean() || i == universe - 1)
        .toArray();
    if (order == InsertionOrder.RANDOM) {
      shuffle(indices, new Random(42));
    }
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_builder() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int index : indices) {
      builder.set(index);
    }
    return builder.build();
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_builderWithExpectedSize() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builderWithExpectedSize(universe);
    for (int index : indices) {
      builder.set(index);
    }
    return builder.build();
  }

  @Benchmark
  public BitSet javaUtilBitSet_newBitSet() {
    BitSet bitSet = new BitSet();
    for (int index : indices) {
      bitSet.set(index);
    }
    return bitSet;
  }

  @Benchmark
  public BitSet javaUtilBitSet_newBitSetWithSize() {
    BitSet bitSet = new BitSet(universe);
    for (int index : indices) {
      bitSet.set(index);
    }
    return bitSet;
  }

  @Benchmark
  public BigInteger bigInteger_setBit() {
    BigInteger bits = BigInteger.ZERO;
    for (int index : indices) {
      bits = bits.setBit(index);
    }
    return bits;
  }

  @Benchmark
  public BigInteger bigInteger_fromByteArray() {
    byte[] bigEndian = new byte[(universe + 7) / 8];
    for (int index : indices) {
      bigEndian[bigEndian.length - 1 - (index >> 3)] |= (byte) (1 << (index & 7));
    }
    return new BigInteger(1, bigEndian);
  }

  @Test public void immutableBitSet_builder_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(immutableBitSet_builder().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void immutableBitSet_builderWithExpectedSize_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(immutableBitSet_builderWithExpectedSize().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void javaUtilBitSet_newBitSet_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(javaUtilBitSet_newBitSet().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void javaUtilBitSet_newBitSetWithSize_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(javaUtilBitSet_newBitSetWithSize().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void bigInteger_setBit_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(setBitsOf(bigInteger_setBit()))
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void bigInteger_fromByteArray_buildsExpectedBits() {
    universe = 1024;
    chooseIndices();
    assertThat(setBitsOf(bigInteger_fromByteArray()))
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void randomOrder_isPermutationOfAscendingOrder() {
    universe = 1024;
    chooseIndices();
    int[] ascending = indices;
    order = InsertionOrder.RANDOM;
    chooseIndices();
    assertThat(Arrays.stream(indices).sorted().toArray()).isEqualTo(ascending);
  }

  @Test public void randomOrder_isNotAscending() {
    universe = 1024;
    chooseIndices();
    int[] ascending = indices;
    order = InsertionOrder.RANDOM;
    chooseIndices();
    assertThat(indices).isNotEqualTo(ascending);
  }

  @Test public void immutableBitSet_builder_randomOrder_buildsExpectedBits() {
    universe = 1024;
    order = InsertionOrder.RANDOM;
    chooseIndices();
    assertThat(immutableBitSet_builder().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  @Test public void immutableBitSet_builderWithExpectedSize_randomOrder_buildsExpectedBits() {
    universe = 1024;
    order = InsertionOrder.RANDOM;
    chooseIndices();
    assertThat(immutableBitSet_builderWithExpectedSize().stream())
        .containsExactlyElementsIn(expectedIndices())
        .inOrder();
  }

  private List<Integer> expectedIndices() {
    return Arrays.stream(indices).sorted().boxed().collect(toList());
  }

  private IntStream setBitsOf(BigInteger bits) {
    return IntStream.range(0, universe).filter(bits::testBit);
  }

  private static void shuffle(int[] array, Random random) {
    for (int i = array.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      int swapped = array[i];
      array[i] = array[j];
      array[j] = swapped;
    }
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }
}

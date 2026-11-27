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
import static java.util.stream.Collectors.toList;

import com.google.mu.collect.ImmutableBitSet;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Compares {@link ImmutableBitSet#get}, {@link BitSet#get} and {@link BigInteger#testBit} on the
 * same bits and the same probe indices.
 *
 * <p>About half of the bits in {@code [0, universe)} are set, plus the highest index, so every
 * implementation spans the whole universe. The universes are 64 bits, 128 bits (the ASCII range)
 * and 1024 bits.
 *
 * <p>Each invocation probes 1024 random indices. Scores are per probe. {@code baseline} measures
 * the probe loop and the {@code Blackhole} alone.
 *
 * <p>Results on JMH 1.37, JDK 24.0.1, arm64 macOS (Apple M3 Pro) with {@code -prof gc}; 5x1s
 * warmup, 5x1s measurement, 3 forks. Errors are 99.9% confidence intervals. No method allocates.
 *
 * <pre>
 * ns per probe         64 bits         128 bits        1024 bits
 * baseline             0.092 +- 0.001  0.092 +- 0.000  0.093 +- 0.002
 * immutableBitSet_get  0.326 +- 0.004  0.485 +- 0.013  0.476 +- 0.004
 * javaUtilBitSet_get   0.474 +- 0.003  0.473 +- 0.001  0.473 +- 0.001
 * bigInteger_testBit   0.694 +- 0.008  0.693 +- 0.007  0.697 +- 0.002
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class ImmutableBitSetBenchmark {
  private static final int PROBES = 1024;

  @Param({"64", "128", "1024"})
  public int universe;

  private ImmutableBitSet immutableBitSet;
  private BitSet bitSet;
  private BigInteger bigInteger;
  private int[] probes;

  @Setup
  public void buildBitSets() {
    Random random = new Random(42);
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    bitSet = new BitSet();
    BigInteger bits = BigInteger.ZERO;
    for (int i = 0; i < universe; i++) {
      if (random.nextBoolean() || i == universe - 1) {
        builder.set(i);
        bitSet.set(i);
        bits = bits.setBit(i);
      }
    }
    immutableBitSet = builder.build();
    bigInteger = bits;
    probes = random.ints(PROBES, 0, universe).toArray();
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void baseline(Blackhole blackhole) {
    for (int probe : probes) {
      blackhole.consume((probe & 1) != 0);
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_get(Blackhole blackhole) {
    ImmutableBitSet set = immutableBitSet;
    for (int probe : probes) {
      blackhole.consume(set.get(probe));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void javaUtilBitSet_get(Blackhole blackhole) {
    BitSet set = bitSet;
    for (int probe : probes) {
      blackhole.consume(set.get(probe));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void bigInteger_testBit(Blackhole blackhole) {
    BigInteger set = bigInteger;
    for (int probe : probes) {
      blackhole.consume(set.testBit(probe));
    }
  }

  @Test public void smallUniverse_lengthIs64() {
    universe = 64;
    buildBitSets();
    assertThat(immutableBitSet.length()).isEqualTo(64);
  }

  @Test public void universe128_lengthIs128() {
    universe = 128;
    buildBitSets();
    assertThat(immutableBitSet.length()).isEqualTo(128);
  }

  @Test public void largeUniverse_lengthIs1024() {
    universe = 1024;
    buildBitSets();
    assertThat(immutableBitSet.length()).isEqualTo(1024);
  }

  @Test public void universe128_immutableBitSetMatchesBitSet() {
    universe = 128;
    buildBitSets();
    assertThat(probeResults(immutableBitSet::get)).isEqualTo(probeResults(bitSet::get));
  }

  @Test public void universe128_bigIntegerMatchesBitSet() {
    universe = 128;
    buildBitSets();
    assertThat(probeResults(bigInteger::testBit)).isEqualTo(probeResults(bitSet::get));
  }

  @Test public void smallUniverse_immutableBitSetMatchesBitSet() {
    universe = 64;
    buildBitSets();
    assertThat(probeResults(immutableBitSet::get)).isEqualTo(probeResults(bitSet::get));
  }

  @Test public void smallUniverse_bigIntegerMatchesBitSet() {
    universe = 64;
    buildBitSets();
    assertThat(probeResults(bigInteger::testBit)).isEqualTo(probeResults(bitSet::get));
  }

  @Test public void largeUniverse_immutableBitSetMatchesBitSet() {
    universe = 1024;
    buildBitSets();
    assertThat(probeResults(immutableBitSet::get)).isEqualTo(probeResults(bitSet::get));
  }

  @Test public void largeUniverse_bigIntegerMatchesBitSet() {
    universe = 1024;
    buildBitSets();
    assertThat(probeResults(bigInteger::testBit)).isEqualTo(probeResults(bitSet::get));
  }

  private List<Boolean> probeResults(IntPredicate implementation) {
    return Arrays.stream(probes).mapToObj(implementation::test).collect(toList());
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }
}

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures {@link ImmutableBitSet#get} at a call site that alternates between sets.
 *
 * <p>{@code immutableBitSet_smallAndLarge} alternates between a 64-bit set and a 1024-bit set,
 * which use different {@code ImmutableBitSet} implementations. {@code immutableBitSet_smallOnly}
 * and {@code immutableBitSet_largeOnly} alternate between two different sets of the same size, as
 * controls. {@code javaUtilBitSet_smallAndLarge} runs the same probes on {@link BitSet}.
 *
 * <p>{@code immutableBitSet_emptyOnly} runs the probes of {@code immutableBitSet_smallOnly} on
 * {@link ImmutableBitSet#EMPTY}, and {@code immutableBitSet_emptyAndSmall} runs them with the first
 * set replaced by {@code EMPTY}. {@code immutableBitSet_emptySmallAndLarge} rotates among {@code
 * EMPTY}, a 64-bit set and a 1024-bit set, and {@code javaUtilBitSet_emptySmallAndLarge} runs the
 * same probes on {@link BitSet}.
 *
 * <p>Each invocation makes 1024 probes. Probe {@code i} goes to set {@code i % n} of the {@code n}
 * sets, at an index within that set's universe (64 bits for {@code EMPTY}). Scores are per probe.
 *
 * <p>Results on JMH 1.37, JDK 24.0.1, arm64 macOS (Apple M3 Pro) with {@code -prof gc}; 5x1s
 * warmup, 5x1s measurement, 3 forks. Errors are 99.9% confidence intervals. No method allocates.
 *
 * <pre>
 * Benchmark                           ns per probe
 * immutableBitSet_emptyOnly           0.748 +- 0.001
 * immutableBitSet_smallOnly           0.619 +- 0.001
 * immutableBitSet_largeOnly           1.083 +- 0.005
 * immutableBitSet_emptyAndSmall       0.620 +- 0.004
 * immutableBitSet_smallAndLarge       0.823 +- 0.003
 * javaUtilBitSet_smallAndLarge        0.947 +- 0.003
 * immutableBitSet_emptySmallAndLarge  1.313 +- 0.005
 * javaUtilBitSet_emptySmallAndLarge   1.313 +- 0.008
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class ImmutableBitSetMixedGetBenchmark {
  private static final int PROBES = 1024;
  private static final int SMALL = 64;
  private static final int LARGE = 1024;

  private ImmutableBitSet[] smallAndLarge;
  private ImmutableBitSet[] smallOnly;
  private ImmutableBitSet[] largeOnly;
  private ImmutableBitSet[] emptyOnly;
  private ImmutableBitSet[] emptyAndSmall;
  private ImmutableBitSet[] emptySmallAndLarge;
  private BitSet[] smallAndLargeBitSets;
  private BitSet[] emptySmallAndLargeBitSets;
  private int[] smallAndLargeProbes;
  private int[] smallProbes;
  private int[] largeProbes;
  private int[] emptySmallAndLargeProbes;

  @Setup
  public void buildSets() {
    int[] small = randomIndices(SMALL, new Random(1));
    int[] otherSmall = randomIndices(SMALL, new Random(2));
    int[] large = randomIndices(LARGE, new Random(3));
    int[] otherLarge = randomIndices(LARGE, new Random(4));
    smallAndLarge = new ImmutableBitSet[] {immutableBitSetOf(small), immutableBitSetOf(large)};
    smallOnly = new ImmutableBitSet[] {immutableBitSetOf(small), immutableBitSetOf(otherSmall)};
    largeOnly = new ImmutableBitSet[] {immutableBitSetOf(large), immutableBitSetOf(otherLarge)};
    emptyOnly = new ImmutableBitSet[] {ImmutableBitSet.EMPTY, ImmutableBitSet.EMPTY};
    emptyAndSmall = new ImmutableBitSet[] {ImmutableBitSet.EMPTY, immutableBitSetOf(otherSmall)};
    emptySmallAndLarge =
        new ImmutableBitSet[] {
          ImmutableBitSet.EMPTY, immutableBitSetOf(small), immutableBitSetOf(large)
        };
    smallAndLargeBitSets = new BitSet[] {bitSetOf(small), bitSetOf(large)};
    emptySmallAndLargeBitSets = new BitSet[] {new BitSet(), bitSetOf(small), bitSetOf(large)};
    Random random = new Random(42);
    smallAndLargeProbes =
        IntStream.range(0, PROBES).map(i -> random.nextInt(i % 2 == 0 ? SMALL : LARGE)).toArray();
    smallProbes = random.ints(PROBES, 0, SMALL).toArray();
    largeProbes = random.ints(PROBES, 0, LARGE).toArray();
    emptySmallAndLargeProbes =
        IntStream.range(0, PROBES).map(i -> random.nextInt(i % 3 == 2 ? LARGE : SMALL)).toArray();
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_smallAndLarge(Blackhole blackhole) {
    ImmutableBitSet[] sets = smallAndLarge;
    int[] probes = smallAndLargeProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_smallOnly(Blackhole blackhole) {
    ImmutableBitSet[] sets = smallOnly;
    int[] probes = smallProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_largeOnly(Blackhole blackhole) {
    ImmutableBitSet[] sets = largeOnly;
    int[] probes = largeProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void javaUtilBitSet_smallAndLarge(Blackhole blackhole) {
    BitSet[] sets = smallAndLargeBitSets;
    int[] probes = smallAndLargeProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_emptyOnly(Blackhole blackhole) {
    ImmutableBitSet[] sets = emptyOnly;
    int[] probes = smallProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_emptyAndSmall(Blackhole blackhole) {
    ImmutableBitSet[] sets = emptyAndSmall;
    int[] probes = smallProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i & 1].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void immutableBitSet_emptySmallAndLarge(Blackhole blackhole) {
    ImmutableBitSet[] sets = emptySmallAndLarge;
    int[] probes = emptySmallAndLargeProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i % 3].get(probes[i]));
    }
  }

  @Benchmark
  @OperationsPerInvocation(PROBES)
  public void javaUtilBitSet_emptySmallAndLarge(Blackhole blackhole) {
    BitSet[] sets = emptySmallAndLargeBitSets;
    int[] probes = emptySmallAndLargeProbes;
    for (int i = 0; i < PROBES; i++) {
      blackhole.consume(sets[i % 3].get(probes[i]));
    }
  }

  @Test public void smallAndLarge_immutableBitSetMatchesBitSet() {
    buildSets();
    assertThat(
            alternatingResults(smallAndLargeProbes, smallAndLarge[0]::get, smallAndLarge[1]::get))
        .isEqualTo(
            alternatingResults(
                smallAndLargeProbes, smallAndLargeBitSets[0]::get, smallAndLargeBitSets[1]::get));
  }

  @Test public void smallAndLarge_sizesAre64And1024() {
    buildSets();
    assertThat(sizes(smallAndLarge)).containsExactly(64, 1024).inOrder();
  }

  @Test public void smallOnly_sizesAre64() {
    buildSets();
    assertThat(sizes(smallOnly)).containsExactly(64, 64);
  }

  @Test public void largeOnly_sizesAre1024() {
    buildSets();
    assertThat(sizes(largeOnly)).containsExactly(1024, 1024);
  }

  @Test public void emptyOnly_setsAreEmpty() {
    buildSets();
    assertThat(emptyOnly).asList().containsExactly(ImmutableBitSet.EMPTY, ImmutableBitSet.EMPTY);
  }

  @Test public void emptyAndSmall_setsAreEmptyAndSecondSmallOnlySet() {
    buildSets();
    assertThat(emptyAndSmall)
        .asList()
        .containsExactly(ImmutableBitSet.EMPTY, smallOnly[1])
        .inOrder();
  }

  @Test public void emptySmallAndLarge_setsAreEmptyThenSmallAndLarge() {
    buildSets();
    assertThat(emptySmallAndLarge)
        .asList()
        .containsExactly(ImmutableBitSet.EMPTY, smallAndLarge[0], smallAndLarge[1])
        .inOrder();
  }

  @Test public void emptySmallAndLarge_immutableBitSetMatchesBitSet() {
    buildSets();
    assertThat(
            alternatingResults(
                emptySmallAndLargeProbes,
                emptySmallAndLarge[0]::get,
                emptySmallAndLarge[1]::get,
                emptySmallAndLarge[2]::get))
        .isEqualTo(
            alternatingResults(
                emptySmallAndLargeProbes,
                emptySmallAndLargeBitSets[0]::get,
                emptySmallAndLargeBitSets[1]::get,
                emptySmallAndLargeBitSets[2]::get));
  }

  private static int[] randomIndices(int universe, Random random) {
    return IntStream.range(0, universe)
        .filter(i -> random.nextBoolean() || i == universe - 1)
        .toArray();
  }

  private static ImmutableBitSet immutableBitSetOf(int[] indices) {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int index : indices) {
      builder.set(index);
    }
    return builder.build();
  }

  private static BitSet bitSetOf(int[] indices) {
    BitSet bitSet = new BitSet();
    for (int index : indices) {
      bitSet.set(index);
    }
    return bitSet;
  }

  private static List<Boolean> alternatingResults(int[] probes, IntPredicate... sets) {
    List<Boolean> results = new ArrayList<>();
    for (int i = 0; i < probes.length; i++) {
      results.add(sets[i % sets.length].test(probes[i]));
    }
    return results;
  }

  private static List<Integer> sizes(ImmutableBitSet[] sets) {
    return Arrays.stream(sets).map(ImmutableBitSet::size).collect(toList());
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }
}

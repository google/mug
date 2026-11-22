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
 * Compares building 64-, 128- and 256-bit {@link ImmutableBitSet}s from words with {@code
 * ImmutableBitSet.valueOf()} against building them from indices with the builder, with {@link
 * BitSet#valueOf(long[])} as a reference.
 *
 * <p>The bits are chosen as in {@link ImmutableBitSetBuildBenchmark}: about half of the indices in
 * {@code [0, universe)} are set, plus the highest index, in ascending order. {@link Sized} holds
 * them as indices and as a {@code long[]}. {@link OneWord}, {@link TwoWords} and {@link FourWords}
 * hold the same words as {@code Sized} at 64, 128 and 256 bits, passed to {@code valueOf()} as
 * separate arguments.
 *
 * <p>Results on JMH 1.37, JDK 24.0.1, arm64 macOS (Apple M3 Pro) with {@code -prof gc}; 5x1s
 * warmup, 5x1s measurement, 3 forks. Each cell is ns/op with its 99.9% confidence interval, and
 * B/op on the line below. The {@code immutableBitSet_valueOfOneWord}, {@code
 * immutableBitSet_valueOfTwoWords} and {@code immutableBitSet_valueOfFourWords} rows have one cell
 * each. In the 64-bit {@code immutableBitSet_builder*} cells, individual iterations measured either
 * 49 to 52 ns or 88 to 103 ns, with the same B/op, which widens their intervals. The cause is not
 * known.
 *
 * <pre>
 * Benchmark                                64 bits         128 bits        256 bits
 * immutableBitSet_valueOfOneWord           1.096 +- 0.011
 *                                          24
 * immutableBitSet_valueOfTwoWords                          3.825 +- 0.030
 *                                                          80
 * immutableBitSet_valueOfFourWords                                         4.074 +- 0.065
 *                                                                          96
 * immutableBitSet_valueOfWordArray         1.181 +- 0.020  5.728 +- 0.036  6.230 +- 0.030
 *                                          24              80              96
 * immutableBitSet_valueOfWordsFromIndices  29.3 +- 0.4     65.1 +- 3.8     175 +- 2
 *                                          48              112             144
 * immutableBitSet_builder                  72.6 +- 24.9    162 +- 7        279 +- 20
 *                                          96              160             224
 * immutableBitSet_builderWithExpectedSize  80.1 +- 24.3    62.7 +- 1.7     104 +- 2
 *                                          96              136             168
 * javaUtilBitSet_valueOf                   2.784 +- 0.015  2.813 +- 0.022  3.018 +- 0.022
 *                                          48              56              72
 * </pre>
 */
@RunWith(JUnit4.class)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class ImmutableBitSetValueOfBenchmark {

  @State(Scope.Benchmark)
  public static class Sized {
    @Param({"64", "128", "256"})
    public int universe;

    int[] indices;
    long[] words;

    @Setup
    public void chooseBits() {
      indices = chooseIndices(universe);
      words = toWords(indices, universe);
    }
  }

  @State(Scope.Benchmark)
  public static class OneWord {
    long word0;

    @Setup
    public void chooseBits() {
      long[] words = toWords(chooseIndices(64), 64);
      word0 = words[0];
    }
  }

  @State(Scope.Benchmark)
  public static class TwoWords {
    long word0;
    long word1;

    @Setup
    public void chooseBits() {
      long[] words = toWords(chooseIndices(128), 128);
      word0 = words[0];
      word1 = words[1];
    }
  }

  @State(Scope.Benchmark)
  public static class FourWords {
    long word0;
    long word1;
    long word2;
    long word3;

    @Setup
    public void chooseBits() {
      long[] words = toWords(chooseIndices(256), 256);
      word0 = words[0];
      word1 = words[1];
      word2 = words[2];
      word3 = words[3];
    }
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_valueOfOneWord(OneWord state) {
    return ImmutableBitSet.valueOf(state.word0);
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_valueOfTwoWords(TwoWords state) {
    return ImmutableBitSet.valueOf(state.word0, state.word1);
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_valueOfFourWords(FourWords state) {
    return ImmutableBitSet.valueOf(state.word0, state.word1, state.word2, state.word3);
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_valueOfWordArray(Sized state) {
    return ImmutableBitSet.valueOf(state.words);
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_valueOfWordsFromIndices(Sized state) {
    return ImmutableBitSet.valueOf(toWords(state.indices, state.universe));
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_builder(Sized state) {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int index : state.indices) {
      builder.set(index);
    }
    return builder.build();
  }

  @Benchmark
  public ImmutableBitSet immutableBitSet_builderWithExpectedSize(Sized state) {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builderWithExpectedSize(state.universe);
    for (int index : state.indices) {
      builder.set(index);
    }
    return builder.build();
  }

  @Benchmark
  public BitSet javaUtilBitSet_valueOf(Sized state) {
    return BitSet.valueOf(state.words);
  }

  @Test public void immutableBitSet_valueOfOneWord_buildsExpectedBits() {
    OneWord state = new OneWord();
    state.chooseBits();
    assertThat(immutableBitSet_valueOfOneWord(state).stream())
        .containsExactlyElementsIn(expectedIndices(64))
        .inOrder();
  }

  @Test public void immutableBitSet_valueOfTwoWords_buildsExpectedBits() {
    TwoWords state = new TwoWords();
    state.chooseBits();
    assertThat(immutableBitSet_valueOfTwoWords(state).stream())
        .containsExactlyElementsIn(expectedIndices(128))
        .inOrder();
  }

  @Test public void immutableBitSet_valueOfFourWords_buildsExpectedBits() {
    FourWords state = new FourWords();
    state.chooseBits();
    assertThat(immutableBitSet_valueOfFourWords(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  @Test public void immutableBitSet_valueOfWordArray_buildsExpectedBits() {
    Sized state = sized(256);
    assertThat(immutableBitSet_valueOfWordArray(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  @Test public void immutableBitSet_valueOfWordsFromIndices_buildsExpectedBits() {
    Sized state = sized(256);
    assertThat(immutableBitSet_valueOfWordsFromIndices(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  @Test public void immutableBitSet_builder_buildsExpectedBits() {
    Sized state = sized(256);
    assertThat(immutableBitSet_builder(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  @Test public void immutableBitSet_builderWithExpectedSize_buildsExpectedBits() {
    Sized state = sized(256);
    assertThat(immutableBitSet_builderWithExpectedSize(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  @Test public void javaUtilBitSet_valueOf_buildsExpectedBits() {
    Sized state = sized(256);
    assertThat(javaUtilBitSet_valueOf(state).stream())
        .containsExactlyElementsIn(expectedIndices(256))
        .inOrder();
  }

  private static Sized sized(int universe) {
    Sized state = new Sized();
    state.universe = universe;
    state.chooseBits();
    return state;
  }

  private static List<Integer> expectedIndices(int universe) {
    return Arrays.stream(chooseIndices(universe)).boxed().collect(toList());
  }

  private static int[] chooseIndices(int universe) {
    Random random = new Random(42);
    return IntStream.range(0, universe)
        .filter(i -> random.nextBoolean() || i == universe - 1)
        .toArray();
  }

  private static long[] toWords(int[] indices, int universe) {
    long[] words = new long[(universe + 63) / 64];
    for (int index : indices) {
      words[index / 64] |= 1L << index;
    }
    return words;
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }
}

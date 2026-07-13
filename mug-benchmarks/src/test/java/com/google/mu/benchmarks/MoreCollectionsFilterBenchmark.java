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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.mu.collect.MoreCollections;

/**
  * Benchmark for {@link MoreCollections#filter(List, Predicate)} comparing against
 * standard Java stream filtering.
 *
 * <p>Benchmark results (JVM: JDK 24.0.1, Throughput in ops/sec):
 *
 * <pre>{@code
 *   Size | Match Rate | MoreCollections.filter | stream().toList() | Speedup
 *   -----+------------+------------------------+-------------------+--------
 *      0 |       0%   |         1,806,612,304  |       59,190,909  |  30.5x
 *      1 |       0%   |           566,995,008  |       54,823,907  |  10.3x
 *      1 |     100%   |           495,301,391  |       39,685,261  |  12.5x
 *      2 |       0%   |           505,644,734  |       53,630,247  |   9.4x
 *      2 |      50%   |           283,244,290  |       39,977,345  |   7.1x
 *      2 |     100%   |           442,565,514  |       39,739,454  |  11.1x
 *      3 |       0%   |           387,272,751  |       51,906,625  |   7.5x
 *      3 |      67%   |           109,216,394  |       39,212,813  |   2.8x
 *      3 |     100%   |           360,620,020  |       38,529,116  |   9.4x
 *      5 |      60%   |            90,403,444  |       37,834,798  |   2.4x
 *      5 |     100%   |           302,455,379  |       35,991,628  |   8.4x
 *     10 |      50%   |            67,698,128  |       32,944,167  |   2.1x
 *     10 |     100%   |           214,023,158  |       31,440,482  |   6.8x
 *     32 |      25%   |            32,202,952  |       21,422,676  |   1.5x
 *     32 |      50%   |            24,473,961  |       20,212,588  |   1.2x
 *     32 |     100%   |            95,754,817  |        7,102,417  |  13.5x
 *     64 |      25%   |            17,305,317  |       13,270,806  |   1.3x
 *     64 |      50%   |            10,408,078  |        5,498,968  |   1.9x
 *     64 |     100%   |            53,600,935  |        3,990,547  |  13.4x
 *   -----+------------+------------------------+-------------------+--------
 *     70 |      25%   |             9,052,802  |        8,118,966  |  1.11x
 *     70 |      50%   |             6,929,459  |        3,924,459  |  1.76x
 *     70 |     100%   |             4,994,682  |        3,949,838  |  1.26x
 *     80 |      25%   |             8,485,679  |        8,046,057  |  1.05x
 *     80 |      50%   |             6,346,297  |        3,569,342  |  1.77x
 *     80 |     100%   |             4,481,216  |        3,506,878  |  1.27x
 *    100 |      25%   |             6,868,235  |        7,036,688  |  0.98x
 *    100 |      50%   |             5,238,215  |        3,114,895  |  1.68x
 *    100 |     100%   |             3,449,274  |        2,909,303  |  1.18x
 * }</pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MoreCollectionsFilterBenchmark {

  @Param({"0", "1", "2", "3", "5", "10", "32", "64", "70", "80", "100"})
  public int size;

  private List<Integer> list;

  @Setup
  public void setupBenchmark() {
    list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(i);
    }
  }

  @Benchmark
  public List<Integer> filter_moreCollections_alwaysMatch() {
    return MoreCollections.filter(list, x -> true);
  }

  @Benchmark
  public List<Integer> filter_streamToList_alwaysMatch() {
    return list.stream().filter(x -> true).toList();
  }

  @Benchmark
  public List<Integer> filter_moreCollections_halfMatch() {
    return MoreCollections.filter(list, x -> x % 2 == 0);
  }

  @Benchmark
  public List<Integer> filter_streamToList_halfMatch() {
    return list.stream().filter(x -> x % 2 == 0).toList();
  }

  @Benchmark
  public List<Integer> filter_moreCollections_tenPercentMatch() {
    return MoreCollections.filter(list, x -> x % 10 == 0);
  }

  @Benchmark
  public List<Integer> filter_streamToList_tenPercentMatch() {
    return list.stream().filter(x -> x % 10 == 0).toList();
  }

  @Benchmark
  public List<Integer> filter_moreCollections_twentyFivePercentMatch() {
    return MoreCollections.filter(list, x -> x % 4 == 0);
  }

  @Benchmark
  public List<Integer> filter_streamToList_twentyFivePercentMatch() {
    return list.stream().filter(x -> x % 4 == 0).toList();
  }

  @Benchmark
  public List<Integer> filter_moreCollections_seventyFivePercentMatch() {
    return MoreCollections.filter(list, x -> x % 4 != 0);
  }

  @Benchmark
  public List<Integer> filter_streamToList_seventyFivePercentMatch() {
    return list.stream().filter(x -> x % 4 != 0).toList();
  }

  @Benchmark
  public List<Integer> filter_moreCollections_noneMatch() {
    return MoreCollections.filter(list, x -> false);
  }

  @Benchmark
  public List<Integer> filter_streamToList_noneMatch() {
    return list.stream().filter(x -> false).toList();
  }

  @Test public void testBenchmark() {
    list = List.of(1, 2, 3, 4);
    assertThat(MoreCollections.filter(list, x -> x % 2 == 0)).containsExactly(2, 4);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}

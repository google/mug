package com.google.mu.benchmarks;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.parse.Parser;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the cost of {@code anyOf()} when the parser is allocated, run once and discarded ({@code
 * initAndFirstParse_*}) versus reused ({@code reusedParser_*}).
 *
 * <p>Results for building the prefix trie eagerly on the 1st invocation (before) versus deferring
 * it to the 2nd invocation (after). JMH 1.37, JDK 24.0.1, arm64 macOS, {@code -prof gc}; 3x2s
 * warmup, 5x2s measurement; 2 forks for {@code initAndFirstParse_*}, 3 forks for {@code
 * reusedParser_*}. Errors are 99.9% confidence intervals.
 *
 * <pre>
 * Benchmark                              before             after
 * initAndFirstParse_cachedSubparsers     719 +- 10 ns/op    96 +- 5 ns/op
 *                                        5832 B/op          1104 B/op
 * initAndFirstParse_nonAlpha             536 +- 44 ns/op    94 +- 2 ns/op
 *                                        4512 B/op          1104 B/op
 * initAndFirstParse_fromScratch          1121 +- 45 ns/op   340 +- 9 ns/op
 *                                        6952 B/op          2104 B/op
 * reusedParser_prunable                  5.50 +- 0.14 ns/op 5.33 +- 0.07 ns/op
 * reusedParser_notPrunable               5.88 +- 0.08 ns/op 5.87 +- 0.07 ns/op
 * </pre>
 *
 * <p>{@code reusedParser_*} allocate nothing per op in either version. {@code fromScratch} also
 * rebuilds the four sub-parsers on every call, which is unaffected by the deferral.
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AnyOfInitBenchmark {
  private final Parser<String> foo = string("foo");
  private final Parser<String> bar = string("bar");
  private final Parser<String> baz = string("baz");
  private final Parser<String> nonDigits = consecutive("[^0-9]");
  private final Parser<String> nonAlpha = consecutive("[^a-z]");
  private final Parser<String> reusedPrunable = anyOf(foo, bar, baz, nonDigits);
  private final Parser<String> reusedNotPrunable = anyOf(nonDigits, nonAlpha);

  @Benchmark
  public String initAndFirstParse_cachedSubparsers() {
    return anyOf(foo, bar, baz, nonDigits).parse("foo");
  }

  @Benchmark
  public String initAndFirstParse_nonAlpha() {
    return anyOf(foo, bar, baz, nonAlpha).parse("foo");
  }

  @Benchmark
  public String initAndFirstParse_fromScratch() {
    return anyOf(string("foo"), string("bar"), string("baz"), consecutive("[^0-9]")).parse("foo");
  }

  @Benchmark
  public String reusedParser_prunable() {
    return reusedPrunable.parse("foo");
  }

  @Benchmark
  public String reusedParser_notPrunable() {
    return reusedNotPrunable.parse("foo");
  }

  @Test public void testBenchmark() {
    assertThat(initAndFirstParse_cachedSubparsers()).isEqualTo("foo");
    assertThat(initAndFirstParse_nonAlpha()).isEqualTo("foo");
    assertThat(initAndFirstParse_fromScratch()).isEqualTo("foo");
    assertThat(reusedParser_prunable()).isEqualTo("foo");
    assertThat(reusedParser_notPrunable()).isEqualTo("foo");
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}

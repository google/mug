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

  @Test public void testBenchmark() {
    assertThat(initAndFirstParse_cachedSubparsers()).isEqualTo("foo");
    assertThat(initAndFirstParse_nonAlpha()).isEqualTo("foo");
    assertThat(initAndFirstParse_fromScratch()).isEqualTo("foo");
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}

package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.labs.markdown.MarkdownLink;

@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MarkdownLinkBenchmark {
  private static final String INPUT =
      "This is a [link](http://example.com) and `[ignored](link)` and ```[ignored too](link)```.";
  private static final int SCALE = 1000;
  private static final String LARGE_INPUT =
      String.join(" ", java.util.Collections.nCopies(SCALE, INPUT));

  @Benchmark
  public long dotParse() {
    return MarkdownLink.scan(LARGE_INPUT).count();
  }

  @Test public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(SCALE);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}

package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.*;

@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class JsonShootoutBenchmark {

  @Param({
    "bar.json",
    "qux2.json",
    "bla25.json",
    "countries.geo.json",
    "ugh10k.json"
  })
  public String jsonFile = "bar.json";

  private String jsonString;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    jsonString = Resources.toString(
        Resources.getResource(jsonFile),
        StandardCharsets.UTF_8);
    
    // Warmup and verify correctness
    JsonValue dotResult = com.google.mu.benchmarks.parsers.dotparse.JsonParser.parse(jsonString);
    JsonValue catsResult = com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parse(jsonString);
    JsonValue fastResult = com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parse(jsonString);
    
    // Assert they all parsed to the same AST structure (optional but good for sanity)
    assertThat(dotResult).isEqualTo(catsResult);
    assertThat(dotResult).isEqualTo(fastResult);
  }

  @Benchmark
  public Object dotParse() {
    return com.google.mu.benchmarks.parsers.dotparse.JsonParser.parse(jsonString);
  }

  @Benchmark
  public Object catsParse() {
    return com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parse(jsonString);
  }

  @Benchmark
  public Object fastParse() {
    return com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parse(jsonString);
  }

  @Test
  public void testBenchmarkSetup() throws IOException {
    for (String file : new String[]{"bar.json", "qux2.json", "bla25.json", "countries.geo.json", "ugh10k.json"}) {
      jsonFile = file;
      setup();
    }
  }
}

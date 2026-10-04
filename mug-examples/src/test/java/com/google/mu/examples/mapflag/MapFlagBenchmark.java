package com.google.mu.examples.mapflag;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.joining;
import static scala.jdk.javaapi.CollectionConverters.asJava;

import com.google.common.collect.Maps;
import com.google.mu.examples.mapflag.antlr.AntlrMapFlagParser;
import com.google.mu.examples.mapflag.betterparse.BetterParseMapFlagParser;
import com.google.mu.examples.mapflag.catsparse.CatsParseMapFlagParser;
import com.google.mu.examples.mapflag.dotparse.DotParseMapFlagParser;
import com.google.mu.examples.mapflag.jjparse.JjparseMapFlagParser;
import com.google.mu.examples.mapflag.jparsec.JparsecMapFlagParser;
import com.google.mu.examples.mapflag.parsecj.ParsecjMapFlagParser;
import com.google.mu.examples.mapflag.petitparser.PetitParserMapFlagParser;
import com.google.mu.examples.mapflag.regex.RegexMapFlagParser;
import com.google.mu.examples.mapflag.taker.TakerMapFlagParser;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
import scala.collection.Seq;
import scala.util.Either;

/**
 * Compares the map-flag parsers in dot-parse/demo.md on the same inputs.
 *
 * <pre>{@code
 * mvn clean test-compile -pl mug-examples -Pshowdown
 * mvn exec:exec -pl mug-examples -Pshowdown -Dexec.executable=java -Dexec.classpathScope=test \
 *     -Dexec.args="-classpath %classpath org.openjdk.jmh.Main MapFlagBenchmark"
 * }</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MapFlagBenchmark {
  public enum Input {
    // The example flag in demo.md.
    SMALL("{k1 = \"v1\", k2 = 3, k3 = [10, 20.5]}"),
    MEDIUM(
        "{name = \"checkout-frontend\", replicas = 3, cpu-limit = 1.5, memory-mb = 2048,"
            + " timeout-ms = -1, zones = [\"us-east1-b\", \"us-east1-c\", \"us-west1-a\"],"
            + " weights = [0.6, 0.3, 0.1], greeting = \"say \\\"hi\\\"\", path = \"C:\\\\temp\","
            + " tags = [], }"),
    LARGE(generateEntries(100));

    private final String text;

    Input(String text) {
      this.text = text;
    }
  }

  @Param({"SMALL", "MEDIUM", "LARGE"})
  public Input input;

  private String flag;

  @Setup
  public void setUp() {
    flag = input.text;
    Map<String, Object> expected = dotParse();
    assertWithMessage("jjparse").that(jjparse()).isEqualTo(expected);
    assertWithMessage("jparsec").that(jparsec()).isEqualTo(expected);
    assertWithMessage("taker").that(taker()).isEqualTo(expected);
    assertWithMessage("parsecj").that(parsecj()).isEqualTo(expected);
    assertWithMessage("regex").that(regex()).isEqualTo(expected);
    assertWithMessage("antlr").that(antlr()).isEqualTo(expected);
    assertWithMessage("petitParser").that(petitParser()).isEqualTo(expected);
    assertWithMessage("catsParse").that(catsParseToJava()).isEqualTo(expected);
    assertWithMessage("betterParse").that(betterParse()).isEqualTo(expected);
  }

  @Benchmark
  public Map<String, Object> dotParse() {
    return DotParseMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> jjparse() {
    return JjparseMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> jparsec() {
    return JparsecMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> taker() {
    return TakerMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> parsecj() {
    return ParsecjMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> regex() {
    return RegexMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> antlr() {
    return AntlrMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> petitParser() {
    return PetitParserMapFlagParser.parse(flag);
  }

  @Benchmark
  public Either<?, ?> catsParse() {
    return CatsParseMapFlagParser.parse(flag);
  }

  @Benchmark
  public Map<String, Object> betterParse() {
    return BetterParseMapFlagParser.INSTANCE.parse(flag);
  }

  private Map<String, Object> catsParseToJava() {
    return CatsParseMapFlagParser.parse(flag)
        .fold(
            error -> {
              throw new IllegalArgumentException(error.toString());
            },
            map ->
                Maps.transformValues(asJava(map), v -> v instanceof Seq<?> seq ? asJava(seq) : v));
  }

  private static String generateEntries(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> switch (i % 4) {
          case 0 -> "int" + i + " = " + i;
          case 1 -> "decimal" + i + " = -" + i + ".5";
          case 2 -> "string" + i + " = \"say \\\"" + i + "\\\"\"";
          default -> "list" + i + " = [" + i + ", " + i + ".25, \"item " + i + "\"]";
        })
        .collect(joining(", ", "{", "}"));
  }
}

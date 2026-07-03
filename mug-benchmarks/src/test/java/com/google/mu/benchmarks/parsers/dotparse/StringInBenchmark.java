package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.fastparse.FastparseOneOfWrapper;
import com.google.mu.benchmarks.parsers.fastparse.FastparseStringInWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.*;
import scala.jdk.javaapi.CollectionConverters;

@RunWith(JUnit4.class)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class StringInBenchmark {

  @Param({"foo", "broad"})
  public String test = "foo";

  private List<String> inputs;
  private List<String> stringsToMatch;

  // dot-parse parsers
  private Parser<String> dotStringInS;
  private Parser<?> dotOneOf;

  // cats-parse parsers
  private cats.parse.Parser<String> catsStringInS;
  private cats.parse.Parser<scala.runtime.BoxedUnit> catsStringInV;
  private cats.parse.Parser<scala.runtime.BoxedUnit> catsOneOf;

  // fastparse parsers
  private FastparseStringInWrapper fastparseStringIn;
  private FastparseOneOfWrapper fastparseOneOf;

  @Setup(Level.Trial)
  public void setup() {
    if ("foo".equals(test)) {
      inputs = List.of("foofoo", "bar", "foobat", "foot", "foobar");
      stringsToMatch = List.of("foobar", "foofoo", "foobaz", "foo", "bar");
    } else if ("broad".equals(test)) {
      stringsToMatch = new ArrayList<>();
      for (char h = 'a'; h <= 'z'; h++) {
        for (char t = 'a'; t <= 'z'; t++) {
          stringsToMatch.add("" + h + h + h + t);
        }
      }
      inputs = stringsToMatch.stream()
          .filter(s -> Math.abs(s.hashCode()) % 10 == 0)
          .collect(Collectors.toList());
    }

    // Setup dot-parse
    dotStringInS = StringInParser.stringIn(stringsToMatch);
    dotOneOf = StringInParser.oneOf(stringsToMatch);

    // Setup cats-parse
    scala.collection.immutable.List<String> scalaStrings = 
        scala.collection.immutable.List$.MODULE$.<String>from(
            CollectionConverters.asScala(stringsToMatch));
    catsStringInS = cats.parse.Parser$.MODULE$.stringIn(scalaStrings);
    catsStringInV = catsStringInS.map(x -> scala.runtime.BoxedUnit.UNIT);

    List<cats.parse.Parser<scala.runtime.BoxedUnit>> catsParsers = stringsToMatch.stream()
        .map(s -> cats.parse.Parser$.MODULE$.string(s))
        .collect(Collectors.toList());
    catsOneOf = cats.parse.Parser$.MODULE$.<scala.runtime.BoxedUnit>oneOf(
        scala.collection.immutable.List$.MODULE$.<cats.parse.Parser<scala.runtime.BoxedUnit>>from(
            CollectionConverters.asScala(catsParsers)));

    // Setup fastparse
    fastparseStringIn = new FastparseStringInWrapper(test, scalaStrings);
    fastparseOneOf = new FastparseOneOfWrapper(scalaStrings);

    // Verify all parsers agree on all inputs
    for (String input : inputs) {
      boolean dotResult = dotStringInS.matches(input);
      boolean catsResult = catsStringInV.parseAll(input).isRight();
      boolean fastparseResult = fastparseStringIn.parse(input);

      if (dotResult != catsResult || dotResult != fastparseResult) {
        throw new AssertionError(
            String.format(
                "Mismatch for stringIn input '%s' under test '%s': dot-parse=%b, cats-parse=%b, fastparse=%b",
                input, test, dotResult, catsResult, fastparseResult));
      }

      boolean dotOneOfResult = dotOneOf.matches(input);
      boolean catsOneOfResult = catsOneOf.parseAll(input).isRight();
      boolean fastparseOneOfResult = fastparseOneOf.parse(input);

      if (dotOneOfResult != catsOneOfResult || dotOneOfResult != fastparseOneOfResult) {
        throw new AssertionError(
            String.format(
                "Mismatch for oneOf input '%s' under test '%s': dot-parse=%b, cats-parse=%b, fastparse=%b",
                input, test, dotOneOfResult, catsOneOfResult, fastparseOneOfResult));
      }
    }
  }

  // --- dot-parse Benchmarks ---

  @Benchmark
  public void dotStringInVParse() {
    for (String input : inputs) {
      dotStringInS.matches(input);
    }
  }

  @Benchmark
  public void dotStringInSParse() {
    for (String input : inputs) {
      dotStringInS.matches(input);
    }
  }

  @Benchmark
  public void dotOneOfParse() {
    for (String input : inputs) {
      dotOneOf.matches(input);
    }
  }

  // --- cats-parse Benchmarks ---

  @Benchmark
  public void catsStringInVParse() {
    for (String input : inputs) {
      catsStringInV.parseAll(input);
    }
  }

  @Benchmark
  public void catsStringInSParse() {
    for (String input : inputs) {
      catsStringInS.parseAll(input);
    }
  }

  @Benchmark
  public void catsOneOfParse() {
    for (String input : inputs) {
      catsOneOf.parseAll(input);
    }
  }

  // --- fastparse Benchmarks ---

  @Benchmark
  public void fastparseStringInParse() {
    for (String input : inputs) {
      fastparseStringIn.parse(input);
    }
  }

  @Benchmark
  public void fastparseOneOfParse() {
    for (String input : inputs) {
      fastparseOneOf.parse(input);
    }
  }

  // --- Baselines ---

  @Benchmark
  public void linearMatchIn() {
    for (String input : inputs) {
      boolean matched = false;
      for (String s : stringsToMatch) {
        if (input.startsWith(s)) {
          matched = true;
          break;
        }
      }
    }
  }

  @Test
  public void testBenchmarkSetup() {
    test = "foo";
    setup();
    assertThat(dotStringInS.parse("foobar")).isEqualTo("foobar");
    assertThat(dotStringInS.matches("foobar")).isTrue();
    assertThat(catsStringInS.parseAll("foobar").toOption().get()).isEqualTo("foobar");
    assertThat(fastparseStringIn.parse("foobar")).isTrue();
  }
}

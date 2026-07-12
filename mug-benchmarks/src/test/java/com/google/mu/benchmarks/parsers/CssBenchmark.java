package com.google.mu.benchmarks.parsers;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

import com.google.mu.benchmarks.parsers.dotparse.CssParser;
import com.google.mu.benchmarks.parsers.catsparse.CatsParseCssParser;
import com.google.mu.benchmarks.parsers.fastparse.FastparseCssParser;
import com.google.mu.benchmarks.parsers.antlr4.Antlr4CssParser;
import com.google.mu.benchmarks.parsers.javacc.HtmlUnitCssParser;
import com.google.mu.benchmarks.parsers.jparsec.JparsecCssParser;
import com.google.mu.benchmarks.parsers.betterparse.BetterParseCssParser;
import com.google.mu.benchmarks.parsers.parboiled.ParboiledCssParser;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class CssBenchmark {

  private static final String BOOTSTRAP;
  static {
    try {
      BOOTSTRAP = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/bootstrap.css"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final String SIMPLE = "h1 { color: red; }";

  private static final String MULTIPLE = 
      "h1 {\n" +
      "  color: red;\n" +
      "  font-size: 12px;\n" +
      "}";

  private static final String MEDIA_QUERY = 
      "@media screen and (max-width: 600px) {\n" +
      "  body {\n" +
      "    background-color: lightblue;\n" +
      "  }\n" +
      "}";

  private static final String NESTED_MEDIA = 
      "@media screen {\n" +
      "  @media (max-width: 600px) {\n" +
      "    body {\n" +
      "      color: red;\n" +
      "    }\n" +
      "  }\n" +
      "}";

  // =========================================================================
  // 1. dot-parse Benchmarks
  // =========================================================================
  @Benchmark public void dotParse_parseSimple(Blackhole bh) { bh.consume(CssParser.parse(SIMPLE)); }
  @Benchmark public void dotParse_parseMultiple(Blackhole bh) { bh.consume(CssParser.parse(MULTIPLE)); }
  @Benchmark public void dotParse_parseMediaQuery(Blackhole bh) { bh.consume(CssParser.parse(MEDIA_QUERY)); }
  @Benchmark public void dotParse_parseNestedMedia(Blackhole bh) { bh.consume(CssParser.parse(NESTED_MEDIA)); }
  @Benchmark public void dotParse_parseBootstrap(Blackhole bh) { bh.consume(CssParser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 2. cats-parse Benchmarks
  // =========================================================================
  @Benchmark public void catsParse_parseSimple(Blackhole bh) { bh.consume(CatsParseCssParser.parse(SIMPLE)); }
  @Benchmark public void catsParse_parseMultiple(Blackhole bh) { bh.consume(CatsParseCssParser.parse(MULTIPLE)); }
  @Benchmark public void catsParse_parseMediaQuery(Blackhole bh) { bh.consume(CatsParseCssParser.parse(MEDIA_QUERY)); }
  @Benchmark public void catsParse_parseNestedMedia(Blackhole bh) { bh.consume(CatsParseCssParser.parse(NESTED_MEDIA)); }
  @Benchmark public void catsParse_parseBootstrap(Blackhole bh) { bh.consume(CatsParseCssParser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 3. fastparse Benchmarks
  // =========================================================================
  @Benchmark public void fastparse_parseSimple(Blackhole bh) { bh.consume(FastparseCssParser.parse(SIMPLE)); }
  @Benchmark public void fastparse_parseMultiple(Blackhole bh) { bh.consume(FastparseCssParser.parse(MULTIPLE)); }
  @Benchmark public void fastparse_parseMediaQuery(Blackhole bh) { bh.consume(FastparseCssParser.parse(MEDIA_QUERY)); }
  @Benchmark public void fastparse_parseNestedMedia(Blackhole bh) { bh.consume(FastparseCssParser.parse(NESTED_MEDIA)); }
  @Benchmark public void fastparse_parseBootstrap(Blackhole bh) { bh.consume(FastparseCssParser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 4. antlr4 Benchmarks
  // =========================================================================
  @State(Scope.Thread)
  public static class Antlr4State {
    public final Antlr4CssParser parser = new Antlr4CssParser();
  }

  @Benchmark public void antlr4_parseSimple(Antlr4State state, Blackhole bh) { bh.consume(state.parser.parse(SIMPLE)); }
  @Benchmark public void antlr4_parseMultiple(Antlr4State state, Blackhole bh) { bh.consume(state.parser.parse(MULTIPLE)); }
  @Benchmark public void antlr4_parseMediaQuery(Antlr4State state, Blackhole bh) { bh.consume(state.parser.parse(MEDIA_QUERY)); }
  @Benchmark public void antlr4_parseNestedMedia(Antlr4State state, Blackhole bh) { bh.consume(state.parser.parse(NESTED_MEDIA)); }
  @Benchmark public void antlr4_parseBootstrap(Antlr4State state, Blackhole bh) { bh.consume(state.parser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 5. javacc (HtmlUnit) Benchmarks
  // =========================================================================
  @State(Scope.Thread)
  public static class HtmlUnitState {
    public final HtmlUnitCssParser parser = new HtmlUnitCssParser();
  }

  @Benchmark public void htmlUnit_parseSimple(HtmlUnitState state, Blackhole bh) { bh.consume(state.parser.parse(SIMPLE)); }
  @Benchmark public void htmlUnit_parseMultiple(HtmlUnitState state, Blackhole bh) { bh.consume(state.parser.parse(MULTIPLE)); }
  @Benchmark public void htmlUnit_parseMediaQuery(HtmlUnitState state, Blackhole bh) { bh.consume(state.parser.parse(MEDIA_QUERY)); }
  @Benchmark public void htmlUnit_parseNestedMedia(HtmlUnitState state, Blackhole bh) { bh.consume(state.parser.parse(NESTED_MEDIA)); }
  @Benchmark public void htmlUnit_parseBootstrap(HtmlUnitState state, Blackhole bh) { bh.consume(state.parser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 6. jparsec Benchmarks
  // =========================================================================
  @Benchmark public void jparsec_parseSimple(Blackhole bh) { bh.consume(JparsecCssParser.parse(SIMPLE)); }
  @Benchmark public void jparsec_parseMultiple(Blackhole bh) { bh.consume(JparsecCssParser.parse(MULTIPLE)); }
  @Benchmark public void jparsec_parseMediaQuery(Blackhole bh) { bh.consume(JparsecCssParser.parse(MEDIA_QUERY)); }
  @Benchmark public void jparsec_parseNestedMedia(Blackhole bh) { bh.consume(JparsecCssParser.parse(NESTED_MEDIA)); }
  @Benchmark public void jparsec_parseBootstrap(Blackhole bh) { bh.consume(JparsecCssParser.parse(BOOTSTRAP)); }

  // =========================================================================
  // 7. better-parse Benchmarks
  // =========================================================================
  @Benchmark public void betterParse_parseSimple(Blackhole bh) { bh.consume(BetterParseCssParser.INSTANCE.parse(SIMPLE)); }
  @Benchmark public void betterParse_parseMultiple(Blackhole bh) { bh.consume(BetterParseCssParser.INSTANCE.parse(MULTIPLE)); }
  @Benchmark public void betterParse_parseMediaQuery(Blackhole bh) { bh.consume(BetterParseCssParser.INSTANCE.parse(MEDIA_QUERY)); }
  @Benchmark public void betterParse_parseNestedMedia(Blackhole bh) { bh.consume(BetterParseCssParser.INSTANCE.parse(NESTED_MEDIA)); }
  @Benchmark public void betterParse_parseBootstrap(Blackhole bh) { bh.consume(BetterParseCssParser.INSTANCE.parse(BOOTSTRAP)); }

  // =========================================================================
  // 8. parboiled Benchmarks
  // =========================================================================
  @Benchmark public void parboiled_parseSimple(Blackhole bh) { bh.consume(ParboiledCssParser.parse(SIMPLE)); }
  @Benchmark public void parboiled_parseMultiple(Blackhole bh) { bh.consume(ParboiledCssParser.parse(MULTIPLE)); }
  @Benchmark public void parboiled_parseMediaQuery(Blackhole bh) { bh.consume(ParboiledCssParser.parse(MEDIA_QUERY)); }
  @Benchmark public void parboiled_parseNestedMedia(Blackhole bh) { bh.consume(ParboiledCssParser.parse(NESTED_MEDIA)); }
  @Benchmark public void parboiled_parseBootstrap(Blackhole bh) { bh.consume(ParboiledCssParser.parse(BOOTSTRAP)); }
}

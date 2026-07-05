package com.google.mu.benchmarks.parsers;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;
import com.google.mu.benchmarks.parsers.dotparse.DotParseStreamingParser;
import com.google.mu.benchmarks.parsers.gson.GsonStreamingParser;
import com.google.mu.benchmarks.parsers.javacc.JavaccStreamingParser;

// Import all framework showdown containers
import com.google.mu.benchmarks.parsers.taker.TakerShowdown;
import com.google.mu.benchmarks.parsers.dotparse.DotParseShowdown;
import com.google.mu.benchmarks.parsers.jparsec.JparsecShowdown;
import com.google.mu.benchmarks.parsers.parboiled.ParboiledShowdown;
import com.google.mu.benchmarks.parsers.parsecj.ParsecjShowdown;
import com.google.mu.benchmarks.parsers.jjparse.JjparseShowdown;
import com.google.mu.benchmarks.parsers.antlr4.Antlr4Showdown;
import com.google.mu.benchmarks.parsers.catsparse.CatsParseShowdown;
import com.google.mu.benchmarks.parsers.fastparse.FastparseShowdown;
import com.google.mu.benchmarks.parsers.fastparse.FastparseCalculatorShowdown;
import com.google.mu.benchmarks.parsers.scalaparser.ScalaParserShowdown;
import com.google.mu.benchmarks.parsers.parboiled2.Parboiled2Showdown;
import com.google.mu.benchmarks.parsers.petitparser.PetitParserShowdown;
import com.google.mu.benchmarks.parsers.betterparse.BetterParseShowdown;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ParserShowdownBenchmark {


  @State(Scope.Benchmark)
  public static class BenchmarkState {
    // 1. Taker Fixtures
    public final TakerShowdown.IpFixture takerIp = new TakerShowdown.IpFixture();
    public final TakerShowdown.StringFixture takerString = new TakerShowdown.StringFixture();
    public final TakerShowdown.KeywordsFixture takerKeywords = new TakerShowdown.KeywordsFixture();
    public final TakerShowdown.IgnoreCaseFixture takerIgnoreCase = new TakerShowdown.IgnoreCaseFixture();
    public final TakerShowdown.CalculatorFixture takerCalculator = new TakerShowdown.CalculatorFixture();
    public final TakerShowdown.NestedCommentFixture takerNestedComment = new TakerShowdown.NestedCommentFixture();
    public final TakerShowdown.UsPhoneFixture takerUsPhone = new TakerShowdown.UsPhoneFixture();
    public final TakerShowdown.UsPhoneListFixture takerUsPhoneList = new TakerShowdown.UsPhoneListFixture();

    // 2. dot-parse Fixtures
    public final DotParseShowdown.IpFixture dotParseIp = new DotParseShowdown.IpFixture();
    public final DotParseShowdown.StringFixture dotParseString = new DotParseShowdown.StringFixture();
    public final DotParseShowdown.KeywordsFixture dotParseKeywords = new DotParseShowdown.KeywordsFixture();
    public final DotParseShowdown.IgnoreCaseFixture dotParseIgnoreCase = new DotParseShowdown.IgnoreCaseFixture();
    public final DotParseShowdown.CalculatorFixture dotParseCalculator = new DotParseShowdown.CalculatorFixture();
    public final DotParseShowdown.NestedCommentFixture dotParseNestedComment = new DotParseShowdown.NestedCommentFixture();
    public final DotParseShowdown.UsPhoneFixture dotParseUsPhone = new DotParseShowdown.UsPhoneFixture();
    public final DotParseShowdown.UsPhoneListFixture dotParseUsPhoneList = new DotParseShowdown.UsPhoneListFixture();

    // 3. cats-parse Fixtures
    public final CatsParseShowdown.IpFixture catsParseIp = new CatsParseShowdown.IpFixture();
    public final CatsParseShowdown.StringFixture catsParseString = new CatsParseShowdown.StringFixture();
    public final CatsParseShowdown.KeywordsFixture catsParseKeywords = new CatsParseShowdown.KeywordsFixture();
    public final CatsParseShowdown.IgnoreCaseFixture catsParseIgnoreCase = new CatsParseShowdown.IgnoreCaseFixture();
    public final CatsParseShowdown.CalculatorFixture catsParseCalculator = new CatsParseShowdown.CalculatorFixture();
    public final CatsParseShowdown.NestedCommentFixture catsParseNestedComment = new CatsParseShowdown.NestedCommentFixture();
    public final CatsParseShowdown.UsPhoneFixture catsParseUsPhone = new CatsParseShowdown.UsPhoneFixture();
    public final CatsParseShowdown.UsPhoneListFixture catsParseUsPhoneList = new CatsParseShowdown.UsPhoneListFixture();

    // 4. fastparse Fixtures
    public final FastparseShowdown.IpFixture fastparseIp = new FastparseShowdown.IpFixture();
    public final FastparseShowdown.StringFixture fastparseString = new FastparseShowdown.StringFixture();
    public final FastparseShowdown.KeywordsFixture fastparseKeywords = new FastparseShowdown.KeywordsFixture();
    public final FastparseShowdown.IgnoreCaseFixture fastparseIgnoreCase = new FastparseShowdown.IgnoreCaseFixture();
    public final FastparseCalculatorShowdown.CalculatorFixture fastparseCalculator = new FastparseCalculatorShowdown.CalculatorFixture();
    public final FastparseShowdown.NestedCommentFixture fastparseNestedComment = new FastparseShowdown.NestedCommentFixture();
    public final FastparseShowdown.UsPhoneFixture fastparseUsPhone = new FastparseShowdown.UsPhoneFixture();
    public final FastparseShowdown.UsPhoneListFixture fastparseUsPhoneList = new FastparseShowdown.UsPhoneListFixture();

    // 5. jparsec Fixtures
    public final JparsecShowdown.IpFixture jparsecIp = new JparsecShowdown.IpFixture();
    public final JparsecShowdown.StringFixture jparsecString = new JparsecShowdown.StringFixture();
    public final JparsecShowdown.KeywordsFixture jparsecKeywords = new JparsecShowdown.KeywordsFixture();
    public final JparsecShowdown.IgnoreCaseFixture jparsecIgnoreCase = new JparsecShowdown.IgnoreCaseFixture();
    public final JparsecShowdown.CalculatorFixture jparsecCalculator = new JparsecShowdown.CalculatorFixture();
    public final JparsecShowdown.NestedCommentFixture jparsecNestedComment = new JparsecShowdown.NestedCommentFixture();
    public final JparsecShowdown.UsPhoneFixture jparsecUsPhone = new JparsecShowdown.UsPhoneFixture();
    public final JparsecShowdown.UsPhoneListFixture jparsecUsPhoneList = new JparsecShowdown.UsPhoneListFixture();

    // 6. parboiled Fixtures
    public final ParboiledShowdown.IpFixture parboiledIp = new ParboiledShowdown.IpFixture();
    public final ParboiledShowdown.StringFixture parboiledString = new ParboiledShowdown.StringFixture();
    public final ParboiledShowdown.KeywordsFixture parboiledKeywords = new ParboiledShowdown.KeywordsFixture();
    public final ParboiledShowdown.IgnoreCaseFixture parboiledIgnoreCase = new ParboiledShowdown.IgnoreCaseFixture();
    public final ParboiledShowdown.CalculatorFixture parboiledCalculator = new ParboiledShowdown.CalculatorFixture();
    public final ParboiledShowdown.NestedCommentFixture parboiledNestedComment = new ParboiledShowdown.NestedCommentFixture();
    public final ParboiledShowdown.UsPhoneFixture parboiledUsPhone = new ParboiledShowdown.UsPhoneFixture();
    public final ParboiledShowdown.UsPhoneListFixture parboiledUsPhoneList = new ParboiledShowdown.UsPhoneListFixture();

    // 7. parsecj Fixtures
    public final ParsecjShowdown.IpFixture parsecjIp = new ParsecjShowdown.IpFixture();
    public final ParsecjShowdown.StringFixture parsecjString = new ParsecjShowdown.StringFixture();
    public final ParsecjShowdown.KeywordsFixture parsecjKeywords = new ParsecjShowdown.KeywordsFixture();
    public final ParsecjShowdown.IgnoreCaseFixture parsecjIgnoreCase = new ParsecjShowdown.IgnoreCaseFixture();
    public final ParsecjShowdown.NestedCommentFixture parsecjNestedComment = new ParsecjShowdown.NestedCommentFixture();
    public final ParsecjShowdown.CalculatorFixture parsecjCalculator;
    public final ParsecjShowdown.UsPhoneFixture parsecjUsPhone = new ParsecjShowdown.UsPhoneFixture();
    public final ParsecjShowdown.UsPhoneListFixture parsecjUsPhoneList = new ParsecjShowdown.UsPhoneListFixture();

    // 8. jjparse Fixtures
    public final JjparseShowdown.IpFixture jjparseIp = new JjparseShowdown.IpFixture();
    public final JjparseShowdown.StringFixture jjparseString = new JjparseShowdown.StringFixture();
    public final JjparseShowdown.KeywordsFixture jjparseKeywords = new JjparseShowdown.KeywordsFixture();
    public final JjparseShowdown.IgnoreCaseFixture jjparseIgnoreCase = new JjparseShowdown.IgnoreCaseFixture();
    public final JjparseShowdown.CalculatorFixture jjparseCalculator = new JjparseShowdown.CalculatorFixture();
    public final JjparseShowdown.NestedCommentFixture jjparseNestedComment = new JjparseShowdown.NestedCommentFixture();
    public final JjparseShowdown.UsPhoneFixture jjparseUsPhone = new JjparseShowdown.UsPhoneFixture();
    public final JjparseShowdown.UsPhoneListFixture jjparseUsPhoneList = new JjparseShowdown.UsPhoneListFixture();

    // 9. antlr4 Fixtures
    public final Antlr4Showdown.IpFixture antlr4Ip = new Antlr4Showdown.IpFixture();
    public final Antlr4Showdown.StringFixture antlr4String = new Antlr4Showdown.StringFixture();
    public final Antlr4Showdown.KeywordsFixture antlr4Keywords = new Antlr4Showdown.KeywordsFixture();
    public final Antlr4Showdown.IgnoreCaseFixture antlr4IgnoreCase = new Antlr4Showdown.IgnoreCaseFixture();
    public final Antlr4Showdown.CalculatorFixture antlr4Calculator = new Antlr4Showdown.CalculatorFixture();
    public final Antlr4Showdown.NestedCommentFixture antlr4NestedComment = new Antlr4Showdown.NestedCommentFixture();
    public final Antlr4Showdown.UsPhoneFixture antlr4UsPhone = new Antlr4Showdown.UsPhoneFixture();
    public final Antlr4Showdown.UsPhoneListFixture antlr4UsPhoneList = new Antlr4Showdown.UsPhoneListFixture();

    // 10. scala-parser-combinators Fixtures
    public final ScalaParserShowdown.IpFixture scalaParserIp = new ScalaParserShowdown.IpFixture();
    public final ScalaParserShowdown.StringFixture scalaParserString = new ScalaParserShowdown.StringFixture();
    public final ScalaParserShowdown.KeywordsFixture scalaParserKeywords = new ScalaParserShowdown.KeywordsFixture();
    public final ScalaParserShowdown.IgnoreCaseFixture scalaParserIgnoreCase = new ScalaParserShowdown.IgnoreCaseFixture();
    public final ScalaParserShowdown.CalculatorFixture scalaParserCalculator = new ScalaParserShowdown.CalculatorFixture();
    public final ScalaParserShowdown.NestedCommentFixture scalaParserNestedComment = new ScalaParserShowdown.NestedCommentFixture();
    public final ScalaParserShowdown.UsPhoneFixture scalaParserUsPhone = new ScalaParserShowdown.UsPhoneFixture();
    public final ScalaParserShowdown.UsPhoneListFixture scalaParserUsPhoneList = new ScalaParserShowdown.UsPhoneListFixture();

    // 11. parboiled2 Fixtures
    public final Parboiled2Showdown.IpFixture parboiled2Ip = new Parboiled2Showdown.IpFixture();
    public final Parboiled2Showdown.StringFixture parboiled2String = new Parboiled2Showdown.StringFixture();
    public final Parboiled2Showdown.KeywordsFixture parboiled2Keywords = new Parboiled2Showdown.KeywordsFixture();
    public final Parboiled2Showdown.IgnoreCaseFixture parboiled2IgnoreCase = new Parboiled2Showdown.IgnoreCaseFixture();
    public final Parboiled2Showdown.CalculatorFixture parboiled2Calculator = new Parboiled2Showdown.CalculatorFixture();
    public final Parboiled2Showdown.NestedCommentFixture parboiled2NestedComment = new Parboiled2Showdown.NestedCommentFixture();
    public final Parboiled2Showdown.UsPhoneFixture parboiled2UsPhone = new Parboiled2Showdown.UsPhoneFixture();
    public final Parboiled2Showdown.UsPhoneListFixture parboiled2UsPhoneList = new Parboiled2Showdown.UsPhoneListFixture();

    // 12. petitparser Fixtures
    public final PetitParserShowdown.IpFixture petitparserIp = new PetitParserShowdown.IpFixture();
    public final PetitParserShowdown.StringFixture petitparserString = new PetitParserShowdown.StringFixture();
    public final PetitParserShowdown.KeywordsFixture petitparserKeywords = new PetitParserShowdown.KeywordsFixture();
    public final PetitParserShowdown.IgnoreCaseFixture petitparserIgnoreCase = new PetitParserShowdown.IgnoreCaseFixture();
    public final PetitParserShowdown.CalculatorFixture petitparserCalculator = new PetitParserShowdown.CalculatorFixture();
    public final PetitParserShowdown.NestedCommentFixture petitparserNestedComment = new PetitParserShowdown.NestedCommentFixture();
    public final PetitParserShowdown.UsPhoneFixture petitparserUsPhone = new PetitParserShowdown.UsPhoneFixture();
    public final PetitParserShowdown.UsPhoneListFixture petitparserUsPhoneList = new PetitParserShowdown.UsPhoneListFixture();

    // 13. better-parse Fixtures
    public final BetterParseShowdown.IpFixture betterParseIp = new BetterParseShowdown.IpFixture();
    public final BetterParseShowdown.StringFixture betterParseString = new BetterParseShowdown.StringFixture();
    public final BetterParseShowdown.KeywordsFixture betterParseKeywords = new BetterParseShowdown.KeywordsFixture();
    public final BetterParseShowdown.IgnoreCaseFixture betterParseIgnoreCase = new BetterParseShowdown.IgnoreCaseFixture();
    public final BetterParseShowdown.CalculatorFixture betterParseCalculator = new BetterParseShowdown.CalculatorFixture();
    public final BetterParseShowdown.NestedCommentFixture betterParseNestedComment = new BetterParseShowdown.NestedCommentFixture();
    public final BetterParseShowdown.UsPhoneFixture betterParseUsPhone = new BetterParseShowdown.UsPhoneFixture();
    public final BetterParseShowdown.UsPhoneListFixture betterParseUsPhoneList = new BetterParseShowdown.UsPhoneListFixture();

    public final String jsonString;
    public final String jsonWithCommentsString;

    public BenchmarkState() {
      try {
        this.parsecjCalculator = new ParsecjShowdown.CalculatorFixture();
        this.jsonString = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/large_benchmark.json"));
        this.jsonWithCommentsString = java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/large_benchmark_with_comments.json"));
        
        // Rigorous setup-time verification for all 9 JSON parsers
        verifyJson(com.google.mu.benchmarks.parsers.dotparse.JsonParser.parse(jsonString), "dot-parse");
        verifyJson(com.google.mu.benchmarks.parsers.dotparse.JsonParser.parseWithComments(jsonWithCommentsString), "dot-parse-comments");
        verifyJson(com.google.mu.benchmarks.parsers.petitparser.PetitParserJsonParser.parseWithComments(jsonWithCommentsString), "petitparser-comments");
        verifyJson(com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parseWithComments(jsonWithCommentsString), "cats-parse-comments");
        verifyJson(com.google.mu.benchmarks.parsers.taker.TakerJsonParser.parseWithComments(jsonWithCommentsString), "taker-comments");
        verifyJson(com.google.mu.benchmarks.parsers.parsecj.ParsecjJsonParser.parseWithComments(jsonWithCommentsString), "parsecj-comments");
        verifyJson(new com.google.mu.benchmarks.parsers.antlr4.Antlr4JsonParser().parse(jsonWithCommentsString), "antlr4-comments");
        verifyJson(com.google.mu.benchmarks.parsers.betterparse.BetterParseJsonWithCommentsParser.INSTANCE.parse(jsonWithCommentsString), "better-parse-comments");
        verifyJson(com.google.mu.benchmarks.parsers.jparsec.JparsecJsonParser.parseWithComments(jsonWithCommentsString), "jparsec-comments");
        verifyJson(com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parseWithComments(jsonWithCommentsString), "fastparse-comments");
        var unusedGson = com.google.gson.JsonParser.parseString(jsonWithCommentsString);

        verifyJson(com.google.mu.benchmarks.parsers.jparsec.JparsecJsonParser.parse(jsonString), "jparsec");
        verifyJson(com.google.mu.benchmarks.parsers.parsecj.ParsecjJsonParser.parse(jsonString), "parsecj");
        verifyJson(com.google.mu.benchmarks.parsers.petitparser.PetitParserJsonParser.parse(jsonString), "petitparser");
        verifyJson(com.google.mu.benchmarks.parsers.taker.TakerJsonParser.parse(jsonString), "taker");
        verifyJson(com.google.mu.benchmarks.parsers.betterparse.BetterParseJsonParser.INSTANCE.parse(jsonString), "better-parse");
        verifyJson(com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parse(jsonString), "fastparse");
        verifyJson(com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parse(jsonString), "cats-parse");
        verifyJson(new com.google.mu.benchmarks.parsers.antlr4.Antlr4JsonParser().parse(jsonString), "antlr4");
        verifyJson(com.google.mu.benchmarks.parsers.javacc.JavaccJsonParser.parse(jsonString), "javacc");
        verifyJson(com.google.mu.benchmarks.parsers.javacc.JavaccJsonParser.parse(jsonWithCommentsString), "javacc-comments");
        var unusedTomcat = new com.google.mu.benchmarks.parsers.javacc.TomcatJsonParser(jsonString).parse();
        var unusedTomcatComments = new com.google.mu.benchmarks.parsers.javacc.TomcatJsonParser(jsonWithCommentsString).parse();
        var unusedRobertFischer = new com.google.mu.benchmarks.parsers.javacc.RobertFischerJsonParser(jsonString).parse();
        var unusedRobertFischerComments = new com.google.mu.benchmarks.parsers.javacc.RobertFischerJsonParser(jsonWithCommentsString).parse();
        verifyJson(com.google.mu.benchmarks.parsers.parboiled.ParboiledJsonParser.parse(jsonString), "parboiled");
        verifyJson(com.google.mu.benchmarks.parsers.parboiled.ParboiledJsonParser.parseWithComments(jsonWithCommentsString), "parboiled-comments");
        verifyJson(com.google.mu.benchmarks.parsers.autumn.AutumnJsonParser.parse(jsonString), "autumn");
        verifyJson(com.google.mu.benchmarks.parsers.autumn.AutumnJsonParser.parseWithComments(jsonWithCommentsString), "autumn-comments");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void verifyJson(com.google.mu.benchmarks.parsers.dotparse.JsonValue value, String parserName) {
      if (!(value instanceof com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonObject)) {
        throw new AssertionError(parserName + " parsed value is not a JsonObject!");
      }
      com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonObject obj = 
          (com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonObject) value;
      if (obj.members().size() != 12) {
        throw new AssertionError(
            parserName + " parsed object has size " + obj.members().size() + ", expected 12!");
      }
    }

  }

  // =========================================================================
  // 1. IP Address Benchmarks
  // =========================================================================
  @Benchmark public void taker_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerIp.run()); }
  @Benchmark public void dotParse_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseIp.run()); }
  @Benchmark public void catsParse_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseIp.run()); }
  @Benchmark public void fastparse_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseIp.run()); }
  @Benchmark public void jparsec_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecIp.run()); }
  @Benchmark public void parboiled_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledIp.run()); }
  @Benchmark public void parsecj_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjIp.run()); }
  @Benchmark public void jjparse_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseIp.run()); }
  @Benchmark public void antlr4_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4Ip.run()); }
  @Benchmark public void scalaParser_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserIp.run()); }
  @Benchmark public void parboiled2_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2Ip.run()); }
  @Benchmark public void petitparser_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserIp.run()); }
  @Benchmark public void betterParse_simpleIpPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseIp.run()); }

  // =========================================================================
  // 2. Quoted String Benchmarks
  // =========================================================================
  @Benchmark public void taker_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void taker_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void dotParse_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void dotParse_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void catsParse_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void catsParse_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void fastparse_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void fastparse_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void jparsec_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void jparsec_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void parboiled_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void parboiled_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void parsecj_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void parsecj_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void jjparse_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void jjparse_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void antlr4_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4String.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void antlr4_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4String.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void scalaParser_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void scalaParser_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserString.run(BenchmarkInputs.STRING_ESCAPED)); }

  @Benchmark public void parboiled2_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2String.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void parboiled2_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2String.run(BenchmarkInputs.STRING_ESCAPED)); }
  @Benchmark public void petitparser_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void petitparser_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserString.run(BenchmarkInputs.STRING_ESCAPED)); }
  @Benchmark public void betterParse_simpleStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseString.run(BenchmarkInputs.STRING_SIMPLE)); }
  @Benchmark public void betterParse_escapedStringPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseString.run(BenchmarkInputs.STRING_ESCAPED)); }

  // =========================================================================
  // 3. Keywords Benchmarks (Case-Sensitive Stream)
  // =========================================================================
  @Benchmark public void taker_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void dotParse_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void catsParse_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void fastparse_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void jparsec_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void parboiled_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void parsecj_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void jjparse_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void antlr4_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4Keywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void scalaParser_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  // @Benchmark public void parboiled2_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2Keywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  @Benchmark public void petitparser_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }
  // @Benchmark public void betterParse_simpleKeywordsPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseKeywords.run(BenchmarkInputs.KEYWORDS_LIST_CS)); }

  // =========================================================================
  // 4. Keywords Benchmarks (Case-Insensitive Stream)
  // =========================================================================
  @Benchmark public void taker_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void dotParse_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void catsParse_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void fastparse_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void jparsec_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void parboiled_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void parsecj_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void jjparse_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void antlr4_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4IgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void scalaParser_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  // @Benchmark public void parboiled2_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2IgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  @Benchmark public void petitparser_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }
  // @Benchmark public void betterParse_simpleIgnoreCasePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseIgnoreCase.run(BenchmarkInputs.KEYWORDS_LIST_CI)); }

  // =========================================================================
  // 5. Calculator Benchmarks
  // =========================================================================
  @Benchmark public void taker_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerCalculator.run()); }
  @Benchmark public void dotParse_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseCalculator.run()); }
  @Benchmark public void catsParse_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseCalculator.run()); }
  @Benchmark public void fastparse_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseCalculator.run()); }
  @Benchmark public void jparsec_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecCalculator.run()); }
  @Benchmark public void parboiled_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledCalculator.run()); }
  @Benchmark public void parsecj_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjCalculator.run()); }
  @Benchmark public void jjparse_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseCalculator.run()); }
  @Benchmark public void antlr4_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4Calculator.run()); }
  @Benchmark public void scalaParser_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserCalculator.run()); }
  @Benchmark public void parboiled2_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2Calculator.run()); }
  @Benchmark public void petitparser_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserCalculator.run()); }
  @Benchmark public void betterParse_calculatorPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseCalculator.run()); }

  // =========================================================================
  // 6. Nested Comment Benchmarks
  // =========================================================================
  @Benchmark public void taker_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerNestedComment.run()); }
  @Benchmark public void dotParse_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseNestedComment.run()); }
  @Benchmark public void catsParse_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseNestedComment.run()); }
  @Benchmark public void fastparse_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseNestedComment.run()); }
  @Benchmark public void jparsec_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecNestedComment.run()); }
  @Benchmark public void parboiled_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledNestedComment.run()); }
  @Benchmark public void parsecj_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjNestedComment.run()); }
  @Benchmark public void jjparse_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseNestedComment.run()); }
  @Benchmark public void antlr4_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4NestedComment.run()); }
  @Benchmark public void scalaParser_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserNestedComment.run()); }
  @Benchmark public void parboiled2_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2NestedComment.run()); }
  @Benchmark public void petitparser_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserNestedComment.run()); }
  @Benchmark public void betterParse_nestedCommentPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseNestedComment.run(BenchmarkInputs.NESTED_COMMENT)); }

  // =========================================================================
  // 7. JSON Benchmarks
  // =========================================================================
  @Benchmark public void dotParse_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.dotparse.JsonParser.parse(s.jsonString)); }
  @Benchmark public void jparsec_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.jparsec.JparsecJsonParser.parse(s.jsonString)); }
  @Benchmark public void parsecj_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.parsecj.ParsecjJsonParser.parse(s.jsonString)); }
  @Benchmark public void petitparser_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.petitparser.PetitParserJsonParser.parse(s.jsonString)); }
  @Benchmark public void taker_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.taker.TakerJsonParser.parse(s.jsonString)); }
  @Benchmark public void betterParse_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.betterparse.BetterParseJsonParser.INSTANCE.parse(s.jsonString)); }
  @Benchmark public void fastparse_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parse(s.jsonString)); }
  @Benchmark public void parboiled_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.parboiled.ParboiledJsonParser.parse(s.jsonString)); }
  @Benchmark public void autumn_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.autumn.AutumnJsonParser.parse(s.jsonString)); }
  @State(Scope.Thread)
  public static class Antlr4JsonState {
    public final com.google.mu.benchmarks.parsers.antlr4.Antlr4JsonParser parser = 
        new com.google.mu.benchmarks.parsers.antlr4.Antlr4JsonParser();
  }

  @Benchmark public void catsParse_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parse(s.jsonString)); }
  @Benchmark public void antlr4_jsonPerformance(BenchmarkState s, Antlr4JsonState state, Blackhole bh) { bh.consume(state.parser.parse(s.jsonString)); }
  @Benchmark public void javacc_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.mu.benchmarks.parsers.javacc.JavaccJsonParser.parse(s.jsonString)); }
  @Benchmark public void tomcatJavacc_jsonPerformance(BenchmarkState s, Blackhole bh) throws Exception {
    bh.consume(new com.google.mu.benchmarks.parsers.javacc.TomcatJsonParser(s.jsonString).parse());
  }
  @Benchmark public void robertFischerJavacc_jsonPerformance(BenchmarkState s, Blackhole bh) throws Exception {
    bh.consume(new com.google.mu.benchmarks.parsers.javacc.RobertFischerJsonParser(s.jsonString).parse());
  }
  @Benchmark public void gson_jsonPerformance(BenchmarkState s, Blackhole bh) { bh.consume(com.google.gson.JsonParser.parseString(s.jsonString)); }


  // =========================================================================
  // 8. JSON with Comments Benchmarks
  // =========================================================================
  @Benchmark public void dotParse_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.dotparse.JsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void petitparser_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.petitparser.PetitParserJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void catsParse_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.catsparse.CatsParseJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void taker_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.taker.TakerJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void parsecj_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.parsecj.ParsecjJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void antlr4_jsonWithCommentsPerformance(BenchmarkState s, Antlr4JsonState state, Blackhole bh) {
    bh.consume(state.parser.parse(s.jsonWithCommentsString));
  }
  @Benchmark public void tomcatJavacc_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) throws Exception {
    bh.consume(new com.google.mu.benchmarks.parsers.javacc.TomcatJsonParser(s.jsonWithCommentsString).parse());
  }
  @Benchmark public void robertFischerJavacc_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) throws Exception {
    bh.consume(new com.google.mu.benchmarks.parsers.javacc.RobertFischerJsonParser(s.jsonWithCommentsString).parse());
  }
  @Benchmark public void betterParse_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.betterparse.BetterParseJsonWithCommentsParser.INSTANCE.parse(s.jsonWithCommentsString));
  }
  @Benchmark public void jparsec_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.jparsec.JparsecJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void fastparse_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.fastparse.FastparseJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void gson_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.gson.JsonParser.parseString(s.jsonWithCommentsString));
  }

  @Benchmark public void javacc_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.javacc.JavaccJsonParser.parse(s.jsonWithCommentsString));
  }
  @Benchmark public void parboiled_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.parboiled.ParboiledJsonParser.parseWithComments(s.jsonWithCommentsString));
  }
  @Benchmark public void autumn_jsonWithCommentsPerformance(BenchmarkState s, Blackhole bh) {
    bh.consume(com.google.mu.benchmarks.parsers.autumn.AutumnJsonParser.parseWithComments(s.jsonWithCommentsString));
  }

  // =========================================================================
  // 9. JSON Streaming Benchmarks (Incremental Parsing from File)
  // =========================================================================

  @State(Scope.Benchmark)
  public static class StreamingState {
    Path filePath;
    StreamingJsonParser dotParseParser;

    StreamingJsonParser gsonParser;
    StreamingJsonParser javaccParser;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
      var resource = StreamingState.class.getResource("/large_benchmark.jsonl");
      if (resource == null) {
        throw new IllegalStateException("large_benchmark.jsonl not found in classpath resources");
      }
      this.filePath = Paths.get(resource.toURI());
      this.dotParseParser = new DotParseStreamingParser();

      this.gsonParser = new GsonStreamingParser();
      this.javaccParser = new JavaccStreamingParser();
    }
  }

  @Benchmark
  public void dotParse_streamingPerformance(StreamingState s, Blackhole bh) throws Exception {
    try (Reader reader = new FileReader(s.filePath.toFile(), StandardCharsets.UTF_8);
         Stream<JsonValue> stream = s.dotParseParser.parse(reader)) {
      stream.forEach(bh::consume);
    }
  }



  @Benchmark
  public void gson_streamingPerformance(StreamingState s, Blackhole bh) throws Exception {
    try (BufferedReader reader = Files.newBufferedReader(s.filePath, StandardCharsets.UTF_8);
         Stream<JsonValue> stream = s.gsonParser.parse(reader)) {
      stream.forEach(bh::consume);
    }
  }

  @Benchmark
  public void javacc_streamingPerformance(StreamingState s, Blackhole bh) throws Exception {
    try (Reader reader = new FileReader(s.filePath.toFile(), StandardCharsets.UTF_8);
         Stream<JsonValue> stream = s.javaccParser.parse(reader)) {
      stream.forEach(bh::consume);
    }
  }

  // =========================================================================
  // 10. US Phone Number Benchmarks (Single Phone Number)
  // =========================================================================
  @Benchmark public void taker_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void dotParse_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void catsParse_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void fastparse_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void jparsec_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void parboiled_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void parsecj_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void jjparse_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void antlr4_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4UsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void scalaParser_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void parboiled2_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2UsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void petitparser_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserUsPhone.run(BenchmarkInputs.US_PHONE)); }
  @Benchmark public void betterParse_simpleUsPhonePerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseUsPhone.run(BenchmarkInputs.US_PHONE)); }

  // =========================================================================
  // 11. US Phone Number Benchmarks (List of 1000 Phone Numbers)
  // =========================================================================
  @Benchmark public void taker_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.takerUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void dotParse_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.dotParseUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void catsParse_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.catsParseUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void fastparse_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.fastparseUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void jparsec_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jparsecUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void parboiled_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiledUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void parsecj_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parsecjUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void jjparse_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.jjparseUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void antlr4_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.antlr4UsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void scalaParser_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.scalaParserUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void parboiled2_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.parboiled2UsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void petitparser_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.petitparserUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
  @Benchmark public void betterParse_usPhoneListPerformance(BenchmarkState s, Blackhole bh) { bh.consume(s.betterParseUsPhoneList.run(BenchmarkInputs.US_PHONE_LIST)); }
}

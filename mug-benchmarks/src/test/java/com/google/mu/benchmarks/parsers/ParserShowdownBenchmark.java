package com.google.mu.benchmarks.parsers;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

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

    // 2. dot-parse Fixtures
    public final DotParseShowdown.IpFixture dotParseIp = new DotParseShowdown.IpFixture();
    public final DotParseShowdown.StringFixture dotParseString = new DotParseShowdown.StringFixture();
    public final DotParseShowdown.KeywordsFixture dotParseKeywords = new DotParseShowdown.KeywordsFixture();
    public final DotParseShowdown.IgnoreCaseFixture dotParseIgnoreCase = new DotParseShowdown.IgnoreCaseFixture();
    public final DotParseShowdown.CalculatorFixture dotParseCalculator = new DotParseShowdown.CalculatorFixture();

    // 3. cats-parse Fixtures
    public final CatsParseShowdown.IpFixture catsParseIp = new CatsParseShowdown.IpFixture();
    public final CatsParseShowdown.StringFixture catsParseString = new CatsParseShowdown.StringFixture();
    public final CatsParseShowdown.KeywordsFixture catsParseKeywords = new CatsParseShowdown.KeywordsFixture();
    public final CatsParseShowdown.IgnoreCaseFixture catsParseIgnoreCase = new CatsParseShowdown.IgnoreCaseFixture();
    public final CatsParseShowdown.CalculatorFixture catsParseCalculator = new CatsParseShowdown.CalculatorFixture();

    // 4. fastparse Fixtures
    public final FastparseShowdown.IpFixture fastparseIp = new FastparseShowdown.IpFixture();
    public final FastparseShowdown.StringFixture fastparseString = new FastparseShowdown.StringFixture();
    public final FastparseShowdown.KeywordsFixture fastparseKeywords = new FastparseShowdown.KeywordsFixture();
    public final FastparseShowdown.IgnoreCaseFixture fastparseIgnoreCase = new FastparseShowdown.IgnoreCaseFixture();
    public final FastparseCalculatorShowdown.CalculatorFixture fastparseCalculator = new FastparseCalculatorShowdown.CalculatorFixture();

    // 5. jparsec Fixtures
    public final JparsecShowdown.IpFixture jparsecIp = new JparsecShowdown.IpFixture();
    public final JparsecShowdown.StringFixture jparsecString = new JparsecShowdown.StringFixture();
    public final JparsecShowdown.KeywordsFixture jparsecKeywords = new JparsecShowdown.KeywordsFixture();
    public final JparsecShowdown.IgnoreCaseFixture jparsecIgnoreCase = new JparsecShowdown.IgnoreCaseFixture();
    public final JparsecShowdown.CalculatorFixture jparsecCalculator = new JparsecShowdown.CalculatorFixture();

    // 6. parboiled Fixtures
    public final ParboiledShowdown.IpFixture parboiledIp = new ParboiledShowdown.IpFixture();
    public final ParboiledShowdown.StringFixture parboiledString = new ParboiledShowdown.StringFixture();
    public final ParboiledShowdown.KeywordsFixture parboiledKeywords = new ParboiledShowdown.KeywordsFixture();
    public final ParboiledShowdown.IgnoreCaseFixture parboiledIgnoreCase = new ParboiledShowdown.IgnoreCaseFixture();
    public final ParboiledShowdown.CalculatorFixture parboiledCalculator = new ParboiledShowdown.CalculatorFixture();

    // 7. parsecj Fixtures
    public final ParsecjShowdown.IpFixture parsecjIp = new ParsecjShowdown.IpFixture();
    public final ParsecjShowdown.StringFixture parsecjString = new ParsecjShowdown.StringFixture();
    public final ParsecjShowdown.KeywordsFixture parsecjKeywords = new ParsecjShowdown.KeywordsFixture();
    public final ParsecjShowdown.IgnoreCaseFixture parsecjIgnoreCase = new ParsecjShowdown.IgnoreCaseFixture();
    public final ParsecjShowdown.CalculatorFixture parsecjCalculator;

    // 8. jjparse Fixtures
    public final JjparseShowdown.IpFixture jjparseIp = new JjparseShowdown.IpFixture();
    public final JjparseShowdown.StringFixture jjparseString = new JjparseShowdown.StringFixture();
    public final JjparseShowdown.KeywordsFixture jjparseKeywords = new JjparseShowdown.KeywordsFixture();
    public final JjparseShowdown.IgnoreCaseFixture jjparseIgnoreCase = new JjparseShowdown.IgnoreCaseFixture();
    public final JjparseShowdown.CalculatorFixture jjparseCalculator = new JjparseShowdown.CalculatorFixture();

    // 9. antlr4 Fixtures
    public final Antlr4Showdown.IpFixture antlr4Ip = new Antlr4Showdown.IpFixture();
    public final Antlr4Showdown.StringFixture antlr4String = new Antlr4Showdown.StringFixture();
    public final Antlr4Showdown.KeywordsFixture antlr4Keywords = new Antlr4Showdown.KeywordsFixture();
    public final Antlr4Showdown.IgnoreCaseFixture antlr4IgnoreCase = new Antlr4Showdown.IgnoreCaseFixture();
    public final Antlr4Showdown.CalculatorFixture antlr4Calculator = new Antlr4Showdown.CalculatorFixture();

    public BenchmarkState() {
      try {
        this.parsecjCalculator = new ParsecjShowdown.CalculatorFixture();
      } catch (Exception e) {
        throw new RuntimeException(e);
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

  // =========================================================================
  // 3. Keywords Benchmarks
  // =========================================================================
  @Benchmark
  public void taker_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void taker_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void taker_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void taker_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void dotParse_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void dotParse_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void dotParse_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void dotParse_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void catsParse_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void catsParse_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void catsParse_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void catsParse_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void fastparse_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void fastparse_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void fastparse_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void fastparse_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void jparsec_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void jparsec_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void jparsec_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void jparsec_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void parboiled_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void parboiled_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void parboiled_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void parboiled_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void parsecj_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void parsecj_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void parsecj_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void parsecj_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void jjparse_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseKeywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void jjparse_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseKeywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void jjparse_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseKeywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void jjparse_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseKeywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  @Benchmark
  public void antlr4_simpleKeywords1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4Keywords.run(BenchmarkInputs.KEYWORDS.get(0)));
  }
  @Benchmark
  public void antlr4_simpleKeywords4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4Keywords.run(BenchmarkInputs.KEYWORDS.get(3)));
  }
  @Benchmark
  public void antlr4_simpleKeywords8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4Keywords.run(BenchmarkInputs.KEYWORDS.get(7)));
  }
  @Benchmark
  public void antlr4_simpleKeywords12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4Keywords.run(BenchmarkInputs.KEYWORDS.get(11)));
  }

  // =========================================================================
  // 4. Case-Insensitive Keywords Benchmarks
  // =========================================================================
  @Benchmark
  public void taker_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void taker_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void taker_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void taker_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.takerIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void dotParse_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void dotParse_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void dotParse_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void dotParse_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.dotParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void catsParse_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void catsParse_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void catsParse_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void catsParse_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.catsParseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void fastparse_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void fastparse_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void fastparse_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void fastparse_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.fastparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void jparsec_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void jparsec_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void jparsec_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void jparsec_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jparsecIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void parboiled_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void parboiled_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void parboiled_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void parboiled_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parboiledIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void parsecj_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void parsecj_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void parsecj_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void parsecj_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.parsecjIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void jjparse_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void jjparse_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void jjparse_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void jjparse_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.jjparseIgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

  @Benchmark
  public void antlr4_simpleIgnoreCase1st(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4IgnoreCase.run(BenchmarkInputs.KEYWORDS.get(0).toUpperCase()));
  }
  @Benchmark
  public void antlr4_simpleIgnoreCase4th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4IgnoreCase.run(BenchmarkInputs.KEYWORDS.get(3).toUpperCase()));
  }
  @Benchmark
  public void antlr4_simpleIgnoreCase8th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4IgnoreCase.run(BenchmarkInputs.KEYWORDS.get(7).toUpperCase()));
  }
  @Benchmark
  public void antlr4_simpleIgnoreCase12th(BenchmarkState s, Blackhole bh) {
    bh.consume(s.antlr4IgnoreCase.run(BenchmarkInputs.KEYWORDS.get(11).toUpperCase()));
  }

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
}

package com.google.mu.benchmarks.parsers.javatype;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

import com.google.mu.benchmarks.parsers.dotparse.JavaTypeParser;
import com.google.mu.benchmarks.parsers.jparsec.JparsecJavaTypeParser;
import com.google.mu.benchmarks.parsers.parsecj.ParsecjJavaTypeParser;
import com.google.mu.benchmarks.parsers.taker.TakerJavaTypeParser;
import com.google.mu.benchmarks.parsers.fastparse.FastparseJavaTypeParser;
import com.google.mu.benchmarks.parsers.antlr4.Antlr4JavaTypeParser;
import com.google.mu.benchmarks.parsers.petitparser.PetitParserJavaTypeParser;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class JavaTypeBenchmark {

  private static final String SIMPLE = "String";
  
  private static final String FULLY_QUALIFIED = "java.lang.String";
  
  private static final String NESTED_GENERICS = 
      "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>";
      
  private static final String ANNOTATED_ARRAY = 
      "java.util.@com.google.NonNull List<java.lang.@com.google.Nullable String>[]";
      
  private static final String COMPLEX = 
      "@com.google.MyAnnotation(classes={java.lang.String.class, int[].class}, value=@NestedAnno(123)) List<Integer>";

  // =========================================================================
  // 1. dot-parse Benchmarks
  // =========================================================================
  @Benchmark public void dotParse_parseSimple(Blackhole bh) { bh.consume(JavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void dotParse_parseFullyQualified(Blackhole bh) { bh.consume(JavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void dotParse_parseNestedGenerics(Blackhole bh) { bh.consume(JavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void dotParse_parseAnnotatedArray(Blackhole bh) { bh.consume(JavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void dotParse_parseComplex(Blackhole bh) { bh.consume(JavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 2. jparsec Benchmarks
  // =========================================================================
  @Benchmark public void jparsec_parseSimple(Blackhole bh) { bh.consume(JparsecJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void jparsec_parseFullyQualified(Blackhole bh) { bh.consume(JparsecJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void jparsec_parseNestedGenerics(Blackhole bh) { bh.consume(JparsecJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void jparsec_parseAnnotatedArray(Blackhole bh) { bh.consume(JparsecJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void jparsec_parseComplex(Blackhole bh) { bh.consume(JparsecJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 3. parsecj Benchmarks
  // =========================================================================
  @Benchmark public void parsecj_parseSimple(Blackhole bh) { bh.consume(ParsecjJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void parsecj_parseFullyQualified(Blackhole bh) { bh.consume(ParsecjJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void parsecj_parseNestedGenerics(Blackhole bh) { bh.consume(ParsecjJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void parsecj_parseAnnotatedArray(Blackhole bh) { bh.consume(ParsecjJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void parsecj_parseComplex(Blackhole bh) { bh.consume(ParsecjJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 3b. taker Benchmarks
  // =========================================================================
  @Benchmark public void taker_parseSimple(Blackhole bh) { bh.consume(TakerJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void taker_parseFullyQualified(Blackhole bh) { bh.consume(TakerJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void taker_parseNestedGenerics(Blackhole bh) { bh.consume(TakerJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void taker_parseAnnotatedArray(Blackhole bh) { bh.consume(TakerJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void taker_parseComplex(Blackhole bh) { bh.consume(TakerJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 4. fastparse Benchmarks
  // =========================================================================
  @Benchmark public void fastparse_parseSimple(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void fastparse_parseFullyQualified(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void fastparse_parseNestedGenerics(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void fastparse_parseAnnotatedArray(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void fastparse_parseComplex(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 5. parboiled Benchmarks (Excluded)
  // =========================================================================

  // =========================================================================
  // 6. antlr4 Benchmarks
  // =========================================================================
  @State(Scope.Thread)
  public static class Antlr4TypeState {
    public final Antlr4JavaTypeParser parser = new Antlr4JavaTypeParser();
  }

  @Benchmark public void antlr4_parseSimple(Antlr4TypeState state, Blackhole bh) { bh.consume(state.parser.parse(SIMPLE)); }
  @Benchmark public void antlr4_parseFullyQualified(Antlr4TypeState state, Blackhole bh) { bh.consume(state.parser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void antlr4_parseNestedGenerics(Antlr4TypeState state, Blackhole bh) { bh.consume(state.parser.parse(NESTED_GENERICS)); }
  @Benchmark public void antlr4_parseAnnotatedArray(Antlr4TypeState state, Blackhole bh) { bh.consume(state.parser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void antlr4_parseComplex(Antlr4TypeState state, Blackhole bh) { bh.consume(state.parser.parse(COMPLEX)); }

  // =========================================================================
  // 7. parboiled2 Benchmarks (Excluded)
  // =========================================================================

  // =========================================================================
  // 8. petitparser Benchmarks
  // =========================================================================
  @Benchmark public void petitparser_parseSimple(Blackhole bh) { bh.consume(PetitParserJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void petitparser_parseFullyQualified(Blackhole bh) { bh.consume(PetitParserJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void petitparser_parseNestedGenerics(Blackhole bh) { bh.consume(PetitParserJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void petitparser_parseAnnotatedArray(Blackhole bh) { bh.consume(PetitParserJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void petitparser_parseComplex(Blackhole bh) { bh.consume(PetitParserJavaTypeParser.parse(COMPLEX)); }
}

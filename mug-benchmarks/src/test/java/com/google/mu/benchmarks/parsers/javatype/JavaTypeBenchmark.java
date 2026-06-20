package com.google.mu.benchmarks.parsers.javatype;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

import com.google.mu.benchmarks.parsers.jparsec.JparsecJavaTypeParser;
import com.google.mu.benchmarks.parsers.catsparse.CatsParseJavaTypeParser;
import com.google.mu.benchmarks.parsers.fastparse.FastparseJavaTypeParser;
import com.google.mu.benchmarks.parsers.parboiled.ParboiledJavaTypeParser;

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
      "@com.google.NonNull java.util.List<@com.google.Nullable java.lang.String>[]";
      
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
  // 3. cats-parse Benchmarks
  // =========================================================================
  @Benchmark public void catsParse_parseSimple(Blackhole bh) { bh.consume(CatsParseJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void catsParse_parseFullyQualified(Blackhole bh) { bh.consume(CatsParseJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void catsParse_parseNestedGenerics(Blackhole bh) { bh.consume(CatsParseJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void catsParse_parseAnnotatedArray(Blackhole bh) { bh.consume(CatsParseJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void catsParse_parseComplex(Blackhole bh) { bh.consume(CatsParseJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 4. fastparse Benchmarks
  // =========================================================================
  @Benchmark public void fastparse_parseSimple(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void fastparse_parseFullyQualified(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void fastparse_parseNestedGenerics(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void fastparse_parseAnnotatedArray(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void fastparse_parseComplex(Blackhole bh) { bh.consume(FastparseJavaTypeParser.parse(COMPLEX)); }

  // =========================================================================
  // 5. parboiled Benchmarks
  // =========================================================================
  @Benchmark public void parboiled_parseSimple(Blackhole bh) { bh.consume(ParboiledJavaTypeParser.parse(SIMPLE)); }
  @Benchmark public void parboiled_parseFullyQualified(Blackhole bh) { bh.consume(ParboiledJavaTypeParser.parse(FULLY_QUALIFIED)); }
  @Benchmark public void parboiled_parseNestedGenerics(Blackhole bh) { bh.consume(ParboiledJavaTypeParser.parse(NESTED_GENERICS)); }
  @Benchmark public void parboiled_parseAnnotatedArray(Blackhole bh) { bh.consume(ParboiledJavaTypeParser.parse(ANNOTATED_ARRAY)); }
  @Benchmark public void parboiled_parseComplex(Blackhole bh) { bh.consume(ParboiledJavaTypeParser.parse(COMPLEX)); }
}

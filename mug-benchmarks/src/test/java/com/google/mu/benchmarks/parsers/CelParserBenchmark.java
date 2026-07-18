package com.google.mu.benchmarks.parsers;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.parser.CelParserFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import com.google.mu.cel.CelParser;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@SuppressWarnings("IdentifierName")
public class CelParserBenchmark {

  private dev.cel.parser.CelParser antlrParser;
  private CelParser labsParser;

  private String smokeTestExpr;
  private String chainedOrsExpr;
  private String chainedAndsExpr;
  private String messageCreationExpr;
  private String anyFieldMessageSelectionExpr;
  private String deepFieldMessageSelectionExpr;
  private String longListExpr;
  private String simpleMessageContextExpr;
  private String listComprehensionExpr;
  private String mapComprehensionExpr;

  @Setup
  public void setup() {
    antlrParser =
        CelParserFactory.standardCelParserBuilder()
            .setOptions(
                CelOptions.current()
                    .enableOptionalSyntax(true)
                    .retainRepeatedUnaryOperators(true)
                    .build())
            .build();

    labsParser = new CelParser();

    smokeTestExpr = "1 + 2 == 3";
    chainedOrsExpr = "1 > 2 || 2 > 3 || 3 > 4 || 4 > 5 || 5 > 6";
    chainedAndsExpr = "1 < 2 && 2 < 3 && 3 < 4 && 4 < 5 && 5 < 6";
    messageCreationExpr =
        "NestedTestAllTypes{ "
            + "child: NestedTestAllTypes{ payload: TestAllTypes{single_int64: 42} }, "
            + "payload: TestAllTypes{ "
            + "  single_string: '', "
            + "  single_nested_message: "
            + "    TestAllTypes.NestedMessage{ bb: 42}"
            + "  }"
            + "}"
            + ".child.payload.single_int64 == 42";
    anyFieldMessageSelectionExpr = "payload.single_any.single_int64 == 42";
    deepFieldMessageSelectionExpr = "child.child.child.child.payload.single_int64 == 42";
    longListExpr =
        String.format(
            "size(%s) == 1000",
            LongStream.rangeClosed(1, 1000)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "[", "]")));
    simpleMessageContextExpr = "payload.single_int64 == 42 && payload.single_string == '42'";
    listComprehensionExpr = "payload.repeated_int64.exists(i, i >= 99)";
    mapComprehensionExpr = "payload.map_int64_int64.exists(k, k >= 99)";
  }

  // Smoke Test
  @Benchmark
  public Object benchmarkAntlr_smokeTest() throws Exception {
    return antlrParser.parse(smokeTestExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_smokeTest() {
    return labsParser.parse(smokeTestExpr);
  }

  // Chained Ors
  @Benchmark
  public Object benchmarkAntlr_chainedOrs() throws Exception {
    return antlrParser.parse(chainedOrsExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_chainedOrs() {
    return labsParser.parse(chainedOrsExpr);
  }

  // Chained Ands
  @Benchmark
  public Object benchmarkAntlr_chainedAnds() throws Exception {
    return antlrParser.parse(chainedAndsExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_chainedAnds() {
    return labsParser.parse(chainedAndsExpr);
  }

  // Message Creation
  @Benchmark
  public Object benchmarkAntlr_messageCreation() throws Exception {
    return antlrParser.parse(messageCreationExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_messageCreation() {
    return labsParser.parse(messageCreationExpr);
  }

  // Any Field Message Selection
  @Benchmark
  public Object benchmarkAntlr_anyFieldMessageSelection() throws Exception {
    return antlrParser.parse(anyFieldMessageSelectionExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_anyFieldMessageSelection() {
    return labsParser.parse(anyFieldMessageSelectionExpr);
  }

  // Deep Field Message Selection
  @Benchmark
  public Object benchmarkAntlr_deepFieldMessageSelection() throws Exception {
    return antlrParser.parse(deepFieldMessageSelectionExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_deepFieldMessageSelection() {
    return labsParser.parse(deepFieldMessageSelectionExpr);
  }

  // Long List
  @Benchmark
  public Object benchmarkAntlr_longList() throws Exception {
    return antlrParser.parse(longListExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_longList() {
    return labsParser.parse(longListExpr);
  }

  // Simple Message Context
  @Benchmark
  public Object benchmarkAntlr_simpleMessageContext() throws Exception {
    return antlrParser.parse(simpleMessageContextExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_simpleMessageContext() {
    return labsParser.parse(simpleMessageContextExpr);
  }

  // List Comprehension
  @Benchmark
  public Object benchmarkAntlr_listComprehension() throws Exception {
    return antlrParser.parse(listComprehensionExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_listComprehension() {
    return labsParser.parse(listComprehensionExpr);
  }

  // Map Comprehension
  @Benchmark
  public Object benchmarkAntlr_mapComprehension() throws Exception {
    return antlrParser.parse(mapComprehensionExpr);
  }

  @Benchmark
  public Object benchmarkLabsText_mapComprehension() {
    return labsParser.parse(mapComprehensionExpr);
  }

  private static final ImmutableSet<String> VALID_CPP_EXPRS =
      ImmutableSet.of(
          "x * 2",
          "x * 2u",
          "x * 2.0",
          "\"\\u2764\"",
          "! false",
          "-a",
          "a.b(5)",
          "a[3]",
          "SomeMessage{foo: 5, bar: \"xyz\"}",
          "[3, 4, 5]",
          "{foo: 5, bar: \"xyz\"}",
          "a > 5 && a < 10",
          "a < 5 || a > 10",
          "\"A\"",
          "true",
          "false",
          "0",
          "42",
          "0u",
          "23u",
          "24u",
          "0xAu",
          "-0xA",
          "0xA",
          "-1",
          "4--4",
          "4--4.1",
          "b\"abc\"",
          "23.39",
          "!a",
          "a",
          "a?b:c",
          "a || b",
          "a || b || c || d || e || f ",
          "a && b",
          "a && b && c && d && e && f && g",
          "a && b && c && d || e && f && g && h",
          "a + b",
          "a - b",
          "a * b",
          "a / b",
          "a % b",
          "a in b",
          "a == b",
          "a != b",
          "a > b",
          "a >= b",
          "a < b",
          "a <= b",
          "a.b",
          "a.b.c",
          "a[b]",
          "foo{ }",
          "foo{ a:b }",
          "foo{ a:b, c:d }",
          "{}",
          "{a:b, c:d}",
          "[]",
          "[a]",
          "[a, b, c]",
          "(a)",
          "((a))",
          "a()",
          "a(b)",
          "a(b, c)",
          "a.b()",
          "a.b(c)",
          "aaa.bbb(ccc)",
          "has(m.f)",
          "m.exists_one(v, f)",
          "m.map(v, f)",
          "m.map(v, p, f)",
          "m.filter(v, p)",
          "[] + [1,2,3,] + [4]",
          "{1:2u, 2:3u}",
          "TestAllTypes{single_int32: 1, single_int64: 2}",
          "size(x) == x.size()",
          "\"\\\"\"",
          "[1,3,4][0]",
          "x[\"a\"].single_int32 == 23",
          "x.single_nested_message != null",
          "false && !true || false ? 2 : 3",
          "b\"abc\" + B\"def\"",
          "1 + 2 * 3 - 1 / 2 == 6 % 1",
          "---a",
          "\"abc\" + \"def\"",
          "\"\\xC3\\XBF\"",
          "\"\\303\\277\"",
          "\"hi\\u263A \\u263Athere\"",
          "\"\\U000003A8\\?\"",
          "\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\\\? Legal escapes\"",
          "'😁' in ['😁', '😑', '😦']",
          "'\\u00ff' in ['\\u00ff', '\\u00ff', '\\u00ff']",
          "'\\u00ff' in ['\\uffff', '\\U00100000', '\\U0010ffff']",
          "'\\u00ff' in ['\\U00100000', '\\uffff', '\\U0010ffff']",
          "a.`b`",
          "a.`b-c`",
          "a.`b c`",
          "a.`b/c`",
          "a.`b.c`",
          "a.`in`",
          "A{`b`: 1}",
          "A{`b-c`: 1}",
          "A{`b c`: 1}",
          "A{`b/c`: 1}",
          "A{`b.c`: 1}",
          "A{`in`: 1}",
          "has(a.`b/c`)",
          "x.filter(y, y.filter(z, z > 0))",
          "has(a.b).filter(c, c)",
          "x.filter(y, y.exists(z, has(z.a)) && y.exists(z, has(z.b)))",
          "has(a.b).asList().exists(c, c)",
          "a.?b[?0] && a[?c]",
          "{?'key': value}",
          "[?a, ?b]",
          "[?a[?b]]",
          "Msg{?field: value}",
          "m.optMap(v, f)",
          "m.optFlatMap(v, f)");

  // Cpp Suite (120 valid expressions)
  @Benchmark
  public void benchmarkAntlr_cppSuite(Blackhole bh) throws Exception {
    for (String expr : VALID_CPP_EXPRS) {
      bh.consume(antlrParser.parse(expr));
    }
  }

  @Benchmark
  public void benchmarkLabsText_cppSuite(Blackhole bh) {
    for (String expr : VALID_CPP_EXPRS) {
      bh.consume(labsParser.parse(expr));
    }
  }
}

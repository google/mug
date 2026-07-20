package com.google.mu.benchmarks.parsers;

import com.google.common.collect.ImmutableSet;
import com.google.mu.cel.CelParser;
import dev.cel.common.CelOptions;
import dev.cel.parser.CelParserFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.Test;
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
            .setStandardMacros(dev.cel.parser.CelStandardMacro.STANDARD_MACROS)
            .setOptions(
                CelOptions.current()
                    .enableOptionalSyntax(true)
                    .retainRepeatedUnaryOperators(true)
                    .populateMacroCalls(true)
                    .build())
            .build();

    labsParser = new CelParser();

    smokeTestExpr = "1 + 2 == 3 // smoke test";
    chainedOrsExpr = "1 > 2 || 2 > 3 || // comment\n 3 > 4 || 4 > 5 || 5 > 6";
    chainedAndsExpr = "1 < 2 && 2 < 3 && // comment\n 3 < 4 && 4 < 5 && 5 < 6";
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
    simpleMessageContextExpr =
        "payload.single_int64 == 42 // check single int64\n"
            + "&& payload.single_string == '42' // check single string";
    listComprehensionExpr = "payload.repeated_int64.exists(i, i >= 99) // list exists";
    mapComprehensionExpr = "payload.map_int64_int64.exists(k, k >= 99) // map exists";
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

  @Test
  public void sanityParityCheck() throws Exception {
    setup();
    assertParity(smokeTestExpr);
    assertParity(chainedOrsExpr);
    assertParity(chainedAndsExpr);
    assertParity(messageCreationExpr);
    assertParity(anyFieldMessageSelectionExpr);
    assertParity(deepFieldMessageSelectionExpr);
    assertParity(longListExpr);
    assertParity(simpleMessageContextExpr);
    assertParity(listComprehensionExpr);
    assertParity(mapComprehensionExpr);
    for (String expr : VALID_CPP_EXPRS) {
      assertParity(expr);
    }
  }

  private void assertParity(String expr) throws Exception {
    dev.cel.common.CelAbstractSyntaxTree antlrAst = antlrParser.parse(expr).getAst();
    dev.cel.expr.ParsedExpr antlrParsed =
        dev.cel.common.CelProtoAbstractSyntaxTree.fromCelAst(antlrAst).toParsedExpr();
    com.google.api.expr.v1alpha1.ParsedExpr labsParsed = labsParser.parseToProto(expr);
    assertExprEquals(expr, labsParsed.getExpr(), labsParsed, antlrParsed.getExpr(), antlrParsed);
  }

  private static void assertExprEquals(
      String expr,
      com.google.api.expr.v1alpha1.Expr labsExpr,
      com.google.api.expr.v1alpha1.ParsedExpr labsParsed,
      dev.cel.expr.Expr antlrExpr,
      dev.cel.expr.ParsedExpr antlrParsed) {

    org.junit.Assert.assertEquals(
        "Expression kind mismatch for expression ["
            + expr
            + "] at node: "
            + labsExpr
            + " vs "
            + antlrExpr,
        antlrExpr.getExprKindCase().name(),
        labsExpr.getExprKindCase().name());

    int labsPos = labsParsed.getSourceInfo().getPositionsMap().getOrDefault(labsExpr.getId(), -1);
    int antlrPos =
        antlrParsed.getSourceInfo().getPositionsMap().getOrDefault(antlrExpr.getId(), -1);

    boolean isNegativeLiteral =
        labsExpr.getExprKindCase() == com.google.api.expr.v1alpha1.Expr.ExprKindCase.CONST_EXPR
            && (labsExpr.getConstExpr().getInt64Value() < 0
                || labsExpr.getConstExpr().getUint64Value() < 0
                || labsExpr.getConstExpr().getDoubleValue() < 0);

    if (!isNegativeLiteral) {
      org.junit.Assert.assertEquals(
          "Position mismatch for expression [" + expr + "] at node: " + labsExpr,
          antlrPos,
          labsPos);
    }

    boolean labsHasMacro =
        labsParsed.getSourceInfo().getMacroCallsMap().containsKey(labsExpr.getId());
    boolean antlrHasMacro =
        antlrParsed.getSourceInfo().getMacroCallsMap().containsKey(antlrExpr.getId());
    if (antlrHasMacro != labsHasMacro) {
      System.err.println("Expression: " + expr);
      System.err.println(
          "ANTLR Macro calls keys: "
              + antlrParsed.getSourceInfo().getMacroCallsMap().keySet()
              + " map: "
              + antlrParsed.getSourceInfo().getMacroCallsMap());
      System.err.println(
          "Labs Macro calls keys: "
              + labsParsed.getSourceInfo().getMacroCallsMap().keySet()
              + " map: "
              + labsParsed.getSourceInfo().getMacroCallsMap());
    }
    org.junit.Assert.assertEquals(
        "Macro presence mismatch for expression [" + expr + "] at node: " + labsExpr,
        antlrHasMacro,
        labsHasMacro);
    if (labsHasMacro) {
      com.google.api.expr.v1alpha1.Expr labsMacro =
          labsParsed.getSourceInfo().getMacroCallsMap().get(labsExpr.getId());
      dev.cel.expr.Expr antlrMacro =
          antlrParsed.getSourceInfo().getMacroCallsMap().get(antlrExpr.getId());
      assertExprEquals(expr, labsMacro, labsParsed, antlrMacro, antlrParsed);
    }

    switch (labsExpr.getExprKindCase()) {
      case CONST_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getConstExpr().getConstantKindCase().name(),
            labsExpr.getConstExpr().getConstantKindCase().name());
        break;
      case IDENT_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getIdentExpr().getName(), labsExpr.getIdentExpr().getName());
        break;
      case SELECT_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getSelectExpr().getField(), labsExpr.getSelectExpr().getField());
        assertExprEquals(
            expr,
            labsExpr.getSelectExpr().getOperand(),
            labsParsed,
            antlrExpr.getSelectExpr().getOperand(),
            antlrParsed);
        break;
      case CALL_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getCallExpr().getFunction(), labsExpr.getCallExpr().getFunction());
        org.junit.Assert.assertEquals(
            antlrExpr.getCallExpr().hasTarget(), labsExpr.getCallExpr().hasTarget());
        if (labsExpr.getCallExpr().hasTarget()) {
          assertExprEquals(
              expr,
              labsExpr.getCallExpr().getTarget(),
              labsParsed,
              antlrExpr.getCallExpr().getTarget(),
              antlrParsed);
        }
        org.junit.Assert.assertEquals(
            antlrExpr.getCallExpr().getArgsCount(), labsExpr.getCallExpr().getArgsCount());
        for (int i = 0; i < labsExpr.getCallExpr().getArgsCount(); i++) {
          assertExprEquals(
              expr,
              labsExpr.getCallExpr().getArgs(i),
              labsParsed,
              antlrExpr.getCallExpr().getArgs(i),
              antlrParsed);
        }
        break;
      case LIST_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getListExpr().getElementsCount(), labsExpr.getListExpr().getElementsCount());
        for (int i = 0; i < labsExpr.getListExpr().getElementsCount(); i++) {
          assertExprEquals(
              expr,
              labsExpr.getListExpr().getElements(i),
              labsParsed,
              antlrExpr.getListExpr().getElements(i),
              antlrParsed);
        }
        break;
      case STRUCT_EXPR:
        org.junit.Assert.assertEquals(
            antlrExpr.getStructExpr().getMessageName(), labsExpr.getStructExpr().getMessageName());
        org.junit.Assert.assertEquals(
            antlrExpr.getStructExpr().getEntriesCount(),
            labsExpr.getStructExpr().getEntriesCount());
        for (int i = 0; i < labsExpr.getStructExpr().getEntriesCount(); i++) {
          com.google.api.expr.v1alpha1.Expr.CreateStruct.Entry labsEntry =
              labsExpr.getStructExpr().getEntries(i);
          dev.cel.expr.Expr.CreateStruct.Entry antlrEntry = antlrExpr.getStructExpr().getEntries(i);
          org.junit.Assert.assertEquals(
              antlrEntry.getKeyKindCase().name(), labsEntry.getKeyKindCase().name());
          switch (labsEntry.getKeyKindCase()) {
            case FIELD_KEY:
              org.junit.Assert.assertEquals(antlrEntry.getFieldKey(), labsEntry.getFieldKey());
              break;
            case MAP_KEY:
              assertExprEquals(
                  expr, labsEntry.getMapKey(), labsParsed, antlrEntry.getMapKey(), antlrParsed);
              break;
          }
          assertExprEquals(
              expr, labsEntry.getValue(), labsParsed, antlrEntry.getValue(), antlrParsed);
        }
        break;
      case COMPREHENSION_EXPR:
        dev.cel.expr.Expr.Comprehension antlrComp = antlrExpr.getComprehensionExpr();
        com.google.api.expr.v1alpha1.Expr.Comprehension labsComp = labsExpr.getComprehensionExpr();
        org.junit.Assert.assertEquals(antlrComp.getIterVar(), labsComp.getIterVar());
        org.junit.Assert.assertEquals(antlrComp.getAccuVar(), labsComp.getAccuVar());
        assertExprEquals(
            expr, labsComp.getIterRange(), labsParsed, antlrComp.getIterRange(), antlrParsed);
        assertExprEquals(
            expr, labsComp.getAccuInit(), labsParsed, antlrComp.getAccuInit(), antlrParsed);
        assertExprEquals(
            expr,
            labsComp.getLoopCondition(),
            labsParsed,
            antlrComp.getLoopCondition(),
            antlrParsed);
        assertExprEquals(
            expr, labsComp.getLoopStep(), labsParsed, antlrComp.getLoopStep(), antlrParsed);
        assertExprEquals(
            expr, labsComp.getResult(), labsParsed, antlrComp.getResult(), antlrParsed);
        break;
    }
  }
}

package com.google.mu.cel;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.cel.CelExpr.bytes;
import static com.google.mu.cel.CelExpr.negative;
import static com.google.mu.cel.CelExpr.not;
import static com.google.mu.cel.CelExpr.string;
import static com.google.mu.cel.CelExpr.unsigned;
import static com.google.mu.cel.CelExpr.value;
import static org.junit.Assert.assertThrows;

import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.cel.CelExpr.Element;
import com.google.mu.cel.CelExpr.Entry;
import com.google.mu.cel.CelExpr.FunctionCall;
import com.google.mu.cel.CelExpr.Ident;
import com.google.mu.cel.CelExpr.ListLiteral;
import com.google.mu.cel.CelExpr.Macro.*;
import com.google.mu.cel.CelExpr.MapLiteral;
import com.google.mu.cel.CelExpr.NullValue;
import com.google.mu.cel.CelExpr.OptionalIndex;
import com.google.mu.cel.CelExpr.OptionalSelect;
import com.google.mu.cel.CelExpr.StructLiteral;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelParserTest {
  private final CelParser parser = new CelParser();

  @Test
  public void testLiterals() throws Exception {
    assertAst("123", value(123L).withSourceIndex(0));
    assertAst("-123", value(-123L).withSourceIndex(0));
    assertAst("0x1a", value(26L).withSourceIndex(0));
    assertAst("-0x1b", value(-27L).withSourceIndex(0));
    assertAst("123u", unsigned(123L).withSourceIndex(0));
    assertAst("0x1au", unsigned(26L).withSourceIndex(0));
    assertAst("1.23", value(1.23).withSourceIndex(0));
    assertAst("-1.23", value(-1.23).withSourceIndex(0));
    assertAst("1e3", value(1000.0).withSourceIndex(0));
    assertAst("1.23e-4", value(1.23E-4).withSourceIndex(0));
    assertAst("-1.23e+4", value(-12300.0).withSourceIndex(0));
    assertAst("\"hello\"", string("hello").withSourceIndex(0));
    assertAst("'hello'", string("hello").withSourceIndex(0));
    assertAst("true", value(true).withSourceIndex(0));
    assertAst("false", value(false).withSourceIndex(0));
    assertAst("null", new NullValue(0));
  }

  @Test
  public void testIdentifiers() throws Exception {
    assertAst("x", new Ident(0, "x"));
    assertAst("true_var", new Ident(0, "true_var"));
    assertAst("false_var", new Ident(0, "false_var"));
    assertAst("null_var", new Ident(0, "null_var"));
    assertAst("in_var", new Ident(0, "in_var"));
  }

  @Test
  public void testUnaryOperators() throws Exception {
    assertAst("!true", not(value(true).withSourceIndex(1)).withSourceIndex(0));
    assertAst("-x", negative(new Ident(1, "x")).withSourceIndex(0));
    assertAst("-(1)", negative(value(1L).withSourceIndex(1)).withSourceIndex(0));
    assertAst("- (1)", negative(value(1L).withSourceIndex(2)).withSourceIndex(0));
    assertAst("-1", value(-1L).withSourceIndex(0));
    assertAst("- 1", value(-1L).withSourceIndex(0));
    assertAst("- 9223372036854775808", value(-9223372036854775808L).withSourceIndex(0));
    assertAst(
        "!!true", not(not(value(true).withSourceIndex(2)).withSourceIndex(1)).withSourceIndex(0));
    assertAst("--x", negative(negative(new Ident(2, "x")).withSourceIndex(1)).withSourceIndex(0));
  }

  @Test
  public void testBinaryOperators() throws Exception {
    assertAst(
        "1 + 2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "1 - 2",
        value(1L).withSourceIndex(0).subtract(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "1 * 2",
        value(1L).withSourceIndex(0).multiply(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "1 / 2",
        value(1L).withSourceIndex(0).divide(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "1 % 2",
        value(1L).withSourceIndex(0).modulo(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "1 + 2 * 3",
        value(1L)
            .withSourceIndex(0)
            .add(
                value(2L)
                    .withSourceIndex(4)
                    .multiply(value(3L).withSourceIndex(8))
                    .withSourceIndex(4))
            .withSourceIndex(0));

    assertAst(
        "(1 + 2) * 3",
        value(1L)
            .withSourceIndex(1)
            .add(value(2L).withSourceIndex(5))
            .withSourceIndex(0)
            .multiply(value(3L).withSourceIndex(10))
            .withSourceIndex(0));

    assertAst(
        "1 * 2 + 3",
        value(1L)
            .withSourceIndex(0)
            .multiply(value(2L).withSourceIndex(4))
            .withSourceIndex(0)
            .add(value(3L).withSourceIndex(8))
            .withSourceIndex(0));

    assertAst(
        "1+2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(2)).withSourceIndex(0));
    assertAst(
        "1-2",
        value(1L).withSourceIndex(0).subtract(value(2L).withSourceIndex(2)).withSourceIndex(0));
    assertAst(
        "1*2",
        value(1L).withSourceIndex(0).multiply(value(2L).withSourceIndex(2)).withSourceIndex(0));
    assertAst(
        "1/2",
        value(1L).withSourceIndex(0).divide(value(2L).withSourceIndex(2)).withSourceIndex(0));
    assertAst(
        "1%2",
        value(1L).withSourceIndex(0).modulo(value(2L).withSourceIndex(2)).withSourceIndex(0));
  }

  @Test
  public void testRelations() throws Exception {
    assertAst("a < b", new Ident(0, "a").lessThan(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a <= b", new Ident(0, "a").atMost(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a > b", new Ident(0, "a").greaterThan(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a >= b", new Ident(0, "a").atLeast(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a == b", new Ident(0, "a").equalTo(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a != b", new Ident(0, "a").notEqualTo(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a in b", new Ident(0, "a").in(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a<b", new Ident(0, "a").lessThan(new Ident(2, "b")).withSourceIndex(0));
    assertAst("a<=b", new Ident(0, "a").atMost(new Ident(3, "b")).withSourceIndex(0));
    assertAst("a>b", new Ident(0, "a").greaterThan(new Ident(2, "b")).withSourceIndex(0));
    assertAst("a>=b", new Ident(0, "a").atLeast(new Ident(3, "b")).withSourceIndex(0));
    assertAst("a==b", new Ident(0, "a").equalTo(new Ident(3, "b")).withSourceIndex(0));
    assertAst("a!=b", new Ident(0, "a").notEqualTo(new Ident(3, "b")).withSourceIndex(0));
  }

  @Test
  public void testLogical() throws Exception {
    assertAst("a && b", new Ident(0, "a").and(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a || b", new Ident(0, "a").or(new Ident(5, "b")).withSourceIndex(0));
    assertAst(
        "a && b || c",
        new Ident(0, "a")
            .and(new Ident(5, "b"))
            .withSourceIndex(0)
            .or(new Ident(10, "c"))
            .withSourceIndex(0));

    assertAst(
        "a || b && c",
        new Ident(0, "a")
            .or(new Ident(5, "b").and(new Ident(10, "c")).withSourceIndex(5))
            .withSourceIndex(0));
  }

  @Test
  public void testTernary() throws Exception {
    assertAst(
        "a ? b : c",
        new Ident(0, "a").ifElse(new Ident(4, "b"), new Ident(8, "c")).withSourceIndex(0));
    assertAst(
        "a ? (b ? c : d) : e",
        new Ident(0, "a")
            .ifElse(
                new Ident(5, "b").ifElse(new Ident(9, "c"), new Ident(13, "d")).withSourceIndex(4),
                new Ident(18, "e"))
            .withSourceIndex(0));
    assertAst(
        "a ? b : c ? d : e",
        new Ident(0, "a")
            .ifElse(
                new Ident(4, "b"),
                new Ident(8, "c").ifElse(new Ident(12, "d"), new Ident(16, "e")).withSourceIndex(8))
            .withSourceIndex(0));
    assertAst(
        "a || b ? c && d : e || f",
        new Ident(0, "a")
            .or(new Ident(5, "b"))
            .withSourceIndex(0)
            .ifElse(
                new Ident(9, "c").and(new Ident(14, "d")).withSourceIndex(9),
                new Ident(18, "e").or(new Ident(23, "f")).withSourceIndex(18))
            .withSourceIndex(0));
  }

  @Test
  public void testMemberOperations() throws Exception {
    assertAst("a.b", new Ident(0, "a").select("b").withSourceIndex(0));
    assertAst(
        "a.b.c", new Ident(0, "a").select("b").withSourceIndex(0).select("c").withSourceIndex(0));
    assertAst("a[0]", new Ident(0, "a").index(value(0L).withSourceIndex(2)).withSourceIndex(0));
    assertAst("a[b]", new Ident(0, "a").index(new Ident(2, "b")).withSourceIndex(0));
    assertAst(
        "a.b[c]",
        new Ident(0, "a")
            .select("b")
            .withSourceIndex(0)
            .index(new Ident(4, "c"))
            .withSourceIndex(0));
    assertAst(
        "a[b].c",
        new Ident(0, "a")
            .index(new Ident(2, "b"))
            .withSourceIndex(0)
            .select("c")
            .withSourceIndex(0));
    assertAst(
        "a(b)",
        new FunctionCall(0, new Ident(0, "a"), List.of(new Ident(2, "b"))).withSourceIndex(0));
    assertAst(
        "a.b(c)",
        new Ident(0, "a").call(new Ident(0, "b"), List.of(new Ident(4, "c"))).withSourceIndex(0));
    assertAst(
        "a(b, c)",
        new FunctionCall(0, new Ident(0, "a"), List.of(new Ident(2, "b"), new Ident(5, "c")))
            .withSourceIndex(0));
    assertAst(
        "a.b(c, d)",
        new Ident(0, "a")
            .call(new Ident(0, "b"), List.of(new Ident(4, "c"), new Ident(7, "d")))
            .withSourceIndex(0));
  }

  @Test
  public void testOptionalSyntax() throws Exception {
    assertAst("a.?b", new OptionalSelect(0, new Ident(0, "a"), "b"));
    assertAst("a[?b]", new OptionalIndex(0, new Ident(0, "a"), new Ident(3, "b")));
  }

  @Test
  public void testStructures() throws Exception {
    assertAst(
        "[1, 2, 3]",
        new ListLiteral(
            0,
            List.of(
                new Element(value(1L).withSourceIndex(1), false),
                new Element(value(2L).withSourceIndex(4), false),
                new Element(value(3L).withSourceIndex(7), false))));
    assertAst(
        "[1, 2,]",
        new ListLiteral(
            0,
            List.of(
                new Element(value(1L).withSourceIndex(1), false),
                new Element(value(2L).withSourceIndex(4), false)))); // Trailing comma
    assertAst("[]", new ListLiteral(0, List.of()));
    assertAst(
        "{'a': 1, 'b': 2}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false),
                new Entry<>(
                    0, string("b").withSourceIndex(9), value(2L).withSourceIndex(14), false))));
    assertAst(
        "{'a': 1, 'b': 2,}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false),
                new Entry<>(
                    0,
                    string("b").withSourceIndex(9),
                    value(2L).withSourceIndex(14),
                    false)))); // Trailing comma
    assertAst("{}", new MapLiteral(0, List.of()));
  }

  @Test
  public void testMessageCreation() throws Exception {
    assertAst(
        "Type{field: 1}",
        new StructLiteral(
            0,
            "Type",
            List.of(new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(12), false))));
    assertAst(
        "a.b.Type{field: 1, field2: 2}",
        new StructLiteral(
            0,
            "a.b.Type",
            List.of(
                new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(16), false),
                new Entry<>(0, new Ident(0, "field2"), value(2L).withSourceIndex(27), false))));
    assertAst(
        ".Type{field: 1}",
        new StructLiteral(
            0,
            ".Type",
            List.of(new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(13), false))));
    assertAst(
        ".   Type{field: 1}",
        new StructLiteral(
            0,
            ".Type",
            List.of(new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(16), false))));
  }

  @Test
  public void testComplexLiterals() throws Exception {
    assertAst("\"hello \\\"world\\\"\"", string("hello \"world\"").withSourceIndex(0));
    assertAst("'hello \\'world\\''", string("hello 'world'").withSourceIndex(0));
    assertAst("\"hello \\n world\"", string("hello \n world").withSourceIndex(0));
    assertAst("\"hello \\t world\"", string("hello \t world").withSourceIndex(0));
    assertAst("\"hello \\\\ world\"", string("hello \\ world").withSourceIndex(0));
    assertAst("r\"hello \\ world\"", string("hello \\ world").withSourceIndex(0));
    assertAst("r'hello \\ world'", string("hello \\ world").withSourceIndex(0));
    assertAst(
        "b\"hello\"",
        bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111})
            .withSourceIndex(0));
    assertAst(
        "b'hello'",
        bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111})
            .withSourceIndex(0));
    assertAst(
        "br'hello'",
        bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111})
            .withSourceIndex(0));
    assertAst(
        "br\"hello\"",
        bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111})
            .withSourceIndex(0));
    assertAst(
        "b\"hello \\n world\"",
        bytes(
                new byte[] {
                  (byte) 104,
                  (byte) 101,
                  (byte) 108,
                  (byte) 108,
                  (byte) 111,
                  (byte) 32,
                  (byte) 10,
                  (byte) 32,
                  (byte) 119,
                  (byte) 111,
                  (byte) 114,
                  (byte) 108,
                  (byte) 100
                })
            .withSourceIndex(0));
    assertAst("b\"\\x00\\x01\"", bytes(new byte[] {(byte) 0, (byte) 1}).withSourceIndex(0));
    assertAst("\"\\u270c\"", string("\u270c").withSourceIndex(0));
    // These might reveal bugs in byte parsing:
    assertAst("b\"\\xff\"", bytes(new byte[] {(byte) -1}).withSourceIndex(0));
  }

  @Test
  public void testComplexUnary() throws Exception {
    assertAst("!a.b", not(new Ident(1, "a").select("b").withSourceIndex(1)).withSourceIndex(0));
    assertAst(
        "!a[0]",
        not(new Ident(1, "a").index(value(0L).withSourceIndex(3)).withSourceIndex(1))
            .withSourceIndex(0));
    assertAst(
        "-a.b", negative(new Ident(1, "a").select("b").withSourceIndex(1)).withSourceIndex(0));
    assertAst(
        "-a[0]",
        negative(new Ident(1, "a").index(value(0L).withSourceIndex(3)).withSourceIndex(1))
            .withSourceIndex(0));
    assertAst(
        "-(a + b)",
        negative(new Ident(2, "a").add(new Ident(6, "b")).withSourceIndex(1)).withSourceIndex(0));
  }

  @Test
  public void testComplexBinaryAndPrecedence() throws Exception {
    assertAst(
        "a + b * c / d % e - f",
        new Ident(0, "a")
            .add(
                new Ident(4, "b")
                    .multiply(new Ident(8, "c"))
                    .withSourceIndex(4)
                    .divide(new Ident(12, "d"))
                    .withSourceIndex(4)
                    .modulo(new Ident(16, "e"))
                    .withSourceIndex(4))
            .withSourceIndex(0)
            .subtract(new Ident(20, "f"))
            .withSourceIndex(0));
    assertAst(
        "a - b - c",
        new Ident(0, "a")
            .subtract(new Ident(4, "b"))
            .withSourceIndex(0)
            .subtract(new Ident(8, "c"))
            .withSourceIndex(0));

    assertAst(
        "a / b / c",
        new Ident(0, "a")
            .divide(new Ident(4, "b"))
            .withSourceIndex(0)
            .divide(new Ident(8, "c"))
            .withSourceIndex(0));

    assertAst(
        "a % b % c",
        new Ident(0, "a")
            .modulo(new Ident(4, "b"))
            .withSourceIndex(0)
            .modulo(new Ident(8, "c"))
            .withSourceIndex(0));

    assertAst(
        "a + b < c - d",
        new Ident(0, "a")
            .add(new Ident(4, "b"))
            .withSourceIndex(0)
            .lessThan(new Ident(8, "c").subtract(new Ident(12, "d")).withSourceIndex(8))
            .withSourceIndex(0));
    assertAst(
        "a * b == c + d",
        new Ident(0, "a")
            .multiply(new Ident(4, "b"))
            .withSourceIndex(0)
            .equalTo(new Ident(9, "c").add(new Ident(13, "d")).withSourceIndex(9))
            .withSourceIndex(0));
    assertAst(
        "a && b || c && d",
        new Ident(0, "a")
            .and(new Ident(5, "b"))
            .withSourceIndex(0)
            .or(new Ident(10, "c").and(new Ident(15, "d")).withSourceIndex(10))
            .withSourceIndex(0));
    assertAst(
        "a || b && c || d",
        new Ident(0, "a")
            .or(new Ident(5, "b").and(new Ident(10, "c")).withSourceIndex(5))
            .withSourceIndex(0)
            .or(new Ident(15, "d"))
            .withSourceIndex(0));
    assertAst(
        "!a && b || c",
        not(new Ident(1, "a"))
            .withSourceIndex(0)
            .and(new Ident(6, "b"))
            .withSourceIndex(0)
            .or(new Ident(11, "c"))
            .withSourceIndex(0));
  }

  @Test
  public void testComplexTernary() throws Exception {
    assertAst(
        "a ? b : c ? d : e ? f : g",
        new Ident(0, "a")
            .ifElse(
                new Ident(4, "b"),
                new Ident(8, "c")
                    .ifElse(
                        new Ident(12, "d"),
                        new Ident(16, "e")
                            .ifElse(new Ident(20, "f"), new Ident(24, "g"))
                            .withSourceIndex(16))
                    .withSourceIndex(8))
            .withSourceIndex(0));
    assertAst(
        "a && b ? c || d : e && f",
        new Ident(0, "a")
            .and(new Ident(5, "b"))
            .withSourceIndex(0)
            .ifElse(
                new Ident(9, "c").or(new Ident(14, "d")).withSourceIndex(9),
                new Ident(18, "e").and(new Ident(23, "f")).withSourceIndex(18))
            .withSourceIndex(0));
    assertAst(
        "a ? b : c ? d : e ? f : g",
        new Ident(0, "a")
            .ifElse(
                new Ident(4, "b"),
                new Ident(8, "c")
                    .ifElse(
                        new Ident(12, "d"),
                        new Ident(16, "e")
                            .ifElse(new Ident(20, "f"), new Ident(24, "g"))
                            .withSourceIndex(16))
                    .withSourceIndex(8))
            .withSourceIndex(0));
  }

  @Test
  public void testComplexMemberOperations() throws Exception {
    assertAst(
        "a.b.c(d)",
        new Ident(0, "a")
            .select("b")
            .withSourceIndex(0)
            .call(new Ident(0, "c"), List.of(new Ident(6, "d")))
            .withSourceIndex(0));
    assertAst(
        "a.b(c).d",
        new Ident(0, "a")
            .call(new Ident(0, "b"), List.of(new Ident(4, "c")))
            .withSourceIndex(0)
            .select("d")
            .withSourceIndex(0));
    assertAst(
        "a(b)[c]",
        new FunctionCall(0, new Ident(0, "a"), List.of(new Ident(2, "b")))
            .withSourceIndex(0)
            .index(new Ident(5, "c"))
            .withSourceIndex(0));
    assertAst(
        "a.b(c).d(e)",
        new Ident(0, "a")
            .call(new Ident(0, "b"), List.of(new Ident(4, "c")))
            .withSourceIndex(0)
            .call(new Ident(7, "d"), List.of(new Ident(9, "e")))
            .withSourceIndex(0));
    assertAst(
        "a.b(c)[d].e(f)",
        new Ident(0, "a")
            .call(new Ident(0, "b"), List.of(new Ident(4, "c")))
            .withSourceIndex(0)
            .index(new Ident(7, "d"))
            .withSourceIndex(0)
            .call(new Ident(10, "e"), List.of(new Ident(12, "f")))
            .withSourceIndex(0));
    assertAst(
        "a.b.Type{field: 1}",
        new StructLiteral(
            0,
            "a.b.Type",
            List.of(new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(16), false))));
    assertAst(
        "Type{field: 1}.field",
        new StructLiteral(
                0,
                "Type",
                List.of(
                    new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(12), false)))
            .select("field")
            .withSourceIndex(0));
    assertAst(
        "a.b.Type{field: 1}.field",
        new StructLiteral(
                0,
                "a.b.Type",
                List.of(
                    new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(16), false)))
            .select("field")
            .withSourceIndex(0));
    assertAst(
        "Type{field: 1}[0]",
        new StructLiteral(
                0,
                "Type",
                List.of(
                    new Entry<>(0, new Ident(0, "field"), value(1L).withSourceIndex(12), false)))
            .index(value(0L).withSourceIndex(15))
            .withSourceIndex(0));
  }

  @Test
  public void testComplexOptionalSyntax() throws Exception {
    assertAst("a.?b.c", new OptionalSelect(0, new Ident(0, "a"), "b").select("c"));
    assertAst("a.b.?c", new OptionalSelect(0, new Ident(0, "a").select("b"), "c"));
    assertAst(
        "a[?b][c]",
        new OptionalIndex(0, new Ident(0, "a"), new Ident(3, "b")).index(new Ident(6, "c")));
    assertAst(
        "a[b][?c]",
        new OptionalIndex(0, new Ident(0, "a").index(new Ident(2, "b")), new Ident(6, "c")));
    assertAst(
        "a.?b[?c]",
        new OptionalIndex(0, new OptionalSelect(0, new Ident(0, "a"), "b"), new Ident(6, "c")));
  }

  @Test
  public void testComplexStructures() throws Exception {
    assertAst(
        "[[1, 2], [3, 4]]",
        new ListLiteral(
            0,
            List.of(
                new Element(
                    new ListLiteral(
                        1,
                        List.of(
                            new Element(value(1L).withSourceIndex(2), false),
                            new Element(value(2L).withSourceIndex(5), false))),
                    false),
                new Element(
                    new ListLiteral(
                        9,
                        List.of(
                            new Element(value(3L).withSourceIndex(10), false),
                            new Element(value(4L).withSourceIndex(13), false))),
                    false))));
    assertAst(
        "{'a': {'b': 1}}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(
                    0,
                    string("a").withSourceIndex(1),
                    new MapLiteral(
                        6,
                        List.of(
                            new Entry<>(
                                0,
                                string("b").withSourceIndex(7),
                                value(1L).withSourceIndex(12),
                                false))),
                    false))));
    assertAst(
        "[{'a': 1}, {'b': 2}]",
        new ListLiteral(
            0,
            List.of(
                new Element(
                    new MapLiteral(
                        1,
                        List.of(
                            new Entry<>(
                                0,
                                string("a").withSourceIndex(2),
                                value(1L).withSourceIndex(7),
                                false))),
                    false),
                new Element(
                    new MapLiteral(
                        11,
                        List.of(
                            new Entry<>(
                                0,
                                string("b").withSourceIndex(12),
                                value(2L).withSourceIndex(17),
                                false))),
                    false))));
    assertAst(
        "[?a, b, ?c]",
        new ListLiteral(
            0,
            List.of(
                new Element(new Ident(2, "a"), true),
                new Element(new Ident(5, "b"), false),
                new Element(new Ident(9, "c"), true))));
    assertAst(
        "{?a: b, ?c: d}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, new Ident(2, "a"), new Ident(5, "b"), true),
                new Entry<>(0, new Ident(9, "c"), new Ident(12, "d"), true))));
    assertAst(
        "Type{?field: value, field2: value}",
        new StructLiteral(
            0,
            "Type",
            List.of(
                new Entry<>(0, new Ident(0, "field"), new Ident(13, "value"), true),
                new Entry<>(0, new Ident(0, "field2"), new Ident(28, "value"), false))));
  }

  @Test
  public void testTripleQuotedStrings() throws Exception {
    assertAst("\"\"\"hello\"\"\"", string("hello").withSourceIndex(0));
    assertAst("'''hello'''", string("hello").withSourceIndex(0));
    assertAst("r\"\"\"hello\"\"\"", string("hello").withSourceIndex(0));
    assertAst("r'''hello'''", string("hello").withSourceIndex(0));
    assertAst("\"\"\"hello \"world\" \"\"\"", string("hello \"world\" ").withSourceIndex(0));
    assertAst("'''hello 'world' '''", string("hello 'world' ").withSourceIndex(0));
    assertAst("\"\"\"hello\\nworld\"\"\"", string("hello\nworld").withSourceIndex(0));
    assertAst("'''hello\\nworld'''", string("hello\nworld").withSourceIndex(0));
    assertAst("\"\"\"hello \\n world\"\"\"", string("hello \n world").withSourceIndex(0));
    assertAst("'''hello \\t world'''", string("hello \t world").withSourceIndex(0));
  }

  @Test
  public void testEscapes() throws Exception {
    assertAst(
        "\"hello \\a \\b \\f \\n \\r \\t \\v \\? \\` \\' \\\" \\\\\"",
        string("hello \u0007 \u0008 \u000c \n \r \t \u000b ? ` ' \" \\").withSourceIndex(0));
    assertAst("\"\\377\"", string("\u00ff").withSourceIndex(0));
    assertAst("\"\\000\"", string("\u0000").withSourceIndex(0));
    assertAst("\"\\U0000270c\"", string("\u270c").withSourceIndex(0));
  }

  @Test
  public void testComments() throws Exception {
    assertAstWithComments(
        "1 + 2 // comment",
        value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAstWithComments(
        "1 + // comment\n 2",
        value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(16)).withSourceIndex(0));
    assertAstWithComments(
        "// comment\n 1 + 2",
        value(1L).withSourceIndex(12).add(value(2L).withSourceIndex(16)).withSourceIndex(12));
    assertAstWithComments(
        "1 // comment 1\n + 2 // comment 2",
        value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(18)).withSourceIndex(0));
  }

  @Test
  public void testMapNonStringKeys() throws Exception {
    assertAst(
        "{1: 'a', 2u: 'b', true: 'c'}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, value(1L).withSourceIndex(1), string("a").withSourceIndex(4), false),
                new Entry<>(
                    0, unsigned(2L).withSourceIndex(9), string("b").withSourceIndex(13), false),
                new Entry<>(
                    0, value(true).withSourceIndex(18), string("c").withSourceIndex(24), false))));
    assertAst(
        "{1.0: 'a'}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(
                    0, value(1.0).withSourceIndex(1), string("a").withSourceIndex(6), false))));
  }

  @Test
  public void testKeywordsAsFields() throws Exception {
    assertAst("a.`in`", new Ident(0, "a").select("in").withSourceIndex(0));
    assertAst("a.` b`", new Ident(0, "a").select(" b").withSourceIndex(0));
    assertAst("a.`b `", new Ident(0, "a").select("b ").withSourceIndex(0));
    assertAst("a.` b `", new Ident(0, "a").select(" b ").withSourceIndex(0));
  }

  @Test
  public void testComplexPrecedenceMixed() throws Exception {
    assertAst(
        "a ? b + c : d * e",
        new Ident(0, "a")
            .ifElse(
                new Ident(4, "b").add(new Ident(8, "c")).withSourceIndex(4),
                new Ident(12, "d").multiply(new Ident(16, "e")).withSourceIndex(12))
            .withSourceIndex(0));
    assertAst(
        "a || b && c == d + e",
        new Ident(0, "a")
            .or(
                new Ident(5, "b")
                    .and(
                        new Ident(10, "c")
                            .equalTo(new Ident(15, "d").add(new Ident(19, "e")).withSourceIndex(15))
                            .withSourceIndex(10))
                    .withSourceIndex(5))
            .withSourceIndex(0));
    assertAst(
        "!a && -b",
        not(new Ident(1, "a"))
            .withSourceIndex(0)
            .and(negative(new Ident(7, "b")).withSourceIndex(6))
            .withSourceIndex(0));
    assertAst(
        "a < b && c >= d || e == f",
        new Ident(0, "a")
            .lessThan(new Ident(4, "b"))
            .withSourceIndex(0)
            .and(new Ident(9, "c").atLeast(new Ident(14, "d")).withSourceIndex(9))
            .withSourceIndex(0)
            .or(new Ident(19, "e").equalTo(new Ident(24, "f")).withSourceIndex(19))
            .withSourceIndex(0));
  }

  @Test
  public void testInvalidSyntax() throws Exception {
    assertParseFailure("b\"\\u270c\"", "1:4", "expecting <a>, encountered: ");
    assertParseFailure("!-x", "1:3", "expecting <digits>, encountered: ");
    assertParseFailure("a ? b ? c : d : e", "1:7", "expecting <:>, encountered: ");
    assertParseFailure("a[b](c)", "1:5", "expecting <EOF>, encountered: ");
    assertParseFailure("1 + ", "1:5", "expecting <!>, encountered: ");
    assertParseFailure("?", "1:1", "expecting <!>, encountered: ");
    assertParseFailure("a ? b", "1:6", "expecting <:>, encountered: ");
    assertParseFailure("a : b", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("{'a'}", "1:5", "expecting <:>, encountered: ");
    assertParseFailure("[1,,2]", "1:4", "expecting <]>, encountered: ");
    assertParseFailure("a.in", "1:3", "expecting <identifier>, encountered: ");
    assertParseFailure("a.true", "1:3", "expecting <identifier>, encountered: ");
    assertParseFailure("a.false", "1:3", "expecting <identifier>, encountered: ");
    assertParseFailure("a.null", "1:3", "expecting <identifier>, encountered: ");
    assertParseFailure("b\"\"\"\\u270c\"\"\"", "1:6", "expecting <a>, encountered: ");
    assertParseFailure("b'''\\u270c'''", "1:6", "expecting <a>, encountered: ");
    assertParseFailure("rb'hello'", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("rb\"hello\"", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("Type{field: 1}(1)", "1:15", "expecting <EOF>, encountered: ");
    assertParseFailure("!-x", "1:3", "expecting <digits>, encountered: ");
    assertParseFailure("-!x", "1:2", "expecting <digits>, encountered: ");
    assertParseFailure("a input", "1:5", "unexpected `[a-zA-Z0-9_]`: ");
    assertParseFailure("r \"hello\"", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("b \"hello\"", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("r 'hello'", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("b 'hello'", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("br 'hello'", "1:4", "expecting <EOF>, encountered: ");
    assertParseFailure("rb 'hello'", "1:4", "expecting <EOF>, encountered: ");
    assertParseFailure("\"hello\\ nworld\"", "1:8", "expecting <a>, encountered: ");
    assertParseFailure("\"hello\\3 7 7world\"", "1:9", "expecting <[0-7]>, encountered: ");
    assertParseFailure("\"hello\\x1 aworld\"", "1:9", "expecting <2 hex digits>, encountered: ");
  }

  @Test
  public void testCppSuite_valid_arithmetic() {
    assertAst("x * 2", new Ident(0, "x").multiply(value(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "x * 2u", new Ident(0, "x").multiply(unsigned(2L).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "4--4",
        value(4L).withSourceIndex(0).subtract(value(-4L).withSourceIndex(2)).withSourceIndex(0));
    assertAst("a + b", new Ident(0, "a").add(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a - b", new Ident(0, "a").subtract(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a * b", new Ident(0, "a").multiply(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a / b", new Ident(0, "a").divide(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a % b", new Ident(0, "a").modulo(new Ident(4, "b")).withSourceIndex(0));
    assertAst(
        "b\"abc\" + B\"def\"",
        bytes(new byte[] {(byte) 97, (byte) 98, (byte) 99})
            .withSourceIndex(0)
            .add(bytes(new byte[] {(byte) 100, (byte) 101, (byte) 102}).withSourceIndex(9))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_memberOperations() {
    assertAst(
        "x * 2.0", new Ident(0, "x").multiply(value(2.0).withSourceIndex(4)).withSourceIndex(0));
    assertAst(
        "a.b(5)",
        new Ident(0, "a")
            .call(new Ident(0, "b"), List.of(value(5L).withSourceIndex(4)))
            .withSourceIndex(0));
    assertAst(
        "4--4.1",
        value(4L).withSourceIndex(0).subtract(value(-4.1).withSourceIndex(2)).withSourceIndex(0));
    assertAst("23.39", value(23.39).withSourceIndex(0));
    assertAst("a.b", new Ident(0, "a").select("b").withSourceIndex(0));
    assertAst(
        "a.b.c", new Ident(0, "a").select("b").withSourceIndex(0).select("c").withSourceIndex(0));
    assertAst("(a)", new Ident(0, "a"));
    assertAst("((a))", new Ident(0, "a"));
    assertAst("a()", new FunctionCall(0, new Ident(0, "a"), List.of()).withSourceIndex(0));
    assertAst(
        "a(b)",
        new FunctionCall(0, new Ident(0, "a"), List.of(new Ident(2, "b"))).withSourceIndex(0));
    assertAst(
        "a(b, c)",
        new FunctionCall(0, new Ident(0, "a"), List.of(new Ident(2, "b"), new Ident(5, "c")))
            .withSourceIndex(0));
    assertAst("a.b()", new Ident(0, "a").call(new Ident(0, "b"), List.of()).withSourceIndex(0));
    assertAst(
        "a.b(c)",
        new Ident(0, "a").call(new Ident(0, "b"), List.of(new Ident(4, "c"))).withSourceIndex(0));
    assertAst(
        "aaa.bbb(ccc)",
        new Ident(0, "aaa")
            .call(new Ident(0, "bbb"), List.of(new Ident(8, "ccc")))
            .withSourceIndex(0));
    assertAst("has(m.f)", new Has(0, new Ident(4, "m").select("f").withSourceIndex(4)));
    assertAst(
        "x.single_nested_message != null",
        new Ident(0, "x")
            .select("single_nested_message")
            .withSourceIndex(0)
            .notEqualTo(new NullValue(27))
            .withSourceIndex(0));
    assertAst("a.`b`", new Ident(0, "a").select("b").withSourceIndex(0));
    assertAst("a.`b-c`", new Ident(0, "a").select("b-c").withSourceIndex(0));
    assertAst("a.`b c`", new Ident(0, "a").select("b c").withSourceIndex(0));
    assertAst("a.`b/c`", new Ident(0, "a").select("b/c").withSourceIndex(0));
    assertAst("a.`b.c`", new Ident(0, "a").select("b.c").withSourceIndex(0));
    assertAst("a.`in`", new Ident(0, "a").select("in").withSourceIndex(0));
    assertAst("has(a.`b/c`)", new Has(0, new Ident(4, "a").select("b/c").withSourceIndex(4)));
  }

  @Test
  public void testCppSuite_valid_literals() {
    assertAst("\"\\u2764\"", string("\u2764").withSourceIndex(0));
    assertAst("\"A\"", string("A").withSourceIndex(0));
    assertAst("true", value(true).withSourceIndex(0));
    assertAst("false", value(false).withSourceIndex(0));
    assertAst("0", value(0L).withSourceIndex(0));
    assertAst("42", value(42L).withSourceIndex(0));
    assertAst("0u", unsigned(0L).withSourceIndex(0));
    assertAst("23u", unsigned(23L).withSourceIndex(0));
    assertAst("24u", unsigned(24L).withSourceIndex(0));
    assertAst("0xAu", unsigned(10L).withSourceIndex(0));
    assertAst("0xA", value(10L).withSourceIndex(0));
    assertAst("b\"abc\"", bytes(new byte[] {(byte) 97, (byte) 98, (byte) 99}).withSourceIndex(0));
    assertAst("a", new Ident(0, "a"));
    assertAst("\"\\\"\"", string("\"").withSourceIndex(0));
    assertAst(
        "\"abc\" + \"def\"",
        string("abc").withSourceIndex(0).add(string("def").withSourceIndex(8)).withSourceIndex(0));
    assertAst("\"\\xC3\\XBF\"", string("\u00c3\u00bf").withSourceIndex(0));
    assertAst("\"\\303\\277\"", string("\u00c3\u00bf").withSourceIndex(0));
    assertAst("\"hi\\u263A \\u263Athere\"", string("hi\u263a \u263athere").withSourceIndex(0));
    assertAst("\"\\U000003A8\\?\"", string("\u03a8?").withSourceIndex(0));
    assertAst(
        "\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\\\? Legal escapes\"",
        string("\u0007\u0008\u000c\n\r\t\u000b'\"\\? Legal escapes").withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_unaryOperators() {
    assertAst("! false", not(value(false).withSourceIndex(2)).withSourceIndex(0));
    assertAst("-a", negative(new Ident(1, "a")).withSourceIndex(0));
    assertAst("-0xA", value(-10L).withSourceIndex(0));
    assertAst("-1", value(-1L).withSourceIndex(0));
    assertAst("!a", not(new Ident(1, "a")).withSourceIndex(0));
    assertAst(
        "---a",
        negative(negative(negative(new Ident(3, "a")).withSourceIndex(2)).withSourceIndex(1))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_creators() {
    assertAst("a[3]", new Ident(0, "a").index(value(3L).withSourceIndex(2)).withSourceIndex(0));
    assertAst(
        "SomeMessage{foo: 5, bar: \"xyz\"}",
        new StructLiteral(
            0,
            "SomeMessage",
            List.of(
                new Entry<>(0, new Ident(0, "foo"), value(5L).withSourceIndex(17), false),
                new Entry<>(0, new Ident(0, "bar"), string("xyz").withSourceIndex(25), false))));
    assertAst(
        "[3, 4, 5]",
        new ListLiteral(
            0,
            List.of(
                new Element(value(3L).withSourceIndex(1), false),
                new Element(value(4L).withSourceIndex(4), false),
                new Element(value(5L).withSourceIndex(7), false))));
    assertAst(
        "{foo: 5, bar: \"xyz\"}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, new Ident(1, "foo"), value(5L).withSourceIndex(6), false),
                new Entry<>(0, new Ident(9, "bar"), string("xyz").withSourceIndex(14), false))));
    assertAst("a[b]", new Ident(0, "a").index(new Ident(2, "b")).withSourceIndex(0));
    assertAst("foo{ }", new StructLiteral(0, "foo", List.of()));
    assertAst(
        "foo{ a:b }",
        new StructLiteral(
            0, "foo", List.of(new Entry<>(0, new Ident(0, "a"), new Ident(7, "b"), false))));
    assertAst(
        "foo{ a:b, c:d }",
        new StructLiteral(
            0,
            "foo",
            List.of(
                new Entry<>(0, new Ident(0, "a"), new Ident(7, "b"), false),
                new Entry<>(0, new Ident(0, "c"), new Ident(12, "d"), false))));
    assertAst("{}", new MapLiteral(0, List.of()));
    assertAst(
        "{a:b, c:d}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, new Ident(1, "a"), new Ident(3, "b"), false),
                new Entry<>(0, new Ident(6, "c"), new Ident(8, "d"), false))));
    assertAst("[]", new ListLiteral(0, List.of()));
    assertAst("[a]", new ListLiteral(0, List.of(new Element(new Ident(1, "a"), false))));
    assertAst(
        "[a, b, c]",
        new ListLiteral(
            0,
            List.of(
                new Element(new Ident(1, "a"), false),
                new Element(new Ident(4, "b"), false),
                new Element(new Ident(7, "c"), false))));
    assertAst(
        "[] + [1,2,3,] + [4]",
        new ListLiteral(0, List.of())
            .add(
                new ListLiteral(
                    5,
                    List.of(
                        new Element(value(1L).withSourceIndex(6), false),
                        new Element(value(2L).withSourceIndex(8), false),
                        new Element(value(3L).withSourceIndex(10), false))))
            .withSourceIndex(0)
            .add(new ListLiteral(16, List.of(new Element(value(4L).withSourceIndex(17), false))))
            .withSourceIndex(0));
    assertAst(
        "{1:2u, 2:3u}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(
                    0, value(1L).withSourceIndex(1), unsigned(2L).withSourceIndex(3), false),
                new Entry<>(
                    0, value(2L).withSourceIndex(7), unsigned(3L).withSourceIndex(9), false))));
    assertAst(
        "TestAllTypes{single_int32: 1, single_int64: 2}",
        new StructLiteral(
            0,
            "TestAllTypes",
            List.of(
                new Entry<>(0, new Ident(0, "single_int32"), value(1L).withSourceIndex(27), false),
                new Entry<>(
                    0, new Ident(0, "single_int64"), value(2L).withSourceIndex(44), false))));
    assertAst(
        "[1,3,4][0]",
        new ListLiteral(
                0,
                List.of(
                    new Element(value(1L).withSourceIndex(1), false),
                    new Element(value(3L).withSourceIndex(3), false),
                    new Element(value(4L).withSourceIndex(5), false)))
            .index(value(0L).withSourceIndex(8))
            .withSourceIndex(0));
    assertAst(
        "x[\"a\"].single_int32 == 23",
        new Ident(0, "x")
            .index(string("a").withSourceIndex(2))
            .withSourceIndex(0)
            .select("single_int32")
            .withSourceIndex(0)
            .equalTo(value(23L).withSourceIndex(23))
            .withSourceIndex(0));
    assertAst(
        "'😁' in ['😁', '😑', '😦']",
        string("\ud83d\ude01")
            .withSourceIndex(0)
            .in(
                new ListLiteral(
                    8,
                    List.of(
                        new Element(string("\ud83d\ude01").withSourceIndex(9), false),
                        new Element(string("\ud83d\ude11").withSourceIndex(15), false),
                        new Element(string("\ud83d\ude26").withSourceIndex(21), false))))
            .withSourceIndex(0));
    assertAst(
        "'\\u00ff' in ['\\u00ff', '\\u00ff', '\\u00ff']",
        string("\u00ff")
            .withSourceIndex(0)
            .in(
                new ListLiteral(
                    12,
                    List.of(
                        new Element(string("\u00ff").withSourceIndex(13), false),
                        new Element(string("\u00ff").withSourceIndex(23), false),
                        new Element(string("\u00ff").withSourceIndex(33), false))))
            .withSourceIndex(0));
    assertAst(
        "'\\u00ff' in ['\\uffff', '\\U00100000', '\\U0010ffff']",
        string("\u00ff")
            .withSourceIndex(0)
            .in(
                new ListLiteral(
                    12,
                    List.of(
                        new Element(string("\uffff").withSourceIndex(13), false),
                        new Element(string("\udbc0\udc00").withSourceIndex(23), false),
                        new Element(string("\udbff\udfff").withSourceIndex(37), false))))
            .withSourceIndex(0));
    assertAst(
        "'\\u00ff' in ['\\U00100000', '\\uffff', '\\U0010ffff']",
        string("\u00ff")
            .withSourceIndex(0)
            .in(
                new ListLiteral(
                    12,
                    List.of(
                        new Element(string("\udbc0\udc00").withSourceIndex(13), false),
                        new Element(string("\uffff").withSourceIndex(27), false),
                        new Element(string("\udbff\udfff").withSourceIndex(37), false))))
            .withSourceIndex(0));
    assertAst(
        "A{`b`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "b"), value(1L).withSourceIndex(7), false))));
    assertAst(
        "A{`b-c`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "b-c"), value(1L).withSourceIndex(9), false))));
    assertAst(
        "A{`b c`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "b c"), value(1L).withSourceIndex(9), false))));
    assertAst(
        "A{`b/c`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "b/c"), value(1L).withSourceIndex(9), false))));
    assertAst(
        "A{`b.c`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "b.c"), value(1L).withSourceIndex(9), false))));
    assertAst(
        "A{`in`: 1}",
        new StructLiteral(
            0,
            "A",
            List.of(new Entry<>(0, new Ident(0, "in"), value(1L).withSourceIndex(8), false))));
    assertAst(
        "{?'key': value}",
        new MapLiteral(
            0,
            List.of(
                new Entry<>(0, string("key").withSourceIndex(2), new Ident(9, "value"), true))));
  }

  @Test
  public void testCppSuite_valid_logical() {
    assertAst(
        "a > 5 && a < 10",
        new Ident(0, "a")
            .greaterThan(value(5L).withSourceIndex(4))
            .withSourceIndex(0)
            .and(new Ident(9, "a").lessThan(value(10L).withSourceIndex(13)).withSourceIndex(9))
            .withSourceIndex(0));
    assertAst(
        "a < 5 || a > 10",
        new Ident(0, "a")
            .lessThan(value(5L).withSourceIndex(4))
            .withSourceIndex(0)
            .or(new Ident(9, "a").greaterThan(value(10L).withSourceIndex(13)).withSourceIndex(9))
            .withSourceIndex(0));
    assertAst("a || b", new Ident(0, "a").or(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a && b", new Ident(0, "a").and(new Ident(5, "b")).withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_ternary() {
    assertAst(
        "a?b:c", new Ident(0, "a").ifElse(new Ident(2, "b"), new Ident(4, "c")).withSourceIndex(0));
    assertAst(
        "false && !true || false ? 2 : 3",
        value(false)
            .withSourceIndex(0)
            .and(not(value(true).withSourceIndex(10)).withSourceIndex(9))
            .withSourceIndex(0)
            .or(value(false).withSourceIndex(18))
            .withSourceIndex(0)
            .ifElse(value(2L).withSourceIndex(26), value(3L).withSourceIndex(30))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_relations() {
    assertAst("a in b", new Ident(0, "a").in(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a == b", new Ident(0, "a").equalTo(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a != b", new Ident(0, "a").notEqualTo(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a > b", new Ident(0, "a").greaterThan(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a >= b", new Ident(0, "a").atLeast(new Ident(5, "b")).withSourceIndex(0));
    assertAst("a < b", new Ident(0, "a").lessThan(new Ident(4, "b")).withSourceIndex(0));
    assertAst("a <= b", new Ident(0, "a").atMost(new Ident(5, "b")).withSourceIndex(0));
    assertAst(
        "1 + 2 * 3 - 1 / 2 == 6 % 1",
        value(1L)
            .withSourceIndex(0)
            .add(
                value(2L)
                    .withSourceIndex(4)
                    .multiply(value(3L).withSourceIndex(8))
                    .withSourceIndex(4))
            .withSourceIndex(0)
            .subtract(
                value(1L)
                    .withSourceIndex(12)
                    .divide(value(2L).withSourceIndex(16))
                    .withSourceIndex(12))
            .withSourceIndex(0)
            .equalTo(
                value(6L)
                    .withSourceIndex(21)
                    .modulo(value(1L).withSourceIndex(25))
                    .withSourceIndex(21))
            .withSourceIndex(0));
    assertAst(
        "a <= b <= c",
        new Ident(0, "a")
            .atMost(new Ident(5, "b"))
            .withSourceIndex(0)
            .atMost(new Ident(10, "c"))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_macros() {
    assertAst("m.exists_one(v, f)", new ExistsOne(0, new Ident(0, "m"), "v", new Ident(16, "f")));
    assertAst("m.map(v, f)", new Map(0, new Ident(0, "m"), "v", new Ident(9, "f")));
    assertAst(
        "m.map(v, p, f)",
        new FilterMap(0, new Ident(0, "m"), "v", new Ident(9, "p"), new Ident(12, "f")));
    assertAst("m.filter(v, p)", new Filter(0, new Ident(0, "m"), "v", new Ident(12, "p")));
    assertAst(
        "size(x) == x.size()",
        new FunctionCall(0, new Ident(0, "size"), List.of(new Ident(5, "x")))
            .withSourceIndex(0)
            .equalTo(new Ident(11, "x").call(new Ident(11, "size"), List.of()).withSourceIndex(11))
            .withSourceIndex(0));
    assertAst(
        "x.filter(y, y.filter(z, z > 0))",
        new Filter(
            0,
            new Ident(0, "x"),
            "y",
            new Filter(
                12,
                new Ident(12, "y"),
                "z",
                new Ident(24, "z")
                    .greaterThan(value(0L).withSourceIndex(28))
                    .withSourceIndex(24))));
    assertAst(
        "has(a.b).filter(c, c)",
        new Filter(
            0,
            new Has(0, new Ident(4, "a").select("b").withSourceIndex(4)),
            "c",
            new Ident(19, "c")));
    assertAst(
        "x.filter(y, y.exists(z, has(z.a)) && y.exists(z, has(z.b)))",
        new Filter(
            0,
            new Ident(0, "x"),
            "y",
            new Exists(
                    12,
                    new Ident(12, "y"),
                    "z",
                    new Has(24, new Ident(28, "z").select("a").withSourceIndex(28)))
                .and(
                    new Exists(
                        37,
                        new Ident(37, "y"),
                        "z",
                        new Has(49, new Ident(53, "z").select("b").withSourceIndex(53))))
                .withSourceIndex(12)));
    assertAst(
        "has(a.b).asList().exists(c, c)",
        new Exists(
            0,
            new Has(0, new Ident(4, "a").select("b").withSourceIndex(4))
                .call(new Ident(9, "asList"), List.of())
                .withSourceIndex(0),
            "c",
            new Ident(28, "c")));
    assertAst(
        "m.all(x, x > 0)",
        new All(
            0,
            new Ident(0, "m"),
            "x",
            new Ident(9, "x").greaterThan(value(0L).withSourceIndex(13)).withSourceIndex(9)));
    assertAst(
        "[1, 2].exists(x, x > 0)",
        new Exists(
            0,
            new ListLiteral(
                0,
                List.of(
                    new Element(value(1L).withSourceIndex(1), false),
                    new Element(value(2L).withSourceIndex(4), false))),
            "x",
            new Ident(17, "x").greaterThan(value(0L).withSourceIndex(21)).withSourceIndex(17)));
    assertAst(
        "{\"a\": 1}.exists(x, x == 'a')",
        new Exists(
            0,
            new MapLiteral(
                0,
                List.of(
                    new Entry<>(
                        0, string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false))),
            "x",
            new Ident(19, "x").equalTo(string("a").withSourceIndex(24)).withSourceIndex(19)));
    assertAst(
        "exists(x, y)",
        new FunctionCall(0, new Ident(0, "exists"), List.of(new Ident(7, "x"), new Ident(10, "y")))
            .withSourceIndex(0));
    assertAst(
        "all(x, y)",
        new FunctionCall(0, new Ident(0, "all"), List.of(new Ident(4, "x"), new Ident(7, "y")))
            .withSourceIndex(0));
    assertAst(
        "map(x, y)",
        new FunctionCall(0, new Ident(0, "map"), List.of(new Ident(4, "x"), new Ident(7, "y")))
            .withSourceIndex(0));
    assertAst(
        "filter(x, y)",
        new FunctionCall(0, new Ident(0, "filter"), List.of(new Ident(7, "x"), new Ident(10, "y")))
            .withSourceIndex(0));
    assertAst(
        "exists_one(x, y)",
        new FunctionCall(
                0, new Ident(0, "exists_one"), List.of(new Ident(11, "x"), new Ident(14, "y")))
            .withSourceIndex(0));
    assertParseFailure("has(x)", "1:1", "has() expects 1 select argument");
    assertParseFailure("has(x, y)", "1:1", "has() expects 1 arg, 2 provided");
    assertParseFailure("has()", "1:1", "has() expects 1 arg, 0 provided");
    assertAst(
        "has(a.b.c)",
        new Has(
            0, new Ident(4, "a").select("b").withSourceIndex(4).select("c").withSourceIndex(4)));
  }

  @Test
  public void testCppSuite_valid_optionalSyntax() {
    assertAst(
        "a.?b[?0] && a[?c]",
        new Ident(0, "a")
            .optionalSelect("b")
            .optionalIndex(value(0L).withSourceIndex(6))
            .and(new Ident(12, "a").optionalIndex(new Ident(15, "c")).withSourceIndex(12))
            .withSourceIndex(0));
    assertAst(
        "[?a, ?b]",
        new ListLiteral(
            0,
            List.of(new Element(new Ident(2, "a"), true), new Element(new Ident(6, "b"), true))));
    assertAst(
        "[?a[?b]]",
        new ListLiteral(
            0,
            List.of(
                new Element(
                    new Ident(2, "a").optionalIndex(new Ident(5, "b")).withSourceIndex(2), true))));
    assertAst(
        "Msg{?field: value}",
        new StructLiteral(
            0,
            "Msg",
            List.of(new Entry<>(0, new Ident(0, "field"), new Ident(12, "value"), true))));
    assertAst(
        "m.optMap(v, f)",
        new Ident(0, "m")
            .call(new Ident(0, "optMap"), List.of(new Ident(9, "v"), new Ident(12, "f")))
            .withSourceIndex(0));
    assertAst(
        "m.optFlatMap(v, f)",
        new Ident(0, "m")
            .call(new Ident(0, "optFlatMap"), List.of(new Ident(13, "v"), new Ident(16, "f")))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_logicalChaining() {
    assertAst(
        "a || b || c || d || e || f",
        new Ident(0, "a")
            .or(new Ident(5, "b"))
            .withSourceIndex(0)
            .or(new Ident(10, "c"))
            .withSourceIndex(0)
            .or(
                new Ident(15, "d")
                    .or(new Ident(20, "e"))
                    .withSourceIndex(15)
                    .or(new Ident(25, "f"))
                    .withSourceIndex(15))
            .withSourceIndex(0));
    assertAst(
        "a && b && c && d && e && f && g",
        new Ident(0, "a")
            .and(new Ident(5, "b"))
            .withSourceIndex(0)
            .and(new Ident(10, "c").and(new Ident(15, "d")).withSourceIndex(10))
            .withSourceIndex(0)
            .and(
                new Ident(20, "e")
                    .and(new Ident(25, "f"))
                    .withSourceIndex(20)
                    .and(new Ident(30, "g"))
                    .withSourceIndex(20))
            .withSourceIndex(0));
    assertAst(
        "a && b && c && d || e && f && g && h",
        new Ident(0, "a")
            .and(new Ident(5, "b"))
            .withSourceIndex(0)
            .and(new Ident(10, "c").and(new Ident(15, "d")).withSourceIndex(10))
            .withSourceIndex(0)
            .or(
                new Ident(20, "e")
                    .and(new Ident(25, "f"))
                    .withSourceIndex(20)
                    .and(new Ident(30, "g").and(new Ident(35, "h")).withSourceIndex(30))
                    .withSourceIndex(20))
            .withSourceIndex(0));
  }

  @Test
  public void testCppSuite_invalid() {
    assertParseFailure("{", "1:2", "expecting <?>, encountered: ");
    assertParseFailure("*@a | b", "1:1", "expecting <!>, encountered: ");
    assertParseFailure("a | b", "1:3", "expecting <EOF>, encountered: ");
    assertParseFailure("?", "1:1", "expecting <!>, encountered: ");
    assertParseFailure("t{>C}", "1:3", "expecting <}>, encountered: ");
    assertParseFailure(
        "TestAllTypes(){single_int32: 1, single_int64: 2}",
        "1:15",
        "expecting <EOF>, encountered: ");
    assertParseFailure("1 + $", "1:5", "expecting <!>, encountered: ");
    assertParseFailure("1 + 2\n3 +", "2:1", "expecting <EOF>, encountered: ");
    assertParseFailure("1.all(2, 3)", "1:1", "identifier expected for the 1st arg of all()");
    assertParseFailure("1 + +", "1:5", "expecting <!>, encountered: ");
    assertParseFailure("{\"a\": 1}.\"a\"", "1:10", "expecting <`>, encountered: ");
    assertParseFailure("\"\\xFh\"", "1:4", "expecting <2 hex digits>, encountered: ");
    assertParseFailure(
        "\"\\a\\b\\f\\n\\r\\t\\v\\'\\\"\\\\\\? Illegal escape \\>\"",
        "1:41",
        "expecting <a>, encountered: ");
    assertParseFailure(
        "'😁' in ['😁', '😑', '😦']\n   && in.😁", "2:7", "expecting <identifier>, encountered: ");
    assertParseFailure("as", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("break", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("const", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("continue", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("else", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("for", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("function", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("if", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("import", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("in", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("let", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("loop", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("package", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("namespace", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("return", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("var", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("void", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure("while", "1:1", "expecting <identifier>, encountered: ");
    assertParseFailure(
        "[1, 2, 3].map(var, var * var)", "1:15", "expecting <identifier>, encountered: ");
    assertParseFailure(
        "[\n\t\r[\n\t\r[\n\t\r]\n\t\r]\n\t\r", "6:3", "expecting <]>, encountered: ");
    assertParseFailure("a.`b\tc`", "1:5", "expecting <`>, encountered: ");
    assertParseFailure(
        "a.`@foo`", "1:4", "expecting <one or more [a-zA-Z0-9_./ -]>, encountered: ");
    assertParseFailure(
        "a.`$foo`", "1:4", "expecting <one or more [a-zA-Z0-9_./ -]>, encountered: ");
    assertParseFailure("`a.b`", "1:1", "expecting <!>, encountered: ");
    assertParseFailure("`a.b`()", "1:1", "expecting <!>, encountered: ");
    assertParseFailure("b'\\UFFFFFFFF'", "1:4", "expecting <a>, encountered: ");
  }

  @Test
  public void testFailures() {
    assertParseFailure("1(2)", "1:2", "expecting <EOF>, encountered: ");
    assertParseFailure("1{foo: 2}", "1:2", "expecting <EOF>, encountered: ");
  }

  @Test
  public void testIntegerOverflow_signedPositive() {
    assertParseFailure("9223372036854775808", "1:1", "integer overflow");
  }

  @Test
  public void testIntegerOverflow_signedNegative() {
    assertParseFailure("-9223372036854775809", "1:1", "integer overflow");
  }

  @Test
  public void testIntegerOverflow_unsigned() {
    assertParseFailure("18446744073709551616u", "1:1", "integer overflow");
  }

  @Test
  public void testExistsMacro_invalidArgs() {
    assertParseFailure("m.exists(v, f, g)", "1:1", "exists() expects 2 args, 3 provided");
    assertParseFailure("m.exists(1, f)", "1:1", "identifier expected for the 1st arg of exists()");
    assertParseFailure("m.exists(v)", "1:1", "exists() expects 2 args, 1 provided");
  }

  @Test
  public void testMapMacro_invalidArgs() {
    assertParseFailure("m.map(v)", "1:1", "map() macro expects 2 or 3 args, 1 provided");
    assertParseFailure("m.map(v, f, g, h)", "1:1", "map() macro expects 2 or 3 args, 4 provided");
    assertParseFailure("m.map(1, f)", "1:1", "identifier expected for the 1st arg of map()");
    assertParseFailure("m.map(1, f, g)", "1:1", "identifier expected for the 1st arg of map()");
  }

  @Test
  public void testAllMacro_invalidArgs() {
    assertParseFailure("m.all(v, f, g)", "1:1", "all() expects 2 args, 3 provided");
    assertParseFailure("m.all(1, f)", "1:1", "identifier expected for the 1st arg of all()");
    assertParseFailure("m.all(v)", "1:1", "all() expects 2 args, 1 provided");
  }

  @Test
  public void testExistsOneMacro_invalidArgs() {
    assertParseFailure("m.exists_one(v, f, g)", "1:1", "exists_one() expects 2 args, 3 provided");
    assertParseFailure(
        "m.exists_one(1, f)", "1:1", "identifier expected for the 1st arg of exists_one()");
    assertParseFailure("m.exists_one(v)", "1:1", "exists_one() expects 2 args, 1 provided");
  }

  @Test
  public void testFilterMacro_invalidArgs() {
    assertParseFailure("m.filter(v, f, g)", "1:1", "filter() expects 2 args, 3 provided");
    assertParseFailure("m.filter(1, f)", "1:1", "identifier expected for the 1st arg of filter()");
    assertParseFailure("m.filter(v)", "1:1", "filter() expects 2 args, 1 provided");
  }

  @Test
  public void testSourceIndex() throws Exception {
    CelExpr ast = parser.parse("a + b * 3");
    assertThat(ast.sourceIndex()).isEqualTo(2);
    CelExpr.Add add = (CelExpr.Add) ast;
    assertThat(add.left().sourceIndex()).isEqualTo(0);
    CelExpr.Multiply mult = (CelExpr.Multiply) add.right();
    assertThat(mult.sourceIndex()).isEqualTo(6);
    assertThat(mult.left().sourceIndex()).isEqualTo(4);
    assertThat(mult.right().sourceIndex()).isEqualTo(8);
  }

  private void assertAst(String expression, CelExpr expectedAst) {
    CelExpr ast = parser.parse(expression);
    assertThat(clearSourceIndices(ast)).isEqualTo(clearSourceIndices(expectedAst));
  }

  private void assertAstWithComments(String expression, CelExpr expectedAst) {
    CelExpr ast = parser.withComments().parse(expression);
    assertThat(clearSourceIndices(ast)).isEqualTo(clearSourceIndices(expectedAst));
  }

  private static CelExpr clearSourceIndices(CelExpr expr) {
    if (expr == null) {
      return null;
    }
    return switch (expr) {
      case CelExpr.NullValue v -> new CelExpr.NullValue(0);
      case CelExpr.BoolValue v -> new CelExpr.BoolValue(0, v.value());
      case CelExpr.LongValue v -> new CelExpr.LongValue(0, v.value());
      case CelExpr.UintValue v -> new CelExpr.UintValue(0, v.value());
      case CelExpr.DoubleValue v -> new CelExpr.DoubleValue(0, v.value());
      case CelExpr.StringValue v -> new CelExpr.StringValue(0, v.value());
      case CelExpr.BytesValue v -> new CelExpr.BytesValue(0, v.value());
      case CelExpr.Ident v -> new CelExpr.Ident(0, v.name());
      case CelExpr.Select v -> new CelExpr.Select(0, clearSourceIndices(v.operand()), v.field());
      case CelExpr.Index v ->
          new CelExpr.Index(0, clearSourceIndices(v.operand()), clearSourceIndices(v.index()));
      case CelExpr.OptionalSelect v ->
          new CelExpr.OptionalSelect(0, clearSourceIndices(v.operand()), v.field());
      case CelExpr.OptionalIndex v ->
          new CelExpr.OptionalIndex(
              0, clearSourceIndices(v.operand()), clearSourceIndices(v.index()));
      case CelExpr.Add v ->
          new CelExpr.Add(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Subtract v ->
          new CelExpr.Subtract(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Multiply v ->
          new CelExpr.Multiply(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Divide v ->
          new CelExpr.Divide(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Modulo v ->
          new CelExpr.Modulo(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.LessThan v ->
          new CelExpr.LessThan(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.LessThanOrEqualTo v ->
          new CelExpr.LessThanOrEqualTo(
              0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.GreaterThan v ->
          new CelExpr.GreaterThan(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.GreaterThanOrEqualTo v ->
          new CelExpr.GreaterThanOrEqualTo(
              0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.EqualTo v ->
          new CelExpr.EqualTo(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.NotEqualTo v ->
          new CelExpr.NotEqualTo(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.And v ->
          new CelExpr.And(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Or v ->
          new CelExpr.Or(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.In v ->
          new CelExpr.In(0, clearSourceIndices(v.left()), clearSourceIndices(v.right()));
      case CelExpr.Not v -> new CelExpr.Not(0, clearSourceIndices(v.operand()));
      case CelExpr.Negative v -> new CelExpr.Negative(0, clearSourceIndices(v.operand()));
      case CelExpr.Ternary v ->
          new CelExpr.Ternary(
              0,
              clearSourceIndices(v.condition()),
              clearSourceIndices(v.ifTrue()),
              clearSourceIndices(v.ifFalse()));
      case CelExpr.FunctionCall v ->
          new CelExpr.FunctionCall(
              0,
              (CelExpr.Ident) clearSourceIndices(v.function()),
              v.args().stream().map(CelParserTest::clearSourceIndices).toList());
      case CelExpr.MemberCall v ->
          new CelExpr.MemberCall(
              0,
              clearSourceIndices(v.target()),
              (CelExpr.Ident) clearSourceIndices(v.member()),
              v.args().stream().map(CelParserTest::clearSourceIndices).toList());
      case CelExpr.ListLiteral v ->
          new CelExpr.ListLiteral(
              0,
              v.elements().stream()
                  .map(
                      e ->
                          new CelExpr.ListLiteral.Element(
                              clearSourceIndices(e.value()), e.optional()))
                  .toList());
      case CelExpr.MapLiteral v ->
          new CelExpr.MapLiteral(
              0,
              v.entries().stream()
                  .map(
                      e ->
                          new CelExpr.Entry<>(
                              0,
                              clearSourceIndices(e.key()),
                              clearSourceIndices(e.value()),
                              e.optional()))
                  .toList());
      case CelExpr.StructLiteral v ->
          new CelExpr.StructLiteral(
              0,
              v.messageName(),
              v.fields().stream()
                  .map(
                      e ->
                          new CelExpr.Entry<>(
                              0,
                              (CelExpr.Ident) clearSourceIndices(e.key()),
                              clearSourceIndices(e.value()),
                              e.optional()))
                  .toList());
      case CelExpr.Macro.Has v ->
          new CelExpr.Macro.Has(0, (CelExpr.Select) clearSourceIndices(v.member()));
      case CelExpr.Macro.All v ->
          new CelExpr.Macro.All(
              0, clearSourceIndices(v.target()), v.varName(), clearSourceIndices(v.condition()));
      case CelExpr.Macro.Exists v ->
          new CelExpr.Macro.Exists(
              0, clearSourceIndices(v.target()), v.varName(), clearSourceIndices(v.condition()));
      case CelExpr.Macro.ExistsOne v ->
          new CelExpr.Macro.ExistsOne(
              0, clearSourceIndices(v.target()), v.varName(), clearSourceIndices(v.condition()));
      case CelExpr.Macro.Filter v ->
          new CelExpr.Macro.Filter(
              0, clearSourceIndices(v.target()), v.varName(), clearSourceIndices(v.expr()));
      case CelExpr.Macro.Map v ->
          new CelExpr.Macro.Map(
              0, clearSourceIndices(v.target()), v.varName(), clearSourceIndices(v.expr()));
      case CelExpr.Macro.FilterMap v ->
          new CelExpr.Macro.FilterMap(
              0,
              clearSourceIndices(v.target()),
              v.varName(),
              clearSourceIndices(v.filter()),
              clearSourceIndices(v.transform()));
    };
  }

  @Test
  public void parseToProto_positions() {
    ParsedExpr parsed = parser.parseToProto("a + b * 3");
    SourceInfo sourceInfo = parsed.getSourceInfo();
    assertThat(sourceInfo.getPositionsMap()).containsExactly(1L, 2, 2L, 0, 3L, 6, 4L, 4, 5L, 8);
  }

  @Test
  public void parseToProtoWithComments_lineOffsets() {
    ParsedExpr parsed = parser.withComments().parseToProto("a\n+ b\n* 3");
    SourceInfo sourceInfo = parsed.getSourceInfo();
    assertThat(sourceInfo.getLineOffsetsList()).containsExactly(2, 6, 10).inOrder();
  }

  @Test
  public void parseToProto_macroCalls() {
    ParsedExpr parsed = parser.parseToProto("has(a.b)");
    SourceInfo sourceInfo = parsed.getSourceInfo();
    Expr expr = parsed.getExpr();
    assertThat(sourceInfo.getMacroCallsMap())
        .containsExactly(
            expr.getId(),
            Expr.newBuilder()
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("has")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setSelectExpr(
                                    Expr.Select.newBuilder()
                                        .setOperand(
                                            Expr.newBuilder()
                                                .setId(2)
                                                .setIdentExpr(Expr.Ident.newBuilder().setName("a")))
                                        .setField("b"))))
                .build());
  }

  @Test
  public void parseToProto_balancedLogical() {
    ParsedExpr parsed = parser.parseToProto("a && b && c && d");
    SourceInfo sourceInfo = parsed.getSourceInfo();
    Expr expr = parsed.getExpr();

    // Verify balanced binary tree structure: (a && b) && (c && d)
    assertThat(expr.getCallExpr().getFunction()).isEqualTo("_&&_");
    assertThat(expr.getCallExpr().getArgs(0).getCallExpr().getFunction()).isEqualTo("_&&_");
    assertThat(expr.getCallExpr().getArgs(1).getCallExpr().getFunction()).isEqualTo("_&&_");

    // Verify positions map of balanced tree
    assertThat(sourceInfo.getPositionsMap())
        .containsExactly(1L, 7, 2L, 2, 3L, 0, 4L, 5, 5L, 12, 6L, 10, 7L, 15);
  }

  @Test
  public void parseToProto_positions_parenthesizedAndCurlyBraces() {
    // 1. Parenthesized expression: (a + b)
    ParsedExpr parsedParentheses = parser.parseToProto("(a + b)");
    assertThat(parsedParentheses.getSourceInfo().getPositionsMap())
        .containsExactly(
            1L, 3, // Binary '+' at index 3
            2L, 1, // Ident 'a' at index 1
            3L, 5 // Ident 'b' at index 5
            );

    // 2. Map literal: {"a": 1}
    ParsedExpr parsedMap = parser.parseToProto("{\"a\": 1}");
    assertThat(parsedMap.getSourceInfo().getPositionsMap())
        .containsExactly(
            1L, 0, // MapLiteral at '{' (index 0)
            2L, 4, // CreateStruct.Entry at ':' (index 4)
            3L, 1, // String key "a" at index 1
            4L, 6 // Integer value 1 at index 6
            );

    // 3. Struct literal: Type{field: 1}
    ParsedExpr parsedStruct = parser.parseToProto("Type{field: 1}");
    assertThat(parsedStruct.getSourceInfo().getPositionsMap())
        .containsExactly(
            1L, 4, // StructLiteral at '{' (index 4)
            2L, 10, // CreateStruct.Entry at ':' (index 10)
            3L, 12 // Integer value 1 at index 12
            );
  }

  private void assertParseFailure(
      String expression, String expectedPosition, String expectedMessageSubstring) {
    ParseException ex = assertThrows(ParseException.class, () -> parser.parse(expression));
    assertThat(ex.getMessage()).contains("at " + expectedPosition + ":");
    assertThat(ex.getMessage()).contains(expectedMessageSubstring);
  }
}

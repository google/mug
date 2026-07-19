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
import com.google.mu.cel.CelExpr.ListOf;
import com.google.mu.cel.CelExpr.Macro.*;
import com.google.mu.cel.CelExpr.MapOf;
import com.google.mu.cel.CelExpr.MemberCall;
import com.google.mu.cel.CelExpr.NullValue;
import com.google.mu.cel.CelExpr.Struct;
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
    assertAst("x", new Ident("x", 0));
    assertAst("true_var", new Ident("true_var", 0));
    assertAst("false_var", new Ident("false_var", 0));
    assertAst("null_var", new Ident("null_var", 0));
    assertAst("in_var", new Ident("in_var", 0));
  }

  @Test
  public void testUnaryOperators() throws Exception {
    assertAst("!true", not(value(true).withSourceIndex(1)).withSourceIndex(0));
    assertAst("-x", negative(new Ident("x", 1)).withSourceIndex(0));
    assertAst("-(1)", negative(value(1L).withSourceIndex(2)).withSourceIndex(0));
    assertAst("- (1)", negative(value(1L).withSourceIndex(3)).withSourceIndex(0));
    assertAst("-1", value(-1L).withSourceIndex(0));
    assertAst("- 1", value(-1L).withSourceIndex(0));
    assertAst("- 9223372036854775808", value(-9223372036854775808L).withSourceIndex(0));
    assertAst("!!true", not(not(value(true).withSourceIndex(2)).withSourceIndex(1)).withSourceIndex(0));
    assertAst("--x", negative(negative(new Ident("x", 2)).withSourceIndex(1)).withSourceIndex(0));
  }

  @Test
  public void testBinaryOperators() throws Exception {
    assertAst("1 + 2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("1 - 2", value(1L).withSourceIndex(0).subtract(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("1 * 2", value(1L).withSourceIndex(0).multiply(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("1 / 2", value(1L).withSourceIndex(0).divide(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("1 % 2", value(1L).withSourceIndex(0).modulo(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("1 + 2 * 3", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4).multiply(value(3L).withSourceIndex(8)).withSourceIndex(6)).withSourceIndex(2));

    assertAst("(1 + 2) * 3", value(1L).withSourceIndex(1).add(value(2L).withSourceIndex(5)).withSourceIndex(3).multiply(value(3L).withSourceIndex(10)).withSourceIndex(8));

    assertAst("1 * 2 + 3", value(1L).withSourceIndex(0).multiply(value(2L).withSourceIndex(4)).withSourceIndex(2).add(value(3L).withSourceIndex(8)).withSourceIndex(6));

    assertAst("1+2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(2)).withSourceIndex(1));
    assertAst("1-2", value(1L).withSourceIndex(0).subtract(value(2L).withSourceIndex(2)).withSourceIndex(1));
    assertAst("1*2", value(1L).withSourceIndex(0).multiply(value(2L).withSourceIndex(2)).withSourceIndex(1));
    assertAst("1/2", value(1L).withSourceIndex(0).divide(value(2L).withSourceIndex(2)).withSourceIndex(1));
    assertAst("1%2", value(1L).withSourceIndex(0).modulo(value(2L).withSourceIndex(2)).withSourceIndex(1));
  }

  @Test
  public void testRelations() throws Exception {
    assertAst("a < b", new Ident("a", 0).lessThan(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a <= b", new Ident("a", 0).atMost(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a > b", new Ident("a", 0).greaterThan(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a >= b", new Ident("a", 0).atLeast(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a == b", new Ident("a", 0).equalTo(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a != b", new Ident("a", 0).notEqualTo(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a in b", new Ident("a", 0).in(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a<b", new Ident("a", 0).lessThan(new Ident("b", 2)).withSourceIndex(1));
    assertAst("a<=b", new Ident("a", 0).atMost(new Ident("b", 3)).withSourceIndex(1));
    assertAst("a>b", new Ident("a", 0).greaterThan(new Ident("b", 2)).withSourceIndex(1));
    assertAst("a>=b", new Ident("a", 0).atLeast(new Ident("b", 3)).withSourceIndex(1));
    assertAst("a==b", new Ident("a", 0).equalTo(new Ident("b", 3)).withSourceIndex(1));
    assertAst("a!=b", new Ident("a", 0).notEqualTo(new Ident("b", 3)).withSourceIndex(1));
  }

  @Test
  public void testLogical() throws Exception {
    assertAst("a && b", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a || b", new Ident("a", 0).or(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a && b || c", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2).or(new Ident("c", 10)).withSourceIndex(7));

    assertAst("a || b && c", new Ident("a", 0).or(new Ident("b", 5).and(new Ident("c", 10)).withSourceIndex(7)).withSourceIndex(2));
  }

  @Test
  public void testTernary() throws Exception {
    assertAst("a ? b : c", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 4), new Ident("c", 8), 2));
    assertAst("a ? (b ? c : d) : e", new CelExpr.IfElse(new Ident("a", 0), new CelExpr.IfElse(new Ident("b", 5), new Ident("c", 9), new Ident("d", 13), 7), new Ident("e", 18), 2));
    assertAst("a ? b : c ? d : e", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 4), new CelExpr.IfElse(new Ident("c", 8), new Ident("d", 12), new Ident("e", 16), 10), 2));
    assertAst("a || b ? c && d : e || f", new CelExpr.IfElse(new Ident("a", 0).or(new Ident("b", 5)).withSourceIndex(2), new Ident("c", 9).and(new Ident("d", 14)).withSourceIndex(11), new Ident("e", 18).or(new Ident("f", 23)).withSourceIndex(20), 7));
  }

  @Test
  public void testMemberOperations() throws Exception {
    assertAst("a.b", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1));
    assertAst("a.b.c", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1).select(new Ident("c", 4)).withSourceIndex(3));
    assertAst("a[0]", new Ident("a", 0).index(value(0L).withSourceIndex(2)).withSourceIndex(2));
    assertAst("a[b]", new Ident("a", 0).index(new Ident("b", 2)).withSourceIndex(2));
    assertAst("a.b[c]", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1).index(new Ident("c", 4)).withSourceIndex(4));
    assertAst("a[b].c", new Ident("a", 0).index(new Ident("b", 2)).withSourceIndex(2).select(new Ident("c", 5)).withSourceIndex(4));
    assertAst("a(b)", new FunctionCall(new Ident("a", 0), List.of(new Ident("b", 2)), 1));
    assertAst("a.b(c)", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4))));
    assertAst("a(b, c)", new FunctionCall(new Ident("a", 0), List.of(new Ident("b", 2), new Ident("c", 5)), 1));
    assertAst("a.b(c, d)", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4), new Ident("d", 7))));
  }

  @Test
  public void testOptionalSyntax() throws Exception {
    assertAst("a.?b", new Ident("a", 0).optionalSelect(new Ident("b", 3)).withSourceIndex(1));
    assertAst("a[?b]", new Ident("a", 0).optionalIndex(new Ident("b", 3)).withSourceIndex(0));
  }

  @Test
  public void testStructures() throws Exception {
    assertAst("[1, 2, 3]", new ListOf(List.of(new Element(value(1L).withSourceIndex(1), false), new Element(value(2L).withSourceIndex(4), false), new Element(value(3L).withSourceIndex(7), false)), 0));
    assertAst("[1, 2,]", new ListOf(List.of(new Element(value(1L).withSourceIndex(1), false), new Element(value(2L).withSourceIndex(4), false)), 0)); // Trailing comma
    assertAst("[]", new ListOf(List.of(), 0));
    assertAst("{'a': 1, 'b': 2}", new MapOf(List.of(new Entry<>(string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false, 4), new Entry<>(string("b").withSourceIndex(9), value(2L).withSourceIndex(14), false, 12)), 0));
    assertAst("{'a': 1, 'b': 2,}", new MapOf(List.of(new Entry<>(string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false, 4), new Entry<>(string("b").withSourceIndex(9), value(2L).withSourceIndex(14), false, 12)), 0)); // Trailing comma
    assertAst("{}", new MapOf(List.of(), 0));
  }

  @Test
  public void testMessageCreation() throws Exception {
    assertAst("Type{field: 1}", new Struct("Type", List.of(new Entry<>(new Ident("field", 5), value(1L).withSourceIndex(12), false, 10)), 4));
    assertAst("a.b.Type{field: 1, field2: 2}", new Struct("a.b.Type", List.of(new Entry<>(new Ident("field", 9), value(1L).withSourceIndex(16), false, 14), new Entry<>(new Ident("field2", 19), value(2L).withSourceIndex(27), false, 25)), 8));
    assertAst(".Type{field: 1}", new Struct(".Type", List.of(new Entry<>(new Ident("field", 6), value(1L).withSourceIndex(13), false, 11)), 5));
    assertAst(".   Type{field: 1}", new Struct(".Type", List.of(new Entry<>(new Ident("field", 9), value(1L).withSourceIndex(16), false, 14)), 8));
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
    assertAst("b\"hello\"", bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111}).withSourceIndex(0));
    assertAst("b'hello'", bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111}).withSourceIndex(0));
    assertAst("br'hello'", bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111}).withSourceIndex(0));
    assertAst("br\"hello\"", bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111}).withSourceIndex(0));
    assertAst("b\"hello \\n world\"", bytes(new byte[] {(byte) 104, (byte) 101, (byte) 108, (byte) 108, (byte) 111, (byte) 32, (byte) 10, (byte) 32, (byte) 119, (byte) 111, (byte) 114, (byte) 108, (byte) 100}).withSourceIndex(0));
    assertAst("b\"\\x00\\x01\"", bytes(new byte[] {(byte) 0, (byte) 1}).withSourceIndex(0));
    assertAst("\"\\u270c\"", string("\u270c").withSourceIndex(0));
    // These might reveal bugs in byte parsing:
    assertAst("b\"\\xff\"", bytes(new byte[] {(byte) -1}).withSourceIndex(0));
  }

  @Test
  public void testComplexUnary() throws Exception {
    assertAst("!a.b", not(new Ident("a", 1).select(new Ident("b", 3)).withSourceIndex(2)).withSourceIndex(0));
    assertAst("!a[0]", not(new Ident("a", 1).index(value(0L).withSourceIndex(3)).withSourceIndex(3)).withSourceIndex(0));
    assertAst("-a.b", negative(new Ident("a", 1).select(new Ident("b", 3)).withSourceIndex(2)).withSourceIndex(0));
    assertAst("-a[0]", negative(new Ident("a", 1).index(value(0L).withSourceIndex(3)).withSourceIndex(3)).withSourceIndex(0));
    assertAst("-(a + b)", negative(new Ident("a", 2).add(new Ident("b", 6)).withSourceIndex(4)).withSourceIndex(0));
  }

  @Test
  public void testComplexBinaryAndPrecedence() throws Exception {
    assertAst("a + b * c / d % e - f", new Ident("a", 0).add(new Ident("b", 4).multiply(new Ident("c", 8)).withSourceIndex(6).divide(new Ident("d", 12)).withSourceIndex(10).modulo(new Ident("e", 16)).withSourceIndex(14)).withSourceIndex(2).subtract(new Ident("f", 20)).withSourceIndex(18));
    assertAst("a - b - c", new Ident("a", 0).subtract(new Ident("b", 4)).withSourceIndex(2).subtract(new Ident("c", 8)).withSourceIndex(6));

    assertAst("a / b / c", new Ident("a", 0).divide(new Ident("b", 4)).withSourceIndex(2).divide(new Ident("c", 8)).withSourceIndex(6));

    assertAst("a % b % c", new Ident("a", 0).modulo(new Ident("b", 4)).withSourceIndex(2).modulo(new Ident("c", 8)).withSourceIndex(6));

    assertAst("a + b < c - d", new Ident("a", 0).add(new Ident("b", 4)).withSourceIndex(2).lessThan(new Ident("c", 8).subtract(new Ident("d", 12)).withSourceIndex(10)).withSourceIndex(6));
    assertAst("a * b == c + d", new Ident("a", 0).multiply(new Ident("b", 4)).withSourceIndex(2).equalTo(new Ident("c", 9).add(new Ident("d", 13)).withSourceIndex(11)).withSourceIndex(6));
    assertAst("a && b || c && d", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2).or(new Ident("c", 10).and(new Ident("d", 15)).withSourceIndex(12)).withSourceIndex(7));
    assertAst("a || b && c || d", new Ident("a", 0).or(new Ident("b", 5).and(new Ident("c", 10)).withSourceIndex(7)).withSourceIndex(2).or(new Ident("d", 15)).withSourceIndex(12));
    assertAst("!a && b || c", not(new Ident("a", 1)).withSourceIndex(0).and(new Ident("b", 6)).withSourceIndex(3).or(new Ident("c", 11)).withSourceIndex(8));
  }

  @Test
  public void testComplexTernary() throws Exception {
    assertAst("a ? b : c ? d : e ? f : g", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 4), new CelExpr.IfElse(new Ident("c", 8), new Ident("d", 12), new CelExpr.IfElse(new Ident("e", 16), new Ident("f", 20), new Ident("g", 24), 18), 10), 2));
    assertAst("a && b ? c || d : e && f", new CelExpr.IfElse(new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2), new Ident("c", 9).or(new Ident("d", 14)).withSourceIndex(11), new Ident("e", 18).and(new Ident("f", 23)).withSourceIndex(20), 7));
    assertAst("a ? b : c ? d : e ? f : g", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 4), new CelExpr.IfElse(new Ident("c", 8), new Ident("d", 12), new CelExpr.IfElse(new Ident("e", 16), new Ident("f", 20), new Ident("g", 24), 18), 10), 2));
  }

  @Test
  public void testComplexMemberOperations() throws Exception {
    assertAst("a.b.c(d)", new MemberCall(5, new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1), new Ident("c", 4), List.of(new Ident("d", 6))));
    assertAst("a.b(c).d", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4))).select(new Ident("d", 7)).withSourceIndex(6));
    assertAst("a(b)[c]", new FunctionCall(new Ident("a", 0), List.of(new Ident("b", 2)), 1).index(new Ident("c", 5)).withSourceIndex(5));
    assertAst("a.b(c).d(e)", new MemberCall(8, new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4))), new Ident("d", 7), List.of(new Ident("e", 9))));
    assertAst("a.b(c)[d].e(f)", new MemberCall(11, new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4))).index(new Ident("d", 7)).withSourceIndex(7), new Ident("e", 10), List.of(new Ident("f", 12))));
    assertAst("a.b.Type{field: 1}", new Struct("a.b.Type", List.of(new Entry<>(new Ident("field", 9), value(1L).withSourceIndex(16), false, 14)), 8));
    assertAst("Type{field: 1}.field", new Struct("Type", List.of(new Entry<>(new Ident("field", 5), value(1L).withSourceIndex(12), false, 10)), 4).select(new Ident("field", 15)).withSourceIndex(14));
    assertAst("a.b.Type{field: 1}.field", new Struct("a.b.Type", List.of(new Entry<>(new Ident("field", 9), value(1L).withSourceIndex(16), false, 14)), 8).select(new Ident("field", 19)).withSourceIndex(18));
    assertAst("Type{field: 1}[0]", new Struct("Type", List.of(new Entry<>(new Ident("field", 5), value(1L).withSourceIndex(12), false, 10)), 4).index(value(0L).withSourceIndex(15)).withSourceIndex(15));
  }

  @Test
  public void testComplexOptionalSyntax() throws Exception {
    assertAst("a.?b.c", new Ident("a", 0).optionalSelect(new Ident("b", 3)).withSourceIndex(1).select(new Ident("c", 5)).withSourceIndex(4));
    assertAst("a.b.?c", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1).optionalSelect(new Ident("c", 5)).withSourceIndex(3));
    assertAst("a[?b][c]", new Ident("a", 0).optionalIndex(new Ident("b", 3)).withSourceIndex(0).index(new Ident("c", 6)).withSourceIndex(6));
    assertAst("a[b][?c]", new Ident("a", 0).index(new Ident("b", 2)).withSourceIndex(2).optionalIndex(new Ident("c", 6)).withSourceIndex(2));
    assertAst("a.?b[?c]", new Ident("a", 0).optionalSelect(new Ident("b", 3)).withSourceIndex(1).optionalIndex(new Ident("c", 6)).withSourceIndex(1));
  }

  @Test
  public void testComplexStructures() throws Exception {
    assertAst("[[1, 2], [3, 4]]", new ListOf(List.of(new Element(new ListOf(List.of(new Element(value(1L).withSourceIndex(2), false), new Element(value(2L).withSourceIndex(5), false)), 1), false), new Element(new ListOf(List.of(new Element(value(3L).withSourceIndex(10), false), new Element(value(4L).withSourceIndex(13), false)), 9), false)), 0));
    assertAst("{'a': {'b': 1}}", new MapOf(List.of(new Entry<>(string("a").withSourceIndex(1), new MapOf(List.of(new Entry<>(string("b").withSourceIndex(7), value(1L).withSourceIndex(12), false, 10)), 6), false, 4)), 0));
    assertAst("[{'a': 1}, {'b': 2}]", new ListOf(List.of(new Element(new MapOf(List.of(new Entry<>(string("a").withSourceIndex(2), value(1L).withSourceIndex(7), false, 5)), 1), false), new Element(new MapOf(List.of(new Entry<>(string("b").withSourceIndex(12), value(2L).withSourceIndex(17), false, 15)), 11), false)), 0));
    assertAst("[?a, b, ?c]", new ListOf(List.of(new Element(new Ident("a", 2), true), new Element(new Ident("b", 5), false), new Element(new Ident("c", 9), true)), 0));
    assertAst("{?a: b, ?c: d}", new MapOf(List.of(new Entry<>(new Ident("a", 2), new Ident("b", 5), true, 3), new Entry<>(new Ident("c", 9), new Ident("d", 12), true, 10)), 0));
    assertAst("Type{?field: value, field2: value}", new Struct("Type", List.of(new Entry<>(new Ident("field", 6), new Ident("value", 13), true, 11), new Entry<>(new Ident("field2", 20), new Ident("value", 28), false, 26)), 4));
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
    assertAst("\"hello \\a \\b \\f \\n \\r \\t \\v \\? \\` \\' \\\" \\\\\"", string("hello \u0007 \u0008 \u000c \n \r \t \u000b ? ` ' \" \\").withSourceIndex(0));
    assertAst("\"\\377\"", string("\u00ff").withSourceIndex(0));
    assertAst("\"\\000\"", string("\u0000").withSourceIndex(0));
    assertAst("\"\\U0000270c\"", string("\u270c").withSourceIndex(0));
  }

  @Test
  public void testComments() throws Exception {
    assertAstWithComments("1 + 2 // comment", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAstWithComments("1 + // comment\n 2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(16)).withSourceIndex(2));
    assertAstWithComments("// comment\n 1 + 2", value(1L).withSourceIndex(12).add(value(2L).withSourceIndex(16)).withSourceIndex(14));
    assertAstWithComments("1 // comment 1\n + 2 // comment 2", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(18)).withSourceIndex(16));
  }

  @Test
  public void testMapNonStringKeys() throws Exception {
    assertAst("{1: 'a', 2u: 'b', true: 'c'}", new MapOf(List.of(new Entry<>(value(1L).withSourceIndex(1), string("a").withSourceIndex(4), false, 2), new Entry<>(unsigned(2L).withSourceIndex(9), string("b").withSourceIndex(13), false, 11), new Entry<>(value(true).withSourceIndex(18), string("c").withSourceIndex(24), false, 22)), 0));
    assertAst("{1.0: 'a'}", new MapOf(List.of(new Entry<>(value(1.0).withSourceIndex(1), string("a").withSourceIndex(6), false, 4)), 0));
  }

  @Test
  public void testKeywordsAsFields() throws Exception {
    assertAst("a.`in`", new Ident("a", 0).select(new Ident("in", 2)).withSourceIndex(1));
    assertAst("a.` b`", new Ident("a", 0).select(new Ident(" b", 2)).withSourceIndex(1));
    assertAst("a.`b `", new Ident("a", 0).select(new Ident("b ", 2)).withSourceIndex(1));
    assertAst("a.` b `", new Ident("a", 0).select(new Ident(" b ", 2)).withSourceIndex(1));
  }

  @Test
  public void testComplexPrecedenceMixed() throws Exception {
    assertAst("a ? b + c : d * e", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 4).add(new Ident("c", 8)).withSourceIndex(6), new Ident("d", 12).multiply(new Ident("e", 16)).withSourceIndex(14), 2));
    assertAst("a || b && c == d + e", new Ident("a", 0).or(new Ident("b", 5).and(new Ident("c", 10).equalTo(new Ident("d", 15).add(new Ident("e", 19)).withSourceIndex(17)).withSourceIndex(12)).withSourceIndex(7)).withSourceIndex(2));
    assertAst("!a && -b", not(new Ident("a", 1)).withSourceIndex(0).and(negative(new Ident("b", 7)).withSourceIndex(6)).withSourceIndex(3));
    assertAst("a < b && c >= d || e == f", new Ident("a", 0).lessThan(new Ident("b", 4)).withSourceIndex(2).and(new Ident("c", 9).atLeast(new Ident("d", 14)).withSourceIndex(11)).withSourceIndex(6).or(new Ident("e", 19).equalTo(new Ident("f", 24)).withSourceIndex(21)).withSourceIndex(16));
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
    assertAst("x * 2", new Ident("x", 0).multiply(value(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("x * 2u", new Ident("x", 0).multiply(unsigned(2L).withSourceIndex(4)).withSourceIndex(2));
    assertAst("4--4", value(4L).withSourceIndex(0).subtract(value(-4L).withSourceIndex(2)).withSourceIndex(1));
    assertAst("a + b", new Ident("a", 0).add(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a - b", new Ident("a", 0).subtract(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a * b", new Ident("a", 0).multiply(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a / b", new Ident("a", 0).divide(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a % b", new Ident("a", 0).modulo(new Ident("b", 4)).withSourceIndex(2));
    assertAst("b\"abc\" + B\"def\"", bytes(new byte[] {(byte) 97, (byte) 98, (byte) 99}).withSourceIndex(0).add(bytes(new byte[] {(byte) 100, (byte) 101, (byte) 102}).withSourceIndex(9)).withSourceIndex(7));
  }

  @Test
  public void testCppSuite_valid_memberOperations() {
    assertAst("x * 2.0", new Ident("x", 0).multiply(value(2.0).withSourceIndex(4)).withSourceIndex(2));
    assertAst("a.b(5)", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(value(5L).withSourceIndex(4))));
    assertAst("4--4.1", value(4L).withSourceIndex(0).subtract(value(-4.1).withSourceIndex(2)).withSourceIndex(1));
    assertAst("23.39", value(23.39).withSourceIndex(0));
    assertAst("a.b", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1));
    assertAst("a.b.c", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1).select(new Ident("c", 4)).withSourceIndex(3));
    assertAst("(a)", new Ident("a", 1));
    assertAst("((a))", new Ident("a", 2));
    assertAst("a()", new FunctionCall(new Ident("a", 0), List.of(), 1));
    assertAst("a(b)", new FunctionCall(new Ident("a", 0), List.of(new Ident("b", 2)), 1));
    assertAst("a(b, c)", new FunctionCall(new Ident("a", 0), List.of(new Ident("b", 2), new Ident("c", 5)), 1));
    assertAst("a.b()", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of()));
    assertAst("a.b(c)", new MemberCall(3, new Ident("a", 0), new Ident("b", 2), List.of(new Ident("c", 4))));
    assertAst("aaa.bbb(ccc)", new MemberCall(7, new Ident("aaa", 0), new Ident("bbb", 4), List.of(new Ident("ccc", 8))));
    assertAst("has(m.f)", new Has(3, new Ident("m", 4).select(new Ident("f", 6)).withSourceIndex(5)));
    assertAst("x.single_nested_message != null", new Ident("x", 0).select(new Ident("single_nested_message", 2)).withSourceIndex(1).notEqualTo(new NullValue(27)).withSourceIndex(24));
    assertAst("a.`b`", new Ident("a", 0).select(new Ident("b", 2)).withSourceIndex(1));
    assertAst("a.`b-c`", new Ident("a", 0).select(new Ident("b-c", 2)).withSourceIndex(1));
    assertAst("a.`b c`", new Ident("a", 0).select(new Ident("b c", 2)).withSourceIndex(1));
    assertAst("a.`b/c`", new Ident("a", 0).select(new Ident("b/c", 2)).withSourceIndex(1));
    assertAst("a.`b.c`", new Ident("a", 0).select(new Ident("b.c", 2)).withSourceIndex(1));
    assertAst("a.`in`", new Ident("a", 0).select(new Ident("in", 2)).withSourceIndex(1));
    assertAst("has(a.`b/c`)", new Has(3, new Ident("a", 4).select(new Ident("b/c", 6)).withSourceIndex(5)));
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
    assertAst("a", new Ident("a", 0));
    assertAst("\"\\\"\"", string("\"").withSourceIndex(0));
    assertAst("\"abc\" + \"def\"", string("abc").withSourceIndex(0).add(string("def").withSourceIndex(8)).withSourceIndex(6));
    assertAst("\"\\xC3\\XBF\"", string("\u00c3\u00bf").withSourceIndex(0));
    assertAst("\"\\303\\277\"", string("\u00c3\u00bf").withSourceIndex(0));
    assertAst("\"hi\\u263A \\u263Athere\"", string("hi\u263a \u263athere").withSourceIndex(0));
    assertAst("\"\\U000003A8\\?\"", string("\u03a8?").withSourceIndex(0));
    assertAst("\"\\a\\b\\f\\n\\r\\t\\v'\\\"\\\\\\? Legal escapes\"", string("\u0007\u0008\u000c\n\r\t\u000b'\"\\? Legal escapes").withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_unaryOperators() {
    assertAst("! false", not(value(false).withSourceIndex(2)).withSourceIndex(0));
    assertAst("-a", negative(new Ident("a", 1)).withSourceIndex(0));
    assertAst("-0xA", value(-10L).withSourceIndex(0));
    assertAst("-1", value(-1L).withSourceIndex(0));
    assertAst("!a", not(new Ident("a", 1)).withSourceIndex(0));
    assertAst("---a", negative(negative(negative(new Ident("a", 3)).withSourceIndex(2)).withSourceIndex(1)).withSourceIndex(0));
  }

  @Test
  public void testCppSuite_valid_creators() {
    assertAst("a[3]", new Ident("a", 0).index(value(3L).withSourceIndex(2)).withSourceIndex(2));
    assertAst("SomeMessage{foo: 5, bar: \"xyz\"}", new Struct("SomeMessage", List.of(new Entry<>(new Ident("foo", 12), value(5L).withSourceIndex(17), false, 15), new Entry<>(new Ident("bar", 20), string("xyz").withSourceIndex(25), false, 23)), 11));
    assertAst("[3, 4, 5]", new ListOf(List.of(new Element(value(3L).withSourceIndex(1), false), new Element(value(4L).withSourceIndex(4), false), new Element(value(5L).withSourceIndex(7), false)), 0));
    assertAst("{foo: 5, bar: \"xyz\"}", new MapOf(List.of(new Entry<>(new Ident("foo", 1), value(5L).withSourceIndex(6), false, 4), new Entry<>(new Ident("bar", 9), string("xyz").withSourceIndex(14), false, 12)), 0));
    assertAst("a[b]", new Ident("a", 0).index(new Ident("b", 2)).withSourceIndex(2));
    assertAst("foo{ }", new Struct("foo", List.of(), 3));
    assertAst("foo{ a:b }", new Struct("foo", List.of(new Entry<>(new Ident("a", 5), new Ident("b", 7), false, 6)), 3));
    assertAst("foo{ a:b, c:d }", new Struct("foo", List.of(new Entry<>(new Ident("a", 5), new Ident("b", 7), false, 6), new Entry<>(new Ident("c", 10), new Ident("d", 12), false, 11)), 3));
    assertAst("{}", new MapOf(List.of(), 0));
    assertAst("{a:b, c:d}", new MapOf(List.of(new Entry<>(new Ident("a", 1), new Ident("b", 3), false, 2), new Entry<>(new Ident("c", 6), new Ident("d", 8), false, 7)), 0));
    assertAst("[]", new ListOf(List.of(), 0));
    assertAst("[a]", new ListOf(List.of(new Element(new Ident("a", 1), false)), 0));
    assertAst("[a, b, c]", new ListOf(List.of(new Element(new Ident("a", 1), false), new Element(new Ident("b", 4), false), new Element(new Ident("c", 7), false)), 0));
    assertAst("[] + [1,2,3,] + [4]", new ListOf(List.of(), 0).add(new ListOf(List.of(new Element(value(1L).withSourceIndex(6), false), new Element(value(2L).withSourceIndex(8), false), new Element(value(3L).withSourceIndex(10), false)), 5)).withSourceIndex(3).add(new ListOf(List.of(new Element(value(4L).withSourceIndex(17), false)), 16)).withSourceIndex(14));
    assertAst("{1:2u, 2:3u}", new MapOf(List.of(new Entry<>(value(1L).withSourceIndex(1), unsigned(2L).withSourceIndex(3), false, 2), new Entry<>(value(2L).withSourceIndex(7), unsigned(3L).withSourceIndex(9), false, 8)), 0));
    assertAst("TestAllTypes{single_int32: 1, single_int64: 2}", new Struct("TestAllTypes", List.of(new Entry<>(new Ident("single_int32", 13), value(1L).withSourceIndex(27), false, 25), new Entry<>(new Ident("single_int64", 30), value(2L).withSourceIndex(44), false, 42)), 12));
    assertAst("[1,3,4][0]", new ListOf(List.of(new Element(value(1L).withSourceIndex(1), false), new Element(value(3L).withSourceIndex(3), false), new Element(value(4L).withSourceIndex(5), false)), 0).index(value(0L).withSourceIndex(8)).withSourceIndex(8));
    assertAst("x[\"a\"].single_int32 == 23", new Ident("x", 0).index(string("a").withSourceIndex(2)).withSourceIndex(2).select(new Ident("single_int32", 7)).withSourceIndex(6).equalTo(value(23L).withSourceIndex(23)).withSourceIndex(20));
    assertAst("'😁' in ['😁', '😑', '😦']", string("\ud83d\ude01").withSourceIndex(0).in(new ListOf(List.of(new Element(string("\ud83d\ude01").withSourceIndex(9), false), new Element(string("\ud83d\ude11").withSourceIndex(15), false), new Element(string("\ud83d\ude26").withSourceIndex(21), false)), 8)).withSourceIndex(5));
    assertAst("'\\u00ff' in ['\\u00ff', '\\u00ff', '\\u00ff']", string("\u00ff").withSourceIndex(0).in(new ListOf(List.of(new Element(string("\u00ff").withSourceIndex(13), false), new Element(string("\u00ff").withSourceIndex(23), false), new Element(string("\u00ff").withSourceIndex(33), false)), 12)).withSourceIndex(9));
    assertAst("'\\u00ff' in ['\\uffff', '\\U00100000', '\\U0010ffff']", string("\u00ff").withSourceIndex(0).in(new ListOf(List.of(new Element(string("\uffff").withSourceIndex(13), false), new Element(string("\udbc0\udc00").withSourceIndex(23), false), new Element(string("\udbff\udfff").withSourceIndex(37), false)), 12)).withSourceIndex(9));
    assertAst("'\\u00ff' in ['\\U00100000', '\\uffff', '\\U0010ffff']", string("\u00ff").withSourceIndex(0).in(new ListOf(List.of(new Element(string("\udbc0\udc00").withSourceIndex(13), false), new Element(string("\uffff").withSourceIndex(27), false), new Element(string("\udbff\udfff").withSourceIndex(37), false)), 12)).withSourceIndex(9));
    assertAst("A{`b`: 1}", new Struct("A", List.of(new Entry<>(new Ident("b", 2), value(1L).withSourceIndex(7), false, 5)), 1));
    assertAst("A{`b-c`: 1}", new Struct("A", List.of(new Entry<>(new Ident("b-c", 2), value(1L).withSourceIndex(9), false, 7)), 1));
    assertAst("A{`b c`: 1}", new Struct("A", List.of(new Entry<>(new Ident("b c", 2), value(1L).withSourceIndex(9), false, 7)), 1));
    assertAst("A{`b/c`: 1}", new Struct("A", List.of(new Entry<>(new Ident("b/c", 2), value(1L).withSourceIndex(9), false, 7)), 1));
    assertAst("A{`b.c`: 1}", new Struct("A", List.of(new Entry<>(new Ident("b.c", 2), value(1L).withSourceIndex(9), false, 7)), 1));
    assertAst("A{`in`: 1}", new Struct("A", List.of(new Entry<>(new Ident("in", 2), value(1L).withSourceIndex(8), false, 6)), 1));
    assertAst("{?'key': value}", new MapOf(List.of(new Entry<>(string("key").withSourceIndex(2), new Ident("value", 9), true, 7)), 0));
  }

  @Test
  public void testCppSuite_valid_logical() {
    assertAst("a > 5 && a < 10", new Ident("a", 0).greaterThan(value(5L).withSourceIndex(4)).withSourceIndex(2).and(new Ident("a", 9).lessThan(value(10L).withSourceIndex(13)).withSourceIndex(11)).withSourceIndex(6));
    assertAst("a < 5 || a > 10", new Ident("a", 0).lessThan(value(5L).withSourceIndex(4)).withSourceIndex(2).or(new Ident("a", 9).greaterThan(value(10L).withSourceIndex(13)).withSourceIndex(11)).withSourceIndex(6));
    assertAst("a || b", new Ident("a", 0).or(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a && b", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2));
  }

  @Test
  public void testCppSuite_valid_ternary() {
    assertAst("a?b:c", new CelExpr.IfElse(new Ident("a", 0), new Ident("b", 2), new Ident("c", 4), 1));
    assertAst("false && !true || false ? 2 : 3", new CelExpr.IfElse(value(false).withSourceIndex(0).and(not(value(true).withSourceIndex(10)).withSourceIndex(9)).withSourceIndex(6).or(value(false).withSourceIndex(18)).withSourceIndex(15), value(2L).withSourceIndex(26), value(3L).withSourceIndex(30), 24));
  }

  @Test
  public void testCppSuite_valid_relations() {
    assertAst("a in b", new Ident("a", 0).in(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a == b", new Ident("a", 0).equalTo(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a != b", new Ident("a", 0).notEqualTo(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a > b", new Ident("a", 0).greaterThan(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a >= b", new Ident("a", 0).atLeast(new Ident("b", 5)).withSourceIndex(2));
    assertAst("a < b", new Ident("a", 0).lessThan(new Ident("b", 4)).withSourceIndex(2));
    assertAst("a <= b", new Ident("a", 0).atMost(new Ident("b", 5)).withSourceIndex(2));
    assertAst("1 + 2 * 3 - 1 / 2 == 6 % 1", value(1L).withSourceIndex(0).add(value(2L).withSourceIndex(4).multiply(value(3L).withSourceIndex(8)).withSourceIndex(6)).withSourceIndex(2).subtract(value(1L).withSourceIndex(12).divide(value(2L).withSourceIndex(16)).withSourceIndex(14)).withSourceIndex(10).equalTo(value(6L).withSourceIndex(21).modulo(value(1L).withSourceIndex(25)).withSourceIndex(23)).withSourceIndex(18));
    assertAst("a <= b <= c", new Ident("a", 0).atMost(new Ident("b", 5)).withSourceIndex(2).atMost(new Ident("c", 10)).withSourceIndex(7));
  }

  @Test
  public void testCppSuite_valid_macros() {
    assertAst("m.exists_one(v, f)", new ExistsOne(new Ident("m", 0), new Ident("v", 13), new Ident("f", 16), 12));
    assertAst("m.map(v, f)", new Map(new Ident("m", 0), new Ident("v", 6), new Ident("f", 9), 5));
    assertAst("m.map(v, p, f)", new FilterMap(new Ident("m", 0), new Ident("v", 6), new Ident("p", 9), new Ident("f", 12), 5));
    assertAst("m.filter(v, p)", new Filter(new Ident("m", 0), new Ident("v", 9), new Ident("p", 12), 8));
    assertAst("size(x) == x.size()", new FunctionCall(new Ident("size", 0), List.of(new Ident("x", 5)), 4).equalTo(new MemberCall(17, new Ident("x", 11), new Ident("size", 13), List.of())).withSourceIndex(8));
    assertAst("x.filter(y, y.filter(z, z > 0))", new Filter(new Ident("x", 0), new Ident("y", 9), new Filter(new Ident("y", 12), new Ident("z", 21), new Ident("z", 24).greaterThan(value(0L).withSourceIndex(28)).withSourceIndex(26), 20), 8));
    assertAst("has(a.b).filter(c, c)", new Filter(new Has(3, new Ident("a", 4).select(new Ident("b", 6)).withSourceIndex(5)), new Ident("c", 16), new Ident("c", 19), 15));
    assertAst("x.filter(y, y.exists(z, has(z.a)) && y.exists(z, has(z.b)))", new Filter(new Ident("x", 0), new Ident("y", 9), new Exists(new Ident("y", 12), new Ident("z", 21), new Has(27, new Ident("z", 28).select(new Ident("a", 30)).withSourceIndex(29)), 20).and(new Exists(new Ident("y", 37), new Ident("z", 46), new Has(52, new Ident("z", 53).select(new Ident("b", 55)).withSourceIndex(54)), 45)).withSourceIndex(34), 8));
    assertAst("has(a.b).asList().exists(c, c)", new Exists(new MemberCall(15, new Has(3, new Ident("a", 4).select(new Ident("b", 6)).withSourceIndex(5)), new Ident("asList", 9), List.of()), new Ident("c", 25), new Ident("c", 28), 24));
    assertAst("m.all(x, x > 0)", new All(new Ident("m", 0), new Ident("x", 6), new Ident("x", 9).greaterThan(value(0L).withSourceIndex(13)).withSourceIndex(11), 5));
    assertAst("[1, 2].exists(x, x > 0)", new Exists(new ListOf(List.of(new Element(value(1L).withSourceIndex(1), false), new Element(value(2L).withSourceIndex(4), false)), 0), new Ident("x", 14), new Ident("x", 17).greaterThan(value(0L).withSourceIndex(21)).withSourceIndex(19), 13));
    assertAst("{\"a\": 1}.exists(x, x == 'a')", new Exists(new MapOf(List.of(new Entry<>(string("a").withSourceIndex(1), value(1L).withSourceIndex(6), false, 4)), 0), new Ident("x", 16), new Ident("x", 19).equalTo(string("a").withSourceIndex(24)).withSourceIndex(21), 15));
    assertAst("exists(x, y)", new FunctionCall(new Ident("exists", 0), List.of(new Ident("x", 7), new Ident("y", 10)), 6));
    assertAst("all(x, y)", new FunctionCall(new Ident("all", 0), List.of(new Ident("x", 4), new Ident("y", 7)), 3));
    assertAst("map(x, y)", new FunctionCall(new Ident("map", 0), List.of(new Ident("x", 4), new Ident("y", 7)), 3));
    assertAst("filter(x, y)", new FunctionCall(new Ident("filter", 0), List.of(new Ident("x", 7), new Ident("y", 10)), 6));
    assertAst("exists_one(x, y)", new FunctionCall(new Ident("exists_one", 0), List.of(new Ident("x", 11), new Ident("y", 14)), 10));
    assertParseFailure("has(x)", "1:1", "has() expects 1 select argument");
    assertParseFailure("has(x, y)", "1:1", "has() expects 1 arg, 2 provided");
    assertParseFailure("has()", "1:1", "has() expects 1 arg, 0 provided");
    assertAst("has(a.b.c)", new Has(3, new Ident("a", 4).select(new Ident("b", 6)).withSourceIndex(5).select(new Ident("c", 8)).withSourceIndex(7)));
  }

  @Test
  public void testCppSuite_valid_optionalSyntax() {
    assertAst("a.?b[?0] && a[?c]", new Ident("a", 0).optionalSelect(new Ident("b", 3)).withSourceIndex(1).optionalIndex(value(0L).withSourceIndex(6)).withSourceIndex(1).and(new Ident("a", 12).optionalIndex(new Ident("c", 15)).withSourceIndex(12)).withSourceIndex(9));
    assertAst("[?a, ?b]", new ListOf(List.of(new Element(new Ident("a", 2), true), new Element(new Ident("b", 6), true)), 0));
    assertAst("[?a[?b]]", new ListOf(List.of(new Element(new Ident("a", 2).optionalIndex(new Ident("b", 5)).withSourceIndex(2), true)), 0));
    assertAst("Msg{?field: value}", new Struct("Msg", List.of(new Entry<>(new Ident("field", 5), new Ident("value", 12), true, 10)), 3));
    assertAst("m.optMap(v, f)", new MemberCall(8, new Ident("m", 0), new Ident("optMap", 2), List.of(new Ident("v", 9), new Ident("f", 12))));
    assertAst("m.optFlatMap(v, f)", new MemberCall(12, new Ident("m", 0), new Ident("optFlatMap", 2), List.of(new Ident("v", 13), new Ident("f", 16))));
  }

  @Test
  public void testCppSuite_valid_logicalChaining() {
    assertAst("a || b || c || d || e || f", new Ident("a", 0).or(new Ident("b", 5)).withSourceIndex(2).or(new Ident("c", 10)).withSourceIndex(7).or(new Ident("d", 15).or(new Ident("e", 20)).withSourceIndex(17).or(new Ident("f", 25)).withSourceIndex(22)).withSourceIndex(12));
    assertAst("a && b && c && d && e && f && g", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2).and(new Ident("c", 10).and(new Ident("d", 15)).withSourceIndex(12)).withSourceIndex(7).and(new Ident("e", 20).and(new Ident("f", 25)).withSourceIndex(22).and(new Ident("g", 30)).withSourceIndex(27)).withSourceIndex(17));
    assertAst("a && b && c && d || e && f && g && h", new Ident("a", 0).and(new Ident("b", 5)).withSourceIndex(2).and(new Ident("c", 10).and(new Ident("d", 15)).withSourceIndex(12)).withSourceIndex(7).or(new Ident("e", 20).and(new Ident("f", 25)).withSourceIndex(22).and(new Ident("g", 30).and(new Ident("h", 35)).withSourceIndex(32)).withSourceIndex(27)).withSourceIndex(17));
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
    if (!ast.equals(expectedAst)) {
      System.out.println("AST_MISMATCH_START: " + expression);
      System.out.println("  EXPECTED: " + toJavaCode(expectedAst));
      System.out.println("  ACTUAL  : " + toJavaCode(ast));
      System.out.println("AST_MISMATCH_END");
    }
    assertThat(ast).isEqualTo(expectedAst);
  }

  private void assertAstWithComments(String expression, CelExpr expectedAst) {
    CelExpr ast = parser.withComments().parse(expression);
    if (!ast.equals(expectedAst)) {
      System.out.println("AST_MISMATCH_START: " + expression + " (with comments)");
      System.out.println("  EXPECTED: " + toJavaCode(expectedAst));
      System.out.println("  ACTUAL  : " + toJavaCode(ast));
      System.out.println("AST_MISMATCH_END");
    }
    assertThat(ast).isEqualTo(expectedAst);
  }

  private static CelExpr clearSourceIndices(CelExpr expr) {
    return expr;
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

  @Test
  public void rewriteExpectedASTs() throws Exception {
    String path = "/Users/benyu/mug/mug-cel/src/test/java/com/google/mu/cel/CelParserTest.java";
    String content =
        java.nio.file.Files.readString(
            java.nio.file.Paths.get(path), java.nio.charset.StandardCharsets.UTF_8);
    int helpersStart = content.indexOf("private void assertAst(");
    int index = 0;
    while (true) {
      int assertStart = content.indexOf("assertAst(", index);
      int assertWithCommentsStart = content.indexOf("assertAstWithComments(", index);
      if (assertStart == -1 && assertWithCommentsStart == -1) {
        break;
      }
      boolean withComments = false;
      int start = assertStart;
      if (assertStart == -1
          || (assertWithCommentsStart != -1 && assertWithCommentsStart < assertStart)) {
        start = assertWithCommentsStart;
        withComments = true;
      }
      if (start >= helpersStart) {
        index = start + 1;
        continue;
      }
      int nameLength = withComments ? "assertAstWithComments".length() : "assertAst".length();
      int parenStart = content.indexOf("(", start + nameLength - 1);
      int firstCharIndex = parenStart + 1;
      while (Character.isWhitespace(content.charAt(firstCharIndex))) {
        firstCharIndex++;
      }
      if (content.charAt(firstCharIndex) != '"') {
        index = start + 1;
        continue;
      }
      int parenEnd = findMatchingParen(content, parenStart);
      int callEnd = content.indexOf(";", parenEnd);

      int exprStart = content.indexOf("\"", parenStart);
      int exprEnd = findEndOfStringLiteral(content, exprStart);
      String rawExpr = content.substring(exprStart, exprEnd + 1);
      String expression = rawExpr.substring(1, rawExpr.length() - 1).translateEscapes();

      CelParser p = withComments ? parser.withComments() : parser;
      CelExpr ast = p.parse(expression);
      String newCall =
          (withComments ? "assertAstWithComments(" : "assertAst(")
              + rawExpr
              + ", "
              + toJavaCode(ast)
              + ");";

      content = content.substring(0, start) + newCall + content.substring(callEnd + 1);
      index = start + newCall.length();
      helpersStart = content.indexOf("private void assertAst(");
    }
    java.nio.file.Files.writeString(
        java.nio.file.Paths.get(path), content, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static int findMatchingParen(String s, int start) {
    int balance = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
      } else {
        if (c == '"') {
          inString = true;
        } else if (c == '(') {
          balance++;
        } else if (c == ')') {
          balance--;
          if (balance == 0) {
            return i;
          }
        }
      }
    }
    return -1;
  }

  private static int findEndOfStringLiteral(String s, int start) {
    boolean escaped = false;
    for (int i = start + 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else if (c == '"') {
        return i;
      }
    }
    return -1;
  }

  private static String toJavaCode(CelExpr expr) {
    if (expr == null) {
      return "null";
    }
    return switch (expr) {
      case CelExpr.NullValue v -> "new NullValue(" + v.sourceIndex() + ")";
      case CelExpr.BoolValue v ->
          "value(" + v.value() + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.LongValue v ->
          "value(" + v.value() + "L).withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.UintValue v ->
          "unsigned(" + v.value() + "L).withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.DoubleValue v ->
          "value(" + v.value() + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.StringValue v ->
          "string(" + escapeString(v.value()) + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.BytesValue v ->
          "bytes(" + bytesToJavaLiteral(v.value()) + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.Ident v -> "new Ident(\"" + v.name() + "\", " + v.sourceIndex() + ")";
      case CelExpr.Select v ->
          toJavaCode(v.operand())
              + ".select("
              + toJavaCode(v.field())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.OptionalSelect v ->
          toJavaCode(v.operand())
              + ".optionalSelect("
              + toJavaCode(v.field())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Index v ->
          toJavaCode(v.operand())
              + ".index("
              + toJavaCode(v.index())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.OptionalIndex v ->
          toJavaCode(v.operand())
              + ".optionalIndex("
              + toJavaCode(v.index())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Not v ->
          "not(" + toJavaCode(v.operand()) + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.Negative v ->
          "negative(" + toJavaCode(v.operand()) + ").withSourceIndex(" + v.sourceIndex() + ")";
      case CelExpr.Add v ->
          toJavaCode(v.left())
              + ".add("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Subtract v ->
          toJavaCode(v.left())
              + ".subtract("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Multiply v ->
          toJavaCode(v.left())
              + ".multiply("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Divide v ->
          toJavaCode(v.left())
              + ".divide("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Modulo v ->
          toJavaCode(v.left())
              + ".modulo("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.LessThan v ->
          toJavaCode(v.left())
              + ".lessThan("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.LessThanOrEqualTo v ->
          toJavaCode(v.left())
              + ".atMost("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.GreaterThan v ->
          toJavaCode(v.left())
              + ".greaterThan("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.GreaterThanOrEqualTo v ->
          toJavaCode(v.left())
              + ".atLeast("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.EqualTo v ->
          toJavaCode(v.left())
              + ".equalTo("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.NotEqualTo v ->
          toJavaCode(v.left())
              + ".notEqualTo("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.And v ->
          toJavaCode(v.left())
              + ".and("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.Or v ->
          toJavaCode(v.left())
              + ".or("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.In v ->
          toJavaCode(v.left())
              + ".in("
              + toJavaCode(v.right())
              + ").withSourceIndex("
              + v.sourceIndex()
              + ")";
      case CelExpr.IfElse v ->
          "new CelExpr.IfElse("
              + toJavaCode(v.condition())
              + ", "
              + toJavaCode(v.ifTrue())
              + ", "
              + toJavaCode(v.ifFalse())
              + ", "
              + v.sourceIndex()
              + ")";
      case CelExpr.FunctionCall v ->
          "new FunctionCall("
              + toJavaCode(v.function())
              + ", "
              + toJavaList(v.args())
              + ", "
              + v.sourceIndex()
              + ")";
      case CelExpr.MemberCall v ->
          "new MemberCall("
              + v.sourceIndex()
              + ", "
              + toJavaCode(v.target())
              + ", "
              + toJavaCode(v.member())
              + ", "
              + toJavaList(v.args())
              + ")";
      case CelExpr.ListOf v ->
          "new ListOf(" + toJavaElements(v.elements()) + ", " + v.sourceIndex() + ")";
      case CelExpr.MapOf v ->
          "new MapOf(" + toJavaEntries(v.entries()) + ", " + v.sourceIndex() + ")";
      case CelExpr.Struct v ->
          "new Struct(\""
              + v.messageName()
              + "\", "
              + toJavaEntries(v.fields())
              + ", "
              + v.sourceIndex()
              + ")";
      case CelExpr.Macro m ->
          switch (m) {
            case CelExpr.Macro.Has v ->
                "new Has(" + v.sourceIndex() + ", " + toJavaCode(v.member()) + ")";
            case CelExpr.Macro.All v ->
                "new All("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.condition())
                    + ", "
                    + v.sourceIndex()
                    + ")";
            case CelExpr.Macro.Exists v ->
                "new Exists("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.condition())
                    + ", "
                    + v.sourceIndex()
                    + ")";
            case CelExpr.Macro.ExistsOne v ->
                "new ExistsOne("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.condition())
                    + ", "
                    + v.sourceIndex()
                    + ")";
            case CelExpr.Macro.Filter v ->
                "new Filter("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.expr())
                    + ", "
                    + v.sourceIndex()
                    + ")";
            case CelExpr.Macro.Map v ->
                "new Map("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.expr())
                    + ", "
                    + v.sourceIndex()
                    + ")";
            case CelExpr.Macro.FilterMap v ->
                "new FilterMap("
                    + toJavaCode(v.target())
                    + ", "
                    + toJavaCode(v.var())
                    + ", "
                    + toJavaCode(v.filter())
                    + ", "
                    + toJavaCode(v.transform())
                    + ", "
                    + v.sourceIndex()
                    + ")";
          };
      case CelExpr.ListLiteral v -> throw new UnsupportedOperationException();
      case CelExpr.MapLiteral v -> throw new UnsupportedOperationException();
      case CelExpr.StructLiteral v -> throw new UnsupportedOperationException();
    };
  }

  private static String escapeString(String s) {
    StringBuilder sb = new StringBuilder();
    sb.append("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        sb.append("\\\"");
      } else if (c == '\\') {
        sb.append("\\\\");
      } else if (c == '\n') {
        sb.append("\\n");
      } else if (c == '\r') {
        sb.append("\\r");
      } else if (c == '\t') {
        sb.append("\\t");
      } else if (c >= 32 && c <= 126) {
        sb.append(c);
      } else {
        sb.append(String.format("\\u%04x", (int) c));
      }
    }
    sb.append("\"");
    return sb.toString();
  }

  private static String bytesToJavaLiteral(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    sb.append("new byte[] {");
    for (int i = 0; i < bytes.length; i++) {
      if (i > 0) sb.append(", ");
      sb.append("(byte) ").append(bytes[i]);
    }
    sb.append("}");
    return sb.toString();
  }

  private static String toJavaList(List<CelExpr> list) {
    return "List.of("
        + list.stream()
            .map(CelParserTest::toJavaCode)
            .collect(java.util.stream.Collectors.joining(", "))
        + ")";
  }

  private static String toJavaElements(List<CelExpr.Element> list) {
    return "List.of("
        + list.stream()
            .map(e -> "new Element(" + toJavaCode(e.value()) + ", " + e.optional() + ")")
            .collect(java.util.stream.Collectors.joining(", "))
        + ")";
  }

  private static String toJavaEntries(List<? extends CelExpr.Entry<?>> list) {
    return "List.of("
        + list.stream()
            .map(
                e -> {
                  String keyStr =
                      (e.key() instanceof CelExpr)
                          ? toJavaCode((CelExpr) e.key())
                          : "new Ident(\""
                              + ((CelExpr.Ident) e.key()).name()
                              + "\", "
                              + ((CelExpr.Ident) e.key()).sourceIndex()
                              + ")";
                  return "new Entry<>("
                      + keyStr
                      + ", "
                      + toJavaCode(e.value())
                      + ", "
                      + e.optional()
                      + ", "
                      + e.sourceIndex()
                      + ")";
                })
            .collect(java.util.stream.Collectors.joining(", "))
        + ")";
  }

  private void assertParseFailure(
      String expression, String expectedPosition, String expectedMessageSubstring) {
    ParseException ex = assertThrows(ParseException.class, () -> parser.parse(expression));
    assertThat(ex.getMessage()).contains("at " + expectedPosition + ":");
    assertThat(ex.getMessage()).contains(expectedMessageSubstring);
  }
}

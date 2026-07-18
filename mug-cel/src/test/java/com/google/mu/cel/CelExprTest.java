package com.google.mu.cel;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import org.junit.Test;

public class CelExprTest {

  @Test
  public void literalExpr_nullValue() {
    CelExpr expr = new CelExpr.NullValue();
    assertThat(expr).isInstanceOf(CelExpr.NullValue.class);
    assertThat(expr.toString()).isEqualTo("null");
  }

  @Test
  public void literalExpr_boolValue() {
    CelExpr.BoolValue expr = new CelExpr.BoolValue(true);
    assertThat(expr.value()).isTrue();
    assertThat(expr.toString()).isEqualTo("true");

    assertThat(new CelExpr.BoolValue(false).toString()).isEqualTo("false");
  }

  @Test
  public void literalExpr_longValue() {
    CelExpr.LongValue expr = new CelExpr.LongValue(42L);
    assertThat(expr.value()).isEqualTo(42L);
    assertThat(expr.toString()).isEqualTo("42");
  }

  @Test
  public void literalExpr_uintValue() {
    CelExpr.UintValue expr = new CelExpr.UintValue(42L);
    assertThat(expr.value()).isEqualTo(42L);
    assertThat(expr.toString()).isEqualTo("42u");
  }

  @Test
  public void literalExpr_uintValue_overflow() {
    CelExpr.UintValue expr = new CelExpr.UintValue(-1L);
    assertThat(expr.toString()).isEqualTo("18446744073709551615u");
  }

  @Test
  public void literalExpr_doubleValue() {
    CelExpr.DoubleValue expr = new CelExpr.DoubleValue(3.14);
    assertThat(expr.value()).isEqualTo(3.14);
    assertThat(expr.toString()).isEqualTo("3.14");
  }

  @Test
  public void literalExpr_stringValue() {
    CelExpr.StringValue expr = new CelExpr.StringValue("hello\n\"world\"");
    assertThat(expr.value()).isEqualTo("hello\n\"world\"");
    assertThat(expr.toString()).isEqualTo("\"hello\\n\\\"world\\\"\"");
  }

  @Test
  public void literalExpr_stringValue_allEscapes() {
    // Tests all escape branches in escapeString: \, ", \n, \r, \t, and normal text
    CelExpr.StringValue expr = new CelExpr.StringValue("a\\b\"c\nd\re\tf");
    assertThat(expr.toString()).isEqualTo("\"a\\\\b\\\"c\\nd\\re\\tf\"");
  }

  @Test
  public void literalExpr_bytesValue() {
    byte[] bytes = new byte[] {0x01, 0x0a};
    CelExpr.BytesValue expr = new CelExpr.BytesValue(bytes);
    assertThat(expr.value()).isEqualTo(bytes);
    assertThat(expr.toString()).isEqualTo("b\"\\x01\\x0a\"");
  }

  @Test
  public void identExpr() {
    CelExpr.Ident expr = new CelExpr.Ident("varName");
    assertThat(expr.name()).isEqualTo("varName");
    assertThat(expr.toString()).isEqualTo("varName");
  }

  @Test
  public void selectExpr() {
    CelExpr operand = new CelExpr.Ident("operand");
    CelExpr.Select expr = new CelExpr.Select(operand, "field");
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.field()).isEqualTo("field");
    assertThat(expr.toString()).isEqualTo("(operand).field");
  }

  @Test
  public void indexExpr() {
    CelExpr operand = new CelExpr.Ident("operand");
    CelExpr index = new CelExpr.LongValue(0L);
    CelExpr.Index expr = new CelExpr.Index(operand, index);
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.index()).isEqualTo(index);
    assertThat(expr.toString()).isEqualTo("(operand)[0]");
  }

  @Test
  public void unaryExpr() {
    CelExpr operand = new CelExpr.Ident("x");
    CelExpr.Unary expr = new CelExpr.Unary(CelExpr.Unary.Op.NEGATIVE, operand);
    assertThat(expr.operator()).isEqualTo(CelExpr.Unary.Op.NEGATIVE);
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.toString()).isEqualTo("-(x)");

    assertThat(new CelExpr.Unary(CelExpr.Unary.Op.NOT, operand).toString()).isEqualTo("!(x)");
  }

  @Test
  public void binaryExpr() {
    CelExpr left = new CelExpr.Ident("x");
    CelExpr right = new CelExpr.LongValue(10L);
    CelExpr.Binary expr = new CelExpr.Binary(left, CelExpr.Binary.Op.ADD, right);
    assertThat(expr.left()).isEqualTo(left);
    assertThat(expr.operator()).isEqualTo(CelExpr.Binary.Op.ADD);
    assertThat(expr.right()).isEqualTo(right);
    assertThat(expr.toString()).isEqualTo("(x) + (10)");
  }

  @Test
  public void ternaryExpr() {
    CelExpr condition = new CelExpr.Ident("cond");
    CelExpr trueExpr = new CelExpr.StringValue("yes");
    CelExpr falseExpr = new CelExpr.StringValue("no");
    CelExpr.Ternary expr = new CelExpr.Ternary(condition, trueExpr, falseExpr);
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.ifTrue()).isEqualTo(trueExpr);
    assertThat(expr.ifFalse()).isEqualTo(falseExpr);
    assertThat(expr.toString()).isEqualTo("(cond) ? (\"yes\") : (\"no\")");
  }

  @Test
  public void memberCallExpr() {
    CelExpr target = new CelExpr.Ident("target");
    CelExpr arg = new CelExpr.LongValue(100);
    CelExpr.MemberCall expr =
        new CelExpr.MemberCall(target, new CelExpr.Ident("method"), List.of(arg));
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.member().name()).isEqualTo("method");
    assertThat(expr.args()).containsExactly(arg);
    assertThat(expr.toString()).isEqualTo("(target).method(100)");
  }

  @Test
  public void functionCallExpr_multipleArgs() {
    CelExpr arg1 = new CelExpr.Ident("x");
    CelExpr arg2 = new CelExpr.LongValue(42L);
    CelExpr.FunctionCall expr =
        new CelExpr.FunctionCall(new CelExpr.Ident("func"), List.of(arg1, arg2));
    // Verification of comma-separator logic in toString()
    assertThat(expr.toString()).isEqualTo("func(x, 42)");
  }

  @Test
  public void functionCallExpr_emptyArgs() {
    CelExpr.FunctionCall expr = new CelExpr.FunctionCall(new CelExpr.Ident("func"), List.of());
    assertThat(expr.toString()).isEqualTo("func()");
  }

  @Test
  public void createListExpr_singleOptional() {
    CelExpr elementExpr = new CelExpr.DoubleValue(1.5);
    CelExpr.ListLiteral.Element element = new CelExpr.ListLiteral.Element(elementExpr, true);
    CelExpr.ListLiteral expr = new CelExpr.ListLiteral(List.of(element));
    assertThat(expr.elements()).containsExactly(element);
    assertThat(element.value()).isEqualTo(elementExpr);
    assertThat(element.optional()).isTrue();
    assertThat(expr.toString()).isEqualTo("[?1.5]");
  }

  @Test
  public void createListExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional list elements
    CelExpr.ListLiteral.Element el1 =
        new CelExpr.ListLiteral.Element(new CelExpr.LongValue(1), false);
    CelExpr.ListLiteral.Element el2 = new CelExpr.ListLiteral.Element(new CelExpr.Ident("x"), true);
    CelExpr.ListLiteral expr = new CelExpr.ListLiteral(List.of(el1, el2));
    assertThat(expr.toString()).isEqualTo("[1, ?x]");
  }

  @Test
  public void createListExpr_empty() {
    CelExpr.ListLiteral expr = new CelExpr.ListLiteral(List.of());
    assertThat(expr.toString()).isEqualTo("[]");
  }

  @Test
  public void createMapExpr_singleOptional() {
    CelExpr key = new CelExpr.StringValue("key");
    CelExpr value = new CelExpr.LongValue(42L);
    CelExpr.Entry<CelExpr> entry = new CelExpr.Entry<>(key, value, true);
    CelExpr.MapLiteral expr = new CelExpr.MapLiteral(List.of(entry));
    assertThat(expr.entries()).containsExactly(entry);
    assertThat(entry.key()).isEqualTo(key);
    assertThat(entry.value()).isEqualTo(value);
    assertThat(entry.optional()).isTrue();
    assertThat(expr.toString()).isEqualTo("{?\"key\": 42}");
  }

  @Test
  public void createMapExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional map entries
    CelExpr.Entry<CelExpr> entry1 =
        new CelExpr.Entry<>(new CelExpr.StringValue("a"), new CelExpr.LongValue(1L), false);
    CelExpr.Entry<CelExpr> entry2 =
        new CelExpr.Entry<>(new CelExpr.Ident("x"), new CelExpr.Ident("y"), true);
    CelExpr.MapLiteral expr = new CelExpr.MapLiteral(List.of(entry1, entry2));
    assertThat(expr.toString()).isEqualTo("{\"a\": 1, ?x: y}");
  }

  @Test
  public void createMapExpr_empty() {
    CelExpr.MapLiteral expr = new CelExpr.MapLiteral(List.of());
    assertThat(expr.toString()).isEqualTo("{}");
  }

  @Test
  public void createStructExpr_single() {
    CelExpr value = new CelExpr.BoolValue(true);
    CelExpr.Entry<CelExpr.Ident> field =
        new CelExpr.Entry<>(new CelExpr.Ident("myField"), value, false);
    CelExpr.StructLiteral expr = new CelExpr.StructLiteral("MyMessage", List.of(field));
    assertThat(expr.messageName()).isEqualTo("MyMessage");
    assertThat(expr.fields()).containsExactly(field);
    assertThat(field.key()).isEqualTo(new CelExpr.Ident("myField"));
    assertThat(field.value()).isEqualTo(value);
    assertThat(field.optional()).isFalse();
    assertThat(expr.toString()).isEqualTo("MyMessage{myField: true}");
  }

  @Test
  public void createStructExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional struct fields
    CelExpr.Entry<CelExpr.Ident> field1 =
        new CelExpr.Entry<>(new CelExpr.Ident("a"), new CelExpr.LongValue(1L), false);
    CelExpr.Entry<CelExpr.Ident> field2 =
        new CelExpr.Entry<>(new CelExpr.Ident("b"), new CelExpr.Ident("x"), true);
    CelExpr.StructLiteral expr = new CelExpr.StructLiteral("MyMessage", List.of(field1, field2));
    assertThat(expr.toString()).isEqualTo("MyMessage{a: 1, ?b: x}");
  }

  @Test
  public void createStructExpr_empty() {
    CelExpr.StructLiteral expr = new CelExpr.StructLiteral("MyMessage", List.of());
    assertThat(expr.toString()).isEqualTo("MyMessage{}");
  }

  @Test
  public void toString_strictParenthesization() {
    CelExpr x = new CelExpr.Ident("x");
    CelExpr y = new CelExpr.Ident("y");
    CelExpr z = new CelExpr.Ident("z");

    // x + y * z -> (x) + ((y) * (z))
    CelExpr addMult =
        new CelExpr.Binary(
            x, CelExpr.Binary.Op.ADD, new CelExpr.Binary(y, CelExpr.Binary.Op.MULT, z));
    assertThat(addMult.toString()).isEqualTo("(x) + ((y) * (z))");

    // (x + y) * z -> ((x) + (y)) * (z)
    CelExpr addMultParens =
        new CelExpr.Binary(
            new CelExpr.Binary(x, CelExpr.Binary.Op.ADD, y), CelExpr.Binary.Op.MULT, z);
    assertThat(addMultParens.toString()).isEqualTo("((x) + (y)) * (z)");

    // x - y - z -> ((x) - (y)) - (z)
    CelExpr subLeft =
        new CelExpr.Binary(
            new CelExpr.Binary(x, CelExpr.Binary.Op.SUB, y), CelExpr.Binary.Op.SUB, z);
    assertThat(subLeft.toString()).isEqualTo("((x) - (y)) - (z)");

    // (x + y).field -> ((x) + (y)).field
    CelExpr selectParens =
        new CelExpr.Select(new CelExpr.Binary(x, CelExpr.Binary.Op.ADD, y), "field");
    assertThat(selectParens.toString()).isEqualTo("((x) + (y)).field");
  }

  @Test
  public void hasExpr() {
    CelExpr.Select select = new CelExpr.Select(new CelExpr.Ident("request"), "auth");
    CelExpr.Macro.Has expr = new CelExpr.Macro.Has(select);
    assertThat(expr.member()).isEqualTo(select);
    assertThat(expr.toString()).isEqualTo("has((request).auth)");
  }

  @Test
  public void allExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr condition =
        new CelExpr.Binary(new CelExpr.Ident("x"), CelExpr.Binary.Op.GT, new CelExpr.LongValue(0L));
    CelExpr.Macro.All expr = new CelExpr.Macro.All(target, "x", condition);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.all(x, (x) > (0))");
  }

  @Test
  public void existsExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr condition =
        new CelExpr.Binary(new CelExpr.Ident("x"), CelExpr.Binary.Op.GT, new CelExpr.LongValue(0L));
    CelExpr.Macro.Exists expr = new CelExpr.Macro.Exists(target, "x", condition);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.exists(x, (x) > (0))");
  }

  @Test
  public void existsOneExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr condition =
        new CelExpr.Binary(new CelExpr.Ident("x"), CelExpr.Binary.Op.GT, new CelExpr.LongValue(0L));
    CelExpr.Macro.ExistsOne expr = new CelExpr.Macro.ExistsOne(target, "x", condition);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.exists_one(x, (x) > (0))");
  }

  @Test
  public void filterExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr condition =
        new CelExpr.Binary(new CelExpr.Ident("x"), CelExpr.Binary.Op.GT, new CelExpr.LongValue(0L));
    CelExpr.Macro.Filter expr = new CelExpr.Macro.Filter(target, "x", condition);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.expr()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.filter(x, (x) > (0))");
  }

  @Test
  public void mapExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr transform =
        new CelExpr.Binary(
            new CelExpr.Ident("x"), CelExpr.Binary.Op.MULT, new CelExpr.LongValue(2L));
    CelExpr.Macro.Map expr = new CelExpr.Macro.Map(target, "x", transform);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.expr()).isEqualTo(transform);
    assertThat(expr.toString()).isEqualTo("users.map(x, (x) * (2))");
  }

  @Test
  public void filterMapExpr() {
    CelExpr target = new CelExpr.Ident("users");
    CelExpr filter =
        new CelExpr.Binary(new CelExpr.Ident("x"), CelExpr.Binary.Op.GT, new CelExpr.LongValue(0L));
    CelExpr transform =
        new CelExpr.Binary(
            new CelExpr.Ident("x"), CelExpr.Binary.Op.MULT, new CelExpr.LongValue(2L));
    CelExpr.Macro.FilterMap expr = new CelExpr.Macro.FilterMap(target, "x", filter, transform);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.varName()).isEqualTo("x");
    assertThat(expr.filter()).isEqualTo(filter);
    assertThat(expr.transform()).isEqualTo(transform);
    assertThat(expr.toString()).isEqualTo("users.map(x, (x) > (0), (x) * (2))");
  }
}

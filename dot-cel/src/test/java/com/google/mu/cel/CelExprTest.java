package com.google.mu.cel;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.NullPointerTester;
import com.google.mu.cel.CelExpr.Element;
import com.google.mu.cel.CelExpr.KeyedBy;
import com.google.mu.cel.CelExpr.Ident;
import com.google.mu.cel.CelExpr.Select;
import java.util.List;
import org.junit.Test;

public class CelExprTest {

  @Test
  public void literalExpr_nullValue() {
    CelExpr expr = new CelExpr.NullValue(0);
    assertThat(expr).isInstanceOf(CelExpr.NullValue.class);
    assertThat(expr.toString()).isEqualTo("null");
  }

  @Test
  public void literalExpr_boolValue() {
    CelExpr.BoolValue expr = new CelExpr.BoolValue(true, 0);
    assertThat(expr.value()).isTrue();
    assertThat(expr.toString()).isEqualTo("true");

    assertThat(new CelExpr.BoolValue(false, 0).toString()).isEqualTo("false");
  }

  @Test
  public void literalExpr_longValue() {
    CelExpr.LongValue expr = new CelExpr.LongValue(42L, 0);
    assertThat(expr.value()).isEqualTo(42L);
    assertThat(expr.toString()).isEqualTo("42");
  }

  @Test
  public void literalExpr_uintValue() {
    CelExpr.UintValue expr = new CelExpr.UintValue(42L, 0);
    assertThat(expr.value()).isEqualTo(42L);
    assertThat(expr.toString()).isEqualTo("42u");
  }

  @Test
  public void literalExpr_uintValue_overflow() {
    CelExpr.UintValue expr = new CelExpr.UintValue(-1L, 0);
    assertThat(expr.toString()).isEqualTo("18446744073709551615u");
  }

  @Test
  public void literalExpr_doubleValue() {
    CelExpr.DoubleValue expr = new CelExpr.DoubleValue(3.14, 0);
    assertThat(expr.value()).isEqualTo(3.14);
    assertThat(expr.toString()).isEqualTo("3.14");
  }

  @Test
  public void literalExpr_stringValue() {
    CelExpr.StringValue expr = new CelExpr.StringValue("hello\n\"world\"", 0);
    assertThat(expr.value()).isEqualTo("hello\n\"world\"");
    assertThat(expr.toString()).isEqualTo("\"hello\\n\\\"world\\\"\"");
  }

  @Test
  public void literalExpr_stringValue_allEscapes() {
    // Tests all escape branches in escapeString: \, ", \n, \r, \t, and normal text
    CelExpr.StringValue expr = new CelExpr.StringValue("a\\b\"c\nd\re\tf", 0);
    assertThat(expr.toString()).isEqualTo("\"a\\\\b\\\"c\\nd\\re\\tf\"");
  }

  @Test
  public void literalExpr_bytesValue() {
    byte[] bytes = new byte[] {0x01, 0x0a};
    CelExpr.BytesValue expr = new CelExpr.BytesValue(bytes, 0);
    assertThat(expr.value()).isEqualTo(bytes);
    assertThat(expr.toString()).isEqualTo("b\"\\x01\\x0a\"");
  }

  @Test
  public void identExpr() {
    CelExpr.Ident expr = new CelExpr.Ident("varName", 0);
    assertThat(expr.name()).isEqualTo("varName");
    assertThat(expr.toString()).isEqualTo("varName");
  }

  @Test
  public void selectExpr() {
    CelExpr operand = new CelExpr.Ident("operand", 0);
    CelExpr.Select expr = new CelExpr.Select(operand, new CelExpr.Ident("field", 0), 0);
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.field().name()).isEqualTo("field");
    assertThat(expr.toString()).isEqualTo("(operand).field");
  }

  @Test
  public void indexExpr() {
    CelExpr operand = new CelExpr.Ident("operand", 0);
    CelExpr index = new CelExpr.LongValue(0L, 0);
    CelExpr.Index expr = new CelExpr.Index(operand, index, 0);
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.index()).isEqualTo(index);
    assertThat(expr.toString()).isEqualTo("(operand)[0]");
  }

  @Test
  public void unaryExpr() {
    CelExpr operand = new CelExpr.Ident("x", 0);
    CelExpr.Negative expr = new CelExpr.Negative(operand, 0);
    assertThat(expr.operand()).isEqualTo(operand);
    assertThat(expr.toString()).isEqualTo("-(x)");

    assertThat(new CelExpr.Not(operand, 0).toString()).isEqualTo("!(x)");
  }

  @Test
  public void binaryExpr() {
    CelExpr left = new CelExpr.Ident("x", 0);
    CelExpr right = new CelExpr.LongValue(10L, 0);
    CelExpr.Add expr = new CelExpr.Add(left, right, 0);
    assertThat(expr.left()).isEqualTo(left);
    assertThat(expr.right()).isEqualTo(right);
    assertThat(expr.toString()).isEqualTo("(x) + (10)");
  }

  @Test
  public void ternaryExpr() {
    CelExpr condition = new CelExpr.Ident("cond", 0);
    CelExpr trueExpr = new CelExpr.StringValue("yes", 0);
    CelExpr falseExpr = new CelExpr.StringValue("no", 0);
    CelExpr.IfElse expr = new CelExpr.IfElse(condition, trueExpr, falseExpr, 0);
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.ifTrue()).isEqualTo(trueExpr);
    assertThat(expr.ifFalse()).isEqualTo(falseExpr);
    assertThat(expr.toString()).isEqualTo("(cond) ? (\"yes\") : (\"no\")");
  }

  @Test
  public void memberCallExpr() {
    CelExpr target = new CelExpr.Ident("target", 0);
    CelExpr arg = new CelExpr.LongValue(100L, 0);
    CelExpr.MemberCall expr =
        new CelExpr.MemberCall(target, new CelExpr.Ident("method", 0), List.of(arg), 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.member().name()).isEqualTo("method");
    assertThat(expr.args()).containsExactly(arg);
    assertThat(expr.toString()).isEqualTo("(target).method(100)");
  }

  @Test
  public void functionCallExpr_multipleArgs() {
    CelExpr arg1 = new CelExpr.Ident("x", 0);
    CelExpr arg2 = new CelExpr.LongValue(42L, 0);
    CelExpr.FunctionCall expr =
        new CelExpr.FunctionCall(new CelExpr.Ident("func", 0), List.of(arg1, arg2), 0);
    // Verification of comma-separator logic in toString()
    assertThat(expr.toString()).isEqualTo("func(x, 42)");
  }

  @Test
  public void functionCallExpr_emptyArgs() {
    CelExpr.FunctionCall expr =
        new CelExpr.FunctionCall(new CelExpr.Ident("func", 0), List.of(), 0);
    assertThat(expr.toString()).isEqualTo("func()");
  }

  @Test
  public void createListExpr_singleOptional() {
    CelExpr elementExpr = new CelExpr.DoubleValue(1.5, 0);
    CelExpr.Element element = new CelExpr.Element(elementExpr, true);
    CelExpr.ListOf expr = new CelExpr.ListOf(List.of(element), 0);
    assertThat(expr.elements()).containsExactly(element);
    assertThat(element.value()).isEqualTo(elementExpr);
    assertThat(element.isOptional()).isTrue();
    assertThat(expr.toString()).isEqualTo("[?1.5]");
  }

  @Test
  public void createListExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional list elements
    CelExpr.Element el1 = new CelExpr.Element(new CelExpr.LongValue(1L, 0), false);
    CelExpr.Element el2 = new CelExpr.Element(new CelExpr.Ident("x", 0), true);
    CelExpr.ListOf expr = new CelExpr.ListOf(List.of(el1, el2), 0);
    assertThat(expr.toString()).isEqualTo("[1, ?x]");
  }

  @Test
  public void createListExpr_empty() {
    CelExpr.ListOf expr = new CelExpr.ListOf(List.of(), 0);
    assertThat(expr.toString()).isEqualTo("[]");
  }

  @Test
  public void createMapExpr_singleOptional() {
    CelExpr key = new CelExpr.StringValue("key", 0);
    CelExpr value = new CelExpr.LongValue(42L, 0);
    CelExpr.KeyedBy<CelExpr> entry = new CelExpr.KeyedBy<>(key, value, true, 0);
    CelExpr.MapOf expr = new CelExpr.MapOf(List.of(entry), 0);
    assertThat(expr.entries()).containsExactly(entry);
    assertThat(entry.key()).isEqualTo(key);
    assertThat(entry.value()).isEqualTo(value);
    assertThat(entry.isOptional()).isTrue();
    assertThat(expr.toString()).isEqualTo("{?\"key\": 42}");
  }

  @Test
  public void createMapExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional map entries
    CelExpr.KeyedBy<CelExpr> entry1 =
        new CelExpr.KeyedBy<>(
            new CelExpr.StringValue("a", 0), new CelExpr.LongValue(1L, 0), false, 0);
    CelExpr.KeyedBy<CelExpr> entry2 =
        new CelExpr.KeyedBy<>(new CelExpr.Ident("x", 0), new CelExpr.Ident("y", 0), true, 0);
    CelExpr.MapOf expr = new CelExpr.MapOf(List.of(entry1, entry2), 0);
    assertThat(expr.toString()).isEqualTo("{\"a\": 1, ?x: y}");
  }

  @Test
  public void createMapExpr_empty() {
    CelExpr.MapOf expr = new CelExpr.MapOf(List.of(), 0);
    assertThat(expr.toString()).isEqualTo("{}");
  }

  @Test
  public void createStructExpr_single() {
    CelExpr value = new CelExpr.BoolValue(true, 0);
    CelExpr.KeyedBy<CelExpr.Ident> field =
        new CelExpr.KeyedBy<>(new CelExpr.Ident("myField", 0), value, false, 0);
    CelExpr.Struct expr = new CelExpr.Struct("MyMessage", List.of(field), 0);
    assertThat(expr.typeName()).isEqualTo("MyMessage");
    assertThat(expr.fields()).containsExactly(field);
    assertThat(field.key()).isEqualTo(new CelExpr.Ident("myField", 0));
    assertThat(field.value()).isEqualTo(value);
    assertThat(field.isOptional()).isFalse();
    assertThat(expr.toString()).isEqualTo("MyMessage{myField: true}");
  }

  @Test
  public void createStructExpr_multipleMix() {
    // Tests comma-separation and both optional/non-optional struct fields
    CelExpr.KeyedBy<CelExpr.Ident> field1 =
        new CelExpr.KeyedBy<>(new CelExpr.Ident("a", 0), new CelExpr.LongValue(1L, 0), false, 0);
    CelExpr.KeyedBy<CelExpr.Ident> field2 =
        new CelExpr.KeyedBy<>(new CelExpr.Ident("b", 0), new CelExpr.Ident("x", 0), true, 0);
    CelExpr.Struct expr = new CelExpr.Struct("MyMessage", List.of(field1, field2), 0);
    assertThat(expr.toString()).isEqualTo("MyMessage{a: 1, ?b: x}");
  }

  @Test
  public void createStructExpr_empty() {
    CelExpr.Struct expr = new CelExpr.Struct("MyMessage", List.of(), 0);
    assertThat(expr.toString()).isEqualTo("MyMessage{}");
  }

  @Test
  public void toString_strictParenthesization() {
    CelExpr x = new CelExpr.Ident("x", 0);
    CelExpr y = new CelExpr.Ident("y", 0);
    CelExpr z = new CelExpr.Ident("z", 0);

    // x + y * z -> (x) + ((y) * (z))
    CelExpr addMult = new CelExpr.Add(x, new CelExpr.Multiply(y, z, 0), 0);
    assertThat(addMult.toString()).isEqualTo("(x) + ((y) * (z))");

    // (x + y) * z -> ((x) + (y)) * (z)
    CelExpr addMultParens = new CelExpr.Multiply(new CelExpr.Add(x, y, 0), z, 0);
    assertThat(addMultParens.toString()).isEqualTo("((x) + (y)) * (z)");

    // x - y - z -> ((x) - (y)) - (z)
    CelExpr subLeft = new CelExpr.Subtract(new CelExpr.Subtract(x, y, 0), z, 0);
    assertThat(subLeft.toString()).isEqualTo("((x) - (y)) - (z)");

    // (x + y).field -> ((x) + (y)).field
    CelExpr selectParens =
        new CelExpr.Select(new CelExpr.Add(x, y, 0), new CelExpr.Ident("field", 0), 0);
    assertThat(selectParens.toString()).isEqualTo("((x) + (y)).field");
  }

  @Test
  public void hasExpr() {
    CelExpr.Select select =
        new CelExpr.Select(new CelExpr.Ident("request", 0), new CelExpr.Ident("auth", 0), 0);
    CelExpr.Macro.Has expr = new CelExpr.Macro.Has(select, 0);
    assertThat(expr.member()).isEqualTo(select);
    assertThat(expr.toString()).isEqualTo("has((request).auth)");
  }

  @Test
  public void allExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr condition = new CelExpr.GreaterThan(var, new CelExpr.LongValue(0L, 0), 0);
    CelExpr.Macro.All expr = new CelExpr.Macro.All(target, var, condition, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.all(x, (x) > (0))");
  }

  @Test
  public void existsExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr condition = new CelExpr.GreaterThan(var, new CelExpr.LongValue(0L, 0), 0);
    CelExpr.Macro.Exists expr = new CelExpr.Macro.Exists(target, var, condition, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.exists(x, (x) > (0))");
  }

  @Test
  public void existsOneExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr condition = new CelExpr.GreaterThan(var, new CelExpr.LongValue(0L, 0), 0);
    CelExpr.Macro.ExistsOne expr = new CelExpr.Macro.ExistsOne(target, var, condition, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.condition()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.exists_one(x, (x) > (0))");
  }

  @Test
  public void filterExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr condition = new CelExpr.GreaterThan(var, new CelExpr.LongValue(0L, 0), 0);
    CelExpr.Macro.Filter expr = new CelExpr.Macro.Filter(target, var, condition, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.expr()).isEqualTo(condition);
    assertThat(expr.toString()).isEqualTo("users.filter(x, (x) > (0))");
  }

  @Test
  public void mapExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr transform = new CelExpr.Multiply(var, new CelExpr.LongValue(2L, 0), 0);
    CelExpr.Macro.Map expr = new CelExpr.Macro.Map(target, var, transform, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.expr()).isEqualTo(transform);
    assertThat(expr.toString()).isEqualTo("users.map(x, (x) * (2))");
  }

  @Test
  public void filterMapExpr() {
    CelExpr target = new CelExpr.Ident("users", 0);
    CelExpr.Ident var = new CelExpr.Ident("x", 0);
    CelExpr filter = new CelExpr.GreaterThan(var, new CelExpr.LongValue(0L, 0), 0);
    CelExpr transform = new CelExpr.Multiply(var, new CelExpr.LongValue(2L, 0), 0);
    CelExpr.Macro.FilterMap expr = new CelExpr.Macro.FilterMap(target, var, filter, transform, 0);
    assertThat(expr.target()).isEqualTo(target);
    assertThat(expr.iterationVar()).isEqualTo(var);
    assertThat(expr.filter()).isEqualTo(filter);
    assertThat(expr.transform()).isEqualTo(transform);
    assertThat(expr.toString()).isEqualTo("users.map(x, (x) > (0), (x) * (2))");
  }

  @Test
  public void testNulls() {
    NullPointerTester tester = new NullPointerTester();
    tester.setDefault(CelExpr.class, CelExpr.string("v"));
    tester.setDefault(Ident.class, new Ident("v", 0));
    tester.setDefault(
        Select.class, new Select(CelExpr.string("v"), new Ident("f", 0)));
    tester.setDefault(Element.class, new Element(CelExpr.string("v"), false));
    tester.testAllPublicStaticMethods(CelExpr.class);
    tester.testAllPublicInstanceMethods(CelExpr.string("v"));
    for (Class<?> nestedClass : CelExpr.class.getDeclaredClasses()) {
      tester.testAllPublicConstructors(nestedClass);
      tester.testAllPublicStaticMethods(nestedClass);
    }
  }

  @Test
  public void literalExpr_roundtrip() {
    assertRoundtrip(new CelExpr.NullValue(0));
    assertRoundtrip(new CelExpr.BoolValue(true, 0));
    assertRoundtrip(new CelExpr.BoolValue(false, 0));
    assertRoundtrip(new CelExpr.LongValue(42L, 0));
    assertRoundtrip(new CelExpr.UintValue(42L, 0));
    assertRoundtrip(new CelExpr.UintValue(-1L, 0));
    assertRoundtrip(new CelExpr.DoubleValue(3.14, 0));
    assertRoundtrip(new CelExpr.StringValue("hello\n\"world\"", 0));
    assertRoundtrip(new CelExpr.StringValue("a\\b\"c\nd\re\tf", 0));
    assertRoundtrip(new CelExpr.BytesValue(new byte[] {0x01, 0x0a}, 0));
  }

  @Test
  public void identExpr_roundtrip() {
    assertRoundtrip(new CelExpr.Ident("varName", 0));
  }

  @Test
  public void selectExpr_roundtrip() {
    CelExpr operand = new CelExpr.Ident("operand", 0);
    assertRoundtrip(new CelExpr.Select(operand, new CelExpr.Ident("field", 0), 0));
  }

  @Test
  public void indexExpr_roundtrip() {
    CelExpr operand = new CelExpr.Ident("operand", 0);
    assertRoundtrip(new CelExpr.Index(operand, new CelExpr.LongValue(0L, 0), 0));
  }

  @Test
  public void unaryExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(new CelExpr.Negative(x, 0));
    assertRoundtrip(new CelExpr.Not(x, 0));
  }

  @Test
  public void binaryExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(new CelExpr.Add(x, new CelExpr.LongValue(10L, 0), 0));
  }

  @Test
  public void ternaryExpr_roundtrip() {
    assertRoundtrip(
        new CelExpr.IfElse(
            new CelExpr.Ident("cond", 0),
            new CelExpr.StringValue("yes", 0),
            new CelExpr.StringValue("no", 0),
            0));
  }

  @Test
  public void memberCallExpr_roundtrip() {
    CelExpr target = new CelExpr.Ident("target", 0);
    assertRoundtrip(
        new CelExpr.MemberCall(
            target, new CelExpr.Ident("method", 0), List.of(new CelExpr.LongValue(100L, 0)), 0));
  }

  @Test
  public void functionCallExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(
        new CelExpr.FunctionCall(
            new CelExpr.Ident("func", 0), List.of(x, new CelExpr.LongValue(42L, 0)), 0));
    assertRoundtrip(new CelExpr.FunctionCall(new CelExpr.Ident("func", 0), List.of(), 0));
  }

  @Test
  public void createListExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(
        new CelExpr.ListOf(List.of(new CelExpr.Element(new CelExpr.DoubleValue(1.5, 0), true)), 0));
    assertRoundtrip(
        new CelExpr.ListOf(
            List.of(
                new CelExpr.Element(new CelExpr.LongValue(1L, 0), false),
                new CelExpr.Element(x, true)),
            0));
    assertRoundtrip(new CelExpr.ListOf(List.of(), 0));
  }

  @Test
  public void createMapExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(
        new CelExpr.MapOf(
            List.of(
                new CelExpr.KeyedBy<>(
                    new CelExpr.StringValue("key", 0), new CelExpr.LongValue(42L, 0), true, 0)),
            0));
    assertRoundtrip(
        new CelExpr.MapOf(
            List.of(
                new CelExpr.KeyedBy<>(
                    new CelExpr.StringValue("a", 0), new CelExpr.LongValue(1L, 0), false, 0),
                new CelExpr.KeyedBy<>(x, new CelExpr.Ident("y", 0), true, 0)),
            0));
    assertRoundtrip(new CelExpr.MapOf(List.of(), 0));
  }

  @Test
  public void createStructExpr_roundtrip() {
    CelExpr x = new CelExpr.Ident("x", 0);
    assertRoundtrip(
        new CelExpr.Struct(
            "MyMessage",
            List.of(
                new CelExpr.KeyedBy<>(
                    new CelExpr.Ident("myField", 0), new CelExpr.BoolValue(true, 0), false, 0)),
            0));
    assertRoundtrip(
        new CelExpr.Struct(
            "MyMessage",
            List.of(
                new CelExpr.KeyedBy<>(
                    new CelExpr.Ident("a", 0), new CelExpr.LongValue(1L, 0), false, 0),
                new CelExpr.KeyedBy<>(new CelExpr.Ident("b", 0), x, true, 0)),
            0));
    assertRoundtrip(new CelExpr.Struct("MyMessage", List.of(), 0));
  }

  @Test
  public void parenthesizationExpr_roundtrip() {
    assertRoundtrip(CelExpr.of("x + y * z"));
    assertRoundtrip(CelExpr.of("(x + y) * z"));
    assertRoundtrip(CelExpr.of("x - y - z"));
    assertRoundtrip(CelExpr.of("(x + y).field"));
  }

  @Test
  public void macroHasExpr_roundtrip() {
    CelExpr.Select selectMember =
        new CelExpr.Select(new CelExpr.Ident("request", 0), new CelExpr.Ident("auth", 0), 0);
    assertRoundtrip(new CelExpr.Macro.Has(selectMember, 0));
  }

  @Test
  public void macroAllExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr condition = new CelExpr.GreaterThan(x, new CelExpr.LongValue(0L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.All(users, x, condition, 0));
  }

  @Test
  public void macroExistsExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr condition = new CelExpr.GreaterThan(x, new CelExpr.LongValue(0L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.Exists(users, x, condition, 0));
  }

  @Test
  public void macroExistsOneExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr condition = new CelExpr.GreaterThan(x, new CelExpr.LongValue(0L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.ExistsOne(users, x, condition, 0));
  }

  @Test
  public void macroFilterExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr condition = new CelExpr.GreaterThan(x, new CelExpr.LongValue(0L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.Filter(users, x, condition, 0));
  }

  @Test
  public void macroMapExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr transform = new CelExpr.Multiply(x, new CelExpr.LongValue(2L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.Map(users, x, transform, 0));
  }

  @Test
  public void macroFilterMapExpr_roundtrip() {
    CelExpr.Ident x = new CelExpr.Ident("x", 0);
    CelExpr users = new CelExpr.Ident("users", 0);
    CelExpr condition = new CelExpr.GreaterThan(x, new CelExpr.LongValue(0L, 0), 0);
    CelExpr transform = new CelExpr.Multiply(x, new CelExpr.LongValue(2L, 0), 0);
    assertRoundtrip(new CelExpr.Macro.FilterMap(users, x, condition, transform, 0));
  }

  private void assertRoundtrip(CelExpr expr) {
    String cel = expr.toString();
    assertThat(CelExpr.of(cel).toString()).isEqualTo(cel);
  }
}

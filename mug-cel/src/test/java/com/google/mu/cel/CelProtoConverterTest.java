package com.google.mu.cel;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CelProtoConverterTest {

  @Test
  public void testNullLiteral() {
    assertThat(CelExpr.of("null").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setNullValue(NullValue.NULL_VALUE))
                .build());
  }

  @Test
  public void testBoolLiteral() {
    assertThat(CelExpr.of("true").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setBoolValue(true))
                .build());
  }

  @Test
  public void testLongLiteral() {
    assertThat(CelExpr.of("42").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setInt64Value(42L))
                .build());
  }

  @Test
  public void testUintLiteral() {
    assertThat(CelExpr.of("42u").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setUint64Value(42L))
                .build());
  }

  @Test
  public void testDoubleLiteral() {
    assertThat(CelExpr.of("3.14").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setDoubleValue(3.14))
                .build());
  }

  @Test
  public void testStringLiteral() {
    assertThat(CelExpr.of("'hello'").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setStringValue("hello"))
                .build());
  }

  @Test
  public void testBytesLiteral() {
    assertThat(CelExpr.of("b'\\x01\\x02'").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(
                    Constant.newBuilder().setBytesValue(ByteString.copyFrom(new byte[] {1, 2})))
                .build());
  }

  @Test
  public void testIdent() {
    assertThat(CelExpr.of("my_var").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setIdentExpr(Expr.Ident.newBuilder().setName("my_var"))
                .build());
  }

  @Test
  public void testSelect() {
    assertThat(CelExpr.of("x.y").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setSelectExpr(
                    Expr.Select.newBuilder()
                        .setOperand(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("x")))
                        .setField("y"))
                .build());
  }

  @Test
  public void testIndex() {
    assertThat(CelExpr.of("arr[0]").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_[_]")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("arr")))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(0))))
                .build());
  }

  @Test
  public void testUnaryMinus() {
    assertThat(CelExpr.of("-(5)").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("-_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(5))))
                .build());
  }

  @Test
  public void testBinaryAdd() {
    assertThat(CelExpr.of("1 + 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_+_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testTernary() {
    assertThat(CelExpr.of("true ? 1 : 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_?_:_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setBoolValue(true)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(4)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testCallWithTarget() {
    assertThat(CelExpr.of("receiver.method(1)").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("method")
                        .setTarget(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("receiver")))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1))))
                .build());
  }

  @Test
  public void testOptionalSelectCall() {
    assertThat(CelExpr.of("obj.?field").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_?._")
                        .setTarget(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("obj")))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setStringValue("field"))))
                .build());
  }

  @Test
  public void testOptionalIndexCall() {
    assertThat(CelExpr.of("arr[?0]").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_[?_]")
                        .setTarget(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("arr")))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(0))))
                .build());
  }

  @Test
  public void testListLiteral() {
    assertThat(CelExpr.of("[1]").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setListExpr(
                    Expr.CreateList.newBuilder()
                        .addElements(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1))))
                .build());
  }

  @Test
  public void testListLiteralWithOptionalElement() {
    assertThat(CelExpr.of("[?1]").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setListExpr(
                    Expr.CreateList.newBuilder()
                        .addElements(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addOptionalIndices(0))
                .build());
  }

  @Test
  public void testMapLiteral() {
    assertThat(CelExpr.of("{'k': 1}").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    Expr.CreateStruct.newBuilder()
                        .addEntries(
                            Expr.CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setMapKey(
                                    Expr.newBuilder()
                                        .setId(3)
                                        .setConstExpr(Constant.newBuilder().setStringValue("k")))
                                .setValue(
                                    Expr.newBuilder()
                                        .setId(4)
                                        .setConstExpr(Constant.newBuilder().setInt64Value(1)))))
                .build());
  }

  @Test
  public void testMapLiteralWithOptionalEntry() {
    assertThat(CelExpr.of("{? 'k': 1}").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    Expr.CreateStruct.newBuilder()
                        .addEntries(
                            Expr.CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setMapKey(
                                    Expr.newBuilder()
                                        .setId(3)
                                        .setConstExpr(Constant.newBuilder().setStringValue("k")))
                                .setValue(
                                    Expr.newBuilder()
                                        .setId(4)
                                        .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                                .setOptionalEntry(true)))
                .build());
  }

  @Test
  public void testStructLiteral() {
    assertThat(CelExpr.of("MyMsg{f: 1}").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    Expr.CreateStruct.newBuilder()
                        .setMessageName("MyMsg")
                        .addEntries(
                            Expr.CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setFieldKey("f")
                                .setValue(
                                    Expr.newBuilder()
                                        .setId(3)
                                        .setConstExpr(Constant.newBuilder().setInt64Value(1)))))
                .build());
  }

  @Test
  public void testStructLiteralWithOptionalField() {
    assertThat(CelExpr.of("MyMsg{?f: 1}").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setStructExpr(
                    Expr.CreateStruct.newBuilder()
                        .setMessageName("MyMsg")
                        .addEntries(
                            Expr.CreateStruct.Entry.newBuilder()
                                .setId(2)
                                .setFieldKey("f")
                                .setValue(
                                    Expr.newBuilder()
                                        .setId(3)
                                        .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                                .setOptionalEntry(true)))
                .build());
  }

  @Test
  public void testMacroHas() {
    assertThat(CelExpr.of("has(x.y)").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setSelectExpr(
                    Expr.Select.newBuilder()
                        .setOperand(
                            Expr.newBuilder()
                                .setId(2)
                                .setIdentExpr(Expr.Ident.newBuilder().setName("x")))
                        .setField("y")
                        .setTestOnly(true))
                .build());
  }

  @Test
  public void testMacroAll() {
    CelExpr expr = CelExpr.of("list.all(v, true)");
    Expr proto = expr.toProto();
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("__result__");
    assertThat(comp.getAccuInit().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopCondition().getIdentExpr().getName()).isEqualTo("__result__");
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_&&_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("__result__");
  }

  @Test
  public void testMacroExists() {
    CelExpr expr = CelExpr.of("list.exists(v, true)");
    Expr proto = expr.toProto();
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("__result__");
    assertThat(comp.getAccuInit().getConstExpr().getBoolValue()).isFalse();
    assertThat(comp.getLoopCondition().getCallExpr().getFunction()).isEqualTo("!_");
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_||_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("__result__");
  }

  @Test
  public void testMacroExistsOne() {
    CelExpr expr = CelExpr.of("list.exists_one(v, true)");
    Expr proto = expr.toProto();
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("__result__");
    assertThat(comp.getAccuInit().getConstExpr().getBoolValue()).isFalse();
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_?_:_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("__result__");
  }

  @Test
  public void testMacroFilter() {
    CelExpr expr = CelExpr.of("list.filter(v, true)");
    Expr proto = expr.toProto();
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("__result__");
    assertThat(comp.getAccuInit().getListExpr().getElementsCount()).isEqualTo(0);
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_?_:_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("__result__");
  }

  @Test
  public void testMacroMap() {
    CelExpr expr = CelExpr.of("list.map(v, 1)");
    Expr proto = expr.toProto();
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("__result__");
    assertThat(comp.getAccuInit().getListExpr().getElementsCount()).isEqualTo(0);
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_+_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("__result__");
  }

  @Test
  public void testUnaryNot() {
    assertThat(CelExpr.of("!true").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("!_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setBoolValue(true))))
                .build());
  }

  @Test
  public void testBinarySubtract() {
    assertThat(CelExpr.of("1 - 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_-_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryMultiply() {
    assertThat(CelExpr.of("1 * 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_*_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryDivide() {
    assertThat(CelExpr.of("1 / 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_/_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryModulo() {
    assertThat(CelExpr.of("1 % 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_%_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryEqual() {
    assertThat(CelExpr.of("1 == 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_==_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryNotEqual() {
    assertThat(CelExpr.of("1 != 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_!=_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryLessThan() {
    assertThat(CelExpr.of("1 < 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_<_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryLessOrEqual() {
    assertThat(CelExpr.of("1 <= 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_<=_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryGreaterThan() {
    assertThat(CelExpr.of("1 > 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_>_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryGreaterOrEqual() {
    assertThat(CelExpr.of("1 >= 2").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_>=_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setInt64Value(2))))
                .build());
  }

  @Test
  public void testBinaryIn() {
    assertThat(CelExpr.of("1 in [2]").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("@in")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setInt64Value(1)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setListExpr(
                                    Expr.CreateList.newBuilder()
                                        .addElements(
                                            Expr.newBuilder()
                                                .setId(4)
                                                .setConstExpr(
                                                    Constant.newBuilder().setInt64Value(2))))))
                .build());
  }

  @Test
  public void testBinaryAnd() {
    assertThat(CelExpr.of("true && false").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_&&_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setBoolValue(true)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setBoolValue(false))))
                .build());
  }

  @Test
  public void testBinaryOr() {
    assertThat(CelExpr.of("true || false").toProto())
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_||_")
                        .addArgs(
                            Expr.newBuilder()
                                .setId(2)
                                .setConstExpr(Constant.newBuilder().setBoolValue(true)))
                        .addArgs(
                            Expr.newBuilder()
                                .setId(3)
                                .setConstExpr(Constant.newBuilder().setBoolValue(false))))
                .build());
  }

  @Test
  public void testDeterministicIds() {
    CelExpr expr = CelExpr.of("1 + 2");
    Expr proto1 = expr.toProto();
    Expr proto2 = expr.toProto();
    assertThat(proto1).isEqualTo(proto2);
  }
}

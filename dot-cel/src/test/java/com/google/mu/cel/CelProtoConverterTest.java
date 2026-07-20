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
    assertThat(CelProtoConverter.toProto(CelExpr.of("null")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setNullValue(NullValue.NULL_VALUE))
                .build());
  }

  @Test
  public void testBoolLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("true")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setBoolValue(true))
                .build());
  }

  @Test
  public void testLongLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("42")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setInt64Value(42L))
                .build());
  }

  @Test
  public void testUintLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("42u")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setUint64Value(42L))
                .build());
  }

  @Test
  public void testDoubleLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("3.14")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setDoubleValue(3.14))
                .build());
  }

  @Test
  public void testStringLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("'hello'")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(Constant.newBuilder().setStringValue("hello"))
                .build());
  }

  @Test
  public void testBytesLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("b'\\x01\\x02'")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setConstExpr(
                    Constant.newBuilder().setBytesValue(ByteString.copyFrom(new byte[] {1, 2})))
                .build());
  }

  @Test
  public void testIdent() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("my_var")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setIdentExpr(Expr.Ident.newBuilder().setName("my_var"))
                .build());
  }

  @Test
  public void testSelect() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("x.y")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("arr[0]")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("-(5)")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 + 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("true ? 1 : 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("receiver.method(1)")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("obj.?field")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_?._")
                        .addArgs(
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("arr[?0]")))
        .isEqualTo(
            Expr.newBuilder()
                .setId(1)
                .setCallExpr(
                    Expr.Call.newBuilder()
                        .setFunction("_[?_]")
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
  public void testListLiteral() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("[1]")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("[?1]")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("{'k': 1}")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("{? 'k': 1}")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("MyMsg{f: 1}")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("MyMsg{?f: 1}")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("has(x.y)")))
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
    Expr proto = CelProtoConverter.toProto(expr);
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("@result");
    assertThat(comp.getAccuInit().getConstExpr().getBoolValue()).isTrue();
    Expr loopCond = comp.getLoopCondition();
    assertThat(loopCond.getCallExpr().getFunction()).isEqualTo("@not_strictly_false");
    assertThat(loopCond.getCallExpr().getArgs(0).getIdentExpr().getName()).isEqualTo("@result");
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_&&_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("@result");
  }

  @Test
  public void testMacroExists() {
    CelExpr expr = CelExpr.of("list.exists(v, true)");
    Expr proto = CelProtoConverter.toProto(expr);
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("@result");
    assertThat(comp.getAccuInit().getConstExpr().getBoolValue()).isFalse();
    Expr loopCond = comp.getLoopCondition();
    assertThat(loopCond.getCallExpr().getFunction()).isEqualTo("@not_strictly_false");
    Expr notCall = loopCond.getCallExpr().getArgs(0);
    assertThat(notCall.getCallExpr().getFunction()).isEqualTo("!_");
    assertThat(notCall.getCallExpr().getArgs(0).getIdentExpr().getName()).isEqualTo("@result");
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_||_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("@result");
  }

  @Test
  public void testMacroExistsOne() {
    CelExpr expr = CelExpr.of("list.exists_one(v, true)");
    Expr proto = CelProtoConverter.toProto(expr);
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("@result");
    assertThat(comp.getAccuInit().getConstExpr().getInt64Value()).isEqualTo(0);
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_?_:_");
    assertThat(comp.getLoopStep().getCallExpr().getArgs(1).getCallExpr().getFunction())
        .isEqualTo("_+_");
    assertThat(comp.getResult().getCallExpr().getFunction()).isEqualTo("_==_");
    assertThat(comp.getResult().getCallExpr().getArgs(0).getIdentExpr().getName())
        .isEqualTo("@result");
    assertThat(comp.getResult().getCallExpr().getArgs(1).getConstExpr().getInt64Value())
        .isEqualTo(1);
  }

  @Test
  public void testMacroFilter() {
    CelExpr expr = CelExpr.of("list.filter(v, true)");
    Expr proto = CelProtoConverter.toProto(expr);
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("@result");
    assertThat(comp.getAccuInit().getListExpr().getElementsCount()).isEqualTo(0);
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_?_:_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("@result");
  }

  @Test
  public void testMacroMap() {
    CelExpr expr = CelExpr.of("list.map(v, 1)");
    Expr proto = CelProtoConverter.toProto(expr);
    assertThat(proto.hasComprehensionExpr()).isTrue();
    Expr.Comprehension comp = proto.getComprehensionExpr();
    assertThat(comp.getIterVar()).isEqualTo("v");
    assertThat(comp.getAccuVar()).isEqualTo("@result");
    assertThat(comp.getAccuInit().getListExpr().getElementsCount()).isEqualTo(0);
    assertThat(comp.getLoopCondition().getConstExpr().getBoolValue()).isTrue();
    assertThat(comp.getLoopStep().getCallExpr().getFunction()).isEqualTo("_+_");
    assertThat(comp.getResult().getIdentExpr().getName()).isEqualTo("@result");
  }

  @Test
  public void testUnaryNot() {
    assertThat(CelProtoConverter.toProto(CelExpr.of("!true")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 - 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 * 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 / 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 % 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 == 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 != 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 < 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 <= 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 > 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 >= 2")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("1 in [2]")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("true && false")))
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
    assertThat(CelProtoConverter.toProto(CelExpr.of("true || false")))
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
    Expr proto1 = CelProtoConverter.toProto(expr);
    Expr proto2 = CelProtoConverter.toProto(expr);
    assertThat(proto1).isEqualTo(proto2);
  }
}

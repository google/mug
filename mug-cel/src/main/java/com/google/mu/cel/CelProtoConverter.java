package com.google.mu.cel;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import java.util.concurrent.atomic.AtomicLong;

/** Helper to convert CelExpr to official CEL Protobuf AST. */
final class CelProtoConverter {
  static Expr toProto(CelExpr expr, AtomicLong idGenerator) {
    long id = idGenerator.getAndIncrement();
    Expr.Builder builder = Expr.newBuilder().setId(id);
    switch (expr) {
      case CelExpr.NullValue v ->
          builder.setConstExpr(Constant.newBuilder().setNullValue(NullValue.NULL_VALUE));
      case CelExpr.BoolValue v ->
          builder.setConstExpr(Constant.newBuilder().setBoolValue(v.value()));
      case CelExpr.LongValue v ->
          builder.setConstExpr(Constant.newBuilder().setInt64Value(v.value()));
      case CelExpr.UintValue v ->
          builder.setConstExpr(Constant.newBuilder().setUint64Value(v.value()));
      case CelExpr.DoubleValue v ->
          builder.setConstExpr(Constant.newBuilder().setDoubleValue(v.value()));
      case CelExpr.StringValue v ->
          builder.setConstExpr(Constant.newBuilder().setStringValue(v.value()));
      case CelExpr.BytesValue v ->
          builder.setConstExpr(Constant.newBuilder().setBytesValue(ByteString.copyFrom(v.value())));
      case CelExpr.Ident v ->
          builder.setIdentExpr(Expr.Ident.newBuilder().setName(v.name()));
      case CelExpr.Select v ->
          builder.setSelectExpr(
              Expr.Select.newBuilder()
                  .setOperand(toProto(v.operand(), idGenerator))
                  .setField(v.field()));
      case CelExpr.Index v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_[_]")
                  .addArgs(toProto(v.operand(), idGenerator))
                  .addArgs(toProto(v.index(), idGenerator)));
      case CelExpr.Unary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction(toOperatorName(v.operator()))
                  .addArgs(toProto(v.operand(), idGenerator)));
      case CelExpr.Binary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction(toOperatorName(v.operator()))
                  .addArgs(toProto(v.left(), idGenerator))
                  .addArgs(toProto(v.right(), idGenerator)));
      case CelExpr.Ternary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_?_:_")
                  .addArgs(toProto(v.condition(), idGenerator))
                  .addArgs(toProto(v.ifTrue(), idGenerator))
                  .addArgs(toProto(v.ifFalse(), idGenerator)));
      case CelExpr.Call v -> {
        String function = v.function();
        Expr.Call.Builder callBuilder = Expr.Call.newBuilder();
        if (v.target().isPresent() && function.equals("optionalSelect") && v.args().size() == 1) {
          callBuilder.setFunction("_?._")
              .setTarget(toProto(v.target().get(), idGenerator))
              .addArgs(toProto(v.args().get(0), idGenerator));
        } else if (v.target().isPresent() && function.equals("optionalIndex") && v.args().size() == 1) {
          callBuilder.setFunction("_[?_]")
              .setTarget(toProto(v.target().get(), idGenerator))
              .addArgs(toProto(v.args().get(0), idGenerator));
        } else {
          callBuilder.setFunction(function);
          v.target().ifPresent(t -> callBuilder.setTarget(toProto(t, idGenerator)));
          for (CelExpr arg : v.args()) {
            callBuilder.addArgs(toProto(arg, idGenerator));
          }
        }
        builder.setCallExpr(callBuilder);
      }
      case CelExpr.ListLiteral v -> {
        Expr.CreateList.Builder listBuilder = Expr.CreateList.newBuilder();
        for (int i = 0; i < v.elements().size(); i++) {
          CelExpr.Element elem = v.elements().get(i);
          listBuilder.addElements(toProto(elem.value(), idGenerator));
          if (elem.optional()) {
            listBuilder.addOptionalIndices(i);
          }
        }
        builder.setListExpr(listBuilder);
      }
      case CelExpr.MapLiteral v -> {
        Expr.CreateStruct.Builder structBuilder = Expr.CreateStruct.newBuilder();
        for (CelExpr.Entry<CelExpr> entry : v.entries()) {
          structBuilder.addEntries(
              Expr.CreateStruct.Entry.newBuilder()
                  .setId(idGenerator.getAndIncrement())
                  .setMapKey(toProto(entry.key(), idGenerator))
                  .setValue(toProto(entry.value(), idGenerator))
                  .setOptionalEntry(entry.optional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.StructLiteral v -> {
        Expr.CreateStruct.Builder structBuilder = Expr.CreateStruct.newBuilder().setMessageName(v.messageName());
        for (CelExpr.Entry<CelExpr.Ident> field : v.fields()) {
          structBuilder.addEntries(
              Expr.CreateStruct.Entry.newBuilder()
                  .setId(idGenerator.getAndIncrement())
                  .setFieldKey(field.key().name())
                  .setValue(toProto(field.value(), idGenerator))
                  .setOptionalEntry(field.optional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.Macro.Has v ->
          builder.setSelectExpr(
              Expr.Select.newBuilder()
                  .setOperand(toProto(v.member().operand(), idGenerator))
                  .setField(v.member().field())
                  .setTestOnly(true));
      case CelExpr.Macro v ->
          builder.setComprehensionExpr(toComprehensionProto(v, idGenerator));
    }
    return builder.build();
  }

  private static Expr.Comprehension toComprehensionProto(CelExpr.Macro macro, AtomicLong idGenerator) {
    return switch (macro) {
      case CelExpr.Macro.Has v -> throw new AssertionError("Macro.Has is handled separately");
      case CelExpr.Macro.All v -> toAllComprehension(v.target(), v.varName(), v.condition(), idGenerator);
      case CelExpr.Macro.Exists v -> toExistsComprehension(v.target(), v.varName(), v.condition(), idGenerator);
      case CelExpr.Macro.ExistsOne v -> toExistsOneComprehension(v.target(), v.varName(), v.condition(), idGenerator);
      case CelExpr.Macro.Filter v -> toFilterComprehension(v.target(), v.varName(), v.expr(), idGenerator);
      case CelExpr.Macro.Map v -> toMapComprehension(v.target(), v.varName(), v.expr(), idGenerator);
      case CelExpr.Macro.FilterMap v -> toFilterMapComprehension(v.target(), v.varName(), v.filter(), v.transform(), idGenerator);
    };
  }

  private static Expr.Comprehension toAllComprehension(
      CelExpr target, String varName, CelExpr condition, AtomicLong idGenerator) {
    long accuId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long condId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(condId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_&&_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(toProto(condition, idGenerator)))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static Expr.Comprehension toExistsComprehension(
      CelExpr target, String varName, CelExpr condition, AtomicLong idGenerator) {
    long notId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setConstExpr(Constant.newBuilder().setBoolValue(false))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(notId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("!_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_||_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId2)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(toProto(condition, idGenerator)))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static Expr.Comprehension toExistsOneComprehension(
      CelExpr target, String varName, CelExpr condition, AtomicLong idGenerator) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long ternaryInnerId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setConstExpr(Constant.newBuilder().setBoolValue(false))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr ternaryInner = Expr.newBuilder()
        .setId(ternaryInnerId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_?_:_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(
                    Expr.newBuilder()
                        .setId(idGenerator.getAndIncrement())
                        .setConstExpr(Constant.newBuilder().setBoolValue(false)))
                .addArgs(
                    Expr.newBuilder()
                        .setId(idGenerator.getAndIncrement())
                        .setConstExpr(Constant.newBuilder().setBoolValue(true))))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_?_:_")
                .addArgs(toProto(condition, idGenerator))
                .addArgs(ternaryInner)
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId2)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static Expr.Comprehension toFilterComprehension(
      CelExpr target, String varName, CelExpr expr, AtomicLong idGenerator) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long elementId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setListExpr(Expr.CreateList.newBuilder())
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr singleList = Expr.newBuilder()
        .setId(listId)
        .setListExpr(
            Expr.CreateList.newBuilder()
                .addElements(
                    Expr.newBuilder()
                        .setId(elementId)
                        .setIdentExpr(Expr.Ident.newBuilder().setName(varName))))
        .build();

    Expr addExpr = Expr.newBuilder()
        .setId(addId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_+_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(singleList))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_?_:_")
                .addArgs(toProto(expr, idGenerator))
                .addArgs(addExpr)
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId2)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static Expr.Comprehension toMapComprehension(
      CelExpr target, String varName, CelExpr expr, AtomicLong idGenerator) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long accuId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setListExpr(Expr.CreateList.newBuilder())
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr singleList = Expr.newBuilder()
        .setId(listId)
        .setListExpr(
            Expr.CreateList.newBuilder()
                .addElements(toProto(expr, idGenerator)))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_+_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(singleList))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static Expr.Comprehension toFilterMapComprehension(
      CelExpr target, String varName, CelExpr filter, CelExpr transform, AtomicLong idGenerator) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit = Expr.newBuilder()
        .setId(idGenerator.getAndIncrement())
        .setListExpr(Expr.CreateList.newBuilder())
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr singleList = Expr.newBuilder()
        .setId(listId)
        .setListExpr(
            Expr.CreateList.newBuilder()
                .addElements(toProto(transform, idGenerator)))
        .build();

    Expr addExpr = Expr.newBuilder()
        .setId(addId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_+_")
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                .addArgs(singleList))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(
            Expr.Call.newBuilder()
                .setFunction("_?_:_")
                .addArgs(toProto(filter, idGenerator))
                .addArgs(addExpr)
                .addArgs(
                    Expr.newBuilder()
                        .setId(accuId2)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
        .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(toProto(target, idGenerator))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static String toOperatorName(CelExpr.Unary.Op op) {
    return switch (op) {
      case NEGATIVE -> "-_";
      case NOT -> "!_";
    };
  }

  private static String toOperatorName(CelExpr.Binary.Op op) {
    return switch (op) {
      case ADD -> "_+_";
      case SUB -> "_-_";
      case MULT -> "_*_";
      case DIV -> "_/_";
      case MOD -> "_%_";
      case EQ -> "_==_";
      case NE -> "_!=_";
      case LT -> "_<_";
      case LE -> "_<=_";
      case GT -> "_>_";
      case GE -> "_>=_";
      case IN -> "@in";
      case AND -> "_&&_";
      case OR -> "_||_";
    };
  }
}

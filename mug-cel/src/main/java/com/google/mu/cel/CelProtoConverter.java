package com.google.mu.cel;

import static com.google.mu.util.Substring.all;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;

/** Helper to convert CelExpr to official CEL Protobuf AST. */
final class CelProtoConverter {
  static Expr toProto(CelExpr expr) {
    return new CelProtoConverter(new AtomicLong(1), SourceInfo.newBuilder()).convert(expr);
  }

  static ParsedExpr toParsedExpr(CelExpr celExpr, String input) {
    SourceInfo.Builder sourceInfo = SourceInfo.newBuilder();
    all('\n').match(input).forEach(m -> sourceInfo.addLineOffsets(m.index()));
    Expr expr = new CelProtoConverter(new AtomicLong(1), sourceInfo).convert(celExpr);
    return ParsedExpr.newBuilder().setExpr(expr).setSourceInfo(sourceInfo).build();
  }

  private final AtomicLong idGenerator;
  private final SourceInfo.Builder sourceInfo;

  private CelProtoConverter(AtomicLong idGenerator, SourceInfo.Builder sourceInfo) {
    this.idGenerator = idGenerator;
    this.sourceInfo = sourceInfo;
  }

  private Expr convert(CelExpr expr) {
    long id = idGenerator.getAndIncrement();
    sourceInfo.putPositions(id, expr.sourceIndex());
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
      case CelExpr.Ident v -> builder.setIdentExpr(Expr.Ident.newBuilder().setName(v.name()));
      case CelExpr.Select v ->
          builder.setSelectExpr(
              Expr.Select.newBuilder().setOperand(convert(v.operand())).setField(v.field()));
      case CelExpr.Index v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_[_]")
                  .addArgs(convert(v.operand()))
                  .addArgs(convert(v.index())));
      case CelExpr.OptionalSelect v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_?._")
                  .setTarget(convert(v.operand()))
                  .addArgs(convert(new CelExpr.StringValue(v.sourceIndex(), v.field()))));
      case CelExpr.OptionalIndex v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_[?_]")
                  .setTarget(convert(v.operand()))
                  .addArgs(convert(v.index())));
      case CelExpr.Unary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction(toOperatorName(v.operator()))
                  .addArgs(convert(v.operand())));
      case CelExpr.Binary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction(toOperatorName(v.operator()))
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Ternary v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_?_:_")
                  .addArgs(convert(v.condition()))
                  .addArgs(convert(v.ifTrue()))
                  .addArgs(convert(v.ifFalse())));
      case CelExpr.FunctionCall v -> {
        Expr.Call.Builder callBuilder = Expr.Call.newBuilder().setFunction(v.function().name());
        for (CelExpr arg : v.args()) {
          callBuilder.addArgs(convert(arg));
        }
        builder.setCallExpr(callBuilder);
      }
      case CelExpr.MemberCall v -> {
        Expr.Call.Builder callBuilder =
            Expr.Call.newBuilder().setFunction(v.member().name()).setTarget(convert(v.target()));
        for (CelExpr arg : v.args()) {
          callBuilder.addArgs(convert(arg));
        }
        builder.setCallExpr(callBuilder);
      }
      case CelExpr.ListLiteral v -> {
        Expr.CreateList.Builder listBuilder = Expr.CreateList.newBuilder();
        for (int i = 0; i < v.elements().size(); i++) {
          CelExpr.Element elem = v.elements().get(i);
          listBuilder.addElements(convert(elem.value()));
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
                  .setMapKey(convert(entry.key()))
                  .setValue(convert(entry.value()))
                  .setOptionalEntry(entry.optional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.StructLiteral v -> {
        Expr.CreateStruct.Builder structBuilder =
            Expr.CreateStruct.newBuilder().setMessageName(v.messageName());
        for (CelExpr.Entry<CelExpr.Ident> field : v.fields()) {
          structBuilder.addEntries(
              Expr.CreateStruct.Entry.newBuilder()
                  .setId(idGenerator.getAndIncrement())
                  .setFieldKey(field.key().name())
                  .setValue(convert(field.value()))
                  .setOptionalEntry(field.optional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.Macro.Has v -> {
        builder.setSelectExpr(
            Expr.Select.newBuilder()
                .setOperand(convert(v.member().operand()))
                .setField(v.member().field())
                .setTestOnly(true));
        Expr macroCall = convert(toMacroCall(v)).toBuilder().setId(id).build();
        sourceInfo.putMacroCalls(id, macroCall);
      }
      case CelExpr.Macro v -> {
        builder.setComprehensionExpr(toComprehensionProto(v));
        Expr macroCall = convert(toMacroCall(v)).toBuilder().setId(id).build();
        sourceInfo.putMacroCalls(id, macroCall);
      }
    }
    return builder.build();
  }

  private Expr.Comprehension toComprehensionProto(CelExpr.Macro macro) {
    return switch (macro) {
      case CelExpr.Macro.Has v -> throw new AssertionError("Macro.Has is handled separately");
      case CelExpr.Macro.All v -> toAllComprehension(v.target(), v.varName(), v.condition());
      case CelExpr.Macro.Exists v -> toExistsComprehension(v.target(), v.varName(), v.condition());
      case CelExpr.Macro.ExistsOne v ->
          toExistsOneComprehension(v.target(), v.varName(), v.condition());
      case CelExpr.Macro.Filter v -> toFilterComprehension(v.target(), v.varName(), v.expr());
      case CelExpr.Macro.Map v -> toMapComprehension(v.target(), v.varName(), v.expr());
      case CelExpr.Macro.FilterMap v ->
          toFilterMapComprehension(v.target(), v.varName(), v.filter(), v.transform());
    };
  }

  private Expr.Comprehension toAllComprehension(CelExpr target, String varName, CelExpr condition) {
    long accuId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long condId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(condId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_&&_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                    .addArgs(convert(condition)))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toExistsComprehension(
      CelExpr target, String varName, CelExpr condition) {
    long notId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setConstExpr(Constant.newBuilder().setBoolValue(false))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(notId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("!_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId1)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_||_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__")))
                    .addArgs(convert(condition)))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toExistsOneComprehension(
      CelExpr target, String varName, CelExpr condition) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long ternaryInnerId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setConstExpr(Constant.newBuilder().setBoolValue(false))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr ternaryInner =
        Expr.newBuilder()
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

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(convert(condition))
                    .addArgs(ternaryInner)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toFilterComprehension(CelExpr target, String varName, CelExpr expr) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long elementId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setListExpr(Expr.CreateList.newBuilder())
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr singleList =
        Expr.newBuilder()
            .setId(listId)
            .setListExpr(
                Expr.CreateList.newBuilder()
                    .addElements(
                        Expr.newBuilder()
                            .setId(elementId)
                            .setIdentExpr(Expr.Ident.newBuilder().setName(varName))))
            .build();

    Expr addExpr =
        Expr.newBuilder()
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

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(convert(expr))
                    .addArgs(addExpr)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toMapComprehension(CelExpr target, String varName, CelExpr expr) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long accuId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setListExpr(Expr.CreateList.newBuilder())
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr singleList =
        Expr.newBuilder()
            .setId(listId)
            .setListExpr(Expr.CreateList.newBuilder().addElements(convert(expr)))
            .build();

    Expr loopStep =
        Expr.newBuilder()
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

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toFilterMapComprehension(
      CelExpr target, String varName, CelExpr filter, CelExpr transform) {
    long loopCondId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long listId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    Expr accuInit =
        Expr.newBuilder()
            .setId(idGenerator.getAndIncrement())
            .setListExpr(Expr.CreateList.newBuilder())
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr singleList =
        Expr.newBuilder()
            .setId(listId)
            .setListExpr(Expr.CreateList.newBuilder().addElements(convert(transform)))
            .build();

    Expr addExpr =
        Expr.newBuilder()
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

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(convert(filter))
                    .addArgs(addExpr)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("__result__"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varName)
        .setIterRange(convert(target))
        .setAccuVar("__result__")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static CelExpr toMacroCall(CelExpr.Macro macro) {
    return switch (macro) {
      case CelExpr.Macro.Has v ->
          new CelExpr.FunctionCall(new CelExpr.Ident(v.sourceIndex(), "has"), List.of(v.member()));
      case CelExpr.Macro.All v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "all"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.condition()));
      case CelExpr.Macro.Exists v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "exists"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.condition()));
      case CelExpr.Macro.ExistsOne v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "exists_one"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.condition()));
      case CelExpr.Macro.Filter v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "filter"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.expr()));
      case CelExpr.Macro.Map v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "map"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.expr()));
      case CelExpr.Macro.FilterMap v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident(v.sourceIndex(), "map"),
              List.of(new CelExpr.Ident(v.sourceIndex(), v.varName()), v.filter(), v.transform()));
    };
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

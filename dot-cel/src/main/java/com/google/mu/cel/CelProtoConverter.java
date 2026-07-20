package com.google.mu.cel;

import static com.google.mu.util.Substring.all;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.google.mu.cel.CelExpr.Element;
import com.google.mu.cel.CelExpr.KeyedBy;
import com.google.mu.cel.CelExpr.Ident;
import java.util.concurrent.atomic.AtomicLong;

/** Helper to convert CelExpr to official CEL Protobuf AST. */
final class CelProtoConverter {
  static Expr toProto(CelExpr expr) {
    return new CelProtoConverter(new AtomicLong(1), SourceInfo.newBuilder(), null).convert(expr);
  }

  static ParsedExpr toParsedExpr(CelExpr celExpr, String input) {
    int[] charToCodePoint = precomputeCharToCodePoint(input);
    SourceInfo.Builder sourceInfo = SourceInfo.newBuilder();
    all('\n').match(input).forEach(m -> sourceInfo.addLineOffsets(charToCodePoint[m.index()] + 1));
    sourceInfo.addLineOffsets(charToCodePoint[input.length()] + 1);
    Expr expr =
        new CelProtoConverter(new AtomicLong(1), sourceInfo, charToCodePoint).convert(celExpr);
    return ParsedExpr.newBuilder().setExpr(expr).setSourceInfo(sourceInfo.build()).build();
  }

  private static int[] precomputeCharToCodePoint(String input) {
    int[] mapping = new int[input.length() + 1];
    int codePointIndex = 0;
    for (int charIndex = 0; charIndex < input.length(); ) {
      mapping[charIndex] = codePointIndex;
      int codePoint = input.codePointAt(charIndex);
      int count = Character.charCount(codePoint);
      for (int i = 1; i < count; i++) {
        mapping[charIndex + i] = codePointIndex;
      }
      charIndex += count;
      codePointIndex++;
    }
    mapping[input.length()] = codePointIndex;
    return mapping;
  }

  private final AtomicLong idGenerator;
  private final SourceInfo.Builder sourceInfo;
  private final int[] charToCodePoint;
  private final IdentityHashMap<CelExpr, Long> convertedIds = new IdentityHashMap<>();
  private final IdentityHashMap<CelExpr, Expr> convertedExprs = new IdentityHashMap<>();
  private final Set<CelExpr> shortCircuitedMacros =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private boolean isSerializingMacroCall = false;

  private CelProtoConverter(
      AtomicLong idGenerator, SourceInfo.Builder sourceInfo, int[] charToCodePoint) {
    this.idGenerator = idGenerator;
    this.sourceInfo = sourceInfo;
    this.charToCodePoint = charToCodePoint;
  }

  private int toCodePoint(int charIndex) {
    if (charToCodePoint == null || charIndex < 0 || charIndex >= charToCodePoint.length) {
      return charIndex;
    }
    return charToCodePoint[charIndex];
  }

  private void putPosition(long id, int charIndex) {
    sourceInfo.putPositions(id, toCodePoint(charIndex));
  }

  private Expr convert(CelExpr expr) {
    if (shortCircuitedMacros.contains(expr)) {
      Long innerId = convertedIds.get(expr);
      if (innerId == null) {
        throw new IllegalStateException("Inner macro not converted yet: " + expr);
      }
      return Expr.newBuilder().setId(innerId).build();
    }
    if (!isSerializingMacroCall) {
      Expr cached = convertedExprs.get(expr);
      if (cached != null) {
        return cached;
      }
    }
    Long existingId = convertedIds.get(expr);
    long id = existingId != null ? existingId : idGenerator.getAndIncrement();
    if (existingId == null) {
      convertedIds.put(expr, id);
      putPosition(id, expr.sourceIndex());
    }
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
              Expr.Select.newBuilder().setOperand(convert(v.operand())).setField(v.field().name()));
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
                  .addArgs(convert(v.operand()))
                  .addArgs(
                      convert(
                          new CelExpr.StringValue(v.field().name(), v.operand().sourceIndex()))));
      case CelExpr.OptionalIndex v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_[?_]")
                  .addArgs(convert(v.operand()))
                  .addArgs(convert(v.index())));
      case CelExpr.Not v ->
          builder.setCallExpr(
              Expr.Call.newBuilder().setFunction("!_").addArgs(convert(v.operand())));
      case CelExpr.Negative v ->
          builder.setCallExpr(
              Expr.Call.newBuilder().setFunction("-_").addArgs(convert(v.operand())));
      case CelExpr.Add v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_+_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Subtract v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_-_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Multiply v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_*_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Divide v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_/_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Modulo v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_%_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.EqualTo v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_==_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.NotEqualTo v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_!=_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.LessThan v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_<_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.LessThanOrEqualTo v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_<=_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.GreaterThan v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_>_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.GreaterThanOrEqualTo v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_>=_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.And v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_&&_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.Or v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("_||_")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.In v ->
          builder.setCallExpr(
              Expr.Call.newBuilder()
                  .setFunction("@in")
                  .addArgs(convert(v.left()))
                  .addArgs(convert(v.right())));
      case CelExpr.IfElse v ->
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
      case CelExpr.ListOf v -> {
        Expr.CreateList.Builder listBuilder = Expr.CreateList.newBuilder();
        for (int i = 0; i < v.elements().size(); i++) {
          Element elem = v.elements().get(i);
          listBuilder.addElements(convert(elem.value()));
          if (elem.isOptional()) {
            listBuilder.addOptionalIndices(i);
          }
        }
        builder.setListExpr(listBuilder);
      }
      case CelExpr.MapOf v -> {
        Expr.CreateStruct.Builder structBuilder = Expr.CreateStruct.newBuilder();
        for (KeyedBy<CelExpr> entry : v.entries()) {
          long entryId = idGenerator.getAndIncrement();
          putPosition(entryId, entry.sourceIndex());
          structBuilder.addEntries(
              Expr.CreateStruct.Entry.newBuilder()
                  .setId(entryId)
                  .setMapKey(convert(entry.key()))
                  .setValue(convert(entry.value()))
                  .setOptionalEntry(entry.isOptional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.Struct v -> {
        Expr.CreateStruct.Builder structBuilder =
            Expr.CreateStruct.newBuilder().setMessageName(v.typeName());
        for (KeyedBy<Ident> field : v.fields()) {
          long entryId = idGenerator.getAndIncrement();
          putPosition(entryId, field.sourceIndex());
          structBuilder.addEntries(
              Expr.CreateStruct.Entry.newBuilder()
                  .setId(entryId)
                  .setFieldKey(field.key().name())
                  .setValue(convert(field.value()))
                  .setOptionalEntry(field.isOptional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case CelExpr.Macro.Has v -> {
        builder.setSelectExpr(
            Expr.Select.newBuilder()
                .setOperand(convert(v.member().operand()))
                .setField(v.member().field().name())
                .setTestOnly(true));
        sourceInfo.putMacroCalls(id, serializeMacroCall(v));
      }
      case CelExpr.Macro v -> {
        builder.setComprehensionExpr(toComprehensionProto(v.sourceIndex(), v));
        sourceInfo.putMacroCalls(id, serializeMacroCall(v));
      }
    }
    Expr result = builder.build();
    if (!isSerializingMacroCall) {
      convertedExprs.put(expr, result);
    }
    return result;
  }

  private Expr serializeMacroCall(CelExpr.Macro v) {
    CelExpr macroCallExpr = toMacroCall(v);
    convertedIds.put(macroCallExpr, 0L);
    java.util.List<CelExpr> toShortCircuit = new java.util.ArrayList<>();
    switch (v) {
      case CelExpr.Macro.Has m -> findMacros(m.member(), toShortCircuit);
      case CelExpr.Macro.All m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case CelExpr.Macro.Exists m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case CelExpr.Macro.ExistsOne m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case CelExpr.Macro.Filter m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.expr(), toShortCircuit);
      }
      case CelExpr.Macro.Map m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.expr(), toShortCircuit);
      }
      case CelExpr.Macro.FilterMap m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.filter(), toShortCircuit);
        findMacros(m.transform(), toShortCircuit);
      }
    }

    shortCircuitedMacros.addAll(toShortCircuit);
    boolean wasSerializing = isSerializingMacroCall;
    isSerializingMacroCall = true;
    try {
      return convert(macroCallExpr);
    } finally {
      isSerializingMacroCall = wasSerializing;
      shortCircuitedMacros.removeAll(toShortCircuit);
    }
  }

  private static void findMacros(CelExpr expr, java.util.List<CelExpr> result) {
    if (expr == null) {
      return;
    }
    if (expr instanceof CelExpr.Macro) {
      result.add(expr);
      return;
    }
    switch (expr) {
      case CelExpr.Not v -> findMacros(v.operand(), result);
      case CelExpr.Negative v -> findMacros(v.operand(), result);
      case CelExpr.Add v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.Subtract v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.Multiply v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.Divide v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.Modulo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.LessThan v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.LessThanOrEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.GreaterThan v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.GreaterThanOrEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.EqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.NotEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.And v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.Or v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.In v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case CelExpr.IfElse v -> {
        findMacros(v.condition(), result);
        findMacros(v.ifTrue(), result);
        findMacros(v.ifFalse(), result);
      }
      case CelExpr.FunctionCall v -> {
        for (CelExpr arg : v.args()) {
          findMacros(arg, result);
        }
      }
      case CelExpr.MemberCall v -> {
        for (CelExpr arg : v.args()) {
          findMacros(arg, result);
        }
      }
      default -> {}
    }
  }

  private Expr.Comprehension toComprehensionProto(int macroPos, CelExpr.Macro macro) {
    return switch (macro) {
      case CelExpr.Macro.Has v -> throw new AssertionError("Macro.Has is handled separately");
      case CelExpr.Macro.All v -> toAllComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case CelExpr.Macro.Exists v ->
          toExistsComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case CelExpr.Macro.ExistsOne v ->
          toExistsOneComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case CelExpr.Macro.Filter v -> toFilterComprehension(macroPos, v.target(), v.iterationVar(), v.expr());
      case CelExpr.Macro.Map v -> toMapComprehension(macroPos, v.target(), v.iterationVar(), v.expr());
      case CelExpr.Macro.FilterMap v ->
          toFilterMapComprehension(macroPos, v.target(), v.iterationVar(), v.filter(), v.transform());
    };
  }

  private Expr.Comprehension toAllComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr condition) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr conditionProto = convert(condition);

    long initId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long condId = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(condId, macroPos);
    sourceInfo.putPositions(accuId2, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder()
            .setId(initId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(condId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("@not_strictly_false")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId1)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_&&_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(conditionProto))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toExistsComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr condition) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr conditionProto = convert(condition);

    long initId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long notId = idGenerator.getAndIncrement();
    long condId = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(notId, macroPos);
    sourceInfo.putPositions(condId, macroPos);
    sourceInfo.putPositions(accuId2, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder()
            .setId(initId)
            .setConstExpr(Constant.newBuilder().setBoolValue(false))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(condId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("@not_strictly_false")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(notId)
                            .setCallExpr(
                                Expr.Call.newBuilder()
                                    .setFunction("!_")
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(accuId1)
                                            .setIdentExpr(
                                                Expr.Ident.newBuilder().setName("@result"))))))
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
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(conditionProto))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toExistsOneComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr condition) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr conditionProto = convert(condition);

    long initId = idGenerator.getAndIncrement();
    long loopCondId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long oneConstId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long accuId3 = idGenerator.getAndIncrement();
    long resultConstId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(loopCondId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(oneConstId, macroPos);
    sourceInfo.putPositions(addId, macroPos);
    sourceInfo.putPositions(accuId2, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(accuId3, macroPos);
    sourceInfo.putPositions(resultConstId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder()
            .setId(initId)
            .setConstExpr(Constant.newBuilder().setInt64Value(0))
            .build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(conditionProto)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(addId)
                            .setCallExpr(
                                Expr.Call.newBuilder()
                                    .setFunction("_+_")
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(accuId1)
                                            .setIdentExpr(
                                                Expr.Ident.newBuilder().setName("@result")))
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(oneConstId)
                                            .setConstExpr(Constant.newBuilder().setInt64Value(1)))))
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_==_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId3)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(
                        Expr.newBuilder()
                            .setId(resultConstId)
                            .setConstExpr(Constant.newBuilder().setInt64Value(1))))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toFilterComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr expr) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr conditionProto = convert(expr);

    long initId = idGenerator.getAndIncrement();
    long loopCondId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long stepListId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(loopCondId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(stepListId, macroPos);
    sourceInfo.putPositions(addId, macroPos);
    sourceInfo.putPositions(accuId2, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder().setId(initId).setListExpr(Expr.CreateList.newBuilder()).build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(conditionProto)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(addId)
                            .setCallExpr(
                                Expr.Call.newBuilder()
                                    .setFunction("_+_")
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(accuId1)
                                            .setIdentExpr(
                                                Expr.Ident.newBuilder().setName("@result")))
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(stepListId)
                                            .setListExpr(
                                                Expr.CreateList.newBuilder()
                                                    .addElements(varProto)))))
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toMapComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr expr) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr exprProto = convert(expr);

    long initId = idGenerator.getAndIncrement();
    long loopCondId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long stepListId = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(loopCondId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(stepListId, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder().setId(initId).setListExpr(Expr.CreateList.newBuilder()).build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_+_")
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId1)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(
                        Expr.newBuilder()
                            .setId(stepListId)
                            .setListExpr(Expr.CreateList.newBuilder().addElements(exprProto))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private Expr.Comprehension toFilterMapComprehension(
      int macroPos, CelExpr target, CelExpr.Ident var, CelExpr filter, CelExpr transform) {
    Expr targetProto = convert(target);
    Expr varProto = convert(var);
    Expr filterProto = convert(filter);
    Expr transformProto = convert(transform);

    long initId = idGenerator.getAndIncrement();
    long loopCondId = idGenerator.getAndIncrement();
    long accuId1 = idGenerator.getAndIncrement();
    long stepListId = idGenerator.getAndIncrement();
    long addId = idGenerator.getAndIncrement();
    long accuId2 = idGenerator.getAndIncrement();
    long stepId = idGenerator.getAndIncrement();
    long resultId = idGenerator.getAndIncrement();

    sourceInfo.putPositions(initId, macroPos);
    sourceInfo.putPositions(loopCondId, macroPos);
    sourceInfo.putPositions(accuId1, macroPos);
    sourceInfo.putPositions(stepListId, macroPos);
    sourceInfo.putPositions(addId, macroPos);
    sourceInfo.putPositions(accuId2, macroPos);
    sourceInfo.putPositions(stepId, macroPos);
    sourceInfo.putPositions(resultId, macroPos);

    Expr accuInit =
        Expr.newBuilder().setId(initId).setListExpr(Expr.CreateList.newBuilder()).build();

    Expr loopCondition =
        Expr.newBuilder()
            .setId(loopCondId)
            .setConstExpr(Constant.newBuilder().setBoolValue(true))
            .build();

    Expr loopStep =
        Expr.newBuilder()
            .setId(stepId)
            .setCallExpr(
                Expr.Call.newBuilder()
                    .setFunction("_?_:_")
                    .addArgs(filterProto)
                    .addArgs(
                        Expr.newBuilder()
                            .setId(addId)
                            .setCallExpr(
                                Expr.Call.newBuilder()
                                    .setFunction("_+_")
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(accuId1)
                                            .setIdentExpr(
                                                Expr.Ident.newBuilder().setName("@result")))
                                    .addArgs(
                                        Expr.newBuilder()
                                            .setId(stepListId)
                                            .setListExpr(
                                                Expr.CreateList.newBuilder()
                                                    .addElements(transformProto)))))
                    .addArgs(
                        Expr.newBuilder()
                            .setId(accuId2)
                            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
            .build();

    Expr result =
        Expr.newBuilder()
            .setId(resultId)
            .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))
            .build();

    return Expr.Comprehension.newBuilder()
        .setIterVar(varProto.getIdentExpr().getName())
        .setIterRange(targetProto)
        .setAccuVar("@result")
        .setAccuInit(accuInit)
        .setLoopCondition(loopCondition)
        .setLoopStep(loopStep)
        .setResult(result)
        .build();
  }

  private static CelExpr toMacroCall(CelExpr.Macro macro) {
    return switch (macro) {
      case CelExpr.Macro.Has v ->
          new CelExpr.FunctionCall(
              new CelExpr.Ident("has", v.sourceIndex()), List.of(v.member()), v.sourceIndex());
      case CelExpr.Macro.All v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("all", v.sourceIndex()),
              List.of(v.iterationVar(), v.condition()),
              v.sourceIndex());
      case CelExpr.Macro.Exists v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("exists", v.sourceIndex()),
              List.of(v.iterationVar(), v.condition()),
              v.sourceIndex());
      case CelExpr.Macro.ExistsOne v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("exists_one", v.sourceIndex()),
              List.of(v.iterationVar(), v.condition()),
              v.sourceIndex());
      case CelExpr.Macro.Filter v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("filter", v.sourceIndex()),
              List.of(v.iterationVar(), v.expr()),
              v.sourceIndex());
      case CelExpr.Macro.Map v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("map", v.sourceIndex()),
              List.of(v.iterationVar(), v.expr()),
              v.sourceIndex());
      case CelExpr.Macro.FilterMap v ->
          new CelExpr.MemberCall(
              v.target(),
              new CelExpr.Ident("map", v.sourceIndex()),
              List.of(v.iterationVar(), v.filter(), v.transform()),
              v.sourceIndex());
    };
  }
}

package com.google.mu.cel;

import static com.google.mu.util.Substring.all;

import com.google.api.expr.v1alpha1.Constant;
import com.google.api.expr.v1alpha1.Expr;
import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.api.expr.v1alpha1.SourceInfo;
import com.google.mu.cel.CelExpr.Add;
import com.google.mu.cel.CelExpr.And;
import com.google.mu.cel.CelExpr.BoolValue;
import com.google.mu.cel.CelExpr.BytesValue;
import com.google.mu.cel.CelExpr.Divide;
import com.google.mu.cel.CelExpr.DoubleValue;
import com.google.mu.cel.CelExpr.Element;
import com.google.mu.cel.CelExpr.EqualTo;
import com.google.mu.cel.CelExpr.FunctionCall;
import com.google.mu.cel.CelExpr.GreaterThan;
import com.google.mu.cel.CelExpr.GreaterThanOrEqualTo;
import com.google.mu.cel.CelExpr.Ident;
import com.google.mu.cel.CelExpr.IfElse;
import com.google.mu.cel.CelExpr.In;
import com.google.mu.cel.CelExpr.Index;
import com.google.mu.cel.CelExpr.KeyedBy;
import com.google.mu.cel.CelExpr.LessThan;
import com.google.mu.cel.CelExpr.LessThanOrEqualTo;
import com.google.mu.cel.CelExpr.ListOf;
import com.google.mu.cel.CelExpr.LongValue;
import com.google.mu.cel.CelExpr.Macro;
import com.google.mu.cel.CelExpr.MapOf;
import com.google.mu.cel.CelExpr.MemberCall;
import com.google.mu.cel.CelExpr.Modulo;
import com.google.mu.cel.CelExpr.Multiply;
import com.google.mu.cel.CelExpr.Negative;
import com.google.mu.cel.CelExpr.Not;
import com.google.mu.cel.CelExpr.NotEqualTo;
import com.google.mu.cel.CelExpr.OptionalIndex;
import com.google.mu.cel.CelExpr.OptionalSelect;
import com.google.mu.cel.CelExpr.Or;
import com.google.mu.cel.CelExpr.Select;
import com.google.mu.cel.CelExpr.StringValue;
import com.google.mu.cel.CelExpr.Struct;
import com.google.mu.cel.CelExpr.Subtract;
import com.google.mu.cel.CelExpr.UintValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
      case BoolValue v -> builder.setConstExpr(Constant.newBuilder().setBoolValue(v.value()));
      case LongValue v -> builder.setConstExpr(Constant.newBuilder().setInt64Value(v.value()));
      case UintValue v -> builder.setConstExpr(Constant.newBuilder().setUint64Value(v.value()));
      case DoubleValue v -> builder.setConstExpr(Constant.newBuilder().setDoubleValue(v.value()));
      case StringValue v -> builder.setConstExpr(Constant.newBuilder().setStringValue(v.value()));
      case BytesValue v ->
          builder.setConstExpr(Constant.newBuilder().setBytesValue(ByteString.copyFrom(v.value())));
      case Ident v -> builder.setIdentExpr(Expr.Ident.newBuilder().setName(v.name()));
      case Select v -> builder.setSelectExpr(
          Expr.Select.newBuilder().setOperand(convert(v.operand())).setField(v.field().name()));
      case Index v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_[_]")
              .addArgs(convert(v.operand()))
              .addArgs(convert(v.index())));
      case OptionalSelect v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_?._")
              .addArgs(convert(v.operand()))
              .addArgs(convert(new StringValue(v.field().name(), v.operand().sourceIndex()))));
      case OptionalIndex v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_[?_]")
              .addArgs(convert(v.operand()))
              .addArgs(convert(v.index())));
      case Not v -> builder.setCallExpr(
          Expr.Call.newBuilder().setFunction("!_").addArgs(convert(v.operand())));
      case Negative v -> builder.setCallExpr(
          Expr.Call.newBuilder().setFunction("-_").addArgs(convert(v.operand())));
      case Add v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_+_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case Subtract v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_-_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case Multiply v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_*_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case Divide v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_/_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case Modulo v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_%_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case EqualTo v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_==_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case NotEqualTo v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_!=_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case LessThan v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_<_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case LessThanOrEqualTo v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_<=_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case GreaterThan v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_>_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case GreaterThanOrEqualTo v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_>=_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case And v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_&&_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case Or v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_||_")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case In v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("@in")
              .addArgs(convert(v.left()))
              .addArgs(convert(v.right())));
      case IfElse v -> builder.setCallExpr(
          Expr.Call.newBuilder()
              .setFunction("_?_:_")
              .addArgs(convert(v.condition()))
              .addArgs(convert(v.ifTrue()))
              .addArgs(convert(v.ifFalse())));
      case FunctionCall v -> {
        Expr.Call.Builder callBuilder = Expr.Call.newBuilder().setFunction(v.function().name());
        for (CelExpr arg : v.args()) {
          callBuilder.addArgs(convert(arg));
        }
        builder.setCallExpr(callBuilder);
      }
      case MemberCall v -> {
        Expr.Call.Builder callBuilder =
            Expr.Call.newBuilder().setFunction(v.member().name()).setTarget(convert(v.target()));
        for (CelExpr arg : v.args()) {
          callBuilder.addArgs(convert(arg));
        }
        builder.setCallExpr(callBuilder);
      }
      case ListOf v -> {
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
      case MapOf v -> {
        Expr.CreateStruct.Builder structBuilder = Expr.CreateStruct.newBuilder();
        for (KeyedBy<CelExpr> entry : v.entries()) {
          long entryId = idGenerator.getAndIncrement();
          putPosition(entryId, entry.sourceIndex());
          structBuilder.addEntries(Expr.CreateStruct.Entry.newBuilder()
              .setId(entryId)
              .setMapKey(convert(entry.key()))
              .setValue(convert(entry.value()))
              .setOptionalEntry(entry.isOptional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case Struct v -> {
        Expr.CreateStruct.Builder structBuilder =
            Expr.CreateStruct.newBuilder().setMessageName(v.typeName());
        for (KeyedBy<Ident> field : v.fields()) {
          long entryId = idGenerator.getAndIncrement();
          putPosition(entryId, field.sourceIndex());
          structBuilder.addEntries(Expr.CreateStruct.Entry.newBuilder()
              .setId(entryId)
              .setFieldKey(field.key().name())
              .setValue(convert(field.value()))
              .setOptionalEntry(field.isOptional()));
        }
        builder.setStructExpr(structBuilder);
      }
      case Macro.Has v -> {
        builder.setSelectExpr(Expr.Select.newBuilder()
            .setOperand(convert(v.member().operand()))
            .setField(v.member().field().name())
            .setTestOnly(true));
        sourceInfo.putMacroCalls(id, serializeMacroCall(v));
      }
      case Macro v -> {
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

  private Expr serializeMacroCall(Macro v) {
    CelExpr macroCallExpr = toMacroCall(v);
    convertedIds.put(macroCallExpr, 0L);
    java.util.List<CelExpr> toShortCircuit = new java.util.ArrayList<>();
    switch (v) {
      case Macro.Has m -> findMacros(m.member(), toShortCircuit);
      case Macro.All m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case Macro.Exists m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case Macro.ExistsOne m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.condition(), toShortCircuit);
      }
      case Macro.Filter m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.expr(), toShortCircuit);
      }
      case Macro.Map m -> {
        findMacros(m.target(), toShortCircuit);
        findMacros(m.expr(), toShortCircuit);
      }
      case Macro.FilterMap m -> {
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
    if (expr instanceof Macro) {
      result.add(expr);
      return;
    }
    switch (expr) {
      case Not v -> findMacros(v.operand(), result);
      case Negative v -> findMacros(v.operand(), result);
      case Add v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case Subtract v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case Multiply v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case Divide v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case Modulo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case LessThan v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case LessThanOrEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case GreaterThan v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case GreaterThanOrEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case EqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case NotEqualTo v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case And v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case Or v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case In v -> {
        findMacros(v.left(), result);
        findMacros(v.right(), result);
      }
      case IfElse v -> {
        findMacros(v.condition(), result);
        findMacros(v.ifTrue(), result);
        findMacros(v.ifFalse(), result);
      }
      case FunctionCall v -> {
        for (CelExpr arg : v.args()) {
          findMacros(arg, result);
        }
      }
      case MemberCall v -> {
        for (CelExpr arg : v.args()) {
          findMacros(arg, result);
        }
      }
      default -> {}
    }
  }

  private Expr.Comprehension toComprehensionProto(int macroPos, Macro macro) {
    return switch (macro) {
      case Macro.Has v -> throw new AssertionError("Macro.Has is handled separately");
      case Macro.All v -> toAllComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case Macro.Exists v ->
          toExistsComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case Macro.ExistsOne v ->
          toExistsOneComprehension(macroPos, v.target(), v.iterationVar(), v.condition());
      case Macro.Filter v ->
          toFilterComprehension(macroPos, v.target(), v.iterationVar(), v.expr());
      case Macro.Map v -> toMapComprehension(macroPos, v.target(), v.iterationVar(), v.expr());
      case Macro.FilterMap v -> toFilterMapComprehension(
          macroPos, v.target(), v.iterationVar(), v.filter(), v.transform());
    };
  }

  private Expr.Comprehension toAllComprehension(
      int macroPos, CelExpr target, Ident var, CelExpr condition) {
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

    Expr accuInit = Expr.newBuilder()
        .setId(initId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(condId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("@not_strictly_false")
            .addArgs(Expr.newBuilder()
                .setId(accuId1)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_&&_")
            .addArgs(Expr.newBuilder()
                .setId(accuId2)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
            .addArgs(conditionProto))
        .build();

    Expr result = Expr.newBuilder()
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
      int macroPos, CelExpr target, Ident var, CelExpr condition) {
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

    Expr accuInit = Expr.newBuilder()
        .setId(initId)
        .setConstExpr(Constant.newBuilder().setBoolValue(false))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(condId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("@not_strictly_false")
            .addArgs(Expr.newBuilder()
                .setId(notId)
                .setCallExpr(Expr.Call.newBuilder()
                    .setFunction("!_")
                    .addArgs(Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_||_")
            .addArgs(Expr.newBuilder()
                .setId(accuId2)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
            .addArgs(conditionProto))
        .build();

    Expr result = Expr.newBuilder()
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
      int macroPos, CelExpr target, Ident var, CelExpr condition) {
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

    Expr accuInit = Expr.newBuilder()
        .setId(initId)
        .setConstExpr(Constant.newBuilder().setInt64Value(0))
        .build();

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_?_:_")
            .addArgs(conditionProto)
            .addArgs(Expr.newBuilder()
                .setId(addId)
                .setCallExpr(Expr.Call.newBuilder()
                    .setFunction("_+_")
                    .addArgs(Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(Expr.newBuilder()
                        .setId(oneConstId)
                        .setConstExpr(Constant.newBuilder().setInt64Value(1)))))
            .addArgs(Expr.newBuilder()
                .setId(accuId2)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
        .build();

    Expr result = Expr.newBuilder()
        .setId(resultId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_==_")
            .addArgs(Expr.newBuilder()
                .setId(accuId3)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
            .addArgs(Expr.newBuilder()
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
      int macroPos, CelExpr target, Ident var, CelExpr expr) {
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

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_?_:_")
            .addArgs(conditionProto)
            .addArgs(Expr.newBuilder()
                .setId(addId)
                .setCallExpr(Expr.Call.newBuilder()
                    .setFunction("_+_")
                    .addArgs(Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(Expr.newBuilder()
                        .setId(stepListId)
                        .setListExpr(Expr.CreateList.newBuilder().addElements(varProto)))))
            .addArgs(Expr.newBuilder()
                .setId(accuId2)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
        .build();

    Expr result = Expr.newBuilder()
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
      int macroPos, CelExpr target, Ident var, CelExpr expr) {
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

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_+_")
            .addArgs(Expr.newBuilder()
                .setId(accuId1)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
            .addArgs(Expr.newBuilder()
                .setId(stepListId)
                .setListExpr(Expr.CreateList.newBuilder().addElements(exprProto))))
        .build();

    Expr result = Expr.newBuilder()
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
      int macroPos, CelExpr target, Ident var, CelExpr filter, CelExpr transform) {
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

    Expr loopCondition = Expr.newBuilder()
        .setId(loopCondId)
        .setConstExpr(Constant.newBuilder().setBoolValue(true))
        .build();

    Expr loopStep = Expr.newBuilder()
        .setId(stepId)
        .setCallExpr(Expr.Call.newBuilder()
            .setFunction("_?_:_")
            .addArgs(filterProto)
            .addArgs(Expr.newBuilder()
                .setId(addId)
                .setCallExpr(Expr.Call.newBuilder()
                    .setFunction("_+_")
                    .addArgs(Expr.newBuilder()
                        .setId(accuId1)
                        .setIdentExpr(Expr.Ident.newBuilder().setName("@result")))
                    .addArgs(Expr.newBuilder()
                        .setId(stepListId)
                        .setListExpr(Expr.CreateList.newBuilder().addElements(transformProto)))))
            .addArgs(Expr.newBuilder()
                .setId(accuId2)
                .setIdentExpr(Expr.Ident.newBuilder().setName("@result"))))
        .build();

    Expr result = Expr.newBuilder()
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

  private static CelExpr toMacroCall(Macro macro) {
    return switch (macro) {
      case Macro.Has v ->
          new FunctionCall(new Ident("has", v.sourceIndex()), List.of(v.member()), v.sourceIndex());
      case Macro.All v -> new MemberCall(
          v.target(), new Ident("all", v.sourceIndex()), List.of(v.iterationVar(), v.condition()),
          v.sourceIndex());
      case Macro.Exists v -> new MemberCall(
          v.target(), new Ident("exists", v.sourceIndex()),
          List.of(v.iterationVar(), v.condition()), v.sourceIndex());
      case Macro.ExistsOne v -> new MemberCall(
          v.target(), new Ident("exists_one", v.sourceIndex()),
          List.of(v.iterationVar(), v.condition()), v.sourceIndex());
      case Macro.Filter v -> new MemberCall(
          v.target(), new Ident("filter", v.sourceIndex()), List.of(v.iterationVar(), v.expr()),
          v.sourceIndex());
      case Macro.Map v -> new MemberCall(
          v.target(), new Ident("map", v.sourceIndex()), List.of(v.iterationVar(), v.expr()),
          v.sourceIndex());
      case Macro.FilterMap v -> new MemberCall(
          v.target(), new Ident("map", v.sourceIndex()),
          List.of(v.iterationVar(), v.filter(), v.transform()), v.sourceIndex());
    };
  }
}

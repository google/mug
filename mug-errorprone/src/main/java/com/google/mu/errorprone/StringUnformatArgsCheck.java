package com.google.mu.errorprone;

import static com.google.mu.util.stream.GuavaCollectors.toImmutableMap;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.mu.util.stream.BiStream.toAdjacentPairs;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.instanceMethod;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers.MethodClassMatcher;
import com.google.errorprone.util.ASTHelpers;
import com.google.mu.errorprone.AbstractBugChecker.ErrorReport;
import com.google.mu.function.MapFrom3;
import com.google.mu.function.MapFrom4;
import com.google.mu.function.MapFrom5;
import com.google.mu.function.MapFrom6;
import com.google.mu.function.MapFrom7;
import com.google.mu.function.MapFrom8;
import com.google.mu.util.CaseBreaker;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;

/**
 * Checks that the "unformat" methods (methods that parse an input string according to a predefined
 * format string with curly braced pattern placeholders) are invoked with the correct lambda
 * according to the string format.
 */
@BugPattern(
    summary =
        "Checks that StringFormat and ResourceNamePattern callers pass in lambda"
            + " that accept the same number of placeholders as defined in the string format.",
    link = "go/java-tips/024#compile-time-check",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class StringUnformatArgsCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final MethodClassMatcher MATCHER =
      instanceMethod().onDescendantOf("com.google.mu.util.StringFormat");
  private static final CharMatcher ALPHA_NUM =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9'));
  private static final ImmutableMap<TypeName, Integer> UNFORMAT_MAPPER_TYPES =
      BiStream.of(
              Consumer.class, 1,
              BiConsumer.class, 2,
              Function.class, 1,
              BiFunction.class, 2,
              BinaryOperator.class, 2)
          .append(MapFrom3.class, 3)
          .append(MapFrom4.class, 4)
          .append(MapFrom5.class, 5)
          .append(MapFrom6.class, 6)
          .append(MapFrom7.class, 7)
          .append(MapFrom8.class, 8)
          .append(Collector.class, 1)
          .append(BiCollector.class, 2)
          .mapKeys(TypeName::of)
          .collect(toImmutableMap());
  private static final String NONCAPTURING_PLACEHOLDER = "...";

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    MethodSymbol symbol = ASTHelpers.getSymbol(tree);
    if (symbol.isVarArgs()) {
      return;
    }
    ExpressionTree stringArg =
        BiStream.zip(symbol.getParameters(), tree.getArguments())
            .filterKeys(param -> isStringType(param.type, state))
            .values()
            .findFirst()
            .orElse(null);
    if (stringArg == null) {
      return; // Not an unformat or parse method.
    }
    ExpressionTree unformatMapperArg =
        BiStream.zip(symbol.getParameters(), tree.getArguments())
            .filterKeys(param -> isUnformatMapperType(param.type, state))
            .values()
            .findFirst()
            .orElse(null);
    int expectedPlaceholders =
        symbol.getParameters().stream()
            .map(param -> expectedNumPlaceholders(param.type, state))
            .filter(num -> num > 0)
            .findFirst()
            .orElse(0);
    ExpressionTree unformatter = ASTHelpers.getReceiver(tree);
    if (unformatter == null) {
      return; // Must be a private instance method.
    }
    String formatString = FormatStringUtils.findFormatString(unformatter, state).orElse(null);
    checkingOn(tree)
        .require(
            formatString == null
                || FormatStringUtils
                    .PLACEHOLDER_NAMES_PATTERN
                    .match(formatString)
                    .collect(toAdjacentPairs())
                    // In "{foo}{bar}", bar.index() - 2 is at the end of foo.
                    .noneMatch((p1, p2) -> p2.index() - 2 <= p1.index() + p1.length()),
            "Format string defined by %s with two placeholders immediately next to each other is"
                + " inherently ambiguous to parse.",
            unformatter);
    if (unformatMapperArg == null || expectedPlaceholders == 0) {
      return; // No unformat mapper function parameter to check
    }
    checkingOn(unformatter)
        .require(
            formatString != null,
            "Compile-time format string required for validating the lambda parameter [%s];"
                + " definition not found. As a result, the lambda parameters cannot be"
                + " validated at compile-time.\nIf your format string is dynamically loaded or"
                + " dynamically computed, and you opt to use the API despite the risk of"
                + " not having comile-time guarantee, consider suppressing the error with"
                + " @SuppressWarnings(\"StringUnformatArgsCheck\").",
            unformatMapperArg);
    ImmutableList<String> placeholderVariableNames =
        FormatStringUtils.placeholderVariableNames(formatString).stream()
            .filter(n -> !n.equals(NONCAPTURING_PLACEHOLDER))
            .collect(toImmutableList());
    checkingOn(tree)
        .require(
            placeholderVariableNames.size() == expectedPlaceholders,
            "%s capturing placeholders defined by: %s; %s expected by %s",
            placeholderVariableNames.size(),
            unformatter,
            expectedPlaceholders,
            unformatMapperArg);
    if (unformatMapperArg instanceof LambdaExpressionTree) {
      checkLambdaParameters(
          tree, (LambdaExpressionTree) unformatMapperArg, placeholderVariableNames);
    } else if (unformatMapperArg instanceof MemberReferenceTree) {
      checkMethodReference(
          tree, (MemberReferenceTree) unformatMapperArg, placeholderVariableNames, state);
    }
  }

  private void checkLambdaParameters(
      ExpressionTree unformatInvocation,
      LambdaExpressionTree lambda,
      List<String> placeholderVariableNames)
      throws ErrorReport {
    ImmutableList<String> lambdaParamNames =
        lambda.getParameters().stream()
            .map(param -> param.getName().toString())
            .collect(toImmutableList());
    ImmutableList<String> normalizedLambdaParamNames =
        normalizeNamesForComparison(lambdaParamNames);
    ImmutableList<String> normalizedPlaceholderNames =
        normalizeNamesForComparison(placeholderVariableNames);
    checkingOn(unformatInvocation)
        .require(
            !outOfOrder(normalizedLambdaParamNames, normalizedPlaceholderNames),
            "lambda variables %s appear to be in inconsistent order with the placeholder"
                + " variables as defined by: %s",
            lambdaParamNames,
            ASTHelpers.getReceiver(unformatInvocation));
    for (int i = 0; i < placeholderVariableNames.size(); i++) {
      checkingOn(unformatInvocation)
          .require(
              mightBeForSameThing(
                  normalizedLambdaParamNames.get(i), normalizedPlaceholderNames.get(i)),
              "Lambda variable `%s` doesn't look to be for placeholder {%s} as defined by: %s\n"
                  + "Consider using %s as the lambda variable name or renaming the {%s}"
                  + " placeholder. A prefix or suffix will work too.",
              lambdaParamNames.get(i),
              placeholderVariableNames.get(i),
              ASTHelpers.getReceiver(unformatInvocation),
              placeholderVariableNames.get(i),
              placeholderVariableNames.get(i));
    }
  }

  private void checkMethodReference(
      ExpressionTree unformatInvocation,
      MemberReferenceTree methodRef,
      List<String> placeholderVariableNames,
      VisitorState state)
      throws ErrorReport {
    MethodTree method = JavacTrees.instance(state.context).getTree(ASTHelpers.getSymbol(methodRef));
    if (method == null) {
      return; // This shouldn't happen. But if it did, we don't want to fail compilation.
    }
    ImmutableList<String> paramNames =
        method.getParameters().stream()
            .map(param -> param.getName().toString())
            .collect(toImmutableList());
    ImmutableList<String> normalizedParamNames = normalizeNamesForComparison(paramNames);
    ImmutableList<String> normalizedPlaceholderNames =
        normalizeNamesForComparison(placeholderVariableNames);
    if (normalizedParamNames.size() != placeholderVariableNames.size()) {
      // Can happen if the method ref is like SomeType::someInstanceMethod.
      // Because we only have string placeholders, SomeType can only be String or its super
      // types. It's an unusual case and there is no way users can ensure name match without
      // resorting to explicit lambda. Just trust the programmer.
      return;
    }
    checkingOn(unformatInvocation)
        .require(
            !outOfOrder(normalizedParamNames, normalizedPlaceholderNames),
            "Parameters of referenced method %s(%s) appear to be in inconsistent order with the"
                + " placeholder variables as defined by: %s",
            methodRef,
            String.join(", ", paramNames),
            ASTHelpers.getReceiver(unformatInvocation));
    if (normalizedParamNames.size() < 3
        && !ASTHelpers.inSamePackage(ASTHelpers.getSymbol(method), state)) {
      // For 1 or 2-arg public API, don't fail on parameter names, because it may make it harder
      // to evolve common APIs.
      // We still validate out-of-order error which should unlikely happen with API evolution.
      //
      // It should be rare for idiomatic common public API to have 3+ String args
      // because such methods would be easy to mess up.
      return;
    }
    for (int i = 0; i < placeholderVariableNames.size(); i++) {
      checkingOn(unformatInvocation)
          .require(
              mightBeForSameThing(normalizedParamNames.get(i), normalizedPlaceholderNames.get(i)),
              "Method parameter `%s` of referenced method `%s` doesn't look to be for"
                  + " placeholder {%s} as defined by: %s\n"
                  + "Consider using `%s` as the method parameter name, renaming the {%s}"
                  + " placeholder, or using a lambda expression where you can use the"
                  + " placeholder name as the parameter name.",
              paramNames.get(i),
              methodRef,
              placeholderVariableNames.get(i),
              ASTHelpers.getReceiver(unformatInvocation),
              placeholderVariableNames.get(i),
              placeholderVariableNames.get(i));
    }
  }

  private static ImmutableList<String> normalizeNamesForComparison(List<String> names) {
    return names.stream()
        .map(name -> CaseBreaker.toCase(CaseFormat.UPPER_CAMEL, name)) // id and jobId should match
        .map(ALPHA_NUM.negate()::removeFrom)
        .collect(toImmutableList());
  }

  private static boolean isStringType(Type type, VisitorState state) {
    return ASTHelpers.isSameType(type, state.getSymtab().stringType, state);
  }

  private static boolean isUnformatMapperType(Type type, VisitorState state) {
    return expectedNumPlaceholders(type, state) > 0;
  }

  private static int expectedNumPlaceholders(Type type, VisitorState state) {
    return BiStream.from(UNFORMAT_MAPPER_TYPES)
        .filterKeys(mapperType -> mapperType.isSameType(type, state))
        .values()
        .findFirst()
        .orElse(0);
  }

  private static boolean outOfOrder(List<String> names1, List<String> names2) {
    ImmutableSet<String> nameSet = ImmutableSet.copyOf(names2);
    return names1.size() > 1
        && names1.stream().allMatch(nameSet::contains)
        && !names1.equals(names2);
  }

  private static boolean mightBeForSameThing(String name1, String name2) {
    return name1.startsWith(name2)
        || name2.startsWith(name1)
        || name1.endsWith(name2)
        || name2.endsWith(name1)
        || Strings.commonPrefix(name1, name2).length() > 3;
  }
}

package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyMethod;
import static com.google.mu.util.stream.BiCollectors.groupingBy;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableListMultimap;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Map;

import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiCollectors;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.GuavaCollectors;
import com.google.mu.util.CaseBreaker;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.lang.model.type.TypeKind;

/**
 * Checks that the {@code StringFormat.format()} method is invoked with the correct lambda according
 * to the string format.
 */
@BugPattern(
    summary =
        "Checks that StringFormat.format() receives the expected number of arguments,"
            + " and the argument expressions look to be in the right order.",
    link = "go/java-tips/024#safer-string-format-reuse",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class StringFormatArgsCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck, AbstractBugChecker.MemberReferenceCheck {
  private static final Matcher<MethodInvocationTree> MATCHER =
      Matchers.anyOf(
          anyMethod().onDescendantOf("com.google.mu.util.StringFormat"),
          anyMethod().onDescendantOf("com.google.mu.util.StringFormat.To"));
  private static final ImmutableSet<TypeName> FORMATTER_TYPES =
      ImmutableSet.of(
          new TypeName("com.google.mu.util.StringFormat"),
          new TypeName("com.google.mu.util.StringFormat.To"));
  private static final ImmutableMap<TypeName, Integer> FUNCTION_CARDINALITIES =
      ImmutableMap.of(
          TypeName.of(Function.class), 1,
          TypeName.of(BiFunction.class), 2,
          TypeName.of(BinaryOperator.class), 2,
          TypeName.of(IntFunction.class), 1,
          TypeName.of(LongFunction.class), 1,
          TypeName.of(DoubleFunction.class), 1);
  private static final ImmutableSet<TypeName> BAD_FORMAT_ARG_TYPES =
      ImmutableSet.of(
          TypeName.of(Optional.class),
          TypeName.of(OptionalInt.class),
          TypeName.of(OptionalLong.class),
          TypeName.of(OptionalDouble.class),
          TypeName.of(Stream.class),
          TypeName.of(IntStream.class),
          TypeName.of(LongStream.class),
          TypeName.of(DoubleStream.class),
          new TypeName("com.google.mu.util.BiStream"),
          new TypeName("com.google.mu.util.Both"));
  private static final Substring.Pattern ARG_COMMENT = Substring.spanningInOrder("/*", "*/");

  @Override
  public void checkMemberReference(MemberReferenceTree tree, VisitorState state)
      throws ErrorReport {
    ExpressionTree receiver = tree.getQualifierExpression();
    Type receiverType = ASTHelpers.getType(receiver);
    if (FORMATTER_TYPES.stream().anyMatch(t -> t.isSameType(receiverType, state))) {
      String memberName = tree.getName().toString();
      Type referenceType = ASTHelpers.getType(tree);
      if (memberName.equals("format")
          || memberName.equals("with")
          || memberName.equals("lenientFormat")) {
        String formatString = FormatStringUtils.findFormatString(receiver, state).orElse(null);
        checkingOn(receiver)
            .require(
                formatString != null,
                "Compile-time format string expected but definition not found. As a result, the"
                    + " format arguments cannot be validated at compile-time.\n"
                    + "If your format string is dynamically loaded or dynamically computed, and you"
                    + " opt to use the API despite the risk of not having comile-time guarantee,"
                    + " consider suppressing the error with"
                    + " @SuppressWarnings(\"LabsStringFormatArgsCheck\").");
        Integer cardinality =
            BiStream.from(FUNCTION_CARDINALITIES)
                .filterKeys(mapperType -> mapperType.isSameType(referenceType, state))
                .values()
                .findFirst()
                .orElse(null);
        checkingOn(tree)
            .require(
                cardinality != null,
                "%s() is used as a %s but ErrorProne was not able to verify the correctness",
                memberName,
                referenceType);
        ImmutableList<String> placeholderVariableNames =
            FormatStringUtils.placeholderVariableNames(formatString);
        checkingOn(tree)
            .require(
                placeholderVariableNames.size() == cardinality,
                "%s placeholders defined by: %s; mismatched number (%s) will be provided from %s",
                placeholderVariableNames.size(),
                receiver,
                cardinality,
                referenceType);
      }
    }
  }

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    MethodSymbol symbol = ASTHelpers.getSymbol(tree);
    if (!symbol.isVarArgs() || symbol.getParameters().size() != 1) {
      return;
    }
    checkArgsFormattability(tree, state);
    ExpressionTree formatter = ASTHelpers.getReceiver(tree);
    String formatString = FormatStringUtils.findFormatString(formatter, state).orElse(null);
    checkingOn(formatter)
        .require(
            formatString != null,
            "Compile-time format string expected but definition not found. As a result, the"
                + " format arguments cannot be validated at compile-time.\n"
                + "If your format string is dynamically loaded or dynamically computed, and you"
                + " opt to use the API despite the risk of not having comile-time guarantee,"
                + " consider suppressing the error with"
                + " @SuppressWarnings(\"LabsStringFormatArgsCheck\").");
    ImmutableList<String> placeholderVariableNames =
        FormatStringUtils.placeholderVariableNames(formatString);
    checkingOn(tree)
        .require(
            placeholderVariableNames.size() == tree.getArguments().size(),
            "%s placeholders defined by: %s; %s provided by %s",
            placeholderVariableNames.size(),
            formatter,
            tree.getArguments().size(),
            tree);
    ImmutableList<String> args = argsAsTexts(tree, state);
    if (args.size() != placeholderVariableNames.size()) {
      return; // This shouldn't happen. But if it did, we don't want to fail compilation.
    }
    // For inline format strings, the args and the placeholders are close to each other.
    // With <= 3 args, we can give the author some leeway and don't ask for silly comments like:
    // new StringFormat("{key}:{value}").format(/* key */ "one", /* value */ 1);
    boolean formatStringIsInlined =
        FormatStringUtils.getInlineStringArg(formatter, state).orElse(null) instanceof JCLiteral;
    ImmutableList<String> normalizedArgTexts =
        args.stream().map(txt -> normalizeForComparison(txt)).collect(toImmutableList());
    for (int i = 0; i < placeholderVariableNames.size(); i++) {
      String placeholderName = placeholderVariableNames.get(i);
      String normalizedPlacehoderName = normalizeForComparison(placeholderName);
      if (!normalizedArgTexts.get(i).contains(normalizedPlacehoderName)) {
        // arg doesn't match placeholder
        ExpressionTree arg = tree.getArguments().get(i);
        boolean trust =
            formatStringIsInlined
                && args.size() <= 3
                && arg instanceof JCLiteral
                && (args.size() <= 1
                    || normalizedArgTexts.stream() // out-of-order is suspicious
                        .noneMatch(txt -> txt.contains(normalizedPlacehoderName)));
        checkingOn(tree)
            .require(
                trust && !ARG_COMMENT.in(args.get(i)).isPresent(),
                "String format placeholder {%s} as defined in %s should appear in the format"
                    + " argument: %s. Or you could add a comment like /* %s */.",
                placeholderVariableNames.get(i),
                formatter,
                arg,
                placeholderName);
      }
    }
    checkDuplicatePlaceholderNames(placeholderVariableNames, tree.getArguments(), state);
  }

  private void checkDuplicatePlaceholderNames(
      List<String> placeholderVariableNames,
      List<? extends ExpressionTree> args,
      VisitorState state)
      throws ErrorReport {
    ImmutableListMultimap<String, ExpressionTree> allPlaceholders =
        BiStream.zip(placeholderVariableNames, args).collect(toImmutableListMultimap());
    for (Map.Entry<String, List<ExpressionTree>> entry :
        Multimaps.asMap(allPlaceholders).entrySet()) {
      List<ExpressionTree> conflicts =
          elide(entry.getValue(), state::getSourceForNode, arg -> tokensFrom(arg, state));
      if (conflicts.size() > 1) {
        throw checkingOn(conflicts.get(0))
            .report(
                "conflicting argument for placeholder {%s} encountered: %s",
                entry.getKey(),
                conflicts.stream().skip(1).map(state::getSourceForNode).findFirst().get());
      }
    }
  }

  private static ImmutableList<String> tokensFrom(Tree tree, VisitorState state) {
    String source = state.getSourceForNode(tree);
    return state.getTokensForNode(tree).stream()
        .map(token -> source.subSequence(token.pos(), token.endPos()).toString())
        .collect(toImmutableList());
  }

  private static String normalizeForComparison(String text) {
    return new CaseBreaker()
        .breakCase(text) // All punctuation chars gone
        .filter(s -> !s.equals("get")) // user.getId() should match e.g. user_id
        .filter(s -> !s.equals("is")) // job.isComplete() should match job_complete
        .map(Ascii::toLowerCase) // ignore case
        .collect(joining("_")); // delimit words
  }

  private static ImmutableList<String> argsAsTexts(
      MethodInvocationTree invocation, VisitorState state) {
    int position = state.getEndPosition(invocation.getMethodSelect());
    if (position < 0) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (ExpressionTree arg : invocation.getArguments()) {
      int next = state.getEndPosition(arg);
      if (next < 0) {
        return ImmutableList.of();
      }
      builder.add(state.getSourceCode().subSequence(position, next).toString());
      position = next;
    }
    return builder.build();
  }

  private void checkArgsFormattability(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    for (ExpressionTree arg : tree.getArguments()) {
      Type type = ASTHelpers.getType(arg);
      checkingOn(arg)
          .require(
              type.getKind() != TypeKind.ARRAY,
              "arrays shouldn't be used as string format argument")
          .require(
              BAD_FORMAT_ARG_TYPES.stream().noneMatch(bad -> bad.isSameType(type, state)),
              "%s shouldn't be used as string format argument",
              type);
    }
  }

  /**
   * Elides elements in {@code list}.
   *
   * <p>Elements mapping to the same key using one of {@code elideFunctions} will be elided and only
   * the first element is retained.
   */
  @SafeVarargs
  private static <T> List<T> elide(List<T> list, Function<? super T, ?>... elidingFunctions) {
    for (Function<? super T, ?> elidingFunction : elidingFunctions) {
      if (list.size() <= 1) {
        return list;
      }
      list =
          list.stream()
              .collect(BiStream.groupingBy(elidingFunction, (a, b) -> a))
              .mapToObj((k, v) -> v)
              .collect(toImmutableList());
    }
    return list;
  }
}

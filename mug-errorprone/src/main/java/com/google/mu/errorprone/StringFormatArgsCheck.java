/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyMethod;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableListMultimap;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Map;
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

import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.ErrorProneTokens;
import com.google.mu.util.CaseBreaker;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCLiteral;

/**
 * Checks that the {@code StringFormat.format()} method is invoked with the correct lambda according
 * to the string format.
 */
@BugPattern(
    summary =
        "Checks that StringFormat.format() receives the expected number of arguments,"
            + " and the argument expressions look to be in the right order.",
    link = "https://github.com/google/mug/wiki/StringFormat-Explained",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class StringFormatArgsCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck,
        AbstractBugChecker.ConstructorCallCheck,
        AbstractBugChecker.MemberReferenceCheck {
  private static final Matcher<MethodInvocationTree> STRING_FORMAT_MATCHER =
      Matchers.anyOf(
          anyMethod().onDescendantOf("com.google.mu.util.StringFormat"),
          anyMethod().onDescendantOf("com.google.mu.util.StringFormat.Template"));
  private static final String FORMAT_STRING_NOT_FOUND =
      "Compile-time format string expected but definition not found. As a result, the"
          + " format arguments cannot be validated at compile-time.\n"
          + "If your format string is dynamically loaded or dynamically computed, and you"
          + " opt to use the API despite the risk of not having comile-time guarantee,"
          + " consider suppressing the error with"
          + " @SuppressWarnings(\"StringFormatArgsCheck\").";
  private static final ImmutableSet<TypeName> FORMATTER_TYPES =
      ImmutableSet.of(
          new TypeName("com.google.mu.util.StringFormat"),
          new TypeName("com.google.mu.util.StringFormat.Template"));
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
          TypeName.of(OptionalInt.class),
          TypeName.of(OptionalLong.class),
          TypeName.of(OptionalDouble.class),
          TypeName.of(Stream.class),
          TypeName.of(IntStream.class),
          TypeName.of(LongStream.class),
          TypeName.of(DoubleStream.class),
          new TypeName("com.google.mu.util.BiStream"),
          new TypeName("com.google.mu.util.Both"));
  private static final TypeName BOOLEAN_TYPE = TypeName.of(Boolean.class);
  private static final TypeName OPTIONAL_TYPE = TypeName.of(Optional.class);
  private static final Substring.Pattern ARG_COMMENT = Substring.spanningInOrder("/*", "*/");

  @Override
  public void checkMemberReference(MemberReferenceTree tree, VisitorState state)
      throws ErrorReport {
    checkingOn(tree)
        .require(!isTemplateFormatMethod(ASTHelpers.getSymbol(tree), state), FORMAT_STRING_NOT_FOUND);
    ExpressionTree receiver = tree.getQualifierExpression();
    Type receiverType = ASTHelpers.getType(receiver);
    if (FORMATTER_TYPES.stream().anyMatch(t -> t.isSameType(receiverType, state))) {
      String memberName = tree.getName().toString();
      Type referenceType = ASTHelpers.getType(tree);
      if (memberName.equals("format")
          || memberName.equals("with")
          || memberName.equals("lenientFormat")) {
        String formatString = FormatStringUtils.findFormatString(receiver, state).orElse(null);
        checkingOn(receiver).require(formatString != null, FORMAT_STRING_NOT_FOUND);
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
  public void checkConstructorCall(NewClassTree tree, VisitorState state) throws ErrorReport {
    MethodSymbol method = ASTHelpers.getSymbol(tree);
    if (isTemplateFormatMethod(method, state)) {
      checkTemplateFormatArgs(
          tree,
          method.getParameters(),
          tree.getArguments(),
          argsAsTexts(tree.getIdentifier(), tree.getArguments(), state),
          state);
    }
  }

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    MethodSymbol method = ASTHelpers.getSymbol(tree);
    if (isTemplateFormatMethod(method, state)) {
      int templateStringIndex = BiStream.zip(indexesFrom(0), method.getParameters().stream())
          .filterValues(param -> ASTHelpers.hasAnnotation(
              param, "com.google.mu.annotations.TemplateString", state))
          .keys()
          .findFirst()
          .orElse(0);
      ExpressionTree formatExpression =
          ASTHelpers.stripParentheses(tree.getArguments().get(templateStringIndex));
      String formatString = ASTHelpers.constValue(formatExpression, String.class);
      checkingOn(tree).require(formatString != null, FORMAT_STRING_NOT_FOUND);
      checkFormatArgs(
          formatExpression,
          FormatStringUtils.placeholdersFrom(formatString),
          tree,
          skip(tree.getArguments(), templateStringIndex + 1),
          skip(argsAsTexts(tree.getMethodSelect(), tree.getArguments(), state), templateStringIndex + 1),
          /* formatStringIsInlined= */ formatExpression instanceof JCLiteral,
          state);
    } else if (STRING_FORMAT_MATCHER.matches(tree, state)) {
      if (!method.isVarArgs() || method.getParameters().size() != 1) {
        return;
      }
      ExpressionTree formatter = ASTHelpers.getReceiver(tree);
      ExpressionTree formatExpression = FormatStringUtils.findFormatStringNode(formatter, state).orElse(null);
      checkingOn(formatter).require(formatExpression != null, FORMAT_STRING_NOT_FOUND);
      // For inline format strings, the args and the placeholders are close to each other.
      // With <= 3 args, we can give the author some leeway and don't ask for silly comments like:
      // new StringFormat("{key}:{value}").format(/* key */ "one", /* value */ 1);
      boolean formatStringIsInlined =
          FormatStringUtils.getInlineStringArg(formatter, state).orElse(null) instanceof JCLiteral;
      checkFormatArgs(
          formatExpression,
          FormatStringUtils.placeholdersFrom(ASTHelpers.constValue(formatExpression, String.class)),
          tree,
          tree.getArguments(),
          argsAsTexts(tree.getMethodSelect(), tree.getArguments(), state),
          formatStringIsInlined,
          state);
    }
  }

  private void checkTemplateFormatArgs(
      ExpressionTree tree,
      List<? extends VarSymbol> params,
      List<? extends ExpressionTree> args,
      List<String> argSources,
      VisitorState state)
      throws ErrorReport {
    int templateStringIndex =
        BiStream.zip(indexesFrom(0), params.stream())
            .filterValues(
                param ->
                    ASTHelpers.hasAnnotation(
                        param, "com.google.mu.annotations.TemplateString", state))
            .keys()
            .findFirst()
            .orElse(0);
    ExpressionTree formatExpression = ASTHelpers.stripParentheses(args.get(templateStringIndex));
    String formatString = ASTHelpers.constValue(formatExpression, String.class);
    checkingOn(tree).require(formatString != null, FORMAT_STRING_NOT_FOUND);
    checkFormatArgs(
        formatExpression,
        FormatStringUtils.placeholdersFrom(formatString),
        tree,
        skip(args, templateStringIndex + 1),
        skip(argSources, templateStringIndex + 1),
        /* formatStringIsInlined= */ formatExpression instanceof JCLiteral,
        state);
  }

  private void checkFormatArgs(
      ExpressionTree formatExpression,
      List<Placeholder> placeholders,
      ExpressionTree invocation,
      List<? extends ExpressionTree> args,
      List<String> argSources,
      boolean formatStringIsInlined,
      VisitorState state)
      throws ErrorReport {
    for (ExpressionTree arg : args) {
      checkArgFormattability(arg, state);
    }
    ImmutableList<String> normalizedArgTexts =
        argSources.stream().map(txt -> normalizeForComparison(txt)).collect(toImmutableList());
    LineMap lineMap = state.getPath().getCompilationUnit().getLineMap();
    for (int i = 0; i < placeholders.size(); i++) {
      Placeholder placeholder = placeholders.get(i);
      NodeCheck onPlaceholder = checkingOn(() -> placeholder.sourcePosition(formatExpression, state));
      onPlaceholder.require(
          args.size() > i, "No value is provided for placeholder {%s}", placeholder.name());
      String normalizedPlacehoderName = normalizeForComparison(placeholder.name());
      ExpressionTree arg = args.get(i);
      if (!normalizedArgTexts.get(i).contains(normalizedPlacehoderName)) {
        // arg doesn't match placeholder
        boolean trust =
            formatStringIsInlined
                && args.size() <= 3
                && arg instanceof JCLiteral
                && (args.size() <= 1
                    || normalizedArgTexts.stream() // out-of-order is suspicious
                        .noneMatch(txt -> txt.contains(normalizedPlacehoderName)));
        checkingOn(arg)
            .require(
                trust && ARG_COMMENT.in(argSources.get(i)).isEmpty(),
                "String format placeholder {%s} at line %s (defined as in \"%s\") should appear in the format"
                    + " argument: %s. Consider the following to address this error:\n"
                    + "  1. Ensure the argument isn't passed in out of order.\n"
                    + "  2. If the argument does correspond to the placeholder positionally, rename"
                    + " either the placeholder {%s} or local variable used in the argument, if any,"
                    + " to make the argument expression include the placeholder name word-by-word"
                    + " (case insensitive)\n"
                    + "  3. If you can't make them organically match, as the last resort, add a"
                    + " comment like /* %s */ before the argument. You only need to add the comment"
                    + " for non-matching placeholders. Don't add redundant comments for the"
                    + " placeholders that already match.",
                placeholder.name(),
                lineMap.getLineNumber(
                    placeholder
                        .sourcePosition(formatExpression, state)
                        .getPreferredPosition()),
                placeholder,
                arg,
                placeholder.name(),
                placeholder.name());
      }
      if (placeholder.hasConditionalOperator()) {
        Type argType = ASTHelpers.getType(arg);
        ImmutableSet<String> references = placeholder.optionalParametersFromOperatorRhs();
        if (ASTHelpers.isSameType(argType, state.getSymtab().booleanType, state)
            || BOOLEAN_TYPE.isSameType(argType, state)) {
          onPlaceholder.require(
              references.isEmpty(),
              "guard placeholder {%s ->} maps to boolean expression <%s> at line %s. The optional"
                  + " placeholder references %s to the right of the `->` operator should only be"
                  + " used for an optional placeholder.",
                  placeholder.name(),
                  arg,
                  lineMap.getLineNumber(ASTHelpers.getStartPosition(arg)),
                  references);
        } else if (OPTIONAL_TYPE.isSameType(argType, state)) {
          onPlaceholder
              .require(
                  placeholder.hasOptionalParameter(),
                  "optional parameter {%s->} must be an identifier followed by a '?'",
                  placeholder.name())
              .require(
                  !references.isEmpty(),
                  "optional parameter %s must be referenced at least once to the right of {%s->}",
                  placeholder.name(),
                  placeholder.name())
              .require(
                  references.equals(ImmutableSet.of(placeholder.cleanName())),
                  "unexpected optional parameters to the right of {%s->}: %s",
                  placeholder.name(),
                  Sets.difference(references, ImmutableSet.of(placeholder.name())));
        } else {
          throw onPlaceholder.report(
              "guard placeholder {%s ->} is expected to be boolean or Optional, whereas"
                  + " argument <%s> at line %s is of type %s",
              placeholder.name(),
              arg,
              lineMap.getLineNumber(ASTHelpers.getStartPosition(arg)),
              argType);
        }
      }
    }
    checkingOn(invocation)
        .require(
            placeholders.size() == args.size(),
            "%s placeholders defined; %s provided",
            placeholders.size(),
            args.size());
    checkDuplicatePlaceholderNames(placeholders, args, state);
  }

  private void checkDuplicatePlaceholderNames(
      List<Placeholder> placeholders,
      List<? extends ExpressionTree> args,
      VisitorState state)
      throws ErrorReport {
    ImmutableListMultimap<String, ExpressionTree> allPlaceholders =
        BiStream.zip(placeholders, args)
            .mapKeys(Placeholder::name)
            .skipKeysIf("..."::equals) // wildcard doesn't count as duplicate name
            .collect(toImmutableListMultimap());
    for (Map.Entry<String, List<ExpressionTree>> entry :
        Multimaps.asMap(allPlaceholders).entrySet()) {
      List<ExpressionTree> conflicts =
          elide(entry.getValue(), state::getSourceForNode, arg -> tokensFrom(arg, state));
      if (conflicts.size() >= 2) {
        throw checkingOn(conflicts.get(0))
            .report(
                "conflicting argument for placeholder {%s} encountered: %s",
                entry.getKey(), state.getSourceForNode(conflicts.get(1)));
      }
    }
  }

  /**
   * Moderately expensive since it needs to re-lex the source. Only call it to confirm a conflict.
   */
  private static ImmutableList<String> tokensFrom(Tree tree, VisitorState state) {
    String source = state.getSourceForNode(tree);
    return ErrorProneTokens.getTokens(source, state.context).stream()
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
      ExpressionTree invocationStart, List<? extends ExpressionTree> args, VisitorState state) {
    int position = state.getEndPosition(invocationStart);
    if (position < 0) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (ExpressionTree arg : args) {
      int next = state.getEndPosition(arg);
      if (next < 0) {
        return ImmutableList.of();
      }
      builder.add(state.getSourceCode().subSequence(position, next).toString());
      position = next;
    }
    return builder.build();
  }

  private void checkArgFormattability(ExpressionTree arg, VisitorState state) throws ErrorReport {
    Type type = ASTHelpers.getType(arg);
    checkingOn(arg)
        .require(
            type.getKind() != TypeKind.ARRAY, "arrays shouldn't be used as string format argument")
        .require(
            BAD_FORMAT_ARG_TYPES.stream().noneMatch(bad -> bad.isSameType(type, state)),
            "%s shouldn't be used as string format argument",
            type);
  }

  private static boolean isTemplateFormatMethod(Symbol method, VisitorState state) {
    return ASTHelpers.hasAnnotation(
        method, "com.google.mu.annotations.TemplateFormatMethod", state);
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
      if (list.size() < 2) {
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

  private static <T> List<T> skip(List<T> list, int n) {
    return n > list.size() ? ImmutableList.of() : list.subList(n, list.size());
  }
}

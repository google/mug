package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyMethod;
import static com.google.mu.errorprone.FormatStringUtils.PLACEHOLDER_PATTERN;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.MoreStreams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.method.MethodMatchers.MethodClassMatcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;

/**
 * Warns against potential SQL injection risks caused by unquoted string placeholders. This class
 * mainly checks that all string placeholder values must correspond to a quoted placeholder such as
 * {@code '{foo}'}; and unquoted placeholders should only accept trusted types (enums, numbers,
 * booleans, TrustedSqlString etc.)
 */
@BugPattern(
    summary = "Checks that string placeholders in SQL template strings are quoted.",
    link = "go/java-tips/024#preventing-sql-injection",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class SafeQueryArgsCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final MethodClassMatcher STRING_FORMAT_TO_METHOD_MATCHER =
      anyMethod().onDescendantOf("com.google.mu.util.StringFormat.To");
  private static final TypeName SAFE_QUERY_TYPE =
      new TypeName("com.google.mu.safesql.SafeQuery");
  private static final ImmutableSet<TypeName> ARG_TYPES_THAT_SHOULD_NOT_BE_QUOTED =
      ImmutableSet.of(
          new TypeName("com.google.storage.googlesql.safesql.TrustedSqlString"),
          SAFE_QUERY_TYPE,
          new TypeName("com.google.protobuf.Timestamp"));
  private static final ImmutableSet<TypeName> ARG_TYPES_THAT_MUST_BE_QUOTED =
      ImmutableSet.of(
          TypeName.of(String.class), TypeName.of(Character.class), TypeName.of(char.class));

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    MethodSymbol symbol = ASTHelpers.getSymbol(tree);
    if (SAFE_QUERY_TYPE.isSameType(symbol.getReturnType(), state)
        && isTemplateFormatMethod(symbol, state)) {
      int stringTemplateArgIndex = getStringTemplateParamIndex(symbol, state);
      String templateString =
          ASTHelpers.constValue(
              ASTHelpers.stripParentheses(tree.getArguments().get(stringTemplateArgIndex)),
              String.class);
      if (templateString == null) {
        // shouldn't happen if the TemplateStringAnnotationCheck is happy
        return;
      }
      checkQuotes(
          PLACEHOLDER_PATTERN
              .repeatedly()
              .match(templateString)
              .collect(toImmutableList()),
          tree.getArguments().subList(stringTemplateArgIndex + 1, tree.getArguments().size()),
          state);
    } else if (STRING_FORMAT_TO_METHOD_MATCHER.matches(tree, state)) {
      if (!SAFE_QUERY_TYPE.isSameType(ASTHelpers.getType(tree), state)) {
        return;
      }
      if (!symbol.isVarArgs() || symbol.getParameters().size() != 1) {
        return;
      }
      ExpressionTree formatter = ASTHelpers.getReceiver(tree);
      String formatString = FormatStringUtils.findFormatString(formatter, state).orElse(null);
      if (formatString == null || !FormatStringUtils.looksLikeSql(formatString)) {
        return;
      }
      checkQuotes(
          PLACEHOLDER_PATTERN
              .repeatedly()
              .match(formatString)
              .collect(toImmutableList()),
          tree.getArguments(),
          state);
    }
  }

  private void checkQuotes(
      List<Substring.Match> placeholders, List<? extends ExpressionTree> args, VisitorState state)
      throws ErrorReport {
    if (placeholders.size() != args.size()) {
      // Shouldn't happen. Will leave it to the other checks to report.
      return;
    }
    for (int i = 0; i < placeholders.size(); i++) {
      Substring.Match placeholder = placeholders.get(i);
      if (placeholder.isImmediatelyBetween("`", "`")) {
        continue;
      }
      ExpressionTree arg = args.get(i);
      Type type = ASTHelpers.getType(arg);
      if (placeholder.isImmediatelyBetween("'", "'")
          || placeholder.isImmediatelyBetween("\"", "\"")) {
        // It's a quoted string literal. Do not use sql query types
        checkingOn(arg)
            .require(
                ARG_TYPES_THAT_SHOULD_NOT_BE_QUOTED.stream()
                    .noneMatch(t -> t.isSameType(type, state)),
                "argument of type %s should not be quoted in the template string: '%s'",
                type,
                placeholder);
      } else { // Disallow arbitrary string literals or characters unless backquoted.
        checkingOn(arg)
            .require(
                ARG_TYPES_THAT_MUST_BE_QUOTED.stream().noneMatch(t -> t.isSameType(type, state)),
                "argument of type %s must be quoted in the template string (for example '%s' for"
                    + " string literals or `%s` for identifiers)",
                type,
                placeholder,
                placeholder);
      }
    }
  }

  private static boolean isTemplateFormatMethod(Symbol method, VisitorState state) {
    return ASTHelpers.hasAnnotation(
        method, "com.google.mu.annotations.TemplateFormatMethod", state);
  }

  private static int getStringTemplateParamIndex(MethodSymbol method, VisitorState state) {
    return BiStream.zip(indexesFrom(0), method.getParameters().stream())
        .filterValues(
            param ->
                ASTHelpers.hasAnnotation(
                    param, "com.google.mu.annotations.TemplateString", state))
        .keys()
        .findFirst()
        .orElse(0);
  }
}

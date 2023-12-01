package com.google.mu.errorprone;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyMethod;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.mu.util.Substring;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.method.MethodMatchers.MethodClassMatcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

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
  private static final MethodClassMatcher MATCHER =
      anyMethod().onDescendantOf("com.google.mu.util.StringFormat.To");
  private static final TypeName SAFE_QUERY_TYPE =
      new TypeName("com.google.mu.safesql.SafeQuery");
  private static final ImmutableSet<TypeName> ARG_TYPES_THAT_SHOULD_NOT_BE_QUOTED =
      ImmutableSet.of(
          new TypeName("com.google.storage.googlesql.safesql.TrustedSqlString"),
          new TypeName("com.google.mu.safesql.SafeQuery"),
          new TypeName("com.google.protobuf.Timestamp"));
  private static final ImmutableSet<TypeName> ARG_TYPES_THAT_MUST_BE_QUOTED =
      ImmutableSet.of(
          TypeName.of(String.class), TypeName.of(Character.class), TypeName.of(char.class));

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    if (!SAFE_QUERY_TYPE.isSameType(ASTHelpers.getType(tree), state)) {
      return;
    }
    MethodSymbol symbol = ASTHelpers.getSymbol(tree);
    if (!symbol.isVarArgs() || symbol.getParameters().size() != 1) {
      return;
    }
    ExpressionTree formatter = ASTHelpers.getReceiver(tree);
    String formatString = FormatStringUtils.findFormatString(formatter, state).orElse(null);
    if (formatString == null || !FormatStringUtils.looksLikeSql(formatString)) {
      return;
    }
    ImmutableList<Substring.Match> placeholders =
        FormatStringUtils
            .PLACEHOLDER_PATTERN
            .repeatedly()
            .match(formatString)
            .collect(toImmutableList());
    if (placeholders.size() != tree.getArguments().size()) {
      // Shouldn't happen. Will leave it to the other checks to report.
      return;
    }
    for (int i = 0; i < placeholders.size(); i++) {
      Substring.Match placeholder = placeholders.get(i);
      if (placeholder.isImmediatelyBetween("`", "`")) {
        continue;
      }
      ExpressionTree arg = tree.getArguments().get(i);
      Type type = ASTHelpers.getType(arg);
      if (placeholder.isImmediatelyBetween("'", "'")
          || placeholder.isImmediatelyBetween("\"", "\"")) {
        // It's a quoted string literal. Do not use sql query types
        checkingOn(arg)
            .require(
                ARG_TYPES_THAT_SHOULD_NOT_BE_QUOTED.stream()
                    .noneMatch(t -> t.isSameType(type, state)),
                "argument of type %s should not be quoted: '%s'",
                type,
                placeholder);
      } else { // Disallow arbitrary string literals or characters unless backquoted.
        checkingOn(arg)
        .require(
            ARG_TYPES_THAT_MUST_BE_QUOTED.stream().noneMatch(t -> t.isSameType(type, state)),
            "argument of type %s must be quoted (for example '%s' for string literals or `%s`"
                + " for identifiers)",
            type,
            placeholder,
            placeholder);
      }
    }
  }
}

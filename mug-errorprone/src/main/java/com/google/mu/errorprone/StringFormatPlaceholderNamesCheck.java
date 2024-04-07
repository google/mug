package com.google.mu.errorprone;


import static com.google.mu.util.Substring.after;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.constructor;
import static com.google.errorprone.matchers.Matchers.staticMethod;

import com.google.auto.service.AutoService;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;

/** Restricts the placeholder names used in StringFormat and ResourceNamePattern. */
@BugPattern(
    summary = "Checks that valid placeholder names are used in string format.",
    link = "https://github.com/google/mug/wiki/StringFormat-Explained",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class StringFormatPlaceholderNamesCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck, AbstractBugChecker.ConstructorCallCheck {
  private static final Matcher<ExpressionTree> MATCHER =
      Matchers.anyOf(
          constructor().forClass("com.google.mu.util.StringFormat"),
          staticMethod().onClass("com.google.mu.util.StringFormat"));
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z');
  private static final CharPredicate VALID_CHARS =
      ALPHA.orRange('0', '9').or(CharMatcher.anyOf(".*_-")::matches);

  /** Currently allowed special placeholder names. */
  private static final ImmutableSet<String> SPECIAL_PLACEHOLDER_NAMES = ImmutableSet.of("...");

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    doCheck(tree, state);
  }

  @Override
  public void checkConstructorCall(NewClassTree tree, VisitorState state) throws ErrorReport {
    doCheck(tree, state);
  }

  private void doCheck(ExpressionTree tree, VisitorState state) throws ErrorReport {
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    String formatString = FormatStringUtils.findFormatString(tree, state).orElse(null);
    if (formatString == null) {
      return;
    }
    for (String placeholderName : FormatStringUtils.placeholderVariableNames(formatString)) {
      checkingOn(tree)
          .require(
              SPECIAL_PLACEHOLDER_NAMES.contains(placeholderName)
                  || isNormalPlaceholderName(placeholderName),
              "Invalid placeholder name {%s}",
              placeholderName);
    }
  }

  private static boolean isNormalPlaceholderName(String placeholderName) {
    return after(Substring.leading(ALPHA))
        .in(placeholderName)
        .filter(VALID_CHARS::matchesAllOf)
        .isPresent();
  }
}

package com.google.mu.errorprone.regex;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.auto.service.AutoService;
import com.google.common.labs.regex.RegexPattern;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

/**
 * Checks that regular expression strings are not vulnerable to exponential backtracking (ReDoS).
 */
@BugPattern(
    summary =
        "Checks that regular expression strings are not vulnerable to exponential backtracking"
            + " (ReDoS).",
    link = "https://github.com/google/mug/blob/master/dot-parse/README.md",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
@SuppressWarnings("restriction")
public final class RegexRedosCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {

  private static final Matcher<ExpressionTree> PARSERS_REGEX =
      staticMethod().onClass("com.google.common.labs.parse.Parsers").named("regex");

  private static final Matcher<ExpressionTree> PATTERN_COMPILE =
      staticMethod().onClass("java.util.regex.Pattern").named("compile");

  private static final Matcher<ExpressionTree> MATCHER = anyOf(PARSERS_REGEX, PATTERN_COMPILE);

  @Override public Description matchMethodInvocation(
      MethodInvocationTree tree, VisitorState state) {
    if (MATCHER.matches(tree, state) && !tree.getArguments().isEmpty()) {
      ExpressionTree arg = tree.getArguments().get(0);
      String regex = ASTHelpers.constValue(arg, String.class);
      if (regex != null) {
        try {
          Redos.checkRedosVulnerability(RegexPattern.of(regex));
        } catch (IllegalArgumentException e) {
          return buildDescription(arg).setMessage(e.getMessage()).build();
        }
      }
    }
    return Description.NO_MATCH;
  }
}

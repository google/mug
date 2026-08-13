package com.google.mu.errorprone;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.auto.service.AutoService;
import com.google.common.labs.regex.RegexPattern;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

/**
 * Checks that the regex literal string passed to {@code Parsers.regex()} does not have an unbounded
 * quantifier, which could cause memory issues or failures when parsing from a Reader.
 */
@BugPattern(
    summary = "Checks that the regex used by Parsers.regex() doesn't have an unbounded quantifier.",
    link = "https://github.com/google/mug/blob/master/dot-parse/README.md",
    linkType = LinkType.CUSTOM,
    severity = WARNING)
@AutoService(BugChecker.class)
@SuppressWarnings("restriction")
public final class RegexCannotStreamFromReader extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final Matcher<ExpressionTree> MATCHER =
      staticMethod().onClass("com.google.common.labs.parse.Parsers").named("regex");

  @Override public void checkMethodInvocation(
      MethodInvocationTree tree, VisitorState state) throws ErrorReport {
    if (MATCHER.matches(tree, state) && tree.getArguments().size() == 1) {
      validateRegex(tree.getArguments().get(0));
    }
  }

  @SuppressWarnings("CompileTimeConstant")
  private void validateRegex(ExpressionTree expression) throws ErrorReport {
    String regex = ASTHelpers.constValue(expression, String.class);
    if (regex == null) {
      return; // Not a compile-time constant, already handled by ParsersRegexCheck
    }
    try {
      RegexPattern pattern = RegexPattern.of(regex);
      checkingOn(expression)
          .require(
              pattern.metadata().maxSize() < 1_000_000,
              "Regex has an unbounded quantifier (or is too large). It may cause "
                  + "excessive memory usage or UnsupportedOperationException when calling "
                  + "parseToStream(Reader).");
    } catch (IllegalArgumentException e) {
      // Ignore, ParsersRegexCheck will report compilation error
    }
  }
}

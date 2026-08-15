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

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.matchers.method.MethodMatchers.staticMethod;

import com.google.auto.service.AutoService;
import com.google.common.labs.parse.Parsers;
import com.google.common.labs.regex.RegexPattern;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.google.mu.errorprone.regex.Redos;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

/** Validates the regular expression literal string passed to {@code Parsers.regex()}. */
@BugPattern(
    summary = "Checks that the regex literal string used by Parsers.regex() is valid.",
    link = "https://github.com/google/mug/blob/master/dot-parse/README.md",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
@SuppressWarnings("restriction")
public final class ParsersRegexCheck extends AbstractBugChecker
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
    String pattern = ASTHelpers.constValue(expression, String.class);
    checkingOn(expression).require(pattern != null, "compile-time string constant expected");
    try {
      Parsers.regex(pattern);
      Redos.checkRedosVulnerability(RegexPattern.of(pattern));
    } catch (IllegalArgumentException e) {
      throw checkingOn(expression).report(e.getMessage());
    }
  }
}

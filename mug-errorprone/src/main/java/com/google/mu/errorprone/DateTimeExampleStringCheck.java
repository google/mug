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
import static com.google.errorprone.matchers.Matchers.staticMethod;

import java.time.DateTimeException;

import com.google.auto.service.AutoService;
import com.google.mu.time.DateTimeFormats;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

/** Validate the example datetime string passed to {@code DateTimeFormats.formatOf()}. */
@BugPattern(
    summary = "Checks that the format string passed to DateTimeFormats.formatOf() is supported.",
    link = "https://github.com/google/mug/blob/master/mug/src/main/java/com/google/mu/time/README.md",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
@SuppressWarnings("restriction")
public final class DateTimeExampleStringCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final Matcher<ExpressionTree> MATCHER =
      staticMethod().onClass("com.google.mu.time.DateTimeFormats").named("formatOf");

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    if (!MATCHER.matches(tree, state) || tree.getArguments().isEmpty()) {
      return;
    }
    ExpressionTree exampleArg = tree.getArguments().get(0);
    String exampleString = ASTHelpers.constValue(exampleArg, String.class);
    checkingOn(exampleArg).require(exampleString != null, "compile-time string constant expected");
    try {
      Object verified = DateTimeFormats.formatOf(exampleString);
    } catch (IllegalArgumentException | DateTimeException e) {
      throw checkingOn(exampleArg).report(e.getMessage());
    }
  }
}
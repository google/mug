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
import com.google.common.labs.parse.Parser;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

/**
 * Validates the character set literal string passed to {@code Parser.anyCharIn()}
 * and {@code Parser.oneOrMoreCharsIn()}.
 */
@BugPattern(
    summary = "Checks that the character set literal string used by Parser is valid.",
    link = "https://github.com/google/mug/blob/master/dot-parse/README.md",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class CharacterSetLiteralCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodInvocationCheck {
  private static final Matcher<ExpressionTree> MATCHER = Matchers.anyOf(
      staticMethod().onClass("com.google.common.labs.parse.Parser").named("anyCharIn"),
      staticMethod().onClass("com.google.common.labs.parse.Parser").named("oneOrMoreCharsIn"));

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    if (!MATCHER.matches(tree, state) || tree.getArguments().isEmpty()) {
      return;
    }
    ExpressionTree characterSetArg = tree.getArguments().get(0);
    String exampleString = ASTHelpers.constValue(characterSetArg, String.class);
    checkingOn(characterSetArg)
        .require(exampleString != null, "compile-time string constant expected");
    try {
      Object verified = Parser.anyCharIn(exampleString);
    } catch (IllegalArgumentException e) {
      throw checkingOn(characterSetArg).report(e.getMessage());
    }
  }
}
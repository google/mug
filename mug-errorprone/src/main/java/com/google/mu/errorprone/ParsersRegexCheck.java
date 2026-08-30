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
import com.google.mu.errorprone.regex.ReDos;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.regex.Pattern;

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
    if (!MATCHER.matches(tree, state)) {
      return;
    }
    List<? extends ExpressionTree> args = tree.getArguments();
    if (args.size() == 1) {
      validateRegex(args.get(0));
    } else if (args.size() == 2) {
      String pattern = validateRegex(args.get(0));
      if (pattern != null) {
        MethodSymbol symbol = ASTHelpers.getSymbol(tree);
        Type mapperType = symbol.getParameters().get(1).type;
        int expectedGroups =
            state.getTypes().findDescriptorType(mapperType).getParameterTypes().size();
        int actualGroups = Pattern.compile(pattern).matcher("").groupCount();
        checkingOn(args.get(0))
            .require(
                actualGroups == expectedGroups,
                "regex pattern '%s' has %s capturing group(s), but %s expected",
                pattern,
                actualGroups,
                expectedGroups);
      }
    }
  }

  @SuppressWarnings("CompileTimeConstant")
  private String validateRegex(ExpressionTree expression) throws ErrorReport {
    String pattern = ASTHelpers.constValue(expression, String.class);
    checkingOn(expression).require(pattern != null, "compile-time string constant expected");
    try {
      Parsers.regex(pattern);
      ReDos.checkRedosVulnerability(RegexPattern.of(pattern));
      return pattern;
    } catch (IllegalArgumentException e) {
      throw checkingOn(expression).report(e.getMessage());
    }
  }
}

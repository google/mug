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

import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.mu.util.stream.BiStream;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.List;

/**
 * Checks that the {@code StringFormat.format()} method is invoked with the correct lambda according
 * to the string format.
 */
@BugPattern(
    summary =
        "Checks that string args passed to a SQL template must be single quoted or backtick"
            + " quoted.",
    link = "go/java-tips/024#preventing-sql-injection",
    linkType = LinkType.CUSTOM,
    severity = ERROR)
@AutoService(BugChecker.class)
public final class TemplateStringArgsMustBeQuotedCheck extends AbstractBugChecker
    implements AbstractBugChecker.MethodCheck,
        AbstractBugChecker.MethodInvocationCheck,
        AbstractBugChecker.ConstructorCallCheck {

  @Override
  public void checkMethod(MethodTree tree, VisitorState state) throws ErrorReport {
    MethodSymbol method = ASTHelpers.getSymbol(tree);
    if (shouldCheck(method, state)) {
      checkingOn(tree)
          .require(
              ASTHelpers.hasAnnotation(
                  method, "com.google.common.labs.text.TemplateFormatMethod", state),
              "Methods annotated with @TemplateStringArgsMustBeQuoted must also be annotated with"
                  + " @TemplateFormatMethod.");
    }
  }

  @Override
  public void checkConstructorCall(NewClassTree tree, VisitorState state) throws ErrorReport {
    MethodSymbol method = ASTHelpers.getSymbol(tree);
    if (shouldCheck(method, state)) {
      checkTemplateFormatArgs(method.getParameters(), tree.getArguments(), state);
    }
  }

  @Override
  public void checkMethodInvocation(MethodInvocationTree tree, VisitorState state)
      throws ErrorReport {
    MethodSymbol method = ASTHelpers.getSymbol(tree);
    if (shouldCheck(method, state)) {
      checkTemplateFormatArgs(method.getParameters(), tree.getArguments(), state);
    }
  }

  private void checkTemplateFormatArgs(
      List<? extends VarSymbol> params, List<? extends ExpressionTree> args, VisitorState state)
      throws ErrorReport {
    int templateStringIndex =
        BiStream.zip(indexesFrom(0), params.stream())
            .filterValues(
                param ->
                    ASTHelpers.hasAnnotation(
                        param, "com.google.common.labs.text.TemplateString", state))
            .keys()
            .findFirst()
            .orElse(0);
    ExpressionTree formatExpression = ASTHelpers.stripParentheses(args.get(templateStringIndex));
    String formatString = ASTHelpers.constValue(formatExpression, String.class);
    if (formatString == null) {
      return;
    }
    List<Placeholder> placeholders =
        FormatStringUtils.placeholdersFrom(formatString);
    args = skip(args, templateStringIndex + 1);
    for (int i = 0; i < placeholders.size(); i++) {
      Placeholder placeholder = placeholders.get(i);
      if (args.size() <= i) {
        // cardinality mismatch is reported by LabsStringFormatArgsCheck
        return;
      }
      ExpressionTree arg = args.get(i);
      if (isStringType(arg, state)) {
        checkingOn(() -> placeholder.sourcePosition(formatExpression, state))
            .require(
                placeholder.match().isImmediatelyBetween("'", "'")
                    || placeholder.match().isImmediatelyBetween("`", "`")
                    || placeholder.match().isImmediatelyBetween("\"", "\""),
                "Ambiguous intention: string placeholder %s must be backtick-, double- or single-quoted.\n" +
                "It's not clear whether you meant to use it as an identifier (table or column name),\n" +
                "or as a string expression. Consider one of the following alternatives:\n" +
                "  1) quote the placeholder with backticks (`%s`) if it should be an identifier;\n" +
                "  2) or, quote the placeholder with single quotes ('%s') if it should be a\n" +
                "     string expression.\n" +
                "  3) or, wrap it inside TrustedSqlString for it to be directly embedded.\n",
                placeholder.match(),
                placeholder.match(),
                placeholder.match());
      }
    }
  }

  private static boolean isStringType(Tree tree, VisitorState state) {
    return ASTHelpers.isSameType(ASTHelpers.getType(tree), state.getSymtab().stringType, state);
  }

  private static boolean shouldCheck(Symbol method, VisitorState state) {
    return ASTHelpers.hasAnnotation(
        method, "com.google.common.labs.text.TemplateStringArgsMustBeQuoted", state);
  }

  private static <T> List<T> skip(List<T> list, int n) {
    return n > list.size() ? ImmutableList.of() : list.subList(n, list.size());
  }
}

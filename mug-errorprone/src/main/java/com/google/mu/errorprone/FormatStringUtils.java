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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.mu.util.Optionals.optionally;
import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.google.mu.util.Substring;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;

/** Some common utils for format and unformat checks. */
@SuppressWarnings("restriction")
final class FormatStringUtils {
  static final Substring.Pattern PLACEHOLDER_PATTERN =
      consecutive(CharMatcher.noneOf("{}")::matches).immediatelyBetween("{", INCLUSIVE, "}", INCLUSIVE);


  static ImmutableList<Placeholder> placeholdersFrom(String formatString) {
    return PLACEHOLDER_PATTERN
        .repeatedly()
        .match(formatString)
        .map(Placeholder::new)
        .collect(toImmutableList());
  }

  static ImmutableList<String> placeholderVariableNames(String formatString) {
    return placeholdersFrom(formatString).stream()
        .map(Placeholder::name)
        .collect(toImmutableList());
  }

  static Optional<ExpressionTree> getInlineStringArg(Tree expression, VisitorState state) {
    ImmutableList<? extends ExpressionTree> args =
        invocationArgs(expression).stream()
            .map(ASTHelpers::stripParentheses)
            .filter(arg -> isStringType(arg, state))
            .collect(toImmutableList());
    return optionally(args.size() == 1, () -> args.get(0));
  }

  static Optional<String> findFormatString(Tree unformatter, VisitorState state) {
    return findFormatStringNode(unformatter, state)
        .map(tree -> ASTHelpers.constValue(tree, String.class));
  }

  static Optional<ExpressionTree> findFormatStringNode(Tree unformatter, VisitorState state) {
    if (unformatter instanceof IdentifierTree) {
      Symbol symbol = ASTHelpers.getSymbol(unformatter);
      if (symbol instanceof VarSymbol) {
        Tree def = JavacTrees.instance(state.context).getTree(symbol);
        if (def instanceof VariableTree) {
          return findFormatStringNode(((VariableTree) def).getInitializer(), state);
        }
      }
      return Optional.empty();
    }
    return getInlineStringArg(unformatter, state);
  }

  static boolean looksLikeSql(String template) {
    return looksLikeQuery().or(looksLikeInsert()).in(Ascii.toLowerCase(template)).isPresent();
  }

  private static Substring.Pattern looksLikeQuery() {
    return Stream.of("select", "update", "delete")
        .map(w -> keyword(w))
        .collect(firstOccurrence())
        .peek(keyword("from").or(keyword("where")))
        .peek(PLACEHOLDER_PATTERN);
  }

  private static Substring.Pattern looksLikeInsert() {
    return keyword("insert into")
        .peek(keyword("values").or(keyword("select")))
        .peek(PLACEHOLDER_PATTERN);
  }

  private static Substring.Pattern keyword(String word) {
    return first(word).separatedBy(CharMatcher.whitespace().or(CharMatcher.anyOf("()"))::matches);
  }

  private static List<? extends ExpressionTree> invocationArgs(Tree tree) {
    if (tree instanceof NewClassTree) {
      return ((NewClassTree) tree).getArguments();
    }
    if (tree instanceof MethodInvocationTree) {
      return ((MethodInvocationTree) tree).getArguments();
    }
    return ImmutableList.of();
  }

  private static boolean isStringType(ExpressionTree arg, VisitorState state) {
    return ASTHelpers.isSameType(ASTHelpers.getType(arg), state.getSymtab().stringType, state);
  }
}

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
import static com.google.errorprone.util.ASTHelpers.getStartPosition;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.suffix;
import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.mu.util.CaseBreaker;
import com.google.mu.util.Substring;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LineMap;

@SuppressWarnings("restriction")
final class SourceUtils {
  private static final Substring.Pattern ARG_COMMENT =
      Substring.spanningInOrder("/*", "*/")
          .or(Substring.between(first("//"), suffix('\n').or(END)));
 
  static boolean hasArgComment(String source) {
	return ARG_COMMENT.in(source).isPresent();
  }

  static ImmutableList<String> normalizeForComparison(Collection<String> codes) {
    return codes.stream().map(SourceUtils::normalizeForComparison).collect(toImmutableList());
  }

  static String normalizeForComparison(String code) {
    return new CaseBreaker()
        .breakCase(code) // All punctuation chars gone
        .filter(s -> !s.equals("get")) // user.getId() should match e.g. user_id
        .filter(s -> !s.equals("is")) // job.isComplete() should match job_complete
        .map(Ascii::toLowerCase) // ignore case
        .collect(joining("_")); // delimit words
  }

  static ImmutableList<String> argsAsTexts(
      ExpressionTree methodName, List<? extends ExpressionTree> args, VisitorState state) {
    int position = state.getEndPosition(methodName);
    if (position < 0) {
      return ImmutableList.of();
    }
    LineMap lineMap = state.getPath().getCompilationUnit().getLineMap();
    if (lineMap == null) {
      return ImmutableList.of();
    }
    long startingLine = lineMap.getLineNumber(position);
    ImmutableList<Long> argLines =
        args.stream()
            .map(arg -> lineMap.getLineNumber(getStartPosition(arg)))
            .collect(toImmutableList());
    boolean isMultiline = argLines.stream().anyMatch(line -> line > startingLine);
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < args.size(); i++) {
      ExpressionTree arg = args.get(i);
      int next = state.getEndPosition(arg);
      if (next < 0) {
        return ImmutableList.of();
      }
      boolean lastArgOfLine = i == args.size() - 1 || argLines.get(i) < argLines.get(i + 1);
      int end = isMultiline && lastArgOfLine ? locateLineEnd(state.getSourceCode(), next) : next;
      builder.add(state.getSourceCode().subSequence(position, end).toString());
      position = end;
    }
    return builder.build();
  }

  private static int locateLineEnd(CharSequence source, int pos) {
    int end = pos;
    while (end < source.length() && source.charAt(end) != '\n') {
      end++;
    }
    return end;
  }
}

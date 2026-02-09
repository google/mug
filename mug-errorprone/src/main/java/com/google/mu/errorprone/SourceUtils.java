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
import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.google.mu.util.CaseBreaker;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LineMap;

@SuppressWarnings("restriction")
final class SourceUtils {
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
      ExpressionTree invocationStart, List<? extends ExpressionTree> args, VisitorState state) {
    int position = state.getEndPosition(invocationStart);
    if (position < 0) {
      return ImmutableList.of();
    }
    LineMap lineMap = state.getPath().getCompilationUnit().getLineMap();
    boolean inMultiline = inMultiline(position, args, lineMap);
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < args.size(); i++) {
      ExpressionTree arg = args.get(i);
      int next = state.getEndPosition(arg);
      if (next < 0) {
        return ImmutableList.of();
      }
      int end =
          inMultiline
                  && (i == args.size() - 1
                      || lineMap.getLineNumber(next)
                          < lineMap.getLineNumber(ASTHelpers.getStartPosition(args.get(i + 1))))
              ? locateLineEnd(state, next)
              : next;
      builder.add(state.getSourceCode().subSequence(position, end).toString());
      position = end;
    }
    return builder.build();
  }

  private static boolean inMultiline(
      int startPosition, List<? extends ExpressionTree> args, LineMap lineMap) {
    long baseLine = lineMap.getLineNumber(startPosition);
    return args.stream()
        .anyMatch(arg -> lineMap.getLineNumber(ASTHelpers.getStartPosition(arg)) > baseLine);
  }

  private static int locateLineEnd(VisitorState state, int pos) {
    CharSequence source = state.getSourceCode();
    int end = pos;
    while (end < source.length() && source.charAt(end) != '\n') {
      end++;
    }
    return end;
  }
}

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
package com.google.mu.safesql;

import static com.google.mu.safesql.SafeSqlUtils.checkArgument;
import static com.google.mu.util.Optionals.optionally;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.word;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.stream.Collectors.toList;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

/** Used to scan fragments around placeholders in a string template. */
final class TemplateFragmentScanner {
  private static final Substring.RepeatingPattern TOKENS =
      Stream.of(word(), first(c -> !Character.isWhitespace(c)))
          .collect(firstOccurrence())
          .repeatedly();
  private final List<Substring.Match> allTokens;
  private final Map<Integer, Integer> charIndexToTokenIndex;
  private final Deque<String> fragments;

  TemplateFragmentScanner(String template, Collection<String> fragments) {
    this.allTokens = TOKENS.match(template).collect(toList());
    this.charIndexToTokenIndex =
        BiStream.zip(allTokens.stream(), indexesFrom(0))
            .mapKeys(Substring.Match::index)
            .collect(Collectors::toMap);
    this.fragments = new ArrayDeque<>(fragments);
  }

  String nextFragment() {
    return fragments.pop();
  }

  Optional<String> nextFragmentIfQuoted(
      String open, Substring.Match placeholder, String close) {
    return optionally(
        placeholder.isImmediatelyBetween(open, close),
        () -> {
          String fragment = suffix(open).removeFrom(fragments.pop());
          fragments.push(prefix(close).removeFrom(fragments.pop()));
          return fragment;
        });
  }

  boolean lookaround(
      String leftPattern, Substring.Match placeholder, String rightPattern) {
    return lookahead(placeholder, rightPattern) && lookbehind(leftPattern, placeholder);
  }

  private boolean lookahead(Substring.Match placeholder, String rightPattern) {
    List<String> lookahead = TOKENS.from(rightPattern).collect(toList());
    int closingBraceIndex = placeholder.index() + placeholder.length() - 1;
    int nextTokenIndex = charIndexToTokenIndex.get(closingBraceIndex) + 1;
    return BiStream.zip(lookahead, allTokens.subList(nextTokenIndex, allTokens.size()))
            .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
            .count() == lookahead.size();
  }

  boolean lookbehind(String leftPattern, Substring.Match placeholder) {
    List<String> lookbehind = TOKENS.from(leftPattern).collect(toList());
    List<Substring.Match> leftTokens =
        allTokens.subList(0, charIndexToTokenIndex.get(placeholder.index()));
    return BiStream.zip(reverse(lookbehind), reverse(leftTokens))  // right-to-left
            .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
            .count() == lookbehind.size();
  }

  void rejectEscapeAfter(Substring.Match placeholder) {
    checkArgument(
        !lookahead(placeholder, "%' ESCAPE") && !lookahead(placeholder, "' ESCAPE"),
        "ESCAPE not supported after %s. Just leave the placeholder alone and SafeSql will auto escape.",
        placeholder);
  }

  private static <T> List<T> reverse(List<T> list) {
    return new AbstractList<T>() {
      @Override public T get(int i) {
        return list.get(list.size() - i - 1);
      }

      @Override public int size() {
        return list.size();
      }
    };
  }
}

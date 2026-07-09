/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Implements {@link Parser#anyOf}. */
final class OrParser<T> extends Parser<T> {
  private final List<Parser<T>> parsers;
  private final PrefixPruneTree<Parser<T>> pruneTree;

  OrParser(List<? extends Parser<? extends T>> candidates) {
    checkArgument(candidates.size() > 0, "parsers cannot be empty");
    this.parsers =
        candidates.stream()
            .flatMap( // flatten nested Or for more effective pruning.
                p -> covariant(p) instanceof OrParser<? extends T> or
                    ? or.parsers.stream()
                    : Stream.of(requireNonNull(p)))
            .map(Parser::<T>covariant)
            .toList();
    this.pruneTree = makePruneTreeIfUseful(parsers);
  }

  private OrParser(List<Parser<T>> parsers, PrefixPruneTree<Parser<T>> pruneTree) {
    this.parsers = parsers;
    this.pruneTree = pruneTree;
  }

  @Override MatchResult<T> skipAndMatch(
      Parser<?> skip, CharInput input, int start, ErrorContext context) {
    // All top-level parsers allow input to apply pre-skipping.
    start = skipIfAny(skip, input, start);
    List<Parser<T>> candidates = parsers;
    if (pruneTree != null) {
      candidates = pruneTree.pruneByPrefix(input, start);
      if (candidates.isEmpty()) {
        // When no candidate match by prefix, they must have failed right at 'start',
        // whichever candidate reports the error. So picking the first is the "farthest".
        // getFirst() will always succeed because we've already checked that parsers
        // cannot be empty.
        return parsers.getFirst().skipAndMatch(skip, input, start, context);
      }
    }
    MatchResult.Failure<?> farthestFailure = null;
    for (Parser<T> parser : candidates) {
      switch (parser.skipAndMatch(skip, input, start, context)) {
        case MatchResult.Success(int head, int tail, T value) -> {
          return new MatchResult.Success<>(head, tail, value);
        }
        case MatchResult.Failure<?> failure -> {
          if (farthestFailure == null || farthestFailure.frontier() < failure.frontier()) {
            farthestFailure = failure;
          }
        }
      }
    }
    return farthestFailure.safeCast();
  }

  @Override Set<String> computePrefixes() {
    List<String> result = new ArrayList<>();
    for (String prefix :
        iterateOnce(parsers.stream().flatMap(parser -> parser.getPrefixes().stream()).sorted())) {
      if (prefix.isEmpty()) { // short circuit upon no prefix.
        return super.computePrefixes();
      }
      // prefixes are sorted lexicographically, so if "a" is a prefix, "an", "any" are redundant.
      if (result.isEmpty() || !prefix.startsWith(result.getLast())) {
        result.add(prefix);
      }
    }
    return result.stream().collect(toUnmodifiableSet());
  }

  @Override Parser<?> ignoreReturn() {
    if (pruneTree == null) {
      @SuppressWarnings("unchecked")
      List<Parser<Object>> elided =
          (List) parsers.stream().map(Parser::ignoreReturn).collect(toUnmodifiableList());
      return new OrParser<Object>(elided, null);
    }
    return super.ignoreReturn();
  }

  private static <T> PrefixPruneTree<Parser<T>> makePruneTreeIfUseful(
      List<Parser<T>> parsers) {
    if (parsers.size() < 3) {
      return null;
    }
    var builder = new PrefixPruneTree.Builder<Parser<T>>();
    for (Parser<T> parser : parsers) {
      for (String prefix : parser.getPrefixes()) {
        builder.addPrefix(prefix, 8, parser); // peek for up to 8 chars lest diminishing return.
      }
    }
    return builder.numSurvivors() < parsers.size() ? builder.build() : null;
  }
}

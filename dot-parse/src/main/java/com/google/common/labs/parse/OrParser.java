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
import static java.lang.Character.isDigit;
import static java.lang.Character.isLowerCase;
import static java.lang.Character.isUpperCase;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.mu.util.stream.Joiner;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Implements {@link Parser#anyOf}. */
final class OrParser<T> extends Parser<T> {
  private final List<Parser<T>> parsers;
  // Built on the 2nd invocation, so allocate-run-discard parsers never pay for it.
  @LazyInit private volatile PrefixPruneTree<Parser<T>> prefixPruneTree;
  private boolean used;
  @LazyInit private volatile Set<String> expectedSymbols;

  OrParser(List<? extends Parser<? extends T>> candidates) {
    checkArgument(candidates.size() > 0, "parsers cannot be empty");
    this.parsers = candidates.stream()
        .flatMap( // flatten nested Or for more effective pruning.
            p ->
            covariant(p) instanceof OrParser<? extends T> or
                ? or.parsers.stream()
                : Stream.of(requireNonNull(p)))
        .map(Parser::<T>covariant)
        .toList();
  }

  @Override MatchResult<T> skipAndMatch(
      Skipper preskipper, Skipper innerSkipper, CharInput input, int start, ErrorContext context) {
    // All top-level parsers allow input to apply pre-skipping.
    start = Parser.skipIfAny(preskipper, input, start);
    List<Parser<T>> candidates = parsers;
    var prefixTree = getPrefixTree();
    // Pruning is transparent: a pruned candidate would have failed at `start`, so the linear
    // scan (1st invocation) and the pruned scan yield the same result and report the same error.
    if (prefixTree != null) {
      candidates = prefixTree.pruneByPrefix(input, start);
      if (candidates.isEmpty()) {
        return context.expectingInternal(this, start);
      }
    }
    MatchResult.Failure<T> farthestFailure = null;
    for (int i = 0, n = candidates.size(); i < n; i++) {
      Parser<T> parser = candidates.get(i);
      MatchResult<T> result = parser.skipAndMatch(null, innerSkipper, input, start, context);
      if (result instanceof MatchResult.Failure<T> failure) {
        if (farthestFailure == null || farthestFailure.frontier() < failure.frontier()) {
          farthestFailure = failure;
        }
      } else {
        return result;
      }
    }
    return farthestFailure.frontier() == start
        ? context.expectingInternal(this, start)
        : farthestFailure;
  }

  @Override Set<String> computePrefixes() {
    return Utils.toPrefixFreeSet(parsers.stream().flatMap(parser -> parser.getPrefixes().stream()));
  }

  @Override Parser<?> doIgnoreReturn() {
    List<Parser<?>> elided = parsers.stream().<Parser<?>>map(Parser::ignoreReturn).toList();
    return elided.equals(parsers) ? this : new OrParser<>(elided);
  }

  @Override BitSet computeBlocklist() {
    var result = (BitSet) parsers.get(0).getBlocklist().clone();
    for (int i = 1; i < parsers.size(); i++) {
      result.and(parsers.get(i).getBlocklist());
    }
    return result;
  }

  private PrefixPruneTree<Parser<T>> getPrefixTree() {
    PrefixPruneTree<Parser<T>> result = this.prefixPruneTree;
    if (result == null) {
      // Benign race: threads racing on the 1st invocation each take the linear scan once more;
      // threads racing on the 2nd build the same tree twice.
      if (used) {
        this.prefixPruneTree = result = makePruneTree(parsers);
      } else {
        used = true;
      }
    }
    return result;
  }

  private static <T> PrefixPruneTree<Parser<T>> makePruneTree(List<Parser<T>> parsers) {
    var builder = new PrefixPruneTree.Builder<Parser<T>>();
    for (Parser<T> parser : parsers) {
      for (String prefix : parser.getPrefixes()) {
        builder.addPrefix(prefix, 8, parser); // peek for up to 8 chars lest diminishing return.
      }
    }
    if (builder.numSurvivors() > 0 && builder.numSurvivors() != parsers.size()) {
      for (Parser<T> parser : parsers) {
        if (parser.getPrefixes().contains("")) {
          builder.addBlocklist(parser.getBlocklist(), parser);
        }
      }
    }
    // If no candidate is prunable, this is a leaf that returns all of them.
    return builder.build();
  }

  @Override Set<String> getExpectedSymbols() {
    Set<String> result = expectedSymbols;
    if (result == null) {
      expectedSymbols =
          result = parsers.stream()
              .flatMap(p -> p.getExpectedSymbols().stream().filter(s -> !s.isEmpty()))
              .collect(toUnmodifiableSet());
    }
    return result;
  }

  @Override public String toString() {
    Comparator<String> friendlyOrder = comparing((String s) -> {
      if (s.equals("EOF")) return 5;
      char c = s.charAt(0);
      if (isLowerCase(c)) return 1;
      if (isUpperCase(c)) return 2;
      if (isDigit(c)) return 3;
      return 4;
    });
    return getExpectedSymbols().stream()
        .sorted(friendlyOrder.thenComparing(naturalOrder()))
        .map(s -> s.equals(",") ? "comma (,)" : s)
        .collect(Joiner.on(", ").between("one of [", "]"));
  }
}

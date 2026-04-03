package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Implements {@link Parser#anyOf}. */
final class OrParser<T> extends Parser<T> {
  private final List<Parser<T>> parsers;
  private final boolean honorsSkipping;
  private final PrefixPruneTree<Parser<T>> pruneTree;

  OrParser(List<? extends Parser<? extends T>> candidates) {
    checkArgument(candidates.size() > 0, "parsers cannot be empty");
    this.parsers =
        candidates.stream()
            .flatMap( // flatten nested Or for more effective pruning.
                p -> covariant(p) instanceof OrParser<? extends T> or
                    ? or.parsers.stream()
                    : Stream.of(p))
            .map(Parser::<T>covariant)
            .collect(toUnmodifiableList());
    this.honorsSkipping = parsers.stream().allMatch(Parser::honorsSkipping);
    this.pruneTree = makePruneTreeIfUseful(parsers);
  }

  @Override MatchResult<T> skipAndMatch(
      Parser<?> skip, CharInput input, int start, ErrorContext context) {
    if (honorsSkipping) {
      start = skipIfAny(skip, input, start);
    }
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
          if (farthestFailure == null || farthestFailure.at() < failure.at()) {
            farthestFailure = failure;
          }
        }
      }
    }
    return farthestFailure.safeCast();
  }

  @Override boolean honorsSkipping() {
    return honorsSkipping;
  }

  @Override public Set<String> getPrefixes() {
    if (!hasConsistentSkippingMode(parsers)) {
      // inconsistent skipping means the candidate prefixes aren't usable.
      return super.getPrefixes();
    }
    List<String> prefixes = new ArrayList<>();
    for (String prefix :
        iterateOnce(parsers.stream().flatMap(parser -> parser.getPrefixes().stream()).sorted())) {
      if (prefix.isEmpty()) { // short circuit upon no prefix.
        return super.getPrefixes();
      }
      // prefixes are sorted lexicographically, so if "a" is a prefix, "an", "any" are redundant.
      if (prefixes.isEmpty() || !prefix.startsWith(prefixes.getLast())) {
        prefixes.add(prefix);
      }
    }
    return prefixes.stream().collect(toUnmodifiableSet());
  }

  private static <T> PrefixPruneTree<Parser<T>> makePruneTreeIfUseful(
      List<Parser<T>> parsers) {
    if (parsers.size() < 4) { // too few candidates, not worth it.
      return null;
    }
    if (hasConsistentSkippingMode(parsers)) {
      // If none skips, the prefixes can match literally.
      // If they all skip, we should have already applied skipping before pruning starts.
      var builder = new PrefixPruneTree.Builder<Parser<T>>();
      for (Parser<T> parser : parsers) {
        for (String prefix : parser.getPrefixes()) {
          builder.addPrefix(prefix, 8, parser); // peek for up to 8 chars lest diminishing return.
        }
      }
      if (builder.numSurvivors() * 2 < parsers.size()) { // with sufficient pruning power.
        return builder.build();
      }
    }
    return null;
  }

  private static boolean hasConsistentSkippingMode(List<? extends Parser<?>> parsers) {
    return parsers.stream().allMatch(Parser::honorsSkipping)
        || parsers.stream().noneMatch(Parser::honorsSkipping);
  }
}

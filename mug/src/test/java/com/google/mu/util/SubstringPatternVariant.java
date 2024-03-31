package com.google.mu.util;

import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;

import java.util.stream.Stream;

enum SubstringPatternVariant {
  AS_IS {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern;
    }
  },
  BETWEEN_EMPTIES {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.immediatelyBetween("", "");
    }
  },
  BETWEEN_EMPTIES_INCLUSIVE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.immediatelyBetween("", INCLUSIVE, "", INCLUSIVE);
    }
  },
  FOLLOWED_BY_EMPTY {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.followedBy("");
    }
  },
  NOT_BETWEEN_IMPOSSIBLES {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.notImmediatelyBetween(NON_EXISTENT, NON_EXISTENT);
    }
  },
  NOT_FOLLOWED_BY_IMPOSSIBLE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.notFollowedBy(NON_EXISTENT);
    }
  },
  TRIVIAL_BOUNDARY {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.separatedBy(CharPredicate.ANY);
    }
  },
  SKIP_NONE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.skip(0, 0);
    }
  },
  LIMIT_MAX {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.limit(Integer.MAX_VALUE);
    }
  },
  PEEK_EMPTY {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.peek(prefix(""));
    }
  },
  PEEK_END {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.peek(Substring.END);
    }
  },
  BEGINNING_THEN {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Substring.BEGINNING.then(pattern);
    }
  },
  PREFIX_THEN {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Substring.prefix("").then(pattern);
    }
  },
  EMPTY_THEN {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Substring.first("").then(pattern);
    }
  },
  ONLY_OCCURRENCE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern).collect(Substring.firstOccurrence());
    }
  },
  FIRST_OCCURRENCE_WITH_NONE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern, Substring.NONE).collect(Substring.firstOccurrence());
    }
  },
  FIRST_OCCURRENCE_WITH_NON_EXISTENT {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern, first(NON_EXISTENT)).collect(Substring.firstOccurrence());
    }
  },
  FIRST_OCCURRENCE_WITH_LAST_NON_EXISTENT {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern, Substring.last(NON_EXISTENT)).collect(Substring.firstOccurrence());
    }
  },
  FIRST_OCCURRENCE_WITH_NON_EXISTENT_PREFIX {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern, prefix(NON_EXISTENT)).collect(Substring.firstOccurrence());
    }
  },
  FIRST_OCCURRENCE_WITH_NON_EXISTENT_SUFFIX {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Stream.of(pattern, Substring.suffix(NON_EXISTENT)).collect(Substring.firstOccurrence());
    }
  },
  OR_NONE {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.or(Substring.NONE);
    }
  },
  NONE_OR {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return Substring.NONE.or(pattern);
    }
  },
  SELF_OR {
    @Override Substring.Pattern wrap(Substring.Pattern pattern) {
      return pattern.or(pattern);
    }
  },
  ;
  private static final String NON_EXISTENT = "You will never see this substring because I promise.";

  abstract Substring.Pattern wrap(Substring.Pattern pattern);
}
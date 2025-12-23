package com.google.mu.safesql;

import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.mu.util.Substring;
import com.google.mu.util.stream.MoreCollectors;

final class SqlTextOutline {
  private static final Substring.RepeatingPattern SECTIONS =
      Stream.of(
          enclosedBy("/*", "*/"),
          enclosedBy("--", "\n"),
          enclosedBy("`", "`"),
          enclosedBy("\"", "\""),
          // single-quoted string, with '' to escape. Closing single-quote may be missing
          first(Pattern.compile("'[^']*(?:''[^']*)*(?:'|(?=$))")))
      .collect(firstOccurrence())
      .repeatedly();

  private final TreeMap<Integer, Substring.Match> sections;

  SqlTextOutline(String sql) {
    this.sections = SECTIONS.match(sql).collect(MoreCollectors.toMap(Substring.Match::index, m -> m, TreeMap::new));
  }

  String getEnclosedBy(Substring.Match match) {
    return getEnclosedBy(match.index());
  }

  /**
   * If the given index falls into a range being commented, or quoted as string literal, returns the
   * starting quote like "--", "/*" or "'", otherwise returns empty string.
   */
  String getEnclosedBy(int index) {
    return Optional.ofNullable(sections.floorEntry(index))
        .map(Map.Entry::getValue)
        .filter(section -> index > section.index() && index < section.index() + section.length())
        .map(section -> {
          if (section.startsWith("--")) {
            return "--";
          }
          if (section.startsWith("/*")) {
            return "/*";
          }
          if (section.startsWith("'")) {
            return "'";
          }
          if (section.startsWith("`")) {
            return "`";
          }
          if (section.startsWith("\"")) {
            return "\"";
          }
          return "";
        })
        .orElse("");
  }

  private static Substring.Pattern enclosedBy(String open, String close) {
    return Substring.between(first(open), INCLUSIVE, first(close).or(Substring.END), INCLUSIVE);
  }
}

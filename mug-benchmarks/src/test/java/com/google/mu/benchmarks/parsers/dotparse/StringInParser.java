package com.google.mu.benchmarks.parsers.dotparse;

import static java.util.Comparator.reverseOrder;

import com.google.common.labs.parse.Parser;
import java.util.Collection;

/**
 * Equivalent of cats-parse StringIn / oneOf parsers in dot-parse.
 */
public final class StringInParser {

  /**
   * Returns a parser that matches any of the given {@code strings}, choosing the longest match.
   */
  public static Parser<String> stringIn(Collection<String> strings) {
    return strings.stream()
        .distinct()
        .sorted(reverseOrder())
        .map(Parser::string)
        .collect(Parser.or());
  }

  /**
   * Returns a parser that matches any of the given {@code strings} in the order they are provided.
   */
  public static Parser<Void> oneOf(Collection<String> strings) {
    return strings.stream()
        .map(Parser::string)
        .map(p -> p.thenReturn((Void) null))
        .collect(Parser.or());
  }

  private StringInParser() {}
}

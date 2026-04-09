package com.google.common.labs.markdown;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;

import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;

/**
 * Represents a markdown link in the format of {@code [label](url)}.
 *
 * <p>Properly handles escaping and ignores code and code blocks.
 *
 * @since 10.0
 */
public record MarkdownLink(String label, String url) {
  /**
   * Parser for a {@link MarkdownLink}.
   *
   * <p>Prefer using {@link #of} for parsing a single link.
   * This constant is meant to be composed with more complex parsers.
   */
  public static final Parser<MarkdownLink> PARSER = sequence(
      quotedByWithEscapes('[', ']', chars(1)),
      literally(quotedByWithEscapes('(', ')', chars(1))),
      MarkdownLink::new);

  private static final Parser<?> IGNORED = anyOf(
      one(is('\\'), "escape").followedBy(Parser.one(ANY, "escaped")),
      consecutive(is('`'), "backticks").flatMap(Parser::first),
      one(noneOf("\\[`"), "ignored char"));

  /**
   * Parses {@code link} into a {@link MarkdownLink}.
   *
   * @throws NullPointerException if {@code link} is null
   * @throws IllegalArgumentException if parsing failed
   */
  public static MarkdownLink of(String link) {
    return PARSER.parse(link);
  }

  /**
   * Scans {@code markdown} and <em>lazily</em> extracts all markdown links.
   *
   * @throws NullPointerException if {@code link} is null
   */
  public static Stream<MarkdownLink> scan(String markdown) {
    return PARSER.skipping(IGNORED).probe(markdown);
  }
}

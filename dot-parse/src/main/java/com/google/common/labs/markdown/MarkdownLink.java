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
package com.google.common.labs.markdown;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.nestedByWithEscapes;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static java.util.Objects.requireNonNull;

import java.io.Reader;
import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;

/**
 * Represents a markdown link in the format of {@code [label](url)}.
 *
 * <p>This class offers a light-weight parser to quickly {@link #scan extract}
 * markdown links from a markdown text or file.
 *
 * <p>While roughly equivalent to <pre>{@code
 * import com.google.mu.util.StringFormat;
 *
 * new StringFormat("[{label}]({url})")
 *     .scan(markdown, MarkdownLink::new);
 * }</pre>
 *
 * The parser properly handles escaping inside and outside of the link, and won't mistakenly
 * extract link-like syntax from backtick-quoted code or code blocks (recognizing code blocks
 * quoted by single backtick, double, triple or any number of consecutive backticks).
 *
 * @since 10.3
 */
public record MarkdownLink(String label, String url) {
  public MarkdownLink {
    requireNonNull(label);
    requireNonNull(url);
  }

  private static final Parser<String> ESCAPED =
      one(anyOf("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"), "escapable punctuation")
          .map(String::valueOf)
          .or(chars(1).map("\\"::concat));  // if the char isn't escapable

  private static final Parser<String> CODE =
      consecutive(is('`'), "backticks").flatMap(Parser::first).source();

  /**
   * Parser for a {@link MarkdownLink}.
   *
   * <p>Prefer using {@link #of} for parsing a single link and {@link #scan(String)}
   * for extracting multiple links. This constant is meant to be composed in larger parsers.
   */
  public static final Parser<MarkdownLink> PARSER = literally(
      sequence(
          nestedByWithEscapes('[', ']', ESCAPED),
          nestedByWithEscapes('(', ')', ESCAPED),
          MarkdownLink::new));

  private static final Parser<?> IGNORED = anyOf(
      one(is('\\'), "escape").then(chars(1)),
      CODE,
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
   * @throws NullPointerException if {@code markdown} is null
   */
  public static Stream<MarkdownLink> scan(String markdown) {
    return PARSER.skipping(IGNORED).probe(markdown);
  }

  /**
   * Scans {@code markdown} and <em>lazily</em> extracts all markdown links.
   *
   * @throws NullPointerException if {@code markdown} is null
   */
  public static Stream<MarkdownLink> scan(Reader markdown) {
    return PARSER.skipping(IGNORED).probe(markdown);
  }
}

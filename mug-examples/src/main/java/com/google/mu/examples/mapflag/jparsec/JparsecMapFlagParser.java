package com.google.mu.examples.mapflag.jparsec;

import static java.util.stream.Collectors.toMap;
import static org.jparsec.Parsers.or;
import static org.jparsec.Parsers.sequence;
import static org.jparsec.Scanners.DOUBLE_QUOTE_STRING;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.isChar;
import static org.jparsec.pattern.Patterns.regex;

import com.google.mu.examples.mapflag.ParseUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jparsec.Parser;

public final class JparsecMapFlagParser {
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");
  private static final Parser<?> COMMA = tok(isChar(','));

  private static final Parser<Object> SCALAR = or(
      tok(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
              .toScanner("number")
              .source())
          .map(ParseUtils::toNumber),
      tok(DOUBLE_QUOTE_STRING)
          .map(s -> ESCAPE.matcher(s.substring(1, s.length() - 1))
              .replaceAll("$1")));

  // sepEndBy(COMMA) parses 0+ items separated and optionally terminated by COMMA.
  private static final Parser<List<Map.Entry<String, Object>>> ENTRIES = sequence(
          tok(regex("[a-zA-Z0-9_-]+")
                  .toScanner("key")
                  .source())
              .followedBy(tok(isChar('='))),
          or(
              SCALAR,
              SCALAR.sepEndBy(COMMA)
                  .between(tok(isChar('[')), tok(isChar(']')))),
          Map::entry)
      .sepEndBy(COMMA)
      .between(
          WHITESPACES.skipMany().next(tok(isChar('{'))),
          tok(isChar('}')));

  public static Map<String, Object> parse(String input) {
    // Built outside the parser, which would wrap the duplicate-key exception in ParserException.
    return ENTRIES.parse(input).stream()
        .collect(
            toMap(
                Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> {
                  throw new IllegalArgumentException("Duplicate key");
                },
                LinkedHashMap::new));
  }

  // Scanner-level JParsec has no global whitespace skipping; each terminal is wrapped in tok().
  private static <T> Parser<T> tok(Parser<T> p) {
    return p.followedBy(WHITESPACES.skipMany());
  }
}

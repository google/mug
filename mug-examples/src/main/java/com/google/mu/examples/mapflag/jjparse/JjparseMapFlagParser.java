package com.google.mu.examples.mapflag.jjparse;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.mu.examples.mapflag.ParseUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import jjparse.StringParsing;
import jjparse.data.Product;
import jjparse.input.Input;

// StringParsing skips whitespace before every terminal and EOF.
public final class JjparseMapFlagParser extends StringParsing {
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");
  private static final JjparseMapFlagParser INSTANCE = new JjparseMapFlagParser();

  private final Parser<Character> comma = character(',');
  private final Parser<Object> scalar = choice(
      regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
          .map(ParseUtils::toNumber),
      regex("(?s)\"([^\"\\\\]|\\\\.)*\"")
          .map(s -> ESCAPE.matcher(s.substring(1, s.length() - 1))
              .replaceAll("$1")));
  private final Parser<Map<String, Object>> map = regex("[a-zA-Z0-9_-]+")
      .andl(character('='))
      .and(
          choice(
              scalar,
              // separate(comma) resets position when the element after comma fails,
              // leaving a trailing comma for comma.optional().
              scalar
                  .separate(comma)
                  .andl(comma.optional())
                  .between(character('['), character(']'))))
      .separate(comma)
      .andl(comma.optional())
      .between(character('{'), character('}'))
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Product::first, Product::second,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  private JjparseMapFlagParser() {
    // The default skip parser, regex("\\s+"), only matches ASCII whitespace.
    setSkipParser(regex("\\p{javaWhitespace}+"));
  }

  public static Map<String, Object> parse(String input) {
    return INSTANCE.parse(INSTANCE.map, Input.of("input", requireNonNull(input)))
        .getOrFail();
  }
}

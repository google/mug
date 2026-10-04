package com.google.mu.examples.mapflag.taker;

import static io.github.parseworks.taker.parsers.Chars.chr;
import static io.github.parseworks.taker.parsers.Combinators.not;
import static io.github.parseworks.taker.parsers.Combinators.oneOf;
import static io.github.parseworks.taker.parsers.Lexical.escapedString;
import static io.github.parseworks.taker.parsers.Lexical.regex;
import static io.github.parseworks.taker.parsers.Lexical.trimWhitespace;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.mu.examples.mapflag.ParseUtils;
import io.github.parseworks.taker.Taker;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TakerMapFlagParser {
  // Lexical.trim() in taker only skips spaces and tabs; trimWhitespace() also skips '\n' and '\r'.
  private static final Taker<Character> COMMA = trimWhitespace(chr(','));
  private static final Taker<Character> RBRACKET = trimWhitespace(chr(']'));
  private static final Taker<Character> RBRACE = trimWhitespace(chr('}'));

  private static final Taker<Object> SCALAR = oneOf(
      widen(trimWhitespace(regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))
          .map(ParseUtils::toNumber)),
      // Empty escape map unescapes any "\x" to "x".
      widen(trimWhitespace(escapedString('"', '\\', Map.of()))));

  private static final Taker<Map<String, Object>> MAP = trimWhitespace(regex("[a-zA-Z0-9_-]+"))
      .thenSkip(trimWhitespace(chr('=')))
      .then(
          oneOf(
              SCALAR,
              widen(
                  // zeroOrMoreSeparatedBy() does not backtrack over a trailing comma;
                  // peek(not(RBRACKET)) leaves the trailing comma for thenSkip(COMMA.optional()).
                  SCALAR
                      .zeroOrMoreSeparatedBy(COMMA.peek(not(RBRACKET)))
                      .thenSkip(COMMA.optional())
                      .between(trimWhitespace(chr('[')), RBRACKET))))
      .map(Map::entry)
      .zeroOrMoreSeparatedBy(COMMA.peek(not(RBRACE)))
      .thenSkip(COMMA.optional())
      .between(trimWhitespace(chr('{')), RBRACE)
      .map(entries -> entries.stream()
          .collect(
              toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (a, b) -> {
                    throw new IllegalArgumentException("Duplicate key");
                  },
                  LinkedHashMap::new)));

  public static Map<String, Object> parse(String input) {
    return MAP.parseAll(requireNonNull(input)).value();
  }

  @SuppressWarnings("unchecked") // Taker only produces A
  private static <A> Taker<A> widen(Taker<? extends A> taker) {
    return (Taker<A>) taker;
  }
}

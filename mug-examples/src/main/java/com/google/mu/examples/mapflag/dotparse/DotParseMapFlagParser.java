package com.google.mu.examples.mapflag.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parsers.UNSIGNED_DECIMAL;
import static com.google.mu.util.stream.BiCollectors.toMap;

import com.google.common.labs.parse.Parser;
import com.google.mu.examples.mapflag.ParseUtils;
import java.util.Map;

public final class DotParseMapFlagParser {
  private static final Parser<Object> SCALAR = anyOf(
      literally(one('-').optional(), UNSIGNED_DECIMAL)  // "-5": accepted, "- 5": rejected
          .source()
          .map(ParseUtils::toNumber),           // to Integer or Double
      quotedByWithEscapes('"', '"', chars(1))); // chars(1) unescapes any "\x" to "x"

  private static final Parser<Map<String, Object>> MAP = Parser.zeroOrMoreDelimited(
          consecutive("[a-zA-Z0-9_-]").followedBy("="),
          anyOf(
              SCALAR,
              SCALAR.zeroOrMoreDelimitedBy(",")
                  .optionallyFollowedBy(",")
                  .between("[", "]")),
          ",",
          toMap())
      .optionallyFollowedBy(",")
      .between("{", "}");

  public static Map<String, Object> parse(String input) {
    return MAP.parseSkipping(Character::isWhitespace, input);
  }
}

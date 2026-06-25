package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.bmpCodeUnit;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.first;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.mu.util.CharPredicate.anyOf;

import java.util.stream.Collectors;
import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

/** Fully RFC 8259-compliant JSON parser built with dot-parse. */
public final class JsonParser {
  private static final Parser<String> ESCAPED = anyOf(
      one('b').thenReturn("\b"),
      one('t').thenReturn("\t"),
      one('n').thenReturn("\n"),
      one('f').thenReturn("\f"),
      one('r').thenReturn("\r"),
      one('\\').thenReturn("\\"),
      one('/').thenReturn("/"),
      one('"').thenReturn("\""),
      one('u').then(bmpCodeUnit()).map(Character::toString));

  private static final Parser<JsonString> JSON_STRING =
      quotedByWithEscapes('"', '"', ESCAPED).map(JsonString::new);
  
  private static final Parser<?> INTEGER =
      anyOf(one('0'), sequence(one("[1-9]"), zeroOrMore("[0-9]")));

  private static final Parser<JsonNumber> JSON_NUMBER = 
      literally(
          sequence(
              anyOf(INTEGER, sequence(one('-'), INTEGER)),
              sequence(one('.'), digits()).orElse(null),
              sequence(caseInsensitive("e"), one("[+-]").orElse(null), digits()).orElse(null)))
        .source()
        .map(s -> new JsonNumber(Double.parseDouble(s)));
 
  private static final Parser<JsonNull> JSON_NULL = string("null").thenReturn(JsonNull.INSTANCE);
  private static final Parser<JsonBoolean> JSON_BOOLEAN = anyOf(JsonBoolean.values());

  public static final Parser<JsonValue> PARSER = define(
      me -> {
        Parser<JsonArray> jsonArray = me.zeroOrMoreDelimitedBy(",")
            .between("[", "]")
            .map(JsonArray::new);
        Parser<JsonObject> jsonObject = 
            Parser.zeroOrMoreDelimited(
                JSON_STRING.map(JsonString::value).followedBy(":"),
                me,
                ",",
                Collectors::toUnmodifiableMap)
              .between("{", "}")
              .map(JsonObject::new);
        return anyOf(JSON_NULL, JSON_BOOLEAN, JSON_NUMBER, JSON_STRING, jsonArray, jsonObject);
      });

  /**
   * Parses the given JSON string into a structured JsonValue, ignoring whitespaces.
   *
   * @throws Parser.ParseException if the input is not a valid JSON document.
   */
  public static JsonValue parse(String input) {
    return PARSER.parseSkipping(anyOf(" \t\r\n"), input);
  }

  public static final Parser<?> WHITESPACES_OR_COMMENTS = anyOf(
      consecutive("[ \t\r\n]"),
      sequence(string("//"), zeroOrMore("[^\n]")),
      sequence(string("/*"), first("*/")));

  /**
   * Parses the given JSON string (which may contain line or block comments) into a structured JsonValue.
   *
   * @throws Parser.ParseException if the input is not a valid JSON document.
   */
  public static JsonValue parseWithComments(String input) {
    return PARSER.skipping(WHITESPACES_OR_COMMENTS).parse(input);
  }
}

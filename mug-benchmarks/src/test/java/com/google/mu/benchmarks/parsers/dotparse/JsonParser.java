package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.bmpCodeUnit;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.mu.util.CharPredicate.anyOf;

import java.util.stream.Collectors;
import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

/** Fully RFC 8259-compliant JSON parser built with dot-parse. */
public final class JsonParser {
  // 1. JSON String Escape Sequences
  private static final Parser<String> ESCAPED = anyOf(
      one('b').thenReturn("\b"),
      one('t').thenReturn("\t"),
      one('n').thenReturn("\n"),
      one('f').thenReturn("\f"),
      one('r').thenReturn("\r"),
      one('\\').thenReturn("\\"),
      one('/').thenReturn("/"),
      one('"').thenReturn("\""),
      string("u").then(bmpCodeUnit()).map(Character::toString));

  private static final Parser<JsonString> JSON_STRING =
      quotedByWithEscapes('"', '"', ESCAPED).map(JsonString::new);
  
  private static final Parser<?> INTEGER =
      anyOf(string("0"), one("[1-9]").then(digits().orElse("")));

  // 2. JSON Number (Strict RFC 8259: no leading zeros in the integer part)
  private static final Parser<JsonNumber> JSON_NUMBER = 
      literally(
          sequence(
              anyOf(INTEGER, sequence(string("-"), INTEGER)),
              sequence(string("."), digits()).orElse(null),
              sequence(caseInsensitive("e"), one("[+-]").orElse(null), digits()).orElse(null)))
        .source()
        .map(s -> new JsonNumber(Double.parseDouble(s)));
 
  private static final Parser<JsonNull> JSON_NULL = string("null").thenReturn(JsonNull.INSTANCE);
  private static final Parser<JsonBoolean> JSON_BOOLEAN = anyOf(JsonBoolean.values());

  // 3. Recursive JSON Value Parser
  public static final Parser<JsonValue> PARSER = define(
      me -> {
        Parser<JsonArray> jsonArray = me.zeroOrMoreDelimitedBy(",")
            .between("[", "]")
            .map(JsonArray::new);
        Parser<JsonObject> jsonObject = 
            Parser.zeroOrMoreDelimited(
                JSON_STRING.followedBy(":").map(JsonString::value),
                me,
                ",",
                Collectors::toUnmodifiableMap)
            .between("{", "}").map(JsonObject::new);
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
}

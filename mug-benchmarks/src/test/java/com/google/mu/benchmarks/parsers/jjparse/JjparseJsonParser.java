package com.google.mu.benchmarks.parsers.jjparse;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonArray;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonBoolean;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonNull;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonNumber;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonObject;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.JsonString;
import java.util.LinkedHashMap;
import java.util.Map;
import jjparse.TextParsing;
import jjparse.data.Product;
import jjparse.input.Input;

/** Jar-Jar-Parse 2.0.0 JSON parser adopting the upstream JsonBenchmark/Example grammar. */
public final class JjparseJsonParser extends TextParsing {
  private static final JjparseJsonParser INSTANCE = new JjparseJsonParser();

  private final Parser<Void> whitespaceOrComments =
      regex("(?:[ \\t\\r\\n]+|//[^\\n]*|/\\*(?:[^*]|\\*+[^*/])*\\*+/)+")
          .ignore()
          .expect("whitespace or comment");

  private final Parser<JsonValue> parser;

  private JjparseJsonParser() {
    final Parser<String> string = regex("\"([^\"\\\\\\u0000-\\u001F]|\\\\.)*\"")
        .expect("a string")
        .map(JjparseJsonParser::strictUnescape);

    final Parser<JsonString> jsonString = string.map(JsonString::new);

    final Parser<JsonNumber> jsonNumber = regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
        .expect("a number")
        .map(Double::parseDouble)
        .map(JsonNumber::new);

    final Parser<JsonBoolean> jsonBoolean = choice(
            literal("true").map(ignore -> JsonBoolean.TRUE),
            literal("false").map(ignore -> JsonBoolean.FALSE))
        .expect("a boolean");

    final Parser<JsonNull> jsonNull =
        literal("null").expect("null").map(ignore -> JsonNull.INSTANCE);

    final Parser<JsonArray> jsonArray = lazy(this::parser)
        .separate(literal(","))
        .between(literal("["), literal("]").commit())
        .expect("an array")
        .map(JsonArray::new);

    final Parser<Product<String, JsonValue>> jsonObjectMember = string
        .expect("a property name")
        .andl(literal(":").expect("a property separator"))
        .and(lazy(this::parser));

    final Parser<JsonObject> jsonObject = jsonObjectMember
        .separate(literal(","))
        .between(literal("{"), literal("}").commit())
        .expect("an object")
        .map(tuples -> {
          Map<String, JsonValue> result = new LinkedHashMap<>();
          tuples.forEach(tuple -> result.put(tuple.first(), tuple.second()));
          return new JsonObject(result);
        });

    this.parser = choice(jsonNull, jsonNumber, jsonBoolean, jsonArray, jsonObject, jsonString);
  }

  private Parser<JsonValue> parser() {
    return parser;
  }

  public static JsonValue parse(String input) {
    return INSTANCE
        .parse(INSTANCE.parser, Input.of("json", input).withFilter(INSTANCE.whitespace))
        .getOrFail();
  }

  public static JsonValue parseWithComments(String input) {
    return INSTANCE
        .parse(INSTANCE.parser, Input.of("json", input).withFilter(INSTANCE.whitespaceOrComments))
        .getOrFail();
  }

  private static String strictUnescape(String quoted) {
    int end = quoted.length() - 1;
    if (quoted.indexOf('\\') == -1) {
      return quoted.substring(1, end);
    }
    StringBuilder sb = new StringBuilder(end - 1);
    for (int i = 1; i < end; i++) {
      char c = quoted.charAt(i);
      if (c == '\\') {
        if (i + 1 >= end) {
          throw new IllegalArgumentException("Trailing backslash");
        }
        char esc = quoted.charAt(++i);
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (i + 4 >= end) {
              throw new IllegalArgumentException("Invalid unicode escape");
            }
            String hex = quoted.substring(i + 1, i + 5);
            i += 4;
            sb.append((char) Integer.parseInt(hex, 16));
          }
          default -> throw new IllegalArgumentException("Invalid escape character: \\" + esc);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

package com.google.mu.benchmarks.parsers.taker;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.parsers.Chars;
import io.github.parseworks.taker.parsers.Combinators;
import io.github.parseworks.taker.parsers.Lexical;
import io.github.parseworks.taker.parsers.Numeric;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strictly RFC 8259-compliant Taker-based JSON parser. */
public final class TakerJsonParser {

  private TakerJsonParser() {}

  private static final Taker<Void> ws = Chars.chr(Character::isWhitespace).zeroOrMore().map(val -> (Void) null);

  private static <T> Taker<T> tok(Taker<T> p) {
    return p.thenSkip(ws);
  }

  private static Taker<Character> tok(char c) {
    return tok(Chars.chr(c));
  }

  private static Taker<String> tok(String s) {
    return tok(Lexical.string(s));
  }

  // Lenient RFC 8259 number parsing using Taker's native high-speed primitive field
  private static final Taker<JsonNumber> JSON_NUMBER = Numeric.doubleValue.map(JsonNumber::new);

  // Matches double-quoted string and unescapes strictly with exactly 1 allocation via Java's native Regex engine
  private static final Taker<String> STRING_LITERAL = Lexical.regex("\"([^\"\\\\]|\\\\.)*\"")
      .map(TakerJsonParser::strictUnescape);

  private static final Taker<JsonNull> JSON_NULL = Lexical.string("null").map(x -> JsonNull.INSTANCE);
  private static final Taker<JsonBoolean> JSON_BOOLEAN = Combinators.oneOf(
      Lexical.string("true").map(x -> JsonBoolean.TRUE),
      Lexical.string("false").map(x -> JsonBoolean.FALSE)
  );

  private static final Taker<JsonValue> PARSER = buildParser();

  @SuppressWarnings("unchecked")
  private static Taker<JsonValue> buildParser() {
    Taker<JsonValue> ref = Taker.ref();

    Taker<JsonNull> jsonNull = tok(JSON_NULL);
    Taker<JsonBoolean> jsonBoolean = tok(JSON_BOOLEAN);
    Taker<JsonNumber> jsonNumber = tok(JSON_NUMBER);
    Taker<JsonString> jsonString = tok(STRING_LITERAL.map(JsonString::new));

    Taker<JsonArray> jsonArray = tok('[')
        .then(
            ref.then(tok(',').then(ref).map((comma, v) -> v).zeroOrMore())
                .map((first, rest) -> {
                  List<JsonValue> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
                .optional()
                .map(opt -> opt.orElse(List.of()))
        )
        .thenSkip(tok(']'))
        .map((start, list) -> new JsonArray(list));

    Taker<Map.Entry<String, JsonValue>> member = tok(STRING_LITERAL)
        .thenSkip(tok(':'))
        .then(ref)
        .map((key, val) -> new AbstractMap.SimpleImmutableEntry<>(key, val));

    Taker<JsonObject> jsonObject = tok('{')
        .then(
            member.then(tok(',').then(member).map((comma, entry) -> entry).zeroOrMore())
                .map((first, rest) -> {
                  Map<String, JsonValue> map = new LinkedHashMap<>();
                  map.put(first.getKey(), first.getValue());
                  for (Map.Entry<String, JsonValue> entry : rest) {
                    map.put(entry.getKey(), entry.getValue());
                  }
                  return map;
                })
                .optional()
                .map(opt -> opt.orElse(Map.of()))
        )
        .thenSkip(tok('}'))
        .map((start, map) -> new JsonObject(map));

    Taker<JsonValue> valueParser = Combinators.oneOf(
        jsonNull.map(x -> (JsonValue) x),
        jsonBoolean.map(x -> (JsonValue) x),
        jsonNumber.map(x -> (JsonValue) x),
        jsonString.map(x -> (JsonValue) x),
        jsonArray.map(x -> (JsonValue) x),
        jsonObject.map(x -> (JsonValue) x)
    );

    ref.set(valueParser);

    return ws.then(valueParser).map((wsVal, r) -> r);
  }

  public static JsonValue parse(String input) {
    Result<JsonValue> result = PARSER.parseAll(input);
    if (result.matches() && result.input().isEof()) {
      return result.value();
    } else {
      throw new IllegalArgumentException("Parsing failed: " + result.toString());
    }
  }

  // Strict unescape complying with RFC 8259 Section 7 string constraints
  private static String strictUnescape(String quoted) {
    String text = quoted.substring(1, quoted.length() - 1);
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (i + 1 >= text.length()) {
          throw new IllegalArgumentException("Trailing backslash");
        }
        char esc = text.charAt(++i);
        switch (esc) {
          case '"': sb.append('"'); break;
          case '\\': sb.append('\\'); break;
          case '/': sb.append('/'); break;
          case 'b': sb.append('\b'); break;
          case 'f': sb.append('\f'); break;
          case 'n': sb.append('\n'); break;
          case 'r': sb.append('\r'); break;
          case 't': sb.append('\t'); break;
          case 'u':
            if (i + 4 >= text.length()) {
              throw new IllegalArgumentException("Invalid unicode escape");
            }
            String hex = text.substring(i + 1, i + 5);
            i += 4;
            sb.append((char) Integer.parseInt(hex, 16));
            break;
          default:
            throw new IllegalArgumentException("Invalid escape character: \\" + esc);
        }
      } else if (c < 0x20) {
        throw new IllegalArgumentException("Unescaped control character: 0x" + Integer.toHexString(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

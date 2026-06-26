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

  private static final Taker<Void> STANDARD_WS = Chars.chr(Character::isWhitespace).skipZeroOrMore();

  private static final Taker<Void> WHITESPACE = Chars.chr(Character::isWhitespace).map(x -> (Void) null);
  private static final Taker<Void> LINE_COMMENT = Lexical.string("//")
      .thenSkip(Chars.chr(c -> c != '\n').skipZeroOrMore())
      .thenSkip(Chars.chr('\n').optional())
      .map(x -> (Void) null);
  private static final Taker<Void> BLOCK_COMMENT = Lexical.string("/*")
      .thenSkip(Chars.chr(c -> true).zeroOrMoreUntil(Lexical.string("*/")))
      .map(x -> (Void) null);

  private static final Taker<Void> WS_WITH_COMMENTS = Combinators.oneOf(WHITESPACE, LINE_COMMENT, BLOCK_COMMENT)
      .skipZeroOrMore();

  private static <T> Taker<T> tok(Taker<T> p, Taker<Void> ws) {
    return p.thenSkip(ws);
  }

  private static Taker<Character> tok(char c, Taker<Void> ws) {
    return tok(Chars.chr(c), ws);
  }

  private static Taker<String> tok(String s, Taker<Void> ws) {
    return tok(Lexical.string(s), ws);
  }

  // Strictly RFC 8259-compliant JSON number parsing
  private static final Taker<JsonNumber> JSON_NUMBER = Lexical.regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
      .map(Double::parseDouble)
      .map(JsonNumber::new);

  // Matches double-quoted string and unescapes strictly with exactly 1 allocation via Java's native Regex engine
  private static final Taker<String> STRING_LITERAL = Lexical.regex("\"([^\"\\\\]|\\\\.)*\"")
      .map(TakerJsonParser::strictUnescape);

  private static final Taker<JsonNull> JSON_NULL = Lexical.string("null").map(x -> JsonNull.INSTANCE);
  private static final Taker<JsonBoolean> JSON_BOOLEAN = Combinators.oneOf(
      Lexical.string("true").map(x -> JsonBoolean.TRUE),
      Lexical.string("false").map(x -> JsonBoolean.FALSE)
  );

  private static final Taker<JsonValue> PARSER = buildParser(STANDARD_WS);
  private static final Taker<JsonValue> PARSER_WITH_COMMENTS = buildParser(WS_WITH_COMMENTS);

  @SuppressWarnings("unchecked")
  private static Taker<JsonValue> buildParser(Taker<Void> ws) {
    Taker<JsonValue> ref = Taker.ref();

    Taker<JsonNull> jsonNull = tok(JSON_NULL, ws);
    Taker<JsonBoolean> jsonBoolean = tok(JSON_BOOLEAN, ws);
    Taker<JsonNumber> jsonNumber = tok(JSON_NUMBER, ws);
    Taker<JsonString> jsonString = tok(STRING_LITERAL.map(JsonString::new), ws);

    Taker<JsonArray> jsonArray = tok('[', ws)
        .then(
            ref.then(tok(',', ws).skipThen(ref).zeroOrMore())
                .map((first, rest) -> {
                  List<JsonValue> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
                .optional()
                .map(opt -> opt.orElse(List.of()))
        )
        .thenSkip(tok(']', ws))
        .map((start, list) -> new JsonArray(list));

    Taker<Map.Entry<String, JsonValue>> member = tok(STRING_LITERAL, ws)
        .thenSkip(tok(':', ws))
        .then(ref)
        .map((key, val) -> new AbstractMap.SimpleImmutableEntry<>(key, val));

    Taker<JsonObject> jsonObject = tok('{', ws)
        .then(
            member.then(tok(',', ws).skipThen(member).zeroOrMore())
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
        .thenSkip(tok('}', ws))
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

    return ws.skipThen(valueParser);
  }

  public static JsonValue parse(String input) {
    Result<JsonValue> result = PARSER.parseAll(input);
    if (result.matches() && result.input().isEof()) {
      return result.value();
    } else {
      throw new IllegalArgumentException("Parsing failed: " + result.toString());
    }
  }

  public static JsonValue parseWithComments(String input) {
    Result<JsonValue> result = PARSER_WITH_COMMENTS.parseAll(input);
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

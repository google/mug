package com.google.mu.benchmarks.parsers.parsecj;

import static org.javafp.parsecj.Combinators.*;
import static org.javafp.parsecj.Text.*;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javafp.data.IList;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.input.Input;

/** Strictly RFC 8259-compliant ParsecJ-based JSON parser. */
public final class ParsecjJsonParser {

  private ParsecjJsonParser() {}

  // Helper to consume trailing whitespaces after a terminal matches
  private static <T> Parser<Character, T> tok(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }

  private static Parser<Character, String> tok(String s) {
    return tok(string(s));
  }

  private static Parser<Character, Character> tok(char c) {
    return tok(chr(c));
  }

  // Strict RFC 8259 number regex (no leading zeros, no trailing decimals, no prefix plus)
  private static final Parser<Character, JsonNumber> JSON_NUMBER =
      regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
          .map(s -> new JsonNumber(Double.parseDouble(s)));

  // Matches a quoted string, unescaping strictly
  private static final Parser<Character, JsonString> JSON_STRING =
      regex("\"([^\"\\\\]|\\\\.)*\"").map(s -> new JsonString(strictUnescape(s)));

  private static final Parser<Character, JsonValue> PARSER = buildParser();

  private static Parser<Character, JsonValue> buildParser() {
    Parser.Ref<Character, JsonValue> ref = Parser.ref();

    Parser<Character, JsonNull> jsonNull = tok(string("null")).then(retn(JsonNull.INSTANCE));
    Parser<Character, JsonBoolean> jsonBoolean = or(
        tok(string("true")).then(retn(JsonBoolean.TRUE)),
        tok(string("false")).then(retn(JsonBoolean.FALSE))
    );

    Parser<Character, JsonNumber> jsonNumber = tok(JSON_NUMBER);
    Parser<Character, JsonString> jsonString = tok(JSON_STRING);

    Parser<Character, JsonArray> jsonArray =
        tok('[')
            .then(ref.sepBy(tok(',')))
            .bind((IList<JsonValue> list) -> tok(']')
                .then(retn(new JsonArray(convertList(list)))));

    Parser<Character, Map.Entry<String, JsonValue>> member =
        tok(JSON_STRING).bind((JsonString key) -> tok(':')
            .then(ref)
            .map((JsonValue val) -> Map.entry(key.value(), val)));

    Parser<Character, JsonObject> jsonObject =
        tok('{')
            .then(member.sepBy(tok(',')))
            .bind((IList<Map.Entry<String, JsonValue>> list) -> tok('}')
                .map(close -> {
                  Map<String, JsonValue> map = new LinkedHashMap<>();
                  for (Map.Entry<String, JsonValue> entry : list) {
                    map.put(entry.getKey(), entry.getValue());
                  }
                  return new JsonObject(map);
                }));

    Parser<Character, JsonValue> valueParser = choice(
        jsonNull,
        jsonBoolean,
        jsonNumber,
        jsonString,
        jsonArray,
        jsonObject
    );

    ref.set(valueParser);

    // Consume leading whitespaces, parse value, and ensure we match until EOF
    return wspaces.then(valueParser).between(retn(null), eof());
  }

  private static <T> List<T> convertList(IList<T> ilist) {
    List<T> list = new ArrayList<>();
    for (T x : ilist) {
      list.add(x);
    }
    return list;
  }

  public static JsonValue parse(String input) {
    try {
      Reply<Character, JsonValue> reply = PARSER.parse(Input.of(input));
      if (reply.isOk()) {
        return reply.getResult();
      } else {
        throw new IllegalArgumentException("Parsing failed: " + reply.toString());
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
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

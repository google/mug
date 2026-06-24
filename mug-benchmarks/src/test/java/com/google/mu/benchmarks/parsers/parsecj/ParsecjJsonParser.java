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

  private static final Parser<Character, ?> STANDARD_WS = wspaces;

  private static final Parser<Character, ?> WHITESPACE = satisfy(Character::isWhitespace);
  private static final Parser<Character, ?> LINE_COMMENT = string("//").attempt()
      .then(satisfy((Character c) -> c != '\n').skipMany())
      .then(chr('\n').optionalOpt());
  private static final Parser<Character, ?> BLOCK_COMMENT = regex("/\\*([^*]|\\*+[^*/])*\\*+/");

  private static final Parser<Character, ?> WS_WITH_COMMENTS = choice(
      WHITESPACE,
      LINE_COMMENT,
      BLOCK_COMMENT
  ).skipMany();

  // Helper to consume trailing whitespaces after a terminal matches
  private static <T> Parser<Character, T> tok(Parser<Character, T> p, Parser<Character, ?> ws) {
    return p.bind(x -> ws.then(retn(x)));
  }

  private static Parser<Character, String> tok(String s, Parser<Character, ?> ws) {
    return tok(string(s), ws);
  }

  private static Parser<Character, Character> tok(char c, Parser<Character, ?> ws) {
    return tok(chr(c), ws);
  }

  // Strict RFC 8259 number regex (no leading zeros, no trailing decimals, no prefix plus)
  private static final Parser<Character, JsonNumber> JSON_NUMBER =
      regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
          .map(s -> new JsonNumber(Double.parseDouble(s)));

  // Matches a quoted string, capturing only the inner body to avoid double allocation
  private static final Parser<Character, JsonString> JSON_STRING =
      between(chr('"'), chr('"'), regex("([^\"\\\\]|\\\\.)*"))
          .map(s -> new JsonString(strictUnescape(s)));

  private static final Parser<Character, JsonValue> PARSER = buildParser(STANDARD_WS);
  private static final Parser<Character, JsonValue> PARSER_WITH_COMMENTS = buildParser(WS_WITH_COMMENTS);
  
  private static Parser<Character, JsonValue> buildParser(Parser<Character, ?> ws) {
    Parser.Ref<Character, JsonValue> ref = Parser.ref();

    Parser<Character, JsonNull> jsonNull = tok(string("null"), ws).then(retn(JsonNull.INSTANCE));
    Parser<Character, JsonBoolean> jsonBoolean = or(
        tok(string("true"), ws).then(retn(JsonBoolean.TRUE)),
        tok(string("false"), ws).then(retn(JsonBoolean.FALSE))
    );

    Parser<Character, JsonNumber> jsonNumber = tok(JSON_NUMBER, ws);
    Parser<Character, JsonString> jsonString = tok(JSON_STRING, ws);

    Parser<Character, JsonArray> jsonArray =
        tok('[', ws)
            .then(ref.sepBy(tok(',', ws)))
            .bind((IList<JsonValue> list) -> tok(']', ws)
                .then(retn(new JsonArray(convertList(list)))));

    Parser<Character, Map.Entry<String, JsonValue>> member =
        tok(JSON_STRING, ws).bind((JsonString key) -> tok(':', ws)
            .then(ref)
            .map((JsonValue val) -> Map.entry(key.value(), val)));

    Parser<Character, JsonObject> jsonObject =
        tok('{', ws)
            .then(member.sepBy(tok(',', ws)))
            .bind((IList<Map.Entry<String, JsonValue>> list) -> tok('}', ws)
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
    return ws.then(valueParser).between(retn(null), eof());
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

  public static JsonValue parseWithComments(String input) {
    try {
      Reply<Character, JsonValue> reply = PARSER_WITH_COMMENTS.parse(Input.of(input));
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
  private static String strictUnescape(String text) {
    if (text.indexOf('\\') == -1) {
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) < 0x20) {
          throw new IllegalArgumentException("Unescaped control character: 0x" + Integer.toHexString(text.charAt(i)));
        }
      }
      return text;
    }
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

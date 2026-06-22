package com.google.mu.benchmarks.parsers.jparsec;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.Terminals;
import org.jparsec.Tokens;
import org.jparsec.pattern.Patterns;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strictly RFC 8259-compliant JParsec-based JSON parser. */
public final class JparsecJsonParser {

  private JparsecJsonParser() {}

  private static final Terminals TERMS = Terminals
      .operators("{", "}", "[", "]", ":", ",")
      .words(Scanners.IDENTIFIER)
      .keywords("null", "true", "false")
      .build();

  // Strict RFC 8259 number pattern built natively using JParsec's Pattern API
  private static final org.jparsec.pattern.Pattern NUMBER_PATTERN;
  static {
    org.jparsec.pattern.Pattern zero = Patterns.isChar('0');
    org.jparsec.pattern.Pattern nonZero = Patterns.range('1', '9')
        .next(Patterns.range('0', '9').many());
    org.jparsec.pattern.Pattern integer = zero.or(nonZero);
    
    NUMBER_PATTERN = Patterns.isChar('-').optional()
        .next(integer)
        .next(Patterns.isChar('.').next(Patterns.range('0', '9').many1()).optional())
        .next(Patterns.among("eE").next(Patterns.among("+-").optional()).next(Patterns.range('0', '9').many1()).optional());
  }

  private static final Parser<String> NUMBER_SCANNER = Scanners.pattern(NUMBER_PATTERN, "number").source();

  private static final Parser<Object> TOKENIZER = Parsers.or(
      Scanners.DOUBLE_QUOTE_STRING.map(s -> Tokens.fragment(s, "string")),
      NUMBER_SCANNER.map(s -> Tokens.fragment(s, "number")),
      TERMS.tokenizer()
  );

  // Parses a string literal and unescapes it under strict RFC 8259 rules
  private static final Parser<String> STRING_LITERAL = Parsers.token(token -> {
    if (token.value() instanceof Tokens.Fragment) {
      Tokens.Fragment f = (Tokens.Fragment) token.value();
      if ("string".equals(f.tag())) {
        return strictUnescape(f.text());
      }
    }
    return null;
  });

  private static final Parser<JsonNumber> JSON_NUMBER = Parsers.token(token -> {
    if (token.value() instanceof Tokens.Fragment) {
      Tokens.Fragment f = (Tokens.Fragment) token.value();
      if ("number".equals(f.tag())) {
        return new JsonNumber(Double.parseDouble(f.text()));
      }
    }
    return null;
  });

  private static final Parser<JsonValue> JSON_PARSER;
  static {
    Parser.Reference<JsonValue> ref = Parser.newReference();

    Parser<JsonNull> jsonNull = TERMS.token("null").retn(JsonNull.INSTANCE);
    Parser<JsonBoolean> jsonBoolean = Parsers.or(
        TERMS.token("true").retn(JsonBoolean.TRUE),
        TERMS.token("false").retn(JsonBoolean.FALSE)
    );

    Parser<JsonString> jsonString = STRING_LITERAL.map(JsonString::new);

    Parser<JsonArray> jsonArray = ref.lazy()
        .sepBy(TERMS.token(","))
        .between(TERMS.token("["), TERMS.token("]"))
        .map(JsonArray::new);

    Parser<Map.Entry<String, JsonValue>> member = Parsers.sequence(
        STRING_LITERAL,
        TERMS.token(":"),
        ref.lazy(),
        (key, colon, val) -> Map.entry(key, val)
    );

    Parser<JsonObject> jsonObject = member
        .sepBy(TERMS.token(","))
        .between(TERMS.token("{"), TERMS.token("}"))
        .map(entries -> {
          Map<String, JsonValue> map = new LinkedHashMap<>();
          for (Map.Entry<String, JsonValue> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return new JsonObject(map);
        });

    Parser<JsonValue> jsonValue = Parsers.or(
        jsonNull,
        jsonBoolean,
        jsonString,
        JSON_NUMBER,
        jsonArray,
        jsonObject
    );

    ref.set(jsonValue);
    JSON_PARSER = jsonValue;
  }

  public static JsonValue parse(String input) {
    return JSON_PARSER.from(TOKENIZER, Scanners.WHITESPACES.optional()).parse(input);
  }

  // Strict unescape complying with RFC 8259 Section 7 string constraints
  private static String strictUnescape(String quoted) {
    String text = quoted.substring(1, quoted.length() - 1);
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

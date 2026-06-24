package com.google.mu.benchmarks.parsers.petitparser;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import org.petitparser.context.Context;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strictly RFC 8259-compliant PetitParser-based JSON parser. */
public final class PetitParserJsonParser {

  private PetitParserJsonParser() {}

  private static <T> List<T> getElements(List<Object> separatedList) {
    if (separatedList == null) return List.of();
    List<T> elements = new ArrayList<>();
    for (int i = 0; i < separatedList.size(); i += 2) {
      elements.add((T) separatedList.get(i));
    }
    return elements;
  }

  // Parses double-quoted JSON string and unescapes strictly
  private static Parser jsonString(Parser trimmer) {
    Parser quote = CharacterParser.of('"');
    Parser escape = CharacterParser.of('\\').seq(CharacterParser.any());
    Parser normal = CharacterParser.pattern("^\"\\\\");
    Parser content = escape.or(normal).star().flatten();
    return quote.seq(content).seq(quote)
        .map((List<Object> list) -> new JsonString(strictUnescape((String) list.get(1))))
        .trim(trimmer);
  }

  // Strict RFC 8259 number parser constructed with native combinators (no regex)
  private static Parser jsonNumber(Parser trimmer) {
    Parser sign = CharacterParser.of('-').optional();
    Parser zero = CharacterParser.of('0');
    Parser nonZero = CharacterParser.pattern("1-9").seq(CharacterParser.digit().star());
    Parser integer = zero.or(nonZero);
    Parser fraction = CharacterParser.of('.').seq(CharacterParser.digit().plus()).optional();
    Parser exponent = CharacterParser.pattern("eE")
        .seq(CharacterParser.pattern("+-").optional())
        .seq(CharacterParser.digit().plus())
        .optional();

    return sign.seq(integer).seq(fraction).seq(exponent).flatten()
        .map((String s) -> new JsonNumber(Double.parseDouble(s)))
        .trim(trimmer);
  }

  private static Parser jsonNull(Parser trimmer) {
    return StringParser.of("null").map(x -> JsonNull.INSTANCE).trim(trimmer);
  }

  private static Parser jsonBoolean(Parser trimmer) {
    return StringParser.of("true").map(x -> JsonBoolean.TRUE)
        .or(StringParser.of("false").map(x -> JsonBoolean.FALSE))
        .trim(trimmer);
  }

  private static Parser trimmer() {
    Parser whitespace = CharacterParser.whitespace();
    Parser lineComment = StringParser.of("//")
        .seq(CharacterParser.pattern("^\n").star())
        .seq(CharacterParser.of('\n').optional());
    Parser blockComment = StringParser.of("/*")
        .seq(CharacterParser.any().starLazy(StringParser.of("*/")))
        .seq(StringParser.of("*/"));
    return whitespace.or(lineComment).or(blockComment);
  }

  private static final Parser PARSER = buildParser(CharacterParser.whitespace());
  private static final Parser PARSER_WITH_COMMENTS = buildParser(trimmer());

  private static Parser buildParser(Parser trimmer) {
    SettableParser jsonValue = CharacterParser.none().settable();

    Parser jsonNull = jsonNull(trimmer);
    Parser jsonBoolean = jsonBoolean(trimmer);
    Parser jsonNumber = jsonNumber(trimmer);
    Parser jsonString = jsonString(trimmer);

    Parser jsonArray = CharacterParser.of('[').trim(trimmer)
        .seq(jsonValue.separatedBy(CharacterParser.of(',').trim(trimmer)).optional())
        .seq(CharacterParser.of(']').trim(trimmer))
        .map((List<Object> x) -> {
          List<Object> rawList = (List<Object>) x.get(1);
          List<JsonValue> list = getElements(rawList);
          return new JsonArray(list);
        });

    Parser member = jsonString(trimmer)
        .seq(CharacterParser.of(':').trim(trimmer))
        .seq(jsonValue)
        .map((List<Object> x) -> new AbstractMap.SimpleImmutableEntry<>(
            ((JsonString) x.get(0)).value(),
            (JsonValue) x.get(2)
        ));

    Parser jsonObject = CharacterParser.of('{').trim(trimmer)
        .seq(member.separatedBy(CharacterParser.of(',').trim(trimmer)).optional())
        .seq(CharacterParser.of('}').trim(trimmer))
        .map((List<Object> x) -> {
          List<Object> rawList = (List<Object>) x.get(1);
          List<Map.Entry<String, JsonValue>> entries = getElements(rawList);
          Map<String, JsonValue> map = new LinkedHashMap<>();
          for (Map.Entry<String, JsonValue> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return new JsonObject(map);
        });

    jsonValue.set(jsonNull
        .or(jsonBoolean)
        .or(jsonNumber)
        .or(jsonString)
        .or(jsonArray)
        .or(jsonObject));

    return jsonValue.end();
  }

  public static JsonValue parse(String input) {
    Result result = PARSER.parse(input);
    if (result.isFailure()) {
      throw new IllegalArgumentException("PetitParser parsing error: " + result.getMessage());
    }
    return (JsonValue) result.get();
  }

  public static JsonValue parseWithComments(String input) {
    Result result = PARSER_WITH_COMMENTS.parse(input);
    if (result.isFailure()) {
      throw new IllegalArgumentException("PetitParser parsing error: " + result.getMessage());
    }
    return (JsonValue) result.get();
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

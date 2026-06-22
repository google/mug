package com.google.mu.benchmarks.parsers.antlr4;

import org.antlr.v4.runtime.*;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import java.util.*;
import java.util.stream.Collectors;

/** Strictly RFC 8259-compliant ANTLR4-based JSON parser. */
public final class Antlr4JsonParser {

  private Antlr4JsonParser() {}

  public static JsonValue parse(String input) {
    JsonLexer lexer = new JsonLexer(CharStreams.fromString(input));
    lexer.removeErrorListeners();
    lexer.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new IllegalArgumentException("Lexing error: " + msg);
          }
        });

    CommonTokenStream tokens = new CommonTokenStream(lexer);
    JsonParser parser = new JsonParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new IllegalArgumentException("Parsing error: " + msg);
          }
        });

    JsonParser.EntryContext entry = parser.entry();
    return new Visitor().visit(entry.jsonValue());
  }

  private static class Visitor extends JsonBaseVisitor<JsonValue> {
    @Override
    public JsonValue visitJsonValue(JsonParser.JsonValueContext ctx) {
      return visit(ctx.getChild(0));
    }

    @Override
    public JsonValue visitJsonNull(JsonParser.JsonNullContext ctx) {
      return JsonNull.INSTANCE;
    }

    @Override
    public JsonValue visitJsonBoolean(JsonParser.JsonBooleanContext ctx) {
      return ctx.getText().equals("true") ? JsonBoolean.TRUE : JsonBoolean.FALSE;
    }

    @Override
    public JsonValue visitJsonNumber(JsonParser.JsonNumberContext ctx) {
      return new JsonNumber(Double.parseDouble(ctx.getText()));
    }

    @Override
    public JsonValue visitJsonString(JsonParser.JsonStringContext ctx) {
      return new JsonString(strictUnescape(ctx.getText()));
    }

    @Override
    public JsonValue visitJsonArray(JsonParser.JsonArrayContext ctx) {
      List<JsonValue> list = ctx.jsonValue().stream()
          .map(this::visit)
          .collect(Collectors.toList());
      return new JsonArray(list);
    }

    @Override
    public JsonValue visitJsonObject(JsonParser.JsonObjectContext ctx) {
      Map<String, JsonValue> map = new LinkedHashMap<>();
      for (JsonParser.MemberContext m : ctx.member()) {
        String key = strictUnescape(m.jsonString().getText());
        JsonValue value = visit(m.jsonValue());
        map.put(key, value);
      }
      return new JsonObject(map);
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

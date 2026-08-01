package com.google.mu.benchmarks.parsers.autumn;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import norswap.autumn.Autumn;
import norswap.autumn.Grammar;
import norswap.autumn.ParseOptions;
import norswap.autumn.ParseResult;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** JSON and JSON-with-Comments parser implemented using Autumn. */
public final class AutumnJsonParser {

  // =========================================================================
  // 1. Standard JSON Grammar (No Comments)
  // =========================================================================
  public static final class JsonGrammar extends Grammar {
    public rule whitespace = set(" \t\r\n").at_least(0);
    
    // Set ws before any other rule fields are initialized.
    { ws = whitespace; }

    public rule integer =
        choice('0', digit.at_least(1));

    public rule fractional =
        seq('.', digit.at_least(1));

    public rule exponent =
        seq(set("eE"), set("+-").opt(), digit.at_least(1));

    public rule number =
        seq(opt('-'), integer, fractional.opt(), exponent.opt())
        .push($ -> new JsonNumber(Double.parseDouble($.str())))
        .word();

    public rule string_char = choice(
        seq(set('"', '\\').not(), range('\u0000', '\u001F').not(), any),
        seq('\\', set("\\\"/bfnrt")),
        seq(str("\\u"), hex_digit, hex_digit, hex_digit, hex_digit));

    public rule string_content =
        string_char.at_least(0)
        .push($ -> unescape($.str()));

    public rule string =
        seq('"', string_content, '"')
        .push($ -> new JsonString((String) $.$[0]))
        .word();

    public rule LBRACE   = word("{");
    public rule RBRACE   = word("}");
    public rule LBRACKET = word("[");
    public rule RBRACKET = word("]");
    public rule COLON    = word(":");
    public rule COMMA    = word(",");

    public rule value = lazy(() -> choice(
        string,
        number,
        this.object,
        this.array,
        word("true")  .as_val(JsonBoolean.TRUE),
        word("false") .as_val(JsonBoolean.FALSE),
        word("null")  .as_val(JsonNull.INSTANCE)));

    public rule pair =
        seq(string, COLON, value)
        .push($ -> $.$);

    public rule object =
        seq(LBRACE, pair.sep(0, COMMA), RBRACE)
        .push($ -> {
          Map<String, JsonValue> map = new LinkedHashMap<>();
          for (Object p : $.$) {
            Object[] pairArr = (Object[]) p;
            JsonString key = (JsonString) pairArr[0];
            JsonValue val = (JsonValue) pairArr[1];
            map.put(key.value(), val);
          }
          return new JsonObject(map);
        });

    public rule array =
        seq(LBRACKET, value.sep(0, COMMA), RBRACKET)
        .push($ -> {
          List<JsonValue> list = Arrays.stream($.$)
              .map(x -> (JsonValue) x)
              .collect(Collectors.toList());
          return new JsonArray(list);
        });

    public rule root = seq(ws, value);

    @Override public rule root() {
        return root;
    }
  }

  // =========================================================================
  // 2. JSON with Comments Grammar
  // =========================================================================
  public static final class JsonWithCommentsGrammar extends Grammar {
    public rule comment = choice(
        seq("/*", seq(not("*/"), any).at_least(0), "*/"),
        seq("//", seq(not(set("\r\n")), any).at_least(0), set("\r\n").opt())
    );

    public rule whitespaceWithComments = 
        choice(set(" \t\r\n"), comment).at_least(0);

    // Set ws before any other rule fields are initialized.
    { ws = whitespaceWithComments; }

    public rule integer =
        choice('0', digit.at_least(1));

    public rule fractional =
        seq('.', digit.at_least(1));

    public rule exponent =
        seq(set("eE"), set("+-").opt(), digit.at_least(1));

    public rule number =
        seq(opt('-'), integer, fractional.opt(), exponent.opt())
        .push($ -> new JsonNumber(Double.parseDouble($.str())))
        .word();

    public rule string_char = choice(
        seq(set('"', '\\').not(), range('\u0000', '\u001F').not(), any),
        seq('\\', set("\\\"/bfnrt")),
        seq(str("\\u"), hex_digit, hex_digit, hex_digit, hex_digit));

    public rule string_content =
        string_char.at_least(0)
        .push($ -> unescape($.str()));

    public rule string =
        seq('"', string_content, '"')
        .push($ -> new JsonString((String) $.$[0]))
        .word();

    public rule LBRACE   = word("{");
    public rule RBRACE   = word("}");
    public rule LBRACKET = word("[");
    public rule RBRACKET = word("]");
    public rule COLON    = word(":");
    public rule COMMA    = word(",");

    public rule value = lazy(() -> choice(
        string,
        number,
        this.object,
        this.array,
        word("true")  .as_val(JsonBoolean.TRUE),
        word("false") .as_val(JsonBoolean.FALSE),
        word("null")  .as_val(JsonNull.INSTANCE)));

    public rule pair =
        seq(string, COLON, value)
        .push($ -> $.$);

    public rule object =
        seq(LBRACE, pair.sep(0, COMMA), RBRACE)
        .push($ -> {
          Map<String, JsonValue> map = new LinkedHashMap<>();
          for (Object p : $.$) {
            Object[] pairArr = (Object[]) p;
            JsonString key = (JsonString) pairArr[0];
            JsonValue val = (JsonValue) pairArr[1];
            map.put(key.value(), val);
          }
          return new JsonObject(map);
        });

    public rule array =
        seq(LBRACKET, value.sep(0, COMMA), RBRACKET)
        .push($ -> {
          List<JsonValue> list = Arrays.stream($.$)
              .map(x -> (JsonValue) x)
              .collect(Collectors.toList());
          return new JsonArray(list);
        });

    public rule root = seq(ws, value);

    @Override public rule root() {
        return root;
    }
  }

  private static String unescape(String literal) {
    return com.google.mu.benchmarks.parsers.BenchmarkInputs.unescape(literal);
  }

  private static final JsonGrammar STANDARD_GRAMMAR = new JsonGrammar();
  private static final JsonWithCommentsGrammar COMMENTS_GRAMMAR = new JsonWithCommentsGrammar();

  public static JsonValue parse(String input) {
    ParseResult result = Autumn.parse(STANDARD_GRAMMAR.root, input, ParseOptions.get());
    if (!result.fullMatch) {
      throw new RuntimeException("Parse failed");
    }
    return (JsonValue) result.topValue();
  }

  public static JsonValue parseWithComments(String input) {
    ParseResult result = Autumn.parse(COMMENTS_GRAMMAR.root, input, ParseOptions.get());
    if (!result.fullMatch) {
      throw new RuntimeException("Parse failed");
    }
    return (JsonValue) result.topValue();
  }
}

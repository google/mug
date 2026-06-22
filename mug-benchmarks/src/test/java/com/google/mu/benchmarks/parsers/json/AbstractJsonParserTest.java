package com.google.mu.benchmarks.parsers.json;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import org.junit.Test;

/** Shared test suite to guarantee semantic equivalence across all JSON parsers. */
public abstract class AbstractJsonParserTest {

  protected abstract JsonValue parse(String input) throws Exception;

  @Test
  public void parseNull() throws Exception {
    assertThat(parse("null")).isEqualTo(JsonNull.INSTANCE);
  }

  @Test
  public void parseBoolean() throws Exception {
    assertThat(parse("true")).isEqualTo(JsonBoolean.TRUE);
    assertThat(parse("false")).isEqualTo(JsonBoolean.FALSE);
  }

  @Test
  public void parseNumbers() throws Exception {
    assertThat(parse("123")).isEqualTo(new JsonNumber(123.0));
    assertThat(parse("-1.23e-4")).isEqualTo(new JsonNumber(-1.23e-4));
  }

  @Test
  public void parseInvalidNumbers_throwsException() {
    assertThrows(Exception.class, () -> parse("01"));
    assertThrows(Exception.class, () -> parse("+123"));
    assertThrows(Exception.class, () -> parse("1."));
  }

  @Test
  public void parseStrings() throws Exception {
    assertThat(parse("\"hello \\\"world\\\"\"")).isEqualTo(new JsonString("hello \"world\""));
    assertThat(parse("\"\\" + "uD83D\\" + "uDE00\"")).isEqualTo(new JsonString("\uD83D\uDE00"));
  }

  @Test
  public void parseStringWithAllEscapes() throws Exception {
    // Tests all RFC 8259 allowed escape sequences: \", \\, \/, \b, \f, \n, \r, \t, and unicode escapes
    String json = "\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\" + "u0041\"";
    String expected = "\" \\ / \b \u000c \n \r \t A";
    assertThat(parse(json)).isEqualTo(new JsonString(expected));
  }

  @Test
  public void parseStringWithInvalidEscapes_throwsException() {
    // Invalid escape character
    assertThrows(Exception.class, () -> parse("\"\\x\""));
    // Truncated unicode escape
    assertThrows(Exception.class, () -> parse("\"\\" + "u002\""));
    // Non-hex character in unicode escape
    assertThrows(Exception.class, () -> parse("\"\\" + "u002g\""));
    // Trailing backslash / unterminated escape
    assertThrows(Exception.class, () -> parse("\"\\\""));
    // Unescaped control character (< 0x20)
    assertThrows(Exception.class, () -> parse("\"\n\""));
  }

  @Test
  public void parseEmptyContainers() throws Exception {
    assertThat(parse("[]")).isEqualTo(new JsonArray(List.of()));
    assertThat(parse("{}")).isEqualTo(new JsonObject(Map.of()));
  }

  @Test
  public void parseHeterogeneousList() throws Exception {
    JsonValue result = parse("""
        [1, "two", null, true, [10, 20], {"x": 9.5}]
        """);
    
    assertThat(result.stream())
        .containsExactly(
            new JsonNumber(1.0),
            new JsonString("two"),
            JsonNull.INSTANCE,
            JsonBoolean.TRUE,
            new JsonArray(List.<JsonValue>of(new JsonNumber(10.0), new JsonNumber(20.0))),
            new JsonObject(Map.<String, JsonValue>of("x", new JsonNumber(9.5)))
        )
        .inOrder();
  }

  @Test
  public void parseNestedLists() throws Exception {
    JsonValue result = parse("""
        [
          [1, 2],
          [3, 4]
        ]
        """);

    assertThat(result.stream().map(Object::toString))
        .containsExactly("[1.0, 2.0]", "[3.0, 4.0]")
        .inOrder();
  }

  @Test
  public void parseHeterogeneousMap() throws Exception {
    JsonObject result = (JsonObject) parse("""
        {
          "a": 1,
          "b": "two",
          "c": null,
          "d": true,
          "e": [10, 20],
          "f": {"x": 9.5}
        }
        """);

    assertThat(result.members()).containsExactly(
        "a", new JsonNumber(1.0),
        "b", new JsonString("two"),
        "c", JsonNull.INSTANCE,
        "d", JsonBoolean.TRUE,
        "e", new JsonArray(List.<JsonValue>of(new JsonNumber(10.0), new JsonNumber(20.0))),
        "f", new JsonObject(Map.<String, JsonValue>of("x", new JsonNumber(9.5)))
    );
  }

  @Test
  public void parseNestedMaps() throws Exception {
    JsonObject result = (JsonObject) parse("""
        {
          "outer": {
            "inner": 42
          }
        }
        """);

    assertThat(result.members()).containsExactly(
        "outer", new JsonObject(Map.<String, JsonValue>of("inner", new JsonNumber(42.0)))
    );
  }

  @Test
  public void parseListOfMapOfLists() throws Exception {
    JsonValue result = parse("""
        [
          {
            "a": [1, 2]
          },
          {
            "b": [3, 4]
          }
        ]
        """);

    assertThat(result.stream()
        .flatMap(JsonValue::entryStream)
        .flatMap(entry -> entry.getValue().stream())
        .map(val -> ((JsonNumber) val).value()))
        .containsExactly(1.0, 2.0, 3.0, 4.0)
        .inOrder();
  }

  @Test
  public void parseMapOfMaps() throws Exception {
    JsonObject result = (JsonObject) parse("""
        {
          "level1": {
            "level2": {
              "level3": "deep"
            }
          }
        }
        """);

    assertThat(result.members()).containsExactly(
        "level1", new JsonObject(Map.<String, JsonValue>of(
            "level2", new JsonObject(Map.<String, JsonValue>of(
                "level3", new JsonString("deep")
            ))
        ))
    );
  }

  @Test
  public void parseComplexHeterogeneousNesting() throws Exception {
    String json = """
        {
          "id": "item-123",
          "active": true,
          "tags": ["featured", "new"],
          "metadata": {
            "score": 9.5,
            "reviews": [
              {"user": "alice", "rating": 5},
              {"user": "bob", "rating": 4}
            ]
          }
        }
        """;

    JsonObject result = (JsonObject) parse(json);

    assertThat(result.members()).containsExactly(
        "id", new JsonString("item-123"),
        "active", JsonBoolean.TRUE,
        "tags", new JsonArray(List.<JsonValue>of(new JsonString("featured"), new JsonString("new"))),
        "metadata", new JsonObject(Map.<String, JsonValue>of(
            "score", new JsonNumber(9.5),
            "reviews", new JsonArray(List.<JsonValue>of(
                new JsonObject(Map.<String, JsonValue>of("user", new JsonString("alice"), "rating", new JsonNumber(5.0))),
                new JsonObject(Map.<String, JsonValue>of("user", new JsonString("bob"), "rating", new JsonNumber(4.0)))
            ))
        ))
    );
  }

  @Test
  public void parseInvalidJson_throwsException() {
    assertThrows(Exception.class, () -> parse("[1, 2, ]"));
    assertThrows(Exception.class, () -> parse("{\"a\": 1, }"));
    assertThrows(Exception.class, () -> parse("{a: 1}"));
  }
}

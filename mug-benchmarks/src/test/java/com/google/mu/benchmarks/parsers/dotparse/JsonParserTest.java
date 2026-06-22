package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JsonParserTest {

  @Test
  public void parseNull() {
    assertThat(JsonParser.parse("null")).isEqualTo(JsonNull.INSTANCE);
  }

  @Test
  public void parseBoolean() {
    assertThat(JsonParser.parse("true")).isEqualTo(JsonBoolean.TRUE);
    assertThat(JsonParser.parse("false")).isEqualTo(JsonBoolean.FALSE);
  }

  @Test
  public void parseNumbers() {
    assertThat(JsonParser.parse("123")).isEqualTo(new JsonNumber(123.0));
    assertThat(JsonParser.parse("-1.23e-4")).isEqualTo(new JsonNumber(-1.23e-4));
  }

  @Test
  public void parseInvalidNumbers_throwsException() {
    assertThrows(ParseException.class, () -> JsonParser.parse("01"));
    assertThrows(ParseException.class, () -> JsonParser.parse("+123"));
    assertThrows(ParseException.class, () -> JsonParser.parse("1."));
  }

  @Test
  public void parseStrings() {
    assertThat(JsonParser.parse("\"hello \\\"world\\\"\"")).isEqualTo(new JsonString("hello \"world\""));
    assertThat(JsonParser.parse("\"\\uD83D\\uDE00\"")).isEqualTo(new JsonString("\uD83D\uDE00"));
  }

  @Test
  public void parseEmptyContainers() {
    assertThat(JsonParser.parse("[]")).isEqualTo(new JsonArray(List.of()));
    assertThat(JsonParser.parse("{}")).isEqualTo(new JsonObject(Map.of()));
  }

  @Test
  public void parseHeterogeneousList() {
    // Contains all possible JSON types: number, string, null, boolean, nested list, and nested map
    JsonValue result = JsonParser.parse("""
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
  public void parseNestedLists() {
    JsonValue result = JsonParser.parse("""
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
  public void parseHeterogeneousMap() {
    // Contains all possible JSON types: number, string, null, boolean, nested list, and nested map
    JsonObject result = (JsonObject) JsonParser.parse("""
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
  public void parseNestedMaps() {
    JsonObject result = (JsonObject) JsonParser.parse("""
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
  public void parseListOfMapOfLists() {
    JsonValue result = JsonParser.parse("""
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
  public void parseMapOfMaps() {
    JsonObject result = (JsonObject) JsonParser.parse("""
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
  public void parseComplexHeterogeneousNesting() {
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

    JsonObject result = (JsonObject) JsonParser.parse(json);

    // Verify the entire deeply nested heterogeneous document in a single, unified Map assertion!
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
    assertThrows(ParseException.class, () -> JsonParser.parse("[1, 2, ]"));
    assertThrows(ParseException.class, () -> JsonParser.parse("{\"a\": 1, }"));
    assertThrows(ParseException.class, () -> JsonParser.parse("{a: 1}"));
  }
}

package com.google.mu.benchmarks.parsers.javacc;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import com.google.mu.benchmarks.parsers.json.AbstractJsonParserTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public final class TomcatJsonParserTest extends AbstractJsonParserTest {

  @Override
  protected JsonValue parse(String input) throws Exception {
    TomcatJsonParser parser = new TomcatJsonParser(input);
    Object parsed = parser.parse();
    return convert(parsed);
  }

  private static JsonValue convert(Object parsed) {
    if (parsed == null) {
      return JsonNull.INSTANCE;
    } else if (parsed instanceof Boolean) {
      return (Boolean) parsed ? JsonBoolean.TRUE : JsonBoolean.FALSE;
    } else if (parsed instanceof Number) {
      return new JsonNumber(((Number) parsed).doubleValue());
    } else if (parsed instanceof String) {
      return new JsonString((String) parsed);
    } else if (parsed instanceof List) {
      List<?> list = (List<?>) parsed;
      return new JsonArray(list.stream().map(TomcatJsonParserTest::convert).toList());
    } else if (parsed instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) parsed;
      java.util.LinkedHashMap<String, JsonValue> convertedMap = new java.util.LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        convertedMap.put((String) entry.getKey(), convert(entry.getValue()));
      }
      return new JsonObject(convertedMap);
    }
    throw new IllegalArgumentException("Unknown type: " + parsed.getClass());
  }

  @Override
  @Test
  public void parseInvalidNumbers_throwsException() {
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("01"));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("+123"));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("1."));
  }

  @Override
  @Test
  public void parseStringWithInvalidEscapes_throwsException() {
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"\\x\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\u" + "\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\u1" + "\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\u12" + "\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\u123" + "\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\u123g" + "\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\\""));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("\"" + "hello\\"));
  }

  @Override
  @Test
  public void parseInvalidJson_throwsException() {
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("[1, 2, ]"));
    org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> parse("{\"a\": 1, }"));
  }

  @Test
  public void parseWithLineComment() throws Exception {
    JsonValue result = parse("""
        {
          // this is a comment
          "a": 1 // another comment
        }
        """);
    assertThat(result).isEqualTo(new JsonObject(java.util.Map.of("a", new JsonNumber(1.0))));
  }

  @Test
  public void parseWithBlockComment() throws Exception {
    JsonValue result = parse("""
        {
          /* this is a
             multi-line block comment */
          "a": 1,
          "b"/* inline comment */: 2
        }
        """);
    assertThat(result).isEqualTo(
        new JsonObject(java.util.Map.of("a", new JsonNumber(1.0), "b", new JsonNumber(2.0))));
  }

}

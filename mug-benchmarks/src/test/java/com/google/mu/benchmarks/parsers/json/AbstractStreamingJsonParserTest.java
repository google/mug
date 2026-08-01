package com.google.mu.benchmarks.parsers.json;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import org.junit.Test;

/** Base test class for streaming JSON parsers to ensure consistency and prevent code duplication. */
public abstract class AbstractStreamingJsonParserTest {

  protected abstract StreamingJsonParser parser();

  @Test
  public void parseSingleRecord_null() throws Exception {
    assertThat(parser().parse(new StringReader("null")).toList())
        .containsExactly(JsonNull.INSTANCE);
  }

  @Test
  public void parseSingleRecord_boolean() throws Exception {
    assertThat(parser().parse(new StringReader("true")).toList()).containsExactly(JsonBoolean.TRUE);
    assertThat(parser().parse(new StringReader("false")).toList()).containsExactly(JsonBoolean.FALSE);
  }

  @Test
  public void parseSingleRecord_number() throws Exception {
    assertThat(parser().parse(new StringReader("123.45")).toList()).containsExactly(new JsonNumber(123.45));
  }

  @Test
  public void parseSingleRecord_string() throws Exception {
    assertThat(parser().parse(new StringReader("\"hello \\n world\"")).toList())
        .containsExactly(new JsonString("hello \n world"));
  }

  @Test
  public void parseSingleRecord_array() throws Exception {
    assertThat(parser().parse(new StringReader("[1, 2, 3]")).toList())
        .containsExactly(new JsonArray(List.of(new JsonNumber(1.0), new JsonNumber(2.0), new JsonNumber(3.0))));
  }

  @Test
  public void parseSingleRecord_object() throws Exception {
    assertThat(parser().parse(new StringReader("{\"key\": \"value\"}")).toList())
        .containsExactly(new JsonObject(Map.of("key", new JsonString("value"))));
  }

  @Test
  public void parseMultipleRecords_streamed() throws Exception {
    String ndjson = """
        {"id": 1, "name": "Alice"}
        {"id": 2, "name": "Bob"}
        {"id": 3, "name": "Charlie"}
        """;

    List<JsonValue> expected = List.of(
        new JsonObject(Map.of("id", new JsonNumber(1.0), "name", new JsonString("Alice"))),
        new JsonObject(Map.of("id", new JsonNumber(2.0), "name", new JsonString("Bob"))),
        new JsonObject(Map.of("id", new JsonNumber(3.0), "name", new JsonString("Charlie")))
    );
    assertThat(parser().parse(new StringReader(ndjson)).toList()).isEqualTo(expected);
  }

  @Test
  public void parseMultipleRecords_heterogeneousStream() throws Exception {
    String input = "123 [1, 2] \"hello\" {\"x\": true} null";
    List<JsonValue> expected = List.of(
        new JsonNumber(123.0),
        new JsonArray(List.of(new JsonNumber(1.0), new JsonNumber(2.0))),
        new JsonString("hello"),
        new JsonObject(Map.of("x", JsonBoolean.TRUE)),
        JsonNull.INSTANCE
    );
    assertThat(parser().parse(new StringReader(input)).toList()).isEqualTo(expected);
  }

  @Test
  public void parseEmptyInput_returnsEmptyStream() throws Exception {
    assertThat(parser().parse(new StringReader("")).toList()).isEmpty();
    assertThat(parser().parse(new StringReader("   \n\t  ")).toList()).isEmpty();
  }

  @Test
  public void parseInvalidRecordInStream_throwsException() throws Exception {
    String input = "123 {invalid} null";
    
    // Verify Laziness: If we only consume the first element, it should succeed
    assertThat(parser().parse(new StringReader(input)).limit(1).toList())
        .containsExactly(new JsonNumber(123.0));

    // Verify Failure: Consuming the entire stream should throw
    assertThrows(
        RuntimeException.class,
        () -> parser().parse(new StringReader(input)).toList());
  }
}

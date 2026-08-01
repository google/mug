package com.google.mu.benchmarks.parsers.grappa;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

/** Unit tests for {@link GrappaJsonParser}. */
public class GrappaJsonParserTest {

  @Test
  public void testParseSimple() {
    JsonValue val = GrappaJsonParser.parse("{\"a\": [1, 2, null, true, false, \"hello\"]}");
    assertThat(val).isInstanceOf(JsonObject.class);
    JsonObject obj = (JsonObject) val;
    assertThat(obj.members()).containsKey("a");
    assertThat(obj.members().get("a")).isInstanceOf(JsonArray.class);
    JsonArray arr = (JsonArray) obj.members().get("a");
    assertThat(arr.elements()).containsExactly(
        new JsonNumber(1.0),
        new JsonNumber(2.0),
        JsonNull.INSTANCE,
        JsonBoolean.TRUE,
        JsonBoolean.FALSE,
        new JsonString("hello")
    ).inOrder();
  }

  @Test
  public void testParseWithComments() {
    JsonValue val = GrappaJsonParser.parseWithComments(
        "{\n" +
        "  // a comment\n" +
        "  \"a\": /* block comment */ [1, 2]\n" +
        "}");
    assertThat(val).isInstanceOf(JsonObject.class);
    JsonObject obj = (JsonObject) val;
    assertThat(obj.members()).containsKey("a");
    JsonArray arr = (JsonArray) obj.members().get("a");
    assertThat(arr.elements()).containsExactly(new JsonNumber(1.0), new JsonNumber(2.0)).inOrder();
  }

  @Test
  public void testParseFailure_invalidSyntax() {
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> GrappaJsonParser.parse("{\"a\": [1, 2, }")
    );
    assertThat(ex).hasMessageThat().contains("1:14");
  }
}

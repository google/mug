package com.google.mu.benchmarks.parsers.autumn;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

/** Unit tests for {@link AutumnJsonParser}. */
public class AutumnJsonParserTest {

  @Test
  public void testParseSimple() {
    JsonValue val = AutumnJsonParser.parse("{\"a\": [1, 2, null, true, false, \"hello\"]}");
    assertThat(val).isInstanceOf(JsonObject.class);
    JsonObject obj = (JsonObject) val;
    assertThat(obj.members().get("a")).isInstanceOf(JsonArray.class);
    JsonArray arr = (JsonArray) obj.members().get("a");
    assertThat(arr.elements().size()).isEqualTo(6);
  }

  @Test
  public void testParseWithComments() {
    JsonValue val = AutumnJsonParser.parseWithComments(
        "{\n" +
        "  // a comment\n" +
        "  \"a\": /* block comment */ [1, 2]\n" +
        "}");
    assertThat(val).isInstanceOf(JsonObject.class);
    JsonObject obj = (JsonObject) val;
    assertThat(obj.members().get("a")).isInstanceOf(JsonArray.class);
  }
}

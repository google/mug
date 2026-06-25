package com.google.mu.benchmarks.parsers.jparsec;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.json.AbstractJsonParserTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;

@RunWith(JUnit4.class)
public final class JparsecJsonWithCommentsParserTest extends AbstractJsonParserTest {
  @Override
  protected JsonValue parse(String input) {
    return JparsecJsonParser.parseWithComments(input);
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

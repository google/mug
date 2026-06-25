package com.google.mu.benchmarks.parsers.antlr4;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.AbstractJsonParserTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Antlr4JsonParserTest extends AbstractJsonParserTest {
  private final Antlr4JsonParser parser = new Antlr4JsonParser();

  @Override
  protected JsonValue parse(String input) {
    return parser.parse(input);
  }
}

package com.google.mu.benchmarks.parsers.parsecj;

import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.AbstractJsonParserTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParsecjJsonParserTest extends AbstractJsonParserTest {
  @Override
  protected JsonValue parse(String input) {
    return ParsecjJsonParser.parse(input);
  }
}

package com.google.mu.benchmarks.parsers.dotparse;

import com.google.mu.benchmarks.parsers.dotparse.JsonParser;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.AbstractJsonParserTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JsonParserTest extends AbstractJsonParserTest {
  @Override
  protected JsonValue parse(String input) {
    return JsonParser.parse(input);
  }
}

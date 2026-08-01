package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;
import org.parboiled.support.ParsingResult;

public class ParboiledUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    ParsingResult<Object> res = new ParboiledShowdown.UsPhoneFixture().run(input);
    if (!res.matched) {
      throw new Exception("failed");
    }
    return (String) res.resultValue;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected List<String> parseList(String input) throws Throwable {
    ParsingResult<Object> res = new ParboiledShowdown.UsPhoneListFixture().run(input);
    if (!res.matched) {
      throw new Exception("failed");
    }
    return (List<String>) res.resultValue;
  }
}

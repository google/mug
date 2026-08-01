package com.google.mu.benchmarks.parsers.dotparse;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;

public class DotParseUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) {
    return new DotParseShowdown.UsPhoneFixture().run(input);
  }

  @Override
  protected List<String> parseList(String input) {
    return new DotParseShowdown.UsPhoneListFixture().run(input);
  }
}

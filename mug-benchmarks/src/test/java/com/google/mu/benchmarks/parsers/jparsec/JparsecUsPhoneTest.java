package com.google.mu.benchmarks.parsers.jparsec;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;

public class JparsecUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    return new JparsecShowdown.UsPhoneFixture().run(input);
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    return new JparsecShowdown.UsPhoneListFixture().run(input);
  }
}

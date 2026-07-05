package com.google.mu.benchmarks.parsers.antlr4;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;

public class Antlr4UsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    return new Antlr4Showdown.UsPhoneFixture().run(input);
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    return new Antlr4Showdown.UsPhoneListFixture().run(input);
  }
}

package com.google.mu.benchmarks.parsers.betterparse;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;

public class BetterParseUsPhoneTest extends AbstractUsPhoneParserTest {
  private final BetterParseShowdown.UsPhoneFixture singleFixture = new BetterParseShowdown.UsPhoneFixture();
  private final BetterParseShowdown.UsPhoneListFixture listFixture = new BetterParseShowdown.UsPhoneListFixture();

  @Override
  protected String parseSingle(String input) throws Throwable {
    return singleFixture.run(input);
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    return listFixture.run(input);
  }
}

package com.google.mu.benchmarks.parsers.petitparser;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;
import org.petitparser.context.Result;

public class PetitParserUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    Result res = new PetitParserShowdown.UsPhoneFixture().run(input);
    if (!res.isSuccess()) {
      throw new Exception("failed: " + res.getMessage());
    }
    return (String) res.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected List<String> parseList(String input) throws Throwable {
    Result res = new PetitParserShowdown.UsPhoneListFixture().run(input);
    if (!res.isSuccess()) {
      throw new Exception("failed: " + res.getMessage());
    }
    return (List<String>) res.get();
  }
}

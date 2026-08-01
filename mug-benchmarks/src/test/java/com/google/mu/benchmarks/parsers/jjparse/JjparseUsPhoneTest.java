package com.google.mu.benchmarks.parsers.jjparse;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;
import jjparse.Parsing;

public class JjparseUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    Parsing<Character>.Result<String> res = new JjparseShowdown.UsPhoneFixture().run(input);
    if (!res.isSuccess()) {
      throw new Exception("failed");
    }
    return res.getOrFail();
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    Parsing<Character>.Result<List<String>> res = new JjparseShowdown.UsPhoneListFixture().run(input);
    if (!res.isSuccess()) {
      throw new Exception("failed");
    }
    return res.getOrFail();
  }
}

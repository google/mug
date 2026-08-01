package com.google.mu.benchmarks.parsers.taker;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import io.github.parseworks.taker.Result;
import java.util.List;

public class TakerUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    Result<String> res = new TakerShowdown.UsPhoneFixture().run(input);
    if (!res.matches() || !res.input().isEof()) {
      throw new Exception("failed: " + res);
    }
    return res.value();
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    Result<List<String>> res = new TakerShowdown.UsPhoneListFixture().run(input);
    if (!res.matches() || !res.input().isEof()) {
      throw new Exception("failed: " + res);
    }
    return res.value();
  }
}

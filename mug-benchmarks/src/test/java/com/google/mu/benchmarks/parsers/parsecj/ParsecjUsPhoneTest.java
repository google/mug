package com.google.mu.benchmarks.parsers.parsecj;

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest;
import java.util.List;
import org.javafp.parsecj.Reply;

public class ParsecjUsPhoneTest extends AbstractUsPhoneParserTest {

  @Override
  protected String parseSingle(String input) throws Throwable {
    Reply<Character, String> reply = new ParsecjShowdown.UsPhoneFixture().run(input);
    if (!reply.isOk()) {
      throw new Exception("failed: " + reply.getMsg());
    }
    return reply.getResult();
  }

  @Override
  protected List<String> parseList(String input) throws Throwable {
    Reply<Character, List<String>> reply = new ParsecjShowdown.UsPhoneListFixture().run(input);
    if (!reply.isOk()) {
      throw new Exception("failed: " + reply.getMsg());
    }
    return reply.getResult();
  }
}

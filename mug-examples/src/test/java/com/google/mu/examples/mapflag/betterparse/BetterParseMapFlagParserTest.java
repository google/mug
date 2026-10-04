package com.google.mu.examples.mapflag.betterparse;

import com.github.h0tk3y.betterParse.parser.ParseException;
import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BetterParseMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    try {
      return BetterParseMapFlagParser.INSTANCE.parse(input);
    } catch (Exception e) {
      // javac rejects catch (ParseException e): Kotlin's parse() doesn't declare it.
      if (e instanceof ParseException) {
        throw new IllegalArgumentException(e);
      }
      throw e;
    }
  }
}

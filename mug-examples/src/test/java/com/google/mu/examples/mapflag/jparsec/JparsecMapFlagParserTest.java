package com.google.mu.examples.mapflag.jparsec;

import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JparsecMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return JparsecMapFlagParser.parse(input);
  }
}

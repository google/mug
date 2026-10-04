package com.google.mu.examples.mapflag.petitparser;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.petitparser.context.ParseError;

@RunWith(JUnit4.class)
public class PetitParserMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return PetitParserMapFlagParser.parse(input);
  }

  @Test public void missingCommaBetweenEntries_throwsParseErrorWithPosition() {
    ParseError thrown = assertThrows(ParseError.class, () -> parse("{a = 1 b = 2}"));
    assertThat(thrown).hasMessageThat().isEqualTo("'}' expected");
    assertThat(thrown.getFailure().getPosition()).isEqualTo(7);
  }
}

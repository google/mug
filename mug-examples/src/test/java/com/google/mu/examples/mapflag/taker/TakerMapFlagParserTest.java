package com.google.mu.examples.mapflag.taker;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TakerMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return TakerMapFlagParser.parse(input);
  }

  @Test public void missingCommaBetweenEntries_reportsLineAndColumn() {
    RuntimeException thrown = assertThrows(RuntimeException.class, () -> parse("{a = 1 b = 2}"));
    assertThat(thrown).hasMessageThat().startsWith("Error: line 1 position 8");
  }
}

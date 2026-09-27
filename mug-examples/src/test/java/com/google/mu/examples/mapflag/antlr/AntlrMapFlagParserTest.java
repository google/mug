package com.google.mu.examples.mapflag.antlr;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AntlrMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return AntlrMapFlagParser.parse(input);
  }

  @Test public void missingCommaBetweenEntries_reportsOneBasedColumn() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> parse("{a = 1 b = 2}"));
    assertThat(thrown).hasMessageThat().isEqualTo("1:8 mismatched input 'b' expecting {',', '}'}");
  }
}

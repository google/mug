package com.google.mu.examples.mapflag.jjparse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.mu.examples.mapflag.AbstractMapFlagParserTest;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JjparseMapFlagParserTest extends AbstractMapFlagParserTest {
  @Override protected Map<String, Object> parse(String input) {
    return JjparseMapFlagParser.parse(input);
  }

  @Test public void missingCommaBetweenEntries_reportsInputNameAndPosition() {
    NoSuchElementException thrown =
        assertThrows(NoSuchElementException.class, () -> parse("{a = 1 b = 2}"));
    assertThat(thrown).hasMessageThat().startsWith("syntax error in input at line 1 and column 8:");
  }
}

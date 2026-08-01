package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.ScaledKeywordInputs;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DotParseScaledKeywordsTest {
  private static final Parser<Integer> KEYWORD =
      IntStream.range(0, ScaledKeywordInputs.NUM_KEYWORDS)
          .mapToObj(i -> Parser.string(ScaledKeywordInputs.KEYWORDS.get(i)).thenReturn(i))
          .collect(Parser.or());
  private static final Parser<List<Integer>> PARSER = KEYWORD.atLeastOnceDelimitedBy(",");

  @Test
  public void parse_validScaledKeywordsList() {
    List<Integer> result = PARSER.parse(ScaledKeywordInputs.KEYWORDS_LIST_CS);
    assertThat(result.size()).isEqualTo(1000);
  }

  @Test
  public void parse_invalidScaledKeywordsList_throwsException() {
    Parser.ParseException ex =
        assertThrows(
            Parser.ParseException.class,
            () -> PARSER.parse(ScaledKeywordInputs.KEYWORDS_LIST_INVALID));
    assertThat(ex.getMessage()).contains("kw9999");
  }
}

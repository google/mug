package com.google.mu.benchmarks.parsers.jparsec;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.jparsec.Parsers.or;

import com.google.mu.benchmarks.parsers.ScaledKeywordInputs;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jparsec.Parser;
import org.jparsec.Scanners;
import org.jparsec.error.ParserException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JparsecScaledKeywordsTest {
  private static final Parser<Integer> KEYWORD =
      or(
          IntStream.range(0, ScaledKeywordInputs.NUM_KEYWORDS)
              .mapToObj(i -> Scanners.string(ScaledKeywordInputs.KEYWORDS.get(i)).retn(i))
              .collect(Collectors.toList()));
  private static final Parser<List<Integer>> PARSER = KEYWORD.sepBy(Scanners.isChar(','));

  @Test
  public void parse_validScaledKeywordsList() {
    List<Integer> result = PARSER.parse(ScaledKeywordInputs.KEYWORDS_LIST_CS);
    assertThat(result.size()).isEqualTo(1000);
  }

  @Test
  public void parse_invalidScaledKeywordsList_throwsException() {
    assertThrows(
        ParserException.class,
        () -> PARSER.parse(ScaledKeywordInputs.KEYWORDS_LIST_INVALID));
  }
}

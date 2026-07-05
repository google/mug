package com.google.mu.benchmarks.parsers.taker;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.ScaledKeywordInputs;
import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.parsers.Chars;
import io.github.parseworks.taker.parsers.Combinators;
import io.github.parseworks.taker.parsers.Lexical;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TakerScaledKeywordsTest {
  private static final Taker<Integer> KEYWORD;
  private static final Taker<List<Integer>> PARSER;

  static {
    List<Taker<Integer>> list =
        IntStream.range(0, ScaledKeywordInputs.NUM_KEYWORDS)
            .mapToObj(i -> Lexical.string(ScaledKeywordInputs.KEYWORDS.get(i)).map(val -> i))
            .collect(Collectors.toList());
    KEYWORD = Combinators.oneOf(list.toArray(new Taker[0]));

    PARSER = KEYWORD
        .then(Chars.chr(',').then(KEYWORD).map((comma, kw) -> kw).zeroOrMore())
        .map((first, rest) -> {
          List<Integer> resList = new ArrayList<>();
          resList.add(first);
          resList.addAll(rest);
          return resList;
        });
  }

  @Test
  public void parse_validScaledKeywordsList() {
    Result<List<Integer>> res = PARSER.parseAll(ScaledKeywordInputs.KEYWORDS_LIST_CS);
    assertThat(res.matches()).isTrue();
    assertThat(res.value().size()).isEqualTo(1000);
  }

  @Test
  public void parse_invalidScaledKeywordsList_throwsException() {
    Result<List<Integer>> res = PARSER.parseAll(ScaledKeywordInputs.KEYWORDS_LIST_INVALID);
    assertThat(res.matches()).isFalse();
  }
}

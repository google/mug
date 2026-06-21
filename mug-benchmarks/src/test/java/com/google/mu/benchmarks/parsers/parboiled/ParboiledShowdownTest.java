package com.google.mu.benchmarks.parsers.parboiled;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.parboiled.support.ParsingResult;

public class ParboiledShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    ParsingResult<?> result = new ParboiledShowdown.IpFixture().run();
    assertThat(result.matched).isTrue();
    assertThat(result.resultValue).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    ParsingResult<?> result = new ParboiledShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result.matched).isTrue();
    assertThat(result.resultValue).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    ParsingResult<?> result = new ParboiledShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result.matched).isTrue();
    assertThat(result.resultValue).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseSensitive() {
    ParsingResult<?> result = new ParboiledShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result.matched).isTrue();
    List<Keyword> keywords = (List<Keyword>) result.resultValue;
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseInsensitive() {
    ParsingResult<?> result = new ParboiledShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result.matched).isTrue();
    List<Keyword> keywords = (List<Keyword>) result.resultValue;
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    ParsingResult<?> result = new ParboiledShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    assertThat(result.matched).isFalse();
  }

  @Test
  public void testNestedComment() {
    ParsingResult<?> result = new ParboiledShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    assertThat(result.matched).isTrue();

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    ParsingResult<?> deepResult = new ParboiledShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepResult.matched).isTrue();
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    assertThat(new ParboiledShowdown.NestedCommentFixture().run("/* comment /* nested */").matched).isFalse();

    // 2. Unbalanced: Missing outer opening delimiter
    assertThat(new ParboiledShowdown.NestedCommentFixture().run("comment /* nested */ */").matched).isFalse();

    // 3. Unbalanced: Extra trailing closing delimiter
    assertThat(new ParboiledShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */").matched).isFalse();

    // 4. Balanced but incorrect order
    assertThat(new ParboiledShowdown.NestedCommentFixture().run("*/ comment /*").matched).isFalse();
  }
}

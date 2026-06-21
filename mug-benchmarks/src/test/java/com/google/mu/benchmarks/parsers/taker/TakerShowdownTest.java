package com.google.mu.benchmarks.parsers.taker;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import io.github.parseworks.taker.Result;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TakerShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    Result<?> result = new TakerShowdown.IpFixture().run();
    assertThat(result.matches()).isTrue();
    assertThat(result.value()).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    Result<?> result = new TakerShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result.matches()).isTrue();
    assertThat(result.value()).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    Result<?> result = new TakerShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result.matches()).isTrue();
    assertThat(result.value()).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    Result<List<Keyword>> result = new TakerShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result.matches()).isTrue();
    assertThat(result.value()).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    Result<List<Keyword>> result = new TakerShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result.matches()).isTrue();
    assertThat(result.value()).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    Result<?> result = new TakerShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    assertThat(result.matches()).isFalse();
  }

  @Test
  public void testNestedComment() {
    Result<?> result = new TakerShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    assertThat(result.matches()).isTrue();

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    Result<?> deepResult = new TakerShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepResult.matches()).isTrue();
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    assertThat(new TakerShowdown.NestedCommentFixture().run("/* comment /* nested */").matches()).isFalse();

    // 2. Unbalanced: Missing outer opening delimiter
    assertThat(new TakerShowdown.NestedCommentFixture().run("comment /* nested */ */").matches()).isFalse();

    // 3. Unbalanced: Extra trailing closing delimiter
    assertThat(new TakerShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */").matches()).isFalse();

    // 4. Balanced but incorrect order
    assertThat(new TakerShowdown.NestedCommentFixture().run("*/ comment /*").matches()).isFalse();
  }
}

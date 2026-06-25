package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class DotParseShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    String result = new DotParseShowdown.IpFixture().run();
    assertThat(result).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    String result = new DotParseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    String result = new DotParseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    List<Keyword> result = new DotParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    List<Keyword> result = new DotParseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID));
  }

  @Test
  public void testNestedComment() {
    String result = new DotParseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    assertThat(result).isEqualTo(BenchmarkInputs.NESTED_COMMENT_EXPECTED_INNER);

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    String deepResult = new DotParseShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepResult).isEqualTo(deepNested.substring(2, deepNested.length() - 2));
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.NestedCommentFixture().run("/* comment /* nested */"));

    // 2. Unbalanced: Missing outer opening delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.NestedCommentFixture().run("comment /* nested */ */"));

    // 3. Unbalanced: Extra trailing closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */"));

    // 4. Balanced but incorrect order
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.NestedCommentFixture().run("*/ comment /*"));
  }
}

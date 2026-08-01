package com.google.mu.benchmarks.parsers.jparsec;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class JparsecShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    String result = new JparsecShowdown.IpFixture().run();
    assertThat(result).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    String result = new JparsecShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    String result = new JparsecShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    List<Keyword> result = new JparsecShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    List<Keyword> result = new JparsecShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID));
  }

  @Test
  public void testNestedComment() {
    new JparsecShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);

    // Verify deeper nesting (3 levels deep)
    new JparsecShowdown.NestedCommentFixture().run("/* level one /* level two /* level three */ nested two */ nested one */");
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.NestedCommentFixture().run("/* comment /* nested */"));

    // 2. Unbalanced: Missing outer opening delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.NestedCommentFixture().run("comment /* nested */ */"));

    // 3. Unbalanced: Extra trailing closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */"));

    // 4. Balanced but incorrect order
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.NestedCommentFixture().run("*/ comment /*"));
  }
}

package com.google.mu.benchmarks.parsers.dotparse;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class DotParseShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    String result = new DotParseShowdown.IpFixture().run();
    Assert.assertEquals("192.168.1.1", result);
  }

  @Test
  public void testQuotedStringSimple() {
    String result = new DotParseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result);
  }

  @Test
  public void testQuotedStringEscaped() {
    String result = new DotParseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    List<Keyword> result = new DotParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    List<Keyword> result = new DotParseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID));
  }

  @Test
  public void testNestedComment() {
    String result = new DotParseShowdown.NestedCommentFixture().run();
    Assert.assertEquals(BenchmarkInputs.NESTED_COMMENT_EXPECTED_INNER, result);
  }

  @Test
  public void testNestedCommentFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new DotParseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID));
  }
}

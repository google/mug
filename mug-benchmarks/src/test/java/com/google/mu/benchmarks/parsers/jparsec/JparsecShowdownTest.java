package com.google.mu.benchmarks.parsers.jparsec;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class JparsecShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    String result = new JparsecShowdown.IpFixture().run();
    Assert.assertEquals("192.168.1.1", result);
  }

  @Test
  public void testQuotedStringSimple() {
    String result = new JparsecShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result);
  }

  @Test
  public void testQuotedStringEscaped() {
    String result = new JparsecShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    List<Keyword> result = new JparsecShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    List<Keyword> result = new JparsecShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID));
  }

  @Test
  public void testNestedComment() {
    new JparsecShowdown.NestedCommentFixture().run();
  }

  @Test
  public void testNestedCommentFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new JparsecShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID));
  }
}

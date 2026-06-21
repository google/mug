package com.google.mu.benchmarks.parsers.taker;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import io.github.parseworks.taker.Result;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class TakerShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    Result<?> result = new TakerShowdown.IpFixture().run();
    Assert.assertTrue(result.matches());
    Assert.assertEquals("192.168.1.1", result.value());
  }

  @Test
  public void testQuotedStringSimple() {
    Result<?> result = new TakerShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertTrue(result.matches());
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result.value());
  }

  @Test
  public void testQuotedStringEscaped() {
    Result<?> result = new TakerShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertTrue(result.matches());
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result.value());
  }

  @Test
  public void testKeywordsCaseSensitive() {
    Result<List<Keyword>> result = new TakerShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertTrue(result.matches());
    List<Keyword> keywords = result.value();
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    Result<List<Keyword>> result = new TakerShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertTrue(result.matches());
    List<Keyword> keywords = result.value();
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsFailure() {
    Result<?> result = new TakerShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    Assert.assertFalse(result.matches());
  }

  @Test
  public void testNestedComment() {
    Result<?> result = new TakerShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    Assert.assertTrue(result.matches());
  }

  @Test
  public void testNestedCommentFailure() {
    Result<?> result = new TakerShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID);
    Assert.assertFalse(result.matches());
  }
}

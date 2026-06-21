package com.google.mu.benchmarks.parsers.jjparse;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class JjparseShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.IpFixture().run();
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals("192.168.1.1", result.getOrFail());
  }

  @Test
  public void testQuotedStringSimple() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result.getOrFail());
  }

  @Test
  public void testQuotedStringEscaped() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result.getOrFail());
  }

  @Test
  public void testKeywordsCaseSensitive() {
    jjparse.Parsing<Character>.Result<List<Keyword>> result = new JjparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertTrue(result.isSuccess());
    List<Keyword> keywords = result.getOrFail();
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    jjparse.Parsing<Character>.Result<List<Keyword>> result = new JjparseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertTrue(result.isSuccess());
    List<Keyword> keywords = result.getOrFail();
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsFailure() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    Assert.assertFalse(result.isSuccess());
  }

  @Test
  public void testNestedComment() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    Assert.assertTrue(result.isSuccess());
  }

  @Test
  public void testNestedCommentFailure() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID);
    Assert.assertFalse(result.isSuccess());
  }
}

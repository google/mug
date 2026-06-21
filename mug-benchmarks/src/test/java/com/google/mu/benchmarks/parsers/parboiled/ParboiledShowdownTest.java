package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.parboiled.support.ParsingResult;

public class ParboiledShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    ParsingResult<?> result = new ParboiledShowdown.IpFixture().run();
    Assert.assertTrue(result.matched);
    Assert.assertEquals("192.168.1.1", result.resultValue);
  }

  @Test
  public void testQuotedStringSimple() {
    ParsingResult<?> result = new ParboiledShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertTrue(result.matched);
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result.resultValue);
  }

  @Test
  public void testQuotedStringEscaped() {
    ParsingResult<?> result = new ParboiledShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertTrue(result.matched);
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result.resultValue);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseSensitive() {
    ParsingResult<?> result = new ParboiledShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertTrue(result.matched);
    List<Keyword> keywords = (List<Keyword>) result.resultValue;
    Assert.assertEquals(120, keywords.size());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseInsensitive() {
    ParsingResult<?> result = new ParboiledShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertTrue(result.matched);
    List<Keyword> keywords = (List<Keyword>) result.resultValue;
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsFailure() {
    ParsingResult<?> result = new ParboiledShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    Assert.assertFalse(result.matched);
  }

  @Test
  public void testNestedComment() {
    ParsingResult<?> result = new ParboiledShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    Assert.assertTrue(result.matched);
  }

  @Test
  public void testNestedCommentFailure() {
    ParsingResult<?> result = new ParboiledShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID);
    Assert.assertFalse(result.matched);
  }
}

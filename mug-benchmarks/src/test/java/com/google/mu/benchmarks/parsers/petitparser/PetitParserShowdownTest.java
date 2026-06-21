package com.google.mu.benchmarks.parsers.petitparser;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.petitparser.context.Result;

public class PetitParserShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    Result result = new PetitParserShowdown.IpFixture().run();
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals("192.168.1.1", result.get());
  }

  @Test
  public void testQuotedStringSimple() {
    Result result = new PetitParserShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result.get());
  }

  @Test
  public void testQuotedStringEscaped() {
    Result result = new PetitParserShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result.get());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseSensitive() {
    Result result = new PetitParserShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertTrue(result.isSuccess());
    List<Keyword> keywords = (List<Keyword>) result.get();
    Assert.assertEquals(120, keywords.size());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseInsensitive() {
    Result result = new PetitParserShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertTrue(result.isSuccess());
    List<Keyword> keywords = (List<Keyword>) result.get();
    Assert.assertEquals(120, keywords.size());
  }

  @Test
  public void testKeywordsFailure() {
    Result result = new PetitParserShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    Assert.assertTrue(result.isFailure());
  }

  @Test
  public void testNestedComment() {
    Result result = new PetitParserShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    Assert.assertTrue(result.isSuccess());
    Assert.assertEquals(BenchmarkInputs.NESTED_COMMENT, result.get());
  }

  @Test
  public void testNestedCommentFailure() {
    Result result = new PetitParserShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID);
    Assert.assertTrue(result.isFailure());
  }
}

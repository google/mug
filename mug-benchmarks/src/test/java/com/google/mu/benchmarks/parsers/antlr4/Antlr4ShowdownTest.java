package com.google.mu.benchmarks.parsers.antlr4;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class Antlr4ShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() {
    String result = new Antlr4Showdown.IpFixture().run();
    Assert.assertEquals("192.168.1.1", result);
  }

  @Test
  public void testQuotedStringSimple() {
    String result = new Antlr4Showdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, result);
  }

  @Test
  public void testQuotedStringEscaped() {
    String result = new Antlr4Showdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, result);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    List<Keyword> result = new Antlr4Showdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    List<Keyword> result = new Antlr4Showdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertEquals(120, result.size());
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new Antlr4Showdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID));
  }

  @Test
  public void testNestedComment() {
    String result = new Antlr4Showdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    if (result.endsWith("<EOF>")) {
      result = result.substring(0, result.length() - 5);
    }
    Assert.assertEquals("/* comment /* nested */*/", result);
  }

  @Test
  public void testNestedCommentFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> new Antlr4Showdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID));
  }
}

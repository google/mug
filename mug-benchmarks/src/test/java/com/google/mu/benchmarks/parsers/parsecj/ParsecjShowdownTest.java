package com.google.mu.benchmarks.parsers.parsecj;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.List;
import org.javafp.parsecj.Reply;
import org.junit.Assert;
import org.junit.Test;

public class ParsecjShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  @Test
  public void testIp() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.IpFixture().run();
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals("192.168.1.1", reply.getResult());
  }

  @Test
  public void testQuotedStringSimple() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals(EXPECTED_STRING_SIMPLE, reply.getResult());
  }

  @Test
  public void testQuotedStringEscaped() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals(EXPECTED_STRING_ESCAPED, reply.getResult());
  }

  @Test
  public void testKeywordsCaseSensitive() throws Exception {
    Reply<Character, List<Keyword>> reply = new ParsecjShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals(120, reply.getResult().size());
  }

  @Test
  public void testKeywordsCaseInsensitive() throws Exception {
    Reply<Character, List<Keyword>> reply = new ParsecjShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals(120, reply.getResult().size());
  }

  @Test
  public void testKeywordsFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, List<Keyword>> reply = new ParsecjShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });
  }

  @Test
  public void testNestedComment() throws Exception {
    Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    Assert.assertTrue(reply.isOk());
    Assert.assertEquals("*/", reply.getResult());
  }

  @Test
  public void testNestedCommentFailure() {
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID);
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });
  }
}

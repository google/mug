package com.google.mu.benchmarks.parsers.parsecj;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.javafp.parsecj.Reply;
import org.junit.Assert;
import org.junit.Test;

public class ParsecjShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.IpFixture().run();
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() throws Exception {
    Reply<Character, String> reply = new ParsecjShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @Test
  public void testKeywordsCaseSensitive() throws Exception {
    Reply<Character, List<Keyword>> reply = new ParsecjShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsCaseInsensitive() throws Exception {
    Reply<Character, List<Keyword>> reply = new ParsecjShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
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
    assertThat(reply.isOk()).isTrue();
    assertThat(reply.getResult()).isEqualTo("*/");

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    Reply<Character, ?> deepReply = new ParsecjShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepReply.isOk()).isTrue();
    assertThat(deepReply.getResult()).isEqualTo("*/");
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run("/* comment /* nested */");
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });

    // 2. Unbalanced: Missing outer opening delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run("comment /* nested */ */");
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });

    // 3. Unbalanced: Extra trailing closing delimiter
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */");
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });

    // 4. Balanced but incorrect order
    Assert.assertThrows(
        Throwable.class,
        () -> {
          Reply<Character, ?> reply = new ParsecjShowdown.NestedCommentFixture().run("*/ comment /*");
          if (!reply.isOk()) {
            throw new Exception("failed");
          }
        });
  }
}

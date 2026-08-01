package com.google.mu.benchmarks.parsers.jjparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class JjparseShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.IpFixture().run();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOrFail()).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOrFail()).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOrFail()).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @Test
  public void testKeywordsCaseSensitive() {
    jjparse.Parsing<Character>.Result<List<Keyword>> result = new JjparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result.isSuccess()).isTrue();
    List<Keyword> keywords = result.getOrFail();
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsCaseInsensitive() {
    jjparse.Parsing<Character>.Result<List<Keyword>> result = new JjparseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result.isSuccess()).isTrue();
    List<Keyword> keywords = result.getOrFail();
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  public void testNestedComment() {
    jjparse.Parsing<Character>.Result<?> result = new JjparseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    assertThat(result.isSuccess()).isTrue();

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    jjparse.Parsing<Character>.Result<?> deepResult = new JjparseShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepResult.isSuccess()).isTrue();
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    assertThat(new JjparseShowdown.NestedCommentFixture().run("/* comment /* nested */").isSuccess()).isFalse();

    // 2. Unbalanced: Missing outer opening delimiter
    assertThat(new JjparseShowdown.NestedCommentFixture().run("comment /* nested */ */").isSuccess()).isFalse();

    // 3. Unbalanced: Extra trailing closing delimiter
    assertThat(new JjparseShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */").isSuccess()).isFalse();

    // 4. Balanced but incorrect order
    assertThat(new JjparseShowdown.NestedCommentFixture().run("*/ comment /*").isSuccess()).isFalse();
  }
}

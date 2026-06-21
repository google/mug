package com.google.mu.benchmarks.parsers.petitparser;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.petitparser.context.Result;

public class PetitParserShowdownTest {
  private static final String EXPECTED_STRING_SIMPLE = "hello world!";
  private static final String EXPECTED_STRING_ESCAPED = "hello \"world\"!";

  private static final List<Keyword> EXPECTED_KEYWORDS =
      Arrays.stream(BenchmarkInputs.KEYWORDS_LIST_CS.split(","))
          .map(BenchmarkInputs.KEYWORD_MAP::get)
          .toList();

  @Test
  public void testIp() {
    Result result = new PetitParserShowdown.IpFixture().run();
    assertThat(result.isSuccess()).isTrue();
    assertThat((String) result.get()).isEqualTo("192.168.1.1");
  }

  @Test
  public void testQuotedStringSimple() {
    Result result = new PetitParserShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE);
    assertThat(result.isSuccess()).isTrue();
    assertThat((String) result.get()).isEqualTo(EXPECTED_STRING_SIMPLE);
  }

  @Test
  public void testQuotedStringEscaped() {
    Result result = new PetitParserShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED);
    assertThat(result.isSuccess()).isTrue();
    assertThat((String) result.get()).isEqualTo(EXPECTED_STRING_ESCAPED);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseSensitive() {
    Result result = new PetitParserShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS);
    assertThat(result.isSuccess()).isTrue();
    List<Keyword> keywords = (List<Keyword>) result.get();
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testKeywordsCaseInsensitive() {
    Result result = new PetitParserShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI);
    assertThat(result.isSuccess()).isTrue();
    List<Keyword> keywords = (List<Keyword>) result.get();
    assertThat(keywords).containsExactlyElementsIn(EXPECTED_KEYWORDS).inOrder();
  }

  @Test
  public void testKeywordsFailure() {
    Result result = new PetitParserShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID);
    assertThat(result.isFailure()).isTrue();
  }

  @Test
  public void testNestedComment() {
    Result result = new PetitParserShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT);
    assertThat(result.isSuccess()).isTrue();
    assertThat((String) result.get()).isEqualTo(BenchmarkInputs.NESTED_COMMENT);

    // Verify deeper nesting (3 levels deep)
    String deepNested = "/* level one /* level two /* level three */ nested two */ nested one */";
    Result deepResult = new PetitParserShowdown.NestedCommentFixture().run(deepNested);
    assertThat(deepResult.isSuccess()).isTrue();
    assertThat((String) deepResult.get()).isEqualTo(deepNested);
  }

  @Test
  public void testNestedCommentFailure() {
    // 1. Unbalanced: Missing outer closing delimiter
    assertThat(new PetitParserShowdown.NestedCommentFixture().run("/* comment /* nested */").isFailure()).isTrue();

    // 2. Unbalanced: Missing outer opening delimiter
    assertThat(new PetitParserShowdown.NestedCommentFixture().run("comment /* nested */ */").isFailure()).isTrue();

    // 3. Unbalanced: Extra trailing closing delimiter
    assertThat(new PetitParserShowdown.NestedCommentFixture().run("/* comment /* nested */ */ */").isFailure()).isTrue();

    // 4. Balanced but incorrect order
    assertThat(new PetitParserShowdown.NestedCommentFixture().run("*/ comment /*").isFailure()).isTrue();
  }
}

package com.google.common.labs.markdown;

import static com.google.common.truth.Truth8.assertThat;

import java.io.StringReader;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.testing.NullPointerTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class MarkdownLinkTest {

  @Test
  public void testParseLink(@TestParameter Scanner scanner) {
    assertThat(MarkdownLink.scan("[text](url)"))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testBackticksInsideCode(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("`` `foo` `` [text](url)"))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testBackticksInsideLinkText(@TestParameter Scanner scanner) {
   assertThat(scanner.scan("this is a link: [`text`](url)"))
        .containsExactly(new MarkdownLink("`text`", "url"));
  }

  @Test
  public void testLinkAfterEscapedBacktick(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("\\`[`text`](url)"))
        .containsExactly(new MarkdownLink("`text`", "url"));
  }

  @Test
  public void testIgnoreCodeBlock(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("```[text](url)```")).isEmpty();
  }

  @Test
  public void testIgnoreCodeSpan(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("`[text](url)`")).isEmpty();
  }

  @Test
  public void testEmptyLabel(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("this is an empty label link: [](url)"))
        .containsExactly(new MarkdownLink("", "url"));
  }

  @Test
  public void testBlankCodeLabel(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("this is an empty code label link: [` `](url)"))
        .containsExactly(new MarkdownLink("` `", "url"));
  }

  @Test
  public void testIgnoreDoubleBacktickCodeSpan(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("``[text](url)``")).isEmpty();
  }

  @Test
  public void testIgnoreArbitraryBacktickCodeSpan(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("````[text](url)````")).isEmpty();
  }

  @Test
  public void testQuotedMarkdownLink(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("\"[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscapeThenMarkdownLink(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("\\[text](url)\"")).isEmpty();
  }

  @Test
  public void testDoubleEscapeThenMarkdownLink(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("\\\\[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscaping(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("[x\\[y\\]](url)"))
        .containsExactly(new MarkdownLink("x[y]", "url"));
  }

  @Test
  public void testNestedSquareBrackets(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("[x\\[y[?]\\]](url)"))
        .containsExactly(new MarkdownLink("x[y[?]]", "url"));
  }

  @Test
  public void testNestedEmptySquareBrackets(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("[x\\[y[[]]\\]](url)"))
        .containsExactly(new MarkdownLink("x[y[[]]]", "url"));
  }

  @Test
  public void testUnclosedBackticks(@TestParameter Scanner scanner) {
    assertThat(scanner.scan("`unclosed [link](url)")).isEmpty();
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MarkdownLink.class);
    new NullPointerTester().testAllPublicConstructors(MarkdownLink.class);
  }

  private enum Scanner {
    FROM_STRING {
      @Override Stream<MarkdownLink> scan(String markdown) {
        return MarkdownLink.scan(markdown);
      }
    },
    FROM_READER {
      @Override Stream<MarkdownLink> scan(String markdown) {
        return MarkdownLink.scan(new StringReader(markdown));
      }
    };

    abstract Stream<MarkdownLink> scan(String markdown);
  }
}

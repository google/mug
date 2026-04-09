package com.google.common.labs.markdown;

import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class MarkdownLinkTest {

  @Test
  public void testParseLink() {
    assertThat(MarkdownLink.scan("[text](url)"))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testBackticksInsideCode() {
    assertThat(MarkdownLink.scan("`` `foo` `` [text](url)"))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testBackticksInsideLinkText() {
    assertThat(MarkdownLink.scan("this is a link: [`text`](url)"))
        .containsExactly(new MarkdownLink("`text`", "url"));
  }

  @Test
  public void testLinkAfterEscapedBacktick() {
    assertThat(MarkdownLink.scan("\\`[`text`](url)"))
        .containsExactly(new MarkdownLink("`text`", "url"));
  }

  @Test
  public void testIgnoreCodeBlock() {
    assertThat(MarkdownLink.scan("```[text](url)```")).isEmpty();
  }

  @Test
  public void testIgnoreCodeSpan() {
    assertThat(MarkdownLink.scan("`[text](url)`")).isEmpty();
  }

  @Test
  public void testIgnoreDoubleBacktickCodeSpan() {
    assertThat(MarkdownLink.scan("``[text](url)``")).isEmpty();
  }

  @Test
  public void testIgnoreArbitraryBacktickCodeSpan() {
    assertThat(MarkdownLink.scan("````[text](url)````")).isEmpty();
  }

  @Test
  public void testQuotedMarkdownLink() {
    assertThat(MarkdownLink.scan("\"[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscapeThenMarkdownLink() {
    assertThat(MarkdownLink.scan("\\[text](url)\"")).isEmpty();
  }

  @Test
  public void testDoubleEscapeThenMarkdownLink() {
    assertThat(MarkdownLink.scan("\\\\[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscaping() {
    assertThat(MarkdownLink.scan("[x\\[y\\]](url)"))
        .containsExactly(new MarkdownLink("x[y]", "url"));
  }

  @Test
  public void testUnclosedBackticks() {
    assertThat(MarkdownLink.scan("`unclosed [link](url)")).isEmpty();
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MarkdownLink.class);
  }
}

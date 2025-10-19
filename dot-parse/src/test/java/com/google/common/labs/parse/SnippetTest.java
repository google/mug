package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.labs.parse.Parser.Snippet;

@RunWith(JUnit4.class)
public class SnippetTest {

  @Test
  public void toString_atEnd_isEof() {
    assertThat(new Snippet("abc", 3).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_pastEnd_isEof() {
    assertThat(new Snippet("abc", 4).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_emptyString_isEof() {
    assertThat(new Snippet("", 0).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_shortNonWhitespace_followedByMore() {
    assertThat(new Snippet("foo bar", 0).toString()).isEqualTo("[foo...]");
  }

  @Test
  public void toString_shortNonWhitespace_atEnd() {
    assertThat(new Snippet("foo", 0).toString()).isEqualTo("[foo]");
  }

  @Test
  public void toString_shortNonWhitespace_inMiddle_atEnd() {
    assertThat(new Snippet("bar foo", 4).toString()).isEqualTo("[foo]");
    assertThat(new Snippet("bar foo", 3).toString()).isEqualTo("[ foo]");
  }

  @Test
  public void toString_longNonWhitespace_isTruncated() {
    String longString = "a".repeat(60);
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_longNonWhitespace_followedByMore_isTruncated() {
    String longString = "a".repeat(60) + " bar";
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_exactly50NonWhitespace_followedByMore_isTruncated() {
    String longString = "a".repeat(50) + " bar";
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_exactly50NonWhitespace_atEnd() {
    String longString = "a".repeat(50);
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "]");
  }

  @Test
  public void toString_shortWhitespace_followedByMore() {
    assertThat(new Snippet(" foo", 0).toString()).isEqualTo("[ foo]");
  }

  @Test
  public void toString_shortWhitespace_atEnd() {
    assertThat(new Snippet(" ", 0).toString()).isEqualTo("[ ]");
  }

  @Test
  public void toString_shortWhitespace_inMiddle_atEnd() {
    assertThat(new Snippet("foo ", 3).toString()).isEqualTo("[ ]");
  }

  @Test
  public void toString_longWhitespace_isTruncated() {
    String longString = " ".repeat(60);
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[   ...]");
  }

  @Test
  public void toString_longWhitespace_followedByMore_isTruncated() {
    String longString = " ".repeat(60) + "bar";
    assertThat(new Snippet(longString, 0).toString()).isEqualTo("[   ...]");
  }

  @Test
  public void toString_fullString() {
    assertThat(new Snippet("abc", 0).toString()).isEqualTo("[abc]");
  }

  @Test
  public void toString_substring() {
    assertThat(new Snippet("abc", 1).toString()).isEqualTo("[bc]");
  }

  @Test
  public void toString_withNewline() {
    assertThat(new Snippet("abc\nxyz", 3).toString()).isEqualTo("[\nxyz]");
  }

  @Test
  public void toString_pastNewline() {
    assertThat(new Snippet("abc\nxyz", 4).toString()).isEqualTo("[xyz]");
  }

  @Test
  public void toString_oneWhitespace() {
    assertThat(new Snippet(" ", 0).toString()).isEqualTo("[ ]");
  }

  @Test
  public void toString_twoWhitespaces() {
    assertThat(new Snippet("  ", 0).toString()).isEqualTo("[  ]");
  }

  @Test
  public void toString_threeWhitespaces() {
    assertThat(new Snippet("   ", 0).toString()).isEqualTo("[   ]");
  }

  @Test
  public void toString_fourWhitespaces_truncated() {
    assertThat(new Snippet("    ", 0).toString()).isEqualTo("[   ...]");
  }

  @Test
  public void toString_specialWhitespace() {
    assertThat(new Snippet("\t\r\n", 0).toString()).isEqualTo("[\t\r\n]");
  }

  @Test
  public void toString_specialWhitespace_truncated() {
    assertThat(new Snippet("\t\r\n ", 0).toString()).isEqualTo("[\t\r\n...]");
  }
}

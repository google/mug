package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.labs.parse.Parser.Synopsis;

@RunWith(JUnit4.class)
public class SynopsisTest {

  @Test
  public void toString_atEnd_isEof() {
    assertThat(new Synopsis("abc", 3).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_pastEnd_isEof() {
    assertThat(new Synopsis("abc", 4).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_emptyString_isEof() {
    assertThat(new Synopsis("", 0).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_shortNonWhitespace_followedByMore() {
    assertThat(new Synopsis("foo bar", 0).toString()).isEqualTo("[foo...]");
  }

  @Test
  public void toString_shortNonWhitespace_atEnd() {
    assertThat(new Synopsis("foo", 0).toString()).isEqualTo("[foo]");
  }

  @Test
  public void toString_shortNonWhitespace_inMiddle_atEnd() {
    assertThat(new Synopsis("bar foo", 4).toString()).isEqualTo("[foo]");
  }

  @Test
  public void toString_longNonWhitespace_isTruncated() {
    String longString = "a".repeat(60);
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_longNonWhitespace_followedByMore_isTruncated() {
    String longString = "a".repeat(60) + " bar";
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_exactly50NonWhitespace_followedByMore_isTruncated() {
    String longString = "a".repeat(50) + " bar";
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "...]");
  }

  @Test
  public void toString_exactly50NonWhitespace_atEnd() {
    String longString = "a".repeat(50);
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[" + "a".repeat(50) + "]");
  }

  @Test
  public void toString_shortWhitespace_followedByMore() {
    assertThat(new Synopsis(" foo", 0).toString()).isEqualTo("[ foo]");
  }

  @Test
  public void toString_shortWhitespace_atEnd() {
    assertThat(new Synopsis(" ", 0).toString()).isEqualTo("[ ]");
  }

  @Test
  public void toString_shortWhitespace_inMiddle_atEnd() {
    assertThat(new Synopsis("foo ", 3).toString()).isEqualTo("[ ]");
  }

  @Test
  public void toString_longWhitespace_isTruncated() {
    String longString = " ".repeat(60);
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[ ...]");
  }

  @Test
  public void toString_longWhitespace_followedByMore_isTruncated() {
    String longString = " ".repeat(60) + "bar";
    assertThat(new Synopsis(longString, 0).toString()).isEqualTo("[" + " ".repeat(50) + "...]");
  }
}

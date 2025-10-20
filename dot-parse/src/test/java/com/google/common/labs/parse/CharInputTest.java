package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharInputTest {

  @Test
  public void fromString_isEof() {
    assertThat(CharInput.from("").isEof(0)).isTrue();
    assertThat(CharInput.from("a").isEof(0)).isFalse();
    assertThat(CharInput.from("a").isEof(1)).isTrue();
  }

  @Test
  public void fromString_isInRange() {
    assertThat(CharInput.from("").isInRange(0)).isFalse();
    assertThat(CharInput.from("a").isInRange(0)).isTrue();
    assertThat(CharInput.from("a").isInRange(1)).isFalse();
  }

  @Test
  public void fromString_isEmpty() {
    assertThat(CharInput.from("").isEmpty()).isTrue();
    assertThat(CharInput.from("a").isEmpty()).isFalse();
  }

  @Test
  public void fromString_snippet() {
    assertThat(CharInput.from("abc").snippet(0, 2)).isEqualTo("ab");
    assertThat(CharInput.from("abc").snippet(1, 5)).isEqualTo("bc");
    assertThat(CharInput.from("abc").snippet(3, 2)).isEqualTo("");
    assertThat(CharInput.from("").snippet(0, 1)).isEqualTo("");
  }

  @Test
  public void fromReader_startsWith_isPrefix() {
    CharInput input = CharInput.from(new StringReader("food"));
    assertThat(input.startsWith("foo", 0)).isTrue();
  }

  @Test
  public void fromReader_startsWith_isNotPrefix() {
    CharInput input = CharInput.from(new StringReader("food"));
    assertThat(input.startsWith("fobar", 0)).isFalse();
  }

  @Test
  public void fromReader_isEof() {
    CharInput empty = CharInput.from(new StringReader(""));
    assertThat(empty.isEof(0)).isTrue();
    CharInput input = CharInput.from(new StringReader("a"));
    assertThat(input.isEof(0)).isFalse();
    assertThat(input.isEof(1)).isTrue();
  }

  @Test
  public void fromReader_isInRange() {
    CharInput empty = CharInput.from(new StringReader(""));
    assertThat(empty.isInRange(0)).isFalse();
    CharInput input = CharInput.from(new StringReader("a"));
    assertThat(input.isInRange(0)).isTrue();
    assertThat(input.isInRange(1)).isFalse();
  }

  @Test
  public void fromReader_isEmpty() {
    assertThat(CharInput.from(new StringReader("")).isEmpty()).isTrue();
    assertThat(CharInput.from(new StringReader("a")).isEmpty()).isFalse();
  }

  @Test
  public void fromReader_snippet() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(input.snippet(0, 2)).isEqualTo("ab");
    assertThat(input.snippet(1, 5)).isEqualTo("bc");
    assertThat(input.snippet(3, 2)).isEqualTo("");
    assertThat(CharInput.from(new StringReader("")).snippet(0, 1)).isEqualTo("");
  }

  @Test
  public void fromReader_startsWith_prefixLongerThanBuffer_isPrefix() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader(prefix + "b"));
    assertThat(input.startsWith(prefix, 0)).isTrue();
  }

  @Test
  public void fromReader_startsWith_prefixLongerThanBuffer_isNotPrefix() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader("a".repeat(8999) + "cb"));
    assertThat(input.startsWith(prefix, 0)).isFalse();
  }

  @Test
  public void fromReader_startsWith_prefixLongerThanBuffer_isPrefix_loadedTwice() throws Exception {
    String prefix = "a".repeat(9000);
    MockReader reader = new MockReader(prefix + "a");
    CharInput input = CharInput.from(reader);
    assertThat(input.startsWith(prefix, 0)).isTrue();
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.startsWith(prefix, 1)).isTrue();
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.startsWith("a", 9000)).isTrue();
    assertThat(reader.loadCount).isEqualTo(2);
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_charAt_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.charAt(5));
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_isEof_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.isEof(5));
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_startsWith_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.startsWith("5", 5));
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_snippet_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.snippet(5, 1));
  }

  @Test
  public void fromReader_markCheckpoint_accessAtCheckpoint_charAt() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.charAt(6)).isEqualTo('6');
  }

  @Test
  public void fromReader_markCheckpoint_accessPastCheckpoint_charAt() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.charAt(9)).isEqualTo('9');
  }

  @Test
  public void fromReader_markCheckpoint_accessAtCheckpoint_isEof() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.isEof(10)).isTrue();
  }

  @Test
  public void fromReader_markCheckpoint_accessPastCheckpoint_isEof() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.isEof(10)).isTrue();
  }

  @Test
  public void fromReader_markCheckpoint_accessAtCheckpoint_startsWith() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.startsWith("67", 6)).isTrue();
  }

  @Test
  public void fromReader_markCheckpoint_accessPastCheckpoint_startsWith() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.startsWith("89", 8)).isTrue();
  }

  @Test
  public void fromReader_markCheckpoint_accessAtCheckpoint_snippet() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.snippet(7, 2)).isEqualTo("78");
  }

  @Test
  public void fromReader_markCheckpoint_accessPastCheckpoint_snippet() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.snippet(9, 1)).isEqualTo("9");
  }

  private static class MockReader extends StringReader {
    private int loadCount = 0;

    MockReader(String str) {
      super(str);
    }

    @Override
    public int read(char[] cbuf) throws IOException {
      loadCount++;
      return super.read(cbuf);
    }
  }
}

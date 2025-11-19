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
  public void fromString_snippet() {
    assertThat(CharInput.from("abc").snippet(0, 2)).isEqualTo("ab");
    assertThat(CharInput.from("abc").snippet(1, 5)).isEqualTo("bc");
    assertThat(CharInput.from("abc").snippet(3, 2)).isEmpty();
    assertThat(CharInput.from("").snippet(0, 1)).isEmpty();
  }

  @Test
  public void fromString_indexOf_found() {
    assertThat(CharInput.from("hello world").indexOf("world", 0)).isEqualTo(6);
    assertThat(CharInput.from("hello world").indexOf("world", 6)).isEqualTo(6);
  }

  @Test
  public void fromString_indexOf_notFound() {
    assertThat(CharInput.from("hello world").indexOf("moon", 0)).isEqualTo(-1);
  }

  @Test
  public void fromString_indexOf_notFound_pastTarget() {
    assertThat(CharInput.from("hello world").indexOf("hello", 1)).isEqualTo(-1);
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
  public void fromReader_indexOf_prefixLongerThanBuffer_found() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader(prefix + "b"));
    assertThat(input.indexOf(prefix, 0)).isEqualTo(0);
  }

  @Test
  public void fromReader_indexOf_prefixLongerThanBuffer_notFound() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader("a".repeat(8999) + "cb"));
    assertThat(input.indexOf(prefix, 0)).isEqualTo(-1);
  }

  @Test
  public void fromReader_indexOf_prefixLongerThanBuffer_loadedTwice() throws Exception {
    String prefix = "a".repeat(9000);
    MockReader reader = new MockReader("b" + prefix + "a");
    CharInput input = CharInput.from(reader);
    assertThat(input.indexOf(prefix, 1)).isEqualTo(1);
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.indexOf("a", 9001)).isEqualTo(9001);
    assertThat(reader.loadCount).isEqualTo(2);
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
  public void fromReader_snippet() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(input.snippet(0, 2)).isEqualTo("ab");
    assertThat(input.snippet(1, 5)).isEqualTo("bc");
    assertThat(input.snippet(3, 2)).isEmpty();
    assertThat(CharInput.from(new StringReader("")).snippet(0, 1)).isEmpty();
  }

  @Test
  public void fromReader_indexOf_found() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("world", 0)).isEqualTo(6);
    assertThat(input.indexOf("world", 6)).isEqualTo(6);
  }

  @Test
  public void fromReader_indexOf_notFound() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("moon", 0)).isEqualTo(-1);
  }

  @Test
  public void fromReader_indexOf_notFound_pastTarget() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("hello", 1)).isEqualTo(-1);
  }

  @Test
  public void fromReader_indexOf_loadsMoreChars() {
    CharInput input = CharInput.from(new StringReader("0123456789abcfoo"), 10, 5);
    assertThat(input.indexOf("foo", 9)).isEqualTo(13);
  }

  @Test
  public void fromReader_indexOf_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("0123456789abcdef"), 10, 5);
    assertThat(input.charAt(9)).isEqualTo('9'); // load first 10
    input.markCheckpoint(6);
    assertThat(input.indexOf("f", 6)).isEqualTo(15);
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_charAt_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.charAt(5));
  }

  @Test
  public void fromReader_markCheckpoint_accessBeforeCheckpoint_indexOf_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.indexOf("5", 5));
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

  @Test
  public void fromString_sourcePosition_emptyString() {
    assertThat(CharInput.from("").sourcePosition(0)).isEqualTo("1:1");
  }

  @Test
  public void fromString_sourcePosition_singleLine() {
    assertThat(CharInput.from("abc").sourcePosition(0)).isEqualTo("1:1");
    assertThat(CharInput.from("abc").sourcePosition(1)).isEqualTo("1:2");
    assertThat(CharInput.from("abc").sourcePosition(3)).isEqualTo("1:4");
  }

  @Test
  public void fromString_sourcePosition_singleLineEndingWithNewline() {
    assertThat(CharInput.from("abc\n").sourcePosition(3)).isEqualTo("1:4");
    assertThat(CharInput.from("abc\n").sourcePosition(4)).isEqualTo("2:1");
  }

  @Test
  public void fromString_sourcePosition_twoLines() {
    assertThat(CharInput.from("abc\ndef").sourcePosition(3)).isEqualTo("1:4");
    assertThat(CharInput.from("abc\ndef").sourcePosition(4)).isEqualTo("2:1");
    assertThat(CharInput.from("abc\ndef").sourcePosition(5)).isEqualTo("2:2");
  }

  @Test
  public void fromString_sourcePosition_twoLinesEndingWithNewline() {
    assertThat(CharInput.from("abc\ndef\n").sourcePosition(7)).isEqualTo("2:4");
    assertThat(CharInput.from("abc\ndef\n").sourcePosition(8)).isEqualTo("3:1");
  }

  @Test
  public void fromString_sourcePosition_threeLines() {
    assertThat(CharInput.from("abc\ndef\nghi").sourcePosition(5)).isEqualTo("2:2");
    assertThat(CharInput.from("abc\ndef\nghi").sourcePosition(8)).isEqualTo("3:1");
  }

  @Test
  public void fromReader_sourcePosition_emptyString() {
    CharInput input = CharInput.from(new StringReader(""));
    boolean unused = input.isEof(0);
    assertThat(input.sourcePosition(0)).isEqualTo("1:1");
  }

  @Test
  public void fromReader_sourcePosition_singleLine() {
    CharInput input = CharInput.from(new StringReader("abc"));
    boolean unused = input.isEof(3);
    assertThat(input.sourcePosition(0)).isEqualTo("1:1");
    assertThat(input.sourcePosition(1)).isEqualTo("1:2");
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
  }

  @Test
  public void fromReader_sourcePosition_singleLineEndingWithNewline() {
    CharInput input = CharInput.from(new StringReader("abc\n"));
    boolean unused = input.isEof(4);
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
    assertThat(input.sourcePosition(4)).isEqualTo("2:1");
  }

  @Test
  public void fromReader_sourcePosition_twoLines() {
    CharInput input = CharInput.from(new StringReader("abc\ndef"));
    boolean unused = input.isEof(5);
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
    assertThat(input.sourcePosition(4)).isEqualTo("2:1");
    assertThat(input.sourcePosition(5)).isEqualTo("2:2");
  }

  @Test
  public void fromReader_sourcePosition_twoLinesEndingWithNewline() {
    CharInput input = CharInput.from(new StringReader("abc\ndef\n"));
    boolean unused = input.isEof(8);
    assertThat(input.sourcePosition(7)).isEqualTo("2:4");
    assertThat(input.sourcePosition(8)).isEqualTo("3:1");
  }

  @Test
  public void fromReader_sourcePosition_threeLines() {
    CharInput input = CharInput.from(new StringReader("abc\ndef\nghi"));
    boolean unused = input.isEof(8);
    assertThat(input.sourcePosition(5)).isEqualTo("2:2");
    assertThat(input.sourcePosition(8)).isEqualTo("3:1");
  }

  @Test
  public void fromReader_sourcePosition_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("012\n456\n89abcdefg"), 10, 5);
    assertThat(input.charAt(10)).isEqualTo('a');

    // checkpoint is 6. indices 0-5 are before checkpoint.
    input.markCheckpoint(6);

    // After compaction, read more. The builder is of size 15 - 6 + 1 = 10.
    assertThat(input.charAt(15)).isEqualTo('f');
    assertThat(input.sourcePosition(9)).isEqualTo("9");
    assertThat(input.sourcePosition(15)).isEqualTo("15");
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

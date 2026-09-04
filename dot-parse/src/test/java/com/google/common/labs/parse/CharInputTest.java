package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.range;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.regex.RegexPattern;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharInputTest {

  @Test public void fromString_isEof() {
    assertThat(CharInput.from("").isEof(0)).isTrue();
    assertThat(CharInput.from("a").isEof(0)).isFalse();
    assertThat(CharInput.from("a").isEof(1)).isTrue();
  }

  @Test public void fromString_isInRange() {
    assertThat(CharInput.from("").isInRange(0)).isFalse();
    assertThat(CharInput.from("a").isInRange(0)).isTrue();
    assertThat(CharInput.from("a").isInRange(1)).isFalse();
  }

  @Test public void fromString_snippet() {
    assertThat(CharInput.from("abc").snippet(0, 2)).isEqualTo("ab");
    assertThat(CharInput.from("abc").snippet(1, 5)).isEqualTo("bc");
    assertThat(CharInput.from("abc").snippet(3, 2)).isEmpty();
    assertThat(CharInput.from("").snippet(0, 1)).isEmpty();
  }

  @Test public void fromString_indexOf_found() {
    assertThat(CharInput.from("hello world").indexOf("world", 0)).isEqualTo(6);
    assertThat(CharInput.from("hello world").indexOf("world", 6)).isEqualTo(6);
  }

  @Test public void fromString_indexOf_notFound() {
    assertThat(CharInput.from("hello world").indexOf("moon", 0)).isEqualTo(-1);
  }

  @Test public void fromString_indexOf_notFound_pastTarget() {
    assertThat(CharInput.from("hello world").indexOf("hello", 1)).isEqualTo(-1);
  }

  @Test public void fromString_startsWithCaseInsensitive_isPrefix() {
    CharInput input = CharInput.from("AbCde");
    assertThat(input.startsWithCaseInsensitive("aBcD", 0)).isTrue();
  }

  @Test public void fromString_startsWithCaseInsensitive_isNotPrefix() {
    CharInput input = CharInput.from("AbCde");
    assertThat(input.startsWithCaseInsensitive("aBcDg", 0)).isFalse();
  }

  @Test public void fromReader_startsWith_isPrefix() {
    CharInput input = CharInput.from(new StringReader("food"));
    assertThat(input.startsWith("foo", 0)).isTrue();
  }

  @Test public void fromReader_startsWithCaseInsensitive_isPrefix() {
    CharInput input = CharInput.from(new StringReader("AbCde"));
    assertThat(input.startsWithCaseInsensitive("aBcD", 0)).isTrue();
  }

  @Test public void fromReader_startsWith_isNotPrefix() {
    CharInput input = CharInput.from(new StringReader("food"));
    assertThat(input.startsWith("fobar", 0)).isFalse();
  }

  @Test public void fromReader_startsWithCaseInsensitive_isNotPrefix() {
    CharInput input = CharInput.from(new StringReader("AbCde"));
    assertThat(input.startsWithCaseInsensitive("aBcDf", 0)).isFalse();
  }

  @Test public void fromReader_startsWith_prefixLongerThanBuffer_isPrefix() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader(prefix + "b"));
    assertThat(input.startsWith(prefix, 0)).isTrue();
  }

  @Test public void fromReader_startsWithCaseInsensitive_prefixLongerThanBuffer_isPrefix() {
    String prefix = "a".repeat(4500) + "B".repeat(4500);
    CharInput input = CharInput.from(new StringReader("A".repeat(4500) + "b".repeat(4500) + "c"));
    assertThat(input.startsWithCaseInsensitive(prefix, 0)).isTrue();
  }

  @Test public void fromReader_startsWith_prefixLongerThanBuffer_isNotPrefix() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader("a".repeat(8999) + "cb"));
    assertThat(input.startsWith(prefix, 0)).isFalse();
  }

  @Test public void fromReader_startsWithCaseInsensitive_prefixLongerThanBuffer_isNotPrefix() {
    String prefix = "a".repeat(4500) + "B".repeat(4500);
    CharInput input = CharInput.from(new StringReader("A".repeat(4500) + "b".repeat(4499) + "cD"));
    assertThat(input.startsWithCaseInsensitive(prefix, 0)).isFalse();
  }

  @Test public void fromReader_startsWith_prefixLongerThanBuffer_isPrefix_loadedTwice()
      throws Exception {
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

  @Test public void
      fromReader_startsWithCaseInsensitive_prefixLongerThanBuffer_isPrefix_loadedTwice()
          throws Exception {
    String prefix = "a".repeat(4500) + "B".repeat(4500);
    MockReader reader = new MockReader("A".repeat(4500) + "b".repeat(4500) + "A");
    CharInput input = CharInput.from(reader);
    assertThat(input.startsWithCaseInsensitive(prefix, 0)).isTrue();
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.startsWithCaseInsensitive(prefix, 1)).isFalse();
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.startsWithCaseInsensitive("a", 9000)).isTrue();
    assertThat(reader.loadCount).isEqualTo(2);
  }

  @Test public void fromReader_indexOf_prefixLongerThanBuffer_found() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader(prefix + "b"));
    assertThat(input.indexOf(prefix, 0)).isEqualTo(0);
  }

  @Test public void fromReader_indexOf_prefixLongerThanBuffer_notFound() {
    String prefix = "a".repeat(9000);
    CharInput input = CharInput.from(new StringReader("a".repeat(8999) + "cb"));
    assertThat(input.indexOf(prefix, 0)).isEqualTo(-1);
  }

  @Test public void fromReader_indexOf_prefixLongerThanBuffer_loadedTwice() throws Exception {
    String prefix = "a".repeat(9000);
    MockReader reader = new MockReader("b" + prefix + "a");
    CharInput input = CharInput.from(reader);
    assertThat(input.indexOf(prefix, 1)).isEqualTo(1);
    assertThat(reader.loadCount).isEqualTo(2);
    assertThat(input.indexOf("a", 9001)).isEqualTo(9001);
    assertThat(reader.loadCount).isEqualTo(2);
  }

  @Test public void fromReader_isEof() {
    CharInput empty = CharInput.from(new StringReader(""));
    assertThat(empty.isEof(0)).isTrue();
    CharInput input = CharInput.from(new StringReader("a"));
    assertThat(input.isEof(0)).isFalse();
    assertThat(input.isEof(1)).isTrue();
  }

  @Test public void fromReader_isInRange() {
    CharInput empty = CharInput.from(new StringReader(""));
    assertThat(empty.isInRange(0)).isFalse();
    CharInput input = CharInput.from(new StringReader("a"));
    assertThat(input.isInRange(0)).isTrue();
    assertThat(input.isInRange(1)).isFalse();
  }

  @Test public void fromReader_snippet() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(input.snippet(0, 2)).isEqualTo("ab");
    assertThat(input.snippet(1, 5)).isEqualTo("bc");
    assertThat(input.snippet(3, 2)).isEmpty();
    assertThat(CharInput.from(new StringReader("")).snippet(0, 1)).isEmpty();
  }

  @Test public void fromReader_indexOf_found() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("world", 0)).isEqualTo(6);
    assertThat(input.indexOf("world", 6)).isEqualTo(6);
  }

  @Test public void fromReader_indexOf_notFound() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("moon", 0)).isEqualTo(-1);
  }

  @Test public void fromReader_indexOf_notFound_pastTarget() {
    CharInput input = CharInput.from(new StringReader("hello world"));
    assertThat(input.indexOf("hello", 1)).isEqualTo(-1);
  }

  @Test public void fromReader_indexOf_loadsMoreChars() {
    CharInput input = CharInput.from(new StringReader("0123456789abcfoo"), 10, 5);
    assertThat(input.indexOf("foo", 9)).isEqualTo(13);
  }

  @Test public void fromReader_indexOf_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("0123456789abcdef"), 10, 5);
    assertThat(input.charAt(9)).isEqualTo('9'); // load first 10
    input.markCheckpoint(6);
    assertThat(input.indexOf("f", 6)).isEqualTo(15);
  }

  @Test public void fromReader_markCheckpoint_accessBeforeCheckpoint_charAt_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IndexOutOfBoundsException.class, () -> input.charAt(5));
  }

  @Test public void fromReader_markCheckpoint_accessBeforeCheckpoint_indexOf_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IllegalArgumentException.class, () -> input.indexOf("5", 5));
  }

  @Test public void fromReader_markCheckpoint_accessBeforeCheckpoint_isEof_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IndexOutOfBoundsException.class, () -> input.isEof(5));
  }

  @Test public void fromReader_markCheckpoint_accessBeforeCheckpoint_startsWith_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IndexOutOfBoundsException.class, () -> input.startsWith("5", 5));
  }

  @Test public void fromReader_markCheckpoint_accessBeforeCheckpoint_snippet_throws() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThrows(IndexOutOfBoundsException.class, () -> input.snippet(5, 1));
  }

  @Test public void fromReader_markCheckpoint_accessAtCheckpoint_charAt() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.charAt(6)).isEqualTo('6');
  }

  @Test public void fromReader_markCheckpoint_accessPastCheckpoint_charAt() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.charAt(9)).isEqualTo('9');
  }

  @Test public void fromReader_markCheckpoint_accessAtCheckpoint_isEof() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.isEof(10)).isTrue();
  }

  @Test public void fromReader_markCheckpoint_accessPastCheckpoint_isEof() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.isEof(10)).isTrue();
  }

  @Test public void fromReader_markCheckpoint_accessAtCheckpoint_startsWith() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.startsWith("67", 6)).isTrue();
  }

  @Test public void fromReader_markCheckpoint_accessPastCheckpoint_startsWith() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.startsWith("89", 8)).isTrue();
  }

  @Test public void fromReader_markCheckpoint_accessAtCheckpoint_snippet() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.snippet(7, 2)).isEqualTo("78");
  }

  @Test public void fromReader_markCheckpoint_accessPastCheckpoint_snippet() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    char unused = input.charAt(9); // load all
    input.markCheckpoint(6);
    assertThat(input.snippet(9, 1)).isEqualTo("9");
  }

  @Test public void fromString_sourcePosition_emptyString() {
    assertThat(CharInput.from("").sourcePosition(0)).isEqualTo("1:1");
  }

  @Test public void fromString_sourcePosition_singleLine() {
    assertThat(CharInput.from("abc").sourcePosition(0)).isEqualTo("1:1");
    assertThat(CharInput.from("abc").sourcePosition(1)).isEqualTo("1:2");
    assertThat(CharInput.from("abc").sourcePosition(3)).isEqualTo("1:4");
  }

  @Test public void fromString_sourcePosition_singleLineEndingWithNewline() {
    assertThat(CharInput.from("abc\n").sourcePosition(3)).isEqualTo("1:4");
    assertThat(CharInput.from("abc\n").sourcePosition(4)).isEqualTo("2:1");
  }

  @Test public void fromString_sourcePosition_twoLines() {
    assertThat(CharInput.from("abc\ndef").sourcePosition(3)).isEqualTo("1:4");
    assertThat(CharInput.from("abc\ndef").sourcePosition(4)).isEqualTo("2:1");
    assertThat(CharInput.from("abc\ndef").sourcePosition(5)).isEqualTo("2:2");
  }

  @Test public void fromString_sourcePosition_twoLinesEndingWithNewline() {
    assertThat(CharInput.from("abc\ndef\n").sourcePosition(7)).isEqualTo("2:4");
    assertThat(CharInput.from("abc\ndef\n").sourcePosition(8)).isEqualTo("3:1");
  }

  @Test public void fromString_sourcePosition_threeLines() {
    assertThat(CharInput.from("abc\ndef\nghi").sourcePosition(5)).isEqualTo("2:2");
    assertThat(CharInput.from("abc\ndef\nghi").sourcePosition(8)).isEqualTo("3:1");
  }

  @Test public void fromReader_sourcePosition_emptyString() {
    CharInput input = CharInput.from(new StringReader(""));
    boolean unused = input.isEof(0);
    assertThat(input.sourcePosition(0)).isEqualTo("1:1");
  }

  @Test public void fromReader_sourcePosition_singleLine() {
    CharInput input = CharInput.from(new StringReader("abc"));
    boolean unused = input.isEof(3);
    assertThat(input.sourcePosition(0)).isEqualTo("1:1");
    assertThat(input.sourcePosition(1)).isEqualTo("1:2");
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
  }

  @Test public void fromReader_sourcePosition_singleLineEndingWithNewline() {
    CharInput input = CharInput.from(new StringReader("abc\n"));
    boolean unused = input.isEof(4);
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
    assertThat(input.sourcePosition(4)).isEqualTo("2:1");
  }

  @Test public void fromReader_sourcePosition_twoLines() {
    CharInput input = CharInput.from(new StringReader("abc\ndef"));
    boolean unused = input.isEof(5);
    assertThat(input.sourcePosition(3)).isEqualTo("1:4");
    assertThat(input.sourcePosition(4)).isEqualTo("2:1");
    assertThat(input.sourcePosition(5)).isEqualTo("2:2");
  }

  @Test public void fromReader_sourcePosition_twoLinesEndingWithNewline() {
    CharInput input = CharInput.from(new StringReader("abc\ndef\n"));
    boolean unused = input.isEof(8);
    assertThat(input.sourcePosition(7)).isEqualTo("2:4");
    assertThat(input.sourcePosition(8)).isEqualTo("3:1");
  }

  @Test public void fromReader_sourcePosition_threeLines() {
    CharInput input = CharInput.from(new StringReader("abc\ndef\nghi"));
    boolean unused = input.isEof(8);
    assertThat(input.sourcePosition(5)).isEqualTo("2:2");
    assertThat(input.sourcePosition(8)).isEqualTo("3:1");
  }

  @Test public void fromReader_sourcePosition_afterCompaction() {
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

    @Override public int read(char[] cbuf) throws IOException {
      loadCount++;
      return super.read(cbuf);
    }
  }

  @Test public void fromReader_matchRegex_loadsLazilyBasedOnStartAndMaxSize() {
    CharInput input = CharInput.from(new OneCharReader("abcdefg"));
    int matchLength = input.match(Pattern.compile("cde"), RegexPattern.of("cde").metadata(), 2);
    assertThat(matchLength).isEqualTo(5);
  }

  @Test public void fromReader_matchRegex_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("0123456789abcdef"), 10, 5);
    // Load some characters to allow compaction
    assertThat(input.charAt(9)).isEqualTo('9');

    // Mark checkpoint at 6. garbageCharCount becomes 6.
    input.markCheckpoint(6);

    // Match "789" at index 7.
    // If the logical conversion is correct, it will return logical end index 10.
    int matchLength = input.match(Pattern.compile("789"), RegexPattern.of("789").metadata(), 7);
    assertThat(matchLength).isEqualTo(10);
  }

  @Test public void fromReader_matchRegex_maxSizeOverflowsSaturatedAdd() {
    CharInput input = CharInput.from(new OneCharReader("0123456789a"));
    // Read the first 10 characters to advance the stream
    for (int i = 0; i < 10; i++) {
      assertThat(input.charAt(i)).isEqualTo((char) ('0' + i));
    }
    // Now match "a{1,2147483640}" at index 10.
    // Since start + maxSize overflows Integer.MAX_VALUE, it must throw
    // UnsupportedOperationException.
    var patternMetadata = RegexPattern.of("a{1,2147483640}");
    assertThat(patternMetadata.metadata().maxSize()).isEqualTo(2147483640);
    assertThrows(
        UnsupportedOperationException.class,
        () -> input.match(Pattern.compile("a{1,2147483640}"), patternMetadata.metadata(), 10));
  }

  private static class OneCharReader extends Reader {
    private final String content;
    private int index = 0;

    OneCharReader(String content) {
      this.content = content;
    }

    @Override public int read(char[] cbuf, int off, int len) {
      if (index >= content.length()) {
        return -1;
      }
      cbuf[off] = content.charAt(index++);
      return 1;
    }

    @Override public void close() {}
  }

  @Test public void fromString_skipWhile_emptyInput() {
    CharInput input = CharInput.from("");
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhile_noMatch() {
    CharInput input = CharInput.from("bc");
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhile_allMatch() {
    CharInput input = CharInput.from("aaaa");
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(4);
  }

  @Test public void fromString_skipWhile_partialMatch() {
    CharInput input = CharInput.from("aaab");
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(3);
  }

  @Test public void fromString_skipWhile_fromOffset() {
    CharInput input = CharInput.from("baaaac");
    assertThat(input.skipWhile(is('a'), 1)).isEqualTo(5);
  }

  @Test public void fromString_skipWhile_longRun() {
    String text = "a".repeat(100) + "b";
    CharInput input = CharInput.from(text);
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(100);
  }

  @Test public void fromString_skipWhile_rangePredicate() {
    CharInput input = CharInput.from("1234567890abc");
    assertThat(input.skipWhile(range('0', '9'), 0)).isEqualTo(10);
  }

  @Test public void fromReader_skipWhile_emptyInput() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhile_noMatch() {
    CharInput input = CharInput.from(new StringReader("bc"));
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhile_allMatch() {
    CharInput input = CharInput.from(new StringReader("aaaa"));
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(4);
  }

  @Test public void fromReader_skipWhile_partialMatch() {
    CharInput input = CharInput.from(new StringReader("aaab"));
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(3);
  }

  @Test public void fromReader_skipWhile_acrossBufferBoundary() {
    String text = "a".repeat(9000) + "b";
    CharInput input = CharInput.from(new StringReader(text));
    assertThat(input.skipWhile(is('a'), 0)).isEqualTo(9000);
  }

  @Test public void fromReader_skipWhile_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("xxxxaaaaaay"), 10, 4);
    input.charAt(4); // Advance read
    input.markCheckpoint(4); // Compact "xxxx"
    assertThat(input.skipWhile(is('a'), 4)).isEqualTo(10);
  }

  @Test public void fromString_skipWhileLow64_emptyInput() {
    CharInput input = CharInput.from("");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhileLow64_noMatch() {
    CharInput input = CharInput.from("abc");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhileLow64_allMatch() {
    CharInput input = CharInput.from("00000000");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(8);
  }

  @Test public void fromString_skipWhileLow64_partialMatch() {
    CharInput input = CharInput.from("0000abc");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromString_skipWhileLow64_transitionToHighAscii() {
    CharInput input = CharInput.from("0000xyz");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromString_skipWhileLow64_transitionToNonAscii() {
    CharInput input = CharInput.from("0000\u00E9");
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromReader_skipWhileLow64_emptyInput() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhileLow64_noMatch() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhileLow64_allMatch() {
    CharInput input = CharInput.from(new StringReader("00000000"));
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(8);
  }

  @Test public void fromReader_skipWhileLow64_partialMatch() {
    CharInput input = CharInput.from(new StringReader("0000abc"));
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromReader_skipWhileLow64_acrossBufferBoundary() {
    String text = "0".repeat(9000) + "a";
    CharInput input = CharInput.from(new StringReader(text));
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 0)).isEqualTo(9000);
  }

  @Test public void fromReader_skipWhileLow64_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("xxxx000000y"), 10, 4);
    input.charAt(4); // Advance read
    input.markCheckpoint(4); // Compact "xxxx"
    assertThat(input.skipWhile(range('0', '0').precomputeForAscii(), 4)).isEqualTo(10);
  }

  @Test public void fromString_skipWhileHigh64_emptyInput() {
    CharInput input = CharInput.from("");
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhileHigh64_noMatch() {
    CharInput input = CharInput.from("0123");
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromString_skipWhileHigh64_allMatch() {
    CharInput input = CharInput.from("aaaaaaaa");
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(8);
  }

  @Test public void fromString_skipWhileHigh64_partialMatch() {
    CharInput input = CharInput.from("aaaa123");
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromString_skipWhileHigh64_transitionToNonAscii() {
    CharInput input = CharInput.from("aaaa\u00E9");
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromReader_skipWhileHigh64_emptyInput() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhileHigh64_noMatch() {
    CharInput input = CharInput.from(new StringReader("0123"));
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(0);
  }

  @Test public void fromReader_skipWhileHigh64_allMatch() {
    CharInput input = CharInput.from(new StringReader("aaaaaaaa"));
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(8);
  }

  @Test public void fromReader_skipWhileHigh64_partialMatch() {
    CharInput input = CharInput.from(new StringReader("aaaa123"));
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(4);
  }

  @Test public void fromReader_skipWhileHigh64_acrossBufferBoundary() {
    String text = "a".repeat(9000) + "0";
    CharInput input = CharInput.from(new StringReader(text));
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 0)).isEqualTo(9000);
  }

  @Test public void fromReader_skipWhileHigh64_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("xxxxaaaaaay"), 10, 4);
    input.charAt(4); // Advance read
    input.markCheckpoint(4); // Compact "xxxx"
    assertThat(input.skipWhile(range('a', 'a').precomputeForAscii(), 4)).isEqualTo(10);
  }
}

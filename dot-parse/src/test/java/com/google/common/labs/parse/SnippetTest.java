package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SnippetTest {

  @Test
  public void toString_atEnd_showsContextBefore() {
    assertThat(new Snippet(CharInput.from("abc"), 3).toString()).isEqualTo("""

            abc
               ^
        """);
  }


  @Test
  public void toString_emptyString_isEof() {
    assertThat(new Snippet(CharInput.from(""), 0).toString()).isEqualTo("""

            <EOF>
            ^
        """);
  }

  @Test
  public void toString_shortNonWhitespace_followedByMore() {
    assertThat(new Snippet(CharInput.from("foo bar"), 0).toString()).isEqualTo("""

            foo bar
            ^
        """);
  }

  @Test
  public void toString_shortNonWhitespace_atEnd() {
    assertThat(new Snippet(CharInput.from("foo"), 0).toString()).isEqualTo("""

            foo
            ^
        """);
  }

  @Test
  public void toString_shortNonWhitespace_inMiddle() {
    assertThat(new Snippet(CharInput.from("bar foo"), 4).toString()).isEqualTo("""

            bar foo
                ^
        """);
    assertThat(new Snippet(CharInput.from("bar foo"), 3).toString()).isEqualTo("""

            bar foo
               ^
        """);
  }

  @Test
  public void toString_longNonWhitespace_beforeCapped() {
    String input = "a".repeat(35) + "bar";
    assertThat(new Snippet(CharInput.from(input), 35).toString()).isEqualTo("""

            aaaaaaaaaaaaaaaaaaaaaaaaabar
                                     ^
        """);
  }

  @Test
  public void toString_longNonWhitespace_afterCapped() {
    String input = "foo" + "a".repeat(60);
    assertThat(new Snippet(CharInput.from(input), 3).toString()).isEqualTo("""

            fooaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
               ^
        """);
  }

  @Test
  public void toString_whitespaceSkipping_before() {
    String input = "a b c d e f g h";
    assertThat(new Snippet(CharInput.from(input), 12).toString()).isEqualTo("""

            d e f g h
                  ^
        """);
  }

  @Test
  public void toString_whitespaceSkipping_after() {
    String input = "f g h i j k l ";
    assertThat(new Snippet(CharInput.from(input), 4).toString()).isEqualTo("""

            f g h i j
                ^
        """);
  }

  @Test
  public void toString_nextWordTooLong_fallbackToCap_before() {
    String input = " " + "a".repeat(30) + " " + "foo";
    assertThat(new Snippet(CharInput.from(input), 35).toString()).isEqualTo("""

            aaaaaaaaaaaaaaaaaaaaa foo
                                     ^
        """);
  }

  @Test
  public void toString_nextWordTooLong_fallbackToCap_after() {
    String input = "foo " + "a".repeat(60) + " ";
    assertThat(new Snippet(CharInput.from(input), 0).toString()).isEqualTo("""

            foo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            ^
        """);
  }

  @Test
  public void toStringWithIndent() {
    assertThat(new Snippet(8, CharInput.from("abc"), 1).toString()).isEqualTo("""

                abc
                 ^
        """);
  }

  @Test
  public void toString_withCompactedReaderInput() {
    String text = "012345678901234567890123456789"; // length 30
    CharInput input = CharInput.from(new java.io.StringReader(text), 30, 5);
    // Force read up to index 20 so buffer has it.
    input.charAt(20);
    // Mark checkpoint at index 20 to trigger compaction.
    input.markCheckpoint(20);

    // at = 22. Attempting to scan 25 chars back would read index -3, which is < 20 (compacted).
    // It should fallback to original toString() behavior.
    assertThat(new Snippet(input, 22).toString()).isEqualTo("[23456789]");
  }

  @Test
  public void toString_withCompactedReaderInput_atEof() {
    String text = "012345678901234567890123456789"; // length 30
    CharInput input = CharInput.from(new java.io.StringReader(text), 30, 5);
    input.charAt(29);
    input.markCheckpoint(20);

    // at = 30 (EOF).
    assertThat(new Snippet(input, 30).toString()).isEqualTo("<EOF>");
  }

  @Test
  public void toString_withCompactedReaderInput_truncated() {
    String text = "01234567890123456789012345678901234567890123456789012345678901234567890123456789"; // length 80
    CharInput input = CharInput.from(new java.io.StringReader(text), 80, 5);
    input.charAt(79);
    input.markCheckpoint(20);

    // at = 22. snippet scans forward up to 50 characters, which is truncated.
    assertThat(new Snippet(input, 22).toString()).isEqualTo("[23456789012345678901234567890123456789012345678901...]");
  }
}

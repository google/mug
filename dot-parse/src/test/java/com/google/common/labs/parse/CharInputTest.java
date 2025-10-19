package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.StringReader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharInputTest {

  @Test
  public void fromReader_charAt_nonSequentialIndex_throws() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThrows(IllegalStateException.class, () -> input.charAt(1));
  }

  @Test
  public void fromReader_isEof_nonSequentialIndex_throws() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThrows(IllegalStateException.class, () -> input.isEof(1));
  }

  @Test
  public void fromReader_startsWith_nonSequentialIndex_throws() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThrows(IllegalStateException.class, () -> input.startsWith("a", 1));
  }

  @Test
  public void fromReader_snippet_nonSequentialIndex_throws() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThrows(IllegalStateException.class, () -> input.snippet(1, 1));
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
}

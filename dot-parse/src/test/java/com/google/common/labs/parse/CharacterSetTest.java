package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class CharacterSetTest {

  @Test
  public void test_positiveCharSet_parseSuccess() {
    CharacterSet set = charsIn("[a-fA-F-_]");
    assertThat(set.matchesAllOf("abcfED-_")).isTrue();
    assertThat(set.matchesNoneOf("gzZ")).isTrue();
  }

  @Test
  public void test_negativeCharSet_parseSuccess() {
    CharacterSet set = charsIn("[^\"{}]");
    assertThat(set.matchesAllOf("zzZ")).isTrue();
    assertThat(set.matchesNoneOf("\"{}")).isTrue();
  }

  @Test
  public void test_emptyCharSet() {
    CharacterSet set = charsIn("[]");
    assertThat(set.matchesNoneOf("ab123")).isTrue();
  }

  @Test
  public void test_emptyNegativeCharSet_parseSucceeds() {
    CharacterSet set = charsIn("[^]");
    assertThat(set.matchesAllOf("ab123")).isTrue();
  }

  @Test
  public void test_backslashNotAllowed_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[\\]"));
    assertThat(thrown).hasMessageThat().contains("Escaping ([\\]) not supported");
  }

  @Test
  public void test_closingBracketNotAllowed_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[]]"));
    assertThat(thrown).hasMessageThat().contains("encountered []]");
  }

  @Test
  public void test_missingBrackets_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("a-z"));
    assertThat(thrown).hasMessageThat().contains("Use [a-z] instead.");
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(charsIn("[]"), charsIn("[]"))
        .addEqualityGroup(charsIn("[^]"), charsIn("[^]"))
        .addEqualityGroup(charsIn("[a-zA-Z0-9]"), charsIn("[a-zA-Z0-9]"))
        .addEqualityGroup(charsIn("[^a-zA-Z0-9]"), charsIn("[^a-zA-Z0-9]"))
        .testEquals();
  }
}

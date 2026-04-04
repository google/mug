package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
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
    assertThat(set.candidateCharsIfAscii().get())
        .containsExactly('a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F', '-', '_');
  }

  @Test
  public void test_negativeCharSet_parseSuccess() {
    CharacterSet set = charsIn("[^\"{}]");
    assertThat(set.matchesAllOf("zzZ")).isTrue();
    assertThat(set.matchesNoneOf("\"{}")).isTrue();
    assertThat(set.candidateCharsIfAscii()).isEmpty();
  }

  @Test
  public void test_emptyCharSet() {
    CharacterSet set = charsIn("[]");
    assertThat(set.matchesNoneOf("ab123")).isTrue();
    assertThat(set.candidateCharsIfAscii().get()).isEmpty();
  }

  @Test
  public void test_emptyNegativeCharSet_parseSucceeds() {
    CharacterSet set = charsIn("[^]");
    assertThat(set.matchesAllOf("ab123")).isTrue();
    assertThat(set.candidateCharsIfAscii()).isEmpty();
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_backslashNotAllowed_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[\\]"));
    assertThat(thrown).hasMessageThat().contains("Escaping ([\\]) not supported");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_closingBracketNotAllowed_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[]]"));
    assertThat(thrown).hasMessageThat().contains("encountered []]");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_missingBrackets_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("a-z"));
    assertThat(thrown).hasMessageThat().contains("Use [a-z] instead.");
  }

  @Test
  public void not_positiveSet() {
    CharacterSet positive = charsIn("[ab]");
    assertThat(positive.not().contains('a')).isFalse();
    assertThat(positive.not().contains('b')).isFalse();
    assertThat(positive.not().contains('c')).isTrue();
    assertThat(positive.not().toString()).isEqualTo("[^ab]");
    assertThat(positive.candidateCharsIfAscii().get()).containsExactly('a', 'b');
    assertThat(positive.not().candidateCharsIfAscii()).isEmpty();
  }

  @Test
  public void not_negativeSet() {
    CharacterSet negative = charsIn("[^ab]");
    assertThat(negative.not().contains('a')).isTrue();
    assertThat(negative.not().contains('b')).isTrue();
    assertThat(negative.not().contains('c')).isFalse();
    assertThat(negative.not().toString()).isEqualTo("[ab]");
    assertThat(negative.candidateCharsIfAscii()).isEmpty();
    assertThat(negative.not().candidateCharsIfAscii().get()).containsExactly('a', 'b');
  }

  @Test
  public void not_rangeSet() {
    CharacterSet range = charsIn("[a-c]");
    assertThat(range.not().contains('a')).isFalse();
    assertThat(range.not().contains('b')).isFalse();
    assertThat(range.not().contains('c')).isFalse();
    assertThat(range.not().contains('d')).isTrue();
    assertThat(range.not().toString()).isEqualTo("[^a-c]");
    assertThat(range.candidateCharsIfAscii().get()).containsExactly('a', 'b', 'c');
    assertThat(range.not().candidateCharsIfAscii()).isEmpty();
  }

  @Test
  public void not_negatedRangeSet() {
    CharacterSet negatedRange = charsIn("[^a-c]");
    assertThat(negatedRange.not().contains('a')).isTrue();
    assertThat(negatedRange.not().contains('b')).isTrue();
    assertThat(negatedRange.not().contains('c')).isTrue();
    assertThat(negatedRange.not().contains('d')).isFalse();
    assertThat(negatedRange.not().toString()).isEqualTo("[a-c]");
    assertThat(negatedRange.candidateCharsIfAscii()).isEmpty();
    assertThat(negatedRange.not().candidateCharsIfAscii().get()).containsExactly('a', 'b', 'c');
  }

  @Test
  public void not_emptySet() {
    CharacterSet empty = charsIn("[]");
    assertThat(empty.not().contains('a')).isTrue();
    assertThat(empty.not().toString()).isEqualTo("[^]");
    assertThat(empty.candidateCharsIfAscii().get()).isEmpty();
    assertThat(empty.not().candidateCharsIfAscii()).isEmpty();
  }

  @Test
  public void not_fullSet() {
    CharacterSet full = charsIn("[^]");
    assertThat(full.not().contains('a')).isFalse();
    assertThat(full.not().toString()).isEqualTo("[]");
    assertThat(full.candidateCharsIfAscii()).isEmpty();
    assertThat(full.not().candidateCharsIfAscii().get()).isEmpty();
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

  @Test
  public void candidateCharsIfAscii_nonAscii() {
    CharacterSet set = charsIn("[á]");
    assertThat(set.candidateCharsIfAscii()).isEmpty();
  }

  @Test
  public void candidateCharsIfAscii_nonAsciiRange() {
    CharacterSet set = charsIn("[a-á]");
    assertThat(set.candidateCharsIfAscii()).isEmpty();
  }
}

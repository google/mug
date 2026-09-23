package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterRangeSet.charsIn;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.testing.EqualsTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CharacterRangeSetTest {

  @Test public void test_positiveCharSet_parseSuccess() {
    CharacterRangeSet set = charsIn("[a-fA-F-_]");
    assertThat(set.matchesAllOf("abcfED-_")).isTrue();
    assertThat(set.matchesNoneOf("gzZ")).isTrue();
    assertThat(set.getAsciiPrefixes())
        .containsExactly("a", "b", "c", "d", "e", "f", "A", "B", "C", "D", "E", "F", "-", "_");
  }

  @Test public void test_negativeCharSet_parseSuccess() {
    CharacterRangeSet set = charsIn("[^\"{}]");
    assertThat(set.matchesAllOf("zzZ")).isTrue();
    assertThat(set.matchesNoneOf("\"{}")).isTrue();
    assertThat(set.getAsciiPrefixes()).containsExactly("");
  }

  @Test public void test_emptyCharSet() {
    CharacterRangeSet set = charsIn("[]");
    assertThat(set.matchesNoneOf("ab123")).isTrue();
    assertThat(set.getAsciiPrefixes()).isEmpty();
  }

  @Test public void test_emptyNegativeCharSet_parseSucceeds() {
    CharacterRangeSet set = charsIn("[^]");
    assertThat(set.matchesAllOf("ab123")).isTrue();
    assertThat(set.getAsciiPrefixes()).containsExactly("");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_backslashAllowed() {
    CharacterRangeSet set = charsIn("[\\]");
    assertThat(set.contains('\\')).isTrue();
    assertThat(set.contains('a')).isFalse();
    assertThat(set.toString()).isEqualTo("[\\\\]");
    assertThat(set.getAsciiPrefixes()).containsExactly("\\");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_negativeCharSetWithBackslash() {
    CharacterRangeSet set = charsIn("[^\\]");
    assertThat(set.contains('\\')).isFalse();
    assertThat(set.contains('a')).isTrue();
    assertThat(set.toString()).isEqualTo("[^\\\\]");
    assertThat(set.getAsciiPrefixes()).containsExactly("");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_rangeWithBackslash() {
    CharacterRangeSet set = charsIn("[\\]-a]");
    assertThat(set.contains('\\')).isTrue();
    assertThat(set.contains(']')).isTrue();
    assertThat(set.contains('^')).isTrue();
    assertThat(set.contains('_')).isTrue();
    assertThat(set.contains('`')).isTrue();
    assertThat(set.contains('a')).isTrue();
    assertThat(set.contains('b')).isFalse();
    assertThat(set.toString()).isEqualTo("[\\\\]-a]");
    assertThat(set.getAsciiPrefixes()).containsExactly("\\", "]", "^", "_", "`", "a");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_invalidRangeWithBackslash_throws() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[a-\\]"));
    assertThat(e).hasMessageThat().contains("[a-\\]");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_rightBracketAsFirstChar_parseSuccess() {
    CharacterRangeSet set1 = charsIn("[]]");
    assertThat(set1.contains(']')).isTrue();
    assertThat(set1.contains('a')).isFalse();
    assertThat(set1.toString()).isEqualTo("[]]");
    assertThat(set1.getAsciiPrefixes()).containsExactly("]");

    CharacterRangeSet set2 = charsIn("[^]]");
    assertThat(set2.contains(']')).isFalse();
    assertThat(set2.contains('a')).isTrue();
    assertThat(set2.toString()).isEqualTo("[^]]");
    assertThat(set2.getAsciiPrefixes()).containsExactly("");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_missingBrackets_throws() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> charsIn("a-z"));
    assertThat(thrown).hasMessageThat().contains("Use [a-z] instead.");
  }

  @Test public void not_positiveSet() {
    CharacterRangeSet positive = charsIn("[ab]");
    assertThat(positive.not().test('a')).isFalse();
    assertThat(positive.not().test('b')).isFalse();
    assertThat(positive.not().test('c')).isTrue();
    assertThat(positive.getAsciiPrefixes()).containsExactly("a", "b");
  }

  @Test public void not_negativeSet() {
    CharacterRangeSet negative = charsIn("[^ab]");
    assertThat(negative.not().test('a')).isTrue();
    assertThat(negative.not().test('b')).isTrue();
    assertThat(negative.not().test('c')).isFalse();
    assertThat(negative.getAsciiPrefixes()).containsExactly("");
  }

  @Test public void not_rangeSet() {
    CharacterRangeSet range = charsIn("[a-c]");
    assertThat(range.not().test('a')).isFalse();
    assertThat(range.not().test('b')).isFalse();
    assertThat(range.not().test('c')).isFalse();
    assertThat(range.not().test('d')).isTrue();
    assertThat(range.getAsciiPrefixes()).containsExactly("a", "b", "c");
  }

  @Test public void not_negatedRangeSet() {
    CharacterRangeSet negatedRange = charsIn("[^a-c]");
    assertThat(negatedRange.not().test('a')).isTrue();
    assertThat(negatedRange.not().test('b')).isTrue();
    assertThat(negatedRange.not().test('c')).isTrue();
    assertThat(negatedRange.not().test('d')).isFalse();
    assertThat(negatedRange.getAsciiPrefixes()).containsExactly("");
  }

  @Test public void not_emptySet() {
    CharacterRangeSet empty = charsIn("[]");
    assertThat(empty.not().test('a')).isTrue();
    assertThat(empty.getAsciiPrefixes()).isEmpty();
  }

  @Test public void not_fullSet() {
    CharacterRangeSet full = charsIn("[^]");
    assertThat(full.not().test('a')).isFalse();
    assertThat(full.getAsciiPrefixes()).containsExactly("");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(charsIn("[]"), charsIn("[]"))
        .addEqualityGroup(charsIn("[^]"), charsIn("[^]"))
        .addEqualityGroup(charsIn("[a-zA-Z0-9]"), charsIn("[a-zA-Z0-9]"))
        .addEqualityGroup(charsIn("[^a-zA-Z0-9]"), charsIn("[^a-zA-Z0-9]"))
        .addEqualityGroup(charsIn("[\\]"), charsIn("[\\]"))
        .testEquals();
  }

  @Test public void getAsciiPrefixes_nonAscii() {
    CharacterRangeSet set = charsIn("[á]");
    assertThat(set.getAsciiPrefixes()).containsExactly("");
  }

  @Test public void getAsciiPrefixes_nonAsciiRange() {
    CharacterRangeSet set = charsIn("[a-á]");
    assertThat(set.getAsciiPrefixes()).containsExactly("");
  }

  @Test @SuppressWarnings("CharacterSetLiteralCheck")
  public void invalidRange_throws() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[1-0]"));
    assertThat(e).hasMessageThat().contains("[1-0]");
  }

  @Test public void toString_escapesInvisibleCharacters() {
    CharacterRangeSet set = charsIn("[\r\n\t\f\b]");
    assertThat(set.toString()).isEqualTo("[\\r\\n\\t\\f\\b]");
  }

  @Test public void charsIn_surrogateCharacterThrows() {
    assertThrows(IllegalArgumentException.class, () -> charsIn("[😀]"));
  }

  @Test public void charsIn_surrogateRangeThrows() {
    assertThrows(IllegalArgumentException.class, () -> charsIn("[😀-😁]"));
  }

  @Test public void toString_escapesUnicodeControlCharacters() {
    CharacterRangeSet set = charsIn("[\u0000\u001F\u007F]");
    assertThat(set.toString()).isEqualTo("[\\u0000\\u001F\\u007F]");
  }

  @Test public void toString_mixedControlAndRegularCharacters() {
    CharacterRangeSet set = charsIn("[a-z\r\n\t\f\b0-9\u0000\u001F\u007F]");
    assertThat(set.toString()).isEqualTo("[a-z\\r\\n\\t\\f\\b0-9\\u0000\\u001F\\u007F]");
  }

  @Test public void toString_regularCharactersOnly() {
    CharacterRangeSet set = charsIn("[a-zA-Z0-9-_]");
    assertThat(set.toString()).isEqualTo("[a-zA-Z0-9-_]");
  }

  @Test public void skipLeading_allMatch() {
    CharacterRangeSet set = charsIn("[0-9]");
    assertThat(set.skipLeading("0123456789abc", 0)).isEqualTo(10);
  }

  @Test public void skipLeading_withOffset() {
    CharacterRangeSet set = charsIn("[a-z]");
    assertThat(set.skipLeading("123abcdef456", 3)).isEqualTo(9);
  }

  @Test public void skipLeading_negativeSet() {
    CharacterRangeSet set = charsIn("[^0-9]");
    assertThat(set.skipLeading("abcdef123", 0)).isEqualTo(6);
  }

  @Test public void precomputeForAscii_returnsSelf() {
    CharacterRangeSet set = charsIn("[a-z]");
    assertThat(set.precomputeForAscii()).isSameInstanceAs(set);
  }
}

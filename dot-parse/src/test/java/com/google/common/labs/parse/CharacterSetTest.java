package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.CharacterSet.prefixesIfAscii;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class CharacterSetTest {

  @Test
  public void test_positiveCharSet_parseSuccess() {
    CharacterSet set = charsIn("[a-fA-F-_]");
    assertThat(set.matchesAllOf("abcfED-_")).isTrue();
    assertThat(set.matchesNoneOf("gzZ")).isTrue();
    assertThat(prefixesIfAscii(set))
        .containsExactly("a", "b", "c", "d", "e", "f", "A", "B", "C", "D", "E", "F", "-", "_");
  }

  @Test
  public void test_negativeCharSet_parseSuccess() {
    CharacterSet set = charsIn("[^\"{}]");
    assertThat(set.matchesAllOf("zzZ")).isTrue();
    assertThat(set.matchesNoneOf("\"{}")).isTrue();
    assertThat(prefixesIfAscii(set)).containsExactly("");
  }

  @Test
  public void test_emptyCharSet() {
    CharacterSet set = charsIn("[]");
    assertThat(set.matchesNoneOf("ab123")).isTrue();
    assertThat(prefixesIfAscii(set)).isEmpty();
  }

  @Test
  public void test_emptyNegativeCharSet_parseSucceeds() {
    CharacterSet set = charsIn("[^]");
    assertThat(set.matchesAllOf("ab123")).isTrue();
    assertThat(prefixesIfAscii(set)).containsExactly("");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_backslashAllowed() {
    CharacterSet set = charsIn("[\\]");
    assertThat(set.contains('\\')).isTrue();
    assertThat(set.contains('a')).isFalse();
    assertThat(set.toString()).isEqualTo("[\\\\]");
    assertThat(prefixesIfAscii(set)).containsExactly("\\");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_negativeCharSetWithBackslash() {
    CharacterSet set = charsIn("[^\\]");
    assertThat(set.contains('\\')).isFalse();
    assertThat(set.contains('a')).isTrue();
    assertThat(set.toString()).isEqualTo("[^\\\\]");
    assertThat(prefixesIfAscii(set)).containsExactly("");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_rangeWithBackslash() {
    CharacterSet set = charsIn("[\\]-a]");
    assertThat(set.contains('\\')).isTrue();
    assertThat(set.contains(']')).isTrue();
    assertThat(set.contains('^')).isTrue();
    assertThat(set.contains('_')).isTrue();
    assertThat(set.contains('`')).isTrue();
    assertThat(set.contains('a')).isTrue();
    assertThat(set.contains('b')).isFalse();
    assertThat(set.toString()).isEqualTo("[\\\\]-a]");
    assertThat(prefixesIfAscii(set)).containsExactly("\\", "]", "^", "_", "`", "a");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_invalidRangeWithBackslash_throws() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[a-\\]"));
    assertThat(e).hasMessageThat().contains("[a-\\]");
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void test_rightBracketAsFirstChar_parseSuccess() {
    CharacterSet set1 = charsIn("[]]");
    assertThat(set1.contains(']')).isTrue();
    assertThat(set1.contains('a')).isFalse();
    assertThat(set1.toString()).isEqualTo("[]]");
    assertThat(prefixesIfAscii(set1)).containsExactly("]");

    CharacterSet set2 = charsIn("[^]]");
    assertThat(set2.contains(']')).isFalse();
    assertThat(set2.contains('a')).isTrue();
    assertThat(set2.toString()).isEqualTo("[^]]");
    assertThat(prefixesIfAscii(set2)).containsExactly("");
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
    assertThat(prefixesIfAscii(positive)).containsExactly("a", "b");
    assertThat(prefixesIfAscii(positive.not())).containsExactly("");
  }

  @Test
  public void not_negativeSet() {
    CharacterSet negative = charsIn("[^ab]");
    assertThat(negative.not().contains('a')).isTrue();
    assertThat(negative.not().contains('b')).isTrue();
    assertThat(negative.not().contains('c')).isFalse();
    assertThat(negative.not().toString()).isEqualTo("[ab]");
    assertThat(prefixesIfAscii(negative)).containsExactly("");
    assertThat(prefixesIfAscii(negative.not())).containsExactly("a", "b");
  }

  @Test
  public void not_rangeSet() {
    CharacterSet range = charsIn("[a-c]");
    assertThat(range.not().contains('a')).isFalse();
    assertThat(range.not().contains('b')).isFalse();
    assertThat(range.not().contains('c')).isFalse();
    assertThat(range.not().contains('d')).isTrue();
    assertThat(range.not().toString()).isEqualTo("[^a-c]");
    assertThat(prefixesIfAscii(range)).containsExactly("a", "b", "c");
    assertThat(prefixesIfAscii(range.not())).containsExactly("");
  }

  @Test
  public void not_negatedRangeSet() {
    CharacterSet negatedRange = charsIn("[^a-c]");
    assertThat(negatedRange.not().contains('a')).isTrue();
    assertThat(negatedRange.not().contains('b')).isTrue();
    assertThat(negatedRange.not().contains('c')).isTrue();
    assertThat(negatedRange.not().contains('d')).isFalse();
    assertThat(negatedRange.not().toString()).isEqualTo("[a-c]");
    assertThat(prefixesIfAscii(negatedRange)).containsExactly("");
    assertThat(prefixesIfAscii(negatedRange.not())).containsExactly("a", "b", "c");
  }

  @Test
  public void not_emptySet() {
    CharacterSet empty = charsIn("[]");
    assertThat(empty.not().contains('a')).isTrue();
    assertThat(empty.not().toString()).isEqualTo("[^]");
    assertThat(prefixesIfAscii(empty)).isEmpty();
    assertThat(prefixesIfAscii(empty.not())).containsExactly("");
  }

  @Test
  public void not_fullSet() {
    CharacterSet full = charsIn("[^]");
    assertThat(full.not().contains('a')).isFalse();
    assertThat(full.not().toString()).isEqualTo("[]");
    assertThat(prefixesIfAscii(full)).containsExactly("");
    assertThat(prefixesIfAscii(full.not())).isEmpty();
  }

  @Test
  @SuppressWarnings("CharacterSetLiteralCheck")
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(charsIn("[]"), charsIn("[]"))
        .addEqualityGroup(charsIn("[^]"), charsIn("[^]"))
        .addEqualityGroup(charsIn("[a-zA-Z0-9]"), charsIn("[a-zA-Z0-9]"))
        .addEqualityGroup(charsIn("[^a-zA-Z0-9]"), charsIn("[^a-zA-Z0-9]"))
        .addEqualityGroup(charsIn("[\\]"), charsIn("[\\]"))
        .testEquals();
  }

  @Test
  public void candidateCharsIfAscii_nonAscii() {
    CharacterSet set = charsIn("[á]");
    assertThat(prefixesIfAscii(set)).containsExactly("");
  }

  @Test
  public void candidateCharsIfAscii_nonAsciiRange() {
    CharacterSet set = charsIn("[a-á]");
    assertThat(prefixesIfAscii(set)).containsExactly("");
  }

  @Test
  public void invalidRange_throws() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> charsIn("[1-0]"));
    assertThat(e).hasMessageThat().contains("[1-0]");
  }

  @Test
  public void toString_escapesInvisibleCharacters() {
    CharacterSet set = charsIn("[\r\n\t\f\b]");
    assertThat(set.toString()).isEqualTo("[\\r\\n\\t\\f\\b]");
  }

  @Test
  public void toString_escapesUnicodeControlCharacters() {
    CharacterSet set = charsIn("[\u0000\u001F\u007F]");
    assertThat(set.toString()).isEqualTo("[\\u0000\\u001F\\u007F]");
  }

  @Test
  public void toString_mixedControlAndRegularCharacters() {
    CharacterSet set = charsIn("[a-z\r\n\t\f\b0-9\u0000\u001F\u007F]");
    assertThat(set.toString()).isEqualTo("[a-z\\r\\n\\t\\f\\b0-9\\u0000\\u001F\\u007F]");
  }

  @Test
  public void toString_regularCharactersOnly() {
    CharacterSet set = charsIn("[a-zA-Z0-9-_]");
    assertThat(set.toString()).isEqualTo("[a-zA-Z0-9-_]");
  }

  @Test
  public void prefixesIfAscii_customPredicate_invalidRange_throws() {
    CharPredicate custom = new CharPredicate() {
      @Override public boolean test(char c) {
        return false;
      }
      @Override public String characterRangeSet() {
        return "[z-a]";
      }
    };
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> prefixesIfAscii(custom));
    assertThat(e).hasMessageThat().contains("invalid range [z-a]");
  }

  @Test
  public void prefixesIfAscii_unicodeRange_returnsEmptySet() {
    assertThat(prefixesIfAscii(CharPredicate.ANY)).containsExactly("");
  }
}

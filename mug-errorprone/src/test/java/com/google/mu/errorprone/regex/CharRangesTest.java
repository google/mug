package com.google.mu.errorprone.regex;

import static com.google.common.collect.Range.closedOpen;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharRangesTest {

  @Test public void empty_containsNothing() {
    ImmutableRangeSet<Integer> ranges = CharRanges.EMPTY;
    assertThat(ranges.isEmpty()).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void any_containsAllCodePoints() {
    ImmutableRangeSet<Integer> ranges = CharRanges.ANY;
    assertThat(ranges.isEmpty()).isFalse();
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains(0)).isTrue();
    assertThat(ranges.contains(Character.MAX_CODE_POINT)).isTrue();
  }

  @Test public void of_singleCodePoint() {
    ImmutableRangeSet<Integer> ranges = CharRanges.of('c');
    assertThat(ranges.contains((int) 'c')).isTrue();
    assertThat(ranges.contains((int) 'b')).isFalse();
    assertThat(ranges.contains((int) 'd')).isFalse();
  }

  @Test public void range_inclusiveBounds() {
    ImmutableRangeSet<Integer> ranges = CharRanges.range('a', 'z');
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'm')).isTrue();
    assertThat(ranges.contains((int) 'z')).isTrue();
    assertThat(ranges.contains((int) '`')).isFalse();
    assertThat(ranges.contains((int) '{')).isFalse();
  }

  @Test public void range_startGreaterThanEnd_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> CharRanges.range('z', 'a'));
  }

  @Test public void union_adjacentIntervals_mergedIntoSingleRange() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'c');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('d', 'f');
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).containsExactly(closedOpen((int) 'a', (int) 'f' + 1));
  }

  @Test public void union_overlappingIntervals_merged() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'd');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('c', 'f');
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).containsExactly(closedOpen((int) 'a', (int) 'f' + 1));
  }

  @Test public void union_disjointIntervals_keepsBoth() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'c');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('e', 'g');
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).hasSize(2);
  }

  @Test public void intersection_overlappingIntervals_returnsOverlap() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'd');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('c', 'f');
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.asRanges()).containsExactly(closedOpen((int) 'c', (int) 'd' + 1));
  }

  @Test public void intersection_singlePointOverlap_returnsSinglePoint() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'c');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('c', 'e');
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.asRanges()).containsExactly(closedOpen((int) 'c', (int) 'c' + 1));
  }

  @Test public void intersection_disjointIntervals_returnsEmpty() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'c');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('d', 'f');
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.isEmpty()).isTrue();
  }

  @Test public void complement_invertsRanges() {
    ImmutableRangeSet<Integer> r = CharRanges.range(10, 20);
    ImmutableRangeSet<Integer> comp = CharRanges.complement(r);
    assertThat(comp.contains(9)).isTrue();
    assertThat(comp.contains(10)).isFalse();
    assertThat(comp.contains(20)).isFalse();
    assertThat(comp.contains(21)).isTrue();
  }

  @Test public void from_predefinedDigit_contains0And9() {
    ImmutableRangeSet<Integer> digit = CharRanges.from(RegexPattern.PredefinedCharClass.DIGIT);
    assertThat(digit.contains((int) '0')).isTrue();
    assertThat(digit.contains((int) '9')).isTrue();
    assertThat(digit.contains((int) '/')).isFalse();
    assertThat(digit.contains((int) ':')).isFalse();
  }

  @Test public void from_predefinedWord_containsAlphanumericAndUnderscore() {
    ImmutableRangeSet<Integer> word = CharRanges.from(RegexPattern.PredefinedCharClass.WORD);
    assertThat(word.contains((int) 'a')).isTrue();
    assertThat(word.contains((int) 'Z')).isTrue();
    assertThat(word.contains((int) '0')).isTrue();
    assertThat(word.contains((int) '_')).isTrue();
    assertThat(word.contains((int) '-')).isFalse();
  }

  @Test public void from_posixLower_containsLowerAlpha() {
    ImmutableRangeSet<Integer> lower = CharRanges.from(RegexPattern.PosixCharClass.LOWER);
    assertThat(lower.contains((int) 'a')).isTrue();
    assertThat(lower.contains((int) 'z')).isTrue();
    assertThat(lower.contains((int) 'A')).isFalse();
  }

  @Test public void from_anyOf_unionsElements() {
    RegexPattern.CharacterSet.AnyOf anyOf =
        (RegexPattern.CharacterSet.AnyOf) RegexPattern.of("[a-c0-9]");
    ImmutableRangeSet<Integer> ranges = CharRanges.from(anyOf);
    assertThat(ranges.contains((int) 'b')).isTrue();
    assertThat(ranges.contains((int) '5')).isTrue();
    assertThat(ranges.contains((int) 'd')).isFalse();
  }

  @Test public void from_noneOf_complementsElements() {
    RegexPattern.CharacterSet.NoneOf noneOf =
        (RegexPattern.CharacterSet.NoneOf) RegexPattern.of("[^a-c]");
    ImmutableRangeSet<Integer> ranges = CharRanges.from(noneOf);
    assertThat(ranges.contains((int) 'b')).isFalse();
    assertThat(ranges.contains((int) 'd')).isTrue();
  }

  @Test public void intersects_overlappingRanges_returnsTrue() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'd');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('c', 'f');
    assertThat(CharRanges.intersects(r1, r2)).isTrue();
  }

  @Test public void intersects_disjointRanges_returnsFalse() {
    ImmutableRangeSet<Integer> r1 = CharRanges.range('a', 'c');
    ImmutableRangeSet<Integer> r2 = CharRanges.range('d', 'f');
    assertThat(CharRanges.intersects(r1, r2)).isFalse();
  }

  @Test public void intersects_withEmpty_returnsFalse() {
    assertThat(CharRanges.intersects(CharRanges.EMPTY, CharRanges.ANY)).isFalse();
    assertThat(CharRanges.intersects(CharRanges.ANY, CharRanges.EMPTY)).isFalse();
  }

  @Test public void from_predefined_returnsCachedInstance() {
    assertThat(CharRanges.from(RegexPattern.PredefinedCharClass.DIGIT))
        .isSameInstanceAs(CharRanges.from(RegexPattern.PredefinedCharClass.DIGIT));
  }

  @Test public void from_posix_returnsCachedInstance() {
    assertThat(CharRanges.from(RegexPattern.PosixCharClass.ALNUM))
        .isSameInstanceAs(CharRanges.from(RegexPattern.PosixCharClass.ALNUM));
  }

  @Test public void intersection_withEmpty_returnsEmpty() {
    assertThat(CharRanges.intersection(CharRanges.range('a', 'z'), CharRanges.EMPTY).isEmpty())
        .isTrue();
  }

  @Test public void intersection_emptyWithNonEmpty_returnsEmpty() {
    assertThat(CharRanges.intersection(CharRanges.EMPTY, CharRanges.range('a', 'z')).isEmpty())
        .isTrue();
  }

  @Test public void from_unicodePropertyNd_containsDigit() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Nd}]"));
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '9')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyL_containsAlpha() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{L}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'Z')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodePropertyDigit_containsDigit() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Digit}]"));
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '9')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyLetter_containsAlpha() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Letter}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'Z')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unknownUnicodeProperty_returnsAny() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{InHebrew}]"));
    assertThat(ranges).isEqualTo(CharRanges.ANY);
  }

  @Test public void sampleChar_uppercaseOnly_returnsA() {
    assertThat(CharRanges.sampleChar(CharRanges.range('A', 'Z'))).isEqualTo((int) 'A');
  }

  @Test public void sampleChar_digitOnly_returns0() {
    assertThat(CharRanges.sampleChar(CharRanges.range('0', '9'))).isEqualTo((int) '0');
  }

  @Test public void sampleChar_symbolOnly_returnsFirstSymbol() {
    assertThat(CharRanges.sampleChar(CharRanges.range('!', '#'))).isEqualTo((int) '!');
  }

  @Test public void complement_rangeEndingAtMaxCodePoint_invertsCorrectly() {
    ImmutableRangeSet<Integer> r = CharRanges.range(100, Character.MAX_CODE_POINT);
    ImmutableRangeSet<Integer> comp = CharRanges.complement(r);
    assertThat(comp.contains(99)).isTrue();
    assertThat(comp.contains(100)).isFalse();
    assertThat(comp.contains(Character.MAX_CODE_POINT)).isFalse();
  }

  @Test public void from_dotInCharacterSet_treatedAsLiteralDot() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[.]"));
    assertThat(ranges.contains((int) '.')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
    assertThat(ranges.contains((int) '/')).isFalse();
  }

  @Test public void from_unicodePropertyZs_containsSpaceAndNonBreakingSpace() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Zs}]"));
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains(0x00A0)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyZl_containsLineSeparator() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Zl}]"));
    assertThat(ranges.contains(0x2028)).isTrue();
    assertThat(ranges.contains(0x2029)).isFalse();
  }

  @Test public void from_unicodePropertyZp_containsParagraphSeparator() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Zp}]"));
    assertThat(ranges.contains(0x2029)).isTrue();
    assertThat(ranges.contains(0x2028)).isFalse();
  }

  @Test public void from_unicodePropertyLu_containsUppercase() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Lu}]"));
    assertThat(ranges.contains((int) 'A')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyLl_containsLowercase() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Ll}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'A')).isFalse();
  }

  @Test public void from_unicodePropertyAlpha_containsAlpha() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Alpha}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodePropertyAlnum_containsAlphanumeric() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Alnum}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '!')).isFalse();
  }

  @Test public void from_unicodePropertyAscii_containsAscii() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{ASCII}]"));
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains(0x00FF)).isFalse();
  }

  @Test public void from_unicodePropertyPunct_containsPunctuation() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Punct}]"));
    assertThat(ranges.contains((int) '!')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertySpace_containsWhitespace() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of("[\\p{Space}]"));
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }
}

package com.google.mu.errorprone.regex;

import static com.google.common.collect.Range.closedOpen;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
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

  @Test public void union_adjacentIntervals_mergedIntoSingleRange() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-c]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[d-f]");
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).containsExactly(closedOpen((int) 'a', (int) 'f' + 1));
  }

  @Test public void union_overlappingIntervals_merged() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-d]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[c-f]");
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).containsExactly(closedOpen((int) 'a', (int) 'f' + 1));
  }

  @Test public void union_disjointIntervals_keepsBoth() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-c]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[e-g]");
    ImmutableRangeSet<Integer> union = CharRanges.union(r1, r2);
    assertThat(union.asRanges()).hasSize(2);
  }

  @Test public void intersection_overlappingIntervals_returnsOverlap() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-d]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[c-f]");
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.asRanges()).containsExactly(closedOpen((int) 'c', (int) 'd' + 1));
  }

  @Test public void intersection_singlePointOverlap_returnsSinglePoint() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-c]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[c-e]");
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.asRanges()).containsExactly(closedOpen((int) 'c', (int) 'c' + 1));
  }

  @Test public void intersection_disjointIntervals_returnsEmpty() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-c]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[d-f]");
    ImmutableRangeSet<Integer> intersection = CharRanges.intersection(r1, r2);
    assertThat(intersection.isEmpty()).isTrue();
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
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-d]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[c-f]");
    assertThat(CharRanges.intersects(r1, r2)).isTrue();
  }

  @Test public void intersects_disjointRanges_returnsFalse() {
    ImmutableRangeSet<Integer> r1 = fromPattern("[a-c]");
    ImmutableRangeSet<Integer> r2 = fromPattern("[d-f]");
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
    assertThat(CharRanges.intersection(fromPattern("[a-z]"), CharRanges.EMPTY).isEmpty()).isTrue();
  }

  @Test public void intersection_emptyWithNonEmpty_returnsEmpty() {
    assertThat(CharRanges.intersection(CharRanges.EMPTY, fromPattern("[a-z]")).isEmpty()).isTrue();
  }

  @Test public void from_unicodePropertyNd_containsDigit() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Nd}]");
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '9')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyL_containsAlpha() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{L}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'Z')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodePropertyDigit_containsDigit() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Digit}]");
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '9')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyLetter_containsAlpha() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Letter}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'Z')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodePropertyL_containsNonAsciiLetter() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{L}]");
    assertThat(ranges.contains((int) 'é')).isTrue();
  }

  @Test public void from_unicodePropertyLu_containsNonAsciiUppercase() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Lu}]");
    assertThat(ranges.contains((int) 'É')).isTrue();
  }

  @Test public void from_unicodePropertyLl_containsNonAsciiLowercase() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Ll}]");
    assertThat(ranges.contains((int) 'é')).isTrue();
  }

  @Test public void from_unicodePropertyNd_containsNonAsciiDigit() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Nd}]");
    assertThat(ranges.contains(0x0660)).isTrue();
  }

  @Test public void from_unicodeProperty_inHebrew_returnsHebrewBlock() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{InHebrew}]");
    assertThat(ranges.contains(0x05D0)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
    assertThat(ranges).isNotEqualTo(CharRanges.ANY);
  }

  @Test public void from_unicodeProperty_negatedInHebrew_isNotEmpty() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[^\\p{InHebrew}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains(0x05D0)).isFalse();
    assertThat(ranges).isNotEqualTo(CharRanges.EMPTY);
  }

  @Test public void from_unicodeProperty_unrecognized_throws() {
    assertThrows(IllegalArgumentException.class, () -> fromPattern("[\\p{NoSuchProperty}]"));
  }

  @Test public void from_unicodePropertyN_containsDigitsAndLetterNumbers() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{N}]");
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains(0x2160)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodeProperty_singleLetterUnbraced_number() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\pN]");
    assertThat(ranges.contains((int) '5')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodeProperty_numberLongName() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Number}]");
    assertThat(ranges.contains((int) '5')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyP_containsPunctuation() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{P}]");
    assertThat(ranges.contains((int) '!')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyS_containsSymbol() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{S}]");
    assertThat(ranges.contains((int) '$')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyM_containsMark() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{M}]");
    assertThat(ranges.contains(0x0300)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyC_containsControl() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{C}]");
    assertThat(ranges.contains(0)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertySc_containsCurrency() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Sc}]");
    assertThat(ranges.contains((int) '$')).isTrue();
    assertThat(ranges.contains(0x20AC)).isTrue();
    assertThat(ranges.contains((int) '+')).isFalse();
  }

  @Test public void from_unicodePropertySm_containsMath() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Sm}]");
    assertThat(ranges.contains((int) '+')).isTrue();
    assertThat(ranges.contains((int) '=')).isTrue();
    assertThat(ranges.contains((int) '$')).isFalse();
  }

  @Test public void from_unicodePropertyPd_containsDash() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Pd}]");
    assertThat(ranges.contains((int) '-')).isTrue();
    assertThat(ranges.contains(0x2014)).isTrue();
    assertThat(ranges.contains((int) '!')).isFalse();
  }

  @Test public void from_unicodeProperty_isAlphabetic_containsLettersAndLetterNumbers() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsAlphabetic}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains(0x2160)).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodeProperty_isDigit_containsDigits() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsDigit}]");
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains(0x0660)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodeProperty_isIdeographic_containsCjk() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsIdeographic}]");
    assertThat(ranges.contains(0x4E00)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodeProperty_isWhitespace_containsWhitespace() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsWhitespace}]");
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains((int) '\t')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodeProperty_isLowerCase_containsLower() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsLowerCase}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'A')).isFalse();
  }

  @Test public void from_unicodeProperty_isUpperCase_containsUpper() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{IsUpperCase}]");
    assertThat(ranges.contains((int) 'A')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void sampleChar_uppercaseOnly_returnsA() {
    assertThat(CharRanges.sampleChar(fromPattern("[A-Z]"))).isEqualTo((int) 'A');
  }

  @Test public void sampleChar_digitOnly_returns0() {
    assertThat(CharRanges.sampleChar(CharRanges.from(RegexPattern.PredefinedCharClass.DIGIT)))
        .isEqualTo((int) '0');
  }

  @Test public void sampleChar_symbolOnly_returnsFirstSymbol() {
    assertThat(CharRanges.sampleChar(fromPattern("[!-#]"))).isEqualTo((int) '!');
  }

  @Test public void from_dotInCharacterSet_treatedAsLiteralDot() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[.]");
    assertThat(ranges.contains((int) '.')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
    assertThat(ranges.contains((int) '/')).isFalse();
  }

  @Test public void from_unicodePropertyZs_containsSpaceAndNonBreakingSpace() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Zs}]");
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains(0x00A0)).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyZl_containsLineSeparator() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Zl}]");
    assertThat(ranges.contains(0x2028)).isTrue();
    assertThat(ranges.contains(0x2029)).isFalse();
  }

  @Test public void from_unicodePropertyZp_containsParagraphSeparator() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Zp}]");
    assertThat(ranges.contains(0x2029)).isTrue();
    assertThat(ranges.contains(0x2028)).isFalse();
  }

  @Test public void from_unicodePropertyLu_containsUppercase() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Lu}]");
    assertThat(ranges.contains((int) 'A')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertyLl_containsLowercase() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Ll}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) 'A')).isFalse();
  }

  @Test public void from_unicodePropertyAlpha_containsAlpha() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Alpha}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) '0')).isFalse();
  }

  @Test public void from_unicodePropertyAlnum_containsAlphanumeric() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Alnum}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains((int) '0')).isTrue();
    assertThat(ranges.contains((int) '!')).isFalse();
  }

  @Test public void from_unicodePropertyAscii_containsAscii() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{ASCII}]");
    assertThat(ranges.contains((int) 'a')).isTrue();
    assertThat(ranges.contains(0x00FF)).isFalse();
  }

  @Test public void from_unicodePropertyPunct_containsPunctuation() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Punct}]");
    assertThat(ranges.contains((int) '!')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void from_unicodePropertySpace_containsWhitespace() {
    ImmutableRangeSet<Integer> ranges = fromPattern("[\\p{Space}]");
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains((int) 'a')).isFalse();
  }

  @Test public void firstCharRangesOf_nullableFirstElement_includesSecondElement() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.firstCharRangesOf(RegexPattern.of("\\s?foo"));
    assertThat(ranges.contains((int) ' ')).isTrue();
    assertThat(ranges.contains((int) 'f')).isTrue();
  }

  @Test public void from_anyChar_excludesUnicodeLinebreakCharacters() {
    ImmutableRangeSet<Integer> anyChar = CharRanges.from(RegexPattern.PredefinedCharClass.ANY_CHAR);
    assertThat(anyChar.contains(0x85)).isFalse();
    assertThat(anyChar.contains(0x2028)).isFalse();
    assertThat(anyChar.contains(0x2029)).isFalse();
  }

  @Test public void from_whitespace_containsUnicodeWhitespaceAndLinebreaks() {
    ImmutableRangeSet<Integer> ws = CharRanges.from(RegexPattern.PredefinedCharClass.WHITESPACE);
    assertThat(ws.contains((int) ' ')).isTrue();
    assertThat(ws.contains((int) '\t')).isTrue();
    assertThat(ws.contains((int) '\n')).isTrue();
    assertThat(ws.contains((int) '\r')).isTrue();
    assertThat(ws.contains(0x85)).isTrue();
    assertThat(ws.contains(0x2028)).isTrue();
    assertThat(ws.contains(0x2029)).isTrue();
  }

  @Test public void from_linebreak_containsAllJavaLinebreakCharacters() {
    ImmutableRangeSet<Integer> lb = CharRanges.from(RegexPattern.PredefinedCharClass.LINEBREAK);
    assertThat(lb.contains((int) '\n')).isTrue();
    assertThat(lb.contains((int) '\r')).isTrue();
    assertThat(lb.contains(0x85)).isTrue();
    assertThat(lb.contains(0x2028)).isTrue();
    assertThat(lb.contains(0x2029)).isTrue();
    assertThat(lb.contains((int) ' ')).isFalse();
  }

  @Test public void from_horizontalWhitespace_containsAllHorizontalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> hws = CharRanges.from(PredefinedCharClass.HORIZONTAL_WHITESPACE);
    assertThat(hws.contains((int) ' ')).isTrue();
    assertThat(hws.contains((int) '\t')).isTrue();
    assertThat(hws.contains(0x00A0)).isTrue();
    assertThat(hws.contains(0x1680)).isTrue();
    assertThat(hws.contains(0x180E)).isTrue();
    assertThat(hws.contains(0x2000)).isTrue();
    assertThat(hws.contains(0x2005)).isTrue();
    assertThat(hws.contains(0x200A)).isTrue();
    assertThat(hws.contains(0x202F)).isTrue();
    assertThat(hws.contains(0x205F)).isTrue();
    assertThat(hws.contains(0x3000)).isTrue();
  }

  @Test public void from_horizontalWhitespace_excludesNonHorizontalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> hws = CharRanges.from(PredefinedCharClass.HORIZONTAL_WHITESPACE);
    assertThat(hws.contains(0x1FFF)).isFalse();
    assertThat(hws.contains(0x200B)).isFalse();
    assertThat(hws.contains(0x180D)).isFalse();
    assertThat(hws.contains(0x180F)).isFalse();
    assertThat(hws.contains(0x202E)).isFalse();
    assertThat(hws.contains(0x2030)).isFalse();
    assertThat(hws.contains(0x205E)).isFalse();
    assertThat(hws.contains(0x2060)).isFalse();
    assertThat(hws.contains(0x2FFF)).isFalse();
    assertThat(hws.contains(0x3001)).isFalse();
    assertThat(hws.contains((int) '\n')).isFalse();
    assertThat(hws.contains((int) '\r')).isFalse();
    assertThat(hws.contains((int) 'a')).isFalse();
    assertThat(hws.contains((int) '0')).isFalse();
  }

  @Test public void from_nonHorizontalWhitespace_excludesHorizontalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonHws =
        CharRanges.from(PredefinedCharClass.NON_HORIZONTAL_WHITESPACE);
    assertThat(nonHws.contains((int) ' ')).isFalse();
    assertThat(nonHws.contains((int) '\t')).isFalse();
    assertThat(nonHws.contains(0x00A0)).isFalse();
    assertThat(nonHws.contains(0x1680)).isFalse();
    assertThat(nonHws.contains(0x180E)).isFalse();
    assertThat(nonHws.contains(0x2000)).isFalse();
    assertThat(nonHws.contains(0x2005)).isFalse();
    assertThat(nonHws.contains(0x200A)).isFalse();
    assertThat(nonHws.contains(0x202F)).isFalse();
    assertThat(nonHws.contains(0x205F)).isFalse();
    assertThat(nonHws.contains(0x3000)).isFalse();
  }

  @Test public void from_nonHorizontalWhitespace_containsNonHorizontalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonHws =
        CharRanges.from(PredefinedCharClass.NON_HORIZONTAL_WHITESPACE);
    assertThat(nonHws.contains(0x1FFF)).isTrue();
    assertThat(nonHws.contains(0x200B)).isTrue();
    assertThat(nonHws.contains((int) '\n')).isTrue();
    assertThat(nonHws.contains((int) '\r')).isTrue();
    assertThat(nonHws.contains((int) 'a')).isTrue();
    assertThat(nonHws.contains((int) '0')).isTrue();
  }

  @Test public void from_verticalWhitespace_containsAllVerticalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> vws = CharRanges.from(PredefinedCharClass.VERTICAL_WHITESPACE);
    assertThat(vws.contains((int) '\n')).isTrue();
    assertThat(vws.contains(0x0B)).isTrue();
    assertThat(vws.contains((int) '\f')).isTrue();
    assertThat(vws.contains((int) '\r')).isTrue();
    assertThat(vws.contains(0x85)).isTrue();
    assertThat(vws.contains(0x2028)).isTrue();
    assertThat(vws.contains(0x2029)).isTrue();
  }

  @Test public void from_verticalWhitespace_excludesNonVerticalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> vws = CharRanges.from(PredefinedCharClass.VERTICAL_WHITESPACE);
    assertThat(vws.contains(0x2027)).isFalse();
    assertThat(vws.contains(0x202A)).isFalse();
    assertThat(vws.contains(0x84)).isFalse();
    assertThat(vws.contains(0x86)).isFalse();
    assertThat(vws.contains((int) ' ')).isFalse();
    assertThat(vws.contains((int) '\t')).isFalse();
    assertThat(vws.contains((int) 'a')).isFalse();
    assertThat(vws.contains((int) '0')).isFalse();
  }

  @Test public void from_nonVerticalWhitespace_excludesVerticalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonVws =
        CharRanges.from(PredefinedCharClass.NON_VERTICAL_WHITESPACE);
    assertThat(nonVws.contains((int) '\n')).isFalse();
    assertThat(nonVws.contains(0x0B)).isFalse();
    assertThat(nonVws.contains((int) '\f')).isFalse();
    assertThat(nonVws.contains((int) '\r')).isFalse();
    assertThat(nonVws.contains(0x85)).isFalse();
    assertThat(nonVws.contains(0x2028)).isFalse();
    assertThat(nonVws.contains(0x2029)).isFalse();
  }

  @Test public void from_nonVerticalWhitespace_containsNonVerticalWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonVws =
        CharRanges.from(PredefinedCharClass.NON_VERTICAL_WHITESPACE);
    assertThat(nonVws.contains((int) ' ')).isTrue();
    assertThat(nonVws.contains((int) '\t')).isTrue();
    assertThat(nonVws.contains(0x2027)).isTrue();
    assertThat(nonVws.contains(0x202A)).isTrue();
    assertThat(nonVws.contains(0x84)).isTrue();
    assertThat(nonVws.contains(0x86)).isTrue();
    assertThat(nonVws.contains((int) 'a')).isTrue();
    assertThat(nonVws.contains((int) '0')).isTrue();
  }

  @Test public void from_nonDigit_excludesDigits() {
    ImmutableRangeSet<Integer> nonDigit = CharRanges.from(PredefinedCharClass.NON_DIGIT);
    assertThat(nonDigit.contains((int) '0')).isFalse();
    assertThat(nonDigit.contains((int) '5')).isFalse();
    assertThat(nonDigit.contains((int) '9')).isFalse();
  }

  @Test public void from_nonDigit_containsNonDigits() {
    ImmutableRangeSet<Integer> nonDigit = CharRanges.from(PredefinedCharClass.NON_DIGIT);
    assertThat(nonDigit.contains((int) '/')).isTrue();
    assertThat(nonDigit.contains((int) ':')).isTrue();
    assertThat(nonDigit.contains((int) 'a')).isTrue();
    assertThat(nonDigit.contains((int) ' ')).isTrue();
  }

  @Test public void from_nonWord_excludesWordCharacters() {
    ImmutableRangeSet<Integer> nonWord = CharRanges.from(PredefinedCharClass.NON_WORD);
    assertThat(nonWord.contains((int) 'a')).isFalse();
    assertThat(nonWord.contains((int) 'z')).isFalse();
    assertThat(nonWord.contains((int) 'A')).isFalse();
    assertThat(nonWord.contains((int) 'Z')).isFalse();
    assertThat(nonWord.contains((int) '0')).isFalse();
    assertThat(nonWord.contains((int) '9')).isFalse();
    assertThat(nonWord.contains((int) '_')).isFalse();
  }

  @Test public void from_nonWord_containsNonWordCharacters() {
    ImmutableRangeSet<Integer> nonWord = CharRanges.from(PredefinedCharClass.NON_WORD);
    assertThat(nonWord.contains((int) ' ')).isTrue();
    assertThat(nonWord.contains((int) '-')).isTrue();
    assertThat(nonWord.contains((int) '!')).isTrue();
    assertThat(nonWord.contains((int) '@')).isTrue();
    assertThat(nonWord.contains((int) '[')).isTrue();
  }

  @Test public void from_nonWhitespace_excludesWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonWs = CharRanges.from(PredefinedCharClass.NON_WHITESPACE);
    assertThat(nonWs.contains((int) ' ')).isFalse();
    assertThat(nonWs.contains((int) '\t')).isFalse();
    assertThat(nonWs.contains((int) '\n')).isFalse();
    assertThat(nonWs.contains((int) '\r')).isFalse();
    assertThat(nonWs.contains(0x85)).isFalse();
    assertThat(nonWs.contains(0x2028)).isFalse();
    assertThat(nonWs.contains(0x2029)).isFalse();
  }

  @Test public void from_nonWhitespace_containsNonWhitespaceCharacters() {
    ImmutableRangeSet<Integer> nonWs = CharRanges.from(PredefinedCharClass.NON_WHITESPACE);
    assertThat(nonWs.contains((int) 'a')).isTrue();
    assertThat(nonWs.contains((int) '0')).isTrue();
    assertThat(nonWs.contains((int) '-')).isTrue();
    assertThat(nonWs.contains(0x84)).isTrue();
    assertThat(nonWs.contains(0x86)).isTrue();
    assertThat(nonWs.contains(0x2027)).isTrue();
    assertThat(nonWs.contains(0x202A)).isTrue();
  }

  @Test public void from_extendedGraphemeCluster_matchesAny() {
    ImmutableRangeSet<Integer> x = CharRanges.from(PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER);
    assertThat(x).isEqualTo(CharRanges.ANY);
  }

  @Test public void from_posixUpper_containsUpperAlpha() {
    ImmutableRangeSet<Integer> upper = CharRanges.from(PosixCharClass.UPPER);
    assertThat(upper.contains((int) 'A')).isTrue();
    assertThat(upper.contains((int) 'Z')).isTrue();
    assertThat(upper.contains((int) 'a')).isFalse();
    assertThat(upper.contains((int) '@')).isFalse();
    assertThat(upper.contains((int) '[')).isFalse();
  }

  @Test public void from_posixAscii_containsAscii() {
    ImmutableRangeSet<Integer> ascii = CharRanges.from(PosixCharClass.ASCII);
    assertThat(ascii.contains(0)).isTrue();
    assertThat(ascii.contains(0x7F)).isTrue();
    assertThat(ascii.contains(0x80)).isFalse();
  }

  @Test public void from_posixAlpha_containsAlpha() {
    ImmutableRangeSet<Integer> alpha = CharRanges.from(PosixCharClass.ALPHA);
    assertThat(alpha.contains((int) 'a')).isTrue();
    assertThat(alpha.contains((int) 'z')).isTrue();
    assertThat(alpha.contains((int) 'A')).isTrue();
    assertThat(alpha.contains((int) 'Z')).isTrue();
    assertThat(alpha.contains((int) '0')).isFalse();
    assertThat(alpha.contains((int) '_')).isFalse();
  }

  @Test public void from_posixDigit_contains0And9() {
    ImmutableRangeSet<Integer> digit = CharRanges.from(PosixCharClass.DIGIT);
    assertThat(digit.contains((int) '0')).isTrue();
    assertThat(digit.contains((int) '9')).isTrue();
    assertThat(digit.contains((int) '/')).isFalse();
    assertThat(digit.contains((int) ':')).isFalse();
  }

  @Test public void from_posixAlnum_containsAlphaAndDigit() {
    ImmutableRangeSet<Integer> alnum = CharRanges.from(PosixCharClass.ALNUM);
    assertThat(alnum.contains((int) 'a')).isTrue();
    assertThat(alnum.contains((int) 'Z')).isTrue();
    assertThat(alnum.contains((int) '0')).isTrue();
    assertThat(alnum.contains((int) '9')).isTrue();
    assertThat(alnum.contains((int) '_')).isFalse();
    assertThat(alnum.contains((int) '!')).isFalse();
  }

  @Test public void from_posixPunct_containsPunctuation() {
    ImmutableRangeSet<Integer> punct = CharRanges.from(PosixCharClass.PUNCT);
    assertThat(punct.contains((int) '!')).isTrue();
    assertThat(punct.contains((int) '/')).isTrue();
    assertThat(punct.contains((int) ':')).isTrue();
    assertThat(punct.contains((int) '@')).isTrue();
    assertThat(punct.contains((int) '[')).isTrue();
    assertThat(punct.contains((int) '`')).isTrue();
    assertThat(punct.contains((int) '{')).isTrue();
    assertThat(punct.contains((int) '~')).isTrue();
    assertThat(punct.contains((int) 'a')).isFalse();
    assertThat(punct.contains((int) '0')).isFalse();
    assertThat(punct.contains((int) ' ')).isFalse();
  }

  @Test public void from_posixGraph_containsVisibleCharacters() {
    ImmutableRangeSet<Integer> graph = CharRanges.from(PosixCharClass.GRAPH);
    assertThat(graph.contains(0x21)).isTrue();
    assertThat(graph.contains(0x7E)).isTrue();
    assertThat(graph.contains((int) ' ')).isFalse();
    assertThat(graph.contains(0x7F)).isFalse();
  }

  @Test public void from_posixPrint_containsPrintableCharacters() {
    ImmutableRangeSet<Integer> print = CharRanges.from(PosixCharClass.PRINT);
    assertThat(print.contains((int) ' ')).isTrue();
    assertThat(print.contains(0x7E)).isTrue();
    assertThat(print.contains(0x1F)).isFalse();
    assertThat(print.contains(0x7F)).isFalse();
  }

  @Test public void from_posixBlank_containsSpaceAndTab() {
    ImmutableRangeSet<Integer> blank = CharRanges.from(PosixCharClass.BLANK);
    assertThat(blank.contains((int) ' ')).isTrue();
    assertThat(blank.contains((int) '\t')).isTrue();
    assertThat(blank.contains((int) '\n')).isFalse();
    assertThat(blank.contains(0xA0)).isFalse();
    assertThat(blank.contains((int) 'a')).isFalse();
  }

  @Test public void from_posixCntrl_containsControlCharacters() {
    ImmutableRangeSet<Integer> cntrl = CharRanges.from(PosixCharClass.CNTRL);
    assertThat(cntrl.contains(0)).isTrue();
    assertThat(cntrl.contains(0x1F)).isTrue();
    assertThat(cntrl.contains(0x7F)).isTrue();
    assertThat(cntrl.contains((int) ' ')).isFalse();
    assertThat(cntrl.contains(0x7E)).isFalse();
  }

  @Test public void from_posixXdigit_containsHexDigits() {
    ImmutableRangeSet<Integer> xdigit = CharRanges.from(PosixCharClass.XDIGIT);
    assertThat(xdigit.contains((int) '0')).isTrue();
    assertThat(xdigit.contains((int) '9')).isTrue();
    assertThat(xdigit.contains((int) 'a')).isTrue();
    assertThat(xdigit.contains((int) 'f')).isTrue();
    assertThat(xdigit.contains((int) 'A')).isTrue();
    assertThat(xdigit.contains((int) 'F')).isTrue();
    assertThat(xdigit.contains((int) 'g')).isFalse();
    assertThat(xdigit.contains((int) 'G')).isFalse();
    assertThat(xdigit.contains((int) ':')).isFalse();
    assertThat(xdigit.contains((int) '/')).isFalse();
  }

  @Test public void from_posixSpace_containsWhitespace() {
    ImmutableRangeSet<Integer> space = CharRanges.from(PosixCharClass.SPACE);
    assertThat(space.contains((int) ' ')).isTrue();
    assertThat(space.contains((int) '\t')).isTrue();
    assertThat(space.contains((int) '\n')).isTrue();
    assertThat(space.contains((int) '\r')).isTrue();
    assertThat(space.contains((int) 'a')).isFalse();
  }

  @Test public void from_anyChar_containsVerticalTab() {
    ImmutableRangeSet<Integer> anyChar = CharRanges.from(PredefinedCharClass.ANY_CHAR);
    assertThat(anyChar.contains(0x0B)).isTrue();
  }

  @Test public void from_anyChar_containsFormFeed() {
    ImmutableRangeSet<Integer> anyChar = CharRanges.from(PredefinedCharClass.ANY_CHAR);
    assertThat(anyChar.contains((int) '\f')).isTrue();
  }

  @Test public void firstCharRangesOf_unicodeProperty_returnsPropertyRanges() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.firstCharRangesOf(RegexPattern.of("\\p{L}"));
    assertThat(ranges.contains((int) 'a')).isTrue();
  }

  @Test public void firstCharRangesOf_negatedCharacterProperty_returnsPropertyRanges() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.firstCharRangesOf(RegexPattern.of("\\P{Digit}"));
    assertThat(ranges.contains((int) 'a')).isTrue();
  }

  @Test public void firstCharRangesOf_supplementaryLiteral_returnsFullCodePoint() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.firstCharRangesOf(RegexPattern.of("\uD83D\uDE00"));
    assertThat(ranges.contains(0x1F600)).isTrue();
  }

  @Test public void charRangesOf_unicodeProperty_returnsPropertyRanges() {
    ImmutableRangeSet<Integer> ranges = RegexPatternUtils.charRangesOf(RegexPattern.of("\\p{L}"));
    assertThat(ranges.contains((int) 'a')).isTrue();
  }

  @Test public void charRangesOf_negatedCharacterProperty_returnsPropertyRanges() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.charRangesOf(RegexPattern.of("\\P{Digit}"));
    assertThat(ranges.contains((int) 'a')).isTrue();
  }

  @Test public void charRangesOf_supplementaryLiteral_returnsFullCodePoint() {
    ImmutableRangeSet<Integer> ranges =
        RegexPatternUtils.charRangesOf(RegexPattern.of("\uD83D\uDE00"));
    assertThat(ranges.contains(0x1F600)).isTrue();
  }

  @Test public void from_characterSet_containingLinebreak_evaluatesToLinebreakRanges() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from(RegexPattern.anyOf(RegexPattern.PredefinedCharClass.LINEBREAK));
    assertThat(ranges).isEqualTo(CharRanges.from(RegexPattern.PredefinedCharClass.LINEBREAK));
  }

  @Test public void from_characterSet_containingExtendedGraphemeCluster_evaluatesToAny() {
    ImmutableRangeSet<Integer> ranges = CharRanges.from(
        RegexPattern.anyOf(RegexPattern.PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER));
    assertThat(ranges).isEqualTo(CharRanges.ANY);
  }

  @Test public void from_characterSet_containingAnyChar_evaluatesToAnyCharRanges() {
    ImmutableRangeSet<Integer> ranges =
        CharRanges.from(RegexPattern.anyOf(RegexPattern.PredefinedCharClass.ANY_CHAR));
    assertThat(ranges).isEqualTo(CharRanges.from(RegexPattern.PredefinedCharClass.ANY_CHAR));
  }

  private static ImmutableRangeSet<Integer> fromPattern(String regex) {
    return CharRanges.from((RegexPattern.CharacterSet) RegexPattern.of(regex));
  }
}

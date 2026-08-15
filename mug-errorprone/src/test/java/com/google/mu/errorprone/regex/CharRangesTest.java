package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.regex.RegexPattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CharRangesTest {

  @Test public void empty_containsNothing() {
    CharRanges ranges = CharRanges.empty();
    assertThat(ranges.isEmpty()).isTrue();
    assertThat(ranges.contains('a')).isFalse();
  }

  @Test public void any_containsAllCodePoints() {
    CharRanges ranges = CharRanges.any();
    assertThat(ranges.isEmpty()).isFalse();
    assertThat(ranges.contains('a')).isTrue();
    assertThat(ranges.contains(0)).isTrue();
    assertThat(ranges.contains(Character.MAX_CODE_POINT)).isTrue();
  }

  @Test public void of_singleCodePoint() {
    CharRanges ranges = CharRanges.of('c');
    assertThat(ranges.contains('c')).isTrue();
    assertThat(ranges.contains('b')).isFalse();
    assertThat(ranges.contains('d')).isFalse();
  }

  @Test public void range_inclusiveBounds() {
    CharRanges ranges = CharRanges.range('a', 'z');
    assertThat(ranges.contains('a')).isTrue();
    assertThat(ranges.contains('m')).isTrue();
    assertThat(ranges.contains('z')).isTrue();
    assertThat(ranges.contains('`')).isFalse();
    assertThat(ranges.contains('{')).isFalse();
  }

  @Test public void union_adjacentIntervals_mergedIntoSingleRange() {
    CharRanges r1 = CharRanges.range('a', 'c');
    CharRanges r2 = CharRanges.range('d', 'f');
    CharRanges union = r1.union(r2);
    assertThat(union.ranges()).hasSize(1);
    assertThat(union.ranges().get(0)).isEqualTo(new CharRanges.Range('a', 'f'));
  }

  @Test public void union_overlappingIntervals_merged() {
    CharRanges r1 = CharRanges.range('a', 'd');
    CharRanges r2 = CharRanges.range('c', 'f');
    CharRanges union = r1.union(r2);
    assertThat(union.ranges()).hasSize(1);
    assertThat(union.ranges().get(0)).isEqualTo(new CharRanges.Range('a', 'f'));
  }

  @Test public void union_disjointIntervals_keepsBoth() {
    CharRanges r1 = CharRanges.range('a', 'c');
    CharRanges r2 = CharRanges.range('e', 'g');
    CharRanges union = r1.union(r2);
    assertThat(union.ranges()).hasSize(2);
  }

  @Test public void intersection_overlappingIntervals_returnsOverlap() {
    CharRanges r1 = CharRanges.range('a', 'd');
    CharRanges r2 = CharRanges.range('c', 'f');
    CharRanges intersection = r1.intersection(r2);
    assertThat(intersection.ranges()).hasSize(1);
    assertThat(intersection.ranges().get(0)).isEqualTo(new CharRanges.Range('c', 'd'));
  }

  @Test public void intersection_singlePointOverlap_returnsSinglePoint() {
    CharRanges r1 = CharRanges.range('a', 'c');
    CharRanges r2 = CharRanges.range('c', 'e');
    CharRanges intersection = r1.intersection(r2);
    assertThat(intersection.ranges()).hasSize(1);
    assertThat(intersection.ranges().get(0)).isEqualTo(new CharRanges.Range('c', 'c'));
  }

  @Test public void intersection_disjointIntervals_returnsEmpty() {
    CharRanges r1 = CharRanges.range('a', 'c');
    CharRanges r2 = CharRanges.range('d', 'f');
    CharRanges intersection = r1.intersection(r2);
    assertThat(intersection.isEmpty()).isTrue();
  }

  @Test public void complement_invertsRanges() {
    CharRanges r = CharRanges.range(10, 20);
    CharRanges comp = r.complement();
    assertThat(comp.contains(9)).isTrue();
    assertThat(comp.contains(10)).isFalse();
    assertThat(comp.contains(20)).isFalse();
    assertThat(comp.contains(21)).isTrue();
  }

  @Test public void from_predefinedDigit_contains0And9() {
    CharRanges digit = CharRanges.from(RegexPattern.PredefinedCharClass.DIGIT);
    assertThat(digit.contains('0')).isTrue();
    assertThat(digit.contains('9')).isTrue();
    assertThat(digit.contains('/')).isFalse();
    assertThat(digit.contains(':')).isFalse();
  }

  @Test public void from_predefinedWord_containsAlphanumericAndUnderscore() {
    CharRanges word = CharRanges.from(RegexPattern.PredefinedCharClass.WORD);
    assertThat(word.contains('a')).isTrue();
    assertThat(word.contains('Z')).isTrue();
    assertThat(word.contains('0')).isTrue();
    assertThat(word.contains('_')).isTrue();
    assertThat(word.contains('-')).isFalse();
  }

  @Test public void from_posixLower_containsLowerAlpha() {
    CharRanges lower = CharRanges.from(RegexPattern.PosixCharClass.LOWER);
    assertThat(lower.contains('a')).isTrue();
    assertThat(lower.contains('z')).isTrue();
    assertThat(lower.contains('A')).isFalse();
  }

  @Test public void from_anyOf_unionsElements() {
    RegexPattern.CharacterSet.AnyOf anyOf =
        (RegexPattern.CharacterSet.AnyOf) RegexPattern.of("[a-c0-9]");
    CharRanges ranges = CharRanges.from(anyOf);
    assertThat(ranges.contains('b')).isTrue();
    assertThat(ranges.contains('5')).isTrue();
    assertThat(ranges.contains('d')).isFalse();
  }

  @Test public void from_noneOf_complementsElements() {
    RegexPattern.CharacterSet.NoneOf noneOf =
        (RegexPattern.CharacterSet.NoneOf) RegexPattern.of("[^a-c]");
    CharRanges ranges = CharRanges.from(noneOf);
    assertThat(ranges.contains('b')).isFalse();
    assertThat(ranges.contains('d')).isTrue();
  }
}

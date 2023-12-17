package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;

final class SubstringPatternAssertion {
  private final Substring.Pattern pattern;
  private final String input;

  SubstringPatternAssertion(Substring.Pattern pattern, String input) {
    this.pattern = pattern;
    this.input = input;
  }

  void findsNothing() {
    assertThat(pattern.in(input)).isEmpty();
    assertThat(pattern.from(input)).isEmpty();
    assertThat(pattern.repeatedly().from(input)).isEmpty();
    assertThat(pattern.repeatedly().match(input).limit(10)).isEmpty();
    assertThat(pattern.split(input)).isEqualTo(BiOptional.empty());
    assertThat(pattern.splitThenTrim(input)).isEqualTo(BiOptional.empty());
    assertThat(pattern.repeatedly().split(input).map(Substring.Match::toString))
        .containsExactly(input);
    assertThat(pattern.repeatedly().splitThenTrim(input).map(Substring.Match::toString))
        .containsExactly(input.trim());
  }

  void finds(String... findings) {
    assertThat(pattern.from(input)).hasValue(findings[0]);
    assertThat(pattern.repeatedly().from(input).limit(findings.length + 10))
        .containsExactlyElementsIn(asList(findings))
        .inOrder();
  }

  void findsBetween(String before, String after) {
    assertThat(pattern.in(input).map(Substring.Match::before)).hasValue(before);
    assertThat(pattern.in(input).map(Substring.Match::after)).hasValue(after);
  }

  void findsDistinct(String... findings) {
    assertThat(pattern.from(input)).hasValue(findings[0]);
    assertThat(pattern.repeatedly().from(input).distinct().limit(findings.length + 10))
        .containsExactlyElementsIn(asList(findings))
        .inOrder();
  }

  void splitsTo(String... parts) {
    assertThat(
            pattern
                .repeatedly()
                .split(input)
                .map(Substring.Match::toString)
                .limit(parts.length + 10))
        .containsExactlyElementsIn(asList(parts))
        .inOrder();
  }

  void splitsThenTrimsTo(String... parts) {
    assertThat(
            pattern
                .repeatedly()
                .splitThenTrim(input)
                .map(Substring.Match::toString)
                .limit(parts.length + 10))
        .containsExactlyElementsIn(asList(parts))
        .inOrder();
  }

  void splitsDistinctTo(String... parts) {
    assertThat(
            pattern
                .repeatedly()
                .split(input)
                .map(Substring.Match::toString)
                .distinct()
                .limit(parts.length + 10))
        .containsExactlyElementsIn(asList(parts))
        .inOrder();
  }

  void splitsThenTrimsDistinctTo(String... parts) {
    assertThat(
            pattern
                .repeatedly()
                .splitThenTrim(input)
                .map(Substring.Match::toString)
                .distinct()
                .limit(parts.length + 10))
        .containsExactlyElementsIn(asList(parts))
        .inOrder();
  }

  void cutsTo(String... parts) {
    assertThat(
            pattern.repeatedly().cut(input).map(Substring.Match::toString).limit(parts.length + 10))
        .containsExactlyElementsIn(asList(parts))
        .inOrder();
  }

  void twoWaySplitsTo(String left, String right) {
    assertThat(pattern.split(input).map((a, b) -> a)).hasValue(left);
    assertThat(pattern.split(input).map((a, b) -> b)).hasValue(right);
  }

  void twoWaySplitsThenTrimsTo(String left, String right) {
    assertThat(pattern.splitThenTrim(input).map((a, b) -> a)).hasValue(left);
    assertThat(pattern.splitThenTrim(input).map((a, b) -> b)).hasValue(right);
  }
}
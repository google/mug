package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CodePointMatcherTest {

  @Test public void matcher_range() {
    CodePointMatcher matcher = CodePointMatcher.of(Character::isWhitespace);
    assertThat(CodePointMatcher.range('a', 'c').test('a')).isTrue();
    assertThat(CodePointMatcher.range('a', 'c').test('b')).isTrue();
    assertThat(CodePointMatcher.range('a', 'c').test('c')).isTrue();
    assertThat(CodePointMatcher.range('a', 'c').test('d')).isFalse();
    assertThat(CodePointMatcher.range('b', 'c').test('a')).isFalse();
    assertThat(CodePointMatcher.range('a', 'c').toString()).isEqualTo("['a', 'c']");
    assertThat(CodePointMatcher.of(matcher)).isSameAs(matcher);
  }

  @Test public void matcher_of() {
    CodePointMatcher matcher = CodePointMatcher.of(Character::isWhitespace);
    assertThat(matcher.test(' ')).isTrue();
    assertThat(CodePointMatcher.of(matcher)).isSameAs(matcher);
  }

  @Test public void matcher_and() {
    assertThat(CodePointMatcher.is('a').and(CodePointMatcher.range('a', 'b')).test('a')).isTrue();
    assertThat(CodePointMatcher.is('a').and(CodePointMatcher.range('a', 'b')).test('b')).isFalse();
    assertThat(CodePointMatcher.is('a').and(CodePointMatcher.NONE).test('a')).isFalse();
    assertThat(CodePointMatcher.is('a').and(CodePointMatcher.ASCII).toString()).isEqualTo("'a' & ASCII");
  }

  @Test public void matcher_or() {
    assertThat(CodePointMatcher.is('a').orRange('a', 'b').test('a')).isTrue();
    assertThat(CodePointMatcher.is('a').or(CodePointMatcher.range('a', 'b')).test('b')).isTrue();
    assertThat(CodePointMatcher.is('a').orRange('a', 'b').test('c')).isFalse();
    assertThat(CodePointMatcher.is('a').or(CodePointMatcher.NONE).test('a')).isTrue();
    assertThat(CodePointMatcher.is('a').or(CodePointMatcher.ASCII).toString()).isEqualTo("'a' | ASCII");
  }

  @Test public void matcher_negate() {
    assertThat(CodePointMatcher.is('a').negate().test('a')).isFalse();
    assertThat(CodePointMatcher.is('a').negate().test('b')).isTrue();
    assertThat(CodePointMatcher.is('a').negate().toString()).isEqualTo("not ('a')");
  }

  @Test public void matcher_trim() {
    assertThat(CodePointMatcher.of(Character::isWhitespace).trim("  hello ")).isEqualTo("hello");
    assertThat(CodePointMatcher.of(Character::isSupplementaryCodePoint).trim("\uD801\uDC00a\uD801\uDC00bc\uD801\uDC00")).isEqualTo("a\uD801\uDC00bc");

  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(CodePointMatcher.class);
    new NullPointerTester().testAllPublicInstanceMethods(CodePointMatcher.ASCII);
  }
}

package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.CaseFormat;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public final class CaseFormatsTest {
  @Test public void testToCase_lowerCamelCase() {
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "")).isEmpty();
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "FOO")).isEqualTo("foo");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "foo_bar"))
        .isEqualTo("fooBar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "fooBar"))
        .isEqualTo("fooBar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "FooBar"))
        .isEqualTo("fooBar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "FooBAR"))
        .isEqualTo("fooBar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "Foo-Bar"))
        .isEqualTo("fooBar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_CAMEL, "this-Is-A-MixedCase"))
        .isEqualTo("thisIsAMixedCase");
  }

  @Test public void testToCase_upperCamelCase() {
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "")).isEmpty();
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "foo")).isEqualTo("Foo");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "foo_bar"))
        .isEqualTo("FooBar");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "fooBar"))
        .isEqualTo("FooBar");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "FooBar"))
        .isEqualTo("FooBar");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "FooBAR"))
        .isEqualTo("FooBar");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "Foo-Bar"))
        .isEqualTo("FooBar");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_CAMEL, "this-Is-A-MixedCase"))
        .isEqualTo("ThisIsAMixedCase");
  }

  @Test public void testToCase_lowerUnderscore() {
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "")).isEmpty();
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "FOO"))
        .isEqualTo("foo");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "IPv4"))
        .isEqualTo("ipv4");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "userID"))
        .isEqualTo("user_id");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "foo_bar"))
        .isEqualTo("foo_bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "fooBar"))
        .isEqualTo("foo_bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "FooBar"))
        .isEqualTo("foo_bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "FooBAR"))
        .isEqualTo("foo_bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "Foo-Bar"))
        .isEqualTo("foo_bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "this-Is-A_MixedCase"))
        .isEqualTo("this_is_a_mixed_case");
  }

  @Test public void testToCase_upperUnderscore() {
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "")).isEmpty();
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "foo"))
        .isEqualTo("FOO");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "foo_bar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "fooBar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "FooBar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "FooBAR"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "Foo-Bar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseFormats.toCase(CaseFormat.UPPER_UNDERSCORE, "this-Is-A_MixedCase"))
        .isEqualTo("THIS_IS_A_MIXED_CASE");
  }

  @Test public void testToCase_lowerDashCase() {
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "")).isEmpty();
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "foo")).isEqualTo("foo");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "foo_bar"))
        .isEqualTo("foo-bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "fooBar"))
        .isEqualTo("foo-bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "FooBar"))
        .isEqualTo("foo-bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "FooBAR"))
        .isEqualTo("foo-bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "Foo-Bar"))
        .isEqualTo("foo-bar");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "this-Is-A_MixedCase"))
        .isEqualTo("this-is-a-mixed-case");
  }

  @Test public void testToCase_inputIsMultipleWords() {
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_HYPHEN, "call CaseBreaker.toCase()"))
        .isEqualTo("call case-breaker.to-case()");
    assertThat(CaseFormats.toCase(CaseFormat.LOWER_UNDERSCORE, "调用：CASE_BREAKER.toCase()"))
        .isEqualTo("调用：case_breaker.to_case()");
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(CaseFormats.class);
    new NullPointerTester().testAllPublicInstanceMethods(new CaseFormats());
  }
}

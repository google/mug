package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;

@RunWith(JUnit4.class)
public final class CaseBreakerTest {

  @Test public void testBreakCase_camelCase() {
    assertThat(new CaseBreaker().breakCase("x")).containsExactly("x");
    assertThat(new CaseBreaker().breakCase("A")).containsExactly("A");
    assertThat(new CaseBreaker().breakCase("HelloWorld")).containsExactly("Hello", "World");
    assertThat(new CaseBreaker().breakCase("helloWorld")).containsExactly("hello", "World");
    assertThat(new CaseBreaker().breakCase("2sigmaOffice"))
        .containsExactly("2sigma", "Office")
        .inOrder();
  }

  @Test public void testBreakCase_camelCaseInGreek() {
    assertThat(new CaseBreaker().breakCase("αβΑβΤττ")).containsExactly("αβ", "Αβ", "Τττ").inOrder();
  }

  @Test public void testBreakCase_upperCamelCase() {
    assertThat(new CaseBreaker().breakCase("IP Address"))
        .containsExactly("IP", "Address")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("A B CorABC"))
        .containsExactly("A", "B", "Cor", "ABC")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("AB")).containsExactly("AB").inOrder();
    assertThat(new CaseBreaker().breakCase("IPv6OrIPV4"))
        .containsExactly("IPv6", "Or", "IPV4")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("SplitURLsByCase"))
        .containsExactly("Split", "URLs", "By", "Case")
        .inOrder();
  }

  @Test public void testBreakCase_allCaps() {
    assertThat(new CaseBreaker().breakCase("ID")).containsExactly("ID");
    assertThat(new CaseBreaker().breakCase("orderID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("2_WORD2WORD3"))
        .containsExactly("2", "WORD2", "WORD3")
        .inOrder();
  }

  @Test public void testBreakCase_snakeCase() {
    assertThat(new CaseBreaker().breakCase("order_id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order_ID")).containsExactly("order", "ID");
  }

  @Test public void testBreakCase_upperSnakeCase() {
    assertThat(new CaseBreaker().breakCase("SPLIT_ASCII_BY_CASE"))
        .containsExactly("SPLIT", "ASCII", "BY", "CASE")
        .inOrder();
  }

  @Test public void testBreakCase_dashCase() {
    assertThat(new CaseBreaker().breakCase("order-id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order-ID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("ORDER-ID")).containsExactly("ORDER", "ID");
  }

  @Test public void testBreakCase_mixedCase() {
    assertThat(new CaseBreaker().breakCase(" a0--b1+c34DEF56 78"))
        .containsExactly("a0", "b1", "c34", "DEF56", "78")
        .inOrder();
  }

  @Test public void testBreakCase_separateWords() {
    assertThat(new CaseBreaker().breakCase("order id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order ID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("3 separate words."))
        .containsExactly("3", "separate", "words");
    assertThat(new CaseBreaker().breakCase("HTTP or FTP")).containsExactly("HTTP", "or", "FTP");
    assertThat(new CaseBreaker().breakCase("1 + 2 == 3")).containsExactly("1", "2", "3");
  }

  @Test public void testBreakCase_noMatch() {
    assertThat(new CaseBreaker().breakCase("")).isEmpty();
    assertThat(new CaseBreaker().breakCase("?")).isEmpty();
    assertThat(new CaseBreaker().breakCase("_")).isEmpty();
    assertThat(new CaseBreaker().breakCase(" ")).isEmpty();
    assertThat(new CaseBreaker().breakCase("@.")).isEmpty();
  }

  @Test public void testBreakCase_i18n() {
    assertThat(new CaseBreaker().breakCase("中文")).containsExactly("中文");
    assertThat(new CaseBreaker().breakCase("中。文")).containsExactly("中。文");
    assertThat(new CaseBreaker().breakCase("中 文")).containsExactly("中", "文");
    assertThat(new CaseBreaker().breakCase("chineseAsIn中文-or japanese"))
        .containsExactly("chinese", "As", "In", "中文", "or", "japanese")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("٩realms")).containsExactly("٩realms");
  }

  @Test public void testBreakCase_customCaseDelimiter() {
    assertThat(new CaseBreaker().withCaseDelimiterChars(CharMatcher.is('，')).breakCase("中，文"))
        .containsExactly("中", "文");
    assertThat(new CaseBreaker().withCaseDelimiterChars(CharMatcher.is('–')).breakCase("100–200"))
        .containsExactly("100", "200");
  }

  @Test public void testBreakCase_customCamelCase() {
    assertThat(
            new CaseBreaker()
                .withLowerCaseChars(CharMatcher.inRange('a', 'z'))
                .breakCase("aBβaΑβb"))
        .containsExactly("a", "Bβa", "Αβb");
  }

  @Test public void testBreakCase_emoji() {
    assertThat(new CaseBreaker().breakCase("🅗ⓞⓜⓔ🅁ⓤⓝ")).containsExactly("🅗ⓞⓜⓔ", "🅁ⓤⓝ").inOrder();
    assertThat(new CaseBreaker().breakCase("ⓖⓞ🄷ⓞⓜⓔ")).containsExactly("ⓖⓞ", "🄷ⓞⓜⓔ").inOrder();
    assertThat(new CaseBreaker().breakCase("🅣ⓗⓔ🅤🅡🅛ⓢ"))
        .containsExactly("🅣ⓗⓔ", "🅤🅡🅛ⓢ")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("中😀文")).containsExactly("中😀文");
    assertThat(new CaseBreaker().breakCase("ab😀CD")).containsExactly("ab", "😀CD").inOrder();
    assertThat(new CaseBreaker().breakCase("ab😀🤣cd")).containsExactly("ab", "😀🤣cd").inOrder();
    assertThat(new CaseBreaker().breakCase("ab😀c🤣d"))
        .containsExactly("ab", "😀c", "🤣d")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("¹/₂")).containsExactly("¹", "₂").inOrder();
    assertThat(new CaseBreaker().breakCase("۱𑛃")).containsExactly("۱𑛃").inOrder();
    assertThat(new CaseBreaker().breakCase("🔟💯")).containsExactly("🔟💯").inOrder();
  }

  @Test public void testConvertAsciiToLowerCamelCase() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "")).isEmpty();
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "FOO")).isEqualTo("foo");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "foo_bar"))
        .isEqualTo("fooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "fooBar"))
        .isEqualTo("fooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "FooBar"))
        .isEqualTo("fooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "FooBAR"))
        .isEqualTo("fooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "Foo-Bar"))
        .isEqualTo("fooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_CAMEL, "this-Is-A-MixedCase"))
        .isEqualTo("thisIsAMixedCase");
  }

  @Test public void testConvertAsciiToUpperCamelCase() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "")).isEmpty();
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "foo")).isEqualTo("Foo");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "foo_bar"))
        .isEqualTo("FooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "fooBar"))
        .isEqualTo("FooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "FooBar"))
        .isEqualTo("FooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "FooBAR"))
        .isEqualTo("FooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "Foo-Bar"))
        .isEqualTo("FooBar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_CAMEL, "this-Is-A-MixedCase"))
        .isEqualTo("ThisIsAMixedCase");
  }

  @Test public void testConvertAsciiToLowerUnderscore() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "")).isEmpty();
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "FOO"))
        .isEqualTo("foo");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "IPv4"))
        .isEqualTo("ipv4");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "userID"))
        .isEqualTo("user_id");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "foo_bar"))
        .isEqualTo("foo_bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "fooBar"))
        .isEqualTo("foo_bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "FooBar"))
        .isEqualTo("foo_bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "FooBAR"))
        .isEqualTo("foo_bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "Foo-Bar"))
        .isEqualTo("foo_bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_UNDERSCORE, "this-Is-A_MixedCase"))
        .isEqualTo("this_is_a_mixed_case");
  }

  @Test public void testConvertAsciiToUpperUnderscore() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "")).isEmpty();
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "foo"))
        .isEqualTo("FOO");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "foo_bar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "fooBar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "FooBar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "FooBAR"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "Foo-Bar"))
        .isEqualTo("FOO_BAR");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.UPPER_UNDERSCORE, "this-Is-A_MixedCase"))
        .isEqualTo("THIS_IS_A_MIXED_CASE");
  }

  @Test public void testConvertAsciiToLowerDashCase() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "")).isEmpty();
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "foo")).isEqualTo("foo");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "foo_bar"))
        .isEqualTo("foo-bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "fooBar"))
        .isEqualTo("foo-bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "FooBar"))
        .isEqualTo("foo-bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "FooBAR"))
        .isEqualTo("foo-bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "Foo-Bar"))
        .isEqualTo("foo-bar");
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "this-Is-A_MixedCase"))
        .isEqualTo("this-is-a-mixed-case");
  }

  @Test public void testConvertAsciiTo_inputIsMultipleWords() {
    assertThat(CaseBreaker.convertAsciiTo(CaseFormat.LOWER_HYPHEN, "call CaseBreaker.convertAsciiTo()"))
        .isEqualTo("call case-breaker.convert-ascii-to()");
  }
}

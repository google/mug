package com.google.mu.util;

import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class CaseBreakerTest {
  @Test
  public void breakCase_camelCase() {
    assertThat(new CaseBreaker().breakCase("x")).containsExactly("x");
    assertThat(new CaseBreaker().breakCase("A")).containsExactly("A");
    assertThat(new CaseBreaker().breakCase("HelloWorld")).containsExactly("Hello", "World");
    assertThat(new CaseBreaker().breakCase("helloWorld")).containsExactly("hello", "World");
    assertThat(new CaseBreaker().breakCase("2sigmaOffice"))
        .containsExactly("2sigma", "Office")
        .inOrder();
  }

  @Test
  public void breakCase_camelCaseInGreek() {
    assertThat(new CaseBreaker().breakCase("αβΑβΤττ")).containsExactly("αβ", "Αβ", "Τττ").inOrder();
  }

  @Test
  public void breakCase_upperCamelCase() {
    assertThat(new CaseBreaker().breakCase("IP Address")).containsExactly("IP", "Address").inOrder();
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

  @Test
  public void breakCase_allCaps() {
    assertThat(new CaseBreaker().breakCase("ID")).containsExactly("ID");
    assertThat(new CaseBreaker().breakCase("orderID")).containsExactly("order", "ID");
  }

  @Test
  public void breakCase_snakeCase() {
    assertThat(new CaseBreaker().breakCase("order_id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order_ID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("SPLIT_ASCII_BY_CASE"))
        .containsExactly("SPLIT", "ASCII", "BY", "CASE")
        .inOrder();
  }

  @Test
  public void breakCase_dashCase() {
    assertThat(new CaseBreaker().breakCase("order-id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order-ID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("ORDER-ID")).containsExactly("ORDER", "ID");
  }

  @Test
  public void breakCase_mixedCase() {
    assertThat(new CaseBreaker().breakCase(" a0--b1+c34DEF56 78"))
        .containsExactly("a0", "b1", "c34", "DEF56", "78")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("2_WORD2WORD3"))
        .containsExactly("2", "WORD2", "WORD3")
        .inOrder();
  }

  @Test
  public void breakCase_separateWords() {
    assertThat(new CaseBreaker().breakCase("order id")).containsExactly("order", "id");
    assertThat(new CaseBreaker().breakCase("order ID")).containsExactly("order", "ID");
    assertThat(new CaseBreaker().breakCase("3 separate words."))
        .containsExactly("3", "separate", "words");
    assertThat(new CaseBreaker().breakCase("1 + 2 == 3")).containsExactly("1", "2", "3");
    assertThat(new CaseBreaker().breakCase("HTTP or FTP"))
        .containsExactly("HTTP", "or", "FTP");
  }

  @Test
  public void breakCase_noMatch() {
    assertThat(new CaseBreaker().breakCase("")).isEmpty();
    assertThat(new CaseBreaker().breakCase("?")).isEmpty();
    assertThat(new CaseBreaker().breakCase("_")).isEmpty();
    assertThat(new CaseBreaker().breakCase(" ")).isEmpty();
    assertThat(new CaseBreaker().breakCase("@.")).isEmpty();
  }

  @Test
  public void breakCase_i18n() {
    assertThat(new CaseBreaker().breakCase("中文")).containsExactly("中文");
    assertThat(new CaseBreaker().breakCase("中 文")).containsExactly("中", "文");
    assertThat(new CaseBreaker().breakCase("中。文")).containsExactly("中。文");
    assertThat(new CaseBreaker().breakCase("中，文")).containsExactly("中，文");
    assertThat(new CaseBreaker().breakCase("chineseAsIn中文-or japanese"))
        .containsExactly("chinese", "As", "In", "中文", "or", "japanese")
        .inOrder();
    assertThat(new CaseBreaker().breakCase("٩realms")).containsExactly("٩realms");
  }

  @Test
  public void breakCase_emoji() {
    assertThat(new CaseBreaker().breakCase("🅗ⓞⓜⓔ🅁ⓤⓝ")).containsExactly("🅗ⓞⓜⓔ", "🅁ⓤⓝ").inOrder();
    assertThat(new CaseBreaker().breakCase("ⓖⓞ🄷ⓞⓜⓔ")).containsExactly("ⓖⓞ", "🄷ⓞⓜⓔ").inOrder();
    assertThat(new CaseBreaker().breakCase("🅣ⓗⓔ🅤🅡🅛ⓢ")).containsExactly("🅣ⓗⓔ", "🅤🅡🅛ⓢ").inOrder();
    assertThat(new CaseBreaker().breakCase("中😀文")).containsExactly("中😀文");
    assertThat(new CaseBreaker().breakCase("ab😀CD")).containsExactly("ab", "😀CD").inOrder();
    assertThat(new CaseBreaker().breakCase("ab😀🤣cd")).containsExactly("ab", "😀🤣cd").inOrder();
    assertThat(new CaseBreaker().breakCase("ab😀c🤣d")).containsExactly("ab", "😀c", "🤣d").inOrder();
    assertThat(new CaseBreaker().breakCase("¹/₂")).containsExactly("¹", "₂").inOrder();
    assertThat(new CaseBreaker().breakCase("۱𑛃")).containsExactly("۱𑛃").inOrder();
    assertThat(new CaseBreaker().breakCase("🔟💯")).containsExactly("🔟💯").inOrder();
  }

  @Test
  public void breakCase_supplementaryChars() {
    assertThat(new CaseBreaker().breakCase("🔟\uD801\uDC00💯")).containsExactly("🔟\uD801\uDC00💯").inOrder();
  }

  @Test
  public void breakCase_customCamelCase() {
    assertThat(
            new CaseBreaker()
                .withLowerCaseChars(CodePointMatcher.range('a', 'z'))
                .breakCase("aBβaΑβb"))
        .containsExactly("a", "Bβa", "Αβb");
  }

  @Test
  public void breakCase_customCaseDelimiter() {
    assertThat(new CaseBreaker().withCaseDelimiterChars(CodePointMatcher.is('，')).breakCase("中，文"))
        .containsExactly("中", "文");
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicInstanceMethods(new CaseBreaker());
  }
}

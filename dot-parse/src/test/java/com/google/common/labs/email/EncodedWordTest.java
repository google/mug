package com.google.common.labs.email;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EncodedWordTest {

  @Test
  public void testDecode_quotedPrintable_utf8() {
    assertThat(EncodedWord.decode("=?UTF-8?Q?John_Doe?=")).isEqualTo("John Doe");
  }

  @Test
  public void testDecode_quotedPrintable_hexEscapes() {
    assertThat(EncodedWord.decode("=?ISO-8859-1?q?Ren=E9?=")).isEqualTo("René");
  }

  @Test
  public void testDecode_base64() {
    assertThat(EncodedWord.decode("=?UTF-8?B?Sm9obiBEb2U=?=")).isEqualTo("John Doe");
  }

  @Test
  public void testDecode_emptyText() {
    assertThat(EncodedWord.decode("=?UTF-8?Q??=")).isEqualTo("");
  }

  @Test
  public void testDecode_invalidEncodedWord_fallback() {
    assertThat(EncodedWord.decode("=??Q?text?=")).isEqualTo("=??Q?text?=");
    assertThat(EncodedWord.decode("=?utf-8??text?=")).isEqualTo("=?utf-8??text?=");
    assertThat(EncodedWord.decode("=?utf-8?X?text?=")).isEqualTo("=?utf-8?X?text?=");
    assertThat(EncodedWord.decode("=?utf-8?Q?text")).isEqualTo("=?utf-8?Q?text");
  }

  @Test
  public void testDecode_noEncodedWords() {
    assertThat(EncodedWord.decode("Hello World")).isEqualTo("Hello World");
    assertThat(EncodedWord.decode("")).isEqualTo("");
  }

  @Test
  public void testDecode_oneEncodedWord() {
    assertThat(EncodedWord.decode("=?UTF-8?Q?John_Doe?=")).isEqualTo("John Doe");
    assertThat(EncodedWord.decode("Hello =?UTF-8?Q?John_Doe?=")).isEqualTo("Hello John Doe");
    assertThat(EncodedWord.decode("=?UTF-8?Q?John_Doe?= World")).isEqualTo("John Doe World");
  }

  @Test
  public void testDecode_multipleEncodedWords_spaceRule() {
    // Spaces between adjacent encoded words are discarded
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?=  =?UTF-8?Q?Doe?=")).isEqualTo("JohnDoe");
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?= \r\n \t =?UTF-8?Q?Doe?=")).isEqualTo("JohnDoe");

    // Text between encoded words is preserved
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?= and =?UTF-8?Q?Doe?=")).isEqualTo("John and Doe");
  }

  @Test
  public void testDecode_malformedEncodedWords_fallback() {
    // Malformed encoded words are treated as plain text
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?=  =?UTF-8?Q?bad  =?UTF-8?Q?Doe?="))
        .isEqualTo("John  =?UTF-8?Q?bad  Doe");
  }

  @Test
  public void testDecode_decodingFailureInWord_fallback() {
    // If decoding fails, the word falls back to its raw form, but spacing rules still apply
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?=  =?INVALID?Q?bad?=  =?UTF-8?Q?Doe?="))
        .isEqualTo("John  =?INVALID?Q?bad?=  Doe");

    assertThat(EncodedWord.decode("=?UTF-8?Q?John?=  =?UTF-8?B?invalid_base64?=  =?UTF-8?Q?Doe?="))
        .isEqualTo("John=?UTF-8?B?invalid_base64?=Doe");
  }

  @Test
  public void testDecode_whitespaceEdgeCases() {
    // Spaces at beginning
    assertThat(EncodedWord.decode("  =?UTF-8?Q?John?=")).isEqualTo("  John");
    assertThat(EncodedWord.decode("  Hello")).isEqualTo("  Hello");

    // Spaces at end
    assertThat(EncodedWord.decode("=?UTF-8?Q?John?=  ")).isEqualTo("John  ");
    assertThat(EncodedWord.decode("Hello  ")).isEqualTo("Hello  ");

    // Spaces at both ends
    assertThat(EncodedWord.decode("  =?UTF-8?Q?John?=  ")).isEqualTo("  John  ");
    assertThat(EncodedWord.decode("  Hello  ")).isEqualTo("  Hello  ");
  }
}

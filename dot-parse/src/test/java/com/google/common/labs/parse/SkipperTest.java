package com.google.common.labs.parse;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.range;
import static org.junit.Assert.assertThrows;

import com.google.mu.util.CharPredicate;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SkipperTest {

  private static final CharPredicate LOW_64 = range('0', '9');
  private static final CharPredicate HIGH_64 = range('a', 'z');
  private static final CharPredicate BITMASK_128 = range('a', 'z').or(range('0', '9')).or(is('_'));
  private static final CharPredicate NON_ASCII = is('\u00E9').or('\u00E8');
  private static final CharPredicate MIXED = range('a', 'z').or(is('\u00E9'));

  @Test public void from_nullPredicate_throws() {
    assertThrows(NullPointerException.class, () -> Skipper.from(null));
  }

  // --- Low 64-bit Tests (< 64) ---

  @Test public void low64_empty_fromString() {
    CharInput input = CharInput.from("");
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void low64_empty_fromReader() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void low64_noMatch_fromString() {
    CharInput input = CharInput.from("abc");
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void low64_noMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void low64_partialMatch_fromString() {
    CharInput input = CharInput.from("12345abc");
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(5);
  }

  @Test public void low64_partialMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("12345abc"));
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(5);
  }

  @Test public void low64_allMatch_fromString() {
    CharInput input = CharInput.from("12345678");
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void low64_allMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("12345678"));
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void low64_longRun_fromString() {
    String digits = "0123456789".repeat(20);
    CharInput input = CharInput.from(digits + "xyz");
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(200);
  }

  @Test public void low64_longRun_fromReader() {
    String digits = "0123456789".repeat(20);
    CharInput input = CharInput.from(new StringReader(digits + "xyz"));
    assertThat(Skipper.from(LOW_64).skip(input, 0)).isEqualTo(200);
  }

  @Test public void low64_fromOffset_fromString() {
    CharInput input = CharInput.from("xx12345yy");
    assertThat(Skipper.from(LOW_64).skip(input, 2)).isEqualTo(7);
  }

  @Test public void low64_fromOffset_fromReader() {
    CharInput input = CharInput.from(new StringReader("xx12345yy"));
    assertThat(Skipper.from(LOW_64).skip(input, 2)).isEqualTo(7);
  }

  // --- High 64-bit Tests (64..127) ---

  @Test public void high64_empty_fromString() {
    CharInput input = CharInput.from("");
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void high64_empty_fromReader() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void high64_noMatch_fromString() {
    CharInput input = CharInput.from("123");
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void high64_noMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("123"));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(0);
  }

  @Test public void high64_partialMatch_fromString() {
    CharInput input = CharInput.from("abcdef123");
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(6);
  }

  @Test public void high64_partialMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("abcdef123"));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(6);
  }

  @Test public void high64_allMatch_fromString() {
    CharInput input = CharInput.from("abcdefgh");
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void high64_allMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("abcdefgh"));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void high64_longRun_fromString() {
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    CharInput input = CharInput.from(letters + "123");
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(260);
  }

  @Test public void high64_longRun_fromReader() {
    String letters = "abcdefghijklmnopqrstuvwxyz".repeat(10);
    CharInput input = CharInput.from(new StringReader(letters + "123"));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(260);
  }

  @Test public void high64_fromOffset_fromString() {
    CharInput input = CharInput.from("12abcdef34");
    assertThat(Skipper.from(HIGH_64).skip(input, 2)).isEqualTo(8);
  }

  @Test public void high64_fromOffset_fromReader() {
    CharInput input = CharInput.from(new StringReader("12abcdef34"));
    assertThat(Skipper.from(HIGH_64).skip(input, 2)).isEqualTo(8);
  }

  // --- 128-bit Tests (Low + High 64-bit) ---

  @Test public void bitmask128_empty_fromString() {
    CharInput input = CharInput.from("");
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(0);
  }

  @Test public void bitmask128_empty_fromReader() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(0);
  }

  @Test public void bitmask128_noMatch_fromString() {
    CharInput input = CharInput.from("!@#$%");
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(0);
  }

  @Test public void bitmask128_noMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("!@#$%"));
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(0);
  }

  @Test public void bitmask128_partialMatch_fromString() {
    CharInput input = CharInput.from("a0_b1_c2_d3!@#");
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(11);
  }

  @Test public void bitmask128_partialMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("a0_b1_c2_d3!@#"));
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(11);
  }

  @Test public void bitmask128_allMatch_fromString() {
    CharInput input = CharInput.from("a0_b1_c2_d3_e4_f5");
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(17);
  }

  @Test public void bitmask128_allMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("a0_b1_c2_d3_e4_f5"));
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(17);
  }

  @Test public void bitmask128_longRun_fromString() {
    String words = "a0_b1_c2_d3_e4_".repeat(20);
    CharInput input = CharInput.from(words + "   ");
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(300);
  }

  @Test public void bitmask128_longRun_fromReader() {
    String words = "a0_b1_c2_d3_e4_".repeat(20);
    CharInput input = CharInput.from(new StringReader(words + "   "));
    assertThat(Skipper.from(BITMASK_128).skip(input, 0)).isEqualTo(300);
  }

  @Test public void bitmask128_fromOffset_fromString() {
    CharInput input = CharInput.from("   a0_b1_c2   ");
    assertThat(Skipper.from(BITMASK_128).skip(input, 3)).isEqualTo(11);
  }

  @Test public void bitmask128_fromOffset_fromReader() {
    CharInput input = CharInput.from(new StringReader("   a0_b1_c2   "));
    assertThat(Skipper.from(BITMASK_128).skip(input, 3)).isEqualTo(11);
  }

  // --- Non-ASCII Tests (>= 128) ---

  @Test public void nonAscii_empty_fromString() {
    CharInput input = CharInput.from("");
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(0);
  }

  @Test public void nonAscii_empty_fromReader() {
    CharInput input = CharInput.from(new StringReader(""));
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(0);
  }

  @Test public void nonAscii_noMatch_fromString() {
    CharInput input = CharInput.from("abc");
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(0);
  }

  @Test public void nonAscii_noMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("abc"));
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(0);
  }

  @Test public void nonAscii_partialMatch_fromString() {
    CharInput input = CharInput.from("\u00E9\u00E8\u00E9\u00E8abc");
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(4);
  }

  @Test public void nonAscii_partialMatch_fromReader() {
    CharInput input = CharInput.from(new StringReader("\u00E9\u00E8\u00E9\u00E8abc"));
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(4);
  }

  @Test public void nonAscii_allMatch_fromString() {
    CharInput input = CharInput.from("\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8");
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(8);
  }

  @Test public void nonAscii_allMatch_fromReader() {
    CharInput input =
        CharInput.from(new StringReader("\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8\u00E9\u00E8"));
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(8);
  }

  @Test public void nonAscii_longRun_fromString() {
    String unicodeRun = "\u00E9\u00E8".repeat(50);
    CharInput input = CharInput.from(unicodeRun + "end");
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(100);
  }

  @Test public void nonAscii_longRun_fromReader() {
    String unicodeRun = "\u00E9\u00E8".repeat(50);
    CharInput input = CharInput.from(new StringReader(unicodeRun + "end"));
    assertThat(Skipper.from(NON_ASCII).skip(input, 0)).isEqualTo(100);
  }

  @Test public void nonAscii_fromOffset_fromString() {
    CharInput input = CharInput.from("xx\u00E9\u00E8\u00E9\u00E8yy");
    assertThat(Skipper.from(NON_ASCII).skip(input, 2)).isEqualTo(6);
  }

  @Test public void nonAscii_fromOffset_fromReader() {
    CharInput input = CharInput.from(new StringReader("xx\u00E9\u00E8\u00E9\u00E8yy"));
    assertThat(Skipper.from(NON_ASCII).skip(input, 2)).isEqualTo(6);
  }

  // --- Mixed ASCII and Non-ASCII Tests ---

  @Test public void mixedAsciiAndNonAscii_fromString() {
    CharInput input = CharInput.from("abc\u00E9def\u00E9123");
    assertThat(Skipper.from(MIXED).skip(input, 0)).isEqualTo(8);
  }

  @Test public void mixedAsciiAndNonAscii_fromReader() {
    CharInput input = CharInput.from(new StringReader("abc\u00E9def\u00E9123"));
    assertThat(Skipper.from(MIXED).skip(input, 0)).isEqualTo(8);
  }

  // --- Reader Buffer Boundary & Compaction Tests ---

  @Test public void reader_bufferRefillAcrossChunks() {
    String longPayload = "a".repeat(10000) + "1";
    CharInput input = CharInput.from(new StringReader(longPayload));
    assertThat(Skipper.from(HIGH_64).skip(input, 0)).isEqualTo(10000);
  }

  @Test public void reader_afterCompaction() {
    CharInput input = CharInput.from(new StringReader("xxxxaaaaaa1"), 10, 4);
    input.charAt(4); // Advance read
    input.markCheckpoint(4); // Compact "xxxx"
    assertThat(Skipper.from(HIGH_64).skip(input, 4)).isEqualTo(10);
  }

  // --- forLower64Ascii Specific Tests ---

  @Test public void forLower64Ascii_nullPredicate_throws() {
    assertThrows(NullPointerException.class, () -> Skipper.forLower64Ascii(null));
  }

  @Test public void forLower64Ascii_low64_matches() {
    CharInput input = CharInput.from("01234567abc");
    assertThat(Skipper.forLower64Ascii(LOW_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void forLower64Ascii_high64_matchesViaFallback() {
    CharInput input = CharInput.from("abcdefgh123");
    assertThat(Skipper.forLower64Ascii(HIGH_64).skip(input, 0)).isEqualTo(8);
  }

  @Test public void forLower64Ascii_mixed_matches() {
    CharInput input = CharInput.from("01ab01ab!@#");
    assertThat(Skipper.forLower64Ascii(range('0', '9').or(range('a', 'z'))).skip(input, 0))
        .isEqualTo(8);
  }
}

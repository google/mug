package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Range;
import com.google.common.labs.parse.Parser.ParseException;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ParsersTest {

  @Test public void duration_oneSecond() {
    assertThat(Parsers.DURATION.parse("1s")).isEqualTo(Duration.ofSeconds(1));
  }

  @Test public void duration_twoHours() {
    assertThat(Parsers.DURATION.parse("2h")).isEqualTo(Duration.ofHours(2));
  }

  @Test public void duration_fiveDays() {
    assertThat(Parsers.DURATION.parse("5d")).isEqualTo(Duration.ofDays(5));
  }

  @Test public void duration_oneWeek() {
    assertThat(Parsers.DURATION.parse("1w")).isEqualTo(Duration.ofDays(7));
  }

  @Test public void duration_fifteenMillis() {
    assertThat(Parsers.DURATION.parse("15ms")).isEqualTo(Duration.ofMillis(15));
  }

  @Test public void duration_microseconds() {
    assertThat(Parsers.DURATION.parse("500us")).isEqualTo(Duration.ofNanos(500_000));
  }

  @Test public void duration_nanoseconds() {
    assertThat(Parsers.DURATION.parse("100ns")).isEqualTo(Duration.ofNanos(100));
  }

  @Test public void duration_zeroSeconds() {
    assertThat(Parsers.DURATION.parse("0s")).isEqualTo(Duration.ZERO);
  }

  @Test public void duration_zeroDays() {
    assertThat(Parsers.DURATION.parse("0d")).isEqualTo(Duration.ZERO);
  }

  @Test public void duration_combined() {
    assertThat(Parsers.DURATION.parse("2d3h"))
        .isEqualTo(Duration.ofDays(2).plus(Duration.ofHours(3)));
  }

  @Test public void duration_allUnitsCombined() {
    Duration expected = Duration.ofDays(7)
        .plus(Duration.ofDays(2))
        .plus(Duration.ofHours(3))
        .plus(Duration.ofMinutes(4))
        .plus(Duration.ofSeconds(5))
        .plus(Duration.ofMillis(6))
        .plus(Duration.ofNanos(7_000))
        .plus(Duration.ofNanos(8));
    assertThat(Parsers.DURATION.parse("1w2d3h4m5s6ms7us8ns")).isEqualTo(expected);
  }

  @Test public void duration_emptyStringThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse(""));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void duration_whitespaceThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1s 2m"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <EOF>, encountered:\s
                1s 2m
                  ^
            """);
  }

  @Test public void duration_decimalThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1.5s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting <w>, encountered:\s
                1.5s
                 ^
            """);
  }

  @Test public void duration_negativeThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("-1s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                -1s
                ^
            """);
  }

  @Test public void duration_lettersOnlyThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("foo"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                foo
                ^
            """);
  }

  @Test public void duration_invalidUnitThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1ss"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: unexpected `duration unit char`:\s
                1ss
                  ^
            """);
  }

  @Test public void duration_overflowLongThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("999999999999999999999999999s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("at 1:1: For input string: \"999999999999999999999999999\"");
  }

  @Test public void duration_overflowDurationThrows() {
    ParseException e =
        assertThrows(ParseException.class, () -> Parsers.DURATION.parse("3w9223372036854775807d100s"));
    assertThat(e).hasMessageThat().isEqualTo("at 1:3: duration out of range: 9223372036854775807d");
  }

  @Test public void duration_overflowAccumulationThrows() {
    ParseException e =
        assertThrows(ParseException.class, () -> Parsers.DURATION.parse("9223372036854775800s10s"));
    assertThat(e).hasMessageThat().isEqualTo("at 1:1: duration out of range");
  }

  @Test public void bmpCodeUnit_validHexUpper() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("D83D")).isEqualTo(0xD83D);
  }

  @Test public void bmpCodeUnit_validHexLower() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("d83d")).isEqualTo(0xD83D);
  }

  @Test public void bmpCodeUnit_zero() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("0000")).isEqualTo(0);
  }

  @Test public void bmpCodeUnit_max() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("FFFF")).isEqualTo(65535);
  }

  @Test public void bmpCodeUnit_tooShortThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.BMP_CODE_UNIT.parse("FFF"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:\s
                FFF
                ^
            """);
  }

  @Test public void bmpCodeUnit_tooLongThrows() {
    ParseException e =
        assertThrows(ParseException.class, () -> Parsers.BMP_CODE_UNIT.parse("FFFFF"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <EOF>, encountered:\s
                FFFFF
                    ^
            """);
  }

  @Test public void bmpCodeUnit_nonHexThrows() {
    ParseException e =
        assertThrows(ParseException.class, () -> Parsers.BMP_CODE_UNIT.parse("FGHI"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:\s
                FGHI
                ^
            """);
  }

  @Test public void bmpCodeUnit_emptyStringThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.BMP_CODE_UNIT.parse(""));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void unsignedDecimal_matchesZero() {
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("0")).isEqualTo("0");
  }

  @Test public void unsignedDecimal_matchesValidIntegers() {
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("1")).isEqualTo("1");
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("123")).isEqualTo("123");
  }

  @Test public void unsignedDecimal_matchesValidFloats() {
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("0.0")).isEqualTo("0.0");
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("0.5")).isEqualTo("0.5");
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("1.23")).isEqualTo("1.23");
    assertThat(Parsers.UNSIGNED_DECIMAL.parse("0.007")).isEqualTo("0.007");
  }

  @Test public void unsignedDecimal_rejectsMinusSign() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("-1"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                -1
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsPlusSign() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("+1"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                +1
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsLeadingDot() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse(".5"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                .5
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsTrailingDot() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("123."));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <digits>, encountered:\s
                123.
                    ^
            """);
  }

  @Test public void unsignedDecimal_rejectsOnlyDot() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("."));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                .
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsRedundantLeadingZeroInteger() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("05"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <decimal point number>, encountered:\s
                05
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsRedundantLeadingZeroFloat() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("00.5"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <decimal point number>, encountered:\s
                00.5
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsMultipleDots() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("1.2.3"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <EOF>, encountered:\s
                1.2.3
                   ^
            """);
  }

  @Test public void unsignedDecimal_rejectsDotsInARow() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("1..2"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <digits>, encountered:\s
                1..2
                  ^
            """);
  }

  @Test public void unsignedDecimal_rejectsScientificNotation() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("1.2e3"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <EOF>, encountered:\s
                1.2e3
                   ^
            """);
  }

  @Test public void unsignedDecimal_rejectsAlphabetic() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse("a"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                a
                ^
            """);
  }

  @Test public void unsignedDecimal_rejectsEmptyString() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> Parsers.UNSIGNED_DECIMAL.parse(""));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <digits>, encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void unsignedDecimal_rangeParsingSuccess() {
    Parser<Range<String>> rangeParser =
        sequence(Parsers.UNSIGNED_DECIMAL.followedBy(".."), Parsers.UNSIGNED_DECIMAL, Range::closed)
            .between("[", "]");

    // This successfully parses because unsignedDecimal() is non-greedy on dot.
    assertThat(rangeParser.parse("[1.0..2.0]")).isEqualTo(Range.closed("1.0", "2.0"));
  }

  @Test public void unsignedDecimal_skippingWhitespace() {
    // Normal parsing without space succeeds
    assertThat(Parsers.UNSIGNED_DECIMAL.parseSkipping(Character::isWhitespace, "0.1"))
        .isEqualTo("0.1");

    // But parsing with internal spaces "0 . 1" fails because the optional decimal fraction
    // backtracks, matching "0" and then expecting EOF at the dot.
    ParseException thrown = assertThrows(
        ParseException.class,
        () -> Parsers.UNSIGNED_DECIMAL.parseSkipping(Character::isWhitespace, "0 . 1"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <EOF>, encountered:\s
                0 . 1
                  ^
            """);
  }
}

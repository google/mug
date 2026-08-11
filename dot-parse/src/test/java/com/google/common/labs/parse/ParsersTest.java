package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parsers.CODE_POINT;
import static com.google.common.labs.parse.Parsers.SIGNED_DOUBLE;
import static com.google.common.labs.parse.Parsers.UNSIGNED_INTEGER;
import static com.google.common.labs.parse.Parsers.regex;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Range;
import com.google.common.labs.parse.Parser.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ParsersTest {

  @Test public void regex_matchesSimplePattern() {
    Parser<String> p = regex("[a-z]+");
    assertThat(p.parse("abc")).isEqualTo("abc");
  }

  @Test public void regex_matchesComplexPattern() {
    Parser<String> p = regex("[0-9]{3}-[0-9]{3,4}");
    assertThat(p.parse("123-4567")).isEqualTo("123-4567");
  }

  @Test public void regex_canMatchPartially() {
    Parser<String> p = regex("[a-z]+").followedBy("!");
    assertThat(p.parse("abc!")).isEqualTo("abc");
  }

  @Test public void regex_emptyPattern_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex(""));
    assertThat(exception).hasMessageThat().isEqualTo("regex must not match empty string: ");
  }

  @Test public void regex_anchorStart_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("^a"));
    assertThat(exception).hasMessageThat().isEqualTo("anchors are not allowed in regex parser: ^");
  }

  @Test public void regex_anchorEnd_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a$"));
    assertThat(exception).hasMessageThat().isEqualTo("anchors are not allowed in regex parser: $");
  }

  @Test public void regex_anchorWordBoundary_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("\\ba"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("anchors are not allowed in regex parser: \\b");
  }

  @Test public void regex_lookaheadPositive_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a(?=b)"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("lookarounds are not allowed in regex parser: (?=b)");
  }

  @Test public void regex_lookaheadNegative_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a(?!b)"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("lookarounds are not allowed in regex parser: (?!b)");
  }

  @Test public void regex_backreferenceNumeric_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("(a)\\1"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("backreferences are not allowed in regex parser: \\1");
  }

  @Test public void regex_backreferenceNamed_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("(?<foo>a)\\k<foo>"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("backreferences are not allowed in regex parser: \\k<foo>");
  }

  @Test public void regex_quantifierZeroOrMore_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a*"));
    assertThat(exception).hasMessageThat().isEqualTo("regex must not match empty string: a*");
  }

  @Test public void regex_quantifierOptional_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a?"));
    assertThat(exception).hasMessageThat().isEqualTo("regex must not match empty string: a?");
  }

  @Test public void regex_quantifierGroupOptional_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("(foo)?"));
    assertThat(exception).hasMessageThat().isEqualTo("regex must not match empty string: (foo)?");
  }

  @Test public void regex_alternationWithEmptyAlternative_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("foo|"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting one of [one or more [^.[]{}()*+?^$|\\ #], whitespace or #, $, (, (?, (?!, (?<!, (?<=, (?=, ., [, [^, \\, \\A, \\B, \\D, \\P, \\S, \\W, \\Z, \\b, \\d, \\k<, \\p, \\s, \\w, \\z, ^], encountered:\s
                foo|
                    ^
            """);
  }

  @Test public void regex_caseInsensitiveFlag() {
    Parser<String> parser = regex("(?i:abc)");
    assertThat(parser.parse("abc")).isEqualTo("abc");
    assertThat(parser.parse("ABC")).isEqualTo("ABC");
    assertThat(parser.parse("aBc")).isEqualTo("aBc");
    assertThrows(ParseException.class, () -> parser.parse("abd"));
  }

  @Test public void regex_dotallFlag() {
    Parser<String> parserDefault = regex(".");
    assertThrows(ParseException.class, () -> parserDefault.parse("\n"));

    Parser<String> parserDotall = regex("(?s:.)");
    assertThat(parserDotall.parse("\n")).isEqualTo("\n");
  }

  @Test public void regex_freeSpacingFlag() {
    Parser<String> parser = regex("(?x)a b c");
    assertThat(parser.parse("abc")).isEqualTo("abc");
    assertThrows(ParseException.class, () -> parser.parse("a b c"));
  }

  @Test public void regex_enclosedCaseInsensitiveFlag() {
    Parser<String> parser = regex("a(?i:b)c");
    assertThat(parser.parse("abc")).isEqualTo("abc");
    assertThat(parser.parse("aBc")).isEqualTo("aBc");
    assertThrows(ParseException.class, () -> parser.parse("Abc"));
    assertThrows(ParseException.class, () -> parser.parse("abC"));
  }

  @Test public void regex_doesNotMatchSubstringsAfterCursor() {
    Parser<String> parser = regex("[a-z]+");
    assertThrows(ParseException.class, () -> parser.parse("123abc"));
  }

  @Test public void regex_choicePruning_caseInsensitiveFlag() {
    Parser<String> parser = anyOf(regex("(?i:b)"), regex("x"), regex("y"));
    assertThat(parser.parse("B")).isEqualTo("B");
  }

  @Test public void regex_choicePruning_unicodeCharacterClassFlag() {
    Parser<String> parser = anyOf(regex("(?U:\\d)"), regex("x"), regex("y"));
    assertThat(parser.parse("\u0661")).isEqualTo("\u0661");
  }

  @Test public void regex_throwsForReaderBasedInput() {
    Parser<String> p = regex("[a-z]+");
    Stream<String> stream = p.parseToStream(new java.io.StringReader("abc"));
    assertThrows(UnsupportedOperationException.class, () -> stream.findFirst());
  }

  @Test public void regex_prefixPruning() {
    Parser<String> p = anyOf(regex("[a-z]+"), regex("[0-9]+"), regex("foo"));
    assertThat(p.parse("abc")).isEqualTo("abc");
    assertThat(p.parse("123")).isEqualTo("123");
  }

  @Test public void regex_prefixPruning_literal() {
    Parser<String> p = anyOf(regex("abc"), regex("def"), regex("ghi"));
    assertThat(p.parse("abc")).isEqualTo("abc");
    assertThat(p.parse("def")).isEqualTo("def");
  }

  @Test public void regex_prefixPruning_alternation() {
    Parser<String> p = anyOf(regex("ab|cd"), regex("ef"), regex("gh"));
    assertThat(p.parse("ab")).isEqualTo("ab");
    assertThat(p.parse("cd")).isEqualTo("cd");
  }

  @Test public void regex_prefixPruning_group() {
    Parser<String> p = anyOf(regex("(abc)"), regex("def"), regex("ghi"));
    assertThat(p.parse("abc")).isEqualTo("abc");
  }

  @Test public void regex_prefixPruning_quantified() {
    Parser<String> p = anyOf(regex("a+"), regex("b+"), regex("c+"));
    assertThat(p.parse("aaa")).isEqualTo("aaa");
  }

  @Test public void regex_prefixPruning_characterSet() {
    Parser<String> p = anyOf(regex("[ab]"), regex("c"), regex("d"));
    assertThat(p.parse("a")).isEqualTo("a");
    assertThat(p.parse("b")).isEqualTo("b");
  }

  @Test public void regex_prefixPruning_characterSetRange() {
    Parser<String> p = anyOf(regex("[a-c]"), regex("d"), regex("e"));
    assertThat(p.parse("b")).isEqualTo("b");
  }

  @Test public void regex_prefixPruning_predefinedCharClass() {
    Parser<String> p = anyOf(regex("\\d+"), regex("abc"), regex("def"));
    assertThat(p.parse("123")).isEqualTo("123");
  }

  @Test public void regex_prefixPruning_fallbackNoneOf() {
    Parser<String> p = anyOf(regex("[^ab]"), regex("c"), regex("d"));
    assertThat(p.parse("x")).isEqualTo("x");
  }

  @Test public void regex_prefixPruning_withFallback() {
    Parser<String> p = anyOf(regex("\\w+"), regex("[0-9]+"), regex("foo"));
    assertThat(p.parse("abc")).isEqualTo("abc");
  }

  @Test public void regex_prefixPruning_withAlternationFallback() {
    Parser<String> p = anyOf(regex("a|\\w+"), regex("[0-9]+"), regex("foo"));
    assertThat(p.parse("bcd")).isEqualTo("bcd");
  }

  @Test public void regex_prefixPruning_withCharacterSetFallback() {
    Parser<String> p = anyOf(regex("[a-z\\p{Digit}]+"), regex("foo"), regex("bar"));
    assertThat(p.parse("123")).isEqualTo("123");
  }

  @Test public void regex_optionalPrefix_doesNotPruneValidInput() {
    Parser<String> p = anyOf(regex("a*b"), regex("c"), regex("d"));
    assertThat(p.parse("b")).isEqualTo("b");
  }

  @Test public void regex_optionalAlternationPrefix_doesNotPruneValidInput() {
    Parser<String> p = anyOf(regex("(a|b)?c"), regex("x"), regex("y"));
    assertThat(p.parse("c")).isEqualTo("c");
    assertThat(p.parse("ac")).isEqualTo("ac");
    assertThat(p.parse("bc")).isEqualTo("bc");
  }

  @Test public void regex_multipleOptionalPrefixes_doesNotPruneValidInput() {
    Parser<String> p = anyOf(regex("a*b*c"), regex("x"), regex("y"));
    assertThat(p.parse("c")).isEqualTo("c");
    assertThat(p.parse("bc")).isEqualTo("bc");
    assertThat(p.parse("abc")).isEqualTo("abc");
  }

  @Test public void regex_matchesInMiddle() {
    Parser<String> parser = sequence(string("["), regex("[a-z]+"), string("]"), (l, r, rt) -> r);
    assertThat(parser.parse("[abc]")).isEqualTo("abc");
  }

  @Test public void regex_matchesAtEnd() {
    Parser<String> parser = sequence(string("["), regex("[a-z]+"), (l, r) -> r);
    assertThat(parser.parse("[abc")).isEqualTo("abc");
  }

  @Test public void regex_matchesInLoop_parseToStream() {
    Parser<String> parser = regex("[a-z]+");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("abc def ghi").toList())
        .containsExactly("abc", "def", "ghi")
        .inOrder();
  }

  @Test public void regex_matchesInLoop_atLeastOnceDelimitedBy() {
    Parser<List<String>> parser = regex("[a-z]+").atLeastOnceDelimitedBy(",");
    assertThat(parser.parse("abc,def,ghi")).containsExactly("abc", "def", "ghi").inOrder();
  }

  @Test public void regex_parseSkipping() {
    Parser<String> parser = sequence(string("["), regex("[a-z]+"), string("]"), (l, r, rt) -> r);
    assertThat(parser.parseSkipping(Character::isWhitespace, "[   abc   ]")).isEqualTo("abc");
  }

  @Test public void regex_failureMessage() {
    Parser<String> parser = regex("[a-z]+");
    var exception = assertThrows(ParseException.class, () -> parser.parse("123"));
    assertThat(exception.getMessage())
        .isEqualTo(
            """
            at 1:1: expecting <=~/[a-z]+/>, encountered:\s
                123
                ^
            """);
  }

  @Test public void regex_failureMessage_inSequence() {
    Parser<?> parser = sequence(string("["), regex("[a-z]+"), string("]"));
    var exception = assertThrows(ParseException.class, () -> parser.parse("[123]"));
    assertThat(exception.getMessage())
        .isEqualTo(
            """
            at 1:2: expecting <=~/[a-z]+/>, encountered:\s
                [123]
                 ^
            """);
  }

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

  @Test public void duration_longMaxValueNanosSuccess() {
    assertThat(Parsers.DURATION.parse("9223372036854775807ns"))
        .isEqualTo(Duration.ofNanos(Long.MAX_VALUE));
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
            at 1:1: expecting <integer>, encountered:\s
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

  @Test public void duration_skippingWhitespace_success() {
    assertThat(Parsers.DURATION.parseSkipping(Character::isWhitespace, "  2m1s  "))
        .isEqualTo(Duration.ofMinutes(2).plusSeconds(1));
  }

  @Test public void duration_skippingWhitespace_spaceBetweenDigitsAndUnitThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parseSkipping(Character::isWhitespace, "1 s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting one of [d, h, m, ms, ns, s, us, w], encountered:\s
                1 s
                 ^
            """);
  }

  @Test public void duration_skippingWhitespace_spaceBetweenSegmentsThrows() {
    ParseException e = assertThrows(
        ParseException.class,
        () -> Parsers.DURATION.parseSkipping(Character::isWhitespace, "1s 2m"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <EOF>, encountered:\s
                1s 2m
                   ^
            """);
  }

  @Test public void duration_decimalLastSegment() {
    assertThat(Parsers.DURATION.parse("1.5s")).isEqualTo(Duration.ofMillis(1500));
    assertThat(Parsers.DURATION.parse("1h2.5m"))
        .isEqualTo(Duration.ofHours(1).plus(Duration.ofMinutes(2).plusSeconds(30)));
  }

  @Test public void duration_fractionalNotLastSegmentThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1.5h2m"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("at 1:1: Only the last duration segment is allowed to be fractional: 1.5h");
  }

  @Test public void duration_negativeThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("-1s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("3w9223372036854775807d100s"));
    assertThat(e).hasMessageThat().contains("duration out of range: 9223372036854775807d");
  }

  @Test public void duration_overflowAccumulationThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("9223372036854775800s10000ms"));
    assertThat(e).hasMessageThat().isEqualTo("at 1:1: duration out of range");
  }

  @Test public void duration_overflowFractionalThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("100000000000000000000.5s"));
    assertThat(e).hasMessageThat().isEqualTo("at 1:1: duration out of range: 1.0E20s");
  }

  @Test public void duration_unorderedSegmentsThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1s2m"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("at 1:1: Duration units must be specified in order: 1s2m");
  }

  @Test public void duration_duplicateUnitsThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1s2s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("at 1:1: Duration units must be specified in order: 1s2s");
  }

  @Test public void bmpCodeUnit_validHexUpper() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("D83D")).isEqualTo((char) 0xD83D);
  }

  @Test public void bmpCodeUnit_validHexLower() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("d83d")).isEqualTo((char) 0xD83D);
  }

  @Test public void bmpCodeUnit_zero() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("0000")).isEqualTo('\0');
  }

  @Test public void bmpCodeUnit_max() {
    assertThat(Parsers.BMP_CODE_UNIT.parse("FFFF")).isEqualTo((char) 65535);
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:5: expecting <one or more [0-9]>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:3: expecting <one or more [0-9]>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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
            at 1:1: expecting <integer>, encountered:\s
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

  @Test public void unsignedInteger_parseZero() {
    assertThat(UNSIGNED_INTEGER.parse("0")).isEqualTo("0");
  }

  @Test public void unsignedInteger_parseTen() {
    assertThat(UNSIGNED_INTEGER.parse("10")).isEqualTo("10");
  }

  @Test public void unsignedInteger_parseMultipleDigits() {
    assertThat(UNSIGNED_INTEGER.parse("123")).isEqualTo("123");
  }

  @Test public void unsignedInteger_delimited() {
    assertThat(UNSIGNED_INTEGER.atLeastOnceDelimitedBy(",").parse("1,2"))
        .containsExactly("1", "2")
        .inOrder();
  }

  @Test public void unsignedInteger_rejectsAlphabetic() {
    ParseException thrown = assertThrows(ParseException.class, () -> UNSIGNED_INTEGER.parse("foo"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:\s
                foo
                ^
            """);
  }

  @Test public void unsignedInteger_rejectsRedundantLeadingZeroInteger() {
    ParseException thrown = assertThrows(ParseException.class, () -> UNSIGNED_INTEGER.parse("00"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:\s
                00
                ^
            """);
  }

  @Test public void unsignedInteger_rejectsRedundantLeadingZeroWithDigits() {
    ParseException thrown = assertThrows(ParseException.class, () -> UNSIGNED_INTEGER.parse("001"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:\s
                001
                ^
            """);
  }

  @Test public void unsignedInteger_rejectsEmptyString() {
    ParseException thrown = assertThrows(ParseException.class, () -> UNSIGNED_INTEGER.parse(""));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void unsignedInteger_parseSkipping_success() {
    assertThat(UNSIGNED_INTEGER.atLeastOnce().parseSkipping(Character::isWhitespace, " 1 2 3 "))
        .containsExactly("1", "2", "3");
  }

  @Test public void unsignedInteger_parseSkipping_internalWhitespaceThrows() {
    ParseException thrown = assertThrows(
        ParseException.class, () -> UNSIGNED_INTEGER.parseSkipping(Character::isWhitespace, "1 2"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <EOF>, encountered:\s
                1 2
                  ^
            """);
  }

  @Test public void unsignedInteger_getPrefixes_effectiveInAnyOf() {
    Parser<String> parser = anyOf(UNSIGNED_INTEGER, string("abc"), string("def"));

    assertThat(parser.parse("0")).isEqualTo("0");
    assertThat(parser.parse("1")).isEqualTo("1");
    assertThat(parser.parse("2")).isEqualTo("2");
    assertThat(parser.parse("9")).isEqualTo("9");
    assertThat(parser.parse("abc")).isEqualTo("abc");
  }

  @Test public void signedDouble_zero() {
    assertThat(SIGNED_DOUBLE.parse("0")).isEqualTo(0.0);
  }

  @Test public void signedDouble_positiveInteger() {
    assertThat(SIGNED_DOUBLE.parse("123")).isEqualTo(123.0);
  }

  @Test public void signedDouble_negativeInteger() {
    assertThat(SIGNED_DOUBLE.parse("-123")).isEqualTo(-123.0);
  }

  @Test public void signedDouble_positiveFloat() {
    assertThat(SIGNED_DOUBLE.parse("0.5")).isEqualTo(0.5);
  }

  @Test public void signedDouble_negativeFloat() {
    assertThat(SIGNED_DOUBLE.parse("-0.5")).isEqualTo(-0.5);
  }

  @Test public void signedDouble_exponent() {
    assertThat(SIGNED_DOUBLE.parse("1.23e4")).isEqualTo(12300.0);
  }

  @Test public void signedDouble_negativeExponent() {
    assertThat(SIGNED_DOUBLE.parse("1.23e-4")).isEqualTo(0.000123);
  }

  @Test public void signedDouble_positiveExponent() {
    assertThat(SIGNED_DOUBLE.parse("1.23e+4")).isEqualTo(12300.0);
  }

  @Test public void signedDouble_capitalExponent() {
    assertThat(SIGNED_DOUBLE.parse("1.23E4")).isEqualTo(12300.0);
  }

  @Test public void signedDouble_zeroWithExponent() {
    assertThat(SIGNED_DOUBLE.parse("0e1")).isEqualTo(0.0);
  }

  @Test public void signedDouble_emptyThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse(""));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void signedDouble_leadingPlusThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("+123"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                +123
                ^
            """);
  }

  @Test public void signedDouble_leadingZeroThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("05"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                05
                ^
            """);
  }

  @Test public void signedDouble_leadingZeroFloatThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("00.5"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                00.5
                ^
            """);
  }

  @Test public void signedDouble_missingIntegerThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse(".5"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                .5
                ^
            """);
  }

  @Test public void signedDouble_missingFractionThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("5."));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <one or more [0-9]>, encountered:\s
                5.
                  ^
            """);
  }

  @Test public void signedDouble_emptyExponentThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("1e"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <digits>, encountered:\s
                1e
                  ^
            """);
  }

  @Test public void signedDouble_emptyExponentSignThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("1e+"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <digits>, encountered:\s
                1e+
                   ^
            """);
  }

  @Test public void signedDouble_fractionalExponentThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("1e4.5"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <EOF>, encountered:\s
                1e4.5
                   ^
            """);
  }

  @Test public void signedDouble_suffixThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("1e4f"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <EOF>, encountered:\s
                1e4f
                   ^
            """);
  }

  @Test public void signedDouble_nanThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("NaN"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                NaN
                ^
            """);
  }

  @Test public void signedDouble_infinityThrows() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> SIGNED_DOUBLE.parse("Infinity"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting one of [integer, -], encountered:\s
                Infinity
                ^
            """);
  }

  @Test public void signedDouble_overflow() {
    assertThat(SIGNED_DOUBLE.parse("1e999")).isEqualTo(Double.POSITIVE_INFINITY);
  }

  @Test public void signedDouble_negativeOverflow() {
    assertThat(SIGNED_DOUBLE.parse("-1e999")).isEqualTo(Double.NEGATIVE_INFINITY);
  }

  @Test public void signedDouble_underflow() {
    assertThat(SIGNED_DOUBLE.parse("1e-999")).isEqualTo(0.0);
  }

  @Test public void signedDouble_sourceMatchesOverflow() {
    assertThat(SIGNED_DOUBLE.source().parse("1e999")).isEqualTo("1e999");
  }

  @Test public void codePoint_zero() {
    assertThat(CODE_POINT.parse("00000000")).isEqualTo(0);
  }

  @Test public void codePoint_validBmp() {
    assertThat(CODE_POINT.parse("00000041")).isEqualTo(0x41);
  }

  @Test public void codePoint_validSupplementary() {
    assertThat(CODE_POINT.parse("0001F600")).isEqualTo(0x1F600);
  }

  @Test public void codePoint_upperBound() {
    assertThat(CODE_POINT.parse("0010FFFF")).isEqualTo(0x10FFFF);
  }

  @Test public void codePoint_tooLargeThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse("00110000"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <code point>, encountered:\s
                00110000
                ^
            """);
  }

  @Test public void codePoint_negativeWrappedThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse("FFFFFFFF"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <code point>, encountered:\s
                FFFFFFFF
                ^
            """);
  }

  @Test public void codePoint_invalidHexThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse("0001G600"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <8 hex digits>, encountered:\s
                0001G600
                ^
            """);
  }

  @Test public void codePoint_emptyThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse(""));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <8 hex digits>, encountered:\s
                <EOF>
                ^
            """);
  }

  @Test public void codePoint_insufficientDigitsThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse("000041"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <8 hex digits>, encountered:\s
                000041
                ^
            """);
  }

  @Test public void codePoint_excessiveDigitsThrows() {
    ParseException thrown = assertThrows(ParseException.class, () -> CODE_POINT.parse("000000412"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:9: expecting <EOF>, encountered:\s
                000000412
                        ^
            """);
  }
}

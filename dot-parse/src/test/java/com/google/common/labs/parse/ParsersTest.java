package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.ParserSubject.assertThat;
import static com.google.common.labs.parse.Parsers.BMP_CODE_UNIT;
import static com.google.common.labs.parse.Parsers.CODE_POINT;
import static com.google.common.labs.parse.Parsers.SIGNED_DOUBLE;
import static com.google.common.labs.parse.Parsers.UNSIGNED_INTEGER;
import static com.google.common.labs.parse.Parsers.regex;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Range;
import com.google.common.labs.parse.Parser.ParseException;
import java.io.StringReader;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ParsersTest {

  @Test public void regex_matchesSimplePattern() {
    assertThat(regex("[a-z]+")).fromString("abc").parsesTo("abc");
  }

  @Test public void regex_matchesComplexPattern() {
    assertThat(regex("[0-9]{3}-[0-9]{3,4}")).fromStringOrReader("123-4567").parsesTo("123-4567");
  }

  @Test public void regex_canMatchPartially() {
    assertThat(regex("[a-z]+").followedBy("!")).fromString("abc!").parsesTo("abc");
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

  @Test public void regex_sequenceOfOptionalPatterns_throws() {
    var exception = assertThrows(IllegalArgumentException.class, () -> regex("a*foo?(c+)*"));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("regex must not match empty string: a*foo?(c+)*");
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
    assertThat(exception).hasMessageThat().isEqualTo("regex must not match empty string: foo|");
  }

  @Test public void regex_valid() {
    regex("a+");
    regex("[0-9]");
    regex("(?:foo|bar)+");
  }

  @Test public void regex_empty_throws() {
    assertThrows(IllegalArgumentException.class, () -> regex(""));
  }

  @Test public void regex_emptyMatchingWithSpace() {
    regex("a* b*");
    assertThrows(IllegalArgumentException.class, () -> regex("(?x)a* b*"));
  }

  @Test public void regex_anchor_throws() {
    assertThrows(IllegalArgumentException.class, () -> regex("^a"));
    assertThrows(IllegalArgumentException.class, () -> regex("a$"));
    assertThrows(IllegalArgumentException.class, () -> regex("\\ba"));
  }

  @Test public void regex_lookaround_throws() {
    assertThrows(IllegalArgumentException.class, () -> regex("a(?=b)"));
    assertThrows(IllegalArgumentException.class, () -> regex("a(?!b)"));
  }

  @Test public void regex_backreference_throws() {
    assertThrows(IllegalArgumentException.class, () -> regex("(a)\\1"));
    assertThrows(IllegalArgumentException.class, () -> regex("(?<foo>a)\\k<foo>"));
  }

  @Test public void regex_caseInsensitiveFlag() {
    Parser<String> parser = regex("(?i:abc)");
    assertThat(parser).fromStringOrReader("abc").parsesTo("abc");
    assertThat(parser).fromStringOrReader("ABC").parsesTo("ABC");
    assertThat(parser).fromStringOrReader("aBc").parsesTo("aBc");
    assertThat(parser).fromStringOrReader("abd").failsToParse();
  }

  @Test public void regex_dotallFlag() {
    assertThat(regex(".")).fromStringOrReader("\n").failsToParse();
    assertThat(regex("(?s:.)")).fromStringOrReader("\n").parsesTo("\n");
  }

  @Test public void regex_freeSpacingFlag() {
    Parser<String> parser = regex("(?x)a b c");
    assertThat(parser).fromStringOrReader("abc").parsesTo("abc");
    assertThat(parser).fromStringOrReader("a b c").failsToParse();
  }

  @Test public void regex_enclosedCaseInsensitiveFlag() {
    Parser<String> parser = regex("a(?i:b)c");
    assertThat(parser).fromStringOrReader("abc").parsesTo("abc");
    assertThat(parser).fromStringOrReader("aBc").parsesTo("aBc");
    assertThat(parser).fromStringOrReader("Abc").failsToParse();
    assertThat(parser).fromStringOrReader("abC").failsToParse();
  }

  @Test public void regex_doesNotMatchSubstringsAfterCursor() {
    assertThat(regex("[a-z]+")).fromString("123abc").failsToParse();
  }

  @Test public void regex_choicePruning_caseInsensitiveFlag() {
    Parser<String> parser = anyOf(regex("(?i:b)"), regex("x"), regex("y"));
    assertThat(parser).fromStringOrReader("B").parsesTo("B");
  }

  @Test public void regex_choicePruning_unicodeCharacterClassFlag() {
    Parser<String> parser = anyOf(regex("(?U:\\d)"), regex("x"), regex("y"));
    assertThat(parser).fromStringOrReader("\u0661").parsesTo("\u0661");
  }

  @Test public void regex_choicePruning_nonBmp() {
    Parser<String> parser = anyOf(regex("a\uD83D\uDE00b"), regex("x"), regex("y"));
    assertThat(parser).fromStringOrReader("a\uD83D\uDE00b").parsesTo("a\uD83D\uDE00b");
  }

  @Test public void regex_usPhoneNumberExample() {
    Parser<String> usPhoneNumber = regex("\\(\\d{3}\\)\\d{3}-\\d{4}");
    assertThat(usPhoneNumber).fromStringOrReader("(123)456-7890").parsesTo("(123)456-7890");
    assertThat(usPhoneNumber).fromStringOrReader("123-456-7890").failsToParse();
  }

  @Test public void regex_throwsForReaderBasedInput() {
    Parser<String> p = regex("[a-z]+");
    Stream<String> stream = p.parseToStream(new StringReader("abc"));
    assertThrows(UnsupportedOperationException.class, () -> stream.findFirst());
  }

  @Test public void regex_finiteOnReaderInput() {
    Parser<String> parser = regex("a?b{1,3}c");
    assertThat(parser).fromStringOrReader("abbc").parsesTo("abbc");
  }

  @Test public void regex_prefixPruning() {
    Parser<String> p = anyOf(regex("[a-z]+"), regex("[0-9]+"), regex("foo"));
    assertThat(p.parse("abc")).isEqualTo("abc");
    assertThat(p.parse("123")).isEqualTo("123");
  }

  @Test public void regex_prefixPruning_literal() {
    Parser<String> p = anyOf(regex("abc"), regex("def"), regex("ghi"));
    assertThat(p).fromStringOrReader("abc").parsesTo("abc");
    assertThat(p).fromStringOrReader("def").parsesTo("def");
  }

  @Test public void regex_prefixPruning_alternation() {
    Parser<String> p = anyOf(regex("ab|cd"), regex("ef"), regex("gh"));
    assertThat(p).fromStringOrReader("ab").parsesTo("ab");
    assertThat(p).fromStringOrReader("cd").parsesTo("cd");
  }

  @Test public void regex_prefixPruning_group() {
    Parser<String> p = anyOf(regex("(abc)"), regex("def"), regex("ghi"));
    assertThat(p).fromStringOrReader("abc").parsesTo("abc");
  }

  @Test public void regex_prefixPruning_quantified() {
    Parser<String> p = anyOf(regex("a+"), regex("b+"), regex("c+"));
    assertThat(p.parse("aaa")).isEqualTo("aaa");
  }

  @Test public void regex_prefixPruning_characterSet() {
    Parser<String> p = anyOf(regex("[ab]"), regex("c"), regex("d"));
    assertThat(p).fromStringOrReader("a").parsesTo("a");
    assertThat(p).fromStringOrReader("b").parsesTo("b");
  }

  @Test public void regex_prefixPruning_characterSetRange() {
    Parser<String> p = anyOf(regex("[a-c]"), regex("d"), regex("e"));
    assertThat(p).fromStringOrReader("b").parsesTo("b");
  }

  @Test public void regex_prefixPruning_predefinedCharClass() {
    Parser<String> p = anyOf(regex("\\d+"), regex("abc"), regex("def"));
    assertThat(p.parse("123")).isEqualTo("123");
  }

  @Test public void regex_prefixPruning_fallbackNoneOf() {
    Parser<String> p = anyOf(regex("[^ab]"), regex("c"), regex("d"));
    assertThat(p).fromStringOrReader("x").parsesTo("x");
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
            at 1:1: expecting <=~/[a-z]+/>, encountered:
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
            at 1:2: expecting <=~/[a-z]+/>, encountered:
                [123]
                 ^
            """);
  }

  @Test public void regex_as_success() {
    Parser<String> parser = regex("[a-z]+").as("word");
    assertThat(parser.parse("abc")).isEqualTo("abc");
  }

  @Test public void regex_as_failureMessage() {
    Parser<String> parser = regex("[a-z]+").as("letters");
    var exception = assertThrows(ParseException.class, () -> parser.parse("123"));
    assertThat(exception).hasMessageThat().contains("1:1");
    assertThat(exception).hasMessageThat().contains("expecting <letters>");
  }

  @Test public void regex_as_failureMessage_inSequence() {
    Parser<?> parser = sequence(string("["), regex("[a-z]+").as("letters"), string("]"));
    var exception = assertThrows(ParseException.class, () -> parser.parse("[123]"));
    assertThat(exception).hasMessageThat().contains("1:2");
    assertThat(exception).hasMessageThat().contains("expecting <letters>");
  }

  @Test public void regex_as_inAnyOf_aggregatesSymbol() {
    Parser<?> parser = anyOf(regex("[a-z]+").as("lowercase"), regex("[A-Z]+").as("uppercase"));
    var exception = assertThrows(ParseException.class, () -> parser.parse("123"));
    assertThat(exception).hasMessageThat().contains("1:1");
    assertThat(exception).hasMessageThat().contains("expecting one of [lowercase, uppercase]");
  }

  @Test public void regex_as_chainedAs() {
    Parser<String> parser = regex("[a-z]+").as("word").as("identifier");
    var exception = assertThrows(ParseException.class, () -> parser.parse("123"));
    assertThat(exception).hasMessageThat().contains("1:1");
    assertThat(exception).hasMessageThat().contains("expecting <identifier>");
  }

  @Test public void regex_as_returnElision_success() {
    Parser<String> parser = regex("[a-z]+").as("word").thenReturn("ok");
    assertThat(parser.parse("abc")).isEqualTo("ok");
  }

  @Test public void regex_as_returnElision_failure() {
    Parser<String> parser = regex("[a-z]+").as("word").thenReturn("ok");
    var exception = assertThrows(ParseException.class, () -> parser.parse("123"));
    assertThat(exception).hasMessageThat().contains("1:1");
    assertThat(exception).hasMessageThat().contains("expecting <word>");
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:3: expecting <EOF>, encountered:
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
            at 1:2: expecting one of [d, h, m, ms, ns, s, us, w], encountered:
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
            at 1:4: expecting <EOF>, encountered:
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
        .isEqualTo(
            """
            at 1:1: Only the last duration segment is allowed to be fractional: 1.5h

                1.5h2m
                ^
            """);
  }

  @Test public void duration_negativeThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("-1s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:3: unexpected `duration unit char`:
                1ss
                  ^
            """);
  }

  @Test public void duration_overflowLongThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("999999999999999999999999999s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: For input string: "999999999999999999999999999"

                999999999999999999999999999s
                ^
            """);
  }

  @Test public void duration_overflowDurationThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("3w9223372036854775807d100s"));
    assertThat(e).hasMessageThat().contains("duration out of range: 9223372036854775807d");
  }

  @Test public void duration_overflowAccumulationThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("9223372036854775800s10000ms"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: duration out of range

                9223372036854775800s10000ms
                ^
            """);
  }

  @Test public void duration_overflowFractionalThrows() {
    ParseException e = assertThrows(
        ParseException.class, () -> Parsers.DURATION.parse("100000000000000000000.5s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: duration out of range: 1.0E20s

                100000000000000000000.5s
                ^
            """);
  }

  @Test public void duration_unorderedSegmentsThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1s2m"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: Duration units must be specified in order: 1s2m

                1s2m
                ^
            """);
  }

  @Test public void duration_duplicateUnitsThrows() {
    ParseException e = assertThrows(ParseException.class, () -> Parsers.DURATION.parse("1s2s"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: Duration units must be specified in order: 1s2s

                1s2s
                ^
            """);
  }

  @Test public void bmpCodeUnit_validHexUpper() {
    assertThat(BMP_CODE_UNIT.parse("D83D")).isEqualTo((char) 0xD83D);
  }

  @Test public void bmpCodeUnit_validHexLower() {
    assertThat(BMP_CODE_UNIT.parse("d83d")).isEqualTo((char) 0xD83D);
  }

  @Test public void bmpCodeUnit_zero() {
    assertThat(BMP_CODE_UNIT.parse("0000")).isEqualTo('\0');
  }

  @Test public void bmpCodeUnit_max() {
    assertThat(BMP_CODE_UNIT.parse("FFFF")).isEqualTo((char) 65535);
  }

  @Test public void bmpCodeUnit_tooShortThrows() {
    ParseException e = assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse("FFF"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:
                FFF
                ^
            """);
  }

  @Test public void bmpCodeUnit_tooLongThrows() {
    ParseException e = assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse("FFFFF"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <EOF>, encountered:
                FFFFF
                    ^
            """);
  }

  @Test public void bmpCodeUnit_nonHexThrows() {
    ParseException e = assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse("FGHI"));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:
                FGHI
                ^
            """);
  }

  @Test public void bmpCodeUnit_emptyStringThrows() {
    ParseException e = assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse(""));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <4 hex digits>, encountered:
                <EOF>
                ^
            """);
  }

  @Test public void bmpCodeUnit_surrogatesToEmoji() {
    assertThat(BMP_CODE_UNIT.map(String::valueOf).zeroOrMore(joining()).parse("d83dDE00"))
        .isEqualTo("😀");
    assertThat(BMP_CODE_UNIT.map(String::valueOf).zeroOrMore(joining()).matches("d83dDE00"))
        .isTrue();
  }

  @Test public void mapWithIndex_bmpCodeUnit() {
    assertThat(
            BMP_CODE_UNIT
                .mapWithIndex((c, begin, end) -> begin + "-" + end + ": " + (int) c)
                .parse("0000"))
        .isEqualTo("0-4: 0");
  }

  @Test public void returnElision_bmpCodeUnit_matches() {
    assertThat(BMP_CODE_UNIT.parse("123F")).isEqualTo((char) 0x123F);
    assertThat(BMP_CODE_UNIT.matches("123F")).isTrue();
  }

  @Test public void returnElision_bmpCodeUnit_doesNotMatch() {
    assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse("123g"));
    assertThat(BMP_CODE_UNIT.matches("123g")).isFalse();
    assertThrows(ParseException.class, () -> BMP_CODE_UNIT.parse("123"));
    assertThat(BMP_CODE_UNIT.matches("123")).isFalse();
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:5: expecting <one or more [0-9]>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:4: expecting <EOF>, encountered:
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
            at 1:3: expecting <one or more [0-9]>, encountered:
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
            at 1:4: expecting <EOF>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:3: expecting <EOF>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:1: expecting <integer>, encountered:
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
            at 1:3: expecting <EOF>, encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:3: expecting <one or more [0-9]>, encountered:
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
            at 1:3: expecting <digits>, encountered:
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
            at 1:4: expecting <digits>, encountered:
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
            at 1:4: expecting <EOF>, encountered:
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
            at 1:4: expecting <EOF>, encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting one of [integer, -], encountered:
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
            at 1:1: expecting <code point>, encountered:
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
            at 1:1: expecting <code point>, encountered:
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
            at 1:1: expecting <8 hex digits>, encountered:
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
            at 1:1: expecting <8 hex digits>, encountered:
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
            at 1:1: expecting <8 hex digits>, encountered:
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
            at 1:9: expecting <EOF>, encountered:
                000000412
                        ^
            """);
  }

  @Test public void regex_withFunction_oneGroup_parsesToMappedValue() {
    Parser<Integer> parser = regex("id:(\\d+)", s -> Integer.parseInt(s));
    assertThat(parser).fromString("id:123").parsesTo(123);
  }

  @Test public void regex_withFunction_oneGroup_boundedOnReader() {
    Parser<Integer> parser = regex("id:(\\d{3})", s -> Integer.parseInt(s));
    assertThat(parser).fromStringOrReader("id:123").parsesTo(123);
  }

  @Test public void regex_withFunction_oneGroup_mismatch_failsToParse() {
    Parser<Integer> parser = regex("id:(\\d+)", s -> Integer.parseInt(s));
    assertThat(parser).fromString("id:abc").failsToParse();
  }

  @Test public void regex_withFunction_oneGroup_enclosingEntirePattern() {
    Parser<String> parser = regex("([a-z]+)", s -> s.toUpperCase(Locale.ROOT));
    assertThat(parser).fromString("abc").parsesTo("ABC");
  }

  @Test public void regex_withFunction_namedGroup_parsesToMappedValue() {
    Parser<Integer> parser = regex("id:(?<id>\\d+)", s -> Integer.parseInt(s));
    assertThat(parser).fromString("id:123").parsesTo(123);
  }

  @Test public void
      regex_withFunction_namedGroup_cardinalityMismatch_throwsIllegalArgumentException() {
    var ex =
        assertThrows(IllegalArgumentException.class, () -> regex("(?<k>\\w+)=(?<v>\\d+)", s -> s));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "regex pattern '(?<k>\\w+)=(?<v>\\d+)' has 2 capturing group(s), but 1 expected");
  }

  @Test public void regex_withFunction_source_returnsEntireMatchedSubstring() {
    Parser<String> parser = regex("id:(\\d+)", s -> Integer.parseInt(s)).source();
    assertThat(parser).fromString("id:123").parsesTo("id:123");
  }

  @Test public void regex_withFunction_nonCapturingGroup_ignoredInCardinalityAndMapping() {
    Parser<Integer> parser = regex("(?:prefix:)(\\d+)", s -> Integer.parseInt(s));
    assertThat(parser).fromString("prefix:123").parsesTo(123);
  }

  @Test public void regex_withFunction_onlyNonCapturingGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("(?:abc)(?:def)", s -> s));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(?:abc)(?:def)' has 0 capturing group(s), but 1 expected");
  }

  @Test public void regex_withFunction_zeroGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class, () -> regex("[a-z]+", s -> s.toUpperCase(Locale.ROOT)));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '[a-z]+' has 0 capturing group(s), but 1 expected");
  }

  @Test public void regex_withFunction_twoGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class,
        () -> regex("(\\w+)=(\\d+)", s -> s.toUpperCase(Locale.ROOT)));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(\\w+)=(\\d+)' has 2 capturing group(s), but 1 expected");
  }

  @Test public void regex_withFunction_anchor_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("^(\\d+)", s -> s));
    assertThat(ex).hasMessageThat().isEqualTo("anchors are not allowed in regex parser: ^");
  }

  @Test public void regex_withFunction_lookaround_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("(?=\\d)(\\w+)", s -> s));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("lookarounds are not allowed in regex parser: (?=\\d)");
  }

  @Test public void regex_withFunction_backreference_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("(\\w+)-\\1", s -> s));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("backreferences are not allowed in regex parser: \\1");
  }

  @Test public void regex_withFunction_matchesEmpty_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("(\\d*)", s -> s));
    assertThat(ex).hasMessageThat().isEqualTo("regex must not match empty string: (\\d*)");
  }

  @Test public void regex_withFunction_nullMapper_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> regex("(\\d+)", (Function<String, Integer>) null));
  }

  @Test public void regex_withBiFunction_twoGroups_parsesToMappedValue() {
    Parser<List<String>> parser = regex("(\\w+)=(\\d+)", (k, v) -> List.of(k, v));
    assertThat(parser).fromString("k=123").parsesTo(List.of("k", "123"));
  }

  @Test public void regex_withBiFunction_twoGroups_boundedOnReader() {
    Parser<List<String>> parser = regex("(\\w{1,3})=(\\d{1,3})", (k, v) -> List.of(k, v));
    assertThat(parser).fromStringOrReader("k=123").parsesTo(List.of("k", "123"));
  }

  @Test public void regex_withBiFunction_multipleNamedGroups_parsesToMappedValues() {
    Parser<List<String>> parser = regex("(?<key>\\w+)=(?<value>\\d+)", (k, v) -> List.of(k, v));
    assertThat(parser).fromString("k=123").parsesTo(List.of("k", "123"));
  }

  @Test public void regex_withBiFunction_mixedNamedAndNumberedGroups_orderedByIndex() {
    Parser<List<String>> parser = regex("(?<key>\\w+)=(\\d+)", (k, v) -> List.of(k, v));
    assertThat(parser).fromString("k=123").parsesTo(List.of("k", "123"));
  }

  @Test public void
      regex_withBiFunction_multipleNonCapturingGroups_ignoredInCardinalityAndMapping() {
    Parser<List<String>> parser =
        regex("(?:foo|bar)-(\\w+)-(?:baz)-(\\d+)", (w, d) -> List.of(w, d));
    assertThat(parser).fromString("foo-item-baz-42").parsesTo(List.of("item", "42"));
  }

  @Test public void regex_withBiFunction_oneGroup_throwsIllegalArgumentException() {
    var ex = assertThrows(IllegalArgumentException.class, () -> regex("(\\d+)", (a, b) -> a + b));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(\\d+)' has 1 capturing group(s), but 2 expected");
  }

  @Test public void regex_withBiFunction_threeGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class, () -> regex("(\\d+)-(\\d+)-(\\d+)", (a, b) -> a + b));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(\\d+)-(\\d+)-(\\d+)' has 3 capturing group(s), but 2 expected");
  }

  @Test public void regex_withBiFunction_optionalGroup_unmatchedEvaluatesToNull() {
    Parser<List<String>> parser = regex("([a-z]+)(?:-(\\d+))?", (a, b) -> Arrays.asList(a, b));
    assertThat(parser).fromString("foo").parsesTo(Arrays.asList("foo", null));
  }

  @Test public void regex_withBiFunction_optionalGroup_matchedEvaluatesToValue() {
    Parser<List<String>> parser = regex("([a-z]+)(?:-(\\d+))?", (a, b) -> Arrays.asList(a, b));
    assertThat(parser).fromString("foo-123").parsesTo(List.of("foo", "123"));
  }

  @Test public void regex_withBiFunction_alternationGroups_firstBranchMatched() {
    Parser<List<String>> parser = regex("(\\d+)|([a-z]+)", (a, b) -> Arrays.asList(a, b));
    assertThat(parser).fromString("123").parsesTo(Arrays.asList("123", null));
  }

  @Test public void regex_withBiFunction_alternationGroups_secondBranchMatched() {
    Parser<List<String>> parser = regex("(\\d+)|([a-z]+)", (a, b) -> Arrays.asList(a, b));
    assertThat(parser).fromString("abc").parsesTo(Arrays.asList(null, "abc"));
  }

  @Test public void regex_withMapFrom3_threeGroups_parsesToMappedValue() {
    Parser<List<String>> parser =
        regex("(\\d{4})-(\\d{2})-(\\d{2})", (y, m, d) -> List.of(y, m, d));
    assertThat(parser).fromStringOrReader("2026-08-30").parsesTo(List.of("2026", "08", "30"));
  }

  @Test public void regex_withMapFrom3_nestedGroups_orderedByOpeningParenthesis() {
    Parser<List<String>> parser = regex("((a)(b))", (g1, g2, g3) -> List.of(g1, g2, g3));
    assertThat(parser).fromStringOrReader("ab").parsesTo(List.of("ab", "a", "b"));
  }

  @Test public void regex_withMapFrom3_twoGroups_throwsIllegalArgumentException() {
    var ex =
        assertThrows(IllegalArgumentException.class, () -> regex("(\\d+)-(\\d+)", (a, b, c) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(\\d+)-(\\d+)' has 2 capturing group(s), but 3 expected");
  }

  @Test public void regex_withMapFrom4_fourGroups_parsesToMappedValue() {
    Parser<List<String>> parser =
        regex("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)", (a, b, c, d) -> List.of(a, b, c, d));
    assertThat(parser).fromString("127.0.0.1").parsesTo(List.of("127", "0", "0", "1"));
  }

  @Test public void regex_withMapFrom4_threeGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class, () -> regex("(\\d+)-(\\d+)-(\\d+)", (a, b, c, d) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(\\d+)-(\\d+)-(\\d+)' has 3 capturing group(s), but 4 expected");
  }

  @Test public void regex_withMapFrom5_fiveGroups_parsesToMappedValue() {
    Parser<List<String>> parser =
        regex("(a)(b)(c)(d)(e)", (a, b, c, d, e) -> List.of(a, b, c, d, e));
    assertThat(parser).fromStringOrReader("abcde").parsesTo(List.of("a", "b", "c", "d", "e"));
  }

  @Test public void regex_withMapFrom5_fourGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class, () -> regex("(a)(b)(c)(d)", (a, b, c, d, e) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(a)(b)(c)(d)' has 4 capturing group(s), but 5 expected");
  }

  @Test public void regex_withMapFrom6_sixGroups_parsesToMappedValue() {
    Parser<List<String>> parser =
        regex("(a)(b)(c)(d)(e)(f)", (a, b, c, d, e, f) -> List.of(a, b, c, d, e, f));
    assertThat(parser).fromStringOrReader("abcdef").parsesTo(List.of("a", "b", "c", "d", "e", "f"));
  }

  @Test public void regex_withMapFrom6_fiveGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class, () -> regex("(a)(b)(c)(d)(e)", (a, b, c, d, e, f) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(a)(b)(c)(d)(e)' has 5 capturing group(s), but 6 expected");
  }

  @Test public void regex_withMapFrom7_sevenGroups_parsesToMappedValue() {
    Parser<List<String>> parser =
        regex("(a)(b)(c)(d)(e)(f)(g)", (a, b, c, d, e, f, g) -> List.of(a, b, c, d, e, f, g));
    assertThat(parser)
        .fromStringOrReader("abcdefg")
        .parsesTo(List.of("a", "b", "c", "d", "e", "f", "g"));
  }

  @Test public void regex_withMapFrom7_sixGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class,
        () -> regex("(a)(b)(c)(d)(e)(f)", (a, b, c, d, e, f, g) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("regex pattern '(a)(b)(c)(d)(e)(f)' has 6 capturing group(s), but 7 expected");
  }

  @Test public void regex_withMapFrom8_eightGroups_parsesToMappedValue() {
    Parser<List<String>> parser = regex(
        "(a)(b)(c)(d)(e)(f)(g)(h)", (a, b, c, d, e, f, g, h) -> List.of(a, b, c, d, e, f, g, h));
    assertThat(parser)
        .fromStringOrReader("abcdefgh")
        .parsesTo(List.of("a", "b", "c", "d", "e", "f", "g", "h"));
  }

  @Test public void regex_withMapFrom8_sevenGroups_throwsIllegalArgumentException() {
    var ex = assertThrows(
        IllegalArgumentException.class,
        () -> regex("(a)(b)(c)(d)(e)(f)(g)", (a, b, c, d, e, f, g, h) -> a));
    assertThat(ex)
        .hasMessageThat()
        .isEqualTo(
            "regex pattern '(a)(b)(c)(d)(e)(f)(g)' has 7 capturing group(s), but 8 expected");
  }

  @Test public void regex_withFunction_parseFailure_reportsErrorPosition() {
    Parser<Integer> parser = regex("id:(\\d+)", (Function<String, Integer>) Integer::parseInt);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("id:abc"));
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <=~/id:(\\d+)/>");
  }
}

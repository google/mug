package com.google.common.labs.regex;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RegexParserErrorTests {

  @Test public void characterClass_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <]>, encountered:
                [abc
                    ^
            """);
  }

  @Test public void characterClass_negated_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[^abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:6: expecting <]>, encountered:
                [^abc
                     ^
            """);
  }

  @Test public void characterClass_range_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <]>, encountered:
                [a-z
                    ^
            """);
  }

  @Test public void characterClass_range_trailingHyphen_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <]>, encountered:
                [a-
                   ^
            """);
  }

  @Test public void characterClass_empty_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[]"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <]>, encountered:
                []
                  ^
            """);
  }

  @Test public void characterClass_leadingClosingBracket_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[]abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:6: expecting <]>, encountered:
                []abc
                     ^
            """);
  }

  @Test public void characterClass_leadingClosingBracket_range_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[]-z"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <]>, encountered:
                []-z
                    ^
            """);
  }

  @Test public void characterClass_nested_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z[0-9]"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:10: expecting <]>, encountered:
                [a-z[0-9]
                         ^
            """);
  }

  @Test public void characterClass_intersection_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z&&def"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:10: expecting <]>, encountered:
                [a-z&&def
                         ^
            """);
  }

  @Test public void characterClass_intersection_nested_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z&&[0-9]"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:12: expecting <]>, encountered:
                [a-z&&[0-9]
                           ^
            """);
  }

  @Test public void characterClass_intersection_nested_negated_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z&&[^0-9]"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:13: expecting <]>, encountered:
                [a-z&&[^0-9]
                            ^
            """);
  }

  @Test public void characterClass_intersection_missingRightHandSide() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-z&&"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <character set>, encountered:
                [a-z&&
                      ^
            """);
  }

  @Test public void characterClass_negated_intersection_missingRightHandSide() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[^a-z&&"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <character set>, encountered:
                [^a-z&&
                       ^
            """);
  }

  @Test public void group_capturing_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <)>, encountered:
                (abc
                    ^
            """);
  }

  @Test public void group_capturing_empty_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("("));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting one of [subpattern, )], encountered:
                (
                 ^
            """);
  }

  @Test public void group_capturing_nested_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("((abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                ((abc)
                      ^
            """);
  }

  @Test public void group_capturing_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(a(b)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:6: expecting <)>, encountered:
                (a(b)
                     ^
            """);
  }

  @Test public void group_nonCapturing_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?:abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?:abc
                      ^
            """);
  }

  @Test public void group_nonCapturing_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?:(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?:(a)
                      ^
            """);
  }

  @Test public void group_named_missingClosingAngleBracket() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<nameabc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:11: expecting <>>, encountered:
                (?<nameabc)
                          ^
            """);
  }

  @Test public void group_named_missingName() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<>abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <word>, encountered:
                (?<>abc)
                   ^
            """);
  }

  @Test public void group_named_pythonSyntax_missingClosingAngleBracket() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?P<nameabc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:12: expecting <>>, encountered:
                (?P<nameabc)
                           ^
            """);
  }

  @Test public void group_named_pythonSyntax_missingName() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?P<>abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <word>, encountered:
                (?P<>abc)
                    ^
            """);
  }

  @Test public void group_named_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<name>abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:12: expecting <)>, encountered:
                (?<name>abc
                           ^
            """);
  }

  @Test public void group_named_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<name>(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:12: expecting <)>, encountered:
                (?<name>(a)
                           ^
            """);
  }

  @Test public void group_atomic_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?>abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?>abc
                      ^
            """);
  }

  @Test public void group_atomic_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?>(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?>(a)
                      ^
            """);
  }

  @Test public void lookahead_positive_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?=abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?=abc
                      ^
            """);
  }

  @Test public void lookahead_positive_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?=(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?=(a)
                      ^
            """);
  }

  @Test public void lookahead_negative_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?!abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?!abc
                      ^
            """);
  }

  @Test public void lookahead_negative_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?!(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?!(a)
                      ^
            """);
  }

  @Test public void lookbehind_positive_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<=abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <)>, encountered:
                (?<=abc
                       ^
            """);
  }

  @Test public void lookbehind_positive_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<=(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <)>, encountered:
                (?<=(a)
                       ^
            """);
  }

  @Test public void lookbehind_negative_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<!abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <)>, encountered:
                (?<!abc
                       ^
            """);
  }

  @Test public void lookbehind_negative_unclosedInnerGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<!(a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <)>, encountered:
                (?<!(a)
                       ^
            """);
  }

  @Test public void group_modifierFlags_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i:abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:8: expecting <)>, encountered:
                (?i:abc
                       ^
            """);
  }

  @Test public void group_modifierFlags_disabledFlags_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?-i:abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:9: expecting <)>, encountered:
                (?-i:abc
                        ^
            """);
  }

  @Test public void group_modifierFlags_bothFlags_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?is-m:abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:11: expecting <)>, encountered:
                (?is-m:abc
                          ^
            """);
  }

  @Test public void group_modifierFlags_standalone_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting one of [), :], encountered:
                (?i
                   ^
            """);
  }

  @Test public void group_modifierFlags_unknownFlag() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?z:abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting one of [), :], encountered:
                (?z:abc)
                  ^
            """);
  }

  @Test public void group_modifierFlags_standalone_unknownFlag() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?z)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting one of [), :], encountered:
                (?z)
                  ^
            """);
  }

  @Test public void group_modifierFlags_missingColon() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?iabc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting one of [), :], encountered:
                (?iabc)
                   ^
            """);
  }

  @Test public void group_modifierFlags_disabled_missingFlags() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?-:abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <modifier flag>, encountered:
                (?-:abc)
                   ^
            """);
  }

  @Test public void group_modifierFlags_disabled_standalone_missingFlags() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?-)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <modifier flag>, encountered:
                (?-)
                   ^
            """);
  }

  @Test public void group_modifierFlags_enabledThenEmptyDisabled() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i-:abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <modifier flag>, encountered:
                (?i-:abc)
                    ^
            """);
  }

  @Test public void group_modifierFlags_enabledThenEmptyDisabled_standalone() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i-)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <modifier flag>, encountered:
                (?i-)
                    ^
            """);
  }

  @Test public void group_modifierFlags_doubleHyphen() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i--m:a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <modifier flag>, encountered:
                (?i--m:a)
                    ^
            """);
  }

  @Test public void group_modifierFlags_standalone_doubleHyphen() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?i--)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:5: expecting <modifier flag>, encountered:
                (?i--)
                    ^
            """);
  }

  @Test public void group_modifierFlags_hyphenFirst_thenDoubleHyphen() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?--i:a)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <modifier flag>, encountered:
                (?--i:a)
                   ^
            """);
  }

  @Test public void group_unknownConstruct() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?*abc)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting one of [), :], encountered:
                (?*abc)
                  ^
            """);
  }

  @Test public void group_questionOnly_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting one of [), :], encountered:
                (?
                  ^
            """);
  }

  @Test public void escape_trailingBackslash() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting <escaped char>, encountered:
                \\
                 ^
            """);
  }

  @Test public void freeSpacingMode_unclosedGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?x) (abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:6: expecting <)>, encountered:
                 (abc
                     ^
            """);
  }

  @Test public void freeSpacingMode_unclosedCharacterClass() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?x) [abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:6: expecting <]>, encountered:
                 [abc
                     ^
            """);
  }

  @Test public void freeSpacingMode_unclosedGroupAfterComment() {
    ParseException e =
        assertThrows(ParseException.class, () -> RegexPattern.of("(?x) a # comment\n (b"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 2:4: expecting <)>, encountered:
                a # comment
                 (b
                   ^
            """);
  }

  @Test public void quantifier_danglingPlusAtStart() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("+"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <EOF>, encountered:
                +
                ^
            """);
  }

  @Test public void quantifier_danglingStarAtStart() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("*"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <EOF>, encountered:
                *
                ^
            """);
  }

  @Test public void quantifier_danglingQuestionAtStart() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("?"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:1: expecting <EOF>, encountered:
                ?
                ^
            """);
  }

  @Test public void quantifier_danglingPlusInGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(+)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting one of [subpattern, )], encountered:
                (+)
                 ^
            """);
  }

  @Test public void quantifier_danglingStarInGroup() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(*)"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:2: expecting one of [subpattern, )], encountered:
                (*)
                 ^
            """);
  }

  @Test public void quantifier_danglingPlusAfterPipe() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a|+"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <EOF>, encountered:
                a|+
                  ^
            """);
  }

  @Test public void hexEscape_codePointTooBig() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\x{110000}"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:3: expecting <code point>, encountered:
                \\x{110000}
                  ^
            """);
  }

  @Test public void namedUnicodeCharacter_unclosed() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\N{abc"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <}>, encountered:
                \\N{abc
                      ^
            """);
  }

  @Test public void namedUnicodeCharacter_emptyName() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\N{}"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:4: expecting <character name>, encountered:
                \\N{}
                   ^
            """);
  }

  @Test public void namedUnicodeCharacter_unknownName() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> RegexPattern.of("\\N{UNKNOWN_NAME}"));
    assertThat(e).hasMessageThat().contains("UNKNOWN_NAME");
  }
}

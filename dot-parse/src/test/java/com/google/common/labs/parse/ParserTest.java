package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.codePoint;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.first;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.quotedBy;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.single;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Parser.zeroOrMoreDelimited;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertThrows;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.labs.parse.Parser.ParseException;
import com.google.common.testing.NullPointerTester;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class ParserTest {
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');

  @Test public void testReddit() {
    Parser<Double> parser = consecutive(charsIn("[0-9.]"))
        .map(s -> {
          try {
            return Double.parseDouble(s);
          } catch (NumberFormatException e) {
            return null;
          }
        })
        .suchThat(Objects::nonNull, "double number");
    assertThat(parser.probe("1.23.4")).isEmpty();
  }

  @Test
  public void first_atBeginning_followedBy() {
    assertThat(first("foo").followedBy(string("bar")).parse("foobar")).isEqualTo("foo");
  }

  @Test
  public void first_severalCharsIn_followedBy() {
    assertThat(first("foo").followedBy(string("bar")).parse("skip foobar")).isEqualTo("foo");
  }

  @Test
  public void first_notFound() {
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> first("skip").then(first("foo")).parse("skip fobar"));
    assertThat(thrown).hasMessageThat().contains("1:5");
    assertThat(thrown).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void first_withReader_targetInSecondPage() {
    CharInput input = CharInput.from(new StringReader("0123456789foo_"), 10, 5);
    assertThat(first("foo").followedBy("_").parseToStream(input, 0)).containsExactly("foo");
  }

  @Test
  public void first_withReader_targetInThirdPage() {
    CharInput input = CharInput.from(new StringReader("01234567890123456789foo_"), 10, 5);
    assertThat(first("foo").followedBy("_").parseToStream(input, 0)).containsExactly("foo");
  }

  @Test
  public void first_withReader_targetAcrossPageBoundary() {
    CharInput input = CharInput.from(new StringReader("0123456789012345678foobar_"), 10, 5);
    assertThat(first("foobar").followedBy("_").parseToStream(input, 0)).containsExactly("foobar");
  }

  @Test
  public void first_skippingWhitespace() {
    assertThat(first(" foo").skipping(Character::isWhitespace).parse("    foo")).isEqualTo(" foo");
  }

  @Test
  public void first_withReader_largeInput_notFound() {
    String page = "fo".repeat(8192);
    Reader reader = new StringReader(page + page + "bar");
    assertThrows(ParseException.class, () -> first("foo").parseToStream(reader).count());
  }

  @Test
  public void first_withReader_largeInput_foundNearEnd() {
    String page = "fo".repeat(8192);
    Reader reader = new StringReader(page + page + "foo");
    assertThat(first("foo").parseToStream(reader)).containsExactly("foo");
  }

  @Test
  public void first_emptyString_throws() {
    assertThrows(IllegalArgumentException.class, () -> first(""));
  }

  @Test
  public void string_success() {
    Parser<String> parser = string("foo");
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void string_success_source() {
    Parser<String> parser = string("foo");
    assertThat(parser.source().parse("foo")).isEqualTo("foo");
    assertThat(parser.source().parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void string_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("foo").parse("fooa"));
    assertThrows(ParseException.class, () -> string("foo").parseToStream("fooa").toList());
  }

  @Test
  public void string_failure() {
    assertThrows(ParseException.class, () -> string("foo").parse("fo"));
    assertThrows(ParseException.class, () -> string("foo").parseToStream("fo").toList());
    assertThrows(ParseException.class, () -> string("foo").parse("food"));
    assertThrows(ParseException.class, () -> string("foo").parseToStream("food").toList());
    assertThrows(ParseException.class, () -> string("foo").parse("bar"));
    assertThrows(ParseException.class, () -> string("foo").parseToStream("bar").toList());
  }

  @Test
  public void string_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string(""));
  }

  @Test
  public void word_success() {
    assertThat(word("foo").parse("foo")).isEqualTo("foo");
  }

  @Test
  public void word_failIfFollowedByWordChar() {
    assertThrows(ParseException.class, () -> word("foo").parse("foobar"));
    assertThrows(ParseException.class, () -> word("foo").parse("foo_bar"));
    assertThrows(ParseException.class, () -> word("foo").parse("foo1"));
  }

  @Test
  public void word_successIfNotFollowedByWordChar() {
    assertThat(word("foo").probe("foo?")).containsExactly("foo");
    assertThat(word("foo").probe("foo-")).containsExactly("foo");
  }

  @Test
  public void word_skipping_success() {
    assertThat(word("foo").skipping(Character::isWhitespace).parseToStream("foo")).containsExactly("foo");
    assertThat(word("foo").skipping(Character::isWhitespace).parseToStream("foo foo"))
        .containsExactly("foo", "foo");
    assertThat(word("foo").skipping(Character::isWhitespace).probe(" foo-foo")).containsExactly("foo");
  }

  @Test
  public void word_skipping_failIfFollowedByWordChar() {
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(Character::isWhitespace, "foobar"));
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(Character::isWhitespace, "foo_bar"));
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(Character::isWhitespace, "foo1"));
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(Character::isWhitespace, " foo1"));
  }

  @Test
  public void testNulls() {
    NullPointerTester tester =
        new NullPointerTester()
            .setDefault(Parser.class, string("a"))
            .setDefault(Parser.OrEmpty.class, string("a").orElse("default"))
            .setDefault(String.class, "test");
    tester.testAllPublicStaticMethods(Parser.class);
  }

  @Test
  public void thenReturn_success() {
    Parser<Integer> parser1 = string("one").thenReturn(1);
    assertThat(parser1.parse("one")).isEqualTo(1);
    assertThat(parser1.parseToStream("one")).containsExactly(1);
    assertThat(parser1.parseToStream("")).isEmpty();

    Parser<String> parser2 = string("two").thenReturn("deux");
    assertThat(parser2.parse("two")).isEqualTo("deux");
    assertThat(parser2.parseToStream("two")).containsExactly("deux");
    assertThat(parser2.parseToStream("")).isEmpty();
  }

  @Test
  public void thenReturn_success_source() {
    Parser<Integer> parser1 = string("one").thenReturn(1);
    assertThat(parser1.source().parse("one")).isEqualTo("one");
    assertThat(parser1.source().parseToStream("one")).containsExactly("one");
    assertThat(parser1.source().parseToStream("")).isEmpty();

    Parser<String> parser2 = string("two").thenReturn("deux");
    assertThat(parser2.source().parse("two")).isEqualTo("two");
    assertThat(parser2.source().parseToStream("two")).containsExactly("two");
    assertThat(parser2.source().parseToStream("")).isEmpty();
  }

  @Test
  public void thenReturn_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("one").thenReturn(1).parse("onea"));
    assertThrows(
        ParseException.class, () -> string("one").thenReturn(1).parseToStream("onea").toList());
  }

  @Test
  public void thenReturn_failure() {
    assertThrows(ParseException.class, () -> string("one").thenReturn(1).parse("two"));
    assertThrows(
        ParseException.class, () -> string("one").thenReturn(1).parseToStream("two").toList());
  }

  @Test
  public void map_success() {
    Parser<Integer> parser1 = string("123").map(Integer::parseInt);
    assertThat(parser1.parse("123")).isEqualTo(123);
    assertThat(parser1.parseToStream("123")).containsExactly(123);
    assertThat(parser1.parseToStream("")).isEmpty();

    Parser<Boolean> parser2 = string("true").map(Boolean::parseBoolean);
    assertThat(parser2.parse("true")).isTrue();
    assertThat(parser2.parseToStream("true")).containsExactly(true);
    assertThat(parser2.parseToStream("")).isEmpty();
  }

  @Test
  public void map_success_source() {
    Parser<Integer> parser1 = string("123").map(Integer::parseInt);
    assertThat(parser1.source().parse("123")).isEqualTo("123");
    assertThat(parser1.source().parseToStream("123")).containsExactly("123");
    assertThat(parser1.source().parseToStream("")).isEmpty();

    Parser<Boolean> parser2 = string("true").map(Boolean::parseBoolean);
    assertThat(parser2.source().parse("true")).isEqualTo("true");
    assertThat(parser2.source().parseToStream("true")).containsExactly("true");
    assertThat(parser2.source().parseToStream("")).isEmpty();
  }

  @Test
  public void map_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("123").map(Integer::parseInt).parse("123a"));
    assertThrows(
        ParseException.class,
        () -> string("123").map(Integer::parseInt).parseToStream("123a").toList());
  }

  @Test
  public void map_failure() {
    assertThrows(ParseException.class, () -> string("abc").map(Integer::parseInt).parse("def"));
    assertThrows(
        ParseException.class,
        () -> string("abc").map(Integer::parseInt).parseToStream("def").toList());
  }

  @Test
  public void suchThat_parserFails() {
    Set<String> keywords = Set.of("if", "else");
    Parser<String> parser = word().suchThat(keywords::contains, "keyword");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <keyword>, encountered [b]");
  }

  @Test
  public void suchThat_conditionSucceeds() {
    Set<String> magicNumbers = Set.of("888", "911");
    Parser<String> parser = digits().suchThat(magicNumbers::contains, "magic");
    assertThat(parser.parse("888")).isEqualTo("888");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("911 888"))
        .containsExactly("911", "888")
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void suchThat_conditionFails() {
    Parser<Integer> parser =
        string("23").map(Integer::parseInt).suchThat(i -> i > 100, "larger than 100");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("23"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("at 1:1: expecting <larger than 100>, encountered [23]");
  }

  @Test
  public void flatMap_success() {
    Parser<String> parser =
        digits().flatMap(number -> string("=" + number));
    assertThat(parser.parse("123=123")).isEqualTo("=123");
    assertThat(parser.parseToStream("123=123")).containsExactly("=123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void flatMap_success_source() {
    Parser<String> parser =
        digits().flatMap(number -> string("=" + number));
    assertThat(parser.source().parse("123=123")).isEqualTo("123=123");
    assertThat(parser.source().parseToStream("123=123")).containsExactly("123=123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void flatMap_failure_withLeftover() {
    Parser<String> parser =
        digits().flatMap(number -> string("=" + number));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("123=123???"));
    assertThat(thrown).hasMessageThat().contains("at 1:8: expecting <EOF>, encountered [???]");
    assertThrows(ParseException.class, () -> parser.parseToStream("123=123???").toList());
  }

  @Test
  public void flatMap_failure() {
    Parser<String> parser =
        digits().flatMap(number -> string("=" + number));
    assertThrows(ParseException.class, () -> parser.parse("=123"));
    assertThrows(ParseException.class, () -> parser.parseToStream("=123").toList());
    assertThrows(ParseException.class, () -> parser.parse("123=124"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123=124").toList());
  }

  @Test
  public void then_success() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThat(parser.parse("value:123")).isEqualTo(123);
    assertThat(parser.parseToStream("value:123")).containsExactly(123);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void then_success_source() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThat(parser.source().parse("value:123")).isEqualTo("value:123");
    assertThat(parser.source().parseToStream("value:123")).containsExactly("value:123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void then_failure_withLeftover() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:123a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("value:123a").toList());
  }

  @Test
  public void then_failure() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:abc"));
    assertThrows(ParseException.class, () -> parser.parseToStream("value:abc").toList());
    assertThrows(ParseException.class, () -> parser.parse("val:123"));
    assertThrows(ParseException.class, () -> parser.parseToStream("val:123").toList());
  }

  @Test
  public void then_orEmpty_p1Fails() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThrows(ParseException.class, () -> parser.parse("c"));
  }

  @Test
  public void then_orEmpty_p2MatchesZeroTimes() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("a")).isEmpty();
  }

  @Test
  public void then_orEmpty_p2MatchesZeroTimes_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("a")).isEqualTo("a");
  }

  @Test
  public void then_orEmpty_p2MatchesOnce() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("ab")).containsExactly("b");
  }

  @Test
  public void then_orEmpty_p2MatchesOnce_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
  }

  @Test
  public void then_orEmpty_p2MatchesMultipleTimes() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("abb")).containsExactly("b", "b");
  }

  @Test
  public void then_orEmpty_p2MatchesMultipleTimes_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("abb")).isEqualTo("abb");
  }

  @Test
  public void followedBy_success() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThat(parser.parse("123บาท")).isEqualTo("123");
    assertThat(parser.parseToStream("123บาท")).containsExactly("123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void followedBy_success_source() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThat(parser.source().parse("123บาท")).isEqualTo("123บาท");
    assertThat(parser.source().parseToStream("123บาท")).containsExactly("123บาท");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void followedBy_failure_withLeftover() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123บาทa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123บาทa").toList());
  }

  @Test
  public void followedBy_failure() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123baht"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123baht").toList());
    assertThrows(ParseException.class, () -> parser.parse("456บาท"));
    assertThrows(ParseException.class, () -> parser.parseToStream("456บาท").toList());
  }

  @Test
  public void followedByOrEof_suffixMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    assertThat(parser.parse("foobar")).isEqualTo("foo");
  }

  @Test
  public void followedByOrEof_eofMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
  }

  @Test
  public void followedByOrEof_neitherMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("foobaz"));
    assertThat(e).hasMessageThat().contains("at 1:4:");
    assertThat(e).hasMessageThat().contains("expecting <bar>");
    assertThat(e).hasMessageThat().contains("encountered [baz]");
  }

  @Test
  public void followedByOrEof_mainParserFails() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("fobar"));
    assertThat(e).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void optionallyFollowedBy_suffixCannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string("123").optionallyFollowedBy(""));
  }

  @Test
  public void optionallyFollowedBy_success() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.parse("123++")).isEqualTo(124);
    assertThat(parser.parseToStream("123++")).containsExactly(124);
    assertThat(parser.parse("123")).isEqualTo(123);
    assertThat(parser.parseToStream("123")).containsExactly(123);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void optionallyFollowedBy_success_source() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.source().parse("123++")).isEqualTo("123++");
    assertThat(parser.source().parseToStream("123++")).containsExactly("123++");
    assertThat(parser.source().parse("123")).isEqualTo("123");
    assertThat(parser.source().parseToStream("123")).containsExactly("123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void optionallyFollowedBy_unconsumedInput() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("123+"));
    assertThat(thrown).hasMessageThat().contains("at 1:4: expecting <EOF>, encountered [+]");
    assertThrows(ParseException.class, () -> parser.parseToStream("123+").toList());
  }

  @Test
  public void optionallyFollowedBy_failedToMatch() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("abc"));
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <123>, encountered [abc]");
    assertThrows(ParseException.class, () -> parser.parseToStream("abc").toList());
  }

  @Test
  public void optionallyFollowedBy_parserSuffix_suffixExists() {
    Parser<Integer> parser =
        string("123")
            .map(Integer::parseInt)
            .optionallyFollowedBy(
                string("+").then(digits()).map(Integer::parseInt), (n, i) -> n + i);
    assertThat(parser.parse("123+1")).isEqualTo(124);
    assertThat(parser.parseToStream("123+2")).containsExactly(125);
  }

  @Test
  public void optionallyFollowedBy_parserSuffix_suffixDoesNotExist() {
    Parser<Integer> parser =
        string("123")
            .map(Integer::parseInt)
            .optionallyFollowedBy(
                string("+").then(digits()).map(Integer::parseInt), (n, i) -> n + i);
    assertThat(parser.parse("123")).isEqualTo(123);
    assertThat(parser.parseToStream("123+3")).containsExactly(126);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void notFollowedBy_emptySuffix_throws() {
    assertThrows(IllegalArgumentException.class, () -> string("a").notFollowedBy(""));
  }

  @Test
  public void notFollowedBy_selfFailsToMatch() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("c"));
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <a>, encountered [c]");
  }

  @Test
  public void notFollowedBy_suffixFollows() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ab"));
    assertThat(thrown).hasMessageThat().contains("at 1:2: unexpected `b` – [b]");
  }

  @Test
  public void notFollowedBy_suffixDoesNotFollow() {
    assertThat(string("a").notFollowedBy("b").parse("a")).isEqualTo("a");
    assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ac"));
  }

  @Test
  public void notFollowedBy_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notFollowedBy("b");
    assertThat(parser.parse("a")).isEqualTo("a");
    assertThrows(ParseException.class, () -> parser.parse("ac"));
  }

  @Test
  public void notImmediatelyFollowedBy_selfFailsToMatch() {
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("c"));
  }

  @Test
  public void notImmediatelyFollowedBy_suffixFollows() {
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ab"));
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotFollow() {
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").parse("a")).isEqualTo("a");
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ac"));
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notImmediatelyFollowedBy(is('b'), "b");
    assertThat(parser.parse("a")).isEqualTo("a");
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ac"));
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotLiterallyFollow() {
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .parseSkipping(Character::isWhitespace, "a b"))
        .isEqualTo("a");
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotLiterallyFollow_source() {
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .source()
                .parseSkipping(Character::isWhitespace, "a b"))
        .isEqualTo("a b");
  }

  @Test
  public void notFollowedBy_skipping_suffixFollows() {
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(Character::isWhitespace, "a b"));
  }

  @Test
  public void notFollowedBy_skipping_suffixDoesNotFollow() {
    assertThat(string("a").notFollowedBy("b").parseSkipping(Character::isWhitespace, "a")).isEqualTo("a");
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(Character::isWhitespace, "a c"));
  }

  @Test
  public void notFollowedBy_skipping_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notFollowedBy("b");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "a")).isEqualTo("a");
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(Character::isWhitespace, "a c"));
  }

  @Test
  public void expecting_eof() {
    Parser<String> parser = string("f");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(thrown).hasMessageThat().contains("expecting <f>, encountered <EOF>");
  }

  @Test
  public void expecting_differentChar() {
    Parser<String> parser = string("foo");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("bar"));
    assertThat(thrown).hasMessageThat().contains("expecting <foo>, encountered [bar]");
  }

  @Test
  public void simpleCalculator_expectingClosingParen_eof() {
    Parser<Integer> parser = simpleCalculator();
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "(1 + \n( 2 + 3)"));
    assertThat(thrown).hasMessageThat().contains("at 2:9: expecting <)>, encountered <EOF>.");
  }

  @Test
  public void simpleCalculator_expectingClosingParen_differentChar() {
    Parser<Integer> parser = simpleCalculator();
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "(1 + \n( 2 ? 3)"));
    assertThat(thrown).hasMessageThat().contains("at 2:5");
    assertThat(thrown).hasMessageThat().contains("encountered [?...].");
  }

  @Test
  public void sequence_success() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.parse("onetwo")).isEqualTo("1+2=3");
    assertThat(parser.parseToStream("onetwo")).containsExactly("1+2=3");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void sequence_success_source() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.source().parse("onetwo")).isEqualTo("onetwo");
    assertThat(parser.source().parseToStream("onetwo")).containsExactly("onetwo");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void sequence_failure_withLeftover() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("onetwoa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("onetwoa").toList());
  }

  @Test
  public void sequence_failure() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("one-two"));
    assertThrows(ParseException.class, () -> parser.parseToStream("one-two").toList());
  }

  @Test
  public void sequence_orEmpty_leftFails() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThrows(ParseException.class, () -> parser.parseToStream("c").toList());
  }

  @Test
  public void sequence_orEmpty_rightIsEmpty() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.parse("a")).isEqualTo("a[]");
    assertThat(parser.parseToStream("a")).containsExactly("a[]");
  }

  @Test
  public void sequence_orEmpty_rightIsEmpty_source() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
  }

  @Test
  public void sequence_orEmpty_bothSucceed() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.parse("ab")).isEqualTo("a[b]");
    assertThat(parser.parseToStream("ab")).containsExactly("a[b]");
    assertThat(parser.parse("abb")).isEqualTo("a[b, b]");
    assertThat(parser.parseToStream("abb")).containsExactly("a[b, b]");
  }

  @Test
  public void sequence_orEmpty_bothSucceed_source() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
    assertThat(parser.source().parseToStream("ab")).containsExactly("ab");
    assertThat(parser.source().parse("abb")).isEqualTo("abb");
    assertThat(parser.source().parseToStream("abb")).containsExactly("abb");
  }

  @Test
  public void sequence_leftOrEmpty_bothSucceed() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.parse("ab")).isEqualTo("[a]b");
    assertThat(parser.parseToStream("ab")).containsExactly("[a]b");
    assertThat(parser.parse("aab")).isEqualTo("[a, a]b");
    assertThat(parser.parseToStream("aab")).containsExactly("[a, a]b");
  }

  @Test
  public void sequence_leftOrEmpty_bothSucceed_source() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
    assertThat(parser.source().parseToStream("ab")).containsExactly("ab");
    assertThat(parser.source().parse("aab")).isEqualTo("aab");
    assertThat(parser.source().parseToStream("aab")).containsExactly("aab");
  }

  @Test
  public void sequence_leftOrEmpty_leftIsEmpty() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.parse("b")).isEqualTo("[]b");
    assertThat(parser.parseToStream("b")).containsExactly("[]b");
  }

  @Test
  public void sequence_leftOrEmpty_leftIsEmpty_source() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.source().parse("b")).isEqualTo("b");
    assertThat(parser.source().parseToStream("b")).containsExactly("b");
  }

  @Test
  public void sequence_leftOrEmpty_rightFails() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThrows(ParseException.class, () -> parser.parseToStream("c").toList());
  }

  @Test
  public void sequence_bothOrEmpty_bothSucceed() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.parse("ab")).isEqualTo("a:b");
  }

  @Test
  public void sequence_bothOrEmpty_bothSucceed_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.parse("ab")).isEqualTo("a:b");
  }

  @Test
  public void sequence_bothOrEmpty_leftIsEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("b")).isEqualTo("default-a:b");
  }

  @Test
  public void sequence_bothOrEmpty_leftIsEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("b")).isEqualTo("default-a:b");
  }

  @Test
  public void sequence_bothOrEmpty_rightIsEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("a")).isEqualTo("a:default-b");
  }

  @Test
  public void sequence_bothOrEmpty_rightIsEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("a")).isEqualTo("a:default-b");
  }

  @Test
  public void sequence_bothOrEmpty_bothEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void sequence_bothOrEmpty_bothEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void orEmpty_delimitedBy_bothSides() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,bar")).containsExactly("foo", "bar").inOrder();
    assertThat(parser.notEmpty().parse("foo,bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_bothSides_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,bar")).containsExactly("foo", "bar").inOrder();
    assertThat(parser.notEmpty().parse("foo,bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_single() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo")).containsExactly("foo");
    assertThat(parser.notEmpty().parse("foo")).containsExactly("foo");
  }

  @Test
  public void orEmpty_delimitedBy_single_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo")).containsExactly("foo");
    assertThat(parser.notEmpty().parse("foo")).containsExactly("foo");
  }

  @Test
  public void orEmpty_delimitedBy_trailingEmpty() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,")).containsExactly("foo", "").inOrder();
    assertThat(parser.notEmpty().parse("foo,")).containsExactly("foo", "").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_trailingEmpty_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,")).containsExactly("foo", "").inOrder();
    assertThat(parser.notEmpty().parse("foo,")).containsExactly("foo", "").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_leadingEmpty() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse(",bar")).containsExactly("", "bar").inOrder();
    assertThat(parser.notEmpty().parse(",bar")).containsExactly("", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_leadingEmpty_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse(",bar")).containsExactly("", "bar").inOrder();
    assertThat(parser.notEmpty().parse(",bar")).containsExactly("", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_kitchenSink() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse(",foo,bar,,")).containsExactly("", "foo", "bar", "", "").inOrder();
    assertThat(parser.notEmpty().parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_kitchenSink_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse(",foo,bar,,")).containsExactly("", "foo", "bar", "", "").inOrder();
    assertThat(parser.notEmpty().parse(",foo,bar,,")).containsExactly("", "foo", "bar", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_allEmpty() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse(",,,")).containsExactly("", "", "", "");
    assertThat(parser.notEmpty().parse(",,,")).containsExactly("", "", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_allEmpty_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse(",,,")).containsExactly("", "", "", "");
    assertThat(parser.notEmpty().parse(",,,")).containsExactly("", "", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_emptyInput() {
    Parser<List<String>>.OrEmpty parser =
        word().orElse("").delimitedBy(",");
    assertThat(parser.parse("")).containsExactly("");
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void orEmpty_delimitedBy_emptyInput_source() {
    Parser<List<String>>.OrEmpty parser =
        word().source().orElse("").delimitedBy(",");
    assertThat(parser.parse("")).containsExactly("");
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void orEmpty_then_parser_orEmptyMatches() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThat(parser.parse("ab")).isEqualTo("b");
  }

  @Test
  public void orEmpty_then_parser_orEmptyEmitsDefault() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThat(parser.parse("b")).isEqualTo("b");
  }

  @Test
  public void orEmpty_then_parser_suffixFails() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("c"));
  }

  @Test
  public void orEmpty_followedBy_parser_orEmptyMatches() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThat(parser.parse("ab")).isEqualTo("a");
  }

  @Test
  public void orEmpty_followedBy_parser_orEmptyEmitsDefault() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThat(parser.parse("b")).isEqualTo("x");
  }

  @Test
  public void orEmpty_followedBy_parser_suffixFails() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("c"));
  }

  @Test
  public void orEmpty_parseSkipping_emptyInput() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_emptyInput_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSingleSkip() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSingleSkip_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithMultipleSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "   ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "   ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithMultipleSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "   ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "   ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSkipsAndValue() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " foo ")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSkipsAndValue_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " foo ")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithoutSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "fo"));
    assertThrows(
        ParseException.class, () -> parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "fo"));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithoutSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "fo"));
    assertThrows(
        ParseException.class, () -> parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), "fo"));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " fo "));
    assertThrows(
        ParseException.class,
        () -> parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " fo "));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " fo "));
    assertThrows(
        ParseException.class,
        () -> parser.parseSkipping(consecutive(Character::isWhitespace, "skip"), " fo "));
  }

  @Test
  public void or_success() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.parseToStream("bar")).containsExactly("bar");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void or_success_source() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThat(parser.source().parse("foo")).isEqualTo("foo");
    assertThat(parser.source().parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parse("bar")).isEqualTo("bar");
    assertThat(parser.source().parseToStream("bar")).containsExactly("bar");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void or_failure_withLeftover() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThrows(ParseException.class, () -> parser.parse("fooa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("fooa").toList());
    assertThrows(ParseException.class, () -> parser.parse("bara"));
    assertThrows(ParseException.class, () -> parser.parseToStream("bara").toList());
  }

  @Test
  public void or_failure() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThrows(ParseException.class, () -> parser.parse("baz"));
    assertThrows(ParseException.class, () -> parser.parseToStream("baz").toList());
  }

  @Test
  public void or_collectorWithSingleParser_returnsSameInstance() {
    Parser<String> parser = string("a");
    assertThat(Stream.of(parser).collect(Parser.or())).isSameInstanceAs(parser);
  }

  @Test
  public void or_collectorWithEmptyStream_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> Stream.<Parser<String>>of().collect(Parser.or()));
  }

  @Test
  public void or_withOrEmpty() {
    Parser<String>.OrEmpty parser = string("foo").or(string("bar").orElse("default"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.parse("")).isEqualTo("default");
  }

  @Test
  public void anyOf_success() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThat(parser.parse("one")).isEqualTo("one");
    assertThat(parser.parseToStream("one")).containsExactly("one");
    assertThat(parser.parse("two")).isEqualTo("two");
    assertThat(parser.parseToStream("two")).containsExactly("two");
    assertThat(parser.parse("three")).isEqualTo("three");
    assertThat(parser.parseToStream("three")).containsExactly("three");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void anyOf_success_source() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThat(parser.source().parse("one")).isEqualTo("one");
    assertThat(parser.source().parseToStream("one")).containsExactly("one");
    assertThat(parser.source().parse("two")).isEqualTo("two");
    assertThat(parser.source().parseToStream("two")).containsExactly("two");
    assertThat(parser.source().parse("three")).isEqualTo("three");
    assertThat(parser.source().parseToStream("three")).containsExactly("three");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void anyOf_failure_withLeftover() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThrows(ParseException.class, () -> parser.parse("onea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("onea").toList());
    assertThrows(ParseException.class, () -> parser.parse("twoa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("twoa").toList());
    assertThrows(ParseException.class, () -> parser.parse("threea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("threea").toList());
  }

  @Test
  public void anyOf_failure() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThrows(ParseException.class, () -> parser.parse("four"));
    assertThrows(ParseException.class, () -> parser.parseToStream("four").toList());
  }

  @Test
  public void atLeastOnce_success() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThat(parser.parse("a")).containsExactly("a");
    assertThat(parser.parseToStream("a")).containsExactly(List.of("a"));
    assertThat(parser.parse("aa")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa"))
        .containsExactly(List.of("a", "a", "a"))
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();

    Parser<List<String>> parser2 = digits().atLeastOnce();
    assertThat(parser2.parse("1230")).containsExactly("1230");
    assertThat(parser2.parseToStream("1230")).containsExactly(List.of("1230"));
    assertThat(parser2.parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnce_success_source() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
    assertThat(parser.source().parse("aa")).isEqualTo("aa");
    assertThat(parser.source().parseToStream("aa")).containsExactly("aa");
    assertThat(parser.source().parse("aaa")).isEqualTo("aaa");
    assertThat(parser.source().parseToStream("aaa")).containsExactly("aaa");
    assertThat(parser.source().parseToStream("")).isEmpty();

    Parser<List<String>> parser2 = digits().atLeastOnce();
    assertThat(parser2.source().parse("1230")).isEqualTo("1230");
    assertThat(parser2.source().parseToStream("1230")).containsExactly("1230");
    assertThat(parser2.source().parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnce_failure_withLeftover() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("ab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("ab").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void atLeastOnce_failure() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("b").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void atLeastOnce_withReducer_success() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parse("12")).isEqualTo(-1);
    assertThat(parser.parse("123")).isEqualTo(-4);
  }

  @Test
  public void atLeastOnce_withReducer_failure_withLeftover() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parse("12a"));
  }

  @Test
  public void atLeastOnce_withReducer_failure() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("a"));
  }

  @Test
  public void zeroOrMore_between_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_between_zeroMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMore_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_between_oneMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMore_between_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[aa]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[aa]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[aaa]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[aaa]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_between_multipleMatches_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parseToStream("[aa]")).containsExactly("[aa]");
    assertThat(parser.source().parse("[aaa]")).isEqualTo("[aaa]");
    assertThat(parser.source().parseToStream("[aaa]")).containsExactly("[aaa]");
  }

  @Test
  public void zeroOrMore_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedBy_zeroMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMore_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_followedBy_oneMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMore_followedBy_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse("aa;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_followedBy_multipleMatches_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse("aa;")).isEqualTo("aa;");
    assertThat(parser.source().parseToStream("aa;")).containsExactly("aa;");
    assertThat(parser.source().parse("aaa;")).isEqualTo("aaa;");
    assertThat(parser.source().parseToStream("aaa;")).containsExactly("aaa;");
  }

  @Test
  public void zeroOrMore_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_betweenParsers_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMore_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_betweenParsers_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMore_betweenParsers_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[aa]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[aa]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[aaa]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[aaa]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_betweenParsers_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parseToStream("[aa]")).containsExactly("[aa]");
    assertThat(parser.source().parse("[aaa]")).isEqualTo("[aaa]");
    assertThat(parser.source().parseToStream("[aaa]")).containsExactly("[aaa]");
  }

  @Test
  public void zeroOrMore_followedByParser_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedByParser_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMore_followedByParser_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_followedByParser_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMore_followedByParser_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse("aa;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_followedByParser_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse("aa;")).isEqualTo("aa;");
    assertThat(parser.source().parseToStream("aa;")).containsExactly("aa;");
    assertThat(parser.source().parse("aaa;")).isEqualTo("aaa;");
    assertThat(parser.source().parseToStream("aaa;")).containsExactly("aaa;");
  }

  @Test
  public void zeroOrMore_between_failure() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[ab]"));
    assertThrows(ParseException.class, () -> parser.parse("[a]b"));
  }

  @Test
  public void zeroOrMore_parseEmpty() {
    assertThat(string("a").zeroOrMore().parse("")).isEmpty();
  }

  @Test
  public void zeroOrMore_parseEmpty_source() {
    assertThat(string("a").source().zeroOrMore().parse("")).isEmpty();
  }

  @Test
  public void zeroOrMore_parseNonEmpty() {
    assertThat(string("a").zeroOrMore().parse("aa")).containsExactly("a", "a").inOrder();
  }

  @Test
  public void zeroOrMore_parseNonEmpty_source() {
    assertThat(string("a").source().zeroOrMore().parse("aa")).containsExactly("a", "a").inOrder();
  }

  @Test
  public void zeroOrMore_parseFail() {
    assertThrows(ParseException.class, () -> string("a").zeroOrMore().parse("b"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[a,a]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[a,a,a]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a,a]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[a,a]")).isEqualTo("[a,a]");
    assertThat(parser.source().parseToStream("[a,a]")).containsExactly("[a,a]");
    assertThat(parser.source().parse("[a,a,a]")).isEqualTo("[a,a,a]");
    assertThat(parser.source().parseToStream("[a,a,a]")).containsExactly("[a,a,a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse("a,a;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse("a,a;")).isEqualTo("a,a;");
    assertThat(parser.source().parseToStream("a,a;")).containsExactly("a,a;");
    assertThat(parser.source().parse("a,a,a;")).isEqualTo("a,a,a;");
    assertThat(parser.source().parseToStream("a,a,a;")).containsExactly("a,a,a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[a,a]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[a,a,a]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a,a]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[a,a]")).isEqualTo("[a,a]");
    assertThat(parser.source().parseToStream("[a,a]")).containsExactly("[a,a]");
    assertThat(parser.source().parse("[a,a,a]")).isEqualTo("[a,a,a]");
    assertThat(parser.source().parseToStream("[a,a,a]")).containsExactly("[a,a,a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse("a,a;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse("a,a;")).isEqualTo("a,a;");
    assertThat(parser.source().parseToStream("a,a;")).containsExactly("a,a;");
    assertThat(parser.source().parse("a,a,a;")).isEqualTo("a,a,a;");
    assertThat(parser.source().parseToStream("a,a,a;")).containsExactly("a,a,a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_failure() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[a,b]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[,a]"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseEmpty() {
    assertThat(string("a").zeroOrMoreDelimitedBy(",").parse("")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseEmpty_source() {
    assertThat(string("a").source().zeroOrMoreDelimitedBy(",").parse("")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseNonEmpty() {
    assertThat(string("a").zeroOrMoreDelimitedBy(",").parse("a,a"))
        .containsExactly("a", "a")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseNonEmpty_source() {
    assertThat(string("a").source().zeroOrMoreDelimitedBy(",").parse("a,a"))
        .containsExactly("a", "a")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseFail() {
    assertThrows(ParseException.class, () -> string("a").zeroOrMoreDelimitedBy(",").parse("b"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter() {
    Parser<List<String>> parser =
        digits()
            .zeroOrMoreDelimitedBy(",")
            .followedBy(string(",").optional())
            .notEmpty();
    assertThat(parser.parse(",")).isEmpty();
    assertThat(parser.parse("1")).containsExactly("1");
    assertThat(parser.parse("1,")).containsExactly("1");
    assertThat(parser.parse("1,2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1,2,")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1,2,3")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter_source() {
    Parser<List<String>> parser =
        digits()
            .zeroOrMoreDelimitedBy(",")
            .followedBy(string(",").optional())
            .notEmpty();
    assertThat(parser.source().parse(",")).isEqualTo(",");
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parse("1,")).isEqualTo("1,");
    assertThat(parser.source().parse("1,2")).isEqualTo("1,2");
    assertThat(parser.source().parse("1,2,")).isEqualTo("1,2,");
    assertThat(parser.source().parse("1,2,3")).isEqualTo("1,2,3");
    assertThat(parser.source().parse("1,2,3,")).isEqualTo("1,2,3,");
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter_failOnEmpty() {
    Parser<List<String>> parser =
        digits()
            .zeroOrMoreDelimitedBy(",")
            .followedBy(string(",").optional())
            .notEmpty();
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered <EOF>");
  }

  @Test
  public void zeroOrMoreDelimited_empty() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedStringWithEscapes('"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{}")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimited_single() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedStringWithEscapes('"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{k:\"v\"}")).containsExactly("k", "v");
  }

  @Test
  public void zeroOrMoreDelimited_multiple() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedStringWithEscapes('"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{k1:\"v1\",k2:\"v2\"}"))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimited_skippingWhitespace() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedStringWithEscapes('"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.skipping(Character::isWhitespace).parse(" { k1 : \"v1\" , k2 : \"v2\" } "))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimited_withTrailingComma() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedStringWithEscapes('"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .followedBy(string(",").optional())
            .between("{", "}");
    assertThat(parser.skipping(Character::isWhitespace).parse(" { k1 : \"v1\" , k2 : \"v2\", } "))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_empty() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .between("{", "}");
    assertThat(parser.parse("{}")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_singleWithNoValue() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .between("{", "}");
    assertThat(parser.parse("{a}")).containsExactly("a", 0);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_singleWithValue() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .between("{", "}");
    assertThat(parser.parse("{a=1}")).containsExactly("a", 1);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_multiple() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .between("{", "}");
    assertThat(parser.parse("{a=1,b,c=3}")).containsExactly("a", 1, "b", 0, "c", 3);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_skippingWhitespace() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .between("{", "}");
    assertThat(parser.skipping(Character::isWhitespace).parse(" { a=1 , b , c=3 } "))
        .containsExactly("a", 1, "b", 0, "c", 3);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_withTrailingComma() {
    Parser<Map<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                toMap())
            .followedBy(string(",").optional())
            .between("{", "}");
    assertThat(parser.skipping(Character::isWhitespace).parse(" { a=1 , b , c=3, } "))
        .containsExactly("a", 1, "b", 0, "c", 3)
        .inOrder();
  }

  @Test
  public void optional_between_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_between_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void optional_between_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_between_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void optional_followedBy_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedBy_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void optional_followedBy_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedBy_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void optional_betweenParsers_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_betweenParsers_zeroMatch_source() {
    Parser<Optional<String>> parser =
        string("a").source().optional().between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void optional_betweenParsers_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_betweenParsers_oneMatch_source() {
    Parser<Optional<String>> parser =
        string("a").source().optional().between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void optional_followedByParser_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedByParser_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void optional_followedByParser_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedByParser_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void optional_parseEmpty() {
    assertThat(string("a").optional().parse("")).isEmpty();
  }

  @Test
  public void optional_parseEmpty_source() {
    assertThat(string("a").source().optional().parse("")).isEmpty();
  }

  @Test
  public void optional_parseNonEmpty() {
    assertThat(string("a").optional().parse("a")).hasValue("a");
  }

  @Test
  public void optional_parseNonEmpty_source() {
    assertThat(string("a").source().optional().parse("a")).hasValue("a");
  }

  @Test
  public void optional_parseFail() {
    assertThrows(ParseException.class, () -> string("a").optional().parse("b"));
  }

  @Test
  public void optional_withLeftOverInput() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").optional().parse("a bc"));
    assertThat(thrown).hasMessageThat().contains("1:2: expecting <EOF>, encountered [ bc]");
  }

  @Test
  public void orElse_between_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_between_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_between_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_between_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void orElse_followedBy_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedBy_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void orElse_followedBy_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_followedBy_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void orElse_betweenParsers_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_betweenParsers_zeroMatch_source() {
    Parser<String> parser =
        string("a").source().orElse("default").between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_betweenParsers_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_betweenParsers_oneMatch_source() {
    Parser<String> parser =
        string("a").source().orElse("default").between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void orElse_followedByParser_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedByParser_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void orElse_followedByParser_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_followedByParser_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void orElse_parseEmpty() {
    assertThat(string("a").orElse("default").parse("")).isEqualTo("default");
  }

  @Test
  public void orElse_parseEmpty_source() {
    assertThat(string("a").source().orElse("default").parse("")).isEqualTo("default");
  }

  @Test
  public void orElse_parseNonEmpty() {
    assertThat(string("a").orElse("default").parse("a")).isEqualTo("a");
  }

  @Test
  public void orElse_parseNonEmpty_source() {
    assertThat(string("a").source().orElse("default").parse("a")).isEqualTo("a");
  }

  @Test
  public void orElse_parseFail() {
    assertThrows(ParseException.class, () -> string("a").orElse("default").parse("b"));
  }

  @Test
  public void orElse_nullDefault_between_zeroMatch() {
    Parser<String> parser = string("a").orElse(null).between("[", "]");
    assertThat(parser.parse("[]")).isNull();
    assertThat(parser.parseToStream("[]")).containsExactly((String) null);
  }

  @Test
  public void orElse_nullDefault_between_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse(null).between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_nullDefault_parseEmpty() {
    assertThat(string("a").orElse(null).parse("")).isNull();
  }

  @Test
  public void orElse_nullDefault_parseEmpty_source() {
    assertThat(string("a").source().orElse(null).parse("")).isNull();
  }

  @Test
  public void atLeastOnceDelimitedBy_success() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThat(parser.parse("a")).containsExactly("a");
    assertThat(parser.parseToStream("a")).containsExactly(List.of("a"));
    assertThat(parser.parse("a,a")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a"))
        .containsExactly(List.of("a", "a", "a"))
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnceDelimitedBy_success_source() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
    assertThat(parser.source().parse("a,a")).isEqualTo("a,a");
    assertThat(parser.source().parseToStream("a,a")).containsExactly("a,a");
    assertThat(parser.source().parse("a,a,a")).isEqualTo("a,a,a");
    assertThat(parser.source().parseToStream("a,a,a")).containsExactly("a,a,a").inOrder();
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnceDelimitedBy_failure_withLeftover() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThrows(ParseException.class, () -> parser.parse("aa"));
    assertThrows(ParseException.class, () -> parser.parse("a,ab"));
  }

  @Test
  public void atLeastOnceDelimitedBy_failure() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("b").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,").toList());
    assertThrows(ParseException.class, () -> parser.parse(",a"));
    assertThrows(ParseException.class, () -> parser.parseToStream(",a").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,b").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,,a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,,a").toList());
  }

  @Test
  public void atLeastOnceDelimitedBy_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string("a").atLeastOnceDelimitedBy(""));
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    assertThat(parser.parse("12")).containsExactly("12");
    assertThat(parser.parse("12,")).containsExactly("12");
    assertThat(parser.parse("1,23")).containsExactly("1", "23").inOrder();
    assertThat(parser.parse("1,23,")).containsExactly("1", "23").inOrder();
    assertThat(parser.parse("1,2,3")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,4")).containsExactly("1", "2", "3", "4").inOrder();
    assertThat(parser.parse("1,2,3,4,")).containsExactly("1", "2", "3", "4").inOrder();
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_source() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    assertThat(parser.source().parse("12")).isEqualTo("12");
    assertThat(parser.source().parse("12,")).isEqualTo("12,");
    assertThat(parser.source().parse("1,23")).isEqualTo("1,23");
    assertThat(parser.source().parse("1,23,")).isEqualTo("1,23,");
    assertThat(parser.source().parse("1,2,3")).isEqualTo("1,2,3");
    assertThat(parser.source().parse("1,2,3,")).isEqualTo("1,2,3,");
    assertThat(parser.source().parse("1,2,3,4")).isEqualTo("1,2,3,4");
    assertThat(parser.source().parse("1,2,3,4,")).isEqualTo("1,2,3,4,");
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_onlyTrailingDelimiter() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(","));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered [,]");
  }

  @Test
  public void atLeastOnceDelimitedBy_withTrailingDelimiter_emptyInput() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered <EOF>");
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_success() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parse("1,2")).isEqualTo(-1);
    assertThat(parser.parse("1,2,3")).isEqualTo(-4);
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_failure_withLeftover() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parse("1,2a"));
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_failure() {
    Parser<Integer> integer = single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("1,"));
    assertThrows(ParseException.class, () -> parser.parse(",1"));
    assertThrows(ParseException.class, () -> parser.parse("1,,2"));
  }

  @Test
  public void between_success() {
    Parser<String> parser = string("content").between("[", "]");
    assertThat(parser.parse("[content]")).isEqualTo("content");
    assertThat(parser.parseToStream("[content]")).containsExactly("content");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void between_success_source() {
    Parser<String> parser = string("content").between("[", "]");
    assertThat(parser.source().parse("[content]")).isEqualTo("[content]");
    assertThat(parser.source().parseToStream("[content]")).containsExactly("[content]");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void between_failure_withLeftover() {
    Parser<String> parser = string("content").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[content]a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[content]a").toList());
  }

  @Test
  public void between_failure() {
    Parser<String> parser = string("content").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("content]"));
    assertThrows(ParseException.class, () -> parser.parseToStream("content]").toList());
    assertThrows(ParseException.class, () -> parser.parse("[content"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[content").toList());
    assertThrows(ParseException.class, () -> parser.parse("content"));
    assertThrows(ParseException.class, () -> parser.parseToStream("content").toList());
    assertThrows(ParseException.class, () -> parser.parse("[wrong]"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[wrong]").toList());
    assertThrows(ParseException.class, () -> parser.parse(" [content]"));
    assertThrows(ParseException.class, () -> parser.parseToStream(" [content]").toList());
  }

  @Test
  public void between_cannotBeEmpty() {
    Parser<String> parser = string("content");
    assertThrows(IllegalArgumentException.class, () -> parser.between("", "]"));
    assertThrows(IllegalArgumentException.class, () -> parser.between("[", ""));
  }

  @Test
  public void between_orEmpty_success() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(Character::isWhitespace, "ignore"), zeroOrMore(Character::isWhitespace, "ignore"));
    assertThat(parser.parse("content")).isEqualTo("content");
    assertThat(parser.parse(" content")).isEqualTo("content");
    assertThat(parser.parse("content ")).isEqualTo("content");
    assertThat(parser.parse(" content ")).isEqualTo("content");
    assertThat(parser.parse("  content  ")).isEqualTo("content");
  }

  @Test
  public void between_orEmpty_success_source() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(Character::isWhitespace, "ignore"), zeroOrMore(Character::isWhitespace, "ignore"));
    assertThat(parser.source().parse("content")).isEqualTo("content");
    assertThat(parser.source().parse(" content")).isEqualTo(" content");
    assertThat(parser.source().parse("content ")).isEqualTo("content ");
    assertThat(parser.source().parse(" content ")).isEqualTo(" content ");
    assertThat(parser.source().parse("  content  ")).isEqualTo("  content  ");
  }

  @Test
  public void between_orEmpty_failure() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(Character::isWhitespace, "ignore"), zeroOrMore(Character::isWhitespace, "ignore"));
    assertThrows(ParseException.class, () -> parser.parse("Content"));
    assertThrows(ParseException.class, () -> parser.parse(" contentX"));
  }

  @Test
  public void orEmpty_between_orEmpty_success() {
    Parser<String>.OrEmpty parser =
        zeroOrMore(is('a'), "a's")
            .between(zeroOrMore(Character::isWhitespace, "ignore"), zeroOrMore(Character::isWhitespace, "ignore"));
    assertThat(parser.parse("aa")).isEqualTo("aa");
    assertThat(parser.parse(" aa")).isEqualTo("aa");
    assertThat(parser.parse("aa ")).isEqualTo("aa");
    assertThat(parser.parse(" aa ")).isEqualTo("aa");
    assertThat(parser.parse("  ")).isEmpty();
    assertThat(parser.parse("")).isEmpty();
  }

  @Test
  public void orEmpty_between_orEmpty_failure() {
    Parser<String>.OrEmpty parser =
        zeroOrMore(is('a'), "a's")
            .between(zeroOrMore(Character::isWhitespace, "ignore"), zeroOrMore(Character::isWhitespace, "ignore"));
    assertThrows(ParseException.class, () -> parser.parse("a a"));
  }

  @Test
  public void orEmpty_optionallyFollowedBy_suffix() {
    Parser<String> parser = zeroOrMore(is('a'), "a's").optionallyFollowedBy(",").between("[", "]");
    assertThat(parser.parse("[aa,]")).isEqualTo("aa");
    assertThat(parser.parse("[aa]")).isEqualTo("aa");
    assertThat(parser.parse("[,]")).isEmpty();
    assertThat(parser.parse("[]")).isEmpty();
  }

  @Test
  public void orEmpty_optionallyFollowedBy_suffix_source() {
    Parser<String> parser = zeroOrMore(is('a'), "a's").optionallyFollowedBy(",").between("[", "]");
    assertThat(parser.source().parse("[aa,]")).isEqualTo("[aa,]");
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parse("[,]")).isEqualTo("[,]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
  }

  @Test
  public void orEmpty_immediatelyBetween_success() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[foo]")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_immediatelyBetween_success_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[foo]")).isEqualTo("[foo]");
  }

  @Test
  public void orEmpty_immediatelyBetween_emptyContent() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[]")).isEqualTo("");
  }

  @Test
  public void orEmpty_immediatelyBetween_emptyContent_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
  }

  @Test
  public void orEmpty_immediatelyBetween_prefixMismatch_throws() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("foo]"));
  }

  @Test
  public void orEmpty_immediatelyBetween_suffixMismatch_throws() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[foo"));
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_aroundQuotes() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_aroundQuotes_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("[foo]");
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacesInsideQuotes() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo(" foo ");
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacesInsideQuotes_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("[ foo ]");
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spaceFollowingPrefixNotIgnored() {
    Parser<String> parser = zeroOrMore(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " [ foo] "));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("encountered [ foo]...");
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacePrecedingSuffixNotIgnored() {
    Parser<String> parser = zeroOrMore(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " [foo ] "));
    assertThat(thrown).hasMessageThat().contains("1:6");
    assertThat(thrown).hasMessageThat().contains("encountered [ ]...");
  }

  @Test
  public void parser_immediatelyBetween_success() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[foo]")).isEqualTo("foo");
  }

  @Test
  public void parser_immediatelyBetween_success_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[foo]")).isEqualTo("[foo]");
  }

  @Test
  public void parser_immediatelyBetween_mainParserFails_throws() {
    Parser<String> parser = word().immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[!123]"));
  }

  @Test
  public void parser_immediatelyBetween_prefixMismatch_throws() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("foo]"));
  }

  @Test
  public void parser_immediatelyBetween_suffixMismatch_throws() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[foo"));
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_aroundQuotes() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("foo");
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_aroundQuotes_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("[foo]");
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacesInsideQuotes() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo(" foo ");
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacesInsideQuotes_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("[ foo ]");
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spaceFollowingPrefixNotIgnored() {
    Parser<String> parser = consecutive(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " [ foo] "));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("encountered [ foo]...");
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacePrecedingSuffixNotIgnored() {
    Parser<String> parser = consecutive(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " [foo ] "));
    assertThat(thrown).hasMessageThat().contains("1:6");
    assertThat(thrown).hasMessageThat().contains("encountered [ ]...");
  }

  @Test
  public void single_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parseToStream("1")).containsExactly('1');
    assertThat(parser.parse("9")).isEqualTo('9');
    assertThat(parser.parseToStream("9")).containsExactly('9');
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void single_success_source() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parse("9")).isEqualTo("9");
    assertThat(parser.source().parseToStream("9")).containsExactly("9");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void single_failure_withLeftover() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("1a").toList());
  }

  @Test
  public void single_failure() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("12"));
  }

  @Test
  public void quotedBy_emptyDelimiters_throws() {
    assertThrows(IllegalArgumentException.class, () -> quotedBy("", "}"));
    assertThrows(IllegalArgumentException.class, () -> quotedBy("{", ""));
  }

  @Test
  public void quotedBy_emptyContent() {
    assertThat(quotedBy('{', '}').parse("{}")).isEmpty();
    assertThat(quotedBy('{', '}').source().parse("{}")).isEqualTo("{}");
  }

  @Test
  public void quotedBy_success() {
    assertThat(quotedBy('{', '}').parse("{foo}")).isEqualTo("foo");
    assertThat(quotedBy('{', '}').source().parse("{foo}")).isEqualTo("{foo}");
  }

  @Test
  public void quotedBy_beforeNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy('{', '}').parse("a}"));
    assertThat(e).hasMessageThat().contains("1:1");
    assertThat(e).hasMessageThat().contains("expecting <{>");
  }

  @Test
  public void quotedBy_afterNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy('{', '}').parse("{a"));
    assertThat(e).hasMessageThat().contains("1:2");
    assertThat(e).hasMessageThat().contains("expecting <}>");
  }

  @Test
  public void quotedBy_withSkipping() {
    assertThat(quotedBy('{', '}').parseSkipping(Character::isWhitespace, " { foo } "))
        .isEqualTo(" foo ");
    assertThat(quotedBy('{', '}').source().parseSkipping(Character::isWhitespace, " { foo } "))
        .isEqualTo("{ foo }");
  }

  @Test
  public void quotedBy_stringDelimiters_success() {
    assertThat(quotedBy("{{", "}}").parse("{{foo}}")).isEqualTo("foo");
    assertThat(quotedBy("{{", "}}").source().parse("{{foo}}")).isEqualTo("{{foo}}");
  }

  @Test
  public void quotedBy_stringDelimiters_partialDelimiterInContent() {
    assertThat(quotedBy("{{", "}}").parse("{{f}o{o}}")).isEqualTo("f}o{o");
    assertThat(quotedBy("{{", "}}").source().parse("{{f}o{o}}")).isEqualTo("{{f}o{o}}");
  }

  @Test
  public void quotedBy_stringDelimiters_emptyContent() {
    assertThat(quotedBy("{{", "}}").parse("{{}}")).isEmpty();
    assertThat(quotedBy("{{", "}}").source().parse("{{}}")).isEqualTo("{{}}");
  }

  @Test
  public void quotedBy_stringDelimiters_beforeNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy("{{", "}}").parse("{a}}"));
    assertThat(e).hasMessageThat().contains("1:1");
    assertThat(e).hasMessageThat().contains("expecting <{{>");
  }

  @Test
  public void quotedBy_stringDelimiters_afterNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy("{{", "}}").parse("{{a"));
    assertThat(e).hasMessageThat().contains("1:3");
    assertThat(e).hasMessageThat().contains("expecting <}}>");
  }

  @Test
  public void quotedBy_stringDelimiters_withSkipping() {
    assertThat(quotedBy("{{", "}}").parseSkipping(Character::isWhitespace, " {{ foo }} "))
        .isEqualTo(" foo ");
    assertThat(quotedBy("{{", "}}").source().parseSkipping(Character::isWhitespace, " {{ foo }} "))
        .isEqualTo("{{ foo }}");
  }

  @Test
  public void quotedStringWithEscapes_singleQuote_success() {
    Parser<String> singleQuoted = Parser.quotedStringWithEscapes('\'', chars(1));
    assertThat(singleQuoted.parse("''")).isEmpty();
    assertThat(singleQuoted.parse("'foo'")).isEqualTo("foo");
    assertThat(singleQuoted.parse("'foo\\'s'")).isEqualTo("foo's");
    assertThat(singleQuoted.parse("'foo\\\\bar'")).isEqualTo("foo\\bar");
    assertThat(singleQuoted.parse("'\\''")).isEqualTo("'");
    assertThat(singleQuoted.parse("'\\\\'")).isEqualTo("\\");
  }

  @Test
  public void quotedStringWithEscapes_doubleQuote_success() {
    Parser<String> doubleQuoted = Parser.quotedStringWithEscapes('"', chars(1));
    assertThat(doubleQuoted.parse("\"\"")).isEmpty();
    assertThat(doubleQuoted.parse("\"bar\"")).isEqualTo("bar");
    assertThat(doubleQuoted.parse("\"bar\\\"baz\"")).isEqualTo("bar\"baz");
    assertThat(doubleQuoted.parse("\"bar\\\\baz\"")).isEqualTo("bar\\baz");
  }

  @Test
  public void quotedStringWithEscapes_failures() {
    Parser<String> singleQuoted = Parser.quotedStringWithEscapes('\'', chars(1));
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo")); // unclosed
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo'bar")); // leftover
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo\\")); // dangling escape
  }

  @Test
  public void quotedStringWithEscapes_invalidQuoteChar_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Parser.quotedStringWithEscapes('\\', chars(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Parser.quotedStringWithEscapes('\n', chars(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Parser.quotedStringWithEscapes('\r', chars(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> Parser.quotedStringWithEscapes('\t', chars(1)));
  }

  @Test
  public void quotedStringWithEscapes_unicodeEscape_success() {
    Parser<String> unicodeEscaped = string("u").then(codePoint()).map(Character::toString);
    Parser<String> quotedString = Parser.quotedStringWithEscapes('\'', unicodeEscaped.or(chars(1)));
    assertThat(quotedString.parse("''")).isEmpty();
    assertThat(quotedString.parse("'emoji: \\uD83D\\uDE00'")).isEqualTo("emoji: 😀");
  }

  @Test
  public void codePoint_emoji() {
    assertThat(codePoint().map(Character::toString).zeroOrMore(joining()).parse("d83dDE00"))
        .isEqualTo("😀");
  }

  @Test
  public void consecutive_success() {
    Parser<String> parser = digits();
    assertThat(parser.parse("1")).isEqualTo("1");
    assertThat(parser.parseToStream("1")).containsExactly("1");
    assertThat(parser.parse("123")).isEqualTo("123");
    assertThat(parser.parseToStream("123")).containsExactly("123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_success_source() {
    Parser<String> parser = digits();
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parse("123")).isEqualTo("123");
    assertThat(parser.source().parseToStream("123")).containsExactly("123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_failure_withLeftover() {
    Parser<String> parser = digits();
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("1a").toList());
    assertThrows(ParseException.class, () -> parser.parse("123a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123a").toList());
  }

  @Test
  public void consecutive_failure() {
    Parser<String> parser = digits();
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("12a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("12a").toList());
    assertThrows(ParseException.class, () -> parser.parse(""));
  }

  @Test
  public void consecutive_exactNTimes_unicodeEscapeExample() {
    Parser<Integer> uncodeEscape =
        string("\\u")
            .then(chars(4))
            .suchThat(charsIn("[0-9A-F]")::matchesAllOf, "4 hex")
            .map(hex -> Integer.parseInt(hex, 16));
    assertThat(uncodeEscape.parseToStream("\\uD83D\\uDE00"))
        .containsExactly(0xD83D, 0xDE00)
        .inOrder();
  }

  @Test
  public void chars_zeroTimes_fails() {
    assertThrows(IllegalArgumentException.class, () -> chars(0));
  }

  @Test
  public void chars_notSufficientChars_fails() {
    Parser<String> parser = chars(2);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(thrown).hasMessageThat().contains("1:1: expecting <2 char(s)>, encountered [a]");
  }

  @Test
  public void chars_sufficientChars_succeeds() {
    assertThat(chars(2).parse("ab")).isEqualTo("ab");
  }

  @Test
  public void chars_moreThanSufficientChars_succeeds() {
    assertThat(chars(2).parseToStream("abcd")).containsExactly("ab", "cd").inOrder();
  }

  @Test
  public void chars_source_moreThanSufficientChars_succeeds() {
    assertThat(chars(2).source().parseToStream("abcd")).containsExactly("ab", "cd").inOrder();
  }

  @Test
  public void chars_skipping() {
    assertThat(chars(2).skipping(Character::isWhitespace).parseToStream(" ab cd"))
        .containsExactly("ab", "cd")
        .inOrder();
    assertThat(chars(2).parseToStream(" ab cd")).containsExactly(" a", "b ", "cd").inOrder();
    assertThat(literally(chars(2)).skipping(Character::isWhitespace).parseToStream(" ab cd"))
        .containsExactly(" a", "b ", "cd")
        .inOrder();
  }

  @Test
  public void oneOrMoreCharsIn_positiveCharSet_parseSuccess() {
    Parser<String> parser = consecutive(charsIn("[a-fA-F-_]"));
    assertThat(parser.parse("abf-_F")).isEqualTo("abf-_F");
  }

  @Test
  public void oneOrMoreCharsIn_negativeCharSet_parseSuccess() {
    Parser<String> parser = consecutive(charsIn("[^\"{}]"));
    assertThat(parser.parse("zzZ")).isEqualTo("zzZ");
  }

  @Test
  public void oneOrMoreCharsIn_positiveCharSet_parseFailure() {
    Parser<String> parser = consecutive(charsIn("[a-fA-F-_]"));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThat(thrown).hasMessageThat().contains("expecting <one or more [a-fA-F-_]>");
  }

  @Test
  public void oneOrMoreCharsIn_negativeCharSet_parseFailure() {
    Parser<String> parser = consecutive(charsIn("[^\"{}]"));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("{"));
    assertThat(thrown).hasMessageThat().contains("expecting <one or more [^\"{}]>");
  }

  @Test
  public void oneOrMoreCharsIn_emptyCharSet_parseFails() {
    Parser<String> parser = consecutive(charsIn("[]"));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(thrown).hasMessageThat().contains("expecting <one or more []>");
  }

  @Test
  public void oneOrMoreCharsIn_emptyNegativeCharSet_parseSucceeds() {
    Parser<String> parser = consecutive(charsIn("[^]"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
  }

  @Test
  public void prefix_zeroOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void prefix_zeroOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void prefix_oneOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.parse("-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-10")).containsExactly(-10);
  }

  @Test
  public void prefix_oneOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.source().parse("-10")).isEqualTo("-10");
    assertThat(parser.source().parseToStream("-10")).containsExactly("-10");
  }

  @Test
  public void prefix_multipleOperators_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = number.prefix(op);
    assertThat(parser.parse("--10")).isEqualTo(10);
    assertThat(parser.parse("-~10")).isEqualTo(-(~10));
    assertThat(parser.parse("~-10")).isEqualTo(~(-10));
    assertThat(parser.parseToStream("--10")).containsExactly(10);
    assertThat(parser.parse("-+10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-+10")).containsExactly(-10);
    assertThat(parser.parse("+-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("+-10")).containsExactly(-10);
  }

  @Test
  public void prefix_multipleOperators_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = number.prefix(op);
    assertThat(parser.source().parse("--10")).isEqualTo("--10");
    assertThat(parser.source().parse("-~10")).isEqualTo("-~10");
    assertThat(parser.source().parse("~-10")).isEqualTo("~-10");
    assertThat(parser.source().parseToStream("--10")).containsExactly("--10");
    assertThat(parser.source().parse("-+10")).isEqualTo("-+10");
    assertThat(parser.source().parseToStream("-+10")).containsExactly("-+10");
    assertThat(parser.source().parse("+-10")).isEqualTo("+-10");
    assertThat(parser.source().parseToStream("+-10")).containsExactly("+-10");
  }

  @Test
  public void prefix_operandParseFails() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-a").toList());
  }

  @Test
  public void prefix_failure_withLeftover() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThrows(ParseException.class, () -> parser.parse("10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-10a").toList());
  }

  @Test
  public void postfix_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++")).containsExactly(11);
    assertThat(parser.parse("10--")).isEqualTo(9);
    assertThat(parser.parseToStream("10--")).containsExactly(9);
    assertThat(parser.parse("10++--++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++--++")).containsExactly(11);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void postfix_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parse("10++")).isEqualTo("10++");
    assertThat(parser.source().parseToStream("10++")).containsExactly("10++");
    assertThat(parser.source().parse("10--")).isEqualTo("10--");
    assertThat(parser.source().parseToStream("10--")).containsExactly("10--");
    assertThat(parser.source().parse("10++--++")).isEqualTo("10++--++");
    assertThat(parser.source().parseToStream("10++--++")).containsExactly("10++--++");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void postfix_failure() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThrows(ParseException.class, () -> parser.parse("a++"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a++").toList());
    assertThrows(ParseException.class, () -> parser.parse("10+"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10+").toList());
  }

  @Test
  public void postfix_failure_withLeftover() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThrows(ParseException.class, () -> parser.parse("10++a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10++a").toList());
    assertThrows(ParseException.class, () -> parser.parse("10 a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10 a").toList());
  }

  @Test
  public void postfix_withBiFunction_success() {
    Parser<Integer> parser =
        digits().map(Integer::parseInt).postfix(string("++").map(s -> 1), (a, b) -> a + b);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parse("10++++")).isEqualTo(12);
  }

  @Test
  public void postfix_withBiFunction_failure() {
    Parser<Integer> parser =
        digits().map(Integer::parseInt).postfix(string("!").map(s -> 1), (a, b) -> a + b);
    assertThrows(ParseException.class, () -> parser.parse("10!a"));
  }

  @Test
  public void parse_fromIndex() {
    assertThat(string("bar").parse("foobar", 3)).isEqualTo("bar");
    assertThat(string("bar").source().parse("foobar", 3)).isEqualTo("bar");
    assertThat(digits().skipping(Character::isWhitespace).parse("a 123", 1))
        .isEqualTo("123");
    assertThat(digits().source().skipping(Character::isWhitespace).parse("a 123", 1))
        .isEqualTo("123");
  }

  @Test
  public void parse_fromIndex_atEnd() {
    assertThrows(ParseException.class, () -> string("a").parse("a", 1));
    assertThrows(ParseException.class, () -> string("a").skipping(Character::isWhitespace).parse("a  ", 1));
  }

  @Test
  public void parse_fromIndex_outOfBounds() {
    assertThrows(IndexOutOfBoundsException.class, () -> string("a").parse("a", 2));
    assertThrows(
        IndexOutOfBoundsException.class, () -> string("a").skipping(Character::isWhitespace).parse("a", 2));
  }

  @Test
  public void skipping_aroundIdentifier() {
    Parser<String> parser = string("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo \n ")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo \n  ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "   foo   ")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("   foo   ")).containsExactly("foo");
  }

  @Test
  public void skipping_aroundIdentifier_withReader() {
    Parser<String> parser = string("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(new StringReader("foo")))
        .containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(new StringReader(" foo")))
        .containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(new StringReader("foo \n  ")))
        .containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(new StringReader(" foo ")))
        .containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(new StringReader("   foo   ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_aroundIdentifier_source() {
    Parser<String> parser = string("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foo \n ")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo \n  "))
        .containsExactly("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo "))
        .containsExactly("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "   foo   ")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("   foo   "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_parseToStream_allCharactersSkipped() {
    assertThat(digits().skipping(Character::isWhitespace).parseToStream("     ")).isEmpty();
  }

  @Test
  public void skipping_parseToStream_reader_allCharactersSkipped() {
    assertThat(
            digits()
                .skipping(Character::isWhitespace)
                .parseToStream(new StringReader("     ")))
        .isEmpty();
  }

  @Test
  public void skipping_parseToStream_allSkippablePatternsSkipped() {
    assertThat(
            digits()
                .skipping(
                    string("#")
                        .then(consecutive(isNot('\n'), "comment"))
                        .optionallyFollowedBy("\n"))
                .parseToStream("#comment1\n#comment2"))
        .isEmpty();
  }

  @Test
  public void skipping_parseToStream_reader_allSkippablePatternsSkipped() {
    assertThat(
            digits()
                .skipping(
                    string("#")
                        .then(consecutive(isNot('\n'), "comment"))
                        .optionallyFollowedBy("\n"))
                .parseToStream(new StringReader("#comment1\n#comment2")))
        .isEmpty();
  }

  @Test
  public void skipping_aroundIdentifier_failure() {
    Parser<String> parser = string("foo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " foobar "));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream(" foobar ").toList());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " foo bar "));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream(" foo bar ").toList());
  }

  @Test
  public void skipping_aroundIdentifier_reader_failure() {
    Parser<String> parser = string("foo");
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream(new StringReader(" foobar ")).toList());
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream(new StringReader(" foo bar ")).toList());
  }

  @Test
  public void skipping_withAnyOf() {
    Parser<String> foobar = anyOf(string("foo"), string("bar"));
    assertThat(foobar.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(foobar.skipping(Character::isWhitespace).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_withAnyOf_source() {
    Parser<String> foobar = anyOf(string("foo"), string("bar"));
    assertThat(foobar.source().parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(foobar.source().skipping(Character::isWhitespace).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughOr() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = foo.or(bar);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foobar")).containsExactly("foo", "bar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo bar"))
        .containsExactly("foo", "bar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughOr_source() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = foo.or(bar);
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foobar"))
        .containsExactly("foo", "bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo bar"))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughSequence() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = sequence(foo, bar, (f, b) -> f + b);
    assertThat(parser.parseSkipping(Character::isWhitespace, "foobar")).isEqualTo("foobar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foobar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo bar")).isEqualTo("foobar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo bar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo bar ")).isEqualTo("foobar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo bar ")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo   bar ")).isEqualTo("foobar");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo   bar "))
        .containsExactly("foobar");
  }

  @Test
  public void skipping_propagatesThroughSequence_source() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = sequence(foo, bar, (f, b) -> f + b);
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foobar")).isEqualTo("foobar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foobar"))
        .containsExactly("foobar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foo bar")).isEqualTo("foo bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo bar"))
        .containsExactly("foo bar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo bar ")).isEqualTo("foo bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo bar "))
        .containsExactly("foo bar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo   bar ")).isEqualTo("foo   bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo   bar "))
        .containsExactly("foo   bar");
  }

  @Test
  public void skipping_propagatesThroughConsecutive() {
    Parser<String> parser = digits();
    assertThat(parser.parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("123")).containsExactly("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123 ")).isEqualTo("123");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" 123 ")).containsExactly("123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "123a"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "1 23"));
  }

  @Test
  public void skipping_propagatesThroughConsecutive_source() {
    Parser<String> parser = digits();
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("123")).containsExactly("123");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " 123 ")).isEqualTo("123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" 123 "))
        .containsExactly("123");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "123a"));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "1 23"));
  }

  @Test
  public void skipping_propagatesThroughSingle() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseSkipping(Character::isWhitespace, "1")).isEqualTo('1');
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("1")).containsExactly('1');
    assertThat(parser.parseSkipping(Character::isWhitespace, " 1 ")).isEqualTo('1');
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" 1 ")).containsExactly('1');
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "12"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "a"));
  }

  @Test
  public void skipping_propagatesThroughSingle_source() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "1")).isEqualTo("1");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " 1 ")).isEqualTo("1");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" 1 ")).containsExactly("1");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "12"));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "a"));
  }

  @Test
  public void skipping_propagatesThroughAtLeastOnce() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.atLeastOnce();
    assertThat(parser.parseSkipping(Character::isWhitespace, "foofoo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foofoo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo foo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo foo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo   foo "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo   foo "))
        .containsExactly(List.of("foo", "foo"));
  }

  @Test
  public void skipping_propagatesThroughAtLeastOnce_source() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.atLeastOnce();
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foofoo")).isEqualTo("foofoo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foofoo"))
        .containsExactly("foofoo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foo foo")).isEqualTo("foo foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo foo"))
        .containsExactly("foo foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo   foo ")).isEqualTo("foo   foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo   foo "))
        .containsExactly("foo   foo");
  }

  @Test
  public void skipping_propagatesThroughDelimitedBy() {
    Parser<String> word = word();
    Parser<List<String>> parser = word.atLeastOnceDelimitedBy(",");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("foo,bar"))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo, bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo , bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo, bar "))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " bar,foo, bar "))
        .containsExactly("bar", "foo", "bar")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" bar,foo, bar "))
        .containsExactly(List.of("bar", "foo", "bar"));
  }

  @Test
  public void skipping_propagatesThroughDelimitedBy_source() {
    Parser<String> word = word();
    Parser<List<String>> parser = word.atLeastOnceDelimitedBy(",");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "foo,bar")).isEqualTo("foo,bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("foo,bar"))
        .containsExactly("foo,bar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo, bar ")).isEqualTo("foo, bar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo , bar ")).isEqualTo("foo , bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo, bar "))
        .containsExactly("foo, bar");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " bar,foo, bar "))
        .isEqualTo("bar,foo, bar");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" bar,foo, bar "))
        .containsExactly("bar,foo, bar");
  }

  @Test
  public void skipping_propagatesThroughFlatMap() {
    Parser<Integer> parser =
        digits()
            .flatMap(number -> string("=").then(string(number).map(Integer::parseInt)));
    assertThat(parser.parseSkipping(Character::isWhitespace, "123=123")).isEqualTo(123);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("123=123")).containsExactly(123);
    assertThat(parser.parseSkipping(Character::isWhitespace, "123 =123")).isEqualTo(123);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("123 =123")).containsExactly(123);
    assertThat(parser.parseSkipping(Character::isWhitespace, "123 = 123 ")).isEqualTo(123);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("123 = 123 ")).containsExactly(123);
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123  =123 ")).isEqualTo(123);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" 123  =123 ")).containsExactly(123);
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "123 == 123"));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream("123 == 123").toList());
  }

  @Test
  public void skipping_propagatesThroughFlatMap_source() {
    Parser<Integer> parser =
        digits()
            .flatMap(number -> string("=").then(string(number).map(Integer::parseInt)));
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123=123")).isEqualTo("123=123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("123=123"))
        .containsExactly("123=123");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123 =123")).isEqualTo("123 =123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("123 =123"))
        .containsExactly("123 =123");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123 = 123 ")).isEqualTo("123 = 123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("123 = 123 "))
        .containsExactly("123 = 123");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " 123  =123 ")).isEqualTo("123  =123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" 123  =123 "))
        .containsExactly("123  =123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "123 == 123"));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream("123 == 123").toList());
  }

  @Test
  public void skipping_propagatesThroughOrElse() {
    Parser<String> foo = string("foo");
    Parser<String> parser = foo.orElse("default").between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[foo]")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [ foo ] ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEqualTo("default");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("default");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("default");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("default");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughOrElse_source() {
    Parser<String> foo = string("foo");
    Parser<String> parser = foo.orElse("default").between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughZeroOrMore() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.zeroOrMore().between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo foo]"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[foo foo]"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo foo ] "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [ foo foo ] "))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[]"))
        .containsExactly(List.of());
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ ]"))
        .containsExactly(List.of());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
  }

  @Test
  public void skipping_propagatesThroughZeroOrMore_source() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.zeroOrMore().between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[foo foo]")).isEqualTo("[foo foo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[foo foo]"))
        .containsExactly("[foo foo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo foo ] "))
        .isEqualTo("[ foo foo ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [ foo foo ] "))
        .containsExactly("[ foo foo ]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " "));
  }

  @Test
  public void zeroOrMore_charMatcher_matchesZeroTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly("");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesZeroTimes_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("[ ]");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesOneTime() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[1]")).isEqualTo("1");
    assertThat(parser.parseToStream("[1]")).containsExactly("1");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ 1 ]")).isEqualTo("1");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ 1 ]")).containsExactly("1");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesOneTime_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[1]")).isEqualTo("[1]");
    assertThat(parser.source().parseToStream("[1]")).containsExactly("[1]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ 1 ]")).isEqualTo("[ 1 ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ 1 ]"))
        .containsExactly("[ 1 ]");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesMultipleTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[123]")).isEqualTo("123");
    assertThat(parser.parseToStream("[123]")).containsExactly("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ 123 ]")).isEqualTo("123");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ 123 ]")).containsExactly("123");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesMultipleTimes_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[123]")).isEqualTo("[123]");
    assertThat(parser.source().parseToStream("[123]")).containsExactly("[123]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ 123 ]")).isEqualTo("[ 123 ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ 123 ]"))
        .containsExactly("[ 123 ]");
  }

  @Test
  public void skipping_propagatesThroughOptional() {
    Parser<String> foo = string("foo");
    Parser<Optional<String>> parser = foo.optional().between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).hasValue("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[foo]"))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).hasValue("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [ foo ] "))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[]")).containsExactly(Optional.empty());
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[ ]"))
        .containsExactly(Optional.empty());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughOptional_source() {
    Parser<String> foo = string("foo");
    Parser<Optional<String>> parser = foo.optional().between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_simpleLanguage() {
    Parser<?> lineComment = string("//").then(consecutive(isNot('\n'), "line comment"));
    Parser<?> blockComment =
        anyOf(
                consecutive(isNot('*'), "block comment"),
                single(is('*'), "*").notFollowedBy("/").map(Object::toString))
            .zeroOrMore(joining())
            .between("/*", "*/");
    Parser<String> quotedLiteral = zeroOrMore(isNot('\''), "quoted").immediatelyBetween("'", "'");
    Parser<String> language =
        anyOf(
            quotedLiteral,
            digits(),
            word(),
            string("("),
            string(")"));
    Parser<?> skippable = anyOf(consecutive(Character::isWhitespace, "whitespace"), lineComment, blockComment);

    assertThat(language.skipping(skippable).parseToStream("foo123(bar)"))
        .containsExactly("foo123", "(", "bar", ")")
        .inOrder();
    assertThat(language.skipping(skippable).parseToStream(" ' foo 123 ' (bar) "))
        .containsExactly(" foo 123 ", "(", "bar", ")")
        .inOrder();
    assertThat(language.skipping(skippable).parseToStream("foo 123 ( bar )"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
    assertThat(
            language
                .skipping(skippable)
                .parseToStream(
                    "foo // ignore this\n123 /* ignore this */ ( bar\n" + "/* and * also */)"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
  }

  @Test
  public void skipping_simpleLanguage_source() {
    Parser<?> lineComment = string("//").then(consecutive(isNot('\n'), "line comment"));
    Parser<?> blockComment =
        anyOf(
                consecutive(isNot('*'), "block comment"),
                single(is('*'), "*").notFollowedBy("/").map(Object::toString))
            .zeroOrMore(joining())
            .between("/*", "*/");
    Parser<String> quotedLiteral = zeroOrMore(isNot('\''), "quoted").immediatelyBetween("'", "'");
    Parser<String> language =
        anyOf(
            quotedLiteral,
            digits(),
            word(),
            string("("),
            string(")"));
    Parser<?> skippable = anyOf(consecutive(Character::isWhitespace, "whitespace"), lineComment, blockComment);

    assertThat(language.source().skipping(skippable).parseToStream("foo123(bar)"))
        .containsExactly("foo123", "(", "bar", ")")
        .inOrder();
    assertThat(language.source().skipping(skippable).parseToStream(" ' foo 123 ' (bar) "))
        .containsExactly("' foo 123 '", "(", "bar", ")")
        .inOrder();
    assertThat(language.source().skipping(skippable).parseToStream("foo 123 ( bar )"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
    assertThat(
            language
                .source()
                .skipping(skippable)
                .parseToStream(
                    "foo // ignore this\n123 /* ignore this */ ( bar\n" + "/* and * also */)"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
  }

  @Test
  public void literally_doesNotSkip() {
    Parser<String> parser = literally(digits());
    assertThat(parser.parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, "123 ")).isEqualTo("123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123 "));

    Parser<List<String>> numbers =
        literally(digits()).atLeastOnceDelimitedBy(",");
    assertThat(numbers.skipping(Character::isWhitespace).parseToStream("1,23"))
        .containsExactly(List.of("1", "23"));
    assertThrows(
        ParseException.class,
        () -> numbers.skipping(Character::isWhitespace).parseToStream("1 , 23").toList());
    assertThrows(
        ParseException.class,
        () -> numbers.skipping(Character::isWhitespace).parseToStream(" 1 , 23 ").toList());
  }

  @Test
  public void literally_doesNotSkip_source() {
    Parser<String> parser = literally(digits());
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "123 ")).isEqualTo("123");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " 123 "));

    Parser<List<String>> numbers =
        literally(digits()).source().atLeastOnceDelimitedBy(",");
    assertThat(numbers.parseSkipping(Character::isWhitespace, "1,23")).containsExactly("1", "23").inOrder();
    assertThat(numbers.skipping(Character::isWhitespace).parseToStream("1,23"))
        .containsExactly(List.of("1", "23"));
    assertThrows(
        ParseException.class,
        () -> numbers.source().skipping(Character::isWhitespace).parseToStream("1 , 23").toList());
    assertThrows(
        ParseException.class,
        () -> numbers.source().skipping(Character::isWhitespace).parseToStream(" 1 , 23 ").toList());
  }

  @Test
  public void zeroOrMoreChars_literally_between_zeroMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.parseSkipping(Character::isWhitespace, " [] ")).isEmpty();
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ ] ")).isEqualTo(" ");

    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [] ")).containsExactly("");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [  ] ")).containsExactly("  ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_zeroMatch_source() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[]")).isEqualTo("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [] ")).isEqualTo("[]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ ] ")).isEqualTo("[ ]");

    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [] ")).containsExactly("[]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [  ] "))
        .containsExactly("[  ]");
  }

  @Test
  public void zeroOrMoreChars_literally_between_oneMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo(" foo ");

    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[foo]")).containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [foo] ")).containsExactly("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [ foo ] ")).containsExactly(" foo ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_oneMatch_source() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("[foo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("[ foo ]");

    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [foo] "))
        .containsExactly("[foo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
  }

  @Test
  public void zeroOrMoreChars_literally_between_multipleMatches() {
    Parser<String> parser = literally(zeroOrMore(CharPredicate.WORD, "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foofoo]")).isEqualTo("foofoo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foofoo] ")).isEqualTo("foofoo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[ foofoo]"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[foo foo]"));
    assertThat(parser.skipping(Character::isWhitespace).parseToStream("[foofoo]")).containsExactly("foofoo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" [foofoo] ")).containsExactly("foofoo");
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream("[ foofoo]").toList());
    assertThrows(
        ParseException.class,
        () -> parser.skipping(Character::isWhitespace).parseToStream("[foo foo]").toList());
  }

  @Test
  public void zeroOrMoreChars_literally_between_multipleMatches_source() {
    Parser<String> parser = literally(zeroOrMore(CharPredicate.WORD, "name")).between("[", "]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, "[foofoo]")).isEqualTo("[foofoo]");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " [foofoo] ")).isEqualTo("[foofoo]");
    assertThrows(
        ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "[ foofoo]"));
    assertThrows(
        ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, "[foo foo]"));
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream("[foofoo]"))
        .containsExactly("[foofoo]");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" [foofoo] "))
        .containsExactly("[foofoo]");
    assertThrows(
        ParseException.class,
        () -> parser.source().skipping(Character::isWhitespace).parseToStream("[ foofoo]").toList());
    assertThrows(
        ParseException.class,
        () -> parser.source().skipping(Character::isWhitespace).parseToStream("[foo foo]").toList());
  }

  @Test
  public void notEmpty_twoOptionalParsers_firstOptionalParserFails() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(".")).containsExactly("");
  }

  @Test
  public void notEmpty_twoOptionalParsers_firstOptionalParserFails_source() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.source().parse(".")).isEqualTo(".");
  }

  @Test
  public void notEmpty_twoOptionalParsers_secondOptionalParserFails() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(",123,,")).containsExactly("", "123", "", "").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_secondOptionalParserFails_source() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.source().parse(",123,,")).isEqualTo(",123,,");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothOptionalParsersMatch() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(",123,,456.")).containsExactly("", "123", "", "456").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothOptionalParsersMatch_source() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.source().parse(",123,,456.")).isEqualTo(",123,,456.");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothFail_firstErrorIsFarther() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    ParseException thrown = assertThrows(ParseException.class, () -> numbers.parse("123,a."));
    assertThat(thrown).hasMessageThat().contains("at 1:5");
    assertThat(thrown).hasMessageThat().contains(" encountered [a.]");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothFail_secondErrorIsFarther() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string("abc,1").followedBy("!").optional())
            .notEmpty();
    ParseException thrown = assertThrows(ParseException.class, () -> numbers.parse("abc,1."));
    assertThat(thrown).hasMessageThat().contains("at 1:6: expecting <!>, encountered [.]");
  }

  @Test
  public void skipping_anyOfWithLiterally() {
    Parser<String> parser = anyOf(string("foo"), literally(digits()));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(
        ParseException.class, () -> parser.skipping(Character::isWhitespace).parseToStream(" 123").toList());
  }

  @Test
  public void skipping_anyOfWithLiterally_source() {
    Parser<String> parser = anyOf(string("foo"), literally(digits()));
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(
        ParseException.class,
        () -> parser.source().skipping(Character::isWhitespace).parseToStream(" 123").toList());
  }

  @Test
  public void skipping_anyOfWithoutLiterally() {
    Parser<String> parser = anyOf(string("foo"), digits());
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123")).isEqualTo("123");
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" 123")).containsExactly("123");
  }

  @Test
  public void skipping_anyOfWithoutLiterally_source() {
    Parser<String> parser = anyOf(string("foo"), digits());
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " 123")).isEqualTo("123");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" 123")).containsExactly("123");
  }

  @Test
  public void skipping_propagatesThroughRuleParser() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parseSkipping(Character::isWhitespace, " ( 2 ) + 3 ")).isEqualTo(5);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" ( 2 ) + 3 ")).containsExactly(5);
    assertThat(parser.parseSkipping(Character::isWhitespace, " ( 2 + ( 3 + 4 ) ) ")).isEqualTo(9);
    assertThat(parser.skipping(Character::isWhitespace).parseToStream(" ( 2 + ( 3 + 4 ) ) "))
        .containsExactly(9);
  }

  @Test
  public void skipping_propagatesThroughRuleParser_source() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " ( 2 ) + 3 ")).isEqualTo("( 2 ) + 3");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" ( 2 ) + 3 "))
        .containsExactly("( 2 ) + 3");
    assertThat(parser.source().parseSkipping(Character::isWhitespace, " ( 2 + ( 3 + 4 ) ) "))
        .isEqualTo("( 2 + ( 3 + 4 ) )");
    assertThat(parser.source().skipping(Character::isWhitespace).parseToStream(" ( 2 + ( 3 + 4 ) ) "))
        .containsExactly("( 2 + ( 3 + 4 ) )");
  }

  @Test
  public void recursiveGrammar() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parseToStream("1")).containsExactly(1);
    assertThat(parser.parse("(2)")).isEqualTo(2);
    assertThat(parser.parseToStream("(2)")).containsExactly(2);
    assertThat(parser.parse("(2)+3")).isEqualTo(5);
    assertThat(parser.parseToStream("(2)+3")).containsExactly(5);
    assertThat(parser.parse("(2)+3+(4)")).isEqualTo(9);
    assertThat(parser.parseToStream("(2)+3+(4)")).containsExactly(9);
    assertThat(parser.parse("(2+(3+4))")).isEqualTo(9);
    assertThat(parser.parseToStream("(2+(3+4))")).containsExactly(9);
  }

  private static Parser<Integer> simpleCalculator() {
    Parser<Integer> num = Parser.single(DIGIT, "digit").map(c -> c - '0');
    return Parser.define(
        calc ->
        calc.between("(", ")")
            .or(num)
            .atLeastOnceDelimitedBy("+")
            .map(nums -> nums.stream().mapToInt(n -> n).sum()));
  }

  @Test
  public void recursiveGrammar_source() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parse("(2)")).isEqualTo("(2)");
    assertThat(parser.source().parseToStream("(2)")).containsExactly("(2)");
    assertThat(parser.source().parse("(2)+3")).isEqualTo("(2)+3");
    assertThat(parser.source().parseToStream("(2)+3")).containsExactly("(2)+3");
    assertThat(parser.source().parse("(2)+3+(4)")).isEqualTo("(2)+3+(4)");
    assertThat(parser.source().parseToStream("(2)+3+(4)")).containsExactly("(2)+3+(4)");
    assertThat(parser.source().parse("(2+(3+4))")).isEqualTo("(2+(3+4))");
    assertThat(parser.source().parseToStream("(2+(3+4))")).containsExactly("(2+(3+4))");
  }

  @Test
  public void rule_setTwice_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    rule.definedAs(string("a"));
    assertThrows(IllegalStateException.class, () -> rule.definedAs(string("b")));
  }

  @Test
  public void rule_setNull_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    Parser<String> parser = null;
    assertThrows(NullPointerException.class, () -> rule.definedAs(parser));
  }

  @Test
  public void rule_ruleParseBeforeDefined_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    assertThrows(IllegalStateException.class, () -> rule.parse("a"));
    assertThrows(IllegalStateException.class, () -> rule.parseToStream("a").toList());
  }

  @Test
  public void rule_definedAsRule_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    Parser<String> actuallyRule = rule;
    assertThrows(IllegalArgumentException.class, () -> rule.definedAs(actuallyRule));
  }

  @Test
  public void parseToStream_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream("123")).containsExactly('1', '2', '3').inOrder();
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_fromIndex() {
    assertThat(digits().skipping(string(",")).parseToStream("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
    assertThat(
            digits().source().skipping(string(",")).parseToStream("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
  }

  @Test
  public void parseToStream_fromIndex_atEnd() {
    assertThat(digits().parseToStream("123", 3)).isEmpty();
    assertThat(digits().skipping(Character::isWhitespace).parseToStream("123  ", 3))
        .isEmpty();
  }

  @Test
  public void parseToStream_fromIndex_outOfBounds() {
    assertThrows(
        IndexOutOfBoundsException.class, () -> digits().parseToStream("123", 4));
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> digits().skipping(Character::isWhitespace).parseToStream("123 ", 5));
  }

  @Test
  public void parseToStream_reader_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream(new StringReader("123")))
        .containsExactly('1', '2', '3')
        .inOrder();
    assertThat(parser.parseToStream(new StringReader("")).toList()).isEmpty();
  }

  @Test
  public void parseToStream_withCompactingReader() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    assertThat(single(DIGIT, "digit").parseToStream(input, 0))
        .containsExactly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        .inOrder();
  }

  @Test
  public void parseToStream_withCompactingReader_fails() {
    CharInput input = CharInput.from(new StringReader("01 \n234 \n567 \n89 x"), 4, 3);
    Parser<String> parser = digits();
    ParseException e =
        assertThrows(
            ParseException.class,
            () -> parser.skipping(Character::isWhitespace).parseToStream(input, 0).count());
    assertThat(e).hasMessageThat().contains("at 17: expecting <digits>, encountered [x]");
  }

  @Test
  public void parseToStream_success_source() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.source().parseToStream("123")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void parseToStream_emptyInput() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_reader_emptyInput() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream(new StringReader("")).toList()).isEmpty();
  }

  @Test
  public void parseToStream_fail() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parseToStream("1a2").toList());
  }

  @Test
  public void parseToStream_reader_fail() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThrows(
        ParseException.class, () -> parser.parseToStream(new StringReader("1a2")).toList());
  }

  @Test
  public void probe_emptyInput_returnsEmpty() {
    assertThat(string("foo").probe("")).isEmpty();
  }

  @Test
  public void probe_singleMatch_returnsValue() {
    assertThat(string("foo").probe("foo")).containsExactly("foo");
  }

  @Test
  public void probe_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().probe("foo")).containsExactly("foo");
  }

  @Test
  public void probe_multipleMatches_returnsValue() {
    assertThat(string("foo").probe("foofoo")).containsExactly("foo", "foo");
  }

  @Test
  public void probe_reader_singleMatch_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foo"))).containsExactly("foo");
  }

  @Test
  public void probe_reader_multipleMatches_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foofoo"))).containsExactly("foo", "foo");
  }

  @Test
  public void probe_withCompactingReader() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    assertThat(single(DIGIT, "digit").probe(input, 0))
        .containsExactly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        .inOrder();
  }

  @Test
  public void probe_multipleMatches_returnsValue_source() {
    assertThat(string("foo").source().probe("foofoo")).containsExactly("foo", "foo");
  }

  @Test
  public void probe_fromIndex() {
    assertThat(digits().skipping(string(",")).probe("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
    assertThat(digits().source().skipping(string(",")).probe("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
  }

  @Test
  public void probe_fromIndex_atEnd() {
    assertThat(digits().probe("123", 3)).isEmpty();
    assertThat(digits().skipping(Character::isWhitespace).probe("123  ", 3)).isEmpty();
  }

  @Test
  public void probe_fromIndex_outOfBounds() {
    assertThrows(
        IndexOutOfBoundsException.class, () -> digits().probe("123", 4));
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> digits().skipping(Character::isWhitespace).probe("123 ", 5));
  }

  @Test
  public void probe_prefixMatch_returnsValue() {
    assertThat(string("foo").probe("foobar")).containsExactly("foo");
  }

  @Test
  public void probe_reader_prefixMatch_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foobar"))).containsExactly("foo");
  }

  @Test
  public void probe_prefixMatch_returnsValue_source() {
    assertThat(string("foo").source().probe("foobar")).containsExactly("foo");
  }

  @Test
  public void probe_noMatch_returnsEmpty() {
    assertThat(string("foo").probe("bar")).isEmpty();
  }

  @Test
  public void probe_reader_noMatch_returnsEmpty() {
    assertThat(string("foo").probe(new StringReader("bar"))).isEmpty();
  }

  @Test
  public void probe_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_emptyInput_returnsEmpty() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(" ")).isEmpty();
  }

  @Test
  public void skipping_probeReader_emptyInput_returnsEmpty() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(new StringReader(" "))).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_emptyInput_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(Character::isWhitespace).probe(" ")).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(" foo ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeReader_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(new StringReader(" foo ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().skipping(Character::isWhitespace).probe(" foo ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_multipleMatches_returnsValues() {
    assertThat(digits().skipping(Character::isWhitespace).probe(" 123  456 "))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeReader_multipleMatches_returnsValues() {
    assertThat(
            digits()
                .skipping(Character::isWhitespace)
                .probe(new StringReader(" 123  456 ")))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeCharPredicate_multipleMatches_returnsValues_source() {
    assertThat(digits().source().skipping(Character::isWhitespace).probe(" 123  456 "))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeCharPredicate_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(" foobar ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeReader_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(new StringReader(" foobar ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_prefixMatchWithSkipping_returnsValue_source() {
    assertThat(string("foo").source().skipping(Character::isWhitespace).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeReader_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(Character::isWhitespace).probe(new StringReader("bar"))).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(Character::isWhitespace).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeParser_emptyInput_returnsEmpty() {
    assertThat(string("foo").skipping(consecutive(Character::isWhitespace, "skip")).probe("\n\n ")).isEmpty();
  }

  @Test
  public void skipping_probeParser_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(consecutive(Character::isWhitespace, "skip")).probe(" \n foo "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().skipping(consecutive(Character::isWhitespace, "skip")).probe(" \n foo "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_multipleMatches_returnsValues() {
    assertThat(
            word()
                .skipping(consecutive(Character::isWhitespace, "skip"))
                .probe(" \n foo 123"))
        .containsExactly("foo", "123")
        .inOrder();
  }

  @Test
  public void skipping_probeParser_multipleMatches_returnsValues_source() {
    assertThat(
            word()
                .source()
                .skipping(consecutive(Character::isWhitespace, "skip"))
                .probe(" \n foo 123"))
        .containsExactly("foo", "123")
        .inOrder();
  }

  @Test
  public void skipping_probeParser_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(consecutive(Character::isWhitespace, "skip")).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_prefixMatchWithSkipping_returnsValue_source() {
    assertThat(string("foo").source().skipping(consecutive(Character::isWhitespace, "skip")).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(consecutive(Character::isWhitespace, "skip")).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeParser_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(consecutive(Character::isWhitespace, "skip")).probe("bar"))
        .isEmpty();
  }

  @Test
  public void parse_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.parse("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void parse_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(Character::isWhitespace);
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.parse("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void parseToStream_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.parseToStream("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void parseToStream_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(Character::isWhitespace);
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.parseToStream("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void probe_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.probe("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void probe_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(Character::isWhitespace);
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.probe("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void testNestedPlaceholderGrammar() {
    assertThat(Format.parse("a{b=xy{foo=bar}z}d{e=f}{{not a placeholder}}"))
        .isEqualTo(
            new Format(
                "a{b}d{e}{not a placeholder}",
                List.of(
                    new Format.Placeholder(
                        "b",
                        new Format(
                            "xy{foo}z",
                            List.of(
                                new Format.Placeholder(
                                    "foo", new Format("bar", List.of()))))),
                    new Format.Placeholder("e", new Format("f", List.of())))));
  }

  @Test
  public void testNestedPlaceholderGrammar_source() {
    String input = "a{b=xy{foo=bar}z}d{e=f}{{not a placeholder}}";
    assertThat(Format.parser().source().parse(input)).isEqualTo(input);
  }

  /** An example nested placeholder grammar for demo purpose. */
  private record Format(String template, List<Placeholder> placeholders) {

    record Placeholder(String name, Format format) {}

    static class Builder {
      private final List<Placeholder> placeholders = new ArrayList<>();
      private final StringBuilder template = new StringBuilder();

      @CanIgnoreReturnValue
      Builder append(String text) {
        template.append(text);
        return this;
      }

      @CanIgnoreReturnValue
      Builder append(Placeholder placeholder) {
        template.append("{").append(placeholder.name()).append("}");
        placeholders.add(placeholder);
        return this;
      }

      @CanIgnoreReturnValue
      Builder addAll(Builder that) {
        template.append(that.template);
        placeholders.addAll(that.placeholders);
        return this;
      }

      Format build() {
        return new Format(template.toString(), placeholders);
      }
    }

    static Parser<Format> parser() {
      Parser.Rule<Format> rule = new Parser.Rule<>();
      Parser<String> placeholderName =
          consecutive(CharPredicate.range('a', 'z'), "placeholder name");
      Parser<Placeholder> placeholder =
          Parser.sequence(placeholderName.followedBy("="), rule, Placeholder::new)
              .between("{", "}");
      Parser<Format> parser =
          anyOf(
                  placeholder,
                  Parser.string("{{").thenReturn("{"), // escape {
                  Parser.string("}}").thenReturn("}"), // escape }
                  Parser.consecutive(CharPredicate.noneOf("{}"), "literal text"))
              .atLeastOnce(
                  Collector.of(
                      Format.Builder::new,
                      (Format.Builder b, Object v) -> {
                        if (v instanceof Placeholder p) {
                          b.append(p);
                        } else {
                          b.append((String) v);
                        }
                      },
                      Format.Builder::addAll,
                      Format.Builder::build));
      return rule.definedAs(parser);
    }

    static Format parse(String format) {
      return parser().parse(format);
    }
  }

  /** An example resource name pattern for demo purpose. */
  record ResourceNamePattern(List<PathElement> path, String revision) {
    ResourceNamePattern(List<PathElement> path) {
      this(path, null);
    }

    ResourceNamePattern withRevision(String revision) {
      return new ResourceNamePattern(path, revision);
    }

    static Parser<ResourceNamePattern> parser() {
      Parser<String> name = word();
      Parser<String> revision = string("@").then(name);
      Parser<PathElement.Subpath> subpath =
          Parser.sequence(
                  name.followedBy("="),
                  Parser.<PathElement>anyOf(
                          name.map(PathElement.Literal::new),
                          string("**").thenReturn(new PathElement.SubpathWildcard()),
                          string("*").thenReturn(new PathElement.PathElementWildcard()))
                      .atLeastOnceDelimitedBy("/")
                      .map(ResourceNamePattern::new),
                  PathElement.Subpath::new)
              .between("{", "}");
      return Parser.<PathElement>anyOf(
              name.map(PathElement.Literal::new),
              name.between("{", "}").map(PathElement.Placeholder::new),
              subpath)
          .atLeastOnceDelimitedBy("/")
          .map(ResourceNamePattern::new)
          .optionalPostfix(revision.map(v -> pattern -> pattern.withRevision(v)));
    }

    static ResourceNamePattern parse(String path) {
      return parser().parse(path);
    }
  }

  private sealed interface PathElement
      permits PathElement.Literal,
          PathElement.Placeholder,
          PathElement.Subpath,
          PathElement.PathElementWildcard,
          PathElement.SubpathWildcard {
    record Literal(String value) implements PathElement {}

    record Placeholder(String name) implements PathElement {}

    record Subpath(String name, ResourceNamePattern pattern) implements PathElement {}

    record PathElementWildcard() implements PathElement {}

    record SubpathWildcard() implements PathElement {}
  }

  @Test
  public void resourceNamePattern_noPlaceholder() {
    assertThat(ResourceNamePattern.parse("users"))
        .isEqualTo(new ResourceNamePattern(List.of(new PathElement.Literal("users"))));
  }

  @Test
  public void resourceNamePattern_noPlaceholder_source() {
    String input = "users";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
  }

  @Test
  public void resourceNamePattern_withSimplePlaceholder() {
    assertThat(ResourceNamePattern.parse("users/{userId}/messages/{messageId}"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("users"),
                    new PathElement.Placeholder("userId"),
                    new PathElement.Literal("messages"),
                    new PathElement.Placeholder("messageId"))));
  }

  @Test
  public void resourceNamePattern_withSimplePlaceholder_source() {
    String input = "users/{userId}/messages/{messageId}";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
  }

  @Test
  public void resourceNamePattern_withSubpathPlaceholder() {
    assertThat(ResourceNamePattern.parse("v1/{name=projects/*/locations/*}/messages"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            List.of(
                                new PathElement.Literal("projects"),
                                new PathElement.PathElementWildcard(),
                                new PathElement.Literal("locations"),
                                new PathElement.PathElementWildcard()))),
                    new PathElement.Literal("messages"))));
  }

  @Test
  public void resourceNamePattern_withSubpathPlaceholder_source() {
    String input = "v1/{name=projects/*/locations/*}/messages";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
  }

  @Test
  public void resourceNamePattern_withSubpathWildcard() {
    assertThat(ResourceNamePattern.parse("v1/{name=projects/**}/messages"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            List.of(
                                new PathElement.Literal("projects"),
                                new PathElement.SubpathWildcard()))),
                    new PathElement.Literal("messages"))));
  }

  @Test
  public void resourceNamePattern_withSubpathWildcard_source() {
    String input = "v1/{name=projects/**}/messages";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
  }

  private static CharPredicate isNot(char ch) {
    return c -> c != ch;
  }
}

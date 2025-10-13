package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.single;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Parser.MatchResult.Failure.sourcePosition;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.CharPredicate.range;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.common.testing.NullPointerTester;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class ParserTest {
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHANUMERIC =
      CharPredicate.range('a', 'z')
          .orRange('A', 'Z')
          .orRange('0', '9');

  @Test
  public void string_success() {
    Parser<String> parser = string("foo");
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parseToStream("")).isEmpty();
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
  public void flatMap_success() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> string("=" + number));
    assertThat(parser.parse("123=123")).isEqualTo("=123");
    assertThat(parser.parseToStream("123=123")).containsExactly("=123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void flatMap_failure_withLeftover() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> string("=" + number));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("123=123???"));
    assertThat(thrown).hasMessageThat().contains("at 1:8: expecting <EOF>, encountered [???]");
    assertThrows(ParseException.class, () -> parser.parseToStream("123=123???").toList());
  }

  @Test
  public void flatMap_failure() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> string("=" + number));
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
  public void then_orEmpty_p2MatchesOnce() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("ab")).containsExactly("b");
  }

  @Test
  public void then_orEmpty_p2MatchesMultipleTimes() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("abb")).containsExactly("b", "b");
  }

  @Test
  public void followedBy_success() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThat(parser.parse("123บาท")).isEqualTo("123");
    assertThat(parser.parseToStream("123บาท")).containsExactly("123");
    assertThat(parser.parseToStream("")).isEmpty();
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
  public void notFollowedBy_emptySuffix_throws() {
    assertThrows(IllegalArgumentException.class, () -> string("a").notFollowedBy(""));
  }

  @Test
  public void notFollowedBy_selfFailsToMatch() {
    assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("c"));
  }

  @Test
  public void notFollowedBy_suffixFollows() {
    assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ab"));
  }

  @Test
  public void notFollowedBy_suffixDoesNotFollow() {
    assertThat(string("a").notFollowedBy("b").parse("a")).isEqualTo("a");
    assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ac"));
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
    assertThat(thrown).hasMessageThat().contains("encountered [?...]");
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
  public void sequence_orEmpty_bothSucceed() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.parse("ab")).isEqualTo("a[b]");
    assertThat(parser.parseToStream("ab")).containsExactly("a[b]");
    assertThat(parser.parse("abb")).isEqualTo("a[b, b]");
    assertThat(parser.parseToStream("abb")).containsExactly("a[b, b]");
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
  public void sequence_leftOrEmpty_leftIsEmpty() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.parse("b")).isEqualTo("[]b");
    assertThat(parser.parseToStream("b")).containsExactly("[]b");
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
  public void sequence_bothOrEmpty_leftIsEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
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
  public void sequence_bothOrEmpty_bothEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void orEmpty_delimitedBy_bothSides() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,bar")).containsExactly("foo", "bar").inOrder();
    assertThat(parser.notEmpty().parse("foo,bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_single() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse("foo")).containsExactly("foo");
    assertThat(parser.notEmpty().parse("foo")).containsExactly("foo");
  }

  @Test
  public void orEmpty_delimitedBy_trailingEmpty() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse("foo,")).containsExactly("foo", "").inOrder();
    assertThat(parser.notEmpty().parse("foo,")).containsExactly("foo", "").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_leadingEmpty() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse(",bar")).containsExactly("", "bar").inOrder();
    assertThat(parser.notEmpty().parse(",bar")).containsExactly("", "bar").inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_kitchenSink() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse(",foo,bar,,")).containsExactly("", "foo", "bar", "", "").inOrder();
    assertThat(parser.notEmpty().parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_allEmpty() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse(",,,")).containsExactly("", "", "", "");
    assertThat(parser.notEmpty().parse(",,,")).containsExactly("", "", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_emptyInput() {
    Parser<List<String>>.OrEmpty parser =
        consecutive(ALPHANUMERIC, "word").orElse("").delimitedBy(",");
    assertThat(parser.parse("")).containsExactly("");
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
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

    Parser<List<String>> parser2 =
        consecutive(DIGIT, "digit").atLeastOnce();
    assertThat(parser2.parse("1230")).containsExactly("1230");
    assertThat(parser2.parseToStream("1230")).containsExactly(List.of("1230"));
    assertThat(parser2.parseToStream("")).isEmpty();
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
  public void zeroOrMore_between_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
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
  public void zeroOrMore_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
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
  public void zeroOrMore_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
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
  public void zeroOrMore_followedByParser_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedByParser_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
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
  public void zeroOrMore_parseNonEmpty() {
    assertThat(string("a").zeroOrMore().parse("aa")).containsExactly("a", "a").inOrder();
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
  public void zeroOrMoreDelimitedBy_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
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
  public void zeroOrMoreDelimitedBy_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
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
  public void zeroOrMoreDelimitedBy_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
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
  public void zeroOrMoreDelimitedBy_followedByParser_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
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
  public void zeroOrMoreDelimitedBy_parseNonEmpty() {
    assertThat(string("a").zeroOrMoreDelimitedBy(",").parse("a,a"))
        .containsExactly("a", "a")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseFail() {
    assertThrows(ParseException.class, () -> string("a").zeroOrMoreDelimitedBy(",").parse("b"));
  }

  @Test
  public void optional_between_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_between_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedBy_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedBy_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_betweenParsers_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_betweenParsers_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedByParser_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedByParser_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_parseEmpty() {
    assertThat(string("a").optional().parse("")).isEmpty();
  }

  @Test
  public void optional_parseNonEmpty() {
    assertThat(string("a").optional().parse("a")).hasValue("a");
  }

  @Test
  public void optional_parseFail() {
    assertThrows(ParseException.class, () -> string("a").optional().parse("b"));
  }

  @Test
  public void orElse_between_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_between_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_followedBy_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedBy_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_betweenParsers_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_betweenParsers_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_followedByParser_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedByParser_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_parseEmpty() {
    assertThat(string("a").orElse("default").parse("")).isEqualTo("default");
  }

  @Test
  public void orElse_parseNonEmpty() {
    assertThat(string("a").orElse("default").parse("a")).isEqualTo("a");
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
  public void orElse_nullDefault_parseEmpty() {
    assertThat(string("a").orElse(null).parse("")).isNull();
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
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_onlyTrailingDelimiter() {
    Parser<List<String>> parser =
        consecutive(DIGIT, "number").atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(","));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <number>, encountered [,]");
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_emptyInput() {
    Parser<List<String>> parser =
        consecutive(DIGIT, "number").atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <number>, encountered <EOF>");
  }

  @Test
  public void between_success() {
    Parser<String> parser = string("content").between("[", "]");
    assertThat(parser.parse("[content]")).isEqualTo("content");
    assertThat(parser.parseToStream("[content]")).containsExactly("content");
    assertThat(parser.parseToStream("")).isEmpty();
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
  public void single_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parseToStream("1")).containsExactly('1');
    assertThat(parser.parse("9")).isEqualTo('9');
    assertThat(parser.parseToStream("9")).containsExactly('9');
    assertThat(parser.parseToStream("")).isEmpty();
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
  public void consecutive_success() {
    Parser<String> parser = consecutive(DIGIT, "digit");
    assertThat(parser.parse("1")).isEqualTo("1");
    assertThat(parser.parseToStream("1")).containsExactly("1");
    assertThat(parser.parse("123")).isEqualTo("123");
    assertThat(parser.parseToStream("123")).containsExactly("123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_failure_withLeftover() {
    Parser<String> parser = consecutive(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("1a").toList());
    assertThrows(ParseException.class, () -> parser.parse("123a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123a").toList());
  }

  @Test
  public void consecutive_failure() {
    Parser<String> parser = consecutive(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("12a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("12a").toList());
    assertThrows(ParseException.class, () -> parser.parse(""));
  }

  @Test
  public void prefix_zeroOperator_success() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void prefix_oneOperator_success() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThat(parser.parse("-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-10")).containsExactly(-10);
  }

  @Test
  public void prefix_multipleOperators_success() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
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
  public void prefix_operandParseFails() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-a").toList());
  }

  @Test
  public void prefix_failure_withLeftover() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.prefix(neg);
    assertThrows(ParseException.class, () -> parser.parse("10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-10a").toList());
  }

  @Test
  public void postfix_success() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
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
  public void postfix_failure() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
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
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
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
  public void skipping_aroundIdentifier() {
    Parser<String> parser = string("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo ")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "   foo   ")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "   foo   ")).containsExactly("foo");
  }

  @Test
  public void skipping_aroundIdentifier_failure() {
    Parser<String> parser = string("foo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " foobar "));
    assertThrows(
        ParseException.class,
        () -> parser.parseToStreamSkipping(Character::isWhitespace, " foobar ").toList());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " foo bar "));
    assertThrows(
        ParseException.class,
        () -> parser.parseToStreamSkipping(Character::isWhitespace, " foo bar ").toList());
  }

  @Test
  public void skipping_withAnyOf() {
    Parser<String> foobar = anyOf(string("foo"), string("bar"));
    assertThat(foobar.parseSkipping(Character::isWhitespace, " foo ")).isEqualTo("foo");
    assertThat(foobar.parseToStreamSkipping(Character::isWhitespace, " foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughOr() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = foo.or(bar);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foobar")).containsExactly("foo", "bar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo bar")).containsExactly("foo", "bar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughSequence() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = sequence(foo, bar, (f, b) -> f + b);
    assertThat(parser.parseSkipping(Character::isWhitespace, "foobar")).isEqualTo("foobar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foobar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo bar")).isEqualTo("foobar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo bar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo bar ")).isEqualTo("foobar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo bar ")).containsExactly("foobar");
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo   bar ")).isEqualTo("foobar");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo   bar ")).containsExactly("foobar");
  }

  @Test
  public void skipping_propagatesThroughConsecutive() {
    Parser<String> parser = consecutive(DIGIT, "digit");
    assertThat(parser.parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "123")).containsExactly("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123 ")).isEqualTo("123");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " 123 ")).containsExactly("123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "123a"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "1 23"));
  }

  @Test
  public void skipping_propagatesThroughSingle() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseSkipping(Character::isWhitespace, "1")).isEqualTo('1');
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "1")).containsExactly('1');
    assertThat(parser.parseSkipping(Character::isWhitespace, " 1 ")).isEqualTo('1');
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " 1 ")).containsExactly('1');
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "12"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "a"));
  }

  @Test
  public void skipping_propagatesThroughAtLeastOnce() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.atLeastOnce();
    assertThat(parser.parseSkipping(Character::isWhitespace, "foofoo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foofoo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo foo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo foo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo   foo "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo   foo "))
        .containsExactly(List.of("foo", "foo"));
  }

  @Test
  public void skipping_propagatesThroughDelimitedBy() {
    Parser<String> word = consecutive(ALPHANUMERIC, "word");
    Parser<List<String>> parser = word.atLeastOnceDelimitedBy(",");
    assertThat(parser.parseSkipping(Character::isWhitespace, "foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "foo,bar"))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo, bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo , bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo, bar "))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " bar,foo, bar "))
        .containsExactly("bar", "foo", "bar")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " bar,foo, bar "))
        .containsExactly(List.of("bar", "foo", "bar"));
  }

  @Test
  public void skipping_propagatesThroughFlatMap() {
    Parser<Integer> parser =
        consecutive(DIGIT, "number")
            .flatMap(number -> string("=").then(string(number).map(Integer::parseInt)));
    assertThat(parser.parseSkipping(Character::isWhitespace, "123=123")).isEqualTo(123);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "123=123")).containsExactly(123);
    assertThat(parser.parseSkipping(Character::isWhitespace, "123 =123")).isEqualTo(123);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "123 =123")).containsExactly(123);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "123 = 123 ")).containsExactly(123);
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123  =123 ")).isEqualTo(123);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " 123  =123 ")).containsExactly(123);
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "123 == 123"));
    assertThrows(
        ParseException.class,
        () -> parser.parseToStreamSkipping(Character::isWhitespace, "123 == 123").toList());
  }

  @Test
  public void skipping_propagatesThroughOrElse() {
    Parser<String> foo = string("foo");
    Parser<String> parser = foo.orElse("default").between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[foo]")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [ foo ] ")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEqualTo("default");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[]")).containsExactly("default");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEqualTo("default");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ ]")).containsExactly("default");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughZeroOrMore() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.zeroOrMore().between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo foo]"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[foo foo]"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo foo ] "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [ foo foo ] "))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[]"))
        .containsExactly(List.of());
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ ]"))
        .containsExactly(List.of());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
  }

  @Test
  public void zeroOrMore_charMatcher_matchesZeroTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly("");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ ]")).containsExactly("");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesOneTime() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[1]")).isEqualTo("1");
    assertThat(parser.parseToStream("[1]")).containsExactly("1");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ 1 ]")).isEqualTo("1");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ 1 ]")).containsExactly("1");
  }

  @Test
  public void zeroOrMore_charMatcher_matchesMultipleTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[123]")).isEqualTo("123");
    assertThat(parser.parseToStream("[123]")).containsExactly("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ 123 ]")).isEqualTo("123");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ 123 ]")).containsExactly("123");
  }

  @Test
  public void skipping_propagatesThroughOptional() {
    Parser<String> foo = string("foo");
    Parser<Optional<String>> parser = foo.optional().between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).hasValue("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[foo]"))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).hasValue("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [ foo ] "))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[]")).containsExactly(Optional.empty());
    assertThat(parser.parseSkipping(Character::isWhitespace, "[ ]")).isEmpty();
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[ ]")).containsExactly(Optional.empty());
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[bar]"));
  }

  @Test
  public void skipping_simpleLanguage() {
    Parser<?> lineComment = string("//").then(consecutive(c -> c != '\n', "line comment"));
    Parser<?> blockComment =
        anyOf(
                consecutive(c -> c != '*', "block comment"),
                single(is('*'), "*").notFollowedBy("/").map(Object::toString))
            .zeroOrMore(joining())
            .between("/*", "*/");
    Parser<String> quotedLiteral = literally(zeroOrMore(c -> c != '\'', "quoted")).between("'", "'");
    Parser<String> language =
        anyOf(
            quotedLiteral,
            consecutive(DIGIT, "number"),
            consecutive(ALPHANUMERIC, "identifier"),
            string("("),
            string(")"));
    Parser<?> skippable = anyOf(consecutive(Character::isWhitespace, "whitespace"), lineComment, blockComment);

    assertThat(language.parseToStreamSkipping(skippable, "foo123(bar)"))
        .containsExactly("foo123", "(", "bar", ")")
        .inOrder();
    assertThat(language.parseToStreamSkipping(skippable, " ' foo 123 ' (bar) "))
        .containsExactly(" foo 123 ", "(", "bar", ")")
        .inOrder();
    assertThat(language.parseToStreamSkipping(skippable, "foo 123 ( bar )"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
    assertThat(
            language.parseToStreamSkipping(
                skippable,
                "foo // ignore this\n123 /* ignore this */ ( bar\n" + "/* and * also */)"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
  }

  @Test
  public void literally_doesNotSkip() {
    Parser<String> parser = literally(consecutive(DIGIT, "digit"));
    assertThat(parser.parseSkipping(Character::isWhitespace, "123")).isEqualTo("123");
    assertThat(parser.parseSkipping(Character::isWhitespace, "123 ")).isEqualTo("123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123 "));

    Parser<List<String>> numbers = literally(consecutive(DIGIT, "digit")).atLeastOnceDelimitedBy(",");
    assertThat(numbers.parseToStreamSkipping(Character::isWhitespace, "1,23"))
        .containsExactly(List.of("1", "23"));
    assertThrows(
        ParseException.class, () -> numbers.parseToStreamSkipping(Character::isWhitespace, "1 , 23").toList());
    assertThrows(
        ParseException.class,
        () -> numbers.parseToStreamSkipping(Character::isWhitespace, " 1 , 23 ").toList());
  }

  @Test
  public void zeroOrMoreChars_literally_between_zeroMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[]")).isEmpty();
    assertThat(parser.parseSkipping(Character::isWhitespace, " [] ")).isEmpty();
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ ] ")).isEqualTo(" ");

    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[]")).containsExactly("");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [] ")).containsExactly("");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [  ] ")).containsExactly("  ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_oneMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foo]")).isEqualTo("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foo] ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [ foo ] ")).isEqualTo(" foo ");

    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[foo]")).containsExactly("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [foo] ")).containsExactly("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [ foo ] ")).containsExactly(" foo ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_multipleMatches() {
    Parser<String> parser = literally(zeroOrMore(ALPHANUMERIC, "name")).between("[", "]");
    assertThat(parser.parseSkipping(Character::isWhitespace, "[foofoo]")).isEqualTo("foofoo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " [foofoo] ")).isEqualTo("foofoo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[ foofoo]"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, "[foo foo]"));
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, "[foofoo]")).containsExactly("foofoo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " [foofoo] ")).containsExactly("foofoo");
    assertThrows(
        ParseException.class,
        () -> parser.parseToStreamSkipping(Character::isWhitespace, "[ foofoo]").toList());
    assertThrows(
        ParseException.class,
        () -> parser.parseToStreamSkipping(Character::isWhitespace, "[foo foo]").toList());
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter() {
    Parser<List<String>> parser =
        consecutive(DIGIT, "number")
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
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter_failOnEmpty() {
    Parser<List<String>> parser =
        consecutive(DIGIT, "number")
            .zeroOrMoreDelimitedBy(",")
            .followedBy(string(",").optional())
            .notEmpty();
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <number>, encountered <EOF>");
  }

  @Test
  public void notEmpty_twoOptionalParsers_firstOptionalParserFails() {
    var numbers =
        consecutive(DIGIT, "number")
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(".")).containsExactly("");
  }

  @Test
  public void notEmpty_twoOptionalParsers_secondOptionalParserFails() {
    var numbers =
        consecutive(DIGIT, "number")
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(",123,,")).containsExactly("", "123", "", "").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothOptionalParsersMatch() {
    var numbers =
        consecutive(DIGIT, "number")
            .orElse("")
            .delimitedBy(",")
            .followedBy(string(".").optional())
            .notEmpty();
    assertThat(numbers.parse(",123,,456.")).containsExactly("", "123", "", "456").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothFail_firstErrorIsFarther() {
    var numbers =
        consecutive(DIGIT, "number")
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
        consecutive(DIGIT, "number")
            .orElse("")
            .delimitedBy(",")
            .followedBy(string("abc,1").followedBy("!").optional())
            .notEmpty();
    ParseException thrown = assertThrows(ParseException.class, () -> numbers.parse("abc,1."));
    assertThat(thrown).hasMessageThat().contains("at 1:6: expecting <!>, encountered [.]");
  }

  @Test
  public void expecting_primaryParserFails() {
    Parser<String> parser =
        consecutive(DIGIT, "number").expecting(s -> s.length() > 1, "long number");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("abc"));
    assertThat(e).hasMessageThat().contains("expecting <number>, encountered [abc]");
  }

  @Test
  public void expecting_predicateFails() {
    Parser<?> parser =
        consecutive(DIGIT, "number").expecting(s -> s.length() > 3, "long number").atLeastOnceDelimitedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("1234,5"));
    assertThat(e).hasMessageThat().contains("at 1:6: expecting <long number>, encountered [5]");
  }

  @Test
  public void expecting_succeeds() {
    Parser<String> parser =
        consecutive(DIGIT, "number").expecting(s -> s.length() > 1, "long number");
    assertThat(parser.parse("123")).isEqualTo("123");
  }

  @Test
  public void skipping_anyOfWithLiterally() {
    Parser<String> parser = anyOf(string("foo"), literally(consecutive(DIGIT, "digit")));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo")).containsExactly("foo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(Character::isWhitespace, " 123"));
    assertThrows(
        ParseException.class, () -> parser.parseToStreamSkipping(Character::isWhitespace, " 123").toList());
  }

  @Test
  public void skipping_anyOfWithoutLiterally() {
    Parser<String> parser = anyOf(string("foo"), consecutive(DIGIT, "digit"));
    assertThat(parser.parseSkipping(Character::isWhitespace, " foo")).isEqualTo("foo");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(Character::isWhitespace, " 123")).isEqualTo("123");
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " 123")).containsExactly("123");
  }

  @Test
  public void skipping_propagatesThroughLazyParser() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parseSkipping(Character::isWhitespace, " ( 2 ) + 3 ")).isEqualTo(5);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " ( 2 ) + 3 ")).containsExactly(5);
    assertThat(parser.parseSkipping(Character::isWhitespace, " ( 2 + ( 3 + 4 ) ) ")).isEqualTo(9);
    assertThat(parser.parseToStreamSkipping(Character::isWhitespace, " ( 2 + ( 3 + 4 ) ) "))
        .containsExactly(9);
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
    Parser.Lazy<Integer> lazy = new Parser.Lazy<>();
    Parser<Integer> num = Parser.single(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> atomic = lazy.between("(", ")").or(num);
    Parser<Integer> expr =
        atomic.atLeastOnceDelimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
    return lazy.delegateTo(expr);
  }

  @Test
  public void lazy_setTwice_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    lazy.delegateTo(string("a"));
    assertThrows(IllegalStateException.class, () -> lazy.delegateTo(string("b")));
  }

  @Test
  public void lazy_setNull_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    Parser<String> parser = null;
    assertThrows(NullPointerException.class, () -> lazy.delegateTo(parser));
  }

  @Test
  public void lazy_lazyParseBeforeSet_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    assertThrows(IllegalStateException.class, () -> lazy.parse("a"));
    assertThrows(IllegalStateException.class, () -> lazy.parseToStream("a").toList());
  }

  @Test
  public void lazy_delegateToLazy_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    Parser<String> actuallyLazy = lazy;
    assertThrows(IllegalArgumentException.class, () -> lazy.delegateTo(actuallyLazy));
  }

  @Test
  public void parseToStream_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream("123")).containsExactly('1', '2', '3').inOrder();
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_emptyInput() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_fail() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parseToStream("1a2").toList());
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
        return new Format(template.toString(), placeholders.stream().collect(toUnmodifiableList()));
      }
    }

    static Format parse(String format) {
      Parser.Lazy<Format> lazy = new Parser.Lazy<>();
      Parser<String> placeholderName =
          consecutive(range('a', 'z'), "placeholder name");
      Parser<Placeholder> placeholder =
          Parser.sequence(placeholderName.followedBy("="), lazy, Placeholder::new)
              .between("{", "}");
      Parser<Format> parser =
          anyOf(
                  placeholder,
                  Parser.string("{{").thenReturn("{"), // escape {
                  Parser.string("}}").thenReturn("}"), // escape }
                  Parser.consecutive(noneOf("{}"), "literal text"))
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
      return lazy.delegateTo(parser).parse(format);
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

    static ResourceNamePattern parse(String path) {
      Parser<String> name = Parser.consecutive(ALPHANUMERIC, "name");
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
          .optionallyFollowedBy(revision.map(v -> pattern -> pattern.withRevision(v)))
          .parse(path);
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
  public void sourcePosition_emptyString() {
    assertThat(sourcePosition("", 0)).isEqualTo("1:1");
  }

  @Test
  public void sourcePosition_singleLine() {
    assertThat(sourcePosition("abc", 0)).isEqualTo("1:1");
    assertThat(sourcePosition("abc", 1)).isEqualTo("1:2");
    assertThat(sourcePosition("abc", 3)).isEqualTo("1:4");
  }

  @Test
  public void sourcePosition_singleLineEndingWithNewline() {
    assertThat(sourcePosition("abc\n", 3)).isEqualTo("1:4");
    assertThat(sourcePosition("abc\n", 4)).isEqualTo("2:1");
  }

  @Test
  public void sourcePosition_twoLines() {
    assertThat(sourcePosition("abc\ndef", 3)).isEqualTo("1:4");
    assertThat(sourcePosition("abc\ndef", 4)).isEqualTo("2:1");
    assertThat(sourcePosition("abc\ndef", 5)).isEqualTo("2:2");
  }

  @Test
  public void sourcePosition_twoLinesEndingWithNewline() {
    assertThat(sourcePosition("abc\ndef\n", 7)).isEqualTo("2:4");
    assertThat(sourcePosition("abc\ndef\n", 8)).isEqualTo("3:1");
  }

  @Test
  public void sourcePosition_threeLines() {
    assertThat(sourcePosition("abc\ndef\nghi", 5)).isEqualTo("2:2");
    assertThat(sourcePosition("abc\ndef\nghi", 8)).isEqualTo("3:1");
  }
}

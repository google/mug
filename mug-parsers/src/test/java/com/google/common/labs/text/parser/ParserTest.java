package com.google.common.labs.text.parser;


import static com.google.common.labs.text.parser.Parser.anyOf;
import static com.google.common.labs.text.parser.Parser.consecutive;
import static com.google.common.labs.text.parser.Parser.literal;
import static com.google.common.labs.text.parser.Parser.sequence;
import static com.google.common.labs.text.parser.Parser.single;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.labs.text.parser.Parser.ParseException;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class ParserTest {
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');

  @Test
  public void literal_success() {
    Parser<String> parser = literal("foo");
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo").toList()).containsExactly("foo");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void literal_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("foo").parse("fooa"));
    assertThrows(ParseException.class, () -> literal("foo").parseToStream("fooa").toList());
  }

  @Test
  public void literal_failure() {
    assertThrows(ParseException.class, () -> literal("foo").parse("fo"));
    assertThrows(ParseException.class, () -> literal("foo").parseToStream("fo").toList());
    assertThrows(ParseException.class, () -> literal("foo").parse("food"));
    assertThrows(ParseException.class, () -> literal("foo").parseToStream("food").toList());
    assertThrows(ParseException.class, () -> literal("foo").parse("bar"));
    assertThrows(ParseException.class, () -> literal("foo").parseToStream("bar").toList());
  }

  @Test
  public void literal_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> literal(""));
  }

  @Test
  public void thenReturn_success() {
    Parser<Integer> parser1 = literal("one").thenReturn(1);
    assertThat(parser1.parse("one")).isEqualTo(1);
    assertThat(parser1.parseToStream("one").toList()).containsExactly(1);
    assertThat(parser1.parseToStream("").toList()).isEmpty();

    Parser<String> parser2 = literal("two").thenReturn("deux");
    assertThat(parser2.parse("two")).isEqualTo("deux");
    assertThat(parser2.parseToStream("two").toList()).containsExactly("deux");
    assertThat(parser2.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void thenReturn_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("one").thenReturn(1).parse("onea"));
    assertThrows(
        ParseException.class, () -> literal("one").thenReturn(1).parseToStream("onea").toList());
  }

  @Test
  public void thenReturn_failure() {
    assertThrows(ParseException.class, () -> literal("one").thenReturn(1).parse("two"));
    assertThrows(
        ParseException.class, () -> literal("one").thenReturn(1).parseToStream("two").toList());
  }

  @Test
  public void map_success() {
    Parser<Integer> parser1 = literal("123").map(Integer::parseInt);
    assertThat(parser1.parse("123")).isEqualTo(123);
    assertThat(parser1.parseToStream("123").toList()).containsExactly(123);
    assertThat(parser1.parseToStream("").toList()).isEmpty();

    Parser<Boolean> parser2 = literal("true").map(Boolean::parseBoolean);
    assertThat(parser2.parse("true")).isTrue();
    assertThat(parser2.parseToStream("true").toList()).containsExactly(true);
    assertThat(parser2.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void map_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("123").map(Integer::parseInt).parse("123a"));
    assertThrows(
        ParseException.class,
        () -> literal("123").map(Integer::parseInt).parseToStream("123a").toList());
  }

  @Test
  public void map_failure() {
    assertThrows(ParseException.class, () -> literal("abc").map(Integer::parseInt).parse("def"));
    assertThrows(
        ParseException.class,
        () -> literal("abc").map(Integer::parseInt).parseToStream("def").toList());
  }

  @Test
  public void flatMap_success() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> literal("=" + number));
    assertThat(parser.parse("123=123")).isEqualTo("=123");
    assertThat(parser.parseToStream("123=123").toList()).containsExactly("=123");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void flatMap_failure_withLeftover() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> literal("=" + number));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("123=123???"));
    assertThat(thrown).hasMessageThat().contains("at 7: ???");
    assertThrows(ParseException.class, () -> parser.parseToStream("123=123???").toList());
  }

  @Test
  public void flatMap_failure() {
    Parser<String> parser =
        Parser.consecutive(DIGIT, "number").flatMap(number -> literal("=" + number));
    assertThrows(ParseException.class, () -> parser.parse("=123"));
    assertThrows(ParseException.class, () -> parser.parseToStream("=123").toList());
    assertThrows(ParseException.class, () -> parser.parse("123=124"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123=124").toList());
  }

  @Test
  public void then_success() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThat(parser.parse("value:123")).isEqualTo(123);
    assertThat(parser.parseToStream("value:123").toList()).containsExactly(123);
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void then_failure_withLeftover() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:123a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("value:123a").toList());
  }

  @Test
  public void then_failure() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:abc"));
    assertThrows(ParseException.class, () -> parser.parseToStream("value:abc").toList());
    assertThrows(ParseException.class, () -> parser.parse("val:123"));
    assertThrows(ParseException.class, () -> parser.parseToStream("val:123").toList());
  }

  @Test
  public void followedBy_success() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThat(parser.parse("123บาท")).isEqualTo("123");
    assertThat(parser.parseToStream("123บาท").toList()).containsExactly("123");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void followedBy_failure_withLeftover() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123บาทa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123บาทa").toList());
  }

  @Test
  public void followedBy_failure() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123baht"));
    assertThrows(ParseException.class, () -> parser.parseToStream("123baht").toList());
    assertThrows(ParseException.class, () -> parser.parse("456บาท"));
    assertThrows(ParseException.class, () -> parser.parseToStream("456บาท").toList());
  }

  @Test
  public void optionallyFollowedBy_suffixCannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> literal("123").optionallyFollowedBy(""));
  }

  @Test
  public void optionallyFollowedBy_success() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.parse("123++")).isEqualTo(124);
    assertThat(parser.parseToStream("123++").toList()).containsExactly(124);
    assertThat(parser.parse("123")).isEqualTo(123);
    assertThat(parser.parseToStream("123").toList()).containsExactly(123);
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void optionallyFollowedBy_unconsumedInput() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("123+"));
    assertThat(thrown).hasMessageThat().contains("at 3: +");
    assertThrows(ParseException.class, () -> parser.parseToStream("123+").toList());
  }

  @Test
  public void optionallyFollowedBy_failedToMatch() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("abc"));
    assertThat(thrown).hasMessageThat().contains("at 0: expecting `123`");
    assertThrows(ParseException.class, () -> parser.parseToStream("abc").toList());
  }

  @Test
  public void sequence_success() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.parse("onetwo")).isEqualTo("1+2=3");
    assertThat(parser.parseToStream("onetwo").toList()).containsExactly("1+2=3");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void sequence_failure_withLeftover() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("onetwoa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("onetwoa").toList());
  }

  @Test
  public void sequence_failure() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("one-two"));
    assertThrows(ParseException.class, () -> parser.parseToStream("one-two").toList());
  }

  @Test
  public void or_success() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo").toList()).containsExactly("foo");
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.parseToStream("bar").toList()).containsExactly("bar");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void or_failure_withLeftover() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThrows(ParseException.class, () -> parser.parse("fooa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("fooa").toList());
    assertThrows(ParseException.class, () -> parser.parse("bara"));
    assertThrows(ParseException.class, () -> parser.parseToStream("bara").toList());
  }

  @Test
  public void or_failure() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThrows(ParseException.class, () -> parser.parse("baz"));
    assertThrows(ParseException.class, () -> parser.parseToStream("baz").toList());
  }

  @Test
  public void anyOf_success() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThat(parser.parse("one")).isEqualTo("one");
    assertThat(parser.parseToStream("one").toList()).containsExactly("one");
    assertThat(parser.parse("two")).isEqualTo("two");
    assertThat(parser.parseToStream("two").toList()).containsExactly("two");
    assertThat(parser.parse("three")).isEqualTo("three");
    assertThat(parser.parseToStream("three").toList()).containsExactly("three");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void anyOf_failure_withLeftover() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThrows(ParseException.class, () -> parser.parse("onea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("onea").toList());
    assertThrows(ParseException.class, () -> parser.parse("twoa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("twoa").toList());
    assertThrows(ParseException.class, () -> parser.parse("threea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("threea").toList());
  }

  @Test
  public void anyOf_failure() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThrows(ParseException.class, () -> parser.parse("four"));
    assertThrows(ParseException.class, () -> parser.parseToStream("four").toList());
  }

  @Test
  public void atLeastOnce_success() {
    Parser<List<String>> parser = literal("a").atLeastOnce();
    assertThat(parser.parse("a")).containsExactly("a");
    assertThat(parser.parseToStream("a").toList()).containsExactly(asList("a"));
    assertThat(parser.parse("aa")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa").toList()).containsExactly(asList("a", "a"));
    assertThat(parser.parse("aaa")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa").toList())
        .containsExactly(asList("a", "a", "a"));
    assertThat(parser.parseToStream("").toList()).isEmpty();

    Parser<List<String>> parser2 =
        consecutive(CharPredicate.range('0', '9'), "digit").atLeastOnce();
    assertThat(parser2.parse("1230")).containsExactly("1230");
    assertThat(parser2.parseToStream("1230").toList()).containsExactly(asList("1230"));
    assertThat(parser2.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void atLeastOnce_failure_withLeftover() {
    Parser<List<String>> parser = literal("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("ab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("ab").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void atLeastOnce_failure() {
    Parser<List<String>> parser = literal("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("b").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void zeroOrMoreBetween_zeroMatch() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]").toList()).containsExactly(ImmutableList.of());
  }

  @Test
  public void zeroOrMoreBetween_oneMatch() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]").toList()).containsExactly(ImmutableList.of("a"));
  }

  @Test
  public void zeroOrMoreBetween_multipleMatches() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", "]");
    assertThat(parser.parse("[aa]")).containsExactly("a", "a");
    assertThat(parser.parseToStream("[aa]").toList()).containsExactly(ImmutableList.of("a", "a"));
    assertThat(parser.parse("[aaa]")).containsExactly("a", "a", "a");
    assertThat(parser.parseToStream("[aaa]").toList())
        .containsExactly(ImmutableList.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreBetween_withDelimiter_zeroMatch() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", ",", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]").toList()).containsExactly(ImmutableList.of());
  }

  @Test
  public void zeroOrMoreBetween_withDelimiter_oneMatch() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", ",", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]").toList()).containsExactly(ImmutableList.of("a"));
  }

  @Test
  public void zeroOrMoreBetween_withDelimiter_multipleMatches() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", ",", "]");
    assertThat(parser.parse("[a,a]")).containsExactly("a", "a");
    assertThat(parser.parseToStream("[a,a]").toList()).containsExactly(ImmutableList.of("a", "a"));
    assertThat(parser.parse("[a,a,a]")).containsExactly("a", "a", "a");
    assertThat(parser.parseToStream("[a,a,a]").toList())
        .containsExactly(ImmutableList.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreBetween_failure() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[ab]"));
    assertThrows(ParseException.class, () -> parser.parse("[a]b"));
  }

  @Test
  public void zeroOrMoreBetween_withDelimiter_failure() {
    Parser<List<String>> parser = literal("a").zeroOrMoreBetween("[", ",", "]");
    assertThrows(ParseException.class, () -> parser.parse("[a,b]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[,a]"));
  }

  @Test
  public void delimitedBy_success() {
    Parser<List<String>> parser = literal("a").delimitedBy(",");
    assertThat(parser.parse("a")).containsExactly("a").inOrder();
    assertThat(parser.parseToStream("a").toList()).containsExactly(asList("a"));
    assertThat(parser.parse("a,a")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a").toList()).containsExactly(asList("a", "a"));
    assertThat(parser.parse("a,a,a")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a").toList())
        .containsExactly(asList("a", "a", "a"));
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void delimitedBy_failure_withLeftover() {
    Parser<List<String>> parser = literal("a").delimitedBy(",");
    assertThrows(ParseException.class, () -> parser.parse("aa"));
    assertThrows(ParseException.class, () -> parser.parse("a,ab"));
  }

  @Test
  public void delimitedBy_failure() {
    Parser<List<String>> parser = literal("a").delimitedBy(",");
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
  public void delimitedBy_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> literal("a").delimitedBy(""));
  }

  @Test
  public void immediatelyBetween_success() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[content]")).isEqualTo("content");
    assertThat(parser.parseToStream("[content]").toList()).containsExactly("content");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void immediatelyBetween_failure_withLeftover() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[content]a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[content]a").toList());
  }

  @Test
  public void immediatelyBetween_failure() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
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
  public void immediatelyBetween_cannotBeEmpty() {
    Parser<String> parser = literal("content");
    assertThrows(IllegalArgumentException.class, () -> parser.immediatelyBetween("", "]"));
    assertThrows(IllegalArgumentException.class, () -> parser.immediatelyBetween("[", ""));
  }

  @Test
  public void single_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parseToStream("1").toList()).containsExactly('1');
    assertThat(parser.parse("9")).isEqualTo('9');
    assertThat(parser.parseToStream("9").toList()).containsExactly('9');
    assertThat(parser.parseToStream("").toList()).isEmpty();
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
    assertThat(parser.parseToStream("1").toList()).containsExactly("1");
    assertThat(parser.parse("123")).isEqualTo("123");
    assertThat(parser.parseToStream("123").toList()).containsExactly("123");
    assertThat(parser.parseToStream("").toList()).isEmpty();
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
  public void postfix_success() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10").toList()).containsExactly(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++").toList()).containsExactly(11);
    assertThat(parser.parse("10--")).isEqualTo(9);
    assertThat(parser.parseToStream("10--").toList()).containsExactly(9);
    assertThat(parser.parse("10++--++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++--++").toList()).containsExactly(11);
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void postfix_failure() {
    Parser<Integer> number = consecutive(DIGIT, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
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
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThrows(ParseException.class, () -> parser.parse("10++a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10++a").toList());
    assertThrows(ParseException.class, () -> parser.parse("10 a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10 a").toList());
  }

  @Test
  public void recursiveGrammar() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parseToStream("1").toList()).containsExactly(1);
    assertThat(parser.parse("(2)")).isEqualTo(2);
    assertThat(parser.parseToStream("(2)").toList()).containsExactly(2);
    assertThat(parser.parse("(2)+3")).isEqualTo(5);
    assertThat(parser.parseToStream("(2)+3").toList()).containsExactly(5);
    assertThat(parser.parse("(2)+3+(4)")).isEqualTo(9);
    assertThat(parser.parseToStream("(2)+3+(4)").toList()).containsExactly(9);
    assertThat(parser.parse("(2+(3+4))")).isEqualTo(9);
    assertThat(parser.parseToStream("(2+(3+4))").toList()).containsExactly(9);
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  private static Parser<Integer> simpleCalculator() {
    Parser.Lazy<Integer> lazy = new Parser.Lazy<>();
    Parser<Integer> num = Parser.single(CharPredicate.range('0', '9'), "digit").map(c -> c - '0');
    Parser<Integer> atomic = lazy.immediatelyBetween("(", ")").or(num);
    Parser<Integer> expr =
        atomic.delimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
    return lazy.delegateTo(expr);
  }

  @Test
  public void lazy_setTwice_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    lazy.delegateTo(literal("a"));
    assertThrows(IllegalStateException.class, () -> lazy.delegateTo(literal("b")));
  }

  @Test
  public void lazy_setNull_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    assertThrows(NullPointerException.class, () -> lazy.delegateTo(null));
  }

  @Test
  public void lazy_lazyParseBeforeSet_throws() {
    Parser.Lazy<String> lazy = new Parser.Lazy<>();
    assertThrows(IllegalStateException.class, () -> lazy.parse("a"));
    assertThrows(IllegalStateException.class, () -> lazy.parseToStream("a").toList());
  }

  @Test
  public void parseToStream_success() {
    Parser<Character> parser = single(DIGIT, "digit");
    assertThat(parser.parseToStream("123").toList()).containsExactly('1', '2', '3').inOrder();
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
                asList(
                    new Format.Placeholder(
                        "b",
                        new Format(
                            "xy{foo}z",
                            asList(
                                new Format.Placeholder(
                                    "foo", new Format("bar", asList()))))),
                    new Format.Placeholder("e", new Format("f", asList())))));
  }

  /** An example nested placeholder grammar for demo purpose. */
  private record Format(String template, List<Placeholder> placeholders) {

    record Placeholder(String name, Format format) {}

    static class Builder {
      private final Stream.Builder<Placeholder> placeholders = Stream.builder();
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
        that.placeholders.build().forEach(placeholders::add);
        return this;
      }

      Format build() {
        return new Format(template.toString(), placeholders.build().toList());
      }
    }

    static Format parse(String format) {
      Parser.Lazy<Format> lazy = new Parser.Lazy<>();
      Parser<String> placeholderName =
          Parser.consecutive(CharPredicate.range('a', 'z'), "placeholder name");
      Parser<Placeholder> placeholder =
          Parser.sequence(placeholderName.followedBy("="), lazy, Placeholder::new)
              .immediatelyBetween("{", "}");
      Parser<Format> parser =
          Parser.anyOf(
                  placeholder,
                  Parser.literal("{{").thenReturn("{"), // escape {
                  Parser.literal("}}").thenReturn("}"), // escape }
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

    private static final CharPredicate ALPHANUMERIC =
        CharPredicate.range('a', 'z')
            .or(CharPredicate.range('A', 'Z'))
            .or(CharPredicate.range('0', '9'));

    static ResourceNamePattern parse(String path) {
      Parser<String> name = Parser.consecutive(ALPHANUMERIC, "name");
      Parser<String> revision = literal("@").then(name);
      Parser<PathElement.Subpath> subpath =
          Parser.sequence(
                  name.followedBy("="),
                  Parser.<PathElement>anyOf(
                          name.map(PathElement.Literal::new),
                          literal("**").thenReturn(new PathElement.SubpathWildcard()),
                          literal("*").thenReturn(new PathElement.PathElementWildcard()))
                      .delimitedBy("/")
                      .map(ResourceNamePattern::new),
                  PathElement.Subpath::new)
              .immediatelyBetween("{", "}");
      return Parser.<PathElement>anyOf(
              name.map(PathElement.Literal::new),
              name.immediatelyBetween("{", "}").map(PathElement.Placeholder::new),
              subpath)
          .delimitedBy("/")
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
        .isEqualTo(new ResourceNamePattern(asList(new PathElement.Literal("users"))));
  }

  @Test
  public void resourceNamePattern_withSimplePlaceholder() {
    assertThat(ResourceNamePattern.parse("users/{userId}/messages/{messageId}"))
        .isEqualTo(
            new ResourceNamePattern(
                asList(
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
                asList(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            asList(
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
                asList(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            asList(
                                new PathElement.Literal("projects"),
                                new PathElement.SubpathWildcard()))),
                    new PathElement.Literal("messages"))));
  }
}


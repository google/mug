package com.google.common.labs.regex;

import static com.google.common.labs.regex.Parser.anyOf;
import static com.google.common.labs.regex.Parser.consecutive;
import static com.google.common.labs.regex.Parser.literal;
import static com.google.common.labs.regex.Parser.sequence;
import static com.google.common.labs.regex.Parser.single;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.labs.regex.Parser.ParseException;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class ParserTest {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z').or('_');

  @Test
  public void literal_success() {
    assertThat(literal("foo").parse("foo")).isEqualTo("foo");
  }

  @Test
  public void literal_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("foo").parse("fooa"));
  }

  @Test
  public void literal_failure() {
    assertThrows(ParseException.class, () -> literal("foo").parse("fo"));
    assertThrows(ParseException.class, () -> literal("foo").parse("food"));
    assertThrows(ParseException.class, () -> literal("foo").parse("bar"));
  }

  @Test
  public void literal_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> literal(""));
  }

  @Test
  public void thenReturn_success() {
    assertThat(literal("one").thenReturn(1).parse("one")).isEqualTo(1);
    assertThat(literal("two").thenReturn("deux").parse("two")).isEqualTo("deux");
  }

  @Test
  public void thenReturn_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("one").thenReturn(1).parse("onea"));
  }

  @Test
  public void thenReturn_failure() {
    assertThrows(ParseException.class, () -> literal("one").thenReturn(1).parse("two"));
  }

  @Test
  public void map_success() {
    assertThat(literal("123").map(Integer::parseInt).parse("123")).isEqualTo(123);
    assertThat(literal("true").map(Boolean::parseBoolean).parse("true")).isTrue();
  }

  @Test
  public void map_failure_withLeftover() {
    assertThrows(ParseException.class, () -> literal("123").map(Integer::parseInt).parse("123a"));
  }

  @Test
  public void map_failure() {
    assertThrows(ParseException.class, () -> literal("abc").map(Integer::parseInt).parse("def"));
  }

  @Test
  public void flatMap_success() {
    Parser<String> keywordParser = anyOf(literal("name:"), literal("age:"));
    Parser<String> parser =
        keywordParser.flatMap(
            keyword -> {
              if (keyword.equals("name:")) {
                return consecutive(ALPHA, "letter");
              } else {
                return consecutive(NUM, "digit");
              }
            });
    assertThat(parser.parse("name:john")).isEqualTo("john");
    assertThat(parser.parse("age:42")).isEqualTo("42");
  }

  @Test
  public void flatMap_failure_withLeftover() {
    Parser<String> keywordParser = anyOf(literal("name:"), literal("age:"));
    Parser<String> parser =
        keywordParser.flatMap(
            keyword -> {
              if (keyword.equals("name:")) {
                return consecutive(ALPHA, "letter");
              } else {
                return consecutive(NUM, "digit");
              }
            });
    assertThrows(ParseException.class, () -> parser.parse("name:johna "));
    assertThrows(ParseException.class, () -> parser.parse("age:42 "));
  }

  @Test
  public void flatMap_failure() {
    Parser<String> keywordParser = anyOf(literal("name:"), literal("age:"));
    Parser<String> parser =
        keywordParser.flatMap(
            keyword -> {
              if (keyword.equals("name:")) {
                return consecutive(ALPHA, "letter");
              } else {
                return consecutive(NUM, "digit");
              }
            });
    assertThrows(ParseException.class, () -> parser.parse("name:123"));
    assertThrows(ParseException.class, () -> parser.parse("age:john"));
    assertThrows(ParseException.class, () -> parser.parse("id:foo"));
  }

  @Test
  public void then_success() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThat(parser.parse("value:123")).isEqualTo(123);
  }

  @Test
  public void then_failure_withLeftover() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:123a"));
  }

  @Test
  public void then_failure() {
    Parser<Integer> parser = literal("value:").then(literal("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:abc"));
    assertThrows(ParseException.class, () -> parser.parse("val:123"));
  }

  @Test
  public void followedBy_success() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThat(parser.parse("123บาท")).isEqualTo("123");
  }

  @Test
  public void followedBy_failure_withLeftover() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123บาทa"));
  }

  @Test
  public void followedBy_failure() {
    Parser<String> parser = literal("123").followedBy(literal("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123baht"));
    assertThrows(ParseException.class, () -> parser.parse("456บาท"));
  }

  @Test
  public void optionallyFollowedBy_suffixCannotBeEmpty() {
    assertThrows(
        IllegalArgumentException.class, () -> literal("123").optionallyFollowedBy("", n -> n));
  }

  @Test
  public void optionallyFollowedBy_success() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.parse("123++")).isEqualTo(124);
    assertThat(parser.parse("123")).isEqualTo(123);
  }

  @Test
  public void optionallyFollowedBy_unconsumedInput() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("123+"));
    assertThat(thrown).hasMessageThat().contains("at 3: +");
  }

  @Test
  public void optionallyFollowedBy_failedToMatch() {
    Parser<Integer> parser =
        literal("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("abc"));
    assertThat(thrown).hasMessageThat().contains("at 0: expecting `123`");
  }

  @Test
  public void sequence_success() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.parse("onetwo")).isEqualTo("1+2=3");
  }

  @Test
  public void sequence_failure_withLeftover() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("onetwoa"));
  }

  @Test
  public void sequence_failure() {
    Parser<String> parser =
        sequence(
            literal("one").map(s -> 1),
            literal("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("one-two"));
  }

  @Test
  public void or_success() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parse("bar")).isEqualTo("bar");
  }

  @Test
  public void or_failure_withLeftover() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThrows(ParseException.class, () -> parser.parse("fooa"));
    assertThrows(ParseException.class, () -> parser.parse("bara"));
  }

  @Test
  public void or_failure() {
    Parser<String> parser = literal("foo").or(literal("bar"));
    assertThrows(ParseException.class, () -> parser.parse("baz"));
  }

  @Test
  public void anyOf_success() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThat(parser.parse("one")).isEqualTo("one");
    assertThat(parser.parse("two")).isEqualTo("two");
    assertThat(parser.parse("three")).isEqualTo("three");
  }

  @Test
  public void anyOf_failure_withLeftover() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThrows(ParseException.class, () -> parser.parse("onea"));
    assertThrows(ParseException.class, () -> parser.parse("twoa"));
    assertThrows(ParseException.class, () -> parser.parse("threea"));
  }

  @Test
  public void anyOf_failure() {
    Parser<String> parser = anyOf(literal("one"), literal("two"), literal("three"));
    assertThrows(ParseException.class, () -> parser.parse("four"));
  }

  @Test
  public void atLeastOnce_success() {
    assertThat(literal("a").atLeastOnce().parse("a")).containsExactly("a").inOrder();
    assertThat(literal("a").atLeastOnce().parse("aa")).containsExactly("a", "a").inOrder();
    assertThat(literal("a").atLeastOnce().parse("aaa")).containsExactly("a", "a", "a").inOrder();
    assertThat(consecutive(CharPredicate.range('0', '9'), "digit").atLeastOnce().parse("1230"))
        .containsExactly("1230")
        .inOrder();
  }

  @Test
  public void atLeastOnce_failure_withLeftover() {
    Parser<List<String>> parser = literal("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("ab"));
    assertThrows(ParseException.class, () -> parser.parse("aab"));
  }

  @Test
  public void atLeastOnce_failure() {
    Parser<List<String>> parser = literal("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parse("aab"));
  }

  @Test
  public void delimitedBy_success() {
    Parser<List<String>> parser = literal("a").delimitedBy(",");
    assertThat(parser.parse("a")).containsExactly("a").inOrder();
    assertThat(parser.parse("a,a")).containsExactly("a", "a").inOrder();
    assertThat(parser.parse("a,a,a")).containsExactly("a", "a", "a").inOrder();
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
    assertThrows(ParseException.class, () -> parser.parse("a,"));
    assertThrows(ParseException.class, () -> parser.parse(",a"));
    assertThrows(ParseException.class, () -> parser.parse("a,b"));
    assertThrows(ParseException.class, () -> parser.parse("a,,a"));
  }

  @Test
  public void delimitedBy_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> literal("a").delimitedBy(""));
  }

  @Test
  public void immediatelyBetween_success() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[content]")).isEqualTo("content");
  }

  @Test
  public void immediatelyBetween_failure_withLeftover() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[content]a"));
  }

  @Test
  public void immediatelyBetween_failure() {
    Parser<String> parser = literal("content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("content]"));
    assertThrows(ParseException.class, () -> parser.parse("[content"));
    assertThrows(ParseException.class, () -> parser.parse("content"));
    assertThrows(ParseException.class, () -> parser.parse("[wrong]"));
    assertThrows(ParseException.class, () -> parser.parse(" [content]"));
  }

  @Test
  public void immediatelyBetween_cannotBeEmpty() {
    Parser<String> parser = literal("content");
    assertThrows(IllegalArgumentException.class, () -> parser.immediatelyBetween("", "]"));
    assertThrows(IllegalArgumentException.class, () -> parser.immediatelyBetween("[", ""));
  }

  @Test
  public void single_success() {
    Parser<Character> parser = single(NUM, "digit");
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parse("9")).isEqualTo('9');
  }

  @Test
  public void single_failure_withLeftover() {
    Parser<Character> parser = single(NUM, "digit");
    assertThrows(ParseException.class, () -> parser.parse("1a"));
  }

  @Test
  public void single_failure() {
    Parser<Character> parser = single(NUM, "digit");
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("12"));
    assertThrows(ParseException.class, () -> parser.parse(""));
  }

  @Test
  public void consecutive_success() {
    Parser<String> parser = consecutive(NUM, "digit");
    assertThat(parser.parse("1")).isEqualTo("1");
    assertThat(parser.parse("123")).isEqualTo("123");
  }

  @Test
  public void consecutive_failure_withLeftover() {
    Parser<String> parser = consecutive(NUM, "digit");
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parse("123a"));
  }

  @Test
  public void consecutive_failure() {
    Parser<String> parser = consecutive(NUM, "digit");
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("12a"));
    assertThrows(ParseException.class, () -> parser.parse(""));
  }

  @Test
  public void postfix_success() {
    Parser<Integer> number = consecutive(NUM, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parse("10--")).isEqualTo(9);
    assertThat(parser.parse("10++--++")).isEqualTo(11);
  }

  @Test
  public void postfix_failure() {
    Parser<Integer> number = consecutive(NUM, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThrows(ParseException.class, () -> parser.parse("a++"));
    assertThrows(ParseException.class, () -> parser.parse("10+"));
    assertThrows(ParseException.class, () -> parser.parse(""));
  }

  @Test
  public void postfix_failure_withLeftover() {
    Parser<Integer> number = consecutive(NUM, "digit").map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = literal("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = literal("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.postfix(op);
    assertThrows(ParseException.class, () -> parser.parse("10++a"));
    assertThrows(ParseException.class, () -> parser.parse("10 a"));
  }

  @Test
  public void recursiveGrammar() {
    assertThat(simpleCalculator().parse("1")).isEqualTo(1);
    assertThat(simpleCalculator().parse("(2)")).isEqualTo(2);
    assertThat(simpleCalculator().parse("(2)+3")).isEqualTo(5);
    assertThat(simpleCalculator().parse("(2)+3+(4)")).isEqualTo(9);
    assertThat(simpleCalculator().parse("(2+(3+4))")).isEqualTo(9);
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
  }
}

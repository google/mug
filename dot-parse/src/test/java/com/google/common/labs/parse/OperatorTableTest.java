package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.CharPredicate.range;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public final class OperatorTableTest {
  private final OperatorTable<Integer> operatorTable =
      new OperatorTable<Integer>()
          .leftAssociative("+", (l, r) -> l + r, 1)
          .leftAssociative("-", (l, r) -> l - r, 1)
          .leftAssociative("*", (l, r) -> l * r, 2)
          .rightAssociative("^", (l, r) -> (int) Math.pow(l, r), 3)
          .nonAssociative(">", (l, r) -> l > r ? 1 : 0, 0)
          .prefix("-", n -> -n, 10)
          .postfix("++", n -> n + 1, 8);

  @Test
  public void simpleCalculator_noOperator() {
    assertThat(parse(" 10")).isEqualTo(10);
  }

  @Test
  public void simpleCalculator_leftAssociativeInfix() {
    assertThat(parse("1+2 ")).isEqualTo(3);
    assertThat(parse("1 + 2 + 3")).isEqualTo(6);
    assertThat(parse("1 - 2 + 3")).isEqualTo(2);
    assertThat(parse("1 + 2 * 3 ^ 2")).isEqualTo(19);
  }

  @Test
  public void simpleCalculator_rightAssociativeInfix() {
    assertThat(parse("2 ^ 3 ")).isEqualTo(8);
    assertThat(parse("1 + 2 ^ 3")).isEqualTo(9);
    assertThat(parse("1 - -2 ^ 2")).isEqualTo(-3);
    assertThat(parse("2 ^ 3 ^ 2")).isEqualTo(512);
    assertThat(parse("1 + 2 * 3 ^ 2")).isEqualTo(19);
  }

  @Test
  public void simpleCalculator_nonAssociativeInfix() {
    assertThat(parse("2 > 1 + 2 ^ 2")).isEqualTo(0);
    assertThat(parse("1 + 2 > 2")).isEqualTo(1);
    assertThrows(Parser.ParseException.class, () -> parse("3 > 2 > 1"));
  }

  @Test
  public void simpleCalculator_prefix() {
    assertThat(parse("-1")).isEqualTo(-1);
    assertThat(parse("-1 + -2 - 3")).isEqualTo(-6);
    assertThat(parse("-1 - -2 + 3")).isEqualTo(4);
    assertThat(parse("-1 + --2 - 3")).isEqualTo(-2);
  }

  @Test
  public void simpleCalculator_postfix() {
    assertThat(parse("-1++")).isEqualTo(0);
    assertThat(parse("1 - 3++")).isEqualTo(-3);
    assertThat(parse("1 - 3++++")).isEqualTo(-4);
  }

  @Test
  public void simpleCalculator_withRecursion() {
    assertThat(parse("(-1)")).isEqualTo(-1);
    assertThat(parse("-1 - (-2 + 3)")).isEqualTo(-2);
  }

  @Test
  public void simpleCalculator_multilines() {
    String code =
        """
        ((1 + 2)
           * (3 + 4))
            - ((9 + 10) * (11 + 12))
        """;
    assertThat(parse(code)).isEqualTo(-416);
  }

  @Test
  public void testPostfix_parserWithBiFunction() {
    Parser<Expr> memberAccess =
        Parser.define(
            expr ->
                new OperatorTable<Expr>()
                    .postfix(string(".").then(word()), Call::new, 1)
                    .build(word().map(Var::new)));
    assertThat(memberAccess.parse("a.b")).isEqualTo(new Call(new Var("a"), "b"));
    assertThat(memberAccess.parse("a.b.c")).isEqualTo(new Call(new Call(new Var("a"), "b"), "c"));
  }

  private sealed interface Expr permits Var, Call {}

  private record Var(String name) implements Expr {}

  private record Call(Expr callee, String name) implements Expr {}

  @Test
  public void testNulls() {
    new NullPointerTester()
        .setDefault(String.class, "op")
        .setDefault(Parser.class, Parser.string("a"))
        .testAllPublicInstanceMethods(operatorTable);
  }

  private int parse(String input) {
    Parser<Integer> calculator = Parser.define(
        expr ->
            operatorTable.build(
                expr.between("(", ")")
                    .or(consecutive(range('0', '9'), "number").map(Integer::parseInt))));
    return calculator.parseSkipping(Character::isWhitespace, input);
  }
}

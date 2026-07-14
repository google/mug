package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Suffix.suffix;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.labs.parse.Parser.ParseException;

@RunWith(JUnit4.class)
public class SuffixTest {
  @Test
  public void sequence_withMandatorySuffix_success() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> parser = sequence(
        prefix,
        suffix("--", (Integer i) -> i - 1),
        Suffix::apply);
    assertThat(parser.parse("10--")).isEqualTo(9);
  }

  @Test
  public void sequence_withMandatorySuffix_mismatchThrows() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> parser = sequence(
        prefix,
        suffix("--", (Integer i) -> i - 1),
        Suffix::apply);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("10++"));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("expecting <-->");
  }

  @Test
  public void sequence_withMandatoryParserSuffix_success() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponent = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = sequence(
        prefix,
        suffix(exponent, (Integer i, Integer e) -> (int) Math.pow(i, e)),
        Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(8);
  }

  @Test
  public void sequence_withMandatoryParserSuffix_mismatchThrows() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponent = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = sequence(
        prefix,
        suffix(exponent, (Integer i, Integer e) -> (int) Math.pow(i, e)),
        Suffix::apply);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("2*3"));
    assertThat(thrown).hasMessageThat().contains("1:2");
    assertThat(thrown).hasMessageThat().contains("expecting <^>");
  }

  @Test
  public void suffix_withStringAndMapper_nullSuffixThrows() {
    assertThrows(NullPointerException.class, () -> suffix((String) null, i -> i));
  }

  @Test
  public void suffix_withStringAndMapper_nullMapperThrows() {
    assertThrows(NullPointerException.class, () -> suffix("--", (Function<Integer, Integer>) null));
  }

  @Test
  public void suffix_withParserAndCombiner_nullParserThrows() {
    assertThrows(NullPointerException.class, () -> suffix((Parser<Integer>) null, (i, e) -> i));
  }

  @Test
  public void suffix_withParserAndCombiner_nullCombinerThrows() {
    Parser<Integer> exponent = string("^").then(digits().map(Integer::parseInt));
    assertThrows(NullPointerException.class, () -> suffix(exponent, null));
  }

  @Test
  public void optionallyFollowedBy_firstExample_decrement() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("10--")).isEqualTo(9);
  }

  @Test
  public void optionallyFollowedBy_firstExample_exponential() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(8);
  }

  @Test
  public void optionallyFollowedBy_firstExample_noSuffix() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("10")).isEqualTo(10);
  }

  @Test
  public void sequence_secondExample_optional() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(
            suffix("?", (Integer i) -> new OptionalExpr(i)),
            suffix(exponential, (Integer i, Integer exp) -> new PowExpr(i, exp)))
            .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("10?")).isEqualTo(new OptionalExpr(10));
  }

  @Test
  public void sequence_secondExample_exponential() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(
            suffix("?", (Integer i) -> new OptionalExpr(i)),
            suffix(exponential, (Integer i, Integer exp) -> new PowExpr(i, exp)))
            .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(new PowExpr(2, 3));
  }

  @Test
  public void sequence_secondExample_literal() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(
            suffix("?", (Integer i) -> new OptionalExpr(i)),
            suffix(exponential, (Integer i, Integer exp) -> new PowExpr(i, exp)))
          .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("10")).isEqualTo(new LiteralExpr(10));
  }

  private interface Expr {}
  private record LiteralExpr(int value) implements Expr {}
  private record OptionalExpr(int value) implements Expr {}
  private record PowExpr(int base, int exponent) implements Expr {}
}

package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Suffix.suffix;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.common.labs.parse.Parsers.Suffix;
import com.google.common.testing.NullPointerTester;
import java.util.function.UnaryOperator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SuffixTest {
  @Test public void sequence_withStringSuffix_success() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> parser = sequence(prefix, suffix("--", (Integer i) -> i - 1), Suffix::apply);
    assertThat(parser.parse("10--")).isEqualTo(9);
  }

  @Test public void sequence_withStringSuffix_mismatchThrows() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> parser = sequence(prefix, suffix("--", (Integer i) -> i - 1), Suffix::apply);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("10++"));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("expecting <-->");
  }

  @Test public void sequence_withSuffix_success() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponent = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = sequence(
        prefix, suffix(exponent, (Integer i, Integer e) -> (int) Math.pow(i, e)), Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(8);
  }

  @Test public void sequence_withSuffix_mismatchThrows() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponent = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = sequence(
        prefix, suffix(exponent, (Integer i, Integer e) -> (int) Math.pow(i, e)), Suffix::apply);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("2*3"));
    assertThat(thrown).hasMessageThat().contains("1:2");
    assertThat(thrown).hasMessageThat().contains("expecting <^>");
  }

  @Test public void optionallyFollowedBy_twoSuffixes_firstSuffixMatches() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("10--")).isEqualTo(9);
  }

  @Test public void optionallyFollowedBy_twoSuffixes_secondSuffixMatches() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(8);
  }

  @Test public void optionallyFollowedBy_twoSuffixes_noSuffixMatch() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Integer> parser = prefix.optionallyFollowedBy(
        anyOf(
            suffix("--", (Integer i) -> i - 1),
            suffix(exponential, (Integer i, Integer e) -> (int) Math.pow(i, e))),
        Suffix::apply);
    assertThat(parser.parse("10")).isEqualTo(10);
  }

  @Test public void sequence_twoSuffixesWithDefault_firstSuffixMatches() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(suffix("?", OptionalExpr::new), suffix(exponential, PowExpr::new))
            .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("10?")).isEqualTo(new OptionalExpr(10));
  }

  @Test public void sequence_twoSuffixesWithDefault_secondSuffixMatches() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(suffix("?", OptionalExpr::new), suffix(exponential, PowExpr::new))
            .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("2^3")).isEqualTo(new PowExpr(2, 3));
  }

  @Test public void sequence_twoSuffixesWithDefault_noSuffixMatch_defaultApplied() {
    Parser<Integer> prefix = digits().map(Integer::parseInt);
    Parser<Integer> exponential = string("^").then(digits().map(Integer::parseInt));
    Parser<Expr> parser = sequence(
        prefix,
        anyOf(suffix("?", OptionalExpr::new), suffix(exponential, PowExpr::new))
            .orElse(LiteralExpr::new),
        Suffix::apply);
    assertThat(parser.parse("10")).isEqualTo(new LiteralExpr(10));
  }

  @Test public void withPrefixes_zeroOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test public void withPrefixes_zeroOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test public void withPrefixes_oneOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThat(parser.parse("-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-10")).containsExactly(-10);
  }

  @Test public void withPrefixes_oneOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThat(parser.source().parse("-10")).isEqualTo("-10");
    assertThat(parser.source().parseToStream("-10")).containsExactly("-10");
  }

  @Test public void withPrefixes_multipleOperators_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = Suffix.withPrefixes(op, number);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parse("--10")).isEqualTo(10);
    assertThat(parser.parse("-~10")).isEqualTo(- ~10);
    assertThat(parser.parse("~-10")).isEqualTo(~-10);
    assertThat(parser.parseToStream("--10")).containsExactly(10);
    assertThat(parser.parse("-+10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-+10")).containsExactly(-10);
    assertThat(parser.parse("+-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("+-10")).containsExactly(-10);
  }

  @Test public void withPrefixes_multipleOperators_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = Suffix.withPrefixes(op, number);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parse("--10")).isEqualTo("--10");
    assertThat(parser.source().parse("-~10")).isEqualTo("-~10");
    assertThat(parser.source().parse("~-10")).isEqualTo("~-10");
    assertThat(parser.source().parseToStream("--10")).containsExactly("--10");
    assertThat(parser.source().parse("-+10")).isEqualTo("-+10");
    assertThat(parser.source().parseToStream("-+10")).containsExactly("-+10");
    assertThat(parser.source().parse("+-10")).isEqualTo("+-10");
    assertThat(parser.source().parseToStream("+-10")).containsExactly("+-10");
  }

  @Test public void withPrefixes_operandParseFails() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-a").toList());
  }

  @Test public void withPrefixes_failure_withLeftover() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThrows(ParseException.class, () -> parser.parse("10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-10a").toList());
  }

  @Test public void withPrefixes_stringPrefix_multiplePrefixes() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = Suffix.withPrefixes("-", number, i -> -i);
    assertThat(parser.parse("--10")).isEqualTo(10);
    assertThat(parser.parse("---10")).isEqualTo(-10);
    assertThat(parser.parseToStream("---10")).containsExactly(-10);
  }

  @Test public void withPrefixes_withBiFunction_success() {
    Parser<Integer> prefix = anyOf(string("2x").thenReturn(2), string("3x").thenReturn(3));
    Parser<Integer> suffix = digits().map(Integer::parseInt);
    Parser<Integer> parser =
        Suffix.withPrefixes(prefix, suffix, (multiplier, val) -> multiplier * val);
    assertThat(parser.parse("5")).isEqualTo(5);
    assertThat(parser.parse("2x5")).isEqualTo(10);
    assertThat(parser.parse("3x2x5")).isEqualTo(30);
    assertThat(parser.parseToStream("3x2x5")).containsExactly(30);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test public void withPrefixes_withBiFunction_failure() {
    Parser<Integer> prefix = anyOf(string("2x").thenReturn(2), string("3x").thenReturn(3));
    Parser<Integer> suffix = digits().map(Integer::parseInt);
    Parser<Integer> parser =
        Suffix.withPrefixes(prefix, suffix, (multiplier, val) -> multiplier * val);
    assertThrows(ParseException.class, () -> parser.parse("2x"));
    assertThrows(ParseException.class, () -> parser.parse("2x3x"));
    assertThrows(ParseException.class, () -> parser.parse("2x5a"));
  }


  @Test public void testNulls() {
    new NullPointerTester()
        .setDefault(String.class, ".")
        .setDefault(Parser.class, string("foo"))
        .testAllPublicStaticMethods(Suffix.class);
  }

  private interface Expr {}

  private record LiteralExpr(int value) implements Expr {}

  private record OptionalExpr(int value) implements Expr {}

  private record PowExpr(int base, int exponent) implements Expr {}
}

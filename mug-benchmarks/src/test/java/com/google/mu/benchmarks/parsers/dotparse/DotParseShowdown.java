package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.parse.OperatorTable;
import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;

public final class DotParseShowdown {

  public static class IpFixture {
    private final Parser<?> parser;

    public IpFixture() {
      Parser<?> dot = Parser.string(".");
      this.parser = Parser.sequence(
          Parser.digits(), dot,
          Parser.digits(), dot,
          Parser.digits(), dot,
          Parser.digits()
      ).thenReturn("ip");

      // Verify
      assertThat(parser.parse(BenchmarkInputs.IP)).isNotNull();
    }

    public Object run() {
      return parser.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private final Parser<?> parser =
        Parser.quotedByWithEscapes("\"", '"', Parser.chars(1)).thenReturn("string");

    public StringFixture() {
      // Verify
      assertThat(parser.parse(BenchmarkInputs.STRING_SIMPLE)).isNotNull();
      assertThat(parser.parse(BenchmarkInputs.STRING_ESCAPED)).isNotNull();
    }

    public Object run(String input) {
      return parser.parse(input);
    }
  }

  public static class KeywordsFixture {
    private final Parser<?> parser = BenchmarkInputs.KEYWORDS.stream()
        .map(Parser::string)
        .collect(Parser.or());

    public KeywordsFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(parser.parse(keyword)).isNotNull();
      }
    }

    public Object run(String input) {
      return parser.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private final Parser<?> parser = BenchmarkInputs.KEYWORDS.stream()
        .map(Parser::caseInsensitive)
        .collect(Parser.or());

    public IgnoreCaseFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(parser.parse(keyword.toUpperCase())).isNotNull();
      }
    }

    public Object run(String input) {
      return parser.parse(input);
    }
  }

  public static class CalculatorFixture {
    private final Parser<Integer> parser;

    public CalculatorFixture() {
      OperatorTable<Integer> dotParseOpTable = new OperatorTable<Integer>()
          .leftAssociative("+", (l, r) -> l + r, 1)
          .leftAssociative("-", (l, r) -> l - r, 1)
          .leftAssociative("*", (l, r) -> l * r, 2)
          .leftAssociative("/", (l, r) -> l / r, 2);

      this.parser = Parser.define(
          expr ->
              dotParseOpTable.build(
                  expr.between("(", ")")
                      .or(
                          Parser.sequence(
                              Parser.one('-').optional(),
                              Parser.digits(),
                              (minus, digits) -> minus.isPresent() ? "-" + digits : digits
                          ).map(Integer::parseInt)
                      )
              )
      );

      // Verify
      int res = parser.parseSkipping(Character::isWhitespace, BenchmarkInputs.CALCULATOR);
      assertThat(res).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return parser.parseSkipping(Character::isWhitespace, BenchmarkInputs.CALCULATOR);
    }
  }

  private DotParseShowdown() {}
}

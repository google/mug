package com.google.mu.benchmarks.parsers.jparsec;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.stream.Collectors;
import org.jparsec.OperatorTable;
import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.Terminals;

public final class JparsecShowdown {

  public static class IpFixture {
    private final Parser<?> parser;

    public IpFixture() {
      Parser<Void> dot = Scanners.isChar('.');
      Parser<String> digits = Scanners.DEC_INTEGER;
      this.parser = Parsers.sequence(
          digits, dot,
          digits, dot,
          digits, dot,
          digits
      ).retn("ip");

      // Verify
      assertThat(parser.parse(BenchmarkInputs.IP)).isNotNull();
    }

    public Object run() {
      return parser.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private final Parser<?> parser = Scanners.DOUBLE_QUOTE_STRING;

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
    private final Parser<?> parser = Parsers.or(
        BenchmarkInputs.KEYWORDS.stream()
            .map(Scanners::string)
            .collect(Collectors.toList())
    );

    public KeywordsFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        parser.parse(keyword);
      }
    }

    public Object run(String input) {
      return parser.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private final Parser<?> parser = Parsers.or(
        BenchmarkInputs.KEYWORDS.stream()
            .map(Scanners::stringCaseInsensitive)
            .collect(Collectors.toList())
    );

    public IgnoreCaseFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        parser.parse(keyword.toUpperCase());
      }
    }

    public Object run(String input) {
      return parser.parse(input);
    }
  }

  public static class CalculatorFixture {
    private final Parser<Integer> parser;

    public CalculatorFixture() {
      Terminals terms = Terminals.operators("+", "-", "*", "/", "(", ")");
      Parser<Void> ignored = Scanners.WHITESPACES.optional();
      Parser<?> myIntegerTokenizer = Scanners.pattern(
          org.jparsec.pattern.Patterns.regex("-?[0-9]+"),
          "integer"
      ).source();

      Parser<?> tokenizer = Parsers.or(
          myIntegerTokenizer,
          terms.tokenizer()
      );

      Parser<Integer> number = Parsers.tokenType(String.class, "integer").map(Integer::parseInt);
      Parser.Reference<Integer> ref = Parser.newReference();
      Parser<Integer> atom = Parsers.or(
          number,
          ref.lazy().between(terms.token("("), terms.token(")"))
      );
      Parser<Integer> expr = new OperatorTable<Integer>()
          .infixl(terms.token("+").retn((a, b) -> a + b), 1)
          .infixl(terms.token("-").retn((a, b) -> a - b), 1)
          .infixl(terms.token("*").retn((a, b) -> a * b), 2)
          .infixl(terms.token("/").retn((a, b) -> a / b), 2)
          .build(atom);
      ref.set(expr);
      
      this.parser = expr.from(tokenizer, ignored);

      // Verify
      int res = parser.parse(BenchmarkInputs.CALCULATOR);
      assertThat(res).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return parser.parse(BenchmarkInputs.CALCULATOR);
    }
  }

  private JparsecShowdown() {}
}

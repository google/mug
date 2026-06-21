package com.google.mu.benchmarks.parsers.jparsec;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.jparsec.Parsers.or;
import static org.jparsec.Parsers.sequence;
import static org.jparsec.Parsers.tokenType;
import static org.jparsec.Scanners.DEC_INTEGER;
import static org.jparsec.Scanners.DOUBLE_QUOTE_STRING;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.isChar;
import static org.jparsec.Scanners.nestableBlockComment;
import static org.jparsec.Scanners.pattern;
import static org.jparsec.pattern.Patterns.regex;
import static org.junit.Assert.assertThrows;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.List;
import org.jparsec.OperatorTable;
import org.jparsec.Parser;
import org.jparsec.Scanners;
import org.jparsec.Terminals;
import org.jparsec.error.ParserException;

public final class JparsecShowdown {

  public static class IpFixture {
    private static final Parser<?> PARSER = buildParser();

    private static Parser<?> buildParser() {
      Parser<Void> dot = isChar('.');
      Parser<String> digits = DEC_INTEGER;
      return sequence(digits, dot, digits, dot, digits, dot, digits).retn("ip");
    }

    static {
      assertThat(PARSER.parse(BenchmarkInputs.IP)).isNotNull();
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final Parser<?> PARSER = DOUBLE_QUOTE_STRING;

    static {
      assertThat(PARSER.parse(BenchmarkInputs.STRING_SIMPLE)).isNotNull();
      assertThat(PARSER.parse(BenchmarkInputs.STRING_ESCAPED)).isNotNull();
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class KeywordsFixture {
    private static final Parser<?> KEYWORD =
        or(BenchmarkInputs.KEYWORDS.stream().map(Scanners::string).collect(toList()));
    private static final Parser<Integer> PARSER = KEYWORD.sepBy(isChar(',')).map(List::size);

    static {
      assertThat(PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CS)).isEqualTo(120);
      assertThrows(
          ParserException.class, () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID));
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser<?> KEYWORD =
        or(
            BenchmarkInputs.KEYWORDS.stream()
                .map(Scanners::stringCaseInsensitive)
                .collect(toList()));
    private static final Parser<Integer> PARSER = KEYWORD.sepBy(isChar(',')).map(List::size);

    static {
      assertThat(PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CI)).isEqualTo(120);
      assertThrows(
          ParserException.class, () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI));
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class CalculatorFixture {
    private static final Parser<Integer> PARSER = buildParser();

    private static Parser<Integer> buildParser() {
      var terms = Terminals.operators("+", "-", "*", "/", "(", ")");
      var ignored = WHITESPACES.optional();
      var myIntegerTokenizer = pattern(regex("[0-9]+"), "integer").source();

      var tokenizer = or(myIntegerTokenizer, terms.tokenizer());

      var number = tokenType(String.class, "integer").map(Integer::parseInt);
      var ref = Parser.<Integer>newReference();
      var atom = or(number, ref.lazy().between(terms.token("("), terms.token(")")));
      var expr =
          new OperatorTable<Integer>()
              .prefix(terms.token("-").retn(n -> -n), 3)
              .infixl(terms.token("+").retn((a, b) -> a + b), 1)
              .infixl(terms.token("-").retn((a, b) -> a - b), 1)
              .infixl(terms.token("*").retn((a, b) -> a * b), 2)
              .infixl(terms.token("/").retn((a, b) -> a / b), 2)
              .build(atom);
      ref.set(expr);

      return expr.from(tokenizer, ignored);
    }

    static {
      int res = PARSER.parse(BenchmarkInputs.CALCULATOR);
      assertThat(res).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.CALCULATOR);
    }
  }

  public static class NestedCommentFixture {
    private static final Parser<Void> PARSER = nestableBlockComment("/*", "*/");

    static {
      // Verify
      PARSER.parse(BenchmarkInputs.NESTED_COMMENT);
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.NESTED_COMMENT);
    }
  }

  private JparsecShowdown() {}
}

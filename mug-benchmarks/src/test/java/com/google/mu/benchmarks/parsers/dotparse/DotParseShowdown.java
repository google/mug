package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.or;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.counting;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.OperatorTable;
import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;

public final class DotParseShowdown {

  public static class IpFixture {
    private static final Parser<?> PARSER = buildParser();

    private static Parser<?> buildParser() {
      Parser<?> dot = string(".");
      return sequence(digits(), dot, digits(), dot, digits(), dot, digits()).thenReturn("ip");
    }

    static {
      assertThat(PARSER.parse(BenchmarkInputs.IP)).isNotNull();
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final Parser<?> PARSER =
        quotedByWithEscapes("\"", '"', chars(1)).thenReturn("string");

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
        BenchmarkInputs.KEYWORDS.stream().map(Parser::string).collect(or());
    private static final Parser<Long> PARSER = KEYWORD.atLeastOnceDelimitedBy(",", counting());

    static {
      assertThat(PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CS)).isEqualTo(120L);
      assertThrows(
          Parser.ParseException.class, () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID));
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser<?> KEYWORD =
        BenchmarkInputs.KEYWORDS.stream().map(Parser::caseInsensitive).collect(or());
    private static final Parser<Long> PARSER = KEYWORD.atLeastOnceDelimitedBy(",", counting());

    static {
      assertThat(PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CI)).isEqualTo(120L);
      assertThrows(
          Parser.ParseException.class,
          () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI));
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class CalculatorFixture {
    private static final Parser<Integer> PARSER =
        define(
            expr ->
                new OperatorTable<Integer>()
                    .leftAssociative("+", (l, r) -> l + r, 1)
                    .leftAssociative("-", (l, r) -> l - r, 1)
                    .leftAssociative("*", (l, r) -> l * r, 2)
                    .leftAssociative("/", (l, r) -> l / r, 2)
                    .prefix("-", n -> -n, 3)
                    .build(anyOf(digits().map(Integer::parseInt), expr.between("(", ")"))));

    static {
      // Verify
      int res = PARSER.parseSkipping(Character::isWhitespace, BenchmarkInputs.CALCULATOR);
      assertThat(res).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return PARSER.parseSkipping(Character::isWhitespace, BenchmarkInputs.CALCULATOR);
    }
  }

  public static class NestedCommentFixture {
    private static final Parser<String> PARSER = Parser.nestedBy("/*", "*/");

    static {
      // Verify
      assertThat(PARSER.parse(BenchmarkInputs.NESTED_COMMENT))
          .isEqualTo(BenchmarkInputs.NESTED_COMMENT_EXPECTED_INNER);
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.NESTED_COMMENT);
    }
  }

  private DotParseShowdown() {}
}

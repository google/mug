package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.or;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;

import com.google.common.labs.parse.OperatorTable;
import com.google.common.labs.parse.Parser;
import static com.google.mu.util.CharPredicate.WHITESPACE;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;

public final class DotParseShowdown {

  public static class IpFixture {
    private static final Parser<String> PARSER = buildParser();

    private static Parser<String> buildParser() {
      Parser<?> dot = string(".");
      return sequence(digits(), dot, digits(), dot, digits(), dot, digits()).source();
    }

    static {
      assertThat(PARSER.parse(BenchmarkInputs.IP)).isEqualTo(BenchmarkInputs.IP);
    }

    public String run() {
      return PARSER.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final Parser<String> PARSER =
        quotedByWithEscapes("\"", '"', chars(1));

    static {
      assertThat(PARSER.parse(BenchmarkInputs.STRING_SIMPLE)).isEqualTo("hello world!");
      assertThat(PARSER.parse(BenchmarkInputs.STRING_ESCAPED)).isEqualTo("hello \"world\"!");
    }

    public String run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class KeywordsFixture {
    private static final Parser<Keyword> KEYWORD =
        BenchmarkInputs.KEYWORDS.stream()
            .map(kw -> Parser.string(kw).thenReturn(BenchmarkInputs.KEYWORD_MAP.get(kw)))
            .collect(or());
    private static final Parser<List<Keyword>> PARSER = KEYWORD.atLeastOnceDelimitedBy(",");

    static {
      List<Keyword> result = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CS);
      assertThat(result.size()).isEqualTo(120);
      assertThrows(
          Parser.ParseException.class, () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID));
    }

    public List<Keyword> run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser<Keyword> KEYWORD =
        BenchmarkInputs.KEYWORDS.stream()
            .map(kw -> Parser.caseInsensitive(kw).thenReturn(BenchmarkInputs.KEYWORD_MAP.get(kw)))
            .collect(or());
    private static final Parser<List<Keyword>> PARSER = KEYWORD.atLeastOnceDelimitedBy(",");

    static {
      List<Keyword> result = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CI);
      assertThat(result.size()).isEqualTo(120);
      assertThrows(
          Parser.ParseException.class,
          () -> PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI));
    }

    public List<Keyword> run(String input) {
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
      int res = PARSER.parseSkipping(WHITESPACE, BenchmarkInputs.CALCULATOR);
      assertThat(res).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Integer run() {
      return PARSER.parseSkipping(WHITESPACE, BenchmarkInputs.CALCULATOR);
    }
  }

  public static class NestedCommentFixture {
    private static final Parser<String> PARSER = Parser.nestedBy("/*", "*/");

    static {
      // Verify
      assertThat(PARSER.parse(BenchmarkInputs.NESTED_COMMENT))
          .isEqualTo(BenchmarkInputs.NESTED_COMMENT_EXPECTED_INNER);
    }

    public String run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public String run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class UsPhoneFixture {
    private static final Parser<String> PARSER =
        sequence(one('('), digits(3), one(')'), digits(3), one('-'), digits(4))
            .source();

    static {
      assertThat(PARSER.parse(BenchmarkInputs.US_PHONE)).isEqualTo(BenchmarkInputs.US_PHONE);
    }

    public String run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class UsPhoneListFixture {
    private static final Parser<List<String>>.OrEmpty PARSER = literally(UsPhoneFixture.PARSER).zeroOrMore();

    static {
      List<String> result =
          PARSER.parseSkipping(WHITESPACE, BenchmarkInputs.US_PHONE_LIST);
      assertThat(result.size()).isEqualTo(1000);
    }

    public List<String> run(String input) {
      return PARSER.parseSkipping(WHITESPACE, input);
    }
  }

  private DotParseShowdown() {}
}

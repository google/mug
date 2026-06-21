package com.google.mu.benchmarks.parsers.jjparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.function.BiFunction;
import jjparse.Parsing;
import jjparse.StringParsing;
import jjparse.input.Input;

public final class JjparseShowdown {

  public static class IpFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<?> PARSER = jjParserInstance.ip;

    static {
      // Verify
      assertThat(jjParserInstance.parse(PARSER, Input.of("jjIp", BenchmarkInputs.IP)).isSuccess())
          .isTrue();
    }

    public Object run() {
      return jjParserInstance.parse(PARSER, Input.of("jjIp", BenchmarkInputs.IP));
    }
  }

  public static class StringFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<?> PARSER = jjParserInstance.quotedString;

    static {
      // Verify
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjStringSimple", BenchmarkInputs.STRING_SIMPLE))
                  .isSuccess())
          .isTrue();
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjStringEscaped", BenchmarkInputs.STRING_ESCAPED))
                  .isSuccess())
          .isTrue();
    }

    public Object run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjString", input));
    }
  }

  public static class KeywordsFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<Integer> PARSER =
        jjParserInstance
            .regex(
                "("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(str -> (int) str.chars().filter(c -> c == ',').count() + 1);

    static {
      // Verify
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjKeywords", BenchmarkInputs.KEYWORDS_LIST_CS))
                  .getOrFail())
          .isEqualTo(120);
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjKeywords", BenchmarkInputs.KEYWORDS_LIST_INVALID))
                  .isSuccess())
          .isFalse();
    }

    public Object run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjKeywords", input));
    }
  }

  public static class IgnoreCaseFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<Integer> PARSER =
        jjParserInstance
            .regex(
                "(?i)("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(str -> (int) str.chars().filter(c -> c == ',').count() + 1);

    static {
      // Verify
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjIgnoreCase", BenchmarkInputs.KEYWORDS_LIST_CI))
                  .getOrFail())
          .isEqualTo(120);
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjIgnoreCase", BenchmarkInputs.KEYWORDS_LIST_INVALID_CI))
                  .isSuccess())
          .isFalse();
    }

    public Object run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjIgnoreCase", input));
    }
  }

  public static class CalculatorFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<Integer> PARSER = jjParserInstance.calculator;

    static {
      // Verify
      var res = jjParserInstance.parse(PARSER, Input.of("jjcalc", BenchmarkInputs.CALCULATOR));
      assertThat(res.isSuccess()).isTrue();
      assertThat((Integer) res.getOrFail()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return jjParserInstance.parse(PARSER, Input.of("jjcalc", BenchmarkInputs.CALCULATOR));
    }
  }

  public static class NestedCommentFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<?> PARSER = jjParserInstance.nestedComment;

    static {
      // Verify
      var res =
          jjParserInstance.parse(
              PARSER, Input.of("jjNestedComment", BenchmarkInputs.NESTED_COMMENT));
      assertThat(res.isSuccess()).isTrue();
    }

    public Object run() {
      return jjParserInstance.parse(
          PARSER, Input.of("jjNestedComment", BenchmarkInputs.NESTED_COMMENT));
    }
  }

  // Inner Parser Rules Implementation
  public static class JjParserImpl extends StringParsing {
    public final Parsing<Character>.Parser<String> ip = regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
    public final Parsing<Character>.Parser<String> quotedString = regex("\"([^\"\\\\]|\\\\.)*\"");

    @SuppressWarnings("unchecked")
    public final Parsing<Character>.Parser<String> keywords =
        regex(
            "("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + ")(,("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + "))*");

    public final Parsing<Character>.Parser<String> keywordsIgnoreCase =
        regex(
            "(?i)("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + ")(,("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + "))*");

    public final Parsing<Character>.Parser<?> nestedComment;

    // Calculator Rules
    public final Parsing<Character>.Parser<Integer> calculator;

    @SuppressWarnings("unchecked")
    public JjParserImpl() {
      var number = token(regex("-?[0-9]+").map(Integer::parseInt));
      var ref = new ParserRef<Integer>();
      var atom =
          choice(
              number, token(literal("(")).andr(lazy(() -> ref.parser)).andl(token(literal(")"))));
      Parser<BiFunction<Integer, Integer, Integer>> mul =
          token(literal("*")).andr(success(() -> (a, b) -> a * b));
      Parser<BiFunction<Integer, Integer, Integer>> div =
          token(literal("/")).andr(success(() -> (a, b) -> a / b));
      Parser<BiFunction<Integer, Integer, Integer>> add =
          token(literal("+")).andr(success(() -> (a, b) -> a + b));
      Parser<BiFunction<Integer, Integer, Integer>> sub =
          token(literal("-")).andr(success(() -> (a, b) -> a - b));

      var expr = atom.chainl1(choice(mul, div)).chainl1(choice(add, sub));
      this.calculator = regex("\\s*").andr(expr);
      ref.parser = this.calculator;

      // Nested Comment
      var commentRef = new ParserRef<Object>();
      var commentNotEnd = literal("*/").not().andr(regex("."));
      var commentInner = choice(lazy(() -> commentRef.parser), commentNotEnd).repeat();
      this.nestedComment = literal("/*").andr(commentInner).andl(literal("*/"));
      commentRef.parser = (Parsing<Character>.Parser<Object>) this.nestedComment;
    }

    private <T> Parsing<Character>.Parser<T> token(Parsing<Character>.Parser<T> p) {
      return p.andl(regex("\\s*"));
    }

    private class ParserRef<T> {
      Parsing<Character>.Parser<T> parser;
    }
  }

  private JjparseShowdown() {}
}

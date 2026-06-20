package com.google.mu.benchmarks.parsers.jjparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import jjparse.Parsing;
import jjparse.StringParsing;
import jjparse.input.Input;

public final class JjparseShowdown {

  public static class IpFixture {
    private final JjParserImpl jjParserInstance = new JjParserImpl();
    private final Parsing<Character>.Parser<?> parser = jjParserInstance.ip;

    public IpFixture() {
      // Verify
      assertThat(jjParserInstance.parse(parser, Input.of("jjIp", BenchmarkInputs.IP))
          .isSuccess()).isTrue();
    }

    public Object run() {
      return jjParserInstance.parse(parser, Input.of("jjIp", BenchmarkInputs.IP));
    }
  }

  public static class StringFixture {
    private final JjParserImpl jjParserInstance = new JjParserImpl();
    private final Parsing<Character>.Parser<?> parser = jjParserInstance.quotedString;

    public StringFixture() {
      // Verify
      assertThat(jjParserInstance.parse(parser, Input.of("jjStringSimple", BenchmarkInputs.STRING_SIMPLE))
          .isSuccess()).isTrue();
      assertThat(jjParserInstance.parse(parser, Input.of("jjStringEscaped", BenchmarkInputs.STRING_ESCAPED))
          .isSuccess()).isTrue();
    }

    public Object run(String input) {
      return jjParserInstance.parse(parser, Input.of("jjString", input));
    }
  }

  public static class KeywordsFixture {
    private final JjParserImpl jjParserInstance = new JjParserImpl();
    private final Parsing<Character>.Parser<?> parser = jjParserInstance.keywords;

    public KeywordsFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(jjParserInstance.parse(parser, Input.of("jjKeywords", keyword))
            .isSuccess()).isTrue();
      }
    }

    public Object run(String input) {
      return jjParserInstance.parse(parser, Input.of("jjKeywords", input));
    }
  }

  public static class IgnoreCaseFixture {
    private final JjParserImpl jjParserInstance = new JjParserImpl();
    private final Parsing<Character>.Parser<?> parser = jjParserInstance.keywordsIgnoreCase;

    public IgnoreCaseFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(
            jjParserInstance.parse(
                parser,
                Input.of("jjIgnoreCase", keyword.toUpperCase())
            ).isSuccess()
        ).isTrue();
      }
    }

    public Object run(String input) {
      return jjParserInstance.parse(parser, Input.of("jjIgnoreCase", input));
    }
  }

  public static class CalculatorFixture {
    private final JjParserImpl jjParserInstance = new JjParserImpl();
    private final Parsing<Character>.Parser<Integer> parser = jjParserInstance.calculator;

    public CalculatorFixture() {
      // Verify
      var res = jjParserInstance.parse(parser, Input.of("jjcalc", BenchmarkInputs.CALCULATOR));
      assertThat(res.isSuccess()).isTrue();
      assertThat((Integer) res.getOrFail()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return jjParserInstance.parse(parser, Input.of("jjcalc", BenchmarkInputs.CALCULATOR));
    }
  }

  // Inner Parser Rules Implementation
  public static class JjParserImpl extends StringParsing {
    public final Parsing<Character>.Parser<String> ip = regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
    public final Parsing<Character>.Parser<String> quotedString = regex("\"([^\"\\\\]|\\\\.)*\"");
    
    @SuppressWarnings("unchecked")
    public final Parsing<Character>.Parser<String> keywords = choice(
        BenchmarkInputs.KEYWORDS.stream()
            .map(this::literal)
            .toArray(Parser[]::new)
    );
    
    public final Parsing<Character>.Parser<String> keywordsIgnoreCase =
        regex("(?i)" + String.join("|", BenchmarkInputs.KEYWORDS));

    // Calculator Rules
    public final Parsing<Character>.Parser<Integer> calculator;

    public JjParserImpl() {
      Parsing<Character>.Parser<Integer> number = token(regex("-?[0-9]+").map(Integer::parseInt));
      final ParserRef ref = new ParserRef();
      Parsing<Character>.Parser<Integer> atom = choice(
          number,
          token(literal("(")).andr(lazy(() -> ref.parser)).andl(token(literal(")")))
      );
      Parsing<Character>.Parser<java.util.function.BiFunction<Integer, Integer, Integer>> mul =
          token(literal("*")).andr(success(() -> (a, b) -> a * b));
      Parsing<Character>.Parser<java.util.function.BiFunction<Integer, Integer, Integer>> div =
          token(literal("/")).andr(success(() -> (a, b) -> a / b));
      Parsing<Character>.Parser<java.util.function.BiFunction<Integer, Integer, Integer>> add =
          token(literal("+")).andr(success(() -> (a, b) -> a + b));
      Parsing<Character>.Parser<java.util.function.BiFunction<Integer, Integer, Integer>> sub =
          token(literal("-")).andr(success(() -> (a, b) -> a - b));

      Parsing<Character>.Parser<Integer> expr =
          atom.chainl1(choice(mul, div)).chainl1(choice(add, sub));
      this.calculator = regex("\\s*").andr(expr);
      ref.parser = this.calculator;
    }

    private <T> Parsing<Character>.Parser<T> token(Parsing<Character>.Parser<T> p) {
      return p.andl(regex("\\s*"));
    }

    private class ParserRef {
      Parsing<Character>.Parser<Integer> parser;
    }
  }

  private JjparseShowdown() {}
}

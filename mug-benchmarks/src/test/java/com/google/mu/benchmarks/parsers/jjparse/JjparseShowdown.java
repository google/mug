package com.google.mu.benchmarks.parsers.jjparse;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.ArrayList;
import java.util.List;
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

    public Parsing<Character>.Result<?> run() {
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

    public Parsing<Character>.Result<?> run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjString", input));
    }
  }

  public static class KeywordsFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<List<Keyword>> PARSER =
        jjParserInstance
            .regex(
                "("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(
                str -> {
                  List<Keyword> list = new ArrayList<>();
                  for (String s : str.split(",")) {
                    list.add(BenchmarkInputs.KEYWORD_MAP.get(s));
                  }
                  return list;
                });

    static {
      // Verify
      List<Keyword> result =
          jjParserInstance
              .parse(PARSER, Input.of("jjKeywords", BenchmarkInputs.KEYWORDS_LIST_CS))
              .getOrFail();
      assertThat(result.size()).isEqualTo(120);
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjKeywords", BenchmarkInputs.KEYWORDS_LIST_INVALID))
                  .isSuccess())
          .isFalse();
    }

    public Parsing<Character>.Result<List<Keyword>> run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjKeywords", input));
    }
  }

  public static class IgnoreCaseFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<List<Keyword>> PARSER =
        jjParserInstance
            .regex(
                "(?i)("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(
                str -> {
                  List<Keyword> list = new ArrayList<>();
                  for (String s : str.split(",")) {
                    list.add(BenchmarkInputs.KEYWORD_MAP.get(s.toLowerCase()));
                  }
                  return list;
                });

    static {
      // Verify
      List<Keyword> result =
          jjParserInstance
              .parse(PARSER, Input.of("jjIgnoreCase", BenchmarkInputs.KEYWORDS_LIST_CI))
              .getOrFail();
      assertThat(result.size()).isEqualTo(120);
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("jjIgnoreCase", BenchmarkInputs.KEYWORDS_LIST_INVALID_CI))
                  .isSuccess())
          .isFalse();
    }

    public Parsing<Character>.Result<List<Keyword>> run(String input) {
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

    public Parsing<Character>.Result<Integer> run() {
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

    public Parsing<Character>.Result<?> run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public Parsing<Character>.Result<?> run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("jjNestedComment", input));
    }
  }

  public static class UsPhoneFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<String> PARSER = jjParserInstance.usPhone;

    static {
      assertThat(
              jjParserInstance
                  .parse(PARSER, Input.of("usPhone", BenchmarkInputs.US_PHONE))
                  .isSuccess())
          .isTrue();
    }

    public Parsing<Character>.Result<String> run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("usPhone", input));
    }
  }

  public static class UsPhoneListFixture {
    private static final JjParserImpl jjParserInstance = new JjParserImpl();
    private static final Parsing<Character>.Parser<List<String>> PARSER =
        jjParserInstance.usPhoneList;

    static {
      List<String> result =
          jjParserInstance
              .parse(PARSER, Input.of("usPhoneList", BenchmarkInputs.US_PHONE_LIST))
              .getOrFail();
      assertThat(result.size()).isEqualTo(1000);
    }

    public Parsing<Character>.Result<List<String>> run(String input) {
      return jjParserInstance.parse(PARSER, Input.of("usPhoneList", input));
    }
  }

  // Inner Parser Rules Implementation
  public static class JjParserImpl extends StringParsing {
    public final Parser<String> ip = regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
    public final Parser<String> quotedString;

    public final Parser<String> keywords =
        regex(
            "("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + ")(,("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + "))*");

    public final Parser<String> keywordsIgnoreCase =
        regex(
            "(?i)("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + ")(,("
                + String.join("|", BenchmarkInputs.KEYWORDS)
                + "))*");

    public final Parser<?> nestedComment;

    // Calculator Rules
    public final Parser<Integer> calculator;

    public final Parser<String> phoneToken = regex("\\(\\d{3}\\)\\d{3}-\\d{4}");
    public final Parser<String> usPhone = regex("\\(\\d{3}\\)\\d{3}-\\d{4}$");
    public final Parser<List<String>> usPhoneList = regex("\\s*").andr(token(phoneToken).repeat());

    @SuppressWarnings("unchecked")
    public JjParserImpl() {
      setSkipParser(success(() -> null));

      // Quoted String
      this.quotedString = regex("\"([^\"\\\\]|\\\\.)*\"")
          .map(BenchmarkInputs::unescape);

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

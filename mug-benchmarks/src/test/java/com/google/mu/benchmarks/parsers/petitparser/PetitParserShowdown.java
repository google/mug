package com.google.mu.benchmarks.parsers.petitparser;

import com.google.common.truth.Truth;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.ArrayList;
import java.util.List;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

public final class PetitParserShowdown {

  public static class IpFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser digits = CharacterParser.digit().plus().flatten();
      Parser dot = CharacterParser.of('.');
      return digits.seq(dot).seq(digits).seq(dot).seq(digits).seq(dot).seq(digits).flatten();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.IP);
      Truth.assertThat(res.isSuccess()).isTrue();
      Truth.assertThat((String) res.get()).isEqualTo(BenchmarkInputs.IP);
    }

    public Result run() {
      return PARSER.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser open = CharacterParser.of('"');
      Parser close = CharacterParser.of('"');
      Parser escape = CharacterParser.of('\\').seq(CharacterParser.any());
      Parser strChars = CharacterParser.pattern("^\"\\");
      return open.seq(escape.or(strChars).star()).seq(close).flatten().map(BenchmarkInputs::unescape);
    }

    static {
      Result resSimple = PARSER.parse(BenchmarkInputs.STRING_SIMPLE);
      Truth.assertThat(resSimple.isSuccess()).isTrue();
      Truth.assertThat((String) resSimple.get()).isEqualTo("hello world!");

      Result resEscaped = PARSER.parse(BenchmarkInputs.STRING_ESCAPED);
      Truth.assertThat(resEscaped.isSuccess()).isTrue();
      Truth.assertThat((String) resEscaped.get()).isEqualTo("hello \"world\"!");
    }

    public Result run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class KeywordsFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser keywords = null;
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        Parser p = StringParser.of(keyword).map(x -> BenchmarkInputs.KEYWORD_MAP.get(keyword));
        keywords = (keywords == null) ? p : keywords.or(p);
      }
      return keywords
          .separatedBy(CharacterParser.of(','))
          .map(list -> {
            List<Keyword> res = new ArrayList<>();
            List<?> lst = (List<?>) list;
            for (int i = 0; i < lst.size(); i += 2) {
              res.add((Keyword) lst.get(i));
            }
            return res;
          })
          .end();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CS);
      Truth.assertThat(res.isSuccess()).isTrue();
      @SuppressWarnings("unchecked")
      List<Keyword> result = (List<Keyword>) res.get();
      Truth.assertThat(result.size()).isEqualTo(120);

      Result resBad = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID);
      Truth.assertThat(resBad.isSuccess()).isFalse();
    }

    public Result run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser keywords = null;
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        Parser p = StringParser.ofIgnoringCase(keyword).map(x -> BenchmarkInputs.KEYWORD_MAP.get(keyword));
        keywords = (keywords == null) ? p : keywords.or(p);
      }
      return keywords
          .separatedBy(CharacterParser.of(','))
          .map(list -> {
            List<Keyword> res = new ArrayList<>();
            List<?> lst = (List<?>) list;
            for (int i = 0; i < lst.size(); i += 2) {
              res.add((Keyword) lst.get(i));
            }
            return res;
          })
          .end();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CI);
      Truth.assertThat(res.isSuccess()).isTrue();
      @SuppressWarnings("unchecked")
      List<Keyword> result = (List<Keyword>) res.get();
      Truth.assertThat(result.size()).isEqualTo(120);

      Result resBad = PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI);
      Truth.assertThat(resBad.isSuccess()).isFalse();
    }

    public Result run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class CalculatorFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      SettableParser expression = CharacterParser.none().settable();

      Parser number =
          CharacterParser.of('-')
              .optional()
              .seq(CharacterParser.digit().plus())
              .flatten()
              .trim()
              .map(x -> Integer.parseInt((String) x));

      Parser factor =
          CharacterParser.of('(')
              .trim()
              .seq(expression)
              .seq(CharacterParser.of(')').trim())
              .map((List<Object> x) -> (Integer) x.get(1))
              .or(number);

      // term = factor ( ('*'|'/') factor )*
      Parser term =
          factor
              .seq(CharacterParser.anyOf("*/").trim().seq(factor).star())
              .map(
                  (List<Object> x) -> {
                    int val = (Integer) x.get(0);
                    List<List<Object>> rest = (List<List<Object>>) x.get(1);
                    for (List<Object> opAndFactor : rest) {
                      char op = (Character) opAndFactor.get(0);
                      int next = (Integer) opAndFactor.get(1);
                      if (op == '*') {
                        val *= next;
                      } else {
                        val /= next;
                      }
                    }
                    return val;
                  });

      // expression = term ( ('+'|'-') term )*
      Parser expr =
          term.seq(CharacterParser.anyOf("+-").trim().seq(term).star())
              .map(
                  (List<Object> x) -> {
                    int val = (Integer) x.get(0);
                    List<List<Object>> rest = (List<List<Object>>) x.get(1);
                    for (List<Object> opAndTerm : rest) {
                      char op = (Character) opAndTerm.get(0);
                      int next = (Integer) opAndTerm.get(1);
                      if (op == '+') {
                        val += next;
                      } else {
                        val -= next;
                      }
                    }
                    return val;
                  });

      expression.set(expr);

      // Skip leading whitespace and match EOI
      return CharacterParser.whitespace()
          .star()
          .seq(expression)
          .map((List<Object> x) -> x.get(1))
          .end();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.CALCULATOR);
      Truth.assertThat(res.isSuccess()).isTrue();
      Truth.assertThat((Integer) res.get()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Result run() {
      return PARSER.parse(BenchmarkInputs.CALCULATOR);
    }
  }

  public static class NestedCommentFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      SettableParser nestedComment = CharacterParser.none().settable();

      Parser open = StringParser.of("/*");
      Parser close = StringParser.of("*/");
      Parser anyChar = close.not().seq(CharacterParser.any());

      Parser inside = nestedComment.or(anyChar).star();
      Parser comment = open.seq(inside).seq(close).flatten();

      nestedComment.set(comment);

      return comment.end();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.NESTED_COMMENT);
      Truth.assertThat(res.isSuccess()).isTrue();
      Truth.assertThat((String) res.get()).isEqualTo(BenchmarkInputs.NESTED_COMMENT);
    }

    public Result run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public Result run(String input) {
      return PARSER.parse(input);
    }
  }

  private PetitParserShowdown() {}
}

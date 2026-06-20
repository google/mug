package com.google.mu.benchmarks.parsers.petitparser;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

import java.util.List;

public final class PetitParserShowdown {

  public static class IpFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser digits = CharacterParser.digit().plus().flatten();
      Parser dot = CharacterParser.of('.');
      return digits.seq(dot).seq(digits).seq(dot).seq(digits).seq(dot).seq(digits).map(x -> "ip");
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.IP);
      assertThat(res.isSuccess()).isTrue();
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser open = CharacterParser.of('"');
      Parser close = CharacterParser.of('"');
      Parser escaped = CharacterParser.of('\\').seq(CharacterParser.any());
      Parser normal = CharacterParser.pattern("^\"\\");
      return open.seq(escaped.or(normal).star()).seq(close).flatten();
    }

    static {
      assertThat(PARSER.parse(BenchmarkInputs.STRING_SIMPLE).isSuccess()).isTrue();
      assertThat(PARSER.parse(BenchmarkInputs.STRING_ESCAPED).isSuccess()).isTrue();
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class KeywordsFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser keywords = null;
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        Parser p = StringParser.of(keyword);
        keywords = (keywords == null) ? p : keywords.or(p);
      }
      return keywords;
    }

    static {
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(PARSER.parse(keyword).isSuccess()).isTrue();
      }
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      Parser keywords = null;
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        Parser p = StringParser.ofIgnoringCase(keyword);
        keywords = (keywords == null) ? p : keywords.or(p);
      }
      return keywords;
    }

    static {
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(PARSER.parse(keyword.toUpperCase()).isSuccess()).isTrue();
      }
    }

    public Object run(String input) {
      return PARSER.parse(input);
    }
  }

  public static class CalculatorFixture {
    private static final Parser PARSER = buildParser();

    private static Parser buildParser() {
      SettableParser expression = CharacterParser.none().settable();

      Parser number = CharacterParser.of('-').optional().seq(CharacterParser.digit().plus()).flatten()
          .trim()
          .map(x -> Integer.parseInt((String) x));

      Parser factor = CharacterParser.of('(').trim()
          .seq(expression)
          .seq(CharacterParser.of(')').trim())
          .map((List<Object> x) -> (Integer) x.get(1))
          .or(number);

      // term = factor ( ('*'|'/') factor )*
      Parser term = factor.seq(
          CharacterParser.anyOf("*/").trim().seq(factor).star()
      ).map((List<Object> x) -> {
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
      Parser expr = term.seq(
          CharacterParser.anyOf("+-").trim().seq(term).star()
      ).map((List<Object> x) -> {
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
      return CharacterParser.whitespace().star().seq(expression).map((List<Object> x) -> x.get(1)).end();
    }

    static {
      Result res = PARSER.parse(BenchmarkInputs.CALCULATOR);
      assertThat(res.isSuccess()).isTrue();
      assertThat((Integer) res.get()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
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
      assertThat(res.isSuccess()).isTrue();
    }

    public Object run() {
      return PARSER.parse(BenchmarkInputs.NESTED_COMMENT);
    }
  }

  private PetitParserShowdown() {}
}

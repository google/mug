package com.google.mu.benchmarks.parsers.parsecj;

import static com.google.common.truth.Truth.assertThat;
import static org.javafp.parsecj.Combinators.choice;
import static org.javafp.parsecj.Combinators.eof;
import static org.javafp.parsecj.Combinators.or;
import static org.javafp.parsecj.Combinators.retn;
import static org.javafp.parsecj.Combinators.satisfy;
import static org.javafp.parsecj.Text.chr;
import static org.javafp.parsecj.Text.intr;
import static org.javafp.parsecj.Text.regex;
import static org.javafp.parsecj.Text.string;
import static org.javafp.parsecj.Text.wspaces;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.function.BinaryOperator;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.input.Input;

public final class ParsecjShowdown {

  public static class IpFixture {
    private static final Parser<Character, ?> PARSER = buildParser();

    private static Parser<Character, ?> buildParser() {
      Parser<Character, String> digits = intr.map(Object::toString);
      return digits
          .then(chr('.'))
          .then(digits)
          .then(chr('.'))
          .then(digits)
          .then(chr('.'))
          .then(digits)
          .map(x -> "ip");
    }

    static {
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.IP)).isOk()).isTrue();
    }

    public Object run() {
      return PARSER.parse(Input.of(BenchmarkInputs.IP));
    }
  }

  public static class StringFixture {
    private static final Parser<Character, ?> PARSER = buildParser();

    private static Parser<Character, ?> buildParser() {
      Parser<Character, String> escape =
          chr('\\').then(satisfy((Character c) -> true)).map(c -> "\\" + c);
      Parser<Character, String> normal =
          satisfy((Character c) -> c != '"' && c != '\\').map(c -> String.valueOf(c));
      return choice(normal, escape).many().map(x -> "string").between(chr('"'), chr('"'));
    }

    static {
      // Verify
      {
        Reply<Character, ?> res1 = PARSER.parse(Input.of(BenchmarkInputs.STRING_SIMPLE));
        assertThat(res1.isOk()).isTrue();
      }
      {
        Reply<Character, ?> res2 = PARSER.parse(Input.of(BenchmarkInputs.STRING_ESCAPED));
        assertThat(res2.isOk()).isTrue();
      }
    }

    public Object run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class KeywordsFixture {
    private static final Parser<Character, Integer> PARSER =
        regex(
                "("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(str -> (int) str.chars().filter(c -> c == ',').count() + 1)
            .between(retn(null), eof());

    static {
      try {
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_CS)).getResult())
            .isEqualTo(120);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_INVALID)).isOk()).isFalse();
    }

    public Object run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class IgnoreCaseFixture {
    @SuppressWarnings("unchecked")
    private static final Parser<Character, Integer> PARSER =
        regex(
                "(?i)("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + ")(,("
                    + String.join("|", BenchmarkInputs.KEYWORDS)
                    + "))*")
            .map(str -> (int) str.chars().filter(c -> c == ',').count() + 1)
            .between(retn(null), eof());

    static {
      try {
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_CI)).getResult())
            .isEqualTo(120);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI)).isOk()).isFalse();
    }

    public Object run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class CalculatorFixture {
    private static final Parser<Character, Integer> PARSER = buildParser();

    private static Parser<Character, Integer> buildParser() {
      Parser<Character, BinaryOperator<Integer>> parsecjMul =
          token(chr('*')).then(retn((a, b) -> a * b));
      Parser<Character, BinaryOperator<Integer>> parsecjDiv =
          token(chr('/')).then(retn((a, b) -> a / b));
      Parser<Character, BinaryOperator<Integer>> parsecjAdd =
          token(chr('+')).then(retn((a, b) -> a + b));
      Parser<Character, BinaryOperator<Integer>> parsecjSub =
          token(chr('-')).then(retn((a, b) -> a - b));

      Parser.Ref<Character, Integer> parsecjRef = Parser.ref();
      Parser<Character, Integer> signedInt =
          choice(chr('-').map(c -> -1), retn(1)).bind(sign -> intr.map(n -> sign * n));

      Parser<Character, Integer> parsecjAtom =
          choice(token(signedInt), parsecjRef.between(token(chr('(')), token(chr(')'))));

      Parser<Character, Integer> expr =
          parsecjAtom.chainl1(or(parsecjMul, parsecjDiv)).chainl1(or(parsecjAdd, parsecjSub));

      Parser<Character, Integer> parser = wspaces.then(expr);
      parsecjRef.set(parser);
      return parser;
    }

    static {
      try {
        Reply<Character, Integer> reply = PARSER.parse(Input.of(BenchmarkInputs.CALCULATOR));
        assertThat(reply.isOk()).isTrue();
        assertThat(reply.getResult()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public Object run() {
      return PARSER.parse(Input.of(BenchmarkInputs.CALCULATOR));
    }
  }

  public static class NestedCommentFixture {
    private static final Parser<Character, ?> PARSER = buildParser();

    @SuppressWarnings("unchecked")
    private static Parser<Character, ?> buildParser() {
      Parser.Ref<Character, Object> ref = Parser.ref();
      Parser<Character, Object> normalChar =
          satisfy((Character c) -> c != '*' && c != '/').map(c -> c);
      Parser<Character, Object> plainSlash = chr('/').map(c -> c);
      Parser<Character, Object> plainStar =
          chr('*').then(satisfy((Character c) -> c != '/')).map(c -> (Object) c).attempt();
      Parser<Character, Object> inner = choice(ref, normalChar, plainSlash, plainStar);
      Parser<Character, ?> comment = string("/*").then(inner.many()).then(string("*/"));
      ref.set((Parser<Character, Object>) comment);
      return comment;
    }

    static {
      Reply<Character, ?> reply = PARSER.parse(Input.of(BenchmarkInputs.NESTED_COMMENT));
      assertThat(reply.isOk()).isTrue();
    }

    public Object run() {
      return PARSER.parse(Input.of(BenchmarkInputs.NESTED_COMMENT));
    }
  }

  private static <T> Parser<Character, T> token(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }

  private ParsecjShowdown() {}
}

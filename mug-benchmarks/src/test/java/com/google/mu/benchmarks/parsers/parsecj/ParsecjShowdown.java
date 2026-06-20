package com.google.mu.benchmarks.parsers.parsecj;

import static com.google.common.truth.Truth.assertThat;
import static org.javafp.parsecj.Combinators.choice;
import static org.javafp.parsecj.Combinators.many;
import static org.javafp.parsecj.Combinators.or;
import static org.javafp.parsecj.Combinators.retn;
import static org.javafp.parsecj.Combinators.satisfy;
import static org.javafp.parsecj.Text.chr;
import static org.javafp.parsecj.Text.intr;
import static org.javafp.parsecj.Text.regex;
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
      Parser<Character, Character> escape = chr('\\').then(satisfy(c -> true));
      Parser<Character, Character> normalChar = satisfy(c -> c != '"' && c != '\\');
      return chr('"').then(many(or(escape, normalChar))).then(chr('"')).map(x -> "string");
    }

    static {
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.STRING_SIMPLE)).isOk()).isTrue();
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.STRING_ESCAPED)).isOk()).isTrue();
    }

    public Object run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class KeywordsFixture {
    @SuppressWarnings("unchecked")
    private static final Parser<Character, ?> PARSER =
        choice(
            BenchmarkInputs.KEYWORDS.stream()
                .map(org.javafp.parsecj.Text::string)
                .map(org.javafp.parsecj.Combinators::attempt)
                .toArray(Parser[]::new));

    static {
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(PARSER.parse(Input.of(keyword)).isOk()).isTrue();
      }
    }

    public Object run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class IgnoreCaseFixture {
    private static final Parser<Character, ?> PARSER =
        regex("(?i)" + String.join("|", BenchmarkInputs.KEYWORDS));

    static {
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(PARSER.parse(Input.of(keyword.toUpperCase())).isOk()).isTrue();
      }
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

    private static <T> Parser<Character, T> token(Parser<Character, T> p) {
      return p.bind(a -> wspaces.then(retn(a)));
    }

    public Object run() {
      return PARSER.parse(Input.of(BenchmarkInputs.CALCULATOR));
    }
  }

  private ParsecjShowdown() {}
}

package com.google.mu.benchmarks.parsers.parsecj;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.function.BinaryOperator;
import org.javafp.parsecj.Combinators;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.Text;
import org.javafp.parsecj.input.Input;

public final class ParsecjShowdown {

  public static class IpFixture {
    private final Parser<Character, ?> parser;

    public IpFixture() {
      Parser<Character, String> digits = Text.intr.map(Object::toString);
      this.parser = digits.then(Text.chr('.'))
          .then(digits).then(Text.chr('.'))
          .then(digits).then(Text.chr('.'))
          .then(digits)
          .map(x -> "ip");

      // Verify
      assertThat(parser.parse(Input.of(BenchmarkInputs.IP)).isOk()).isTrue();
    }

    public Object run() {
      return parser.parse(Input.of(BenchmarkInputs.IP));
    }
  }

  public static class StringFixture {
    private final Parser<Character, ?> parser;

    public StringFixture() {
      Parser<Character, Character> escape = Text.chr('\\').then(Combinators.satisfy(c -> true));
      Parser<Character, Character> normalChar =
          Combinators.satisfy(c -> c != '"' && c != '\\');
      this.parser = Text.chr('"')
          .then(Combinators.many(Combinators.or(escape, normalChar)))
          .then(Text.chr('"'))
          .map(x -> "string");

      // Verify
      assertThat(parser.parse(Input.of(BenchmarkInputs.STRING_SIMPLE)).isOk()).isTrue();
      assertThat(parser.parse(Input.of(BenchmarkInputs.STRING_ESCAPED)).isOk()).isTrue();
    }

    public Object run(String input) {
      return parser.parse(Input.of(input));
    }
  }

  public static class KeywordsFixture {
    @SuppressWarnings("unchecked")
    private final Parser<Character, ?> parser = Combinators.choice(
        BenchmarkInputs.KEYWORDS.stream()
            .map(Text::string)
            .map(Combinators::attempt)
            .toArray(Parser[]::new)
    );

    public KeywordsFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(parser.parse(Input.of(keyword)).isOk()).isTrue();
      }
    }

    public Object run(String input) {
      return parser.parse(Input.of(input));
    }
  }

  public static class IgnoreCaseFixture {
    private final Parser<Character, ?> parser =
        Text.regex("(?i)" + String.join("|", BenchmarkInputs.KEYWORDS));

    public IgnoreCaseFixture() {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        assertThat(
            parser.parse(Input.of(keyword.toUpperCase())).isOk()
        ).isTrue();
      }
    }

    public Object run(String input) {
      return parser.parse(Input.of(input));
    }
  }

  public static class CalculatorFixture {
    private final Parser<Character, Integer> parser;

    public CalculatorFixture() throws Exception {
      Parser<Character, BinaryOperator<Integer>> parsecjMul =
          token(Text.chr('*')).then(Combinators.retn((a, b) -> a * b));
      Parser<Character, BinaryOperator<Integer>> parsecjDiv =
          token(Text.chr('/')).then(Combinators.retn((a, b) -> a / b));
      Parser<Character, BinaryOperator<Integer>> parsecjAdd =
          token(Text.chr('+')).then(Combinators.retn((a, b) -> a + b));
      Parser<Character, BinaryOperator<Integer>> parsecjSub =
          token(Text.chr('-')).then(Combinators.retn((a, b) -> a - b));

      Parser.Ref<Character, Integer> parsecjRef = Parser.ref();
      Parser<Character, Integer> signedInt = Combinators.choice(
          Text.chr('-').map(c -> -1),
          Combinators.retn(1)
      ).bind(sign -> Text.intr.map(n -> sign * n));

      Parser<Character, Integer> parsecjAtom = Combinators.choice(
          token(signedInt),
          parsecjRef.between(token(Text.chr('(')), token(Text.chr(')')))
      );

      Parser<Character, Integer> expr =
          parsecjAtom.chainl1(Combinators.or(parsecjMul, parsecjDiv))
              .chainl1(Combinators.or(parsecjAdd, parsecjSub));
      
      this.parser = Text.wspaces.then(expr);
      parsecjRef.set(this.parser);

      // Verify
      Reply<Character, Integer> reply = parser.parse(Input.of(BenchmarkInputs.CALCULATOR));
      assertThat(reply.isOk()).isTrue();
      assertThat(reply.getResult()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    private static <T> Parser<Character, T> token(Parser<Character, T> p) {
      return p.bind(a -> Text.wspaces.then(Combinators.retn(a)));
    }

    public Object run() {
      return parser.parse(Input.of(BenchmarkInputs.CALCULATOR));
    }
  }

  private ParsecjShowdown() {}
}

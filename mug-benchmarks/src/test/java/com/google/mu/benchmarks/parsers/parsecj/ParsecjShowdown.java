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
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import org.javafp.data.IList;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.input.Input;

public final class ParsecjShowdown {

  public static class IpFixture {
    private static final Parser<Character, String> PARSER = regex("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");

    static {
      try {
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.IP)).getResult()).isEqualTo(BenchmarkInputs.IP);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }

    public Reply<Character, String> run() {
      return PARSER.parse(Input.of(BenchmarkInputs.IP));
    }
  }

  public static class StringFixture {
    private static final Parser<Character, String> PARSER = buildParser();

    private static Parser<Character, String> buildParser() {
      return regex("\"([^\"\\\\]|\\\\.)*\"").map(BenchmarkInputs::unescape);
    }

    static {
      try {
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.STRING_SIMPLE)).getResult())
            .isEqualTo("hello world!");
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.STRING_ESCAPED)).getResult())
            .isEqualTo("hello \"world\"!");
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }

    public Reply<Character, String> run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class KeywordsFixture {
    @SuppressWarnings("unchecked")
    private static final Parser<Character, Keyword> KEYWORD =
        choice(
            BenchmarkInputs.KEYWORDS.stream()
                .map(
                    kw ->
                        string(kw)
                            .then(retn(BenchmarkInputs.KEYWORD_MAP.get(kw)))
                            .attempt())
                .toArray(Parser[]::new));

    @SuppressWarnings("unchecked")
    private static final Parser<Character, List<Keyword>> PARSER =
        KEYWORD
            .bind(
                first ->
                    chr(',')
                        .then(KEYWORD)
                        .many()
                        .map(
                            rest -> {
                              List<Keyword> list = new ArrayList<>();
                              list.add(first);
                              rest.forEach(list::add);
                              return list;
                            }))
            .between(retn(null), eof());

    static {
      try {
        List<Keyword> result =
            PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_CS)).getResult();
        assertThat(result.size()).isEqualTo(120);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_INVALID)).isOk()).isFalse();
    }

    public Reply<Character, List<Keyword>> run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class IgnoreCaseFixture {
    @SuppressWarnings("unchecked")
    private static final Parser<Character, Keyword> KEYWORD_CI =
        choice(
            BenchmarkInputs.KEYWORDS.stream()
                .map(
                    kw ->
                        regex("(?i)" + kw)
                            .then(retn(BenchmarkInputs.KEYWORD_MAP.get(kw)))
                            .attempt())
                .toArray(Parser[]::new));

    @SuppressWarnings("unchecked")
    private static final Parser<Character, List<Keyword>> PARSER =
        KEYWORD_CI
            .bind(
                first ->
                    chr(',')
                        .then(KEYWORD_CI)
                        .many()
                        .map(
                            rest -> {
                              List<Keyword> list = new ArrayList<>();
                              list.add(first);
                              rest.forEach(list::add);
                              return list;
                            }))
            .between(retn(null), eof());

    static {
      try {
        List<Keyword> result =
            PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_CI)).getResult();
        assertThat(result.size()).isEqualTo(120);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
      assertThat(PARSER.parse(Input.of(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI)).isOk()).isFalse();
    }

    public Reply<Character, List<Keyword>> run(String input) {
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

    public Reply<Character, Integer> run() {
      return PARSER.parse(Input.of(BenchmarkInputs.CALCULATOR));
    }
  }

  public static class NestedCommentFixture {
    private static final Parser<Character, ?> PARSER = buildParser().between(retn(null), eof());

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

    public Reply<Character, ?> run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public Reply<Character, ?> run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  private static <T> Parser<Character, T> token(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }

  private static final Parser<Character, String> PHONE = regex("\\(\\d{3}\\)\\d{3}-\\d{4}");

  public static class UsPhoneFixture {
    private static final Parser<Character, String> PARSER = PHONE.between(retn(null), eof());

    static {
      try {
        assertThat(PARSER.parse(Input.of(BenchmarkInputs.US_PHONE)).getResult()).isEqualTo(BenchmarkInputs.US_PHONE);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }

    public Reply<Character, String> run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  public static class UsPhoneListFixture {
    private static final Parser<Character, List<String>> PARSER =
        wspaces.then(token(PHONE).many()).map(ilist -> {
          List<String> list = new ArrayList<>();
          for (String x : ilist) {
            list.add(x);
          }
          return list;
        }).between(retn(null), eof());

    static {
      try {
        List<String> result = PARSER.parse(Input.of(BenchmarkInputs.US_PHONE_LIST)).getResult();
        assertThat(result.size()).isEqualTo(1000);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }

    public Reply<Character, List<String>> run(String input) {
      return PARSER.parse(Input.of(input));
    }
  }

  private ParsecjShowdown() {}
}

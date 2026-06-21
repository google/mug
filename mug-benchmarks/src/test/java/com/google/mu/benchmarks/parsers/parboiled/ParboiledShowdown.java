package com.google.mu.benchmarks.parsers.parboiled;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.support.ParsingResult;

public final class ParboiledShowdown {

  public static class IpFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.ipAddress());
    }

    static {
      // Verify
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.IP);
      assertThat(res.matched).isTrue();
    }

    public Object run() {
      return RUNNER.run(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.quotedString());
    }

    static {
      // Verify
      ParsingResult<Object> res1 = RUNNER.run(BenchmarkInputs.STRING_SIMPLE);
      assertThat(res1.matched).isTrue();

      ParsingResult<Object> res2 = RUNNER.run(BenchmarkInputs.STRING_ESCAPED);
      assertThat(res2.matched).isTrue();
    }

    public Object run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class KeywordsFixture {
    static class KeywordsParser extends BaseParser<Object> {
      public Rule keywords() {
        return Sequence(keyword(), ZeroOrMore(',', keyword()), EOI);
      }

      public Rule verifyingKeywords() {
        return Sequence(
            push(0), keyword(), increment(), ZeroOrMore(',', keyword(), increment()), EOI);
      }

      public Rule keyword() {
        Rule[] rules = BenchmarkInputs.KEYWORDS.stream().map(this::String).toArray(Rule[]::new);
        return FirstOf(rules);
      }

      boolean increment() {
        poke((Integer) peek() + 1);
        return true;
      }
    }

    private static final KeywordsParser PARSER = Parboiled.createParser(KeywordsParser.class);
    private static final BasicParseRunner<Object> RUNNER =
        new BasicParseRunner<>(PARSER.keywords());

    static {
      // Verify using verifying rule
      var verifyingRunner = new BasicParseRunner<Object>(PARSER.verifyingKeywords());
      ParsingResult<Object> res = verifyingRunner.run(BenchmarkInputs.KEYWORDS_LIST_CS);
      assertThat(res.matched).isTrue();
      assertThat(res.resultValue).isEqualTo(120);
      assertThat(verifyingRunner.run(BenchmarkInputs.KEYWORDS_LIST_INVALID).matched).isFalse();
    }

    public Object run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class IgnoreCaseFixture {
    static class IgnoreCaseParser extends BaseParser<Object> {
      public Rule keywords() {
        return Sequence(keyword(), ZeroOrMore(',', keyword()), EOI);
      }

      public Rule verifyingKeywords() {
        return Sequence(
            push(0), keyword(), increment(), ZeroOrMore(',', keyword(), increment()), EOI);
      }

      public Rule keyword() {
        Rule[] rules = BenchmarkInputs.KEYWORDS.stream().map(this::IgnoreCase).toArray(Rule[]::new);
        return FirstOf(rules);
      }

      boolean increment() {
        poke((Integer) peek() + 1);
        return true;
      }
    }

    private static final IgnoreCaseParser PARSER = Parboiled.createParser(IgnoreCaseParser.class);
    private static final BasicParseRunner<Object> RUNNER =
        new BasicParseRunner<>(PARSER.keywords());

    static {
      // Verify using verifying rule
      var verifyingRunner = new BasicParseRunner<Object>(PARSER.verifyingKeywords());
      ParsingResult<Object> res = verifyingRunner.run(BenchmarkInputs.KEYWORDS_LIST_CI);
      assertThat(res.matched).isTrue();
      assertThat(res.resultValue).isEqualTo(120);
      assertThat(verifyingRunner.run(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI).matched).isFalse();
    }

    public Object run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class CalculatorFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.calculator());
    }

    static {
      // Verify
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.CALCULATOR);
      assertThat(res.matched).isTrue();
      assertThat(res.valueStack.isEmpty()).isFalse();
      assertThat((Integer) res.valueStack.peek()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return RUNNER.run(BenchmarkInputs.CALCULATOR);
    }
  }

  public static class NestedCommentFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.nestedCommentRoot());
    }

    static {
      // Verify
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.NESTED_COMMENT);
      assertThat(res.matched).isTrue();
    }

    public Object run() {
      return RUNNER.run(BenchmarkInputs.NESTED_COMMENT);
    }
  }

  // Rules implementation for parboiled
  public static class ParboiledParser extends BaseParser<Object> {
    public Rule ipAddress() {
      return Sequence(digits(), '.', digits(), '.', digits(), '.', digits(), EOI);
    }

    public Rule digits() {
      return OneOrMore(CharRange('0', '9'));
    }

    public Rule quotedString() {
      return Sequence('"', ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\"))), '"', EOI);
    }

    public Rule keywords() {
      Rule keyword = FirstOf(BenchmarkInputs.KEYWORDS.toArray());
      return Sequence(keyword, ZeroOrMore(',', keyword), EOI);
    }

    public Rule keywordsIgnoreCase() {
      Rule keyword =
          FirstOf(BenchmarkInputs.KEYWORDS.stream().map(this::IgnoreCase).toArray(Rule[]::new));
      return Sequence(keyword, ZeroOrMore(',', keyword), EOI);
    }

    // Nested Comment Rules
    @SuppressWarnings("InfiniteRecursion")
    public Rule nestedComment() {
      return Sequence(
          "/*", ZeroOrMore(FirstOf(nestedComment(), Sequence(TestNot("*/"), ANY))), "*/");
    }

    public Rule nestedCommentRoot() {
      return Sequence(nestedComment(), EOI);
    }

    // Calculator Rule
    public Rule calculator() {
      return Sequence(whitespace(), expression(), EOI);
    }

    public Rule whitespace() {
      return ZeroOrMore(AnyOf(" \t\r\n"));
    }

    public Rule ch(char c) {
      return Sequence(c, whitespace());
    }

    public Rule expression() {
      return Sequence(
          term(),
          ZeroOrMore(
              FirstOf(
                  Sequence(ch('+'), term(), push((Integer) pop(1) + (Integer) pop())),
                  Sequence(ch('-'), term(), push((Integer) pop(1) - (Integer) pop())))));
    }

    public Rule term() {
      return Sequence(
          factor(),
          ZeroOrMore(
              FirstOf(
                  Sequence(ch('*'), factor(), push((Integer) pop(1) * (Integer) pop())),
                  Sequence(ch('/'), factor(), push((Integer) pop(1) / (Integer) pop())))));
    }

    public Rule factor() {
      return FirstOf(Sequence(ch('('), expression(), ch(')')), number());
    }

    public Rule number() {
      return Sequence(
          Sequence(Optional('-'), OneOrMore(CharRange('0', '9'))),
          push(Integer.parseInt(match())),
          whitespace());
    }
  }

  private ParboiledShowdown() {}
}

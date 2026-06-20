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
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.keywords());
    }

    static {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        ParsingResult<Object> res = RUNNER.run(keyword);
        assertThat(res.matched).isTrue();
      }
    }

    public Object run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class IgnoreCaseFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.keywordsIgnoreCase());
    }

    static {
      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        ParsingResult<Object> res = RUNNER.run(keyword.toUpperCase());
        assertThat(res.matched).isTrue();
      }
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

  // Rules implementation for parboiled
  public static class ParboiledParser extends BaseParser<Object> {
    public Rule ipAddress() {
      return Sequence(digits(), '.', digits(), '.', digits(), '.', digits(), EOI);
    }

    public Rule digits() {
      return OneOrMore(CharRange('0', '9'));
    }

    public Rule quotedString() {
      return Sequence(
          '"', ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\"))), '"', EOI);
    }

    public Rule keywords() {
      return Sequence(FirstOf(BenchmarkInputs.KEYWORDS.toArray()), EOI);
    }

    public Rule keywordsIgnoreCase() {
      return Sequence(
          FirstOf(BenchmarkInputs.KEYWORDS.stream().map(this::IgnoreCase).toArray(Rule[]::new)),
          EOI);
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

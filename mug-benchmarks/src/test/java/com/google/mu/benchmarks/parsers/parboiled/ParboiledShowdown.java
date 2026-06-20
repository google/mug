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
    private final BasicParseRunner<Object> runner;

    public IpFixture() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      this.runner = new BasicParseRunner<>(parser.ipAddress());

      // Verify
      ParsingResult<Object> res = runner.run(BenchmarkInputs.IP);
      assertThat(res.matched).isTrue();
    }

    public Object run() {
      return runner.run(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private final BasicParseRunner<Object> runner;

    public StringFixture() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      this.runner = new BasicParseRunner<>(parser.quotedString());

      // Verify
      ParsingResult<Object> res1 = runner.run(BenchmarkInputs.STRING_SIMPLE);
      assertThat(res1.matched).isTrue();

      ParsingResult<Object> res2 = runner.run(BenchmarkInputs.STRING_ESCAPED);
      assertThat(res2.matched).isTrue();
    }

    public Object run(String input) {
      return runner.run(input);
    }
  }

  public static class KeywordsFixture {
    private final BasicParseRunner<Object> runner;

    public KeywordsFixture() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      this.runner = new BasicParseRunner<>(parser.keywords());

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        ParsingResult<Object> res = runner.run(keyword);
        assertThat(res.matched).isTrue();
      }
    }

    public Object run(String input) {
      return runner.run(input);
    }
  }

  public static class IgnoreCaseFixture {
    private final BasicParseRunner<Object> runner;

    public IgnoreCaseFixture() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      this.runner = new BasicParseRunner<>(parser.keywordsIgnoreCase());

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        ParsingResult<Object> res = runner.run(keyword.toUpperCase());
        assertThat(res.matched).isTrue();
      }
    }

    public Object run(String input) {
      return runner.run(input);
    }
  }

  public static class CalculatorFixture {
    private final BasicParseRunner<Object> runner;

    public CalculatorFixture() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      this.runner = new BasicParseRunner<>(parser.calculator());

      // Verify
      ParsingResult<Object> res = runner.run(BenchmarkInputs.CALCULATOR);
      assertThat(res.matched).isTrue();
      assertThat(res.valueStack.isEmpty()).isFalse();
      assertThat((Integer) res.valueStack.peek()).isEqualTo(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Object run() {
      return runner.run(BenchmarkInputs.CALCULATOR);
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
          '"',
          ZeroOrMore(
              FirstOf(
                  Sequence('\\', ANY),
                  NoneOf("\"\\")
              )
          ),
          '"',
          EOI
      );
    }

    public Rule keywords() {
      return Sequence(
          FirstOf(BenchmarkInputs.KEYWORDS.toArray()),
          EOI
      );
    }

    public Rule keywordsIgnoreCase() {
      return Sequence(
          FirstOf(
              BenchmarkInputs.KEYWORDS.stream()
                  .map(this::IgnoreCase)
                  .toArray(Rule[]::new)
          ),
          EOI
      );
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
                  Sequence(ch('-'), term(), push((Integer) pop(1) - (Integer) pop()))
              )
          )
      );
    }

    public Rule term() {
      return Sequence(
          factor(),
          ZeroOrMore(
              FirstOf(
                  Sequence(ch('*'), factor(), push((Integer) pop(1) * (Integer) pop())),
                  Sequence(ch('/'), factor(), push((Integer) pop(1) / (Integer) pop()))
              )
          )
      );
    }

    public Rule factor() {
      return FirstOf(
          Sequence(ch('('), expression(), ch(')')),
          number()
      );
    }

    public Rule number() {
      return Sequence(
          Sequence(Optional('-'), OneOrMore(CharRange('0', '9'))),
          push(Integer.parseInt(match())),
          whitespace()
      );
    }
  }

  private ParboiledShowdown() {}
}

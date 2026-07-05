package com.google.mu.benchmarks.parsers.parboiled;

import static com.google.common.truth.Truth.assertThat;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.ArrayList;
import java.util.List;
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
      assertThat(res.resultValue).isEqualTo(BenchmarkInputs.IP);
    }

    public ParsingResult<Object> run() {
      return RUNNER.run(BenchmarkInputs.IP);
    }
  }

  public static class StringFixture {
    private static final BasicParseRunner<Object> RUNNER = buildRunner();

    private static BasicParseRunner<Object> buildRunner() {
      ParboiledParser parser = Parboiled.createParser(ParboiledParser.class);
      return new BasicParseRunner<>(parser.Sequence(parser.quotedString(), parser.EOI));
    }

    static {
      // Verify
      ParsingResult<Object> res1 = RUNNER.run(BenchmarkInputs.STRING_SIMPLE);
      assertThat(res1.matched).isTrue();
      assertThat(res1.resultValue).isEqualTo("hello world!");

      ParsingResult<Object> res2 = RUNNER.run(BenchmarkInputs.STRING_ESCAPED);
      assertThat(res2.matched).isTrue();
      assertThat(res2.resultValue).isEqualTo("hello \"world\"!");
    }

    public ParsingResult<Object> run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class KeywordsFixture {
    static class KeywordsParser extends BaseParser<Object> {
      public Rule keywords() {
        return Sequence(
            push(new ArrayList<Keyword>()),
            keyword(), pushKeyword(), addKeyword(),
            ZeroOrMore(',', keyword(), pushKeyword(), addKeyword()),
            EOI);
      }

      public Rule keyword() {
        return FirstOf(BenchmarkInputs.KEYWORDS.toArray());
      }

      boolean pushKeyword() {
        String matchedText = match();
        Keyword kw = BenchmarkInputs.KEYWORD_MAP.get(matchedText.toLowerCase());
        push(kw);
        return true;
      }

      boolean addKeyword() {
        Keyword kw = (Keyword) pop();
        @SuppressWarnings("unchecked")
        List<Keyword> list = (List<Keyword>) peek();
        list.add(kw);
        return true;
      }
    }

    private static final KeywordsParser PARSER = Parboiled.createParser(KeywordsParser.class);
    private static final BasicParseRunner<Object> RUNNER =
        new BasicParseRunner<>(PARSER.keywords());

    static {
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.KEYWORDS_LIST_CS);
      assertThat(res.matched).isTrue();
      @SuppressWarnings("unchecked")
      List<Keyword> result = (List<Keyword>) res.resultValue;
      assertThat(result.size()).isEqualTo(500);
      assertThat(RUNNER.run(BenchmarkInputs.KEYWORDS_LIST_INVALID).matched).isFalse();
    }

    public ParsingResult<Object> run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class IgnoreCaseFixture {
    static class IgnoreCaseParser extends BaseParser<Object> {
      public Rule keywords() {
        return Sequence(
            push(new ArrayList<Keyword>()),
            keyword(), pushKeyword(), addKeyword(),
            ZeroOrMore(',', keyword(), pushKeyword(), addKeyword()),
            EOI);
      }

      public Rule keyword() {
        List<Rule> rules = new ArrayList<>();
        for (String kw : BenchmarkInputs.KEYWORDS) {
          rules.add(IgnoreCase(kw));
        }
        return FirstOf(rules.toArray(new Rule[0]));
      }

      boolean pushKeyword() {
        String matchedText = match();
        Keyword kw = BenchmarkInputs.KEYWORD_MAP.get(matchedText.toLowerCase());
        push(kw);
        return true;
      }

      boolean addKeyword() {
        Keyword kw = (Keyword) pop();
        @SuppressWarnings("unchecked")
        List<Keyword> list = (List<Keyword>) peek();
        list.add(kw);
        return true;
      }
    }

    private static final IgnoreCaseParser PARSER = Parboiled.createParser(IgnoreCaseParser.class);
    private static final BasicParseRunner<Object> RUNNER =
        new BasicParseRunner<>(PARSER.keywords());

    static {
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.KEYWORDS_LIST_CI);
      assertThat(res.matched).isTrue();
      @SuppressWarnings("unchecked")
      List<Keyword> result = (List<Keyword>) res.resultValue;
      assertThat(result.size()).isEqualTo(500);
      assertThat(RUNNER.run(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI).matched).isFalse();
    }

    public ParsingResult<Object> run(String input) {
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

    public ParsingResult<Object> run() {
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

    public ParsingResult<Object> run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public ParsingResult<Object> run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class UsPhoneFixture {
    private static final ParboiledParser PARSER = Parboiled.createParser(ParboiledParser.class);
    private static final BasicParseRunner<Object> RUNNER = new BasicParseRunner<>(PARSER.usPhone());

    static {
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.US_PHONE);
      assertThat(res.matched).isTrue();
      assertThat(res.resultValue).isEqualTo(BenchmarkInputs.US_PHONE);
    }

    public ParsingResult<Object> run(String input) {
      return RUNNER.run(input);
    }
  }

  public static class UsPhoneListFixture {
    private static final ParboiledParser PARSER = Parboiled.createParser(ParboiledParser.class);
    private static final BasicParseRunner<Object> RUNNER = new BasicParseRunner<>(PARSER.usPhoneList());

    static {
      ParsingResult<Object> res = RUNNER.run(BenchmarkInputs.US_PHONE_LIST);
      assertThat(res.matched).isTrue();
      @SuppressWarnings("unchecked")
      List<String> result = (List<String>) res.resultValue;
      assertThat(result.size()).isEqualTo(1000);
    }

    public ParsingResult<Object> run(String input) {
      return RUNNER.run(input);
    }
  }

  // Rules implementation for parboiled
  public static class ParboiledParser extends BaseParser<Object> {
    public Rule ipAddress() {
      return Sequence(
          Sequence(digits(), '.', digits(), '.', digits(), '.', digits()),
          push(match()),
          EOI);
    }

    public Rule digits() {
      return OneOrMore(CharRange('0', '9'));
    }

    public Rule quotedString() {
      return Sequence(
          Sequence('"', ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\"))), '"'),
          push(BenchmarkInputs.unescape(match()))
      );
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

    public Rule usPhone() {
      return Sequence(
          Sequence('(', nDigits(3), ')', nDigits(3), '-', nDigits(4)),
          push(match()),
          EOI);
    }

    public Rule nDigits(int n) {
      return NTimes(n, CharRange('0', '9'));
    }

    public Rule usPhoneList() {
      Rule phone = Sequence(
          Sequence('(', nDigits(3), ')', nDigits(3), '-', nDigits(4)),
          push(match()));
      return Sequence(
          push(new ArrayList<String>()),
          whitespace(),
          ZeroOrMore(phone, addPhone(), whitespace()),
          EOI);
    }

    boolean addPhone() {
      String p = (String) pop();
      @SuppressWarnings("unchecked")
      List<String> list = (List<String>) peek();
      list.add(p);
      return true;
    }
  }

  private ParboiledShowdown() {}
}

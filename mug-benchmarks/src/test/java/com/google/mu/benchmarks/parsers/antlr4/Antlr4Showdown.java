package com.google.mu.benchmarks.parsers.antlr4;

import static com.google.common.truth.Truth.assertThat;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import com.google.mu.benchmarks.ShowdownLexer;
import com.google.mu.benchmarks.ShowdownParser;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;

public final class Antlr4Showdown {

  public static class IpFixture {
    private final ShowdownLexer lexer = new ShowdownLexer(null);
    private final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    private final ShowdownParser parser = new ShowdownParser(tokenStream);

    public IpFixture() {
      lexer.removeErrorListeners();
      parser.removeErrorListeners();

      // Verify
      CharStream charStream = CharStreams.fromString(BenchmarkInputs.IP);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.ip();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run() {
      CharStream charStream = CharStreams.fromString(BenchmarkInputs.IP);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.ip();
      return parser;
    }
  }

  public static class StringFixture {
    private final ShowdownLexer lexer = new ShowdownLexer(null);
    private final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    private final ShowdownParser parser = new ShowdownParser(tokenStream);

    public StringFixture() {
      lexer.removeErrorListeners();
      parser.removeErrorListeners();

      // Verify Simple
      CharStream charStream1 = CharStreams.fromString(BenchmarkInputs.STRING_SIMPLE);
      lexer.setInputStream(charStream1);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);

      // Verify Escaped
      CharStream charStream2 = CharStreams.fromString(BenchmarkInputs.STRING_ESCAPED);
      lexer.setInputStream(charStream2);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run(String input) {
      CharStream charStream = CharStreams.fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      return parser;
    }
  }

  public static class KeywordsFixture {
    private final ShowdownLexer lexer = new ShowdownLexer(null);
    private final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    private final ShowdownParser parser = new ShowdownParser(tokenStream);

    public KeywordsFixture() {
      lexer.removeErrorListeners();
      parser.removeErrorListeners();

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        CharStream charStream = CharStreams.fromString(keyword);
        lexer.setInputStream(charStream);
        tokenStream.setTokenSource(lexer);
        parser.setInputStream(tokenStream);
        parser.reset();
        parser.keywords();
        assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      }
    }

    public Object run(String input) {
      CharStream charStream = CharStreams.fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywords();
      return parser;
    }
  }

  public static class IgnoreCaseFixture {
    private final ShowdownLexer lexer = new ShowdownLexer(null);
    private final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    private final ShowdownParser parser = new ShowdownParser(tokenStream);

    public IgnoreCaseFixture() {
      lexer.removeErrorListeners();
      parser.removeErrorListeners();

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        CharStream charStream = CharStreams.fromString(keyword.toUpperCase());
        lexer.setInputStream(charStream);
        tokenStream.setTokenSource(lexer);
        parser.setInputStream(tokenStream);
        parser.reset();
        parser.keywordsIgnoreCase();
        assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      }
    }

    public Object run(String input) {
      CharStream charStream = CharStreams.fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywordsIgnoreCase();
      return parser;
    }
  }

  public static class CalculatorFixture {
    private final ShowdownLexer lexer = new ShowdownLexer(null);
    private final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    private final ShowdownParser parser = new ShowdownParser(tokenStream);

    public CalculatorFixture() {
      lexer.removeErrorListeners();
      parser.removeErrorListeners();

      // Verify
      CharStream charStream = CharStreams.fromString(BenchmarkInputs.CALCULATOR);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.calculator();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run() {
      CharStream charStream = CharStreams.fromString(BenchmarkInputs.CALCULATOR);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.calculator();
      return parser;
    }
  }

  private Antlr4Showdown() {}
}

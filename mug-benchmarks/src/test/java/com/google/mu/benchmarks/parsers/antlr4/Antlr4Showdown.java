package com.google.mu.benchmarks.parsers.antlr4;

import static com.google.common.truth.Truth.assertThat;
import static org.antlr.v4.runtime.CharStreams.fromString;

import com.google.mu.benchmarks.ShowdownLexer;
import com.google.mu.benchmarks.ShowdownParser;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;

public final class Antlr4Showdown {

  public static class IpFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(BenchmarkInputs.IP);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.ip();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run() {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(BenchmarkInputs.IP);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.ip();
      return parser;
    }
  }

  public static class StringFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      // Verify Simple
      CharStream charStream1 = fromString(BenchmarkInputs.STRING_SIMPLE);
      lexer.setInputStream(charStream1);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);

      // Verify Escaped
      CharStream charStream2 = fromString(BenchmarkInputs.STRING_ESCAPED);
      lexer.setInputStream(charStream2);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.quotedString();
      return parser;
    }
  }

  public static class KeywordsFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        CharStream charStream = fromString(keyword);
        lexer.setInputStream(charStream);
        tokenStream.setTokenSource(lexer);
        parser.setInputStream(tokenStream);
        parser.reset();
        parser.keywords();
        assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      }
    }

    public Object run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywords();
      return parser;
    }
  }

  public static class IgnoreCaseFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      // Verify
      for (String keyword : BenchmarkInputs.KEYWORDS) {
        CharStream charStream = fromString(keyword.toUpperCase());
        lexer.setInputStream(charStream);
        tokenStream.setTokenSource(lexer);
        parser.setInputStream(tokenStream);
        parser.reset();
        parser.keywordsIgnoreCase();
        assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      }
    }

    public Object run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywordsIgnoreCase();
      return parser;
    }
  }

  public static class CalculatorFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      // Verify
      CharStream charStream = fromString(BenchmarkInputs.CALCULATOR);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.calculator();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run() {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(BenchmarkInputs.CALCULATOR);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.calculator();
      return parser;
    }
  }

  public static class NestedCommentFixture {
    private static final ThreadLocal<ShowdownLexer> LEXER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownLexer lex = new ShowdownLexer(null);
              lex.removeErrorListeners();
              return lex;
            });
    private static final ThreadLocal<CommonTokenStream> TOKEN_STREAM =
        ThreadLocal.withInitial(() -> new CommonTokenStream(LEXER.get()));
    private static final ThreadLocal<ShowdownParser> PARSER =
        ThreadLocal.withInitial(
            () -> {
              ShowdownParser p = new ShowdownParser(TOKEN_STREAM.get());
              p.removeErrorListeners();
              return p;
            });

    static {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      // Verify
      CharStream charStream = fromString(BenchmarkInputs.NESTED_COMMENT);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.nestedCommentRoot();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public Object run() {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(BenchmarkInputs.NESTED_COMMENT);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.nestedCommentRoot();
      return parser;
    }
  }

  private Antlr4Showdown() {}
}

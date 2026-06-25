package com.google.mu.benchmarks.parsers.antlr4;

import static com.google.common.truth.Truth.assertThat;
import static org.antlr.v4.runtime.CharStreams.fromString;

import com.google.mu.benchmarks.ShowdownLexer;
import com.google.mu.benchmarks.ShowdownParser;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import java.util.ArrayList;
import java.util.List;
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
      String result = parser.ip().getText();
      if (result.endsWith("<EOF>")) {
        result = result.substring(0, result.length() - 5);
      }
      assertThat(result).isEqualTo(BenchmarkInputs.IP);
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
    }

    public String run() {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(BenchmarkInputs.IP);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      String result = parser.ip().getText();
      if (result.endsWith("<EOF>")) {
        result = result.substring(0, result.length() - 5);
      }
      return result;
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
      ShowdownParser.QuotedStringContext ctx1 = parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      assertThat(BenchmarkInputs.unescape(ctx1.QUOTED_STRING().getText())).isEqualTo("hello world!");

      // Verify Escaped
      CharStream charStream2 = fromString(BenchmarkInputs.STRING_ESCAPED);
      lexer.setInputStream(charStream2);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.QuotedStringContext ctx2 = parser.quotedString();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      assertThat(BenchmarkInputs.unescape(ctx2.QUOTED_STRING().getText())).isEqualTo("hello \"world\"!");
    }

    public String run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();
      lexer.setInputStream(fromString(input));
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.QuotedStringContext ctx = parser.quotedString();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("Syntax error");
      }
      return BenchmarkInputs.unescape(ctx.QUOTED_STRING().getText());
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
      CharStream charStream = fromString(BenchmarkInputs.KEYWORDS_LIST_CS);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.KeywordsContext ctx = parser.keywords();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      assertThat(ctx.keyword().size()).isEqualTo(120);

      // Negative Verify
      CharStream badStream = fromString(BenchmarkInputs.KEYWORDS_LIST_INVALID);
      lexer.setInputStream(badStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywords();
      assertThat(parser.getNumberOfSyntaxErrors()).isGreaterThan(0);
    }

    public List<Keyword> run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.KeywordsContext ctx = parser.keywords();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("ANTLR4 parsing failed with " + parser.getNumberOfSyntaxErrors() + " syntax errors");
      }
      List<Keyword> list = new ArrayList<>();
      for (ShowdownParser.KeywordContext k : ctx.keyword()) {
        list.add(BenchmarkInputs.KEYWORD_MAP.get(k.getText()));
      }
      return list;
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
      CharStream charStream = fromString(BenchmarkInputs.KEYWORDS_LIST_CI);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.KeywordsIgnoreCaseContext ctx = parser.keywordsIgnoreCase();
      assertThat(parser.getNumberOfSyntaxErrors()).isEqualTo(0);
      assertThat(ctx.keywordIgnoreCase().size()).isEqualTo(120);

      // Negative Verify
      CharStream badStream = fromString(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI);
      lexer.setInputStream(badStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      parser.keywordsIgnoreCase();
      assertThat(parser.getNumberOfSyntaxErrors()).isGreaterThan(0);
    }

    public List<Keyword> run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.KeywordsIgnoreCaseContext ctx = parser.keywordsIgnoreCase();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("ANTLR4 parsing failed with " + parser.getNumberOfSyntaxErrors() + " syntax errors");
      }
      List<Keyword> list = new ArrayList<>();
      for (ShowdownParser.KeywordIgnoreCaseContext k : ctx.keywordIgnoreCase()) {
        list.add(BenchmarkInputs.KEYWORD_MAP.get(k.getText().toLowerCase()));
      }
      return list;
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

    public ShowdownParser run() {
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

    public String run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public String run(String input) {
      ShowdownLexer lexer = LEXER.get();
      CommonTokenStream tokenStream = TOKEN_STREAM.get();
      ShowdownParser parser = PARSER.get();

      CharStream charStream = fromString(input);
      lexer.setInputStream(charStream);
      tokenStream.setTokenSource(lexer);
      parser.setInputStream(tokenStream);
      parser.reset();
      ShowdownParser.NestedCommentRootContext ctx = parser.nestedCommentRoot();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("ANTLR4 parsing failed with " + parser.getNumberOfSyntaxErrors() + " syntax errors");
      }
      return ctx.getText();
    }
  }

  private Antlr4Showdown() {}
}

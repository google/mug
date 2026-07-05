package com.google.mu.benchmarks.parsers.taker;

import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword;
import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.parsers.Chars;
import io.github.parseworks.taker.parsers.Combinators;
import io.github.parseworks.taker.parsers.Lexical;
import io.github.parseworks.taker.parsers.Numeric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

public final class TakerShowdown {

  public static final class IpFixture {
    private static final Taker<String> PARSER = Numeric.number
        .then(Chars.chr('.'))
        .map((n, dot) -> n + ".")
        .then(Numeric.number)
        .map((s, n) -> s + n)
        .then(Chars.chr('.'))
        .map((s, dot) -> s + ".")
        .then(Numeric.number)
        .map((s, n) -> s + n)
        .then(Chars.chr('.'))
        .map((s, dot) -> s + ".")
        .then(Numeric.number)
        .map((s, n) -> s + n);

    static {
      Result<String> res = PARSER.parseAll(BenchmarkInputs.IP);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().equals(BenchmarkInputs.IP);
    }

    public Result<String> run() {
      return PARSER.parseAll(BenchmarkInputs.IP);
    }
  }

  public static final class StringFixture {
    private static final Map<Character, Character> escapesMap = new HashMap<>();
    static {
      escapesMap.put('n', '\n');
      escapesMap.put('t', '\t');
      escapesMap.put('r', '\r');
      escapesMap.put('b', '\b');
      escapesMap.put('f', '\f');
      escapesMap.put('"', '"');
      escapesMap.put('\\', '\\');
      escapesMap.put('/', '/');
    }

    private static final Taker<String> PARSER = Lexical.escapedString('"', '\\', escapesMap);

    static {
      Result<String> res1 = PARSER.parseAll(BenchmarkInputs.STRING_SIMPLE);
      assert res1.matches();
      assert res1.input().isEof();
      assert res1.value().equals("hello world!");

      Result<String> res2 = PARSER.parseAll(BenchmarkInputs.STRING_ESCAPED);
      assert res2.matches();
      assert res2.input().isEof();
      assert res2.value().equals("hello \"world\"!");
    }

    public Result<String> run(String input) {
      return PARSER.parseAll(input);
    }
  }

  public static final class KeywordsFixture {
    private static final Taker<Keyword> KEYWORD;
    private static final Taker<List<Keyword>> PARSER;

    static {
      List<Taker<Keyword>> list = new ArrayList<>();
      for (String kw : BenchmarkInputs.KEYWORDS) {
        list.add(Lexical.string(kw).map(val -> BenchmarkInputs.KEYWORD_MAP.get(kw)));
      }
      KEYWORD = Combinators.oneOf(list.toArray(new Taker[0]));

      PARSER = KEYWORD
          .then(
              Chars.chr(',').then(KEYWORD).map((comma, kw) -> kw).zeroOrMore()
          )
          .map((first, rest) -> {
            List<Keyword> resList = new ArrayList<>();
            resList.add(first);
            resList.addAll(rest);
            return resList;
          });

      Result<List<Keyword>> res = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_CS);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().size() == 500;
    }

    public Result<List<Keyword>> run(String input) {
      return PARSER.parseAll(input);
    }
  }

  public static final class IgnoreCaseFixture {
    private static final Taker<Keyword> KEYWORD;
    private static final Taker<List<Keyword>> PARSER;

    static {
      List<Taker<Keyword>> list = new ArrayList<>();
      for (String kw : BenchmarkInputs.KEYWORDS) {
        list.add(Lexical.stringIgnoreCase(kw).map(val -> BenchmarkInputs.KEYWORD_MAP.get(kw)));
      }
      KEYWORD = Combinators.oneOf(list.toArray(new Taker[0]));

      PARSER = KEYWORD
          .then(
              Chars.chr(',').then(KEYWORD).map((comma, kw) -> kw).zeroOrMore()
          )
          .map((first, rest) -> {
            List<Keyword> resList = new ArrayList<>();
            resList.add(first);
            resList.addAll(rest);
            return resList;
          });

      Result<List<Keyword>> res = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_CI);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().size() == 500;
    }

    public Result<List<Keyword>> run(String input) {
      return PARSER.parseAll(input);
    }
  }

  public static final class CalculatorFixture {
    private static final Taker<Integer> PARSER = buildParser();

    private static Taker<Integer> buildParser() {
      Taker<Void> ws = Chars.chr(Character::isWhitespace).zeroOrMore().map(val -> (Void) null);
      
      Taker<Integer> takerNum = token(
          Chars.chr('-').optional().then(Numeric.number)
              .map((minusOpt, numStr) -> {
                String sign = minusOpt.isPresent() ? "-" : "";
                return Integer.parseInt(sign + numStr);
              }),
          ws
      );

      Taker<Integer> takerRef = Taker.ref();

      Taker<Integer> takerAtom = Combinators.oneOf(
          takerNum,
          token(Chars.chr('('), ws).then(takerRef).thenSkip(token(Chars.chr(')'), ws))
              .map((paren, valOut) -> valOut)
      );

      Taker<BinaryOperator<Integer>> takerMul = token(Lexical.string("*"), ws)
          .map(val -> (a, b) -> a * b);
      Taker<BinaryOperator<Integer>> takerDiv = token(Lexical.string("/"), ws)
          .map(val -> (a, b) -> a / b);
      Taker<BinaryOperator<Integer>> takerAdd = token(Lexical.string("+"), ws)
          .map(val -> (a, b) -> a + b);
      Taker<BinaryOperator<Integer>> takerSub = token(Lexical.string("-"), ws)
          .map(val -> (a, b) -> a - b);

      Taker<Integer> takerTerm = takerAtom.chainLeftOneOrMore(Combinators.oneOf(takerMul, takerDiv));
      Taker<Integer> takerExpr = ws.then(
          takerTerm.chainLeftOneOrMore(Combinators.oneOf(takerAdd, takerSub))
      ).map((wsVal, expr) -> expr);

      takerRef.set(takerExpr);
      return takerExpr;
    }

    private static <T> Taker<T> token(Taker<T> p, Taker<Void> ws) {
      return p.thenSkip(ws);
    }

    static {
      Result<Integer> res = PARSER.parseAll(BenchmarkInputs.CALCULATOR);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().equals(BenchmarkInputs.CALCULATOR_EXPECTED);
    }

    public Result<Integer> run() {
      return PARSER.parseAll(BenchmarkInputs.CALCULATOR);
    }
  }

  public static final class NestedCommentFixture {
    private static final Taker<Void> PARSER = buildParser();

    private static Taker<Void> buildParser() {
      Taker<Void> commentRef = Taker.ref();
      Taker<Void> notEnd = Combinators.not(Lexical.string("*/")).skipThen(Combinators.any())
          .map(val -> (Void) null);
      Taker<List<Void>> inner = Combinators.oneOf(commentRef, notEnd).zeroOrMore();
      Taker<Void> comment = Lexical.string("/*").then(inner).thenSkip(Lexical.string("*/"))
          .map((start, body) -> (Void) null);
      commentRef.set(comment);
      return comment;
    }

    static {
      Result<Void> res = PARSER.parseAll(BenchmarkInputs.NESTED_COMMENT);
      assert res.matches();
      assert res.input().isEof();
    }

    public Result<Void> run() {
      return run(BenchmarkInputs.NESTED_COMMENT);
    }

    public Result<Void> run(String input) {
      return PARSER.parseAll(input);
    }
  }

  public static final class UsPhoneFixture {
    private static final Taker<String> PARSER = Lexical.regex("\\(\\d{3}\\)\\d{3}-\\d{4}");

    static {
      Result<String> res = PARSER.parseAll(BenchmarkInputs.US_PHONE);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().equals(BenchmarkInputs.US_PHONE);
    }

    public Result<String> run(String input) {
      return PARSER.parseAll(input);
    }
  }

  public static final class UsPhoneListFixture {
    private static final Taker<Void> WS = Chars.chr(Character::isWhitespace).skipZeroOrMore();
    private static final Taker<List<String>> PARSER =
        WS.skipThen(UsPhoneFixture.PARSER.thenSkip(WS).zeroOrMore());

    static {
      Result<List<String>> res = PARSER.parseAll(BenchmarkInputs.US_PHONE_LIST);
      assert res.matches();
      assert res.input().isEof();
      assert res.value().size() == 1000;
    }

    public Result<List<String>> run(String input) {
      return PARSER.parseAll(input);
    }
  }
}

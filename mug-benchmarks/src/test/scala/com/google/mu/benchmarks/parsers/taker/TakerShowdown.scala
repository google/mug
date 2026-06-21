package com.google.mu.benchmarks.parsers.taker

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import io.github.parseworks.taker.CharPredicate
import io.github.parseworks.taker.Input
import io.github.parseworks.taker.Result
import io.github.parseworks.taker.Taker
import io.github.parseworks.taker.parsers.Chars
import io.github.parseworks.taker.parsers.Combinators
import io.github.parseworks.taker.parsers.Lexical
import io.github.parseworks.taker.parsers.Numeric
import java.util.function.BinaryOperator
import scala.jdk.CollectionConverters._

object TakerShowdown {

  object IpFixture {
    private val PARSER: Taker[String] = Numeric.number
      .then(Chars.chr('.'))
      .map((n, _) => n + ".")
      .then(Numeric.number)
      .map((s, n) => s + n)
      .then(Chars.chr('.'))
      .map((s, _) => s + ".")
      .then(Numeric.number)
      .map((s, n) => s + n)
      .then(Chars.chr('.'))
      .map((s, _) => s + ".")
      .then(Numeric.number)
      .map((s, n) => s + n)

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.IP)
      assert(res.matches())
      assert(res.input().isEof())
      assert(res.value() == BenchmarkInputs.IP)
    }
  }

  class IpFixture {
    def run(): Result[String] = IpFixture.PARSER.parseAll(BenchmarkInputs.IP)
  }

  object StringFixture {
    private val escapesMap: java.util.Map[java.lang.Character, java.lang.Character] = {
      val map = new java.util.HashMap[java.lang.Character, java.lang.Character]()
      map.put('n', '\n')
      map.put('t', '\t')
      map.put('r', '\r')
      map.put('b', '\b')
      map.put('f', '\f')
      map.put('"', '"')
      map.put('\\', '\\')
      map.put('/', '/')
      map
    }

    private val PARSER: Taker[String] = Lexical.escapedString('"', '\\', escapesMap)

    // Verify
    {
      val res1 = PARSER.parseAll(BenchmarkInputs.STRING_SIMPLE)
      assert(res1.matches())
      assert(res1.input().isEof())
      assert(res1.value() == "hello world!")

      val res2 = PARSER.parseAll(BenchmarkInputs.STRING_ESCAPED)
      assert(res2.matches())
      assert(res2.input().isEof())
      assert(res2.value() == "hello \"world\"!")
    }
  }

  class StringFixture {
    def run(input: String): Result[String] = StringFixture.PARSER.parseAll(input)
  }

  object KeywordsFixture {
    private val KEYWORD: Taker[Keyword] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(kw => Lexical.string(kw).map(_ => BenchmarkInputs.KEYWORD_MAP.get(kw)))
        .toArray: _*
    )
    private val PARSER: Taker[java.util.List[Keyword]] = KEYWORD
      .then(
        Chars.chr(',').then(KEYWORD).map((_, kw) => kw).zeroOrMore()
      )
      .map((first, rest) => {
        val list = new java.util.ArrayList[Keyword]()
        list.add(first)
        list.addAll(rest)
        list
      })

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_CS)
      assert(res.matches())
      assert(res.input().isEof())
      assert(res.value().size() == 120)

      val resBad = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_INVALID)
      assert(!resBad.matches() || !resBad.input().isEof())
    }
  }

  class KeywordsFixture {
    def run(input: String): Result[java.util.List[Keyword]] = {
      KeywordsFixture.PARSER.parseAll(input)
    }
  }

  object IgnoreCaseFixture {
    private val KEYWORD: Taker[Keyword] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(kw => Lexical.stringIgnoreCase(kw).map(_ => BenchmarkInputs.KEYWORD_MAP.get(kw)))
        .toArray: _*
    )
    private val PARSER: Taker[java.util.List[Keyword]] = KEYWORD
      .then(
        Chars.chr(',').then(KEYWORD).map((_, kw) => kw).zeroOrMore()
      )
      .map((first, rest) => {
        val list = new java.util.ArrayList[Keyword]()
        list.add(first)
        list.addAll(rest)
        list
      })

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_CI)
      assert(res.matches())
      assert(res.input().isEof())
      assert(res.value().size() == 120)

      val resBad = PARSER.parseAll(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI)
      assert(!resBad.matches() || !resBad.input().isEof())
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Result[java.util.List[Keyword]] = IgnoreCaseFixture.PARSER.parseAll(input)
  }

  object CalculatorFixture {
    private val PARSER: Taker[Integer] = buildParser()

    private def buildParser(): Taker[Integer] = {
      val ws = Chars.chr(Character.isWhitespace(_: Char)).zeroOrMore().map(_ => null.asInstanceOf[Void])
      val takerNum = token(
        Chars.chr('-').optional().then(Numeric.number)
          .map((minusOpt, numStr) => {
            val sign = if (minusOpt.isPresent) "-" else ""
            Integer.valueOf((sign + numStr).toInt)
          }),
        ws
      )
      
      val takerRef = Taker.ref[Integer]()
      
      val takerAtom = Combinators.oneOf(
        takerNum,
        token(Chars.chr('('), ws).then(takerRef).thenSkip(token(Chars.chr(')'), ws))
          .map((_, valOut) => valOut)
      )
      
      val takerMul = token(Lexical.string("*"), ws).map(_ => ((a: Integer, b: Integer) => Integer.valueOf(a.toInt * b.toInt)): BinaryOperator[Integer])
      val takerDiv = token(Lexical.string("/"), ws).map(_ => ((a: Integer, b: Integer) => Integer.valueOf(a.toInt / b.toInt)): BinaryOperator[Integer])
      val takerAdd = token(Lexical.string("+"), ws).map(_ => ((a: Integer, b: Integer) => Integer.valueOf(a.toInt + b.toInt)): BinaryOperator[Integer])
      val takerSub = token(Lexical.string("-"), ws).map(_ => ((a: Integer, b: Integer) => Integer.valueOf(a.toInt - b.toInt)): BinaryOperator[Integer])
          
      val takerTerm = takerAtom.chainLeftOneOrMore(Combinators.oneOf(takerMul, takerDiv))
      val takerExpr = ws.then(
        takerTerm.chainLeftOneOrMore(Combinators.oneOf(takerAdd, takerSub))
      ).map((_, expr) => expr)
          
      takerRef.set(takerExpr)
      takerExpr
    }

    private def token[T](p: Taker[T], ws: Taker[Void]): Taker[T] = {
      p.thenSkip(ws)
    }

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.CALCULATOR)
      assert(res.matches())
      assert(res.input().isEof())
      assert(res.value() == BenchmarkInputs.CALCULATOR_EXPECTED)
    }
  }

  class CalculatorFixture {
    def run(): Result[Integer] = CalculatorFixture.PARSER.parseAll(BenchmarkInputs.CALCULATOR)
  }

  object NestedCommentFixture {
    private val PARSER: Taker[Unit] = buildParser()

    private def buildParser(): Taker[Unit] = {
      val commentRef = Taker.ref[Unit]()
      val notEnd = Combinators.not(Lexical.string("*/")).skipThen(Combinators.any())
      val inner = Combinators.oneOf(commentRef, notEnd.map(_ => ())).zeroOrMore()
      val comment = Lexical.string("/*").then(inner).thenSkip(Lexical.string("*/")).map((_, _) => ())
      commentRef.set(comment)
      comment
    }

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.NESTED_COMMENT)
      assert(res.matches())
      assert(res.input().isEof())
    }
  }

  class NestedCommentFixture {
    def run(): Result[Unit] = run(BenchmarkInputs.NESTED_COMMENT)
    def run(input: String): Result[Unit] = NestedCommentFixture.PARSER.parseAll(input)
  }
}

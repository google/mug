package com.google.mu.benchmarks.parsers.taker

import com.google.mu.benchmarks.parsers.BenchmarkInputs
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
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .map(_ => "ip")

    // Verify
    {
      val res = PARSER.parseAll(BenchmarkInputs.IP)
      assert(res.matches())
      assert(res.input().isEof())
    }
  }

  class IpFixture {
    def run(): Any = IpFixture.PARSER.parseAll(BenchmarkInputs.IP)
  }

  object StringFixture {
    private val PARSER: Taker[String] = Chars.chr('"')
      .thenSkip(
        Combinators.oneOf(
          Chars.chr('\\').then(Chars.chr(_ => true)).map((_, c) => c),
          Chars.chr(CharPredicate.notAnyOf("\"\\"))
        ).zeroOrMore()
      )
      .thenSkip(Chars.chr('"'))
      .map(_ => "string")

    // Verify
    {
      val res1 = PARSER.parseAll(BenchmarkInputs.STRING_SIMPLE)
      assert(res1.matches())
      assert(res1.input().isEof())

      val res2 = PARSER.parseAll(BenchmarkInputs.STRING_ESCAPED)
      assert(res2.matches())
      assert(res2.input().isEof())
    }
  }

  class StringFixture {
    def run(input: String): Any = StringFixture.PARSER.parseAll(input)
  }

  object KeywordsFixture {
    private val PARSER: Taker[String] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(Lexical.string)
        .toArray: _*
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      val res = PARSER.parseAll(keyword)
      assert(res.matches())
      assert(res.input().isEof())
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = KeywordsFixture.PARSER.parseAll(input)
  }

  object IgnoreCaseFixture {
    private val PARSER: Taker[String] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(Lexical.stringIgnoreCase)
        .toArray: _*
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      val res = PARSER.parseAll(keyword.toUpperCase)
      assert(res.matches())
      assert(res.input().isEof())
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = IgnoreCaseFixture.PARSER.parseAll(input)
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
    def run(): Any = CalculatorFixture.PARSER.parseAll(BenchmarkInputs.CALCULATOR)
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
    def run(): Any = NestedCommentFixture.PARSER.parseAll(BenchmarkInputs.NESTED_COMMENT)
  }
}

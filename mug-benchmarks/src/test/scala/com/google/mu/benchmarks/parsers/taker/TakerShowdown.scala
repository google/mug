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

  class IpFixture {
    private val parser: Taker[String] = Numeric.number
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .thenSkip(Chars.chr('.'))
      .thenSkip(Numeric.number)
      .map(_ => "ip")

    // Verify
    private val res = parser.parseAll(BenchmarkInputs.IP)
    assert(res.matches())
    assert(res.input().isEof())

    def run(): Any = parser.parseAll(BenchmarkInputs.IP)
  }

  class StringFixture {
    private val parser: Taker[String] = Chars.chr('"')
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
      val res1 = parser.parseAll(BenchmarkInputs.STRING_SIMPLE)
      assert(res1.matches())
      assert(res1.input().isEof())

      val res2 = parser.parseAll(BenchmarkInputs.STRING_ESCAPED)
      assert(res2.matches())
      assert(res2.input().isEof())
    }

    def run(input: String): Any = parser.parseAll(input)
  }

  class KeywordsFixture {
    private val parser: Taker[String] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(Lexical.string)
        .toArray: _*
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      val res = parser.parseAll(keyword)
      assert(res.matches())
      assert(res.input().isEof())
    }

    def run(input: String): Any = parser.parseAll(input)
  }

  class IgnoreCaseFixture {
    private val parser: Taker[String] = Combinators.oneOf(
      BenchmarkInputs.KEYWORDS.asScala
        .map(Lexical.stringIgnoreCase)
        .toArray: _*
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      val res = parser.parseAll(keyword.toUpperCase)
      assert(res.matches())
      assert(res.input().isEof())
    }

    def run(input: String): Any = parser.parseAll(input)
  }

  class CalculatorFixture {
    private val parser: Taker[Integer] = {
      val ws = Chars.chr(Character.isWhitespace(_: Char)).zeroOrMore().map(_ => null.asInstanceOf[Void])
      val takerNum = token(
        Chars.chr('-').optional().then(Numeric.number)
          .map((minusOpt, numStr) => {
            val sign = if (minusOpt.isPresent) "-" else ""
            Integer.valueOf((sign + numStr).toInt)
          }),
        ws
      )
      
      val takerRef = new Array[Taker[Integer]](1)
      val takerLazyExpr = new Taker[Integer] {
        override def apply(input: Input): Result[Integer] = takerRef(0).apply(input)
      }
      
      val takerAtom = Combinators.oneOf(
        takerNum,
        token(Chars.chr('('), ws).then(takerLazyExpr).thenSkip(token(Chars.chr(')'), ws))
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
          
      takerRef(0) = takerExpr
      takerExpr
    }

    // Verify
    private val res = parser.parseAll(BenchmarkInputs.CALCULATOR)
    assert(res.matches())
    assert(res.input().isEof())
    assert(res.value() == BenchmarkInputs.CALCULATOR_EXPECTED)

    private def token[T](p: Taker[T], ws: Taker[Void]): Taker[T] = {
      p.thenSkip(ws)
    }

    def run(): Any = parser.parseAll(BenchmarkInputs.CALCULATOR)
  }
}

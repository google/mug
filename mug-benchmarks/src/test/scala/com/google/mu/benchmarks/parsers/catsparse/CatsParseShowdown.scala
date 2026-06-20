package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0}
import com.google.mu.benchmarks.parsers.BenchmarkInputs
import scala.jdk.CollectionConverters._

object CatsParseShowdown {

  object IpFixture {
    private val digits = P.charIn('0' to '9').rep.void
    private val PARSER = (digits ~ P.char('.') ~ digits ~ P.char('.') ~ digits ~ P.char('.') ~ digits).void

    // Verify
    PARSER.parse(BenchmarkInputs.IP) match {
      case Right(_) =>
      case Left(err) => throw new AssertionError(s"cats-parse IP verification failed: $err")
    }
  }

  class IpFixture {
    def run(): Any = {
      IpFixture.PARSER.parse(BenchmarkInputs.IP)
    }
  }

  object StringFixture {
    private val escape = P.char('\\') *> P.anyChar
    private val normalChar = (!P.char('"') ~ !P.char('\\')).with1 *> P.anyChar
    private val PARSER = P.char('"') *> (escape | normalChar).rep.void <* P.char('"')

    // Verify
    PARSER.parse(BenchmarkInputs.STRING_SIMPLE) match {
      case Right(_) =>
      case Left(err) => throw new AssertionError(s"cats-parse String simple verification failed: $err")
    }
    PARSER.parse(BenchmarkInputs.STRING_ESCAPED) match {
      case Right(_) =>
      case Left(err) => throw new AssertionError(s"cats-parse String escaped verification failed: $err")
    }
  }

  class StringFixture {
    def run(input: String): Any = {
      StringFixture.PARSER.parse(input)
    }
  }

  object KeywordsFixture {
    private val PARSER = P.oneOf(
      BenchmarkInputs.KEYWORDS.asScala.map(P.string).toList
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      PARSER.parse(keyword) match {
        case Right(_) =>
        case Left(err) => throw new AssertionError(s"cats-parse Keywords verification failed for '$keyword': $err")
      }
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = {
      KeywordsFixture.PARSER.parse(input)
    }
  }

  object IgnoreCaseFixture {
    private val PARSER = P.oneOf(
      BenchmarkInputs.KEYWORDS.asScala.map(P.ignoreCase).toList
    )

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      PARSER.parse(keyword.toUpperCase) match {
        case Right(_) =>
        case Left(err) => throw new AssertionError(s"cats-parse IgnoreCase verification failed for '$keyword': $err")
      }
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = {
      IgnoreCaseFixture.PARSER.parse(input)
    }
  }

  object CalculatorFixture {
    private val ws = P.charIn(" \t\r\n").rep0
    private def token[A](p: P[A]): P[A] = p <* ws

    private val PARSER: cats.parse.Parser0[Int] = ws *> P.recursive[Int] { recurse =>
      val num = token((P.char('-').?.with1 ~ P.charIn('0' to '9').rep).string.map(_.toInt))
      val factor = (token(P.char('(')) *> recurse <* token(P.char(')'))) | num

      val mulDiv: P[(Int, Int) => Int] =
        token(P.char('*')).as((a: Int, b: Int) => a * b) | token(P.char('/')).as((a: Int, b: Int) => a / b)

      val term = (factor ~ (mulDiv ~ factor).rep0).map { case (first, rest) =>
        rest.foldLeft(first) { case (acc, (op, next)) => op(acc, next) }
      }

      val addSub: P[(Int, Int) => Int] =
        token(P.char('+')).as((a: Int, b: Int) => a + b) | token(P.char('-')).as((a: Int, b: Int) => a - b)

      val expr = (term ~ (addSub ~ term).rep0).map { case (first, rest) =>
        rest.foldLeft(first) { case (acc, (op, next)) => op(acc, next) }
      }

      expr
    }

    // Verify
    PARSER.parse(BenchmarkInputs.CALCULATOR) match {
      case Right(("", value)) if value == BenchmarkInputs.CALCULATOR_EXPECTED =>
      case Right((left, value)) =>
        throw new AssertionError(s"cats-parse Calculator verification failed. Expected: ${BenchmarkInputs.CALCULATOR_EXPECTED}, got: $value, unparsed: $left")
      case Left(err) =>
        throw new AssertionError(s"cats-parse Calculator verification failed: $err")
    }
  }

  class CalculatorFixture {
    def run(): Any = {
      CalculatorFixture.PARSER.parse(BenchmarkInputs.CALCULATOR)
    }
  }
}

package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0}
import com.google.mu.benchmarks.parsers.BenchmarkInputs
import scala.jdk.CollectionConverters._

object CatsParseShowdown {

  object IpFixture {
    private val digits = P.charIn('0' to '9').rep.void
    private val PARSER = (digits ~ P.char('.') ~ digits ~ P.char('.') ~ digits ~ P.char('.') ~ digits).string

    // Verify
    PARSER.parse(BenchmarkInputs.IP) match {
      case Right((_, res)) if res == BenchmarkInputs.IP =>
      case Left(err) => throw new AssertionError(s"cats-parse IP verification failed: $err")
    }
  }

  class IpFixture {
    def run(): Either[P.Error, (String, String)] = {
      IpFixture.PARSER.parse(BenchmarkInputs.IP)
    }
  }

  object StringFixture {
    val PARSER = cats.parse.strings.Json.delimited.parser

    // Verify
    PARSER.parse(BenchmarkInputs.STRING_SIMPLE) match {
      case Right(("", value)) if value == "hello world!" =>
      case other => throw new AssertionError(s"cats-parse String simple verification failed: $other")
    }
    PARSER.parse(BenchmarkInputs.STRING_ESCAPED) match {
      case Right(("", value)) if value == "hello \"world\"!" =>
      case other => throw new AssertionError(s"cats-parse String escaped verification failed: $other")
    }
  }

  class StringFixture {
    def run(input: String): Either[P.Error, (String, String)] = {
      StringFixture.PARSER.parse(input)
    }
  }

  object KeywordsFixture {
    private val KEYWORD = P.oneOf(
      BenchmarkInputs.KEYWORDS.asScala.map(kw => P.string(kw).map(_ => BenchmarkInputs.KEYWORD_MAP.get(kw))).toList
    )
    private val EOF = P.not(P.anyChar)
    private val PARSER = (KEYWORD.repSep(P.char(',')) <* EOF).map(_.toList.asJava)

    // Verify
    PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Right(("", result)) if result.size() == 500 =>
      case other => throw new AssertionError(s"cats-parse Keywords verification failed: $other")
    }
    PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case Left(_) =>
      case Right(other) => throw new AssertionError(s"cats-parse Keywords should have failed on invalid input but got: $other")
    }
  }

  class KeywordsFixture {
    def run(input: String): Either[P.Error, (String, java.util.List[BenchmarkInputs.Keyword])] = {
      KeywordsFixture.PARSER.parse(input)
    }
  }

  object IgnoreCaseFixture {
    private val KEYWORD = P.oneOf(
      BenchmarkInputs.KEYWORDS.asScala.map(kw => P.ignoreCase(kw).map(_ => BenchmarkInputs.KEYWORD_MAP.get(kw))).toList
    )
    private val EOF = P.not(P.anyChar)
    private val PARSER = (KEYWORD.repSep(P.char(',')) <* EOF).map(_.toList.asJava)

    // Verify
    PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Right(("", result)) if result.size() == 500 =>
      case other => throw new AssertionError(s"cats-parse IgnoreCase verification failed: $other")
    }
    PARSER.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI) match {
      case Left(_) =>
      case Right(other) => throw new AssertionError(s"cats-parse IgnoreCase should have failed on invalid input but got: $other")
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Either[P.Error, (String, java.util.List[BenchmarkInputs.Keyword])] = {
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
    def run(): Either[P.Error, (String, Int)] = {
      CalculatorFixture.PARSER.parse(BenchmarkInputs.CALCULATOR)
    }
  }

  object NestedCommentFixture {
    private val PARSER = P.recursive[Unit] { self =>
      val notDelim = (!P.string("/*") ~ !P.string("*/")).with1 *> P.anyChar
      val inner = (notDelim.void | self).rep0.void
      P.string("/*") *> inner <* P.string("*/")
    }

    // Verify
    PARSER.parse(BenchmarkInputs.NESTED_COMMENT) match {
      case Right(("", ())) =>
      case Right((left, ())) =>
        throw new AssertionError(s"cats-parse NestedComment verification failed: unparsed: $left")
      case Left(err) =>
        throw new AssertionError(s"cats-parse NestedComment verification failed: $err")
    }
  }

  class NestedCommentFixture {
    def run(): Either[P.Error, (String, Unit)] = {
      run(BenchmarkInputs.NESTED_COMMENT)
    }

    def run(input: String): Either[P.Error, (String, Unit)] = {
      NestedCommentFixture.PARSER.parse(input)
    }
  }

  object UsPhoneFixture {
    private val d3 = P.charIn('0' to '9').rep(3, 3).void
    private val d4 = P.charIn('0' to '9').rep(4, 4).void
    private val EOF = P.not(P.anyChar)
    val PHONE = (P.char('(') ~ d3 ~ P.char(')') ~ d3 ~ P.char('-') ~ d4).string
    val PARSER = PHONE <* EOF

    // Verify
    PARSER.parse(BenchmarkInputs.US_PHONE) match {
      case Right(("", res)) if res == BenchmarkInputs.US_PHONE =>
      case other => throw new AssertionError(s"cats-parse US_PHONE verification failed: $other")
    }
  }

  class UsPhoneFixture {
    def run(input: String): Either[P.Error, (String, String)] = {
      UsPhoneFixture.PARSER.parse(input)
    }
  }

  object UsPhoneListFixture {
    private val ws = P.charIn(" \t\r\n").rep0.void
    private val EOF = P.not(P.anyChar)
    private val PARSER = (ws *> UsPhoneFixture.PHONE.repSep0(ws) <* ws <* EOF).map(_.toList.asJava)

    // Verify
    PARSER.parse(BenchmarkInputs.US_PHONE_LIST) match {
      case Right(("", res)) if res.size() == 1000 =>
      case other => throw new AssertionError(s"cats-parse US_PHONE_LIST verification failed: $other")
    }
  }

  class UsPhoneListFixture {
    def run(input: String): Either[P.Error, (String, java.util.List[String])] = {
      UsPhoneListFixture.PARSER.parse(input)
    }
  }
}

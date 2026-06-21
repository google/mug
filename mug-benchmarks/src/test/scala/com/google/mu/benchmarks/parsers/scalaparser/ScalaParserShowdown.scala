package com.google.mu.benchmarks.parsers.scalaparser

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import scala.util.parsing.combinator.RegexParsers
import scala.jdk.CollectionConverters._

object ScalaParserShowdown {
  
  object IpFixture extends RegexParsers {
    override val skipWhitespace = false
    private val digit = """[0-9]+""".r
    private val dot = "."
    val parser = (digit ~ dot ~ digit ~ dot ~ digit ~ dot ~ digit) ^^ { _ => "ip" }
    
    // Verify
    parse(parser, BenchmarkInputs.IP) match {
      case Success(_, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators Ip verification failed: $other")
    }
  }

  class IpFixture {
    def run(): Any = {
      IpFixture.parse(IpFixture.parser, BenchmarkInputs.IP)
    }
  }

  object StringFixture extends RegexParsers {
    override val skipWhitespace = false
    private val stringVal = "\"" + """([^"\\]|\\.)*""" + "\""
    val parser = stringVal.r

    // Verify
    parse(parser, BenchmarkInputs.STRING_SIMPLE) match {
      case Success(_, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators StringSimple verification failed: $other")
    }
    parse(parser, BenchmarkInputs.STRING_ESCAPED) match {
      case Success(_, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators StringEscaped verification failed: $other")
    }
  }

  class StringFixture {
    def run(input: String): Any = {
      StringFixture.parse(StringFixture.parser, input)
    }
  }

  object KeywordsFixture extends RegexParsers {
    private val KEYWORD_REGEX = BenchmarkInputs.KEYWORDS.asScala.mkString("|")
    private val parser = ("^(" + KEYWORD_REGEX + ")(,(" + KEYWORD_REGEX + "))*$").r ^^ { str => str.count(_ == ',') + 1 }

    // Verify
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Success(120, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators Keywords verification failed: $other")
    }
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case Success(_, next) if next.atEnd => throw new AssertionError("scala-parser-combinators Keywords should have failed on invalid input")
      case _ => // expected!
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = {
      KeywordsFixture.parse(KeywordsFixture.parser, input)
    }
  }

  object IgnoreCaseFixture extends RegexParsers {
    private val KEYWORD_REGEX = BenchmarkInputs.KEYWORDS.asScala.mkString("|")
    private val parser = ("^(?i)(" + KEYWORD_REGEX + ")(,(" + KEYWORD_REGEX + "))*$").r ^^ { str => str.count(_ == ',') + 1 }

    // Verify
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Success(120, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators IgnoreCase verification failed: $other")
    }
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_INVALID_CI) match {
      case Success(_, next) if next.atEnd => throw new AssertionError("scala-parser-combinators IgnoreCase should have failed on invalid input")
      case _ => // expected!
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = {
      IgnoreCaseFixture.parse(IgnoreCaseFixture.parser, input)
    }
  }

  object CalculatorFixture extends RegexParsers {
    private val number = """-?[0-9]+""".r ^^ { _.toInt }
    
    private def atom: Parser[Int] = number | "(" ~> expr <~ ")"
    
    private def term: Parser[Int] = atom ~ rep("*" ~ atom | "/" ~ atom) ^^ {
      case first ~ list => list.foldLeft(first) {
        case (acc, "*" ~ x) => acc * x
        case (acc, "/" ~ x) => acc / x
      }
    }
    
    private def expr: Parser[Int] = term ~ rep("+" ~ term | "-" ~ term) ^^ {
      case first ~ list => list.foldLeft(first) {
        case (acc, "+" ~ x) => acc + x
        case (acc, "-" ~ x) => acc - x
      }
    }
    
    val parser = expr

    // Verify
    parse(parser, BenchmarkInputs.CALCULATOR) match {
      case Success(res, next) if next.atEnd =>
        if (res != BenchmarkInputs.CALCULATOR_EXPECTED) {
          throw new AssertionError(s"scala-parser-combinators Calculator expected ${BenchmarkInputs.CALCULATOR_EXPECTED} but got $res")
        }
      case other => throw new AssertionError(s"scala-parser-combinators Calculator verification failed: $other")
    }
  }

  class CalculatorFixture {
    def run(): Any = {
      CalculatorFixture.parse(CalculatorFixture.parser, BenchmarkInputs.CALCULATOR)
    }
  }

  object NestedCommentFixture extends RegexParsers {
    override val skipWhitespace = false
    
    private def comment: Parser[Unit] = "/*" ~ rep(comment | not("*/") ~> ".".r) ~ "*/" ^^ { _ => () }
    
    val parser = comment

    // Verify
    parse(parser, BenchmarkInputs.NESTED_COMMENT) match {
      case Success(_, next) if next.atEnd =>
      case other => throw new AssertionError(s"scala-parser-combinators NestedComment verification failed: $other")
    }
  }

  class NestedCommentFixture {
    def run(): Any = {
      NestedCommentFixture.parse(NestedCommentFixture.parser, BenchmarkInputs.NESTED_COMMENT)
    }
  }
}

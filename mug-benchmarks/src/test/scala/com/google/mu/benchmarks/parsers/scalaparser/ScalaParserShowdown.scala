package com.google.mu.benchmarks.parsers.scalaparser

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import scala.util.parsing.combinator.RegexParsers
import scala.jdk.CollectionConverters._

object ScalaParserShowdown {
  
  object IpFixture extends RegexParsers {
    override val skipWhitespace = false
    private val digit = """[0-9]+""".r
    private val dot = "."
    val parser = (digit ~ dot ~ digit ~ dot ~ digit ~ dot ~ digit) ^^ {
      case d1 ~ _ ~ d2 ~ _ ~ d3 ~ _ ~ d4 => s"$d1.$d2.$d3.$d4"
    }
    
    // Verify
    parse(parser, BenchmarkInputs.IP) match {
      case Success(res, next) if next.atEnd && res == BenchmarkInputs.IP =>
      case other => throw new AssertionError(s"scala-parser-combinators Ip verification failed: $other")
    }
  }

  class IpFixture {
    def run(): IpFixture.ParseResult[String] = {
      IpFixture.parse(IpFixture.parser, BenchmarkInputs.IP)
    }
  }

  import scala.util.parsing.combinator.JavaTokenParsers

  object StringFixture extends JavaTokenParsers {
    override val skipWhitespace = false
    val parser: Parser[String] = stringLiteral ^^ { rawText =>
      StringContext.processEscapes(rawText.substring(1, rawText.length - 1))
    }

    // Verify
    parse(parser, BenchmarkInputs.STRING_SIMPLE) match {
      case Success(res, next) if next.atEnd && res == "hello world!" =>
      case other => throw new AssertionError(s"scala-parser-combinators StringSimple verification failed: $other")
    }
    parse(parser, BenchmarkInputs.STRING_ESCAPED) match {
      case Success(res, next) if next.atEnd && res == "hello \"world\"!" =>
      case other => throw new AssertionError(s"scala-parser-combinators StringEscaped verification failed: $other")
    }
  }

  class StringFixture {
    def run(input: String): StringFixture.ParseResult[String] = {
      StringFixture.parse(StringFixture.parser, input)
    }
  }

  object KeywordsFixture extends RegexParsers {
    private val keyword: Parser[Keyword] =
      BenchmarkInputs.KEYWORDS.asScala
        .map(kw => literal(kw) ^^ { _ => BenchmarkInputs.KEYWORD_MAP.get(kw) })
        .reduceLeft(_ | _)
    private val parser: Parser[java.util.List[Keyword]] =
      phrase(repsep(keyword, ",")) ^^ { _.asJava }

    // Verify
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Success(result, next) if next.atEnd && result.size() == 120 =>
      case other => throw new AssertionError(s"scala-parser-combinators Keywords verification failed: $other")
    }
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case Success(_, next) if next.atEnd => throw new AssertionError("scala-parser-combinators Keywords should have failed on invalid input")
      case _ => // expected!
    }
  }

  class KeywordsFixture {
    def run(input: String): KeywordsFixture.ParseResult[java.util.List[Keyword]] = {
      KeywordsFixture.parse(KeywordsFixture.parser, input)
    }
  }

  object IgnoreCaseFixture extends RegexParsers {
    private val keyword: Parser[Keyword] =
      BenchmarkInputs.KEYWORDS.asScala
        .map(kw => ("(?i)" + kw).r ^^ { _ => BenchmarkInputs.KEYWORD_MAP.get(kw) })
        .reduceLeft(_ | _)
    private val parser: Parser[java.util.List[Keyword]] =
      phrase(repsep(keyword, ",")) ^^ { _.asJava }

    // Verify
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Success(result, next) if next.atEnd && result.size() == 120 =>
      case other => throw new AssertionError(s"scala-parser-combinators IgnoreCase verification failed: $other")
    }
    parse(parser, BenchmarkInputs.KEYWORDS_LIST_INVALID_CI) match {
      case Success(_, next) if next.atEnd => throw new AssertionError("scala-parser-combinators IgnoreCase should have failed on invalid input")
      case _ => // expected!
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): IgnoreCaseFixture.ParseResult[java.util.List[Keyword]] = {
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
    def run(): CalculatorFixture.ParseResult[Int] = {
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
    def run(): NestedCommentFixture.ParseResult[Unit] = {
      run(BenchmarkInputs.NESTED_COMMENT)
    }

    def run(input: String): NestedCommentFixture.ParseResult[Unit] = {
      NestedCommentFixture.parse(NestedCommentFixture.parser, input)
    }
  }
}

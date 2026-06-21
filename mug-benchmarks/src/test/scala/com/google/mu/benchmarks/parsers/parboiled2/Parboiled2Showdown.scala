package com.google.mu.benchmarks.parsers.parboiled2

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import org.parboiled2._
import scala.util.{Success, Failure}
import scala.jdk.CollectionConverters._

object Parboiled2Showdown {

  // 1. IP Address
  class IpParser(val input: ParserInput) extends Parser {
    def digit = rule { oneOrMore(CharPredicate.Digit) }
    def dot = rule { '.' }
    def ip = rule { digit ~ dot ~ digit ~ dot ~ digit ~ dot ~ digit ~ EOI }
  }

  object IpFixture {
    // Verify
    new IpParser(BenchmarkInputs.IP).ip.run() match {
      case Success(_) =>
      case Failure(err) => throw new AssertionError(s"parboiled2 Ip verification failed: $err")
    }
  }

  class IpFixture {
    def run(): Any = {
      new IpParser(BenchmarkInputs.IP).ip.run()
    }
  }

  // 2. Quoted String
  class StringParser(val input: ParserInput) extends Parser {
    def esc = rule { '\\' ~ ANY }
    def normal = rule { !CharPredicate('"', '\\') ~ ANY }
    def quotedString = rule { '"' ~ zeroOrMore(esc | normal) ~ '"' ~ EOI }
  }

  object StringFixture {
    // Verify
    new StringParser(BenchmarkInputs.STRING_SIMPLE).quotedString.run() match {
      case Success(_) =>
      case Failure(err) => throw new AssertionError(s"parboiled2 StringSimple verification failed: $err")
    }
    new StringParser(BenchmarkInputs.STRING_ESCAPED).quotedString.run() match {
      case Success(_) =>
      case Failure(err) => throw new AssertionError(s"parboiled2 StringEscaped verification failed: $err")
    }
  }

  class StringFixture {
    def run(input: String): Any = {
      new StringParser(input).quotedString.run()
    }
  }

  // 3. Keywords (Case-Sensitive)
  class KeywordsParser(val input: ParserInput) extends Parser {
    def keyword = rule {
      ( str("select")
      | str("insert")
      | str("update")
      | str("delete")
      | str("create")
      | str("drop")
      | str("alter")
      | str("where")
      | str("group")
      | str("order")
      | str("having")
      | str("limit")
      )
    }
    def keywords = rule { oneOrMore(keyword).separatedBy(",") ~ EOI }
    def verifyingKeywords = rule { capture(oneOrMore(keyword).separatedBy(",")) ~> (str => str.count(_ == ',') + 1) ~ EOI }
  }

  object KeywordsFixture {
    // Verify
    new KeywordsParser(BenchmarkInputs.KEYWORDS_LIST_CS).verifyingKeywords.run() match {
      case Success(120) =>
      case other => throw new AssertionError(s"parboiled2 Keywords verification failed: $other")
    }
    new KeywordsParser(BenchmarkInputs.KEYWORDS_LIST_INVALID).keywords.run() match {
      case Failure(_) =>
      case Success(_) => throw new AssertionError("parboiled2 Keywords should have failed on invalid input")
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = {
      new KeywordsParser(input).keywords.run()
    }
  }

  // 4. Keywords (Case-Insensitive)
  class IgnoreCaseParser(val input: ParserInput) extends Parser {
    def keyword = rule {
      ( ignoreCase("select")
      | ignoreCase("insert")
      | ignoreCase("update")
      | ignoreCase("delete")
      | ignoreCase("create")
      | ignoreCase("drop")
      | ignoreCase("alter")
      | ignoreCase("where")
      | ignoreCase("group")
      | ignoreCase("order")
      | ignoreCase("having")
      | ignoreCase("limit")
      )
    }
    def keywords = rule { oneOrMore(keyword).separatedBy(",") ~ EOI }
    def verifyingKeywords = rule { capture(oneOrMore(keyword).separatedBy(",")) ~> (str => str.count(_ == ',') + 1) ~ EOI }
  }

  object IgnoreCaseFixture {
    // Verify
    new IgnoreCaseParser(BenchmarkInputs.KEYWORDS_LIST_CI).verifyingKeywords.run() match {
      case Success(120) =>
      case other => throw new AssertionError(s"parboiled2 IgnoreCase verification failed: $other")
    }
    new IgnoreCaseParser(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI).keywords.run() match {
      case Failure(_) =>
      case Success(_) => throw new AssertionError("parboiled2 IgnoreCase should have failed on invalid input")
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = {
      new IgnoreCaseParser(input).keywords.run()
    }
  }

  // 5. Calculator
  class CalculatorParser(val input: ParserInput) extends Parser {
    def ws = rule { zeroOrMore(anyOf(" \t\r\n")) }
    def number = rule { capture(optional('-') ~ oneOrMore(CharPredicate.Digit)) ~> (_.toInt) ~ ws }
    def atom: Rule1[Int] = rule { number | '(' ~ ws ~ expr ~ ')' ~ ws }
    def term: Rule1[Int] = rule { atom ~ zeroOrMore(
      '*' ~ ws ~ atom ~> ((a: Int, b: Int) => a * b)
      | '/' ~ ws ~ atom ~> ((a: Int, b: Int) => a / b)
    ) }
    def expr: Rule1[Int] = rule { term ~ zeroOrMore(
      '+' ~ ws ~ term ~> ((a: Int, b: Int) => a + b)
      | '-' ~ ws ~ term ~> ((a: Int, b: Int) => a - b)
    ) }
    def root = rule { ws ~ expr ~ EOI }
  }

  object CalculatorFixture {
    // Verify
    new CalculatorParser(BenchmarkInputs.CALCULATOR).root.run() match {
      case Success(res) =>
        if (res != BenchmarkInputs.CALCULATOR_EXPECTED) {
          throw new AssertionError(s"parboiled2 Calculator expected ${BenchmarkInputs.CALCULATOR_EXPECTED} but got $res")
        }
      case Failure(err) => throw new AssertionError(s"parboiled2 Calculator verification failed: $err")
    }
  }

  class CalculatorFixture {
    def run(): Any = {
      new CalculatorParser(BenchmarkInputs.CALCULATOR).root.run()
    }
  }

  // 6. Nested Comment
  class NestedCommentParser(val input: ParserInput) extends Parser {
    def comment: Rule0 = rule { "/*" ~ zeroOrMore(comment | !"*/" ~ ANY) ~ "*/" }
    def root = rule { comment ~ EOI }
  }

  object NestedCommentFixture {
    // Verify
    new NestedCommentParser(BenchmarkInputs.NESTED_COMMENT).root.run() match {
      case Success(_) =>
      case Failure(err) => throw new AssertionError(s"parboiled2 NestedComment verification failed: $err")
    }
  }

  class NestedCommentFixture {
    def run(): Any = {
      new NestedCommentParser(BenchmarkInputs.NESTED_COMMENT).root.run()
    }
  }
}

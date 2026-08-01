package com.google.mu.benchmarks.parsers.parboiled2

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import org.parboiled2._
import scala.util.{Success, Failure}
import scala.jdk.CollectionConverters._

object Parboiled2Showdown {

  // 1. IP Address
  class IpParser(val input: ParserInput) extends Parser {
    def digit = rule { oneOrMore(CharPredicate.Digit) }
    def dot = rule { '.' }
    def ip: Rule1[String] = rule { capture(digit ~ dot ~ digit ~ dot ~ digit ~ dot ~ digit) ~ EOI }
  }

  object IpFixture {
    // Verify
    new IpParser(BenchmarkInputs.IP).ip.run() match {
      case Success(res) if res == BenchmarkInputs.IP =>
      case Failure(err) => throw new AssertionError(s"parboiled2 Ip verification failed: $err")
    }
  }

  class IpFixture {
    def run(): scala.util.Try[String] = {
      new IpParser(BenchmarkInputs.IP).ip.run()
    }
  }

  // 2. Quoted String
  class StringParser(val input: ParserInput) extends Parser with StringBuilding {
    import CharPredicate.HexDigit
    val QuoteBackslash = CharPredicate("\"\\")
    val QuoteSlashBackSlash = QuoteBackslash ++ "/"

    def quotedString: Rule1[String] = rule {
      '"' ~ clearSB() ~ Characters ~ '"' ~ push(sb.toString) ~ EOI
    }

    def Characters = rule(zeroOrMore(NormalChar | '\\' ~ EscapedChar))

    def NormalChar = rule(!QuoteBackslash ~ ANY ~ appendSB())

    def EscapedChar =
      rule(
        QuoteSlashBackSlash ~ appendSB()
          | 'b' ~ appendSB('\b')
          | 'f' ~ appendSB('\f')
          | 'n' ~ appendSB('\n')
          | 'r' ~ appendSB('\r')
          | 't' ~ appendSB('\t')
          | Unicode ~> { (code: Int) => sb.append(code.toChar); () }
          | capture(ANY) ~> { (other: String) => sb.append('\\').append(other); () }
      )

    def Unicode = rule(
      'u' ~ capture(HexDigit ~ HexDigit ~ HexDigit ~ HexDigit) ~> (java.lang.Integer.parseInt(_, 16))
    )
  }

  object StringFixture {
    // Verify
    new StringParser(BenchmarkInputs.STRING_SIMPLE).quotedString.run() match {
      case Success(value) if value == "hello world!" =>
      case other => throw new AssertionError(s"parboiled2 StringSimple verification failed: $other")
    }
    new StringParser(BenchmarkInputs.STRING_ESCAPED).quotedString.run() match {
      case Success(value) if value == "hello \"world\"!" =>
      case other => throw new AssertionError(s"parboiled2 StringEscaped verification failed: $other")
    }
  }

  class StringFixture {
    def run(input: String): scala.util.Try[String] = {
      new StringParser(input).quotedString.run()
    }
  }

  // 3. Keywords (Case-Sensitive)
  class KeywordsParser(val input: ParserInput) extends Parser {
    def keyword: Rule1[Keyword] = rule {
      ( str("select") ~> (() => BenchmarkInputs.Keyword.SELECT)
      | str("insert") ~> (() => BenchmarkInputs.Keyword.INSERT)
      | str("update") ~> (() => BenchmarkInputs.Keyword.UPDATE)
      | str("delete") ~> (() => BenchmarkInputs.Keyword.DELETE)
      | str("create") ~> (() => BenchmarkInputs.Keyword.CREATE)
      | str("drop")   ~> (() => BenchmarkInputs.Keyword.DROP)
      | str("alter")  ~> (() => BenchmarkInputs.Keyword.ALTER)
      | str("where")  ~> (() => BenchmarkInputs.Keyword.WHERE)
      | str("group")  ~> (() => BenchmarkInputs.Keyword.GROUP)
      | str("order")  ~> (() => BenchmarkInputs.Keyword.ORDER)
      | str("having") ~> (() => BenchmarkInputs.Keyword.HAVING)
      | str("limit")  ~> (() => BenchmarkInputs.Keyword.LIMIT)
      )
    }
    def keywords: Rule1[java.util.List[Keyword]] = rule {
      (oneOrMore(keyword).separatedBy(",") ~> ((seq: Seq[Keyword]) => seq.asJava)) ~ EOI
    }
  }

  object KeywordsFixture {
    // Verify
    new KeywordsParser(BenchmarkInputs.KEYWORDS_LIST_CS).keywords.run() match {
      case Success(result: java.util.List[_]) if result.size() == 500 =>
      case other => throw new AssertionError(s"parboiled2 Keywords verification failed: $other")
    }
    new KeywordsParser(BenchmarkInputs.KEYWORDS_LIST_INVALID).keywords.run() match {
      case Failure(_) =>
      case Success(_) => throw new AssertionError("parboiled2 Keywords should have failed on invalid input")
    }
  }

  class KeywordsFixture {
    def run(input: String): scala.util.Try[java.util.List[Keyword]] = {
      new KeywordsParser(input).keywords.run()
    }
  }

  // 4. Keywords (Case-Insensitive)
  class IgnoreCaseParser(val input: ParserInput) extends Parser {
    def keyword: Rule1[Keyword] = rule {
      ( ignoreCase("select") ~> (() => BenchmarkInputs.Keyword.SELECT)
      | ignoreCase("insert") ~> (() => BenchmarkInputs.Keyword.INSERT)
      | ignoreCase("update") ~> (() => BenchmarkInputs.Keyword.UPDATE)
      | ignoreCase("delete") ~> (() => BenchmarkInputs.Keyword.DELETE)
      | ignoreCase("create") ~> (() => BenchmarkInputs.Keyword.CREATE)
      | ignoreCase("drop")   ~> (() => BenchmarkInputs.Keyword.DROP)
      | ignoreCase("alter")  ~> (() => BenchmarkInputs.Keyword.ALTER)
      | ignoreCase("where")  ~> (() => BenchmarkInputs.Keyword.WHERE)
      | ignoreCase("group")  ~> (() => BenchmarkInputs.Keyword.GROUP)
      | ignoreCase("order")  ~> (() => BenchmarkInputs.Keyword.ORDER)
      | ignoreCase("having") ~> (() => BenchmarkInputs.Keyword.HAVING)
      | ignoreCase("limit")  ~> (() => BenchmarkInputs.Keyword.LIMIT)
      )
    }
    def keywords: Rule1[java.util.List[Keyword]] = rule {
      (oneOrMore(keyword).separatedBy(",") ~> ((seq: Seq[Keyword]) => seq.asJava)) ~ EOI
    }
  }

  object IgnoreCaseFixture {
    // Verify
    new IgnoreCaseParser(BenchmarkInputs.KEYWORDS_LIST_CI).keywords.run() match {
      case Success(result: java.util.List[_]) if result.size() == 500 =>
      case other => throw new AssertionError(s"parboiled2 IgnoreCase verification failed: $other")
    }
    new IgnoreCaseParser(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI).keywords.run() match {
      case Failure(_) =>
      case Success(_) => throw new AssertionError("parboiled2 IgnoreCase should have failed on invalid input")
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): scala.util.Try[java.util.List[Keyword]] = {
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
    def run(): scala.util.Try[Int] = {
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
    def run(): scala.util.Try[Unit] = {
      run(BenchmarkInputs.NESTED_COMMENT)
    }

    def run(input: String): scala.util.Try[Unit] = {
      new NestedCommentParser(input).root.run()
    }
  }

  class UsPhoneParser(val input: ParserInput) extends Parser {
    def d3 = rule { 3.times(CharPredicate.Digit) }
    def d4 = rule { 4.times(CharPredicate.Digit) }
    def phone: Rule1[String] = rule { capture('(' ~ d3 ~ ')' ~ d3 ~ '-' ~ d4) }
    def usPhone: Rule1[String] = rule { phone ~ EOI }

    def ws = rule { zeroOrMore(CharPredicate(" \t\r\n")) }
    def phoneWs: Rule1[String] = rule { phone ~ ws }
    def usPhoneList: Rule1[java.util.List[String]] = rule {
      (ws ~ zeroOrMore(phoneWs) ~> ((seq: Seq[String]) => seq.asJava)) ~ EOI
    }
  }

  object UsPhoneFixture {
    // Verify
    new UsPhoneParser(BenchmarkInputs.US_PHONE).usPhone.run() match {
      case Success(res) if res == BenchmarkInputs.US_PHONE =>
      case Failure(err) => throw new AssertionError(s"parboiled2 US_PHONE verification failed: $err")
    }
  }

  class UsPhoneFixture {
    def run(input: String): scala.util.Try[String] = {
      new UsPhoneParser(input).usPhone.run()
    }
  }

  object UsPhoneListFixture {
    // Verify
    new UsPhoneParser(BenchmarkInputs.US_PHONE_LIST).usPhoneList.run() match {
      case Success(res) if res.size() == 1000 =>
      case Failure(err) => throw new AssertionError(s"parboiled2 US_PHONE_LIST verification failed: $err")
    }
  }

  class UsPhoneListFixture {
    def run(input: String): scala.util.Try[java.util.List[String]] = {
      new UsPhoneParser(input).usPhoneList.run()
    }
  }
}

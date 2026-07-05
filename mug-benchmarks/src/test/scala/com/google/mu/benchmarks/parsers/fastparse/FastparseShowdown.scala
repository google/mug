package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import scala.jdk.CollectionConverters._

// =========================================================================
// 1. Calculator Container (uses JavaWhitespace)
// =========================================================================
object FastparseCalculatorShowdown {
  import fastparse._, JavaWhitespace._

  object CalculatorFixture {
    private def number[_: P]: P[Int] = P( (P("-").? ~ CharsWhileIn("0-9")).! ).map(_.toInt)
    private def parens[_: P]: P[Int] = P( "(" ~ expr ~ ")" )
    private def factor[_: P]: P[Int] = P( number | parens )

    private def divMul[_: P]: P[Int] = P( factor ~ (CharIn("*/").! ~ factor).rep ).map(t => eval(t))
    private def addSub[_: P]: P[Int] = P( divMul ~ ((P("+") | P("-")).! ~ divMul).rep ).map(t => eval(t))
    private def expr[_: P]: P[Int] = P( addSub )
    def entry[_: P]: P[Int] = P( Start ~ expr ~ End )

    private def eval(tree: (Int, Seq[(String, Int)])): Int = {
      val (base, ops) = tree
      ops.foldLeft(base) {
        case (left, ("+", right)) => left + right
        case (left, ("-", right)) => left - right
        case (left, ("*", right)) => left * right
        case (left, ("/", right)) => left / right
      }
    }

    // Verify
    fastparse.parse(BenchmarkInputs.CALCULATOR, entry(_)) match {
      case Parsed.Success(value, _) if value == BenchmarkInputs.CALCULATOR_EXPECTED =>
      case f =>
        throw new AssertionError(s"fastparse Calculator verification failed. Expected: ${BenchmarkInputs.CALCULATOR_EXPECTED}, got: $f")
    }
  }

  class CalculatorFixture {
    def run(): Parsed[Int] = {
      fastparse.parse(BenchmarkInputs.CALCULATOR, CalculatorFixture.entry(_))
    }
  }
}

// =========================================================================
// 2. Main Showdown Container (uses NoWhitespace)
// =========================================================================
object FastparseShowdown {
  import fastparse._, NoWhitespace._

  object IpFixture {
    private def dot[_: P] = P( "." )
    private def digits[_: P] = P( CharsWhileIn("0-9") )
    def parser[_: P]: P[String] = P( (digits ~ dot ~ digits ~ dot ~ digits ~ dot ~ digits).! )

    // Verify
    fastparse.parse(BenchmarkInputs.IP, parser(_)) match {
      case Parsed.Success(value, _) if value == BenchmarkInputs.IP =>
      case f => throw new AssertionError(s"fastparse IP verification failed: $f")
    }
  }

  class IpFixture {
    def run(): Parsed[String] = {
      fastparse.parse(BenchmarkInputs.IP, IpFixture.parser(_))
    }
  }

  object StringFixture {
    private def stringLiteral[_: P]: P[String] = P( "\"" ~ (CharsWhile(c => c != '"' && c != '\\') | "\\" ~ AnyChar).rep ~ "\"" ).!
    def str[_: P]: P[String] = stringLiteral.map { rawText =>
      StringContext.processEscapes(rawText.substring(1, rawText.length - 1))
    }

    // Verify
    fastparse.parse(BenchmarkInputs.STRING_SIMPLE, str(_)) match {
      case Parsed.Success(val1, _) if val1 == "hello world!" =>
      case f => throw new AssertionError(s"fastparse String simple verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.STRING_ESCAPED, str(_)) match {
      case Parsed.Success(val2, _) if val2 == "hello \"world\"!" =>
      case f => throw new AssertionError(s"fastparse String escaped verification failed: $f")
    }
  }

  class StringFixture {
    def run(input: String): Parsed[String] = {
      fastparse.parse(input, StringFixture.str(_))
    }
  }

  object KeywordsFixture {
    private def choice[_: P](list: List[String]): P[Keyword] = {
      list match {
        case Nil => Fail
        case head :: tail =>
          val mappedHead = P(head).map(_ => BenchmarkInputs.KEYWORD_MAP.get(head))
          P(mappedHead | choice(tail))
      }
    }

    private def keyword[_: P]: P[Keyword] = P(choice(BenchmarkInputs.KEYWORDS.asScala.toList))
    def keywords[_: P]: P[Seq[Keyword]] = P( keyword.rep(sep = ",") )
    def keywordsList[_: P]: P[java.util.List[Keyword]] = P( keywords ~ End ).map(_.asJava)

    // Verify
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_CS, keywordsList(_)) match {
      case Parsed.Success(result, _) if result.size() == 120 =>
      case f => throw new AssertionError(s"fastparse Keywords verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID, keywordsList(_)) match {
      case Parsed.Failure(_, _, _) =>
      case s => throw new AssertionError(s"fastparse Keywords should have failed on invalid input: $s")
    }
  }

  class KeywordsFixture {
    def run(input: String): Parsed[java.util.List[Keyword]] = {
      fastparse.parse(input, KeywordsFixture.keywordsList(_))
    }
  }

  object IgnoreCaseFixture {
    private def choiceIgnoreCase[_: P](list: List[String]): P[Keyword] = {
      list match {
        case Nil => Fail
        case head :: tail =>
          val mappedHead = P(IgnoreCase(head)).map(_ => BenchmarkInputs.KEYWORD_MAP.get(head))
          P(mappedHead | choiceIgnoreCase(tail))
      }
    }

    private def ignoreCaseKeyword[_: P]: P[Keyword] = P(choiceIgnoreCase(BenchmarkInputs.KEYWORDS.asScala.toList))
    def ignoreCaseKeywords[_: P]: P[Seq[Keyword]] = P( ignoreCaseKeyword.rep(sep = ",") )
    def ignoreCaseKeywordsList[_: P]: P[java.util.List[Keyword]] = P( ignoreCaseKeywords ~ End ).map(_.asJava)

    // Verify
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_CI, ignoreCaseKeywordsList(_)) match {
      case Parsed.Success(result, _) if result.size() == 120 =>
      case f => throw new AssertionError(s"fastparse IgnoreCase verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI, ignoreCaseKeywordsList(_)) match {
      case Parsed.Failure(_, _, _) =>
      case s => throw new AssertionError(s"fastparse IgnoreCase should have failed on invalid input: $s")
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Parsed[java.util.List[Keyword]] = {
      fastparse.parse(input, IgnoreCaseFixture.ignoreCaseKeywordsList(_))
    }
  }

  object NestedCommentFixture {
    private def comment[_: P]: P[Unit] = P( "/*" ~ (comment | !"*/" ~ AnyChar).rep ~ "*/" )
    def entry[_: P]: P[Unit] = P( Start ~ comment ~ End )

    // Verify
    fastparse.parse(BenchmarkInputs.NESTED_COMMENT, entry(_)) match {
      case Parsed.Success((), _) =>
      case f =>
        throw new AssertionError(s"fastparse NestedComment verification failed: $f")
    }
  }

  class NestedCommentFixture {
    def run(): Parsed[Unit] = {
      run(BenchmarkInputs.NESTED_COMMENT)
    }

    def run(input: String): Parsed[Unit] = {
      fastparse.parse(input, NestedCommentFixture.entry(_))
    }
  }

  object UsPhoneFixture {
    private def d3[_: P] = P( CharIn("0-9").rep(exactly = 3) )
    private def d4[_: P] = P( CharIn("0-9").rep(exactly = 4) )
    def phone[_: P]: P[String] = P( ("(" ~ d3 ~ ")" ~ d3 ~ "-" ~ d4).! )
    def parser[_: P]: P[String] = P( Start ~ phone ~ End )

    // Verify
    fastparse.parse(BenchmarkInputs.US_PHONE, parser(_)) match {
      case Parsed.Success(value, _) if value == BenchmarkInputs.US_PHONE =>
      case f => throw new AssertionError(s"fastparse US_PHONE verification failed: $f")
    }
  }

  class UsPhoneFixture {
    def run(input: String): Parsed[String] = {
      fastparse.parse(input, UsPhoneFixture.parser(_))
    }
  }

  object UsPhoneListFixture {
    private def ws[_: P] = P( CharIn(" \t\r\n").rep )
    def listParser[_: P]: P[java.util.List[String]] = P( Start ~ ws ~ UsPhoneFixture.phone.rep(sep = ws) ~ ws ~ End ).map(_.asJava)

    // Verify
    fastparse.parse(BenchmarkInputs.US_PHONE_LIST, listParser(_)) match {
      case Parsed.Success(value, _) if value.size == 1000 =>
      case f => throw new AssertionError(s"fastparse US_PHONE_LIST verification failed: $f")
    }
  }

  class UsPhoneListFixture {
    def run(input: String): Parsed[java.util.List[String]] = {
      fastparse.parse(input, UsPhoneListFixture.listParser(_))
    }
  }
}

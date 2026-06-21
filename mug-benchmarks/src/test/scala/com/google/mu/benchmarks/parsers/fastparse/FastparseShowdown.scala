package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.BenchmarkInputs
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
    def run(): Any = {
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
    def parser[_: P] = P( digits ~ dot ~ digits ~ dot ~ digits ~ dot ~ digits )

    // Verify
    fastparse.parse(BenchmarkInputs.IP, parser(_)) match {
      case Parsed.Success(_, _) =>
      case f => throw new AssertionError(s"fastparse IP verification failed: $f")
    }
  }

  class IpFixture {
    def run(): Any = {
      fastparse.parse(BenchmarkInputs.IP, IpFixture.parser(_))
    }
  }

  object StringFixture {
    private def escape[_: P] = P( "\\" ~ AnyChar )
    private def normalChar[_: P] = P( CharPred(c => c != '"' && c != '\\') )
    def str[_: P] = P( "\"" ~ (escape | normalChar).rep ~ "\"" )

    // Verify
    fastparse.parse(BenchmarkInputs.STRING_SIMPLE, str(_)) match {
      case Parsed.Success(_, _) =>
      case f => throw new AssertionError(s"fastparse String simple verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.STRING_ESCAPED, str(_)) match {
      case Parsed.Success(_, _) =>
      case f => throw new AssertionError(s"fastparse String escaped verification failed: $f")
    }
  }

  class StringFixture {
    def run(input: String): Any = {
      fastparse.parse(input, StringFixture.str(_))
    }
  }

  object KeywordsFixture {
    private def choice[_: P](list: List[String]): P[Unit] = {
      list match {
        case Nil => Fail
        case head :: tail => P(head | choice(tail))
      }
    }

    private def keyword[_: P] = P(choice(BenchmarkInputs.KEYWORDS.asScala.toList))
    def keywords[_: P] = P( keyword.rep(sep = ",") )
    def keywordsList[_: P] = P( keywords ~ End )
    private def verifyingList[_: P] = P( keywords.! ~ End ).map(_.count(_ == ',') + 1)

    // Verify
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_CS, verifyingList(_)) match {
      case Parsed.Success(120, _) =>
      case f => throw new AssertionError(s"fastparse Keywords verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID, keywordsList(_)) match {
      case Parsed.Failure(_, _, _) =>
      case s => throw new AssertionError(s"fastparse Keywords should have failed on invalid input: $s")
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = {
      fastparse.parse(input, KeywordsFixture.keywordsList(_))
    }
  }

  object IgnoreCaseFixture {
    private def choiceIgnoreCase[_: P](list: List[String]): P[Unit] = {
      list match {
        case Nil => Fail
        case head :: tail => P(IgnoreCase(head) | choiceIgnoreCase(tail))
      }
    }

    private def ignoreCaseKeyword[_: P] = P(choiceIgnoreCase(BenchmarkInputs.KEYWORDS.asScala.toList))
    def ignoreCaseKeywords[_: P] = P( ignoreCaseKeyword.rep(sep = ",") )
    def ignoreCaseKeywordsList[_: P] = P( ignoreCaseKeywords ~ End )
    private def verifyingList[_: P] = P( ignoreCaseKeywords.! ~ End ).map(_.count(_ == ',') + 1)

    // Verify
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_CI, verifyingList(_)) match {
      case Parsed.Success(120, _) =>
      case f => throw new AssertionError(s"fastparse IgnoreCase verification failed: $f")
    }
    fastparse.parse(BenchmarkInputs.KEYWORDS_LIST_INVALID_CI, ignoreCaseKeywordsList(_)) match {
      case Parsed.Failure(_, _, _) =>
      case s => throw new AssertionError(s"fastparse IgnoreCase should have failed on invalid input: $s")
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = {
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
    def run(): Any = {
      fastparse.parse(BenchmarkInputs.NESTED_COMMENT, NestedCommentFixture.entry(_))
    }
  }
}

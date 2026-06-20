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

    def keywords[_: P] = P(choice(BenchmarkInputs.KEYWORDS.asScala.toList))

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      fastparse.parse(keyword, keywords(_)) match {
        case Parsed.Success(_, _) =>
        case f => throw new AssertionError(s"fastparse Keywords verification failed for '$keyword': $f")
      }
    }
  }

  class KeywordsFixture {
    def run(input: String): Any = {
      fastparse.parse(input, KeywordsFixture.keywords(_))
    }
  }

  object IgnoreCaseFixture {
    private def choiceIgnoreCase[_: P](list: List[String]): P[Unit] = {
      list match {
        case Nil => Fail
        case head :: tail => P(IgnoreCase(head) | choiceIgnoreCase(tail))
      }
    }

    def ignoreCaseKeywords[_: P] = P(choiceIgnoreCase(BenchmarkInputs.KEYWORDS.asScala.toList))

    // Verify
    for (keyword <- BenchmarkInputs.KEYWORDS.asScala) {
      fastparse.parse(keyword.toUpperCase, ignoreCaseKeywords(_)) match {
        case Parsed.Success(_, _) =>
        case f => throw new AssertionError(s"fastparse IgnoreCase verification failed for '$keyword': $f")
      }
    }
  }

  class IgnoreCaseFixture {
    def run(input: String): Any = {
      fastparse.parse(input, IgnoreCaseFixture.ignoreCaseKeywords(_))
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

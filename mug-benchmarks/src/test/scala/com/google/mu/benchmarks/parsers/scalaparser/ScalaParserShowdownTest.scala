package com.google.mu.benchmarks.parsers.scalaparser

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import java.util.List
import org.junit.Assert
import org.junit.Test
import scala.util.parsing.combinator.Parsers
import com.google.mu.benchmarks.parsers.scalaparser.ScalaParserShowdown._

class ScalaParserShowdownTest {
  private val EXPECTED_STRING_SIMPLE = "hello world!"
  private val EXPECTED_STRING_ESCAPED = "hello \"world\"!"

  @Test
  def testIp(): Unit = {
    new IpFixture().run() match {
      case success: IpFixture.Success[_] => Assert.assertEquals("192.168.1.1", success.get)
      case failure => Assert.fail(s"Scala Parser Combinators IP parsing failed: $failure")
    }
  }

  @Test
  def testQuotedStringSimple(): Unit = {
    new StringFixture().run(BenchmarkInputs.STRING_SIMPLE) match {
      case success: StringFixture.Success[_] => Assert.assertEquals(EXPECTED_STRING_SIMPLE, success.get)
      case failure => Assert.fail(s"Scala Parser Combinators StringSimple parsing failed: $failure")
    }
  }

  @Test
  def testQuotedStringEscaped(): Unit = {
    new StringFixture().run(BenchmarkInputs.STRING_ESCAPED) match {
      case success: StringFixture.Success[_] => Assert.assertEquals(EXPECTED_STRING_ESCAPED, success.get)
      case failure => Assert.fail(s"Scala Parser Combinators StringEscaped parsing failed: $failure")
    }
  }

  @Test
  def testKeywordsCaseSensitive(): Unit = {
    new KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case success: KeywordsFixture.Success[_] =>
        val keywords = success.get.asInstanceOf[List[Keyword]]
        Assert.assertEquals(500, keywords.size())
      case failure => Assert.fail(s"Scala Parser Combinators Keywords CS parsing failed: $failure")
    }
  }

  @Test
  def testKeywordsCaseInsensitive(): Unit = {
    new IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case success: IgnoreCaseFixture.Success[_] =>
        val keywords = success.get.asInstanceOf[List[Keyword]]
        Assert.assertEquals(500, keywords.size())
      case failure => Assert.fail(s"Scala Parser Combinators Keywords CI parsing failed: $failure")
    }
  }

  @Test
  def testKeywordsFailure(): Unit = {
    new KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case success: KeywordsFixture.Success[_] => Assert.fail(s"Should have failed but succeeded: $success")
      case _ =>
    }
  }

  @Test
  def testNestedComment(): Unit = {
    new NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT) match {
      case success: NestedCommentFixture.Success[_] => // just assert success!
      case failure => Assert.fail(s"Scala Parser Combinators NestedComment parsing failed: $failure")
    }
  }

  @Test
  def testNestedCommentFailure(): Unit = {
    new NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID) match {
      case success: NestedCommentFixture.Success[_] => Assert.fail(s"Should have failed but succeeded: $success")
      case _ =>
    }
  }
}

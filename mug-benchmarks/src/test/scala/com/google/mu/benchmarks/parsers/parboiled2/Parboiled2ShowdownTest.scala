package com.google.mu.benchmarks.parsers.parboiled2

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import java.util.List
import org.junit.Assert
import org.junit.Test
import scala.util.{Success, Failure}

class Parboiled2ShowdownTest {
  private val EXPECTED_STRING_SIMPLE = "hello world!"
  private val EXPECTED_STRING_ESCAPED = "hello \"world\"!"

  @Test
  def testIp(): Unit = {
    new Parboiled2Showdown.IpFixture().run() match {
      case Success(value) => Assert.assertEquals("192.168.1.1", value)
      case Failure(err) => Assert.fail(s"parboiled2 IP parsing failed: $err")
    }
  }

  @Test
  def testQuotedStringSimple(): Unit = {
    new Parboiled2Showdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE) match {
      case Success(value) => Assert.assertEquals(EXPECTED_STRING_SIMPLE, value)
      case Failure(err) => Assert.fail(s"parboiled2 StringSimple parsing failed: $err")
    }
  }

  @Test
  def testQuotedStringEscaped(): Unit = {
    new Parboiled2Showdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED) match {
      case Success(value) => Assert.assertEquals(EXPECTED_STRING_ESCAPED, value)
      case Failure(err) => Assert.fail(s"parboiled2 StringEscaped parsing failed: $err")
    }
  }

  @Test
  def testKeywordsCaseSensitive(): Unit = {
    new Parboiled2Showdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Success(keywords: List[Keyword]) => Assert.assertEquals(500, keywords.size())
      case Failure(err) => Assert.fail(s"parboiled2 Keywords CS parsing failed: $err")
    }
  }

  @Test
  def testKeywordsCaseInsensitive(): Unit = {
    new Parboiled2Showdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Success(keywords: List[Keyword]) => Assert.assertEquals(500, keywords.size())
      case Failure(err) => Assert.fail(s"parboiled2 Keywords CI parsing failed: $err")
    }
  }

  @Test
  def testKeywordsFailure(): Unit = {
    new Parboiled2Showdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case Failure(_) =>
      case Success(s) => Assert.fail(s"parboiled2 Keywords CS should have failed but succeeded: $s")
    }
  }

  @Test
  def testNestedComment(): Unit = {
    new Parboiled2Showdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT) match {
      case Success(_) =>
      case Failure(err) => Assert.fail(s"parboiled2 NestedComment parsing failed: $err")
    }
  }

  @Test
  def testNestedCommentFailure(): Unit = {
    new Parboiled2Showdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID) match {
      case Failure(_) =>
      case Success(s) => Assert.fail(s"parboiled2 NestedComment should have failed but succeeded: $s")
    }
  }
}

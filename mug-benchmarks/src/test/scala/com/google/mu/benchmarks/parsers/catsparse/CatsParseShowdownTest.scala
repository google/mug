package com.google.mu.benchmarks.parsers.catsparse

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import java.util.List
import org.junit.Assert
import org.junit.Test

class CatsParseShowdownTest {
  private val EXPECTED_STRING_SIMPLE = "hello world!"
  private val EXPECTED_STRING_ESCAPED = "hello \"world\"!"

  @Test
  def testIp(): Unit = {
    new CatsParseShowdown.IpFixture().run() match {
      case Right((_, value)) => Assert.assertEquals("192.168.1.1", value)
      case Left(err) => Assert.fail(s"cats-parse IP parsing failed: $err")
    }
  }

  @Test
  def testQuotedStringSimple(): Unit = {
    new CatsParseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE) match {
      case Right((_, value)) => Assert.assertEquals(EXPECTED_STRING_SIMPLE, value)
      case Left(err) => Assert.fail(s"cats-parse StringSimple parsing failed: $err")
    }
  }

  @Test
  def testQuotedStringEscaped(): Unit = {
    new CatsParseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED) match {
      case Right((_, value)) => Assert.assertEquals(EXPECTED_STRING_ESCAPED, value)
      case Left(err) => Assert.fail(s"cats-parse StringEscaped parsing failed: $err")
    }
  }

  @Test
  def testKeywordsCaseSensitive(): Unit = {
    new CatsParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Right((_, keywords: List[Keyword])) => Assert.assertEquals(500, keywords.size())
      case Left(err) => Assert.fail(s"cats-parse Keywords CS parsing failed: $err")
    }
  }

  @Test
  def testKeywordsCaseInsensitive(): Unit = {
    new CatsParseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Right((_, keywords: List[Keyword])) => Assert.assertEquals(500, keywords.size())
      case Left(err) => Assert.fail(s"cats-parse Keywords CI parsing failed: $err")
    }
  }

  @Test
  def testKeywordsFailure(): Unit = {
    new CatsParseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case Left(_) =>
      case Right((_, s)) => Assert.fail(s"cats-parse Keywords CS should have failed but succeeded: $s")
    }
  }

  @Test
  def testNestedComment(): Unit = {
    new CatsParseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT) match {
      case Right((_, _)) =>
      case Left(err) => Assert.fail(s"cats-parse NestedComment parsing failed: $err")
    }
  }

  @Test
  def testNestedCommentFailure(): Unit = {
    new CatsParseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID) match {
      case Left(_) =>
      case Right((_, s)) => Assert.fail(s"cats-parse NestedComment should have failed but succeeded: $s")
    }
  }
}

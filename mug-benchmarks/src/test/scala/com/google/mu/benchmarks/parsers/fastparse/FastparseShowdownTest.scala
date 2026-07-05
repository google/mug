package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.BenchmarkInputs
import com.google.mu.benchmarks.parsers.BenchmarkInputs.Keyword
import java.util.List
import org.junit.Assert
import org.junit.Test
import fastparse.Parsed

class FastparseShowdownTest {
  private val EXPECTED_STRING_SIMPLE = "hello world!"
  private val EXPECTED_STRING_ESCAPED = "hello \"world\"!"

  @Test
  def testIp(): Unit = {
    new FastparseShowdown.IpFixture().run() match {
      case Parsed.Success(value, _) => Assert.assertEquals("192.168.1.1", value)
      case f => Assert.fail(s"fastparse IP parsing failed: $f")
    }
  }

  @Test
  def testQuotedStringSimple(): Unit = {
    new FastparseShowdown.StringFixture().run(BenchmarkInputs.STRING_SIMPLE) match {
      case Parsed.Success(value, _) => Assert.assertEquals(EXPECTED_STRING_SIMPLE, value)
      case f => Assert.fail(s"fastparse StringSimple parsing failed: $f")
    }
  }

  @Test
  def testQuotedStringEscaped(): Unit = {
    new FastparseShowdown.StringFixture().run(BenchmarkInputs.STRING_ESCAPED) match {
      case Parsed.Success(value, _) => Assert.assertEquals(EXPECTED_STRING_ESCAPED, value)
      case f => Assert.fail(s"fastparse StringEscaped parsing failed: $f")
    }
  }

  @Test
  def testKeywordsCaseSensitive(): Unit = {
    new FastparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_CS) match {
      case Parsed.Success(keywords: List[Keyword], _) => Assert.assertEquals(500, keywords.size())
      case f => Assert.fail(s"fastparse Keywords CS parsing failed: $f")
    }
  }

  @Test
  def testKeywordsCaseInsensitive(): Unit = {
    new FastparseShowdown.IgnoreCaseFixture().run(BenchmarkInputs.KEYWORDS_LIST_CI) match {
      case Parsed.Success(keywords: List[Keyword], _) => Assert.assertEquals(500, keywords.size())
      case f => Assert.fail(s"fastparse Keywords CI parsing failed: $f")
    }
  }

  @Test
  def testKeywordsFailure(): Unit = {
    new FastparseShowdown.KeywordsFixture().run(BenchmarkInputs.KEYWORDS_LIST_INVALID) match {
      case _: Parsed.Failure =>
      case s => Assert.fail(s"fastparse Keywords CS should have failed but succeeded: $s")
    }
  }

  @Test
  def testNestedComment(): Unit = {
    new FastparseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT) match {
      case Parsed.Success(_, _) =>
      case f => Assert.fail(s"fastparse NestedComment parsing failed: $f")
    }
  }

  @Test
  def testNestedCommentFailure(): Unit = {
    new FastparseShowdown.NestedCommentFixture().run(BenchmarkInputs.NESTED_COMMENT_INVALID) match {
      case _: Parsed.Failure =>
      case s => Assert.fail(s"fastparse NestedComment should have failed but succeeded: $s")
    }
  }
}

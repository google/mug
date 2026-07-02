package com.google.mu.benchmarks.parsers.dotparse

import org.junit.Test
import org.junit.Assert._
import cats.parse.{Parser => CP}
import scala.jdk.CollectionConverters._

class StringInParserComparisonTest {

  @Test
  def testComparison(): Unit = {
    val strings = List("foobar", "foofoo", "foobaz", "foo", "bar")
    val stringsJava = strings.asJava

    // Cats-parse parsers (setup once)
    val catsStringIn = CP.stringIn(strings)
    val catsStringInV = catsStringIn.void
    val catsOneOf = CP.oneOf(strings.map(CP.string(_)))

    // Dot-parse parsers (setup once, typed as AnyRef to avoid Scala compiler bug)
    val dotStringIn: AnyRef = StringInParser.stringIn(stringsJava)
    val dotOneOf: AnyRef = StringInParser.oneOf(stringsJava)

    val parseMethod = dotStringIn.getClass.getMethod("parse", classOf[String])
    val matchesMethod = dotStringIn.getClass.getMethod("matches", classOf[String])
    val oneOfMatchesMethod = dotOneOf.getClass.getMethod("matches", classOf[String])

    val testInputs = List("foofoo", "bar", "foobar", "foobaz", "foo")

    for (input <- testInputs) {
      // stringIn
      val catsResult = catsStringIn.parseAll(input)
      val dotResult = try {
        Right(parseMethod.invoke(dotStringIn, input).asInstanceOf[String])
      } catch {
        case e: java.lang.reflect.InvocationTargetException => Left(e.getCause)
        case e: Exception => Left(e)
      }
      assertEquals(s"Mismatch for input: $input", catsResult, dotResult)

      // stringInV
      val catsVResult = catsStringInV.parseAll(input)
      val dotVResult = matchesMethod.invoke(dotStringIn, input).asInstanceOf[Boolean]
      assertEquals(s"Mismatch for input V: $input", catsVResult.isRight, dotVResult)

      // oneOf
      val catsOneOfResult = catsOneOf.parseAll(input)
      val dotOneOfResult = oneOfMatchesMethod.invoke(dotOneOf, input).asInstanceOf[Boolean]
      assertEquals(s"Mismatch for oneOf: $input", catsOneOfResult.isRight, dotOneOfResult)
    }

    // Test failures
    val invalidInputs = List("foobat", "foot", "baz", "f")
    for (input <- invalidInputs) {
      val catsResult = catsStringIn.parseAll(input)
      val dotResult = try {
        Right(parseMethod.invoke(dotStringIn, input).asInstanceOf[String])
      } catch {
        case e: java.lang.reflect.InvocationTargetException => Left(e.getCause)
        case e: Exception => Left(e)
      }
      assertEquals(s"Both should fail for input: $input", catsResult.isRight, dotResult.isRight)
    }
  }

  @Test
  def testFastparse(): Unit = {
    val strings = List("foobar", "foofoo", "foobaz", "foo", "bar")
    
    val fastparseStringIn = new com.google.mu.benchmarks.parsers.fastparse.FastparseStringInWrapper("foo", strings)
    assertTrue(fastparseStringIn.parse("foobar"))
    assertTrue(fastparseStringIn.parse("foofoo"))
    assertFalse(fastparseStringIn.parse("foobat"))

    val fastparseOneOf = new com.google.mu.benchmarks.parsers.fastparse.FastparseOneOfWrapper(strings)
    assertTrue(fastparseOneOf.parse("foobar"))
    assertTrue(fastparseOneOf.parse("foofoo"))
    assertFalse(fastparseOneOf.parse("foobat"))
  }
}

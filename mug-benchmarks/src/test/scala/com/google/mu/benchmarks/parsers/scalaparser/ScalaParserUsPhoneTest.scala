package com.google.mu.benchmarks.parsers.scalaparser

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest
import java.util.List

class ScalaParserUsPhoneTest extends AbstractUsPhoneParserTest {
  private val singleFixture = new ScalaParserShowdown.UsPhoneFixture()
  private val listFixture = new ScalaParserShowdown.UsPhoneListFixture()

  override protected def parseSingle(input: String): String = {
    singleFixture.run(input) match {
      case ScalaParserShowdown.UsPhoneFixture.Success(value, next) if next.atEnd => value
      case other => throw new Exception(s"failed: $other")
    }
  }

  override protected def parseList(input: String): List[String] = {
    listFixture.run(input) match {
      case ScalaParserShowdown.UsPhoneListFixture.Success(value, next) if next.atEnd => value
      case other => throw new Exception(s"failed: $other")
    }
  }
}

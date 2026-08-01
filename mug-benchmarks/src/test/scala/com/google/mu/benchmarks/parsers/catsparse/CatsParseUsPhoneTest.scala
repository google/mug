package com.google.mu.benchmarks.parsers.catsparse

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest
import java.util.List

class CatsParseUsPhoneTest extends AbstractUsPhoneParserTest {
  private val singleFixture = new CatsParseShowdown.UsPhoneFixture()
  private val listFixture = new CatsParseShowdown.UsPhoneListFixture()

  override protected def parseSingle(input: String): String = {
    singleFixture.run(input) match {
      case Right(("", res)) => res
      case other => throw new Exception(s"failed: $other")
    }
  }

  override protected def parseList(input: String): List[String] = {
    listFixture.run(input) match {
      case Right(("", res)) => res
      case other => throw new Exception(s"failed: $other")
    }
  }
}

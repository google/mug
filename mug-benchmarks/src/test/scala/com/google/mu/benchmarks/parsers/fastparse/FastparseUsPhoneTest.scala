package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest
import fastparse.Parsed
import java.util.List

class FastparseUsPhoneTest extends AbstractUsPhoneParserTest {
  private val singleFixture = new FastparseShowdown.UsPhoneFixture()
  private val listFixture = new FastparseShowdown.UsPhoneListFixture()

  override protected def parseSingle(input: String): String = {
    singleFixture.run(input) match {
      case Parsed.Success(value, _) => value
      case f => throw new Exception(s"failed: $f")
    }
  }

  override protected def parseList(input: String): List[String] = {
    listFixture.run(input) match {
      case Parsed.Success(value, _) => value
      case f => throw new Exception(s"failed: $f")
    }
  }
}

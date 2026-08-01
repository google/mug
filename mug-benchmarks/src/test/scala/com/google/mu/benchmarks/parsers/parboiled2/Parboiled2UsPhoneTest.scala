package com.google.mu.benchmarks.parsers.parboiled2

import com.google.mu.benchmarks.parsers.AbstractUsPhoneParserTest
import java.util.List
import scala.util.{Success, Failure}

class Parboiled2UsPhoneTest extends AbstractUsPhoneParserTest {
  private val singleFixture = new Parboiled2Showdown.UsPhoneFixture()
  private val listFixture = new Parboiled2Showdown.UsPhoneListFixture()

  override protected def parseSingle(input: String): String = {
    singleFixture.run(input) match {
      case Success(value) => value
      case Failure(f) => throw new Exception(s"failed: $f")
    }
  }

  override protected def parseList(input: String): List[String] = {
    listFixture.run(input) match {
      case Success(value) => value
      case Failure(f) => throw new Exception(s"failed: $f")
    }
  }
}

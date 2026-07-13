package com.google.mu.benchmarks.parsers.parboiled2

import com.google.mu.benchmarks.parsers.AbstractCssParserTest
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet

class Parboiled2CssParserTest extends AbstractCssParserTest {
  override protected def parse(input: String): Stylesheet = {
    Parboiled2CssParser.parse(input)
  }

  @org.junit.Test
  def parse_dotDecimalDimension_success(): Unit = {
    val stylesheet1 = parse("html { margin: .67em; }")
    org.junit.Assert.assertNotNull(stylesheet1)
  }

  @org.junit.Test
  def parse_integerDimension_success(): Unit = {
    val stylesheet2 = parse("html { margin: 67em; }")
    org.junit.Assert.assertNotNull(stylesheet2)
  }

  @org.junit.Test
  def parse_dimensionWithUnknownUnit_success(): Unit = {
    val stylesheet3 = parse("html { margin: 67xm; }")
    org.junit.Assert.assertNotNull(stylesheet3)
  }
}

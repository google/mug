package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.AbstractCssParserTest
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet

class FastparseCssParserTest extends AbstractCssParserTest {
  override protected def parse(input: String): Stylesheet = {
    FastparseCssParser.parse(input)
  }
}

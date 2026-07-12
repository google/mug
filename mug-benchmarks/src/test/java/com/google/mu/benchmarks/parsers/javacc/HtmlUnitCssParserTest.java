package com.google.mu.benchmarks.parsers.javacc;

import com.google.mu.benchmarks.parsers.AbstractCssParserTest;
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlUnitCssParserTest extends AbstractCssParserTest {

  private final HtmlUnitCssParser parser = new HtmlUnitCssParser();

  @Override
  protected Stylesheet parse(String input) {
    return parser.parse(input);
  }
}

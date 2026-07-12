package com.google.mu.benchmarks.parsers.antlr4;

import com.google.mu.benchmarks.parsers.AbstractCssParserTest;
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Antlr4CssParserTest extends AbstractCssParserTest {

  private final Antlr4CssParser parser = new Antlr4CssParser();

  @Override
  protected Stylesheet parse(String input) {
    return parser.parse(input);
  }

  @Override
  @org.junit.Test
  @org.junit.Ignore("ANTLR4 CSS grammar does not support scientific float notation")
  public void parse_scientificNumber_success() throws Throwable {
    super.parse_scientificNumber_success();
  }
}

package com.google.mu.benchmarks.parsers.betterparse;

import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import com.google.mu.benchmarks.parsers.AbstractCssParserTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BetterParseCssParserTest extends AbstractCssParserTest {
  @Override
  protected Stylesheet parse(String input) {
    return BetterParseCssParser.INSTANCE.parse(input);
  }
}

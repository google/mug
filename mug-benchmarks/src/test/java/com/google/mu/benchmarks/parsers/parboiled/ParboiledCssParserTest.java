package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import com.google.mu.benchmarks.parsers.AbstractCssParserTest;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParboiledCssParserTest extends AbstractCssParserTest {
  @Override
  protected Stylesheet parse(String input) {
    return ParboiledCssParser.parse(input);
  }
}

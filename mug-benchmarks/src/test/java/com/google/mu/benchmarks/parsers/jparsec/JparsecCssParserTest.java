package com.google.mu.benchmarks.parsers.jparsec;

import com.google.mu.benchmarks.parsers.AbstractCssParserTest;
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JparsecCssParserTest extends AbstractCssParserTest {

  @Override
  protected Stylesheet parse(String input) {
    return JparsecCssParser.parse(input);
  }
}

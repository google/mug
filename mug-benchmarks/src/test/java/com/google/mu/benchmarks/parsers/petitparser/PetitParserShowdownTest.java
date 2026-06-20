package com.google.mu.benchmarks.parsers.petitparser;

import org.junit.Test;

public class PetitParserShowdownTest {
  @Test
  public void testClassLoadsAndVerifies() {
    // Instantiating the fixtures triggers class loading and executes all static assertions!
    new PetitParserShowdown.IpFixture();
    new PetitParserShowdown.StringFixture();
    new PetitParserShowdown.KeywordsFixture();
    new PetitParserShowdown.IgnoreCaseFixture();
    new PetitParserShowdown.CalculatorFixture();
    new PetitParserShowdown.NestedCommentFixture();
  }
}

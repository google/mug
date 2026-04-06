package com.google.mu.benchmarks;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.labs.parse.Parser;

/**
 * Benchmark for Chemical Notation Parser.
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ChemicalNotationBenchmark {
  private static final String INPUT = "C6H12O6Ca(OH)2Fe2(SO4)3";

  // Parsers
  private static final Parser<Integer> COUNT = digits().map(Integer::parseInt);

  private static final Parser<Element> ELEMENT =
      one(charsIn("[A-Z]"))
          .followedBy(one(charsIn("[a-z]")).optional())
          .source()
          .map(Element::of)
          .optionallyFollowedBy(COUNT, Element::withCount);

  private static final Parser<Formula> FORMULA = Parser.define(self -> {
    Parser<Part> part = anyOf(
        ELEMENT,
        self.between("(", ")").map(Group::of).optionallyFollowedBy(COUNT, Group::withCount));
    return part.atLeastOnce().map(Formula::new);
  });

  // Tests
  @Test
  public void testSimpleFormula() {
    assertThat(FORMULA.parse("H2O")).isEqualTo(
        new Formula(asList(
            Element.of("H").withCount(2),
            Element.of("O"))));
  }

  @Test
  public void testFormulaWithGroup() {
    assertThat(FORMULA.parse("Ca(OH)2")).isEqualTo(
        new Formula(asList(
            Element.of("Ca"),
            Group.of(new Formula(asList(
                Element.of("O"),
                Element.of("H")))).withCount(2))));
  }

  @Test
  public void testComplexFormula() {
    assertThat(FORMULA.parse("Fe2(SO4)3")).isEqualTo(
        new Formula(asList(
            Element.of("Fe").withCount(2),
            Group.of(new Formula(asList(
                Element.of("S"),
                Element.of("O").withCount(4)))).withCount(3))));
  }

  @Test
  public void testRegexEquivalent() {
    List<Part> parts = PART_PATTERN.matcher(INPUT)
        .results()
        .map(ChemicalNotationBenchmark::fromRegexMatch)
        .toList();
    assertThat(new Formula(parts)).isEqualTo(FORMULA.parse(INPUT));
  }

  @Test
  public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(expectedFormula());
  }

  @Test
  public void testRegexBenchmark() {
    assertThat(regex()).isEqualTo(expectedFormula());
  }

  private static Formula expectedFormula() {
    return new Formula(asList(
        Element.of("C").withCount(6),
        Element.of("H").withCount(12),
        Element.of("O").withCount(6),
        Element.of("Ca"),
        Group.of(new Formula(asList(
            Element.of("O"),
            Element.of("H")))).withCount(2),
        Element.of("Fe").withCount(2),
        Group.of(new Formula(asList(
            Element.of("S"),
            Element.of("O").withCount(4)))).withCount(3)));
  }

  // Benchmarks
  @Benchmark
  public Formula dotParse() {
    return FORMULA.parse(INPUT);
  }

  private static final Pattern PART_PATTERN = Pattern.compile(
      "([A-Z][a-z]?)(\\d*)|\\(([^)]+)\\)(\\d*)");

  @Benchmark
  public Formula regex() {
    return new Formula(
        PART_PATTERN.matcher(INPUT)
            .results()
            .map(ChemicalNotationBenchmark::fromRegexMatch)
            .toList());
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  // Domain Records
  public interface Part {}

  public record Element(String symbol, int count) implements Part {
    public static Element of(String symbol) {
      return new Element(symbol, 1);
    }

    public Element withCount(int count) {
      return new Element(symbol, count);
    }
  }

  public record Group(Formula formula, int count) implements Part {
    public static Group of(Formula formula) {
      return new Group(formula, 1);
    }

    public Group withCount(int count) {
      return new Group(formula, count);
    }
  }

  public record Formula(List<Part> parts) {}

  // Private helpers
  private static Part fromRegexMatch(MatchResult match) {
    String symbol = match.group(1);
    String elementCount = match.group(2);
    String groupContent = match.group(3);
    String groupCount = match.group(4);

    if (symbol != null) {
      int count = elementCount.isEmpty() ? 1 : Integer.parseInt(elementCount);
      return Element.of(symbol).withCount(count);
    } else {
      int count = groupCount.isEmpty() ? 1 : Integer.parseInt(groupCount);
      // For simplicity in regex baseline, we parse the group content recursively
      Formula groupFormula = FORMULA.parse(groupContent);
      return Group.of(groupFormula).withCount(count);
    }
  }
}

package com.google.mu.benchmarks;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
 * Benchmark for Dice Notation Parser.
 * See spec: https://dice-roller.github.io/documentation/guide/notation/
 *
 * <p>Results:
 *
 * <pre>
 * Benchmark                              Mode  Cnt    Score     Error  Units
 * DiceNotationParserBenchmark.dotParse  thrpt    5  771.455 ± 107.845  ops/s
 * DiceNotationParserBenchmark.regex     thrpt    5  322.524 ±  11.126  ops/s
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DiceNotationBenchmark {

  // Parsers
  private static final Parser<Integer> NUM = digits().map(Integer::parseInt);
  private static final Parser<ComparisonPoint> COMPARISON_POINT = sequence(
      Stream.of(">=", "<=", "!=", "<>", ">", "<", "=").map(Parser::string).collect(Parser.or()),
      NUM, ComparisonPoint::new);
  private static final Parser<Modifier> EXPLODING = Stream.of("!!", "!p", "!")
      .map(type -> string(type).map(Exploding::ofType).optionallyFollowedBy(COMPARISON_POINT, Exploding::withComparison))
      .collect(Parser.or());
  private static final Parser<Modifier> REROLL = Stream.of("ro", "r")
      .map(type -> string(type).map(Reroll::ofType).optionallyFollowedBy(COMPARISON_POINT, Reroll::withComparison))
      .collect(Parser.or());
  private static final Parser<Modifier> KEEP = Stream.of("kh", "kl", "k")
      .map(type -> string(type).map(Keep::ofType).optionallyFollowedBy(NUM, Keep::withCount))
      .collect(Parser.or());
  private static final Parser<Modifier> DROP = Stream.of("dh", "dl", "d")
      .map(type -> string(type).map(Drop::ofType).optionallyFollowedBy(NUM, Drop::withCount))
      .collect(Parser.or());
  private static final Parser<Modifier> MODIFIER = anyOf(EXPLODING, REROLL, KEEP, DROP);
  private static final Parser<DiceRoll> DICE_WITH_NUM = sequence(NUM.followedBy("d"), NUM, DiceRoll::of);
  private static final Parser<DiceRoll> DICE_WITHOUT_NUM = string("d").then(NUM).map(DiceRoll::of);
  static final Parser<DiceRoll> PARSER = anyOf(DICE_WITH_NUM, DICE_WITHOUT_NUM)
      .optionallyFollowedBy(MODIFIER.atLeastOnce(), DiceRoll::withModifiers)
      .optionallyFollowedBy(COMPARISON_POINT, DiceRoll::withTargetSuccess)
      .followedBy(Parser.zeroOrMore(charsIn("[ \r\n\t]")));

  // Tests
  @Test
  public void testSimpleDice() {
    assertRoll("2d6", DiceRoll.of(2, 6));
  }

  @Test
  public void testDiceWithTarget() {
    assertRoll("5d10>7", DiceRoll.of(5, 10).withTargetSuccess(new ComparisonPoint(">", 7)));
  }

  @Test
  public void testDiceWithExploding() {
    assertRoll("4d6!", DiceRoll.of(4, 6).withModifiers(List.of(Exploding.ofType("!"))));
  }

  @Test
  public void testDiceWithComplexExploding() {
    assertRoll("4d6!>=5", DiceRoll.of(4, 6).withModifiers(List.of(Exploding.ofType("!").withComparison(new ComparisonPoint(">=", 5)))));
  }

  @Test
  public void testDiceWithReroll() {
    assertRoll("2d20ro<3", DiceRoll.of(2, 20).withModifiers(List.of(Reroll.ofType("ro").withComparison(new ComparisonPoint("<", 3)))));
  }

  @Test
  public void testDiceWithKeepDrop() {
    assertRoll("4d6kh3", DiceRoll.of(4, 6).withModifiers(List.of(Keep.ofType("kh").withCount(3))));
  }

  @Test
  public void testMultipleModifiers() {
    assertRoll("4d6!ro<3kh3", DiceRoll.of(4, 6).withModifiers(List.of(
        Exploding.ofType("!"),
        Reroll.ofType("ro").withComparison(new ComparisonPoint("<", 3)),
        Keep.ofType("kh").withCount(3))));
  }

  @Test
  public void testParseToStream() {
    assertThat(PARSER.parseToStream("2d6"))
        .containsExactly(DiceRoll.of(2, 6));
  }

  // Benchmark
  private static final String INPUT = "4d6!ro<3kh3>=10";
  private static final int SCALE = 1000;
  private static final String LARGE_INPUT = String.join(" ", java.util.Collections.nCopies(SCALE, INPUT));

  @Benchmark
  public long dotParse() {
    return PARSER.parseToStream(LARGE_INPUT).count();
  }

  private static final Pattern REGEX = Pattern.compile(
      "(?:(\\d+)d|d)(\\d+)((?:(?:!!|!p|!|ro|r)(?:(?:>=|<=|!=|<>|>|<|=)\\d+)?|(?:kh|kl|k|dh|dl|d)\\d*)*)((?:>=|<=|!=|<>|>|<|=)\\d+)?");

  @Benchmark
  public long regex() {
    try (Scanner scanner = new Scanner(LARGE_INPUT)) {
      return scanner.findAll(REGEX)
          .map(DiceNotationBenchmark::fromRegexMatch)
          .count();
    }
  }

  @Test public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(SCALE);
  }

  @Test public void testRegexBenchmark() {
    assertThat(regex()).isEqualTo(SCALE);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  // Domain Records
  record ComparisonPoint(String operator, int value) {}

  interface Modifier {}

  record Exploding(String type, Optional<ComparisonPoint> cp) implements Modifier {
    static Exploding ofType(String type) { return new Exploding(type, Optional.empty()); }
    Exploding withComparison(ComparisonPoint cp) { return new Exploding(this.type, Optional.of(cp)); }
  }

  record Reroll(String type, Optional<ComparisonPoint> cp) implements Modifier {
    static Reroll ofType(String type) { return new Reroll(type, Optional.empty()); }
    Reroll withComparison(ComparisonPoint cp) { return new Reroll(this.type, Optional.of(cp)); }
  }

  record Keep(String type, Optional<Integer> count) implements Modifier {
    static Keep ofType(String type) { return new Keep(type, Optional.empty()); }
    Keep withCount(int n) { return new Keep(this.type, Optional.of(n)); }
  }

  record Drop(String type, Optional<Integer> count) implements Modifier {
    static Drop ofType(String type) { return new Drop(type, Optional.empty()); }
    Drop withCount(int n) { return new Drop(this.type, Optional.of(n)); }
  }

  record DiceRoll(int number, int sides, List<Modifier> modifiers, Optional<ComparisonPoint> targetSuccess) {
    static DiceRoll of(int number, int sides) { return new DiceRoll(number, sides, List.of(), Optional.empty()); }
    static DiceRoll of(int sides) { return new DiceRoll(1, sides, List.of(), Optional.empty()); }

    DiceRoll withModifiers(List<Modifier> mods) {
      return new DiceRoll(this.number, this.sides, mods, this.targetSuccess);
    }

    DiceRoll withTargetSuccess(ComparisonPoint cp) {
      return new DiceRoll(this.number, this.sides, this.modifiers, Optional.of(cp));
    }
  }

  // Private Helpers
  private void assertRoll(String input, DiceRoll expected) {
    assertThat(PARSER.parse(input)).isEqualTo(expected);

    try (Scanner scanner = new Scanner(input)) {
      List<DiceRoll> regexMatched = scanner.findAll(REGEX)
          .map(DiceNotationBenchmark::fromRegexMatch)
          .toList();
      assertThat(regexMatched).containsExactly(expected);
    }
  }

  private static final Pattern MOD_PATTERN = Pattern.compile("(!!|!p|!|ro|r)(?:(>=|<=|!=|<>|>|<|=)(\\d+))?|(kh|kl|k|dh|dl|d)(\\d*)");
  private static final Pattern CP_PATTERN = Pattern.compile("(>=|<=|!=|<>|>|<|=)(\\d+)");

  private static DiceRoll fromRegexMatch(java.util.regex.MatchResult mr) {
    int num = mr.group(1) != null ? Integer.parseInt(mr.group(1)) : 1;
    int sides = Integer.parseInt(mr.group(2));
    DiceRoll roll = DiceRoll.of(num, sides);

    String modsStr = mr.group(3);
    if (modsStr != null && !modsStr.isEmpty()) {
      List<Modifier> modifiers = new ArrayList<>();
      Matcher matcher = MOD_PATTERN.matcher(modsStr);
      while (matcher.find()) {
        String type = matcher.group(1);
        if (type != null) {
          if (type.startsWith("!")) {
            Exploding exploding = Exploding.ofType(type);
            if (matcher.group(2) != null) {
              exploding = exploding.withComparison(new ComparisonPoint(matcher.group(2), Integer.parseInt(matcher.group(3))));
            }
            modifiers.add(exploding);
          } else {
            Reroll reroll = Reroll.ofType(type);
            if (matcher.group(2) != null) {
              reroll = reroll.withComparison(new ComparisonPoint(matcher.group(2), Integer.parseInt(matcher.group(3))));
            }
            modifiers.add(reroll);
          }
        } else {
          type = matcher.group(4);
          if (type.startsWith("k")) {
            Keep keep = Keep.ofType(type);
            if (matcher.group(5) != null && !matcher.group(5).isEmpty()) {
              keep = keep.withCount(Integer.parseInt(matcher.group(5)));
            }
            modifiers.add(keep);
          } else {
            Drop drop = Drop.ofType(type);
            if (matcher.group(5) != null && !matcher.group(5).isEmpty()) {
              drop = drop.withCount(Integer.parseInt(matcher.group(5)));
            }
            modifiers.add(drop);
          }
        }
      }
      roll = roll.withModifiers(modifiers);
    }

    String targetStr = mr.group(4);
    if (targetStr != null) {
      Matcher matcher = CP_PATTERN.matcher(targetStr);
      if (matcher.find()) {
        roll = roll.withTargetSuccess(new ComparisonPoint(matcher.group(1), Integer.parseInt(matcher.group(2))));
      }
    }

    return roll;
  }
}

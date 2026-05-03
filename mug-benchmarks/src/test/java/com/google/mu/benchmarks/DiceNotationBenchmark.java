package com.google.mu.benchmarks;

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
 *
 * <p>See <a href="https://dice-roller.github.io/documentation/guide/notation/">spec</a>.
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
  private static final Parser<Bound> BOUND = sequence(anyOf(Comparison.values()), NUM, Bound::new);
  private static final Parser<Modifier> MODIFIER = anyOf(
      anyOf(Exploding.Type.values()).map(Exploding::of).optionallyFollowedBy(BOUND, Exploding::withBound),
      anyOf(Reroll.Type.values()).map(Reroll::of).optionallyFollowedBy(BOUND, Reroll::withBound),
      anyOf(Keep.Type.values()).map(Keep::of).optionallyFollowedBy(NUM, Keep::withCount),
      anyOf(Drop.Type.values()).map(Drop::of).optionallyFollowedBy(NUM, Drop::withCount));
  static final Parser<DiceRoll> PARSER = anyOf(
          sequence(NUM.followedBy("d"), NUM, DiceRoll::of),
          string("d").then(NUM).map(DiceRoll::of))
      .optionallyFollowedBy(MODIFIER.atLeastOnce(), DiceRoll::withModifiers)
      .optionallyFollowedBy(BOUND, DiceRoll::withTargetSuccess);

  // Tests
  @Test
  public void testSimpleDice() {
    assertRoll("2d6", DiceRoll.of(2, 6));
  }

  @Test
  public void testDiceWithTarget() {
    assertRoll("5d10>7", DiceRoll.of(5, 10).withTargetSuccess(new Bound(Comparison.GT, 7)));
  }

  @Test
  public void testDiceWithExploding() {
    assertRoll("4d6!", DiceRoll.of(4, 6).withModifiers(List.of(Exploding.of(Exploding.Type.STANDARD))));
  }

  @Test
  public void testDiceWithComplexExploding() {
    assertRoll("4d6!>=5", DiceRoll.of(4, 6).withModifiers(List.of(Exploding.of(Exploding.Type.STANDARD).withBound(new Bound(Comparison.GE, 5)))));
  }

  @Test
  public void testDiceWithReroll() {
    assertRoll("2d20ro<3", DiceRoll.of(2, 20).withModifiers(List.of(Reroll.of(Reroll.Type.ONCE).withBound(new Bound(Comparison.LT, 3)))));
  }

  @Test
  public void testDiceWithKeepDrop() {
    assertRoll("4d6kh3", DiceRoll.of(4, 6).withModifiers(List.of(Keep.of(Keep.Type.HIGHEST).withCount(3))));
  }

  @Test
  public void testMultipleModifiers() {
    assertRoll("4d6!ro<3kh3", DiceRoll.of(4, 6).withModifiers(List.of(
        Exploding.of(Exploding.Type.STANDARD),
        Reroll.of(Reroll.Type.ONCE).withBound(new Bound(Comparison.LT, 3)),
        Keep.of(Keep.Type.HIGHEST).withCount(3))));
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
    return PARSER.skipping(Character::isWhitespace).parseToStream(LARGE_INPUT).count();
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
  enum Comparison {
    GE(">="), LE("<="), NE("!="), NE2("<>"), GT(">"), LT("<"), EQ("=");

    private final String symbol;

    Comparison(String symbol) {
      this.symbol = symbol;
    }

    @Override
    public String toString() {
      return symbol;
    }

    static Comparison fromSymbol(String symbol) {
      return switch (symbol) {
        case ">=" -> GE;
        case "<=" -> LE;
        case "!=" -> NE;
        case "<>" -> NE2;
        case ">" -> GT;
        case "<" -> LT;
        case "=" -> EQ;
        default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
      };
    }
  }

  record Bound(Comparison operator, int value) {}

  interface Modifier {}

  record Exploding(Exploding.Type type, Optional<Bound> cp) implements Modifier {
    enum Type {
      DOUBLE("!!"), PENETRATING("!p"), STANDARD("!");

      private final String symbol;

      Type(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }

      static Type fromSymbol(String symbol) {
        return switch (symbol) {
          case "!!" -> DOUBLE;
          case "!p" -> PENETRATING;
          case "!" -> STANDARD;
          default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
        };
      }
    }
    static Exploding of(Exploding.Type type) { return new Exploding(type, Optional.empty()); }
    Exploding withBound(Bound cp) { return new Exploding(this.type, Optional.of(cp)); }
  }

  record Reroll(Reroll.Type type, Optional<Bound> cp) implements Modifier {
    enum Type {
      ONCE("ro"), FOREVER("r");

      private final String symbol;

      Type(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }

      static Type fromSymbol(String symbol) {
        return switch (symbol) {
          case "ro" -> ONCE;
          case "r" -> FOREVER;
          default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
        };
      }
    }
    static Reroll of(Reroll.Type type) { return new Reroll(type, Optional.empty()); }
    Reroll withBound(Bound cp) { return new Reroll(this.type, Optional.of(cp)); }
  }

  record Keep(Keep.Type type, Optional<Integer> count) implements Modifier {
    enum Type {
      HIGHEST("kh"), LOWEST("kl"), ALL("k");

      private final String symbol;

      Type(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }

      static Type fromSymbol(String symbol) {
        return switch (symbol) {
          case "kh" -> HIGHEST;
          case "kl" -> LOWEST;
          case "k" -> ALL;
          default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
        };
      }
    }
    static Keep of(Keep.Type type) { return new Keep(type, Optional.empty()); }
    Keep withCount(int n) { return new Keep(this.type, Optional.of(n)); }
  }

  record Drop(Drop.Type type, Optional<Integer> count) implements Modifier {
    enum Type {
      HIGHEST("dh"), LOWEST("dl"), ALL("d");

      private final String symbol;

      Type(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }

      static Type fromSymbol(String symbol) {
        return switch (symbol) {
          case "dh" -> HIGHEST;
          case "dl" -> LOWEST;
          case "d" -> ALL;
          default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
        };
      }
    }
    static Drop of(Drop.Type type) { return new Drop(type, Optional.empty()); }
    Drop withCount(int n) { return new Drop(this.type, Optional.of(n)); }
  }

  record DiceRoll(int number, int sides, List<Modifier> modifiers, Optional<Bound> targetSuccess) {
    static DiceRoll of(int number, int sides) { return new DiceRoll(number, sides, List.of(), Optional.empty()); }
    static DiceRoll of(int sides) { return new DiceRoll(1, sides, List.of(), Optional.empty()); }

    DiceRoll withModifiers(List<Modifier> mods) {
      return new DiceRoll(this.number, this.sides, mods, this.targetSuccess);
    }

    DiceRoll withTargetSuccess(Bound cp) {
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
            Exploding exploding = Exploding.of(Exploding.Type.fromSymbol(type));
            if (matcher.group(2) != null) {
              exploding = exploding.withBound(new Bound(Comparison.fromSymbol(matcher.group(2)), Integer.parseInt(matcher.group(3))));
            }
            modifiers.add(exploding);
          } else {
            Reroll reroll = Reroll.of(Reroll.Type.fromSymbol(type));
            if (matcher.group(2) != null) {
              reroll = reroll.withBound(new Bound(Comparison.fromSymbol(matcher.group(2)), Integer.parseInt(matcher.group(3))));
            }
            modifiers.add(reroll);
          }
        } else {
          type = matcher.group(4);
          if (type.startsWith("k")) {
            Keep keep = Keep.of(Keep.Type.fromSymbol(type));
            if (matcher.group(5) != null && !matcher.group(5).isEmpty()) {
              keep = keep.withCount(Integer.parseInt(matcher.group(5)));
            }
            modifiers.add(keep);
          } else {
            Drop drop = Drop.of(Drop.Type.fromSymbol(type));
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
        roll = roll.withTargetSuccess(new Bound(Comparison.fromSymbol(matcher.group(1)), Integer.parseInt(matcher.group(2))));
      }
    }

    return roll;
  }
}

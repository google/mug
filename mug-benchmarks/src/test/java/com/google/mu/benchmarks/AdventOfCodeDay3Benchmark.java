package com.google.mu.benchmarks;

import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.first;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static java.lang.Integer.parseInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.labs.parse.Parser;
import com.google.mu.util.Substring;

/**
 * <a href="https://adventofcode.com/2024/day/3">Advent of Code 2024 Day 3</a>. Part 1 sums the
 * products of all {@code mul(X,Y)} instructions in corrupted memory. Part 2 excludes the {@code
 * mul} instructions between a {@code don't()} and the next {@code do()}.
 *
 * <p>{@link #regex} is the JDK equivalent of the regex solution in <a
 * href="https://entropicthoughts.com/parser-combinators-beat-regexes">Parser Combinators Beat
 * Regexes</a>. The article has no regex solution for part 2: {@link #part2Regex} is the common
 * single pass with an enabled flag, and {@link #part2RegexSplit} splits out the disabled regions as
 * {@link #part2DotParse} does.
 *
 * <p>Advent of Code asks that puzzle inputs not be redistributed, so the input is generated with
 * the profile of real inputs: about 19 KB in 6 lines, with 716 valid {@code mul(X,Y)}, 44 near
 * misses such as {@code mul(12,34]}, 37 {@code do()} and 34 {@code don't()}, each preceded by 0 to
 * 10 filler tokens (punctuation, or decoy calls such as {@code what()}).
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AdventOfCodeDay3Benchmark {
  private static final String[] DECOYS = {
    "what", "how", "where", "from", "who", "when", "why", "select"
  };
  private static final String PUNCTUATION = "+:/-^(!&><,~%#]'}$*)?; @[{";
  // excludes "(),", which could complete or extend an instruction, as in "mul(12,34" + ")"
  private static final String CORRUPTIONS = PUNCTUATION.replaceAll("[(),]", "");
  private static final int LINES = 6;
  private static final String INPUT = generateInput(new Random(3));
  private static final int EXPECTED_SUM = 176_529_906;
  private static final int EXPECTED_PART2_SUM = 96_959_095;

  private static final Pattern MUL_REGEX = Pattern.compile("mul\\((\\d+),(\\d+)\\)");
  private static final Parser<Integer> MUL = first("mul(").then(
      sequence(
              digits().followedBy(","),
              digits().followedBy(")"),
              (a, b) -> parseInt(a) * parseInt(b))
          .orElse(null));

  private static final Pattern INSTRUCTION_REGEX =
      Pattern.compile("mul\\((\\d+),(\\d+)\\)|(do\\(\\))|(don't\\(\\))");
  private static final Pattern DISABLED_REGEX =
      Pattern.compile("(?s)don't\\(\\).*?(?:do\\(\\)|\\z)");
  private static final Substring.RepeatingPattern DISABLED = Substring.between(
          Substring.first("don't()"), INCLUSIVE, Substring.first("do()").or(END), INCLUSIVE)
      .repeatedly();

  @Benchmark
  public int regex() {
    int sum = 0;
    Matcher matcher = MUL_REGEX.matcher(INPUT);
    while (matcher.find()) {
      sum += parseInt(matcher.group(1)) * parseInt(matcher.group(2));
    }
    return sum;
  }

  @Benchmark
  public int dotParse() {
    return MUL.probe(INPUT).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
  }

  @Benchmark
  public int part2Regex() {
    int sum = 0;
    boolean enabled = true;
    Matcher matcher = INSTRUCTION_REGEX.matcher(INPUT);
    while (matcher.find()) {
      if (matcher.start(3) >= 0) {
        enabled = true;
      } else if (matcher.start(4) >= 0) {
        enabled = false;
      } else if (enabled) {
        sum += parseInt(matcher.group(1)) * parseInt(matcher.group(2));
      }
    }
    return sum;
  }

  @Benchmark
  public int part2RegexSplit() {
    return DISABLED_REGEX.splitAsStream(INPUT).mapToInt(AdventOfCodeDay3Benchmark::regexSum).sum();
  }

  @Benchmark
  public int part2DotParse() {
    return DISABLED.split(INPUT).mapToInt(segment -> dotParseSum(segment.toString())).sum();
  }

  @Test public void testRegexBenchmark() {
    assertThat(regex()).isEqualTo(EXPECTED_SUM);
  }

  @Test public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(EXPECTED_SUM);
  }

  @Test public void testPart2RegexBenchmark() {
    assertThat(part2Regex()).isEqualTo(EXPECTED_PART2_SUM);
  }

  @Test public void testPart2RegexSplitBenchmark() {
    assertThat(part2RegexSplit()).isEqualTo(EXPECTED_PART2_SUM);
  }

  @Test public void testPart2DotParseBenchmark() {
    assertThat(part2DotParse()).isEqualTo(EXPECTED_PART2_SUM);
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
  }

  private static int regexSum(String segment) {
    int sum = 0;
    Matcher matcher = MUL_REGEX.matcher(segment);
    while (matcher.find()) {
      sum += parseInt(matcher.group(1)) * parseInt(matcher.group(2));
    }
    return sum;
  }

  private static int dotParseSum(String segment) {
    return MUL.probe(segment).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
  }

  private static String generateInput(Random random) {
    List<String> instructions = new ArrayList<>();
    add(instructions, 716, () -> "mul(" + operand(random) + "," + operand(random) + ")");
    add(instructions, 37, () -> "do()");
    add(instructions, 34, () -> "don't()");
    add(instructions, 23, () -> "mul(" + operand(random) + corruption(random));
    add(
        instructions, 21,
        () -> "mul(" + operand(random) + "," + operand(random) + corruption(random));
    add(instructions, 21, () -> "mul" + corruption(random));
    Collections.shuffle(instructions, random);
    int perLine = instructions.size() / LINES;
    StringBuilder input = new StringBuilder();
    for (int i = 0; i < instructions.size(); i++) {
      if (i > 0 && i % perLine == 0) {
        input.append('\n');
      }
      for (int fillers = random.nextInt(11); fillers > 0; fillers--) {
        input.append(random.nextInt(4) > 0 ? oneCharOf(PUNCTUATION, random) : decoyCall(random));
      }
      input.append(instructions.get(i));
    }
    return input.toString();
  }

  private static void add(List<String> instructions, int count, Supplier<String> instruction) {
    for (int i = 0; i < count; i++) {
      instructions.add(instruction.get());
    }
  }

  private static String decoyCall(Random random) {
    String name = DECOYS[random.nextInt(DECOYS.length)];
    return random.nextInt(10) > 0
        ? name + "()"
        : name + "(" + operand(random) + "," + operand(random) + ")";
  }

  private static int operand(Random random) {
    return 1 + random.nextInt(999);
  }

  private static String corruption(Random random) {
    return oneCharOf(CORRUPTIONS, random);
  }

  private static String oneCharOf(String chars, Random random) {
    return String.valueOf(chars.charAt(random.nextInt(chars.length())));
  }
}

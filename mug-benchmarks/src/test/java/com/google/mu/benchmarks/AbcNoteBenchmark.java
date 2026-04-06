package com.google.mu.benchmarks;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.MatchResult;
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
 * Benchmark for ABC Music Notation Note Parser.
 *
 * <p>See <a href="http://abcnotation.com/wiki/abc:standard:v2.1#notes">spec</a>.
 *
 * <p>Results:
 *
 * <pre>
 * Benchmark                         Mode  Cnt       Score       Error  Units
 * AbcNoteParserBenchmark.dotParse  thrpt    5  237044.233 ±  5071.176  ops/s
 * AbcNoteParserBenchmark.regex     thrpt    5  238520.581 ± 12170.420  ops/s
 * </pre>
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class AbcNoteBenchmark {
  private static final String INPUT = "^C,,2 _c'1/2 =D3/2 E F G A B";

  // Parsers
  private static final Parser<Integer> NUM = digits().map(Integer::parseInt);
  private static final Parser<Accidental> ACCIDENTAL = Parser.byStringsFrom(Accidental.values());

  private static final Parser<Integer> DURATION_DENOMINATOR = string("/").then(NUM.orElse(2));
  private static final Parser<NoteDuration> DURATION =
      anyOf(
          NUM.map(NoteDuration::of)
              .optionallyFollowedBy(DURATION_DENOMINATOR, NoteDuration::withDenominator),
          DURATION_DENOMINATOR.map(d -> new NoteDuration(1, d)));

  private static final Parser<AbcNote> NOTE =
      anyOf(
          one(charsIn("[ABCDEFG]")).map(AbcNote::middle).withPostfixes(",", AbcNote::down),
          one(charsIn("[abcdefg]")).map(AbcNote::high).withPostfixes("'", AbcNote::up));

  private static final Parser<AbcNote> PARSER =
      anyOf(sequence(ACCIDENTAL, NOTE, (acc, note) -> note.withAccidental(acc)), NOTE)
          .optionallyFollowedBy(DURATION, AbcNote::withDuration)
          .followedBy(zeroOrMore(charsIn("[ \r\n\t]")));

  // Tests
  @Test
  public void testSimpleNote() {
    assertThat(PARSER.parse("C")).isEqualTo(AbcNote.of('C', 0));
    assertThat(PARSER.parse("c")).isEqualTo(AbcNote.of('C', 1));
  }

  @Test
  public void testNoteWithAccidental() {
    assertThat(PARSER.parse("^C")).isEqualTo(AbcNote.of('C', 0).withAccidental(Accidental.SHARP));
    assertThat(PARSER.parse("_c")).isEqualTo(AbcNote.of('C', 1).withAccidental(Accidental.FLAT));
    assertThat(PARSER.parse("=C")).isEqualTo(AbcNote.of('C', 0).withAccidental(Accidental.NATURAL));
  }

  @Test
  public void testNoteWithOctaveModifier() {
    assertThat(PARSER.parse("C,")).isEqualTo(AbcNote.of('C', -1));
    assertThat(PARSER.parse("C,,")).isEqualTo(AbcNote.of('C', -2));
    assertThat(PARSER.parse("c'")).isEqualTo(AbcNote.of('C', 2));
    assertThat(PARSER.parse("c''")).isEqualTo(AbcNote.of('C', 3));
  }

  @Test
  public void testNoteWithDuration() {
    assertThat(PARSER.parse("C2")).isEqualTo(AbcNote.of('C', 0).withDuration(NoteDuration.of(2)));
    assertThat(PARSER.parse("C/2")).isEqualTo(AbcNote.of('C', 0).withDuration(new NoteDuration(1, 2)));
    assertThat(PARSER.parse("C/")).isEqualTo(AbcNote.of('C', 0).withDuration(new NoteDuration(1, 2)));
    assertThat(PARSER.parse("C3/2")).isEqualTo(AbcNote.of('C', 0).withDuration(new NoteDuration(3, 2)));
  }

  @Test
  public void testComplexNote() {
    assertThat(PARSER.parse("^C,,2")).isEqualTo(
        AbcNote.of('C', -2)
            .withAccidental(Accidental.SHARP)
            .withDuration(NoteDuration.of(2)));
  }

  @Test
  public void testRegexEquivalent() {
    String input = "^C,,2";
    Matcher matcher = NOTE_PATTERN.matcher(input);
    assertThat(matcher.matches()).isTrue();
    assertThat(fromRegexMatch(matcher)).isEqualTo(PARSER.parse(input));
  }

  @Test
  public void testDotParseBenchmark() {
    assertThat(dotParse()).containsExactly(
        AbcNote.of('C', -2).withAccidental(Accidental.SHARP).withDuration(NoteDuration.of(2)),
        AbcNote.of('C', 2).withAccidental(Accidental.FLAT).withDuration(new NoteDuration(1, 2)),
        AbcNote.of('D', 0).withAccidental(Accidental.NATURAL).withDuration(new NoteDuration(3, 2)),
        AbcNote.of('E', 0),
        AbcNote.of('F', 0),
        AbcNote.of('G', 0),
        AbcNote.of('A', 0),
        AbcNote.of('B', 0));
  }

  @Test
  public void testRegexBenchmark() {
    assertThat(regex()).containsExactly(
        AbcNote.of('C', -2).withAccidental(Accidental.SHARP).withDuration(NoteDuration.of(2)),
        AbcNote.of('C', 2).withAccidental(Accidental.FLAT).withDuration(new NoteDuration(1, 2)),
        AbcNote.of('D', 0).withAccidental(Accidental.NATURAL).withDuration(new NoteDuration(3, 2)),
        AbcNote.of('E', 0),
        AbcNote.of('F', 0),
        AbcNote.of('G', 0),
        AbcNote.of('A', 0),
        AbcNote.of('B', 0));
  }

  // Benchmarks
  @Benchmark
  public List<AbcNote> dotParse() {
    return PARSER.parseToStream(INPUT).toList();
  }

  private static final Pattern NOTE_PATTERN = Pattern.compile(
      "(\\^\\^|\\^|__|_|=)?(?:([ABCDEFG])(,*)|([abcdefg])('*))(\\d*(?:/\\d*)?)?");

  @Benchmark
  public List<AbcNote> regex() {
    return NOTE_PATTERN.matcher(INPUT)
        .results()
        .map(AbcNoteBenchmark::fromRegexMatch)
        .toList();
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  // Domain Records
  public enum Accidental {
    DOUBLE_SHARP("^^"),
    SHARP("^"),
    DOUBLE_FLAT("__"),
    FLAT("_"),
    NATURAL("=");

    private final String symbol;

    Accidental(String symbol) {
      this.symbol = symbol;
    }

    @Override public String toString() {
      return symbol;
    }
  }

  public record NoteDuration(int numerator, int denominator) {
    static NoteDuration of(int num) { return new NoteDuration(num, 1); }
    NoteDuration withDenominator(int den) { return new NoteDuration(this.numerator, den); }
  }

  public record AbcNote(Accidental accidental, char pitch, int octave, NoteDuration duration) {

    public static AbcNote middle(char pitch) {
      return of(pitch, 0);
    }

    public static AbcNote high(char pitch) {
      return of(Character.toUpperCase(pitch), 1);
    }

    private static AbcNote of(char pitch, int octave) {
      return new AbcNote(null, pitch, octave, NoteDuration.of(1));
    }

    AbcNote withAccidental(Accidental accidental) {
      return new AbcNote(accidental, this.pitch, this.octave, this.duration);
    }

    AbcNote up() {
      return new AbcNote(this.accidental, this.pitch, this.octave + 1, this.duration);
    }

    AbcNote down() {
      return new AbcNote(this.accidental, this.pitch, this.octave - 1, this.duration);
    }

    AbcNote withDuration(NoteDuration duration) {
      return new AbcNote(this.accidental, this.pitch, this.octave, duration);
    }
  }

  // Private helpers
  private static AbcNote fromRegexMatch(MatchResult matcher) {
    String accStr = matcher.group(1);
    String upperPitch = matcher.group(2);
    String commas = matcher.group(3);
    String lowerPitch = matcher.group(4);
    String apostrophes = matcher.group(5);
    String durationStr = matcher.group(6);

    Accidental accidental = null;
    if (accStr != null) {
      accidental = switch (accStr) {
        case "^^" -> Accidental.DOUBLE_SHARP;
        case "^" -> Accidental.SHARP;
        case "__" -> Accidental.DOUBLE_FLAT;
        case "_" -> Accidental.FLAT;
        case "=" -> Accidental.NATURAL;
        default -> null;
      };
    }

    char pitch;
    int octave;
    if (upperPitch != null) {
      pitch = upperPitch.charAt(0);
      octave = 0;
      if (commas != null) {
        octave -= commas.length();
      }
    } else {
      pitch = Character.toUpperCase(lowerPitch.charAt(0));
      octave = 1;
      if (apostrophes != null) {
        octave += apostrophes.length();
      }
    }

    NoteDuration duration = NoteDuration.of(1);
    if (durationStr != null && !durationStr.isEmpty()) {
      if (durationStr.contains("/")) {
        String[] parts = durationStr.split("/", -1);
        int num = parts[0].isEmpty() ? 1 : Integer.parseInt(parts[0]);
        int den = parts[1].isEmpty() ? 2 : Integer.parseInt(parts[1]);
        duration = new NoteDuration(num, den);
      } else {
        duration = NoteDuration.of(Integer.parseInt(durationStr));
      }
    }

    return new AbcNote(accidental, pitch, octave, duration);
  }
}

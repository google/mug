package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.mu.util.stream.Joiner;
import java.time.Duration;

/**
 * Some useful, but not-so-primitive parsers.
 *
 * @since 10.8
 */
public final class Parsers {

  /**
   * Parser for unsigned decimal point numbers, e.g., {@code "1.23"}, {@code "0.0"}, {@code "1"},
   * {@code "0"}.
   *
   * <p>Note that it doesn't match positive or negative signs, and leading zero is only allowed when
   * the number is a fraction smaller than 1.
   */
  public static final Parser<String> UNSIGNED_DECIMAL =
      literally(digits(), one('.').followedBy(digits()).optional())
          .source()
          .suchThat(
              s -> !s.startsWith("0") || s.startsWith("0.") || s.equals("0"),
              "decimal point number");

  /**
   * Parser for duration strings in the shorthand systems format.
   *
   * <p>Matches one or more unit specs consisting of a positive integer followed by a unit suffix.
   * For example:
   *
   * <ul>
   *   <li>{@code "30s"} {@code ->} 30 seconds
   *   <li>{@code "2h30m"} {@code ->} 2 hours and 30 minutes
   *   <li>{@code "1w2d"} {@code ->} 9 days (1 week + 2 days)
   * </ul>
   *
   * <p>Supported units:
   *
   * <ul>
   *   <li>{@code w} (weeks) - treated as exactly 7 days
   *   <li>{@code d} (days) - treated as exactly 24 hours
   *   <li>{@code h} (hours)
   *   <li>{@code m} (minutes)
   *   <li>{@code s} (seconds)
   *   <li>{@code ms} (milliseconds)
   *   <li>{@code us} (microseconds)
   *   <li>{@code ns} (nanoseconds)
   * </ul>
   *
   * <p>Note: Decimal numbers (e.g., {@code "1.5s"}) and negative values (e.g., {@code "-2s"}) are
   * not supported.
   */
  public static final Parser<Duration> DURATION = sequence(
          digits(),
          anyOf(DurationUnit.values())
              .notImmediatelyFollowedBy(charsIn("[a-zA-Z]"), "duration unit char"),
          (num, unit) -> {
            try {
              return unit.of(Long.parseLong(num));
            } catch (NumberFormatException e) {
              throw Parser.fail(e.getMessage());
            } catch (ArithmeticException e) {
              throw Parser.fail("duration out of range: " + num + unit);
            }
          })
      .atLeastOnce()
      .map(durations -> {
        try {
          return durations.stream().reduce(Duration::plus).get();
        } catch (ArithmeticException e) {
          throw Parser.fail("duration out of range");
        }
      });

  private enum DurationUnit {
    WEEK("w") {
      @Override Duration of(long n) {
        return Duration.ofDays(n * 7);
      }
    },
    DAY("d") {
      @Override Duration of(long n) {
        return Duration.ofDays(n);
      }
    },
    HOUR("h") {
      @Override Duration of(long n) {
        return Duration.ofHours(n);
      }
    },
    MINUTE("m") {
      @Override Duration of(long n) {
        return Duration.ofMinutes(n);
      }
    },
    SECOND("s") {
      @Override Duration of(long n) {
        return Duration.ofSeconds(n);
      }
    },
    MILLISECOND("ms") {
      @Override Duration of(long n) {
        return Duration.ofMillis(n);
      }
    },
    MICROSECOND("us") {
      @Override Duration of(long n) {
        return Duration.ofNanos(NANOSECONDS.convert(n, MICROSECONDS));
      }
    },
    NANOSECOND("ns") {
      @Override Duration of(long n) {
        return Duration.ofNanos(n);
      }
    };

    private final String str;

    DurationUnit(String str) {
      this.str = str;
    }

    abstract Duration of(long n);

    @Override public String toString() {
      return str;
    }
  }

  /**
   * Parses a 4-digit hex BMP code unit. The following example parses a surrogate pair of two UTF-16
   * code units and will return the emoji {@code 😀}:
   *
   * <pre>{@code
   * BMP_CODE_UNIT
   *     .map(Character::toString)
   *     .zeroOrMore(Collectors.joining())
   *     .parse("D83DDE00");
   * }</pre>
   *
   * <p>Note that starting from v9.6, it's recommended to use {@link Joiner} ({@code
   * Joiner.on(delimiter)}) in place of JDK {@code Collectors.joining(delimiter)} because {@code
   * Joiner} optimizes for single-string input, which is a common case in the context of parsing.
   *
   * <p>You can also compose it with {@link Parser#quotedByWithEscapes}:
   *
   * <pre>{@code
   * Parser.quotedByWithEscapes('"', '"', Parser.string("u").then(BMP_CODE_UNIT).map(Character::toString));
   * }</pre>
   */
  public static final Parser<Integer> BMP_CODE_UNIT =
      Parser.hexDigits(4).elidableMap(digits -> Integer.parseInt(digits, 16));

  private Parsers() {}
}

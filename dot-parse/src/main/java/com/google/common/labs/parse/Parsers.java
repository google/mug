package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.mu.util.stream.BiStream.adjacentPairsFrom;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.mu.util.stream.Joiner;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Some common composite parsers in addition to the core parsers provided by {@link Parser}.
 *
 * @since 10.8
 */
public final class Parsers {
  static final Parser<String> DIGITS = consecutive(CharacterSet.DECIMAL, "digits");
  static final Parser<String> WORD = consecutive(charsIn("[a-zA-Z0-9_]"), "word");

  /**
   * Parses unsigned decimal integer numbers, e.g., {@code 15}, {@code 0}.
   *
   * <p>To support signs, you can compose it like:
   *
   * <pre>{@code
   * Parser<Long> signed = sequence(
   *     one('-').thenReturn(-1).orElse(1), UNSIGNED_INTEGER.map(Long::parseLong),
   *     (sign, num) -> sign * num);
   * }</pre>
   */
  public static final Parser<String> UNSIGNED_INTEGER =
      new Scanner("integer") {
        @Override int scan(CharInput input, final int from) {
          char c = input.charAt(from);
          int index = from + 1;
          if (c >= '1' && c <= '9') {
            while (input.isInRange(index) && isDigit(input.charAt(index))) index++;
            return index;
          }
          if (c == '0') {
            return input.isInRange(index) && isDigit(input.charAt(index)) ? from : index;
          }
          return from;
        }

        @Override Set<String> computePrefixes() {
          return Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        }

        private static boolean isDigit(char c) {
          return c >= '0' && c <= '9';
        }
      }.source();

  /**
   * Parses unsigned decimal point numbers, e.g., {@code 1.23}, {@code 0.0}, {@code 15}, {@code 0}.
   *
   * <p>To support signs, you can compose it like:
   *
   * <pre>{@code
   * Parser<Double> signedDecimal = sequence(
   *     one('-').thenReturn(-1).orElse(1), UNSIGNED_DECIMAL.map(Double::parseDouble),
   *     (sign, num) -> sign * num);
   * }</pre>
   */
  public static final Parser<String> UNSIGNED_DECIMAL =
      literally(UNSIGNED_INTEGER, sequence(one('.'), consecutive("[0-9]")).optional())
          .source();

  /**
   * Parses duration in the shorthand format of {@code 1.5h}, {@code 30d}, {@code 10m30s} etc.
   *
   * <p>Matches one or more unit specs consisting of a positive decimal number followed by a unit
   * suffix. For example:
   *
   * <ul>
   *   <li>{@code "30s"} {@code ->} 30 seconds
   *   <li>{@code "2h30m"} {@code ->} 2 hours and 30 minutes
   *   <li>{@code "1w2d"} {@code ->} 9 days (1 week + 2 days)
   *   <li>{@code "1.5h"} {@code ->} 1 hour and 30 minutes
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
   * <p>Note:
   *
   * <ul>
   *   <li>The duration components must be specified in strictly descending order of unit size
   *       (e.g., {@code "1d2h"} is allowed, but {@code "2h1d"} or {@code "1d1d"} are not).
   *   <li>Only the last component can contain a decimal point (e.g., {@code "1.5h"} or {@code
   *       "1h2.5m"} are allowed, but {@code "1.5h2m"} is not).
   *   <li>Negative values (e.g., {@code "-2s"}) are not supported.
   * </ul>
   */
  public static final Parser<Duration> DURATION = literally(
          sequence(
                  UNSIGNED_DECIMAL,
                  anyOf(DurationUnit.values())
                      .notImmediatelyFollowedBy(charsIn("[a-zA-Z]"), "duration unit char"),
                  (num, unit) -> {
                    try {
                      return num.contains(".")
                          ? new TimeSpan.Fractional(Double.parseDouble(num), unit)
                          : new TimeSpan.Integral(Long.parseLong(num), unit);
                    } catch (NumberFormatException e) {
                      throw Parser.fail(e.getMessage());
                    }
                  })
              .atLeastOnce())
      .map(durations -> {
        adjacentPairsFrom(durations)
            .forEach((prev, next) -> {
              if (prev instanceof TimeSpan.Fractional) {
                throw Parser.fail(
                    "Only the last duration segment is allowed to be fractional: " + prev);
              }
              if (prev.unit().compareTo(next.unit()) <= 0) {
                throw Parser.fail("Duration units must be specified in order: " + prev + next);
              }
            });
        try {
          return durations.stream()
              .map(seg -> {
                try {
                  return seg.toDuration();
                } catch (ArithmeticException e) {
                  throw Parser.fail("duration out of range: " + seg);
                }
              })
              .reduce(Duration::plus)
              .get();
        } catch (ArithmeticException e) {
          throw Parser.fail("duration out of range");
        }
      });

  private sealed interface TimeSpan {
    DurationUnit unit();
    Duration toDuration();

    record Integral(long n, DurationUnit unit) implements TimeSpan {
      @Override public Duration toDuration() {
        return unit.of(n);
      }

      @Override public String toString() {
        return "" + n + unit;
      }
    }

    record Fractional(double n, DurationUnit unit) implements TimeSpan {
      @Override public Duration toDuration() {
        return unit.of(n);
      }

      @Override public String toString() {
        return "" + n + unit;
      }
    }
  }

  private enum DurationUnit {
    NANOSECOND("ns") {
      @Override Duration of(long n) {
        return Duration.ofNanos(n);
      }

      @Override long nanos() {
        return 1;
      }
    },
    MICROSECOND("us") {
      @Override Duration of(long n) {
        return Duration.ofNanos(nanos() * n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.MICROSECONDS);
      }
    },
    MILLISECOND("ms") {
      @Override Duration of(long n) {
        return Duration.ofMillis(n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.MILLISECONDS);
      }
    },
    SECOND("s") {
      @Override Duration of(long n) {
        return Duration.ofSeconds(n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.SECONDS);
      }
    },
    MINUTE("m") {
      @Override Duration of(long n) {
        return Duration.ofMinutes(n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.MINUTES);
      }
    },
    HOUR("h") {
      @Override Duration of(long n) {
        return Duration.ofHours(n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.HOURS);
      }
    },
    DAY("d") {
      @Override Duration of(long n) {
        return Duration.ofDays(n);
      }

      @Override long nanos() {
        return NANOSECONDS.convert(1, TimeUnit.DAYS);
      }
    },
    WEEK("w") {
      @Override Duration of(long n) {
        return Duration.ofDays(n * 7);
      }

      @Override long nanos() {
        return 7 * DAY.nanos();
      }
    },
    ;

    private final String str;

    DurationUnit(String str) {
      this.str = str;
    }

    abstract Duration of(long n);
    abstract long nanos();

    final Duration of(double d) {
      if (d > Long.MAX_VALUE) {
        throw new ArithmeticException("Double value " + d + " out of range.");
      }
      long n = (long) d;
      return of(n).plusNanos((long) ((d - n) * nanos()));
    }

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
   * <p>You can also compose it with {@link Parser#quotedByWithEscapes
   * Parser.quotedByWithEscapes()}:
   *
   * <pre>{@code
   * Parser.quotedByWithEscapes('"', '"', Parser.string("u").then(BMP_CODE_UNIT).map(Character::toString));
   * }</pre>
   */
  public static final Parser<Integer> BMP_CODE_UNIT =
      Parser.hexDigits(4).elidableMap(digits -> Integer.parseInt(digits, 16));

  private Parsers() {}
}

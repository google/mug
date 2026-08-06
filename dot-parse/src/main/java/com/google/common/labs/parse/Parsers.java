package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.mu.util.stream.BiStream.adjacentPairsFrom;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * More advanced composite parsers in addition to the core parsers provided by {@link Parser}.
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
          if (input.isEof(from)) return from;
          char c = input.charAt(from);
          int index = from + 1;
          if (c >= '1' && c <= '9') {
            while (input.startsWith(CharacterSet.DECIMAL, index)) index++;
            return index;
          }
          if (c == '0') {
            return input.startsWith(CharacterSet.DECIMAL, index) ? from : index;
          }
          return from;
        }

        @Override Set<String> computePrefixes() {
          return DIGITS.getPrefixes();
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
      literally(UNSIGNED_INTEGER, sequence(one('.'), consecutive("[0-9]")).optional()).source();

  /**
   * Parses double-precision numbers that support scientific notation, conforming to <a
   * href="https://tools.ietf.org/html/rfc8259">RFC 8259</a> (JSON spec).
   *
   * <p>E.g., {@code 123}, {@code -0.5}, {@code 1e10}, {@code -1.23e+4}, {@code 0.0e-5}.
   *
   * <p>The input string is parsed into a {@link Double}. You can also call {@code .source()} if you
   * prefer to obtain the raw matched string or parse into a different type such as {@code
   * BigDecimal}:
   *
   * <pre>{@code
   * Parser<BigDecimal> bigDecimal = Parsers.SIGNED_DOUBLE.source().map(BigDecimal::new);
   * }</pre>
   *
   * <p>Note that leading plus signs (e.g., {@code +1}), leading zeros on integers (e.g., {@code
   * 05}), and missing integer or fractional parts (e.g., {@code .5} or {@code 5.}) are not allowed,
   * as per the JSON standard.
   */
  public static final Parser<Double> SIGNED_DOUBLE = literally(
          one('-').optional(), UNSIGNED_DECIMAL,
          sequence(caseInsensitive("e"), one("[+-]").optional(), DIGITS).optional())
      .source()
      .elidableMap(Double::parseDouble);

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
   * Parses a 4-digit hex BMP code unit.
   *
   * <p>You can use it together with {@link Parser#quotedByWithEscapes Parser.quotedByWithEscapes()}
   * to parse unicode escapes like:
   *
   * <pre>{@code
   * Parser.quotedByWithEscapes('"', '"', Parser.one('u').then(BMP_CODE_UNIT).map(String::valueOf));
   * }</pre>
   */
  public static final Parser<Character> BMP_CODE_UNIT =
      Parser.hexDigits(4).elidableMap(digits -> (char) Integer.parseInt(digits, 16));

  /**
   * Parses an 8-digit hex Unicode code point (such as those following {@code \U} in string
   * escapes).
   *
   * <p>The parsed integer is guaranteed to be a valid Unicode code point (between {@code 0} and
   * {@code 0x10FFFF}).
   *
   * <p>You can use it together with {@link Parser#quotedByWithEscapes Parser.quotedByWithEscapes()}
   * to parse unicode escapes like:
   *
   * <pre>{@code
   * Parser<String> quotedStringWithUnicodeEscape = Parser.quotedByWithEscapes(
   *     '"', '"',
   *     Parser.one('U').then(CODE_POINT).map(Character::toString));
   * quotedStringWithUnicodeEscape.parse("\\U0001F600"); // returns "😀"
   * }</pre>
   */
  public static final Parser<Integer> CODE_POINT = Parser.hexDigits(8)
      .map(digits -> Integer.parseUnsignedInt(digits, 16))
      .suchThat(Character::isValidCodePoint, "code point");

  /**
   * A convenience helper to left-factor a common prefix followed by multiple optional suffixes.
   *
   * <p>Usually when you have an optional suffix, you should use {@link
   * Parser#optionallyFollowedBy(String, Function) optionallyFollowedBy()} directly, such as:
   *
   * <pre>{@code
   * expr.optionallyFollowedBy("!", (Integer n) -> factorial(n));
   * }</pre>
   *
   * However when there are more than one optional suffixes to be applied after the same prefix, it
   * becomes harder to compose them without backtracking. You could use {@link
   * Parser#anyOf(Parser...) anyOf()} like:
   *
   * <pre>{@code
   * Parser.anyOf(
   *     expr.followedBy("!").map(n -> factorial(n)),
   *     sequence(expr, exponential, (Expr i, Expr e) -> pow(i, e)));
   * }</pre>
   *
   * But if performance is critical, the same {@code expr} rule will be re-evaluated during
   * backtracking from choice #1 to choice #2, which is wasteful.
   *
   * <p>The following is an example that avoids backtracking, by using the {@code Suffix} helper and
   * {@code anyOf()} to compose the optional suffix operators together before passing to {@code
   * optionallyFollowedBy()}:
   *
   * <pre>{@code
   * import static com.google.common.labs.parse.Parsers.Suffix.suffix;
   *
   * expr.optionallyFollowedBy(
   *     anyOf(
   *         suffix("!", (Expr n) -> factorial(n)),
   *         suffix(exponential, (Expr i, Expr e) -> pow(i, e))),
   *     Parsers.Suffix::apply);
   * }</pre>
   *
   * <p>Occasionally you may need to wrap the left parser's result with or without optional
   * suffixes, regardless. For example, the parsed string needs to be wrapped in either one of
   * {@code Expr} AST types as determined by the optional suffixes, or wrapped in the default {@code
   * LiteralExpr} when no suffix is present, you can use:
   *
   * <pre>{@code
   * import static com.google.common.labs.parse.Parsers.Suffix.suffix;
   *
   * Parser.sequence(
   *     expr,
   *     anyOf(
   *             suffix("!", FactorialExpr::new),
   *             suffix(exponential, PowExpr::new))
   *         .orElse(LiteralExpr::new),
   *     Parsers.Suffix::apply);
   * }</pre>
   *
   * Or even a single optional suffix can benefit too:
   *
   * <pre>{@code
   * import static com.google.common.labs.parse.Parsers.Suffix.suffix;
   *
   * Parser.sequence(
   *     expr,
   *     suffix(exponential, PowExpr::new).orElse(LiteralExpr::new),
   *     Parsers.Suffix::apply);
   * }</pre>
   */
  public static class Suffix {
    /**
     * Returns a parser that applies the {@code prefix} zero or more times before {@code
     * suffix} and applies the result functions iteratively.
     */
    public static <T> Parser<T> withPrefixes(
        Parser<? extends Function<? super T, ? extends T>> prefix, Parser<? extends T> suffix) {
      return sequence(
          prefix.zeroOrMore(), suffix,
          (ops, operand) -> Suffix.applyOperators(operand, ops.reversed()));
    }

    /**
     * Returns a parser that after {@code operand} succeeds, applies the {@code postfix} parser zero or
     * more times and applies the result function iteratively.
     *
     * <p>This is useful to parse postfix operators such as in regex the quantifiers are usually
     * postfix.
     *
     * <p>For infix operator support, consider using {@link OperatorTable}.
     */
    public static <T> Parser<T> withPostfixes(
        Parser<? extends T> operand, Parser<? extends Function<? super T, ? extends T>> postfix) {
      return sequence(operand, postfix.zeroOrMore(), Suffix::applyOperators);
    }

    /**
     * A suffix parser that combines together with its prefix parse's result using the {@code
     * combiner} function.
     */
    public static <T, S, R> Parser<Function<T, R>> suffix(
        Parser<S> suffix, BiFunction<? super T, ? super S, ? extends R> combiner) {
      requireNonNull(combiner);
      return suffix.map(s -> p -> combiner.apply(p, s));
    }

    /** A suffix parser that uses the {@code mapper} function to transform the prefix's result. */
    public static <T, R> Parser<Function<T, R>> suffix(
        String suffix, Function<? super T, ? extends R> mapper) {
      return string(suffix).thenReturn(mapper::apply);
    }

    static <T, S> Parser<UnaryOperator<T>> postfix(
        Parser<S> postfix, BiFunction<? super T, ? super S, ? extends T> op) {
      requireNonNull(op);
      return postfix.map(s -> p -> op.apply(p, s));
    }

    /**
     * A convenience method to apply a suffix to a prefix. When passed to the {@link
     * Parser#optionallyFollowedBy(Parser, BiFunction) optionallyFollowedBy()} as a method reference
     * ({@code Suffix::apply}), it reads in the intuitive encounter order.
     */
    public static <T, R> R apply(T prefix, Function<? super T, ? extends R> suffix) {
      return suffix.apply(prefix);
    }

    static <T> T applyOperators(
        T operand, Iterable<? extends Function<? super T, ? extends T>> ops) {
      for (var op : ops) operand = op.apply(operand);
      return operand;
    }

    Suffix() {}
  }

  private Parsers() {}
}

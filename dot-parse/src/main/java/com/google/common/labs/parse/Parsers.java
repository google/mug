/*****************************************************************************
 * Copyright (C) google.com                                                  *
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.stream.BiStream.adjacentPairsFrom;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.counting;

import com.google.common.labs.parse.Regexes.PrefixAnalyzer;
import com.google.common.labs.regex.RegexPattern;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.function.MapFrom3;
import com.google.mu.function.MapFrom4;
import com.google.mu.function.MapFrom5;
import com.google.mu.function.MapFrom6;
import com.google.mu.function.MapFrom7;
import com.google.mu.function.MapFrom8;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            return input.skipWhile(CharacterSet.DECIMAL, index);
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
   * Returns a leaf-level parser that matches an atomic regular expression {@code pattern}. For
   * example, you could define a parser for US phone numbers using:
   *
   * <pre>{@code
   * Parser<String> usPhoneNumber = Parsers.regex("\\(\\d{3}\\)\\d{3}-\\d{4}");
   * usPhoneNumber.matches("(123)456-7890"); // => true
   * }</pre>
   *
   * <p>Useful when defining a compact, yet composite regex pattern that may otherwise require
   * verbose boilerplate of {@code sequence()}, {@code anyOf()} calls composed together. That said,
   * refrain from creating complex regex patterns, and prefer using the declarative {@code Parser}
   * API unless it's too verbose.
   *
   * <p><b>WARNING: ReDoS Vulnerabilities &amp; Disastrous Backtracking</b>
   *
   * <p>Using regular expressions exposes the application to Regular Expression Denial of Service
   * (ReDoS) attacks. In many regex engines (including {@link java.util.regex.Pattern}), even
   * "simple" patterns can cause exponential backtracking if matched against malicious inputs
   * designed to trigger worst-case paths.
   *
   * <p>For example, the pattern {@code (a+)+} or {@code (a|ab)+} can easily freeze a thread or
   * crash a service with CPU exhaustion when attempting to match a string like {@code
   * "aaaaaaaaaaaaaaaaaaaaaaaaaaaa!"}. See the <a
   * href="https://owasp.org/www-community/attacks/Regular_expression_Denial_of_Service_-_ReDoS">OWASP
   * ReDoS Attack Reference</a> for a detailed analysis of this issue.
   *
   * <p>To avoid ReDoS and keep parsing execution linear and safe, prefer the declarative,
   * backtracking-free {@link Parser} combinator API (using methods like {@link Parser#followedBy
   * followedBy()}, {@link Parser#sequence sequence()}, {@link Parser#anyOf anyOf()}, etc.) and only
   * use {@code regex} on trusted input (such as a config file, command line tool etc).
   *
   * <p>The pattern must be a compile-time constant, must not match the empty string, and must not
   * contain anchors (like {@code ^}, {@code $}), lookarounds (like {@code (?=...)}), or
   * backreferences (like {@code \1}).
   *
   * <p>The returned parser supports parsing from a {@link java.io.Reader} input <em>only if</em>
   * the regex has an upper bound in the match size (e.g. <code>[a-z]{3}</code> or {@code (abc|d)}).
   * Regex patterns with unbounded match size (e.g. {@code [a-z]+}) will throw {@link
   * UnsupportedOperationException} when calling {@link Parser#parseToStream(Reader)} or {@link
   * Parser#probe(Reader)}, because Java regex requires the input to be fully loaded into memory,
   * defeating the purpose of lazy loading from {@code Reader} - you might as well just explicitly
   * load into a {@code String} before parsing.
   *
   * <p>The {@code pattern} string is validated at compile-time by the {@code mug-errorprone}
   * (v10.9+) compiler plugin.
   *
   * <p>If you need to extract values from capturing groups in the matched regex, use {@link
   * #regex(String, Function)} or other group-mapping overloads (such as {@link #regex(String,
   * BiFunction)}) instead.
   *
   * <p>NOTE that this method internally compiles the {@code pattern} so you should almost always
   * pre-create and reuse the returned {@code Parser} object instead of calling {@code
   * regex(pattern)} in the inner loop or on-the-fly.
   *
   * @throws IllegalArgumentException if the pattern is invalid or contains forbidden features.
   * @since 10.9
   */
  public static Parser<String> regex(@CompileTimeConstant String pattern) {
    return regex(Regexes.strict(pattern), Pattern.compile(pattern), "=~/" + pattern + "/").source();
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the captured
   * group value using {@code mapper}.
   *
   * <p>For example, to extract the area code from a phone number:
   *
   * <pre>{@code
   * Parser<Integer> areaCode = regex("\\((\\d{3})\\) \\d{3}-\\d{4}", Integer::parseInt);
   * }</pre>
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 1 capturing group or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, Function<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(pattern, /* expectedGroups= */ 1, matcher -> mapper.apply(matcher.group(1)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 2
   * captured group values using {@code mapper}.
   *
   * <p>For example, to extract the area code and optional extension from a phone number:
   *
   * <pre>{@code
   * Parser<PhoneNumber> phoneNumber = regex(
   *     "\\((?<areaCode>\\d{3})\\) \\d{3}-\\d{4}(?: x(?<extension>\\d+))?",
   *     (areaCode, extension) -> new PhoneNumber(areaCode, extension));
   * }</pre>
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+. For example, the following will fail compilation:
   *
   * <pre>{@code
   * Parser<PhoneNumber> phoneNumber = regex(
   *     "\\((?<areaCode>\\d{3})\\) \\d{3}-\\d{4}(?: x(?<extension>\\d+))?",
   *     (extension, areaCode) -> ...); // Compile error: parameters out of order
   * }</pre>
   *
   * @throws IllegalArgumentException if the regex does not have exactly 2 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern,
      BiFunction<? super String, ? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 2,
        matcher -> mapper.apply(matcher.group(1), matcher.group(2)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 3
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 3 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom3<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 3,
        matcher -> mapper.map(matcher.group(1), matcher.group(2), matcher.group(3)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 4
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 4 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom4<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 4,
        matcher ->
            mapper.map(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 5
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 5 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom5<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 5,
        matcher -> mapper.map(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            matcher.group(4),
            matcher.group(5)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 6
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 6 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom6<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 6,
        matcher -> mapper.map(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            matcher.group(4),
            matcher.group(5),
            matcher.group(6)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 7
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 7 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom7<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 7,
        matcher -> mapper.map(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            matcher.group(4),
            matcher.group(5),
            matcher.group(6),
            matcher.group(7)));
  }

  /**
   * Returns a leaf-level parser that matches the given {@code pattern} and transforms the 8
   * captured group values using {@code mapper}.
   *
   * <p>Capturing groups start from group 1; group 0 (the top-level full match) is not passed to the
   * {@code mapper}. If you only need the top-level match, use {@link #regex(String)}; if you need
   * both the entire match and nested groups, wrap the entire pattern in parentheses (e.g. {@code
   * "((foo)(bar))"}). Alternatively, calling {@link Parser#source() .source()} on the returned
   * parser returns the full matched substring.
   *
   * <p>Nested capturing groups are fully supported (ordered by opening parenthesis). Optional
   * groups (such as {@code (?:-(\d+))?}) and alternations (such as {@code (\d+)|([a-z]+)}) will
   * pass {@code null} to the {@code mapper} if that group was not matched.
   *
   * <p>If named capturing groups (such as {@code "(?<name>...)"}) are used, their names are checked
   * at compile time against lambda parameter names (or method reference parameters) with
   * mug-errorprone 11.0+.
   *
   * @throws IllegalArgumentException if the regex does not have exactly 8 capturing groups or is
   *     invalid / contains forbidden features.
   * @since 11.0
   */
  public static <T> Parser<T> regex(
      @CompileTimeConstant String pattern, MapFrom8<? super String, ? extends T> mapper) {
    requireNonNull(mapper);
    return regex(
        pattern,
        /* expectedGroups= */ 8,
        matcher -> mapper.map(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            matcher.group(4),
            matcher.group(5),
            matcher.group(6),
            matcher.group(7),
            matcher.group(8)));
  }

  private static <T> Parser<T> regex(
      String pattern, int expectedGroups, Function<? super Matcher, ? extends T> mapper) {
    Pattern jdkPattern = Pattern.compile(pattern);
    int groupCount = jdkPattern.matcher("").groupCount();
    checkArgument(
        groupCount == expectedGroups,
        "regex pattern '%s' has %s capturing group(s), but %s expected",
        jdkPattern.pattern(), groupCount, expectedGroups);
    return regex(Regexes.strict(pattern), jdkPattern, "=~/" + pattern + "/", mapper);
  }

  private static <T> Parser<T> regex(
      RegexPattern ast, Pattern jdkPattern, String name,
      Function<? super Matcher, ? extends T> mapper) {
    RegexPattern.Metadata metadata = ast.metadata();
    return new Parser<T>() {
      @Override MatchResult<T> skipAndMatch(
          Skipper skip, CharInput input, int start, ErrorContext context) {
        start = Parser.skipIfAny(skip, input, start);
        Matcher matcher = input.matcher(jdkPattern, metadata, start);
        if (!matcher.lookingAt()) {
          return context.expecting(name, start);
        }
        int end = input.matchEnd(matcher);
        return new MatchResult.Success<>(start, end, mapper.apply(matcher));
      }

      @Override Set<String> computePrefixes() {
        return new PrefixAnalyzer().prefixesOf(ast);
      }

      @Override Set<String> getExpectedSymbols() {
        return Set.of(name);
      }

      @Override public Parser<T> as(String logicalName) {
        return regex(ast, jdkPattern, logicalName, mapper);
      }
    };
  }

  private static Parser<Void> regex(RegexPattern ast, Pattern jdkPattern, String name) {
    RegexPattern.Metadata metadata = ast.metadata();
    return new Scanner(name) {
      @Override int scan(CharInput input, int from) {
        return input.match(jdkPattern, metadata, from);
      }

      @Override Set<String> computePrefixes() {
        return new PrefixAnalyzer().prefixesOf(ast);
      }

      @Override public Parser<Void> as(String logicalName) {
        return regex(ast, jdkPattern, logicalName);
      }
    };
  }

  /**
   * Provides helpers to left-factor common prefixes followed by one or multiple optional suffixes.
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
   * import com.google.common.labs.parse.Parsers.Suffix;
   *
   * expr.optionallyFollowedBy(
   *     anyOf(
   *         suffix("!", (Expr n) -> factorial(n)),
   *         suffix(exponential, (Expr i, Expr e) -> pow(i, e))),
   *     Suffix::apply);
   * }</pre>
   *
   * <p>Occasionally you may need to wrap the left parser's result with or without optional
   * suffixes, regardless. For example, the parsed string needs to be wrapped in either one of
   * {@code Expr} AST types as determined by the optional suffixes, or wrapped in the default {@code
   * LiteralExpr} when no suffix is present, you can use:
   *
   * <pre>{@code
   * import static com.google.common.labs.parse.Parsers.Suffix.suffix;
   * import com.google.common.labs.parse.Parsers.Suffix;
   *
   * Parser.sequence(
   *     expr,
   *     anyOf(
   *             suffix("!", FactorialExpr::new),
   *             suffix(exponential, PowExpr::new))
   *         .orElse(LiteralExpr::new),
   *     Suffix::apply);
   * }</pre>
   *
   * Or even a single optional suffix can benefit too:
   *
   * <pre>{@code
   * import static com.google.common.labs.parse.Parsers.Suffix.suffix;
   * import com.google.common.labs.parse.Parsers.Suffix;
   *
   * Parser.sequence(
   *     expr,
   *     suffix(exponential, PowExpr::new).orElse(LiteralExpr::new),
   *     Suffix::apply);
   * }</pre>
   */
  public static class Suffix {
    /**
     * Returns a parser that matches zero or more occurrences of the {@code prefix} string before
     * {@code suffix} and applies the {@code prefixFunction} iteratively for each matched prefix.
     *
     * <p>For example:
     *
     * <pre>{@code
     * import static com.google.common.labs.parse.Parsers.UNSIGNED_INTEGER;
     * import static com.google.common.labs.parse.Parsers.Suffix.withPrefixes;
     *
     * Parser<Integer> number = withPrefixes("-", UNSIGNED_INTEGER.map(Integer::parseInt), n -> -n);
     * }</pre>
     */
    public static <T> Parser<T> withPrefixes(
        String prefix, Parser<? extends T> suffix, UnaryOperator<T> prefixFunction) {
      requireNonNull(prefixFunction);
      return sequence(
          string(prefix).zeroOrMore(counting()), suffix,
          (times, operand) -> applyOperator(operand, prefixFunction, times));
    }

    /**
     * Returns a parser that matches the {@code prefix} parser zero or more times before {@code
     * suffix} and applies the result functions iteratively, in First-In, Last-Out order.
     *
     * <p>For example:
     *
     * <pre>{@code
     * Parser<Declaration> declaration =
     *     withPrefixes(modifier.map(m -> id -> id.withModifier(m)), IDENTIFIER);
     * }</pre>
     */
    public static <T> Parser<T> withPrefixes(
        Parser<? extends Function<? super T, ? extends T>> prefix, Parser<? extends T> suffix) {
      return sequence(
          prefix.zeroOrMore(), suffix, (ops, operand) -> applyOperators(operand, ops.reversed()));
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

    /**
     * A convenience method to apply a suffix to a prefix. When passed to the {@link
     * Parser#optionallyFollowedBy(Parser, BiFunction) optionallyFollowedBy()} as a method reference
     * ({@code Suffix::apply}), it reads in the intuitive encounter order.
     */
    public static <T, R> R apply(T prefix, Function<? super T, ? extends R> suffix) {
      return suffix.apply(prefix);
    }

    static <T, S> Parser<UnaryOperator<T>> postfix(
        Parser<S> postfix, BiFunction<? super T, ? super S, ? extends T> op) {
      requireNonNull(op);
      return postfix.map(s -> p -> op.apply(p, s));
    }

    static <T> T applyOperator(T operand, Function<? super T, ? extends T> op, long times) {
      for (long i = 0; i < times; i++) operand = op.apply(operand);
      return operand;
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

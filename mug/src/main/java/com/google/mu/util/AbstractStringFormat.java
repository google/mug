/*****************************************************************************
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
package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreCollectors.combining;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.mu.function.MapFrom3;
import com.google.mu.function.MapFrom4;
import com.google.mu.function.MapFrom5;
import com.google.mu.function.MapFrom6;
import com.google.mu.function.MapFrom7;
import com.google.mu.function.MapFrom8;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.MoreStreams;

/**
 * The API of StringFormat. Allows different subclasses to use different placeholder styles.
 */
abstract class AbstractStringFormat {
  private final String format;
  final List<String> fragments; // The string literals between placeholders
  private final List<Boolean> toCapture;
  private final int numCapturingPlaceholders;

  AbstractStringFormat(
      String format, Substring.RepeatingPattern placeholdersPattern, String wildcard) {
    Stream.Builder<String> delimiters = Stream.builder();
    Stream.Builder<Boolean> toCapture = Stream.builder();
    placeholdersPattern.split(format).forEachOrdered(
        literal -> {
          delimiters.add(literal.toString());
          toCapture.add(!literal.isFollowedBy(wildcard));
        });
    this.format = format;
    this.fragments = delimiters.build().collect(toImmutableList());
    this.toCapture = chop(toCapture.build().collect(toImmutableList()));
    this.numCapturingPlaceholders =
        this.fragments.size() - 1 - (int) this.toCapture.stream().filter(c -> !c).count();
  }

  /**
   * Parses {@code input} and applies the {@code mapper} function with the single placeholder value
   * in this string format.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (job id: {job_id})").parse(input, jobId -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly one placeholder.
   */
  public final <R> Optional<R> parse(String input, Function<? super String, ? extends R> mapper) {
    return parseExpecting(1, input, onlyElement(mapper));
  }

  /**
   * Parses {@code input} and applies {@code mapper} with the two placeholder values in this string
   * format.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (job id: '{id}', error code: {code})")
   *     .parse(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly two placeholders.
   */
  public final <R> Optional<R> parse(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    return parseExpecting(2, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>3</em> placeholder values in this string format.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (job id: '{job_id}', error code: {code}, error details: {details})")
   *     .parse(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 3 placeholders.
   */
  public final <R> Optional<R> parse(String input, MapFrom3<? super String, ? extends R> mapper) {
    return parseExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>4</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 4 placeholders.
   */
  public final <R> Optional<R> parse(
      String input, MapFrom4<? super String, ? extends R> mapper) {
    return parseExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>5</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 5 placeholders.
   */
  public final <R> Optional<R> parse(String input, MapFrom5<? super String, ? extends R> mapper) {
    return parseExpecting(5, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>6</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 6 placeholders.
   */
  public final <R> Optional<R> parse(String input, MapFrom6<? super String, ? extends R> mapper) {
    return parseExpecting(6, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>7</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 7 placeholders.
   * @since 7.2
   */
  public final <R> Optional<R> parse(String input, MapFrom7<? super String, ? extends R> mapper) {
    return parseExpecting(7, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>8</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 8 placeholders.
   * @since 7.2
   */
  public final <R> Optional<R> parse(String input, MapFrom8<? super String, ? extends R> mapper) {
    return parseExpecting(8, input, combining(mapper));
  }

  /**
   * Parses {@code input} against the pattern.
   *
   * <p>Returns an immutable list of placeholder values in the same order as the placeholders in
   * the format string, upon success; otherwise returns empty.
   *
   * <p>The {@link Substring.Match} result type allows caller to inspect the characters around each
   * match, or to access the raw index in the input string.
   *
   * @since 8.3
   */
  public final Optional<List<Substring.Match>> parseAsList(String input) {
    return internalParse(input, fragments, toCapture);
  }

  private Optional<List<Substring.Match>> internalParse(
      String input, List<String> fragments, List<Boolean> toCapture) {
    checkUnformattability();
    if (!input.startsWith(fragments.get(0))) { // first literal is the prefix
      return Optional.empty();
    }
    List<Substring.Match> builder = new ArrayList<>(numCapturingPlaceholders);
    int inputIndex = fragments.get(0).length();
    int numPlaceholders = numPlaceholders();
    for (int i = 1; i <= numPlaceholders; i++) {
      // subsequent delimiters are searched left-to-right; last literal is the suffix.
      Substring.Pattern trailingLiteral =
          i < numPlaceholders ? first(fragments.get(i)) : suffix(fragments.get(i));
      Substring.Match placeholder = before(trailingLiteral).in(input, inputIndex).orElse(null);
      if (placeholder == null) {
        return Optional.empty();
      }
      if (toCapture.get(i - 1)) {
        builder.add(placeholder);
      }
      inputIndex = placeholder.index() + placeholder.length() + fragments.get(i).length();
    }
    return optional(inputIndex == input.length(), unmodifiableList(builder));
  }

  /**
   * Parses {@code input} and applies {@code mapper} with the single placeholder value in this
   * format string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (job id: {job_id})").parseOrThrow(input, jobId -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, Function)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, Function)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly one placeholder
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(String input, Function<? super String, R> mapper) {
    return parseOrThrowExpecting(1, input, onlyElement(mapper));
  }

  /**
   * Parses {@code input} and applies {@code mapper} with the two placeholder values in this format
   * string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (job id: '{job_id}', error code: {error_code})")
   *     .parseOrThrow(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, BiFunction)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, BiFunction)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     value.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly two placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(
      String input, BiFunction<? super String, ? super String, R> mapper) {
    return parseOrThrowExpecting(2, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>3</em> placeholder values in this format string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("Job failed (id: '{job_id}', code: {error_code}, error details: {details})")
   *     .parseOrThrow(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, MapFrom3)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom3)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 3 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(String input, MapFrom3<? super String, R> mapper) {
    return parseOrThrowExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>4</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, MapFrom4)}, {@code IllegalArgumentException} is thrown if
   * the input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom4)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 4 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(String input, MapFrom4<? super String, R> mapper) {
    return parseOrThrowExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>5</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, MapFrom5)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom5)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 5 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(String input, MapFrom5<? super String, R> mapper) {
    return parseOrThrowExpecting(5, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>6</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom6)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 6 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.0
   */
  public final <R> R parseOrThrow(String input, MapFrom6<? super String, R> mapper) {
    return parseOrThrowExpecting(6, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>7</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom6)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 7 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.2
   */
  public final <R> R parseOrThrow(String input, MapFrom7<? super String, R> mapper) {
    return parseOrThrowExpecting(7, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>8</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, MapFrom6)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 8 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 7.2
   */
  public final <R> R parseOrThrow(String input, MapFrom8<? super String, R> mapper) {
    return parseOrThrowExpecting(8, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, Function)}, parses {@code input} and applies {@code mapper}
   * with the single placeholder value in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>For unambiguous strings, it's equivalent to {@link #parse(String, Function)}, but if for
   * example you are parsing "a/b/c" against the pattern of "{parent}/{...}", {@code parse("a/b/c",
   * parent -> parent)} results in "a", while {@code parseGreedy("a/b/c", parent -> parent)} results
   * in "a/b".
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly one placeholder.
   * @since 7.0
   */
  public final <R> Optional<R> parseGreedy(
      String input, Function<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(1, input, onlyElement(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, parses {@code input} and applies {@code mapper}
   * with the two placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>For unambiguous strings, it's equivalent to {@link #parse(String, BiFunction)}, but if for
   * example you are parsing "a/b/c" against the pattern of "{parent}/{child}", {@code
   * parse("a/b/c", (parent, child) -> ...)} parses out "a" as parent and "b/c" as child, while
   * {@code parseGreedy("a/b/c", (parent, child) -> ...)} parses "a/b" as parent and "c" as child.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly two placeholders.
   * @since 7.0
   */
  public final <R> Optional<R> parseGreedy(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    return parseGreedyExpecting(2, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, MapFrom3)}, parses {@code input} and applies {@code mapper}
   * with the 3 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 3 placeholders.
   * @since 7.0
   */
  public final <R> Optional<R> parseGreedy(
      String input, MapFrom3<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, MapFrom4)}, parses {@code input} and applies {@code mapper}
   * with the 3 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 4 placeholders.
   * @since 7.0
   */
  public final <R> Optional<R> parseGreedy(
      String input, MapFrom4<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, MapFrom5)}, parses {@code input} and applies {@code mapper}
   * with the 5 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 5 placeholders.
   * @since 7.0
   */
  public final <R> Optional<R> parseGreedy(
      String input, MapFrom5<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(5, input, combining(mapper));
  }

  /**
   * Returns true if this format matches {@code input} entirely.
   *
   * @since 7.0
   */
  public final boolean matches(String input) {
    return parseAsList(input).isPresent();
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>Note that while the placeholder value(s) are passed to the {@code replacement} function, the
   * full matching substring of the string format (including but not limited to the placeholders)
   * are replaced by the return value of the {@code replacement} function. So for example if you are
   * trying to rewrite every {@code "<{value}>"} to {@code "<v...>"}, be sure to include the angle
   * bracket characters as in:
   *
   * <pre>{@code
   * StringFormat bracketed = new StringFormat(""<{value}>"");
   * String rewritten =
   *     bracketed.replaceAllFrom(input, value -> bracketed.format(value.charAt(0) + "..."));
   * }</pre>
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter type.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 1 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, Function<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(1);
    return replaceAllMatches(input, matches -> replacement.apply(matches.get(0)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>Note that while the placeholder value(s) are passed to the {@code replacement} function, the
   * full matching substring of the string format (including but not limited to the placeholders)
   * are replaced by the return value of the {@code replacement} function. So for example if you are
   * trying to rewrite every {@code "[{key}:{value}]"} to {@code "[{key}={value}]"}, be sure to
   * include the square bracket characters as in:
   *
   * <pre>{@code
   * StringFormat oldFormat = new StringFormat("[{key}:{value}]");
   * StringFormat newFormat = new StringFormat("[{key}={value}]");
   * String rewritten =
   *     oldFormat.replaceAllFrom(input, (key, value) -> newFormat.format(key, value));
   * }</pre>
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 2 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, BiFunction<? super Substring.Match, ? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(2);
    return replaceAllMatches(input, matches -> replacement.apply(matches.get(0), matches.get(1)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 3 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom3<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(3);
    return replaceAllMatches(
        input, matches -> replacement.map(matches.get(0), matches.get(1), matches.get(2)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 4 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom4<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(4);
    return replaceAllMatches(
        input,
        matches -> replacement.map(matches.get(0), matches.get(1), matches.get(2), matches.get(3)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 5 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom5<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(5);
    return replaceAllMatches(
        input,
        matches ->
            replacement.map(
                matches.get(0), matches.get(1), matches.get(2), matches.get(3), matches.get(4)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 6 named placeholder
   *
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom6<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(6);
    return replaceAllMatches(
        input,
        matches ->
            replacement.map(
                matches.get(0),
                matches.get(1),
                matches.get(2),
                matches.get(3),
                matches.get(4),
                matches.get(5)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 7 named placeholder
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom7<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(7);
    return replaceAllMatches(
        input,
        matches ->
            replacement.map(
                matches.get(0),
                matches.get(1),
                matches.get(2),
                matches.get(3),
                matches.get(4),
                matches.get(5),
                matches.get(6)));
  }

  /**
   * Scans {@code input} and replaces all matches using the {@code replacement} function.
   *
   * <p>If no match is found, the {@code input} string is returned.
   *
   * <p>If {@code replacement} returns null, the match is ignored as if it didn't match the format.
   * This can be used to post-filter the match with custom predicates (e.g. a placeholder value must
   * be digits only).
   *
   * <p>The {@code replacement} function accepts {@link Substring.Match} instead of String to avoid
   * unnecessary copying of the characters. If you are passing in a method reference, it can also
   * take CharSequence or Object as the parameter types.
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly 8 named placeholder
   * @since 7.2
   */
  public final String replaceAllFrom(
      String input, MapFrom8<? super Substring.Match, ?> replacement) {
    requireNonNull(input);
    requireNonNull(replacement);
    checkPlaceholderCount(8);
    return replaceAllMatches(
        input,
        matches ->
            replacement.map(
                matches.get(0),
                matches.get(1),
                matches.get(2),
                matches.get(3),
                matches.get(4),
                matches.get(5),
                matches.get(6),
                matches.get(7)));
  }

  final String replaceAllMatches(
      String input, Function<? super List<Substring.Match>, ?> replacement) {
    List<int[]> matchIndices = new ArrayList<>();
    List<List<Substring.Match>> matches =
        matchRepeatedly(input, (start, end) -> matchIndices.add(new int[] {start, end}))
            .collect(toImmutableList());
    if (matchIndices.size() != matches.size()) {
      throw new IllegalStateException(matchIndices.size() + " != " + matches.size());
    }
    StringBuilder builder = new StringBuilder();
    int inputIndex = 0;
    for (int i = 0; i < matches.size(); i++) {
      Object replaceWith = replacement.apply(matches.get(i));
      if (replaceWith == null) {
        continue; // skip
      }
      int matchStart = matchIndices.get(i)[0];
      int matchEnd = matchIndices.get(i)[1];
      if (inputIndex < matchStart) {
        builder.append(input, inputIndex, matchStart);
      }
      builder.append(replaceWith);
      inputIndex = matchEnd;
    }
    return inputIndex == 0 && builder.length() == 0
        ? input // avoid copying
        : builder.append(input, inputIndex, input.length()).toString();
  }

  /**
   * Scans the {@code input} string and extracts all matched placeholders in this string format.
   *
   * <p>Unlike {@link #parseAsList}, the input string isn't matched entirely: the pattern doesn't
   * have to start from the beginning, and if there are some remaining characters that don't match
   * the pattern any more, the stream stops. In particular, if there is no match, empty stream is
   * returned.
   *
   * @since 8.3
   */
  public final Stream<List<Substring.Match>> scanAsLists(String input) {
    return matchRepeatedly(input, (start, end) -> {});
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the single placeholder values to the {@code mapper}
   * function for each iteration, with null results skipped.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("/home/usr/myname/{file_name}\n")
   *     .scan(multiLineInput, fileName -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, Function)}, the input string isn't matched entirely: the
   * pattern doesn't have to start from the beginning, and if there are some remaining characters
   * that don't match the pattern any more, the stream stops. In particular, if there is no match,
   * empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If the
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(String input, Function<? super String, ? extends R> mapper) {
    return scanExpecting(1, input, onlyElement(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the two placeholder values to the {@code mapper}
   * function for each iteration, with null results skipped.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("[key={key}, value={value}]")
   *     .repeatedly()
   *     .parse(input, (key, value) -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, BiFunction)}, the input string isn't matched entirely: the
   * pattern doesn't have to start from the beginning, and if there are some remaining characters
   * that don't match the pattern any more, the stream stops. In particular, if there is no match,
   * empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    return scanExpecting(2, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 3 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>For example:
   *
   * <pre>{@code
   * new StringFormat("[{lhs} + {rhs} = {result}]")
   *     .repeatedly()
   *     .parse(input, (lhs, rhs, result) -> ...);
   * }</pre>
   *
   * <p>Unlike {@link #parse(String, MapFrom3)}, the input string isn't matched entirely: the pattern
   * doesn't have to start from the beginning, and if there are some remaining characters that don't
   * match the pattern any more, the stream stops. In particular, if there is no match, empty stream
   * is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(String input, MapFrom3<? super String, ? extends R> mapper) {
    return scanExpecting(3, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 4 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>Unlike {@link #parse(String, MapFrom4)}, the input string isn't matched entirely: the
   * pattern doesn't have to start from the beginning, and if there are some remaining characters
   * that don't match the pattern any more, the stream stops. In particular, if there is no match,
   * empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(String input, MapFrom4<? super String, ? extends R> mapper) {
    return scanExpecting(4, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 5 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>Unlike {@link #parse(String, MapFrom5)}, the input string isn't matched entirely: the pattern
   * doesn't have to start from the beginning, and if there are some remaining characters that don't
   * match the pattern any more, the stream stops. In particular, if there is no match, empty stream
   * is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(String input, MapFrom5<? super String, ? extends R> mapper) {
    return scanExpecting(5, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 6 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, the input string isn't matched entirely: the pattern
   * doesn't have to start from the beginning, and if there are some remaining characters that don't
   * match the pattern any more, the stream stops. In particular, if there is no match, empty stream
   * is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   */
  public final <R> Stream<R> scan(String input, MapFrom6<? super String, ? extends R> mapper) {
    return scanExpecting(6, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 7 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, the input string isn't matched entirely: the pattern
   * doesn't have to start from the beginning, and if there are some remaining characters that don't
   * match the pattern any more, the stream stops. In particular, if there is no match, empty stream
   * is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   *
   * @since 7.2
   */
  public final <R> Stream<R> scan(String input, MapFrom7<? super String, ? extends R> mapper) {
    return scanExpecting(7, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format. Returns the lazy
   * stream of non-null results from passing the 8 placeholder values to the {@code mapper} function
   * for each iteration, with null results skipped.
   *
   * <p>Unlike {@link #parse(String, MapFrom6)}, the input string isn't matched entirely: the pattern
   * doesn't have to start from the beginning, and if there are some remaining characters that don't
   * match the pattern any more, the stream stops. In particular, if there is no match, empty stream
   * is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from the
   * {@code mapper} function, which will then be ignored in the result stream.
   *
   * @since 7.2
   */
  public final <R> Stream<R> scan(String input, MapFrom8<? super String, ? extends R> mapper) {
    return scanExpecting(8, input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and collects all matches of this string format using {@code
   * collector}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * List<String> fileNames =
   *     new StringFormat("/home/usr/myname/{file_name}\n")
   *         .scanAndCollectFrom(multiLineInput, toList());
   * }</pre>
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly one placeholder.
   * @since 8.0
   */
  public final <R> R scanAndCollectFrom(String input, Collector<? super String, ?, R> collector) {
    requireNonNull(collector);
    return scan(input, identity()).collect(collector);
  }

  /**
   * Scans the {@code input} string and collects all pairs of placeholders defined by this string
   * format using {@code collector}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Map<String, String> keyValues =
   *     new StringFormat("{{key}: {value}}")
   *         .scanAndCollectFrom(input, Collectors::toMap);
   * }</pre>
   *
   * <p>If you need to apply intermediary operations before collecting to the final result, consider
   * using {@link com.google.mu.util.stream.BiStream#toBiStream(Function, Function) BiStream::toBiStream}
   * like the following code:
   *
   * <pre>{@code
   * ImmutableMap<UserId, EmailAddress> userEmails =
   *     new StringFormat("{{key}: {value}}")
   *         .scanAndCollectFrom(input, BiStream::toBiStream)
   *         .mapKeys(UserId::of)
   *         .mapValues(EmailAddress::parse)
   *         .toMap();
   * }</pre>
   *
   * @throws IllegalArgumentException if the format string doesn't have exactly two placeholders.
   * @since 8.0
   */
  public final <R> R scanAndCollectFrom(
      String input, BiCollector<? super String, ? super String, R> collector) {
    requireNonNull(collector);
    return scan(input, (l, r) -> new AbstractMap.SimpleImmutableEntry<>(l, r))
        .collect(collector.collectorOf(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 1 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 10.0
   */
  public final String format(long arg) {
    checkFormatArgs(1);
    return fragments.get(0) + arg + fragments.get(1);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 1 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(Object arg) {
    checkFormatArgs(1);
    return fragments.get(0) + arg + fragments.get(1);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 2 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(Object a, Object b) {
    checkFormatArgs(2);
    return fragments.get(0) + a + fragments.get(1) + b + fragments.get(2);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 3 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(Object a, Object b, Object c) {
    checkFormatArgs(3);
    return fragments.get(0) + a + fragments.get(1) + b + fragments.get(2) + c + fragments.get(3);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 4 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(
      Object a, Object b, Object c, Object d) {
    checkFormatArgs(4);
    return fragments.get(0)
        + a
        + fragments.get(1)
        + b
        + fragments.get(2)
        + c
        + fragments.get(3)
        + d
        + fragments.get(4);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 5 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator, and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(
      Object a,
      Object b,
      Object c,
      Object d,
      Object e) {
    checkFormatArgs(5);
    return fragments.get(0)
        + a
        + fragments.get(1)
        + b
        + fragments.get(2)
        + c
        + fragments.get(3)
        + d
        + fragments.get(4)
        + e
        + fragments.get(5);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 6 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator, and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(
      Object a,
      Object b,
      Object c,
      Object d,
      Object e,
      Object f) {
    checkFormatArgs(6);
    return fragments.get(0)
        + a
        + fragments.get(1)
        + b
        + fragments.get(2)
        + c
        + fragments.get(3)
        + d
        + fragments.get(4)
        + e
        + fragments.get(5)
        + f
        + fragments.get(6);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 7 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator, and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(
      Object a,
      Object b,
      Object c,
      Object d,
      Object e,
      Object f,
      Object g) {
    checkFormatArgs(7);
    return fragments.get(0)
        + a
        + fragments.get(1)
        + b
        + fragments.get(2)
        + c
        + fragments.get(3)
        + d
        + fragments.get(4)
        + e
        + fragments.get(5)
        + f
        + fragments.get(6)
        + g
        + fragments.get(7);
  }

  /**
   * Returns the string formatted with placeholders filled using the provided 8 placeholder args.
   *
   * <p>While similar in functionality to {@link String#format}, StringFormat is safer to be used as
   * a class constant, because ErrorProne will check at compile-time if the format arguments are
   * passed in the wrong order.
   *
   * <p>Performance-wise, it's close to native string concatenation using the '+' operator, and is
   * about 6 times faster than {@link String#format}.
   *
   * @since 9.3
   */
  public final String format(
      Object a,
      Object b,
      Object c,
      Object d,
      Object e,
      Object f,
      Object g,
      Object h) {
    checkFormatArgs(8);
    return fragments.get(0)
        + a
        + fragments.get(1)
        + b
        + fragments.get(2)
        + c
        + fragments.get(3)
        + d
        + fragments.get(4)
        + e
        + fragments.get(5)
        + f
        + fragments.get(6)
        + g
        + fragments.get(7)
        + h
        + fragments.get(8);
  }

  /**
   * Returns the string formatted with placeholders filled using {@code args}. This is the reverse
   * operation of the {@code parse(...)} methods. For example:
   *
   * <pre>{@code
   * new StringFormat("Hello {who}").format("world")
   *     => "Hello world"
   * }</pre>
   *
   * @throws IllegalArgumentException if the number of arguments doesn't match that of the
   *     placeholders
   */
  public final String format(Object... args) {
    checkFormatArgs(args);
    StringBuilder builder = new StringBuilder().append(fragments.get(0));
    for (int i = 0; i < args.length; i++) {
      builder.append(args[i]).append(fragments.get(i + 1));
    }
    return builder.toString();
  }

  /** Returns the string format. */
  @Override public String toString() {
    return format;
  }

  private <R> Optional<R> parseGreedyExpecting(
      int cardinality, String input, Collector<? super String, ?, R> collector) {
    requireNonNull(input);
    checkPlaceholderCount(cardinality);
    // To match backwards, we reverse the input as well as the format string.
    // After the matching is done, reverse the results back.
    return internalParse(
            reverse(input),
            reverse(fragments).stream().map(s -> reverse(s)).collect(toImmutableList()),
            reverse(toCapture))
        .map(
            captured ->
                reverse(captured).stream()
                    .map(
                        sub -> { // Return the original (unreversed) substring
                          int forwardIndex = input.length() - (sub.index() + sub.length());
                          return input.substring(forwardIndex, forwardIndex + sub.length());
                        })
                    .collect(collector));
  }

  private <R> Optional<R> parseExpecting(
      int cardinality, String input, Collector<? super String, ?, R> collector) {
    requireNonNull(input);
    checkPlaceholderCount(cardinality);
    return parseAsList(input).map(values -> values.stream().map(Substring.Match::toString).collect(collector));
  }

  /**
   * Parses {@code input} with the number of placeholders equal to {@code cardinality}, then
   * collects the placeholder values using {@code collector}.
   *
   * @throws IllegalArgumentException if input fails parsing
   */
  private <R> R parseOrThrowExpecting(
      int cardinality, String input, Collector<? super String, ?, R> collector) {
    requireNonNull(input);
    checkPlaceholderCount(cardinality);
    List<Substring.Match> values =
        parseAsList(input)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        new StringFormat("input '{input}' doesn't match format string '{format}'")
                            .format(input, format)));
    R result = values.stream().map(Substring.Match::toString).collect(collector);
    if (result == null) {
      throw new NullPointerException(
          String.format(
              "mapper function returned null when matching input '%s' against format string '%s'",
              input, format));
    }
    return result;
  }

  private Stream<List<Substring.Match>> matchRepeatedly(
      String input, IndexRangeConsumer matchRanges) {
    if (format.isEmpty()) {
      return IntStream.range(0, input.length() + 1)
          .mapToObj(
              i -> {
                matchRanges.acceptIndexRange(i, i);
                return Collections.emptyList();
              });
    }
    int numPlaceholders = numPlaceholders();
    return MoreStreams.whileNotNull(
        new Supplier<List<Substring.Match>>() {
          private int inputIndex = 0;
          private boolean done = false;

          @Override
          public List<Substring.Match> get() {
            if (done) {
              return null;
            }
            inputIndex = input.indexOf(fragments.get(0), inputIndex);
            if (inputIndex < 0) {
              return null;
            }
            final int startIndex = inputIndex;
            inputIndex += fragments.get(0).length();
            List<Substring.Match> builder = new ArrayList<>(numCapturingPlaceholders);
            for (int i = 1; i <= numPlaceholders; i++) {
              String literal = fragments.get(i);
              // Always search left-to-right. The last placeholder at the end of format is suffix.
              Substring.Pattern literalLocator =
                  i == numPlaceholders && fragments.get(i).isEmpty()
                      ? Substring.END
                      : first(fragments.get(i));
              Substring.Match placeholder = before(literalLocator).match(input, inputIndex);
              if (placeholder == null) {
                return null;
              }
              if (toCapture.get(i - 1)) {
                builder.add(placeholder);
              }
              inputIndex = placeholder.index() + placeholder.length() + literal.length();
            }
            if (inputIndex == input.length()) {
              done = true;
            }
            matchRanges.acceptIndexRange(startIndex, inputIndex);
            return unmodifiableList(builder);
          }
        });
  }

  private <R> Stream<R> scanExpecting(
      int cardinality, String input, Collector<? super String, ?, R> collector) {
    requireNonNull(input);
    checkPlaceholderCount(cardinality);
    return scanAsLists(input)
        .map(values -> values.stream().map(Substring.Match::toString).collect(collector))
        .filter(v -> v != null);
  }

  private int numPlaceholders() {
    return fragments.size() - 1;
  }

  private void checkUnformattability() {
    for (int i = 1; i < numPlaceholders(); i++) {
      if (this.fragments.get(i).isEmpty()) {
        throw new IllegalArgumentException("Placeholders cannot be next to each other: " + format);
      }
    }
  }

  private void checkPlaceholderCount(int expected) {
    if (numCapturingPlaceholders != expected) {
      throw new IllegalArgumentException(
          String.format(
              "format string has %s placeholders; %s expected.",
              numCapturingPlaceholders,
              expected));
    }
  }

  final void checkFormatArgs(Object[] args) {
    checkFormatArgs(args.length);
  }

  private void checkFormatArgs(int argsCount) {
    if (argsCount != numPlaceholders()) {
      throw new IllegalArgumentException(
          String.format(
              "format string expects %s placeholders, %s provided",
              numPlaceholders(),
              argsCount));
    }
  }

  static String reverse(String s) {
    if (s.length() <= 1) {
      return s;
    }
    StringBuilder builder = new StringBuilder(s.length());
    for (int i = s.length() - 1; i >= 0; i--) {
      builder.append(s.charAt(i));
    }
    return builder.toString();
  }

  static <T> List<T> reverse(List<T> list) {
    if (list.size() <= 1) {
      return list;
    }
    return new AbstractList<T>() {
      @Override public int size() {
        return list.size();
      }
      @Override public T get(int i) {
        return list.get(list.size() - 1 - i);
      }
    };
  }

  private static <T> List<T> chop(List<T> list) {
    return list.subList(0, list.size() - 1);
  }

  private interface IndexRangeConsumer {
    void acceptIndexRange(int startIndex, int endIndex);
  }
}

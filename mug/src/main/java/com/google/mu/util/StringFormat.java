package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static com.google.mu.util.stream.MoreCollectors.combining;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;
import com.google.mu.util.stream.MoreStreams;

/**
 * A string parser to extract placeholder values from input strings according to a format string.
 * For example:
 *
 * <pre>{@code
 * return new StringFormat("{address}+{subaddress}@{domain}")
 *     .parse("my-ldap+test@google.com", (address, subaddress, domain) -> ...);
 * }</pre>
 *
 * <p>An ErrorProne check is provided to guard against incorrect lambda parameters to the {@code
 * parse()}, {@code parseOrThrow()}, {@code parseGreedy()} and {@code scan()} methods. Both the
 * number of parameters and the lambda parameter names are checked to ensure they match the format
 * string. The arguments passed to the {@link #format} are also checked. If you use bazel, the check
 * is automatically enforced.
 *
 * <p>Starting from 6.7, if a certain placeholder is uninteresting and you'd rather not name it, you
 * can use the special {@code ...} placeholder and then you won't need to assign a lambda variable
 * to capture it:
 *
 * <pre>{@code
 * return new StringFormat("{...}+{subaddress}@{domain}")
 *     .parse("my-ldap+test@google.com", (subaddress, domain) -> ...);
 * }</pre>
 *
 * <p>Note that except the placeholders, characters in the format string are treated as literals.
 * This works better if your pattern is close to free-form text with characters like '.', '?', '(',
 * '|' and whatnot because you don't need to escape them. On the other hand, the literal characters
 * won't offer regex functionalities you get from {@code (\w+)}, {@code (foo|bar)} etc.
 *
 * <p>In the face of ambiguity, the {@code parse()} methods can be lossy. Consider the format string
 * of {@code String.format("I bought %s and %s", "apples and oranges", "chips")}, it returns {@code
 * "I bought apples and oranges and chips"}; but the following parsing code will incorrectly parse
 * "apples" as "{fruits}" and "oranges and chips" as "{snacks}":
 *
 * <pre>{@code
 * new StringFormat("I bought {fruits} and {snacks}")
 *     .parse("I bought apples and oranges and chips", (fruits, snacks) -> ...);
 * }</pre>
 *
 * As such, only use this class on trusted input strings (i.e. not user inputs). And use regex
 * instead to better deal with ambiguity.
 *
 * <p>All the {@code parse()} methods attempt to match the entire input string from beginning to
 * end. If you need to find the string format as a substring anywhere inside the input string, or
 * need to find repeated occurrences from the input string, use the {@code scan()} methods instead.
 * Tack on {@code .findFirst()} on the returned lazy stream if you only care to find a single
 * occurrence.
 *
 * <p>This class is immutable and pre-compiles the format string at constructor time so that the
 * {@code parse()} and {@code scan()} methods will be more efficient.
 *
 * @since 6.6
 */
public final class StringFormat {
  private static final Substring.RepeatingPattern PLACEHOLDERS =
      Substring.consecutive(c -> c != '{' && c != '}') // Find the inner-most pairs of curly braces.
          .immediatelyBetween("{", INCLUSIVE, "}", INCLUSIVE)
          .repeatedly();
  private final String format;
  private final List<String> fragments; // The string literals between placeholders
  private final List<Boolean> toCapture;
  private final int numCapturingPlaceholders;
  private final CharPredicate requiredChars; // null for unconstrained matches

  /**
   * Returns a strict StringFormat according to the {@code format} string. All placeholder values
   * must be <em>non-empty</em> and all placeholder value characters must match {@code
   * requiredChars}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StringFormat userFormat =
   *     StringFormat.strict("user: {user_id}", CharMatcher.inRange('0', '9'));
   * userFormat.parse("user: 123", id -> Integer.parseInt(id))  => Optional.of(123)
   * userFormat.parse("user: xyz", ...) => empty()
   * userFormat.parse("user: ", ...)    => empty()
   * }</pre>
   *
   * <p>Note that {@code requiredChars} applies to all placeholders. If you need to apply
   * constraints on an individual placeholder, consider filtering in the lambda by returning null if
   * the placeholder value isn't valid.
   *
   * <p>Parameters passed to {@link #format} are not checked so it's possible that {@code format()}
   * returns a string not parseable by the same {@code StringFormat} strict instance.
   *
   * @since 6.7
   * @deprecated Consider manually checking the placeholder values in the lambda
   */
  @Deprecated
  public static StringFormat strict(String format, CharPredicate requiredChars) {
    return new StringFormat(format, requireNonNull(requiredChars));
  }

  /**
   * Returns a {@link Substring.Pattern} spanning the substring matching {@code format}. For
   * example, {@code StringFormat.span("projects/{project}/")} is equivalent to {@code
   * spanningInOrder("projects/", "/")}.
   *
   * <p>Useful if you need a Substring.Pattern for purposes such as composition, but prefer a more
   * self-documenting syntax. The placeholder names in the format string don't affect runtime
   * semantics, but using meaningful names improves readability.
   *
   * @since 6.7
   */
  public static Substring.Pattern span(String format) {
    List<String> delimiters =
        PLACEHOLDERS.split(format).map(Substring.Match::toString).collect(toList());
    return delimiters.size() > 1 && delimiters.get(delimiters.size() - 1).isEmpty()
        // If the last placeholder is at end, treat it as anchoring to the end.
        ? spanInOrder(delimiters.subList(0, delimiters.size() - 1)).toEnd()
        : spanInOrder(delimiters);
  }

  private static Substring.Pattern spanInOrder(List<String> goalPosts) {
    return goalPosts.stream()
        .skip(1)
        .map(Substring::first)
        .reduce(Substring.first(goalPosts.get(0)), Substring.Pattern::extendTo);
  }

  /**
   * Constructs a StringFormat with placeholders in the syntax of {@code "{foo}"}. For example:
   *
   * <pre>{@code
   * new StringFormat("Dear {customer}, your confirmation number is {conf#}");
   * }</pre>
   *
   * <p>Nesting "{placeholder}" syntax inside literal curly braces is supported. For example, you
   * could use a format like: {@code "{name: {name}, age: {age}}"}, and it will be able to parse
   * record-like strings such as "{name: Joe, age: 25}".
   *
   * @param format the template format with placeholders
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringFormat(String format) {
    this(format, null);
  }

  private StringFormat(String format, CharPredicate requiredChars) {
    Stream.Builder<String> delimiters = Stream.builder();
    Stream.Builder<Boolean> toCapture = Stream.builder();
    PLACEHOLDERS.split(format).forEachOrdered(
        literal -> {
          delimiters.add(literal.toString());
          toCapture.add(!format.startsWith("...}", literal.index() + literal.length() + 1));
        });
    this.format = format;
    this.fragments = delimiters.build().collect(toImmutableList());
    this.toCapture = chop(toCapture.build().collect(toImmutableList()));
    this.numCapturingPlaceholders =
        this.fragments.size() - 1 - (int) this.toCapture.stream().filter(c -> !c).count();
    this.requiredChars = requiredChars;
  }

  /**
   * Parses {@code input} and applies the {@code mapper} function with the single placeholder value
   * in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: {job_id})").parse(input, jobId -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly one placeholder.
   */
  public <R> Optional<R> parse(String input, Function<? super String, ? extends R> mapper) {
    return parseExpecting(1, input, onlyElement(mapper));
  }

  /**
   * Parses {@code input} and applies {@code mapper} with the two placeholder values
   * in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '{id}', error code: {code})")
   *     .parse(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly two placeholders.
   */
  public <R> Optional<R> parse(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    return parseExpecting(2, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>3</em> placeholder values in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '{job_id}', error code: {code}, error details: {details})")
   *     .parse(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 3 placeholders.
   */
  public <R> Optional<R> parse(String input, Ternary<? super String, ? extends R> mapper) {
    return parseExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>4</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 4 placeholders.
   */
  public <R> Optional<R> parse(String input, Quarternary<? super String, ? extends R> mapper) {
    return parseExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>5</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 5 placeholders.
   */
  public <R> Optional<R> parse(String input, Quinary<? super String, ? extends R> mapper) {
    return parseExpecting(5, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction)}, but parses {@code input} and applies {@code
   * mapper} with the <em>6</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 6 placeholders.
   */
  public <R> Optional<R> parse(String input, Senary<? super String, ? extends R> mapper) {
    return parseExpecting(6, input, combining(mapper));
  }

  /**
   * Parses {@code input} against the pattern.
   *
   * <p>Returns an immutable list of placeholder values in the same order as {@link #placeholders},
   * upon success; otherwise returns empty.
   *
   * <p>The {@link Substring.Match} result type allows caller to inspect the characters around each
   * match, or to access the raw index in the input string.
   */
  public Optional<List<Substring.Match>> parse(String input) {
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
      if (requiredChars != null && !isValidPlaceholderValue(placeholder)) {
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
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, Function<? super String, R> mapper) {
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
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, BiFunction<? super String, ? super String, R> mapper) {
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
   * <p>Unlike {@link #parse(String, Ternary)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, Ternary)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 3 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, Ternary<? super String, R> mapper) {
    return parseOrThrowExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>4</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, Quarternary)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, Quarternary)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 4 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, Quarternary<? super String, R> mapper) {
    return parseOrThrowExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parseOrThrow(String, BiFunction)}, but parses {@code input} and applies
   * {@code mapper} with the <em>5</em> placeholder values in this string format.
   *
   * <p>Unlike {@link #parse(String, Quinary)}, {@code IllegalArgumentException} is thrown if the
   * input string doesn't match the string format. The error message will include both the input
   * string and the format string for ease of debugging, but is otherwise generic. If you need a
   * different exception type, or need to customize the error message, consider using {@link
   * parse(String, Quinary)} instead and call {@link Optional#orElseThrow} explicitly.
   *
   * @return the return value of the {@code mapper} function applied on the extracted placeholder
   *     values.
   * @throws IllegalArgumentException if the input string doesn't match the string format, or if the
   *     format string doesn't have exactly 5 placeholders
   * @throws NullPointerException if any of the parameter is null or {@code mapper} returns null.
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, Quinary<? super String, R> mapper) {
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
   * @since 6.7
   */
  public <R> R parseOrThrow(String input, Senary<? super String, R> mapper) {
    return parseOrThrowExpecting(6, input, combining(mapper));
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
   * @since 6.7
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
   * @since 6.7
   */
  public final <R> Optional<R> parseGreedy(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    return parseGreedyExpecting(2, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, Ternary)}, parses {@code input} and applies {@code mapper}
   * with the 3 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 3 placeholders.
   * @since 6.7
   */
  public final <R> Optional<R> parseGreedy(
      String input, Ternary<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(3, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, Quarternary)}, parses {@code input} and applies {@code mapper}
   * with the 3 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 4 placeholders.
   * @since 6.7
   */
  public final <R> Optional<R> parseGreedy(
      String input, Quarternary<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(4, input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, Quinary)}, parses {@code input} and applies {@code mapper}
   * with the 5 placeholder values in this format string, but matches the placeholders backwards
   * from the end to the beginning of the input string.
   *
   * <p>This is also equivalent to allowing the left placeholder to match greedily, while still
   * requiring the remaining placeholder(s) to be matched.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if {@code
   *     input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if the format string doesn't have exactly 5 placeholders.
   * @since 6.7
   */
  public final <R> Optional<R> parseGreedy(
      String input, Quinary<? super String, ? extends R> mapper) {
    return parseGreedyExpecting(5, input, combining(mapper));
  }

  /**
   * Returns true if this format matches {@code input} entirely.
   *
   * @since 6.7
   */
  public boolean matches(String input) {
    return parse(input).isPresent();
  }

  /**
   * Scans the {@code input} string and extracts all matched placeholders in this string format.
   *
   * <p>unlike {@link #parse(String)}, the input string isn't matched entirely:
   * the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   */
  public Stream<List<Substring.Match>> scan(String input) {
    requireNonNull(input);
    if (format.isEmpty()) {
      return requiredChars == null
          ? Stream.generate(() -> Collections.<Substring.Match>emptyList()).limit(input.length() + 1)
          : Stream.empty();
    }
    int numPlaceholders = numPlaceholders();
    Stream<List<Substring.Match>> groups = MoreStreams.whileNotNull(
        new Supplier<List<Substring.Match>>() {
          private int inputIndex = 0;
          private boolean done = false;

          @Override public List<Substring.Match> get() {
            if (done) {
              return null;
            }
            inputIndex = input.indexOf(fragments.get(0), inputIndex);
            if (inputIndex < 0) {
              return null;
            }
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
            return unmodifiableList(builder);
          }
        });
    if (requiredChars == null) {
      return groups;
    }
    return groups.filter(matches -> matches.stream().allMatch(this::isValidPlaceholderValue));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the single placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("/home/usr/myname/{file_name}\n")
   *     .scan(multiLineInput, fileName -> ...);
   * }</pre>
   *
   * <p>unlike {@link #parse(String, Function)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If the
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(String input, Function<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(1);
    return scanAndCollect(input, onlyElement(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the two placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("[key={key}, value={value}]")
   *     .repeatedly()
   *     .parse(input, (key, value) -> ...);
   * }</pre>
   *
   * <p>unlike {@link #parse(String, BiFunction)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(2);
    return scanAndCollect(input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the 3 placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("[{lhs} + {rhs} = {result}]")
   *     .repeatedly()
   *     .parse(input, (lhs, rhs, result) -> ...);
   * }</pre>
   *
   * <p>unlike {@link #parse(String, Ternary)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(String input, Ternary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(3);
    return scanAndCollect(input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the 4 placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>unlike {@link #parse(String, Quarternary)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(String input, Quarternary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(4);
    return scanAndCollect(input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the 5 placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>unlike {@link #parse(String, Quinary)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(String input, Quinary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(5);
    return scanAndCollect(input, combining(mapper));
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the 6 placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>unlike {@link #parse(String, Senary)}, the input string isn't matched
   * entirely: the pattern doesn't have to start from the beginning, and if there are some remaining
   * characters that don't match the pattern any more, the stream stops. In particular, if there
   * is no match, empty stream is returned.
   *
   * <p>By default, placeholders are allowed to be matched against an empty string. If a certain
   * placeholder isn't expected to be empty, consider filtering it out by returning null from
   * the {@code mapper} function, which will then be ignored in the result stream.
   */
  public <R> Stream<R> scan(String input, Senary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(6);
    return scanAndCollect(input, combining(mapper));
  }

  /**
   * Returns the string formatted with placeholders filled using {@code args}.
   * This is the reverse operation of the {@code parse(...)} methods. For example:
   *
   * <pre>{@code
   * new StringFormat("Hello {who}").format("world")
   *     => "Hello world"
   * }</pre>
   *
   * @throws IllegalArgumentException if the number of arguments doesn't match that of the placeholders
   */
  public String format(Object... args) {
    if (args.length != numPlaceholders()) {
      throw new IllegalArgumentException(
          String.format(
              "format string expects %s placeholders, %s provided",
              numPlaceholders(),
              args.length));
    }
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

  private <R> Optional<R> parseExpecting(int cardinality, String input, Collector<? super String, ?, R> collector) {
    requireNonNull(input);
    checkPlaceholderCount(cardinality);
    return parse(input).map(values -> values.stream().map(Substring.Match::toString).collect(collector));
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
        parse(input)
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

  private <R> Stream<R> scanAndCollect(String input, Collector<? super String, ?, R> collector) {
    return scan(input)
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

  private boolean isValidPlaceholderValue(CharSequence chars) {
    return requiredChars == null || (chars.length() > 0 && requiredChars.matchesAllOf(chars));
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
}

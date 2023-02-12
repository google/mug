package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.spanningInOrder;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreCollectors.combining;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
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
 * A (lossy) reverse operation of {@link String#format} to extract placeholder values from input
 * strings according to a format string. For example:
 *
 * <pre>{@code
 * return new StringFormat("Dear {customer}: {question}?")
 *     .parse(input, (customer, question) -> ...);
 * }</pre>
 *
 * <p>Note that other than the placeholders, characters in the format string are treated as
 * literals. This works better if your format string is close to free-form text with characters like
 * '.', '?', '(', '|' and what not because you won't have to escape them. On the other hand, it
 * won't work for more sophisticated patterns where regex modifiers and quantifiers are needed.
 *
 * <p>In the face of ambiguity, the {@code parse()} methods can be lossy. Consider the format string
 * of {@code String.format("I bought %s and %s", "apples and oranges", "chips")}, it returns {@code
 * "I bought apples and oranges and chips"}; but the following parsing code will incorrectly return
 * {@code Map.of("{fruits}", "apples", "{snacks}", "oranges and chips")}:
 *
 * <pre>{@code
 * new StringFormat("I bought {fruits} and {snacks}")
 *     .parse("I bought apples and oranges and chips", (fruits, snacks) -> ...);
 * }</pre>
 *
 * As such, only use this class on trusted input strings (i.e. not user inputs).
 * And use regex instead to better deal with ambiguity.
 *
 * <p>All the {@code parse()} methods attempt to match the entire input string. If you need to find
 * the string format as a substring anywhere inside the input string, or need to find repeated
 * occurrences from the input string, use the {@code scan()} methods instead. Tack on
 * {@code .findFirst()} on the returned lazy stream if you only care to find a single occurrence.
 *
 * <p>This class is immutable and pre-compiles the format string at constructor time so that the
 * {@code parse()} methods will be more efficient.
 *
 * @since 6.6
 */
public final class StringFormat {
  private final String format;
  private final List<String> literals; // The string literals between placeholders

  /**
   * Constructs a StringFormat with placeholders in the syntax of {@code "{foo}"}. For example:
   *
   * <pre>{@code
   * new StringFormat("Dear {customer}, your confirmation number is {conf#}");
   * }</pre>
   *
   * <p>For alternative placeholders, such as "%s", use {@link
   * #StringFormat(String, Substring.RepeatingPattern)} instead:
   *
   * <pre>{@code
   * new StringFormat("%s+%s@%s", first("%s").repeatedly())
   * }</pre>
   *
   * @param format the template format with placeholders
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringFormat(String format) {
    this(format, spanningInOrder("{", "}").repeatedly());
  }

  /**
   * Constructs a StringFormt using {@code placeholderVariablesPattern} to detect placeholders
   * in the {@code format} string. For example, the following code uses "%s" as the placeholder:
   *
   * <pre>{@code
   * new StringFormat("Hi %s, my name is %s", first("%s").repeatedly());
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringFormat(String format, Substring.RepeatingPattern placeholderVariablesPattern) {
    this.format = format;
    this.literals =
        placeholderVariablesPattern.split(format).map(Substring.Match::toString).collect(toImmutableList());
    for (int i = 1; i < numPlaceholders(); i++) {
      if (literals.get(i).isEmpty()) {
        throw new IllegalArgumentException("Placeholders cannot be next to each other: " + format);
      }
    }
  }

  /**
   * Parses {@code input} and applies the {@code mapper} function with the single placeholder value
   * in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: %s)").parse(input, jobId -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly one placeholder.
   */
  public final <R> Optional<R> parse(String input, Function<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(1);
    return parseAndCollect(input, onlyElement(mapper));
  }

  /**
   * Parses {@code input} and applies {@code mapper} with the two placeholder values
   * in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '%s', error code: %s)")
   *     .parse(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly two placeholders.
   */
  public final <R> Optional<R> parse(
      String input, BiFunction<? super String, ? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(2);
    return parseAndCollect(input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * mapper} with the <em>3</em> placeholder values in this string format.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '%s', error code: %s, error details: %s)")
   *     .parse(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 3 placeholders.
   */
  public final <R> Optional<R> parse(String input, Ternary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(3);
    return parseAndCollect(input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * mapper} with the <em>4</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 4 placeholders.
   */
  public final <R> Optional<R> parse(String input, Quarternary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(4);
    return parseAndCollect(input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * mapper} with the <em>5</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 5 placeholders.
   */
  public final <R> Optional<R> parse(String input, Quinary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(5);
    return parseAndCollect(input, combining(mapper));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * mapper} with the <em>6</em> placeholder values in this string format.
   *
   * @return the return value of the {@code mapper} function if not null. Returns empty if
   *     {@code input} doesn't match the format, or {@code mapper} returns null.
   * @throws IllegalArgumentException if or the format string doesn't have exactly 6 placeholders.
   */
  public final <R> Optional<R> parse(String input, Senary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(6);
    return parseAndCollect(input, combining(mapper));
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
    if (!input.startsWith(literals.get(0))) {  // first literal is the prefix
      return Optional.empty();
    }
    final int numPlaceholders = numPlaceholders();
    List<Substring.Match> builder = new ArrayList<>(numPlaceholders);
    int inputIndex = literals.get(0).length();
    for (int i = 1; i <= numPlaceholders; i++) {
      // subsequent literals are searched left-to-right; last literal is the suffix.
      Substring.Pattern trailingLiteral =
          i < numPlaceholders ? first(literals.get(i)) : suffix(literals.get(i));
      Substring.Match placeholder = before(trailingLiteral).match(input, inputIndex);
      if (placeholder == null) {
        return Optional.empty();
      }
      builder.add(placeholder);
      inputIndex = placeholder.index() + placeholder.length() + literals.get(i).length();
    }
    return optional(inputIndex == input.length(), unmodifiableList(builder));
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
    int numPlaceholders = numPlaceholders();
    return MoreStreams.whileNotNull(
        new Supplier<List<Substring.Match>>() {
          private int inputIndex = 0;
          private boolean done = false;

          @Override public List<Substring.Match> get() {
            if (done) {
              return null;
            }
            inputIndex = input.indexOf(literals.get(0), inputIndex);
            if (inputIndex < 0) {
              return null;
            }
            inputIndex += literals.get(0).length();
            List<Substring.Match> builder = new ArrayList<>(numPlaceholders);
            for (int i = 1; i <= numPlaceholders; i++) {
              String literal = literals.get(i);
              // Always search left-to-right. The last placeholder at the end of format is suffix.
              Substring.Pattern literalLocator =
                  i == numPlaceholders && literals.get(i).isEmpty()
                      ? Substring.END
                      : first(literals.get(i));
              Substring.Match placeholder = before(literalLocator).match(input, inputIndex);
              if (placeholder == null) {
                return null;
              }
              builder.add(placeholder);
              inputIndex = placeholder.index() + placeholder.length() + literal.length();
            }
            if (inputIndex == input.length()) {
              done = true;
            }
            return unmodifiableList(builder);
          }
        });
  }

  /**
   * Scans the {@code input} string and extracts all matches of this string format.
   * Returns the lazy stream of non-null results from passing the single placeholder values to
   * the {@code mapper} function for each iteration, with null results skipped.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("/home/usr/myname/%s\n")
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
  public final <R> Stream<R> scan(String input, Function<? super String, ? extends R> mapper) {
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
   * new StringFormat("[key=%s, value=%s]")
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
  public final <R> Stream<R> scan(
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
   * new StringFormat("[%s + %s = %s]")
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
  public final <R> Stream<R> scan(String input, Ternary<? super String, ? extends R> mapper) {
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
  public final <R> Stream<R> scan(String input, Quarternary<? super String, ? extends R> mapper) {
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
  public final <R> Stream<R> scan(String input, Quinary<? super String, ? extends R> mapper) {
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
  public final <R> Stream<R> scan(String input, Senary<? super String, ? extends R> mapper) {
    requireNonNull(input);
    requireNonNull(mapper);
    checkPlaceholderCount(6);
    return scanAndCollect(input, combining(mapper));
  }

  /** Returns the string format. */
  @Override public String toString() {
    return format;
  }

  private <R> Optional<R> parseAndCollect(String input, Collector<? super String, ?, R> collector) {
    return parse(input).map(values -> values.stream().map(Substring.Match::toString).collect(collector));
  }

  private <R> Stream<R> scanAndCollect(String input, Collector<? super String, ?, R> collector) {
    return scan(input)
        .map(values -> values.stream().map(Substring.Match::toString).collect(collector))
        .filter(v -> v != null);
  }

  private int numPlaceholders() {
    return literals.size() - 1;
  }

  private void checkPlaceholderCount(int expected) {
    if (numPlaceholders() != expected) {
      throw new IllegalArgumentException(
          String.format(
              "format string has %s placeholders; %s expected.",
              numPlaceholders(),
              expected));
    }
  }

}

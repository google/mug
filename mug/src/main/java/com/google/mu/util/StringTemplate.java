package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreCollectors.asIn;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.mu.function.Quarternary;
import com.google.mu.function.Quinary;
import com.google.mu.function.Senary;
import com.google.mu.function.Ternary;
import com.google.mu.util.stream.BiStream;

/**
 * A utility class to extract placeholder values from input strings based on a template.
 *
 * <p>If you have a simple template with placeholder names like "{recipient}", "{question}",
 * you can then parse out the placeholder values from strings like "To Charlie: How are you?":
 *
 * <pre>{@code
 * StringTemplate template = new StringTemplate("To {recipient}: {question}?");
 * Map<String, String> placeholderValues = template.parse("To Charlie: How are you?").toMap();
 * assertThat(placeholderValues)
 *     .containsExactly("{recipient}", "Charlie", "{question}", "How are you");
 * }</pre>
 *
 * <p>It's not required that the placeholder variable names must be distinct or even logically named.
 * Sometimes, it may be easier to directly collect the placeholder values using lambda:
 *
 * <pre>{@code
 * return new StringTemplate("To {}: {}?")
 *     .parse(input, (recipient, question) -> ...));
 * }</pre>
 *
 * <p>Note that other than the placeholders, characters in the template are treated as literals.
 * This makes it simpler compared to regex if your pattern is close to free-form text with
 * characters like '.', '?', '(', '|' and what not. On the other hand, if you need to use regex
 * modifiers and quantifiers to express complex pattern, this class is not the right tool for the job.
 *
 * @since 6.6
 */
public final class StringTemplate {
  private final String format;
  private final List<Substring.Match> placeholders;
  private final List<String> placeholderVariableNames;

  /**
   * In the input string, a placeholder value is found from the current position until the next
   * text literal, which includes all literal characters found in the template between the previous
   * placeholder variable (or the BEGINNING) and the next placeholder variable (or the END).
   */
  private final List<String> literals;
  private final List<Substring.Pattern> literalLocators;

  /**
   * Constructs a StringTemplate
   *
   * @param format the template format with placeholders in the format of {@code "{placeholder_name}"}
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringTemplate(String format) {
    this(format, Substring.spanningInOrder("{", "}").repeatedly());
  }

  /**
   * Constructs a StringTemplate. By default, {@code new StringTemplate(format)} assumes placeholders
   * to be enclosed by curly braces. For example: "Hello {customer}". If you need different placeholder
   * syntax, for example, to emulate Java's "%s", you can use:
   *
   * <pre>{@code new StringTemplate("Hi %s, my name is %s", first("%s").repeatedly())}</pre>
   *
   * @param format the template format with placeholders
   * @param placeholderVariablePattern placeholders in {@code format}.
   *     For example: {@code first("%s").repeatedly()}.
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringTemplate(String format, Substring.RepeatingPattern placeholderVariablesPattern) {
    this.format = format;
    this.placeholders = placeholderVariablesPattern.match(format).collect(toImmutableList());
    this.placeholderVariableNames =
        placeholders.stream().map(Substring.Match::toString).collect(toImmutableList());
    List<String> literals = new ArrayList<>(placeholders.size() + 1);
    List<Substring.Pattern> literalLocators = new ArrayList<>(literals.size());
    populateLiterals(format, placeholders, literals, literalLocators);
    this.literals = unmodifiableList(literals);
    this.literalLocators = unmodifiableList(literalLocators);
  }

  /**
   * Returns a StringTemplate of {@code format} with {@code %s} placeholders
   * (no other String format specifiers are supported).
   *
   * <p>For example: <pre>{@code
   * StringTemplate.ofFormatString("I bought %s and %s at price of %s")
   *     .parse("I bought ice cream and beer at price of $15.4", (a, b, price) -> ...));
   * }</pre>
   */
  public static StringTemplate ofFormatString(String format) {
    return new StringTemplate(format, first("%s").repeatedly());
  }

  /**
   * Parses {@code input} and extracts all placeholder name-value pairs in a BiStream,
   * in the same order as {@link #placeholders}.
   *
   * <p>The entire {@code input} string is matched against the template. So if there are trailing
   * characters after what's captured by the template, the parsing will fail. If you need to allow
   * trailing characters, consider adding a trailing placeholder such as "{*}" to capture them.
   * You can then filter them out using {@code .skipKeysIf("{*}"::equals)}.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template
   */
  public BiStream<String, String> parse(String input) {
    return BiStream.zip(placeholderVariableNames.stream(),  parsePlaceholderValues(input));
  }

  /**
   * Parses {@code input} and applies {@code function} with the single placeholder value
   * in this template.
   *
   * <p>For example: <pre>{@code
   * new StringTemplate("Job failed (job id: {})").parse(input, jobId -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly one placeholder.
   */
  public <R> R parse(String input, Function<? super String, R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Parses {@code input} and applies {@code function} with the two placeholder values
   * in this template.
   *
   * <p>For example: <pre>{@code
   * new StringTemplate("Job failed (job id: '{}', error code: {})")
   *     .parse(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly two placeholders.
   */
  public <R> R parse(String input, BiFunction<? super String, ? super String, R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * function} with the <em>3</em> placeholder values in this template.
   *
   * <p>For example: <pre>{@code
   * new StringTemplate("Job failed (job id: '{}', error code: {}, error details: {})")
   *     .parse(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 3 placeholders.
   */
  public <R> R parse(String input, Ternary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * function} with the <em>4</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 4 placeholders.
   */
  public <R> R parse(String input, Quarternary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * function} with the <em>5</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 5 placeholders.
   */
  public <R> R parse(String input, Quinary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * function} with the <em>6</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 6 placeholders.
   */
  public <R> R parse(String input, Senary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(asIn(function));
  }

  /**
   * Matches {@code input} against the pattern.
   *
   * <p>Returns an immutable list of placeholder values in the same order as {@link #placeholders},
   * upon success; otherwise returns empty.
   *
   * <p>The {@link Substring.Match} result type allows caller to inspect the characters around each
   * match, or to access the raw index in the input string.
   */
  public Optional<List<Substring.Match>> match(String input) {
    List<Substring.Match> builder = new ArrayList<>(placeholders.size());
    int inputIndex = 0;
    for (int i = 0; i < literalLocators.size(); i++) {
      Substring.Match delimiter = literalLocators.get(i).match(input, inputIndex);
      if (delimiter == null) return Optional.empty();
      if (i > 0) {
        builder.add(
            Substring.Match.nonBacktrackable(input, inputIndex, delimiter.index() - inputIndex));
      }
      inputIndex = delimiter.index() + delimiter.length();
    }
    return optional(inputIndex == input.length(), unmodifiableList(builder));
  }

  /**
   * Formats this template with the provided {@code args} for each placeholder, in the same order
   * as {@link #placeholders}. Null arg will show up as "null" in the result string.
   *
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if {@code args} is longer or shorter than {@link #placeholders}.
   */
  public String format(Object... args) {
    if (args.length != placeholders.size()) {
      throw new IllegalArgumentException(
          placeholders.size() + " format arguments expected, " + args.length + " provided.");
    }
    int[] index = new int[1];
    // We know the function is called once for each placeholder, in strict order.
    return formatWith(placeholder -> String.valueOf(args[index[0]++]));
  }

  /**
   * Formats this template with placeholder values returned by {@code placeholderValueFunction}.
   *
   * @throws NullPointerException if {@code placeholderValueFunction} is null or returns null value for any placeholder
   */
  public String formatWith(
      Function<? super Substring.Match, ? extends CharSequence> placeholderValueFunction) {
    requireNonNull(placeholderValueFunction);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < placeholders.size(); i++) {
      Substring.Match placeholder = placeholders.get(i);
      builder
          .append(literals.get(i))
          .append(
              requireNonNull(
                  placeholderValueFunction.apply(placeholder),
                  "null returned from placeholder value function."));
    }
    return builder.append(literals.get(placeholders.size())).toString();
  }

  /**
   * Returns the immutable list of placeholders in this template, in occurrence order.
   *
   * <p>Each placeholder is-a {@link CharSequence} with extra accessors to the index in this
   * template string. Callers can also use, for example, {@code .skip(1, 1)} to easily strip away
   * the '{' and '}' characters around the placeholder names.
   */
  public List<Substring.Match> placeholders() {
    return placeholders;
  }

  /** Returns the immutable list of placeholder variable names in this template, in occurrence order. */
  public List<String> placeholderVariableNames() {
    return placeholderVariableNames;
  }

  /** Returns the template pattern. */
  @Override public String toString() {
    return format;
  }

  private Stream<String> parsePlaceholderValues(String input) {
    return match(input)
        .orElseThrow(
            () -> new IllegalArgumentException("Input doesn't match template (" + format + ")"))
        .stream()
        .map(Substring.Match::toString);
  }

  private static void populateLiterals(
      String format, List<Substring.Match> placeholders,
      List<String> literals, List<Substring.Pattern> literalLocators) {
    if (placeholders.isEmpty()) {
      literals.add(format);
      literalLocators.add(prefix(format));
    } else {
      Substring.Match placeholder = placeholders.get(0);
      {
        String literal = format.substring(0, placeholder.index());
        literals.add(literal);
        literalLocators.add(prefix(literal));
      }
      for (int i = 1; ; i++) {
        final int from = placeholder.index() + placeholder.length();
        if (i == placeholders.size()) {
          String literal = format.substring(from, format.length());
          literals.add(literal);
          literalLocators.add(suffix(literal));
          break;
        }
        int end = placeholders.get(i).index();
        if (from >= end) {
          throw new IllegalArgumentException(
              "Invalid pattern with '" + placeholder + placeholders.get(i) + "'");
        }
        String literal = format.substring(from, end);
        literals.add(literal);
        literalLocators.add(first(literal));
        placeholder = placeholders.get(i);
      }
    }
  }
}

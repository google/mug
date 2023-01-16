package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreCollectors.asIn;
import static java.util.Collections.unmodifiableList;

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
 * A utility class to extract placeholder values from input strings based on a format string.
 * For example:
 *
 * <pre>{@code
 * return new StringFormat("To %s: %s?")
 *     .parse(input, (recipient, question) -> ...));
 * }</pre>
 *
 * <p>Placeholders can be named:
 *
 * <pre>{@code
 * List<String> values =
 *     new StringFormat("To {recipient}: {question}?", "{", "}")
 *         .parse("To Charlie: How are you?");
 * }</pre>
 *
 * <p>If you'd like to access the placeholder values by name, you can {@link BiStream#zip zip}
 * them with {@link #placeholders}:
 *
 * <pre>{@code
 * StringFormat template = new StringFormat("To {recipient}: {question}?", "{", "}");
 * Map<String, String> values =
 *     BiStream.zip(template.placeholders(), template.parse("To Charlie: How are you?"))
 *         .mapKeys(Substring::Match::toString)
 *         .toMap();
 * }</pre>
 *
 * <p>Note that other than the placeholders, characters in the template are treated as literals.
 * This makes it simpler compared to regex if your pattern is close to free-form text with
 * characters like '.', '?', '(', '|' and what not. On the other hand, if you need to use regex
 * modifiers and quantifiers to express complex patterns, you need a regex pattern, not a literal
 * pattern with placeholders.
 *
 * <p>The {@code parse()} methods are potentially lossy reverse operations of {@link String#format}
 * or the {@code format()} methods. Consider the format string of {@code String.format("I bought %s
 * and %s", "apples and oranges", "chips")}, it returns {@code "I bought apples and oranges and
 * chips"}; but the parsing code will incorrectly return {@code Map.of("{fruits}", "apples",
 * "{snacks}", "oranges and chips")}:
 *
 * <pre>{@code
 * new StringFormat("I bought {fruits} and {snacks}", "{", "}")
 *     .parse( "I bought apples and oranges and chips", (fruit, snacks) -> ...);
 * }</pre>
 *
 * This is because the parser will see the " and " substring and immediately matches {@code
 * "apples"} as {@code fruits}, with no backtracking attempted. As such, only use this class to
 * parse unambiguous strings. Use regex otherwise.
 *
 * <p>This class is immutable and pre-compiles the template format at constructor time so that the
 * {@code parse()} and {@code format()} methods will be more efficient.
 *
 * @since 6.6
 */
public final class StringFormat {
  private final String format;
  private final List<Substring.Match> placeholders;

  /**
   * In the input string, a placeholder value is found from the current position until the next
   * text literal, which includes all literal characters found in the template between the previous
   * placeholder variable (or the BEGINNING) and the next placeholder variable (or the END).
   */
  private final List<String> literals;
  private final List<Substring.Pattern> literalLocators;

  /**
   * Constructs a StringFormat. For example:
   *
   * <pre>{@code
   * new StringFormat("Dear {person}, your confirmation number is {confirmation_number}")
   * }</pre>
   *
   * @param format the template format with placeholders in the format of {@code "{placeholder_name}"}
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringFormat(String format) {
    this(format, first("%s").repeatedly());
  }

  /**
   * Returns a StringFormat for the given {@code format} with {@code placeholder}.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("I bought {fruits} and {snacks} at price of {price}", "{", "}")
   *     .parse("I bought ice cream and beer at price of $15.4", (a, b, price) -> ...));
   * }</pre>
   */
  public StringFormat(String format, String placeholderPrefix, String placeholderSuffix) {
    this(format, Substring.spanningInOrder(placeholderPrefix, placeholderSuffix).repeatedly());
  }

  /**
   * Constructs a StringFormat. By default, {@code new StringFormat(format)} assumes placeholders
   * to be enclosed by curly braces. For example: "Hello {customer}". If you need different placeholder
   * syntax, for example, to emulate Java's "%s", you can use:
   *
   * <pre>{@code new StringFormat("Hi %s, my name is %s", first("%s").repeatedly())}</pre>
   *
   * @param format the template format with placeholders
   * @param placeholderVariablePattern placeholders in {@code format}.
   *     For example: {@code first("%s").repeatedly()}.
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  private StringFormat(String format, Substring.RepeatingPattern placeholderVariablesPattern) {
    this.format = format;
    this.placeholders = placeholderVariablesPattern.match(format).collect(toImmutableList());
    List<String> literals = new ArrayList<>(placeholders.size() + 1);
    List<Substring.Pattern> literalLocators = new ArrayList<>(literals.size());
    extractLiteralsFromFormat(format, placeholders, literals, literalLocators);
    this.literals = unmodifiableList(literals);
    this.literalLocators = unmodifiableList(literalLocators);
  }

  /**
   * Parses {@code input} and applies {@code reducer} with the single placeholder value
   * in this template.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: {})").parse(input, jobId -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly one placeholder.
   */
  public <R> R parse(String input, Function<? super String, R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Parses {@code input} and applies {@code reducer} with the two placeholder values
   * in this template.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '{}', error code: {})")
   *     .parse(input, (jobId, errorCode) -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly two placeholders.
   */
  public <R> R parse(String input, BiFunction<? super String, ? super String, R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * reducer} with the <em>3</em> placeholder values in this template.
   *
   * <p>For example: <pre>{@code
   * new StringFormat("Job failed (job id: '{}', error code: {}, error details: {})")
   *     .parse(input, (jobId, errorCode, errorDetails) -> ...);
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly 3 placeholders.
   */
  public <R> R parse(String input, Ternary<? super String,  R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * reducer} with the <em>4</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly 4 placeholders.
   */
  public <R> R parse(String input, Quarternary<? super String,  R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * reducer} with the <em>5</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly 5 placeholders.
   */
  public <R> R parse(String input, Quinary<? super String,  R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Similar to {@link #parse(String, BiFunction}, but parses {@code input} and applies {@code
   * reducer} with the <em>6</em> placeholder values in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format or the template
   *     doesn't have exactly 6 placeholders.
   */
  public <R> R parse(String input, Senary<? super String,  R> reducer) {
    return parsePlaceholderValues(input).collect(asIn(reducer));
  }

  /**
   * Parses {@code input} and extracts all placeholder name-value pairs in a BiStream,
   * in the same order as {@link #placeholders}.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the format
   */
  public BiStream<String, String> parse(String input) {
    return BiStream.zip(
        placeholders.stream().map(Substring.Match::toString), parsePlaceholderValues(input));
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
    for (int i = 0; i < literals.size(); i++) {
      Substring.Match placeholder = before(literalLocators.get(i)).match(input, inputIndex);
      if (placeholder == null) return Optional.empty();
      if (i > 0) {
        builder.add(placeholder);
      }
      inputIndex = placeholder.index() + placeholder.length() + literals.get(i).length();
    }
    return optional(inputIndex == input.length(), unmodifiableList(builder));
  }

  /**
   * By default, (with "%s" as the placeholder), it's equivalent to {@link String#format}
   * (but faster). It can also be used for named placeholders. For example:
   *
   * <pre>{@code
   * new StringFormat("projects/{project}/locations/{location}", "{", "}")
   *     .format("my-project", "us");
   *   => "projects/my-project/locations/us"
   * }</pre>
   *
   * <p>If you wish to supply the placeholder values by name, consider using {@link Substring},
   * as in:
   *
   * <pre>{@code
   * Substring.spanningInOrder("{", "}")
   *     .repeatedly()
   *     .replaceAllFrom(
   *         "Dear {person}, your confirmation number is {confirmation#}",
   *         placeholder -> ...);
   * }</pre>
   *
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if {@code args} is longer or shorter than {@link #placeholders}.
   */
  @SafeVarargs
  public final String format(Object... args) {
    if (args.length != placeholders.size()) {
      throw new IllegalArgumentException(
          placeholders.size() + " format arguments expected, " + args.length + " provided.");
    }
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < args.length; i++) {
      builder
          .append(literals.get(i))
          .append(args[i]);
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

  /** Returns the template pattern. */
  @Override public String toString() {
    return format;
  }

  private Stream<String> parsePlaceholderValues(String input) {
    return match(input)
        .orElseThrow(
            () -> new IllegalArgumentException("input doesn't match template (" + format + ")"))
        .stream()
        .map(Substring.Match::toString);
  }

  private static void extractLiteralsFromFormat(
      String template, List<Substring.Match> placeholders,
      List<String> literals, List<Substring.Pattern> literalLocators) {
    int templateIndex = 0;
    for (
        int i = 0; i < placeholders.size();
        templateIndex = placeholders.get(i).index() + placeholders.get(i).length(), i++) {
      Substring.Match nextPlaceholder = placeholders.get(i);
      int literalEnd = nextPlaceholder.index();
      String literal = template.substring(templateIndex, literalEnd);
      literals.add(literal);
      if (i == 0) {
        literalLocators.add(prefix(literal));  // First literal anchored to beginning
        continue;
      }
      literalLocators.add(first(literal));  // Subsequent literals are searched
      if (templateIndex >= literalEnd) {
        throw new IllegalArgumentException(
            "invalid pattern with '" + placeholders.get(i - 1) + nextPlaceholder + "'");
      }
    }
    String literal = template.substring(templateIndex, template.length());
    literals.add(literal);
    // If no placeholder, anchor to beginning; else last literal anchored to end.
    literalLocators.add(templateIndex == 0 ? prefix(literal) : suffix(literal));
  }
}

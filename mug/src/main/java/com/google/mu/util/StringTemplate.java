package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Optionals.optional;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static com.google.mu.util.stream.MoreCollectors.onlyElements;
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
 * A utility class to extract placeholder values from input strings based on a template.
 *
 * <p>If you have a simple template with placeholder names like "{recipient}", "{question}",
 * you can then reverse-engineer the placeholder values from a formatted string such as
 * "To Charlie: How are you?":
 *
 * <pre>{@code
 * StringTemplate template = new StringTemplate("To {recipient}: {question}?");
 * Map<String, String> placeholderValues = template.parse("To Charlie: How are you?").toMap();
 * assertThat(placeholderValues)
 *     .containsExactly("{recipient}", "Charlie", "{question}", "How are you");
 * }</pre>
 *
 * <p>More fluently, instead of parsing into a {@code Map} keyed by the placeholder variable names,
 * directly collect the placeholder values using lambda:
 * "To Charlie: How are you?":
 *
 * <pre>{@code
 * StringTemplate template = new StringTemplate("To {recipient}: {question}?");
 * return template.parse("To Charlie: How are you?", (recipient, question) -> ...));
 * }</pre>
 *
 * <p>Note that other than the placeholders, characters in the template and the input must match
 * exactly, case sensitively, including whitespaces, punctuations and everything.
 *
 * @since 6.6
 */
public final class StringTemplate {
  private final String pattern;
  private final Substring.Pattern placeholderVariablePattern;
  private final List<Substring.Match> placeholders;
  private final List<String> placeholderVariableNames;
  private final List<Substring.Pattern> anchoringPatterns;

  /**
   * Constructs a StringTemplate
   *
   * @param pattern the template pattern with placeholders in the format of {@code "{placeholder_name}"}
   * @throws IllegalArgumentException if {@code pattern} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringTemplate(String pattern) {
    this(pattern, Substring.spanningInOrder("{", "}"));
  }

  /**
   * Constructs a StringTemplate
   *
   * @param pattern the template pattern with placeholders
   * @throws IllegalArgumentException if {@code pattern} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringTemplate(String pattern, Substring.Pattern placeholderVariablePattern) {
    this.pattern = pattern;
    this.placeholderVariablePattern = placeholderVariablePattern;
    this.placeholders =
        placeholderVariablePattern.repeatedly().match(pattern).collect(toImmutableList());
    this.placeholderVariableNames =
        placeholders.stream().map(Substring.Match::toString).collect(toImmutableList());
    this.anchoringPatterns = getAnchoringPatterns(pattern, placeholders);
  }

  /** Render this template using placeholder values returned by {@code placeholderValueFunction}. */
  public String render(Function<? super Substring.Match, ? extends CharSequence> placeholderValueFunction) {
    return placeholderVariablePattern.repeatedly().replaceAllFrom(pattern, placeholderValueFunction);
  }

  /**
   * Parses {@code input} and extracts all placeholder name-value pairs in a BiStream,
   * in the same order as {@link #placeholders}.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template
   */
  public BiStream<String, String> parse(String input) {
    return BiStream.zip(placeholderVariableNames.stream(),  parsePlaceholderValues(input));
  }

  /**
   * Parses {@code input} and apply {@code function} with the single placeholder value
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly one placeholder.
   */
  public <R> R parse(String input, Function<? super String, R> function) {
    return parsePlaceholderValues(input).collect(onlyElement(function));
  }

  /**
   * Parses {@code input} and apply {@code function} with the two placeholder values
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly two placeholders.
   */
  public <R> R parse(String input, BiFunction<? super String, ? super String, R> function) {
    return parsePlaceholderValues(input).collect(onlyElements(function));
  }

  /**
   * Parses {@code input} and apply {@code function} with the 3 placeholder values
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 3 placeholders.
   */
  public <R> R parse(String input, Ternary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(onlyElements(function));
  }

  /**
   * Parses {@code input} and apply {@code function} with the 4 placeholder values
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 4 placeholders.
   */
  public <R> R parse(String input, Quarternary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(onlyElements(function));
  }

  /**
   * Parses {@code input} and apply {@code function} with the 5 placeholder values
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 5 placeholders.
   */
  public <R> R parse(String input, Quinary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(onlyElements(function));
  }

  /**
   * Parses {@code input} and apply {@code function} with the 6 placeholder values
   * in this template.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template or the template
   *     doesn't have exactly 6 placeholders.
   */
  public <R> R parse(String input, Senary<? super String,  R> function) {
    return parsePlaceholderValues(input).collect(onlyElements(function));
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
    List<Substring.Match> builder = new ArrayList<>();
    Substring.Match prelude = anchoringPatterns.get(0).match(input, 0);
    if (prelude == null) return Optional.empty();
    int inputIndex = prelude.length();
    for (int i = 1; i < anchoringPatterns.size(); i++) {
      Substring.Match following = anchoringPatterns.get(i).match(input, inputIndex);
      if (following == null) return Optional.empty();
      builder.add(Substring.Match.nonBacktrackable(input, inputIndex, following.index() - inputIndex));
      inputIndex = following.index() + following.length();
    }
    return optional(inputIndex == input.length(), unmodifiableList(builder));
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
    return pattern;
  }

  private Stream<String> parsePlaceholderValues(String input) {
    return match(input)
        .orElseThrow(() -> new IllegalArgumentException("Input doesn't match template (" + pattern + ")"))
        .stream()
        .map(Substring.Match::toString);
  }

  private static List<Substring.Pattern> getAnchoringPatterns(
      String pattern, List<Substring.Match> placeholders) {
    List<Substring.Pattern> literals = new ArrayList<>();
    if (placeholders.isEmpty()) {
      literals.add(Substring.prefix(pattern));
    } else {
      Substring.Match placeholder = placeholders.get(0);
      literals.add(prefix(pattern.substring(0, placeholder.index())));
      for (int i = 1; ; i++) {
        if (i == placeholders.size()) {
          int from = placeholder.index() + placeholder.length();
          literals.add(suffix(pattern.substring(from, pattern.length())));
          break;
        }
        int from = placeholder.index() + placeholder.length();
        int end = placeholders.get(i).index();
        if (from >= end) {
          throw new IllegalArgumentException(
              "Invalid pattern with '" + placeholder + placeholders.get(i) + "'");
        }
        literals.add(first(pattern.substring(from, end)));
        placeholder = placeholders.get(i);
      }
    }
    return unmodifiableList(literals);
  }
}

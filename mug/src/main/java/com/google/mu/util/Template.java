package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.mu.util.stream.BiStream;

/**
 * A utility class to help extract placeholder values from input strings based on a template.
 *
 * <p>If you have a simple template with placeholder names like "{recipient}", "{question}", you can then
 * reverse-engineer the placeholder values from a formatted string such as "To Charlie: How are you?":
 *
 * <pre>{@code
 * Template template = new Template("To {recipient}: {question}?", is('?').not());
 * Map<String, String> placeholderValues = template.parse("To Charlie: How are you?").toMap();
 * assertThat(placeholderValues)
 *     .containsExactly("{recipient}", "Charlie", "{question}", "How are you");
 * }</pre>
 *
 * <p>Note that other than the placeholders, characters in the template and the input must match exactly,
 * case sensitively, including whitespaces, punctuations and everything.
 *
 * @since 6.6
 */
public final class Template {
  private final String pattern;
  private final List<Substring.Match> placeholders;
  private final CharPredicate placeholderCharMatcher;

  /**
   * Constructs a Template
   *
   * @param pattern the template pattern with placeholders in the format of {@code "{placeholder_name}"}
   * @param placeholderCharMatcher
   *     the characters that are allowed in each matched placeholder value
   * @throws IllegalArgumentException if {@code pattern} includes duplicate placeholders
   */
  public Template(String pattern, CharPredicate placeholderMatcher) {
    this(pattern, Substring.spanningInOrder("{", "}"), placeholderMatcher);
  }

  /**
   * Constructs a Template
   *
   * @param pattern the template pattern with placeholders
   * @param placeholderNamePattern
   *     the pattern of the placeholder names such as {@code Substring.spanningInOrder("[", "]")}.
   * @param placeholderCharMatcher
   *     the characters that are allowed in each matched placeholder value
   * @throws IllegalArgumentException if {@code pattern} includes duplicate placeholders
   */
  public Template(
      String pattern, Substring.Pattern placeholderNamePattern, CharPredicate placeholderCharMatcher) {
    this.pattern = pattern;
    this.placeholders =
        placeholderNamePattern.repeatedly().match(pattern).collect(toImmutableList());
    this.placeholderCharMatcher = requireNonNull(placeholderCharMatcher);
    Set<String> placeholderNames = new HashSet<>(placeholders.size());
    for (Substring.Match placeholder : placeholders) {
      if (!placeholderNames.add(placeholder.toString())) {
        throw new IllegalArgumentException("Duplicate placeholder (" + placeholder + ")");
      }
    }
  }

  /**
   * Parses {@code input} and extracts all placeholder name-value pairs in a BiStream,
   * in encounter order.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template
   */
  public BiStream<String, String> parse(String input) {
    return BiStream.zip(
        placeholders.stream().map(Substring.Match::toString),
        match(input).stream().map(Substring.Match::toString));
  }

  /**
   * Matches {@code input} against the pattern.
   *
   * <p>Returns an immutable list of placeholder values in the same order as {@link #placeholders}.
   *
   * <p>The {@link Substring.Match} result type allows caller to inspect the characters around each match,
   * or to access the raw index in the input string.
   *
   * @throws IllegalArgumentException if {@code input} doesn't match the template
   */
  public List<Substring.Match> match(String input) {
    Substring.Pattern placeholderValuePattern =
        Substring.leading(placeholderCharMatcher).or(Substring.BEGINNING);
    List<Substring.Match> builder = new ArrayList<>();
    int templateIndex = 0;
    int inputIndex = 0;
    for (Substring.Match placeholder : placeholders) {
      int preludeLength = placeholder.index() - templateIndex;
      if (!input.regionMatches(inputIndex, pattern, templateIndex, preludeLength)) {
        throw new IllegalArgumentException("Input doesn't match template (" + pattern + ")");
      }
      templateIndex += preludeLength;
      inputIndex += preludeLength;
      Substring.Match placeholderValue = placeholderValuePattern.match(input, inputIndex);
      builder.add(placeholderValue);
      templateIndex += placeholder.length();
      inputIndex += placeholderValue.length();
    }
    int remaining = pattern.length() - templateIndex;
    if (remaining != input.length() - inputIndex
        || !input.regionMatches(inputIndex, pattern, templateIndex, remaining)) {
      throw new IllegalArgumentException("Input doesn't match template (" + pattern + ")");
    }
    return Collections.unmodifiableList(builder);
  }

  /**
   * Returns the immutable list of placeholders in this template.
   *
   * <p>Each placeholder is-a {@link CharSequence} with extra accessors to the index in this
   * template string.
   */
  public List<Substring.Match> placeholders() {
    return placeholders;
  }

  /** Returns the template pattern. */
  @Override public String toString() {
    return pattern;
  }
}

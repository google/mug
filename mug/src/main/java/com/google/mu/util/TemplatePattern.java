package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static java.util.Objects.requireNonNull;

import java.util.List;

import com.google.mu.util.stream.BiStream;

/**
 * A utility class to help extract placeholder values from input strings based on a template.
 *
 * <p>If you have a simple template with placeholder names like "{recipient}", "{question}", you can then
 * reverse-engineer the placeholder values from a formatted string such as "To Charlie: How are you?":
 *
 * <pre>{@code
 * TemplatePattern template = new TemplatePattern("To {recipient}: {question}?", is('?').not());
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
public final class TemplatePattern {
  private final String template;
  private final List<Substring.Match> placeholderMatches;
  private final CharPredicate placeholderCharMatcher;

  /**
   * Constructs a TemplatePattern.
   *
   * @param template the template pattern with placeholders
   * @param placeholderNamePattern
   *     the pattern of the placeholder names such as {@code Substring.spanningInOrder("[", "]")}.
   * @param placeholderCharMatcher
   *     the characters that are allowed in each matched placeholder value
   */
  public TemplatePattern(
      String template, Substring.Pattern placeholderNamePattern, CharPredicate placeholderCharMatcher) {
    this.template = template;
    this.placeholderMatches =
        placeholderNamePattern.repeatedly().match(template).collect(toImmutableList());
    this.placeholderCharMatcher = requireNonNull(placeholderCharMatcher);
  }

  /**
   * Constructs a TemplatePattern.
   *
   * @param template the template pattern with placeholders in the format of {@code "{placeholder_name}"}
   * @param placeholderCharMatcher
   *     the characters that are allowed in each matched placeholder value
   */
  public TemplatePattern(String template, CharPredicate placeholderMatcher) {
    this(template, Substring.spanningInOrder("{", "}"), placeholderMatcher);
  }

  /** Parses {@code input} and extracts all placeholder name-value pairs in a BiStream, in encounter order. */
  public BiStream<String, String> parse(String input) {
    return match(input).mapKeys(Substring.Match::toString).mapValues(Substring.Match::toString);
  }

  /**
   * Matches {@code input} against the pattern.
   *
   * <p>Returns each placeholder name and the corresponding placeholder value (both of type {@link Substring.Match} in a BiStream,
   * in encounter order.
   *
   * <p>The {@link Substring.Match} result type allows caller to inspect the characters around each match,
   * or to access the raw index in the original template or the input.
   */
  public BiStream<Substring.Match, Substring.Match> match(String input) {
    Substring.Pattern placeholderValuePattern =
        Substring.leading(placeholderCharMatcher).or(Substring.BEGINNING);
    BiStream.Builder<Substring.Match, Substring.Match> builder = BiStream.builder();
    int templateIndex = 0;
    int inputIndex = 0;
    for (Substring.Match placeholder : placeholderMatches) {
      int preludeLength = placeholder.index() - templateIndex;
      if (!input.regionMatches(inputIndex, template, templateIndex, preludeLength)) {
        throw new IllegalArgumentException("Input doesn't match template (" + template + ")");
      }
      templateIndex += preludeLength;
      inputIndex += preludeLength;
      Substring.Match placeholderValue = placeholderValuePattern.match(input, inputIndex);
      builder.add(placeholder, placeholderValue);
      templateIndex += placeholder.length();
      inputIndex += placeholderValue.length();
    }
    int remaining = template.length() - templateIndex;
    if (remaining != input.length() - inputIndex
        || !input.regionMatches(inputIndex, template, templateIndex, remaining)) {
      throw new IllegalArgumentException("Input doesn't match template (" + template + ")");
    }
    return builder.build();
  }
}

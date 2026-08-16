package com.google.mu.errorprone.regex;

import static java.util.Objects.requireNonNull;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.annotations.ParametersMustMatchByName;
import java.util.List;

/**
 * Exception thrown when a regular expression is vulnerable to backtracking (ReDoS or PDA).
 *
 * <p>In addition to a human-readable {@link #getMessage()}, this exception provides structured
 * access to the vulnerable {@link #getPattern()}, the {@link #getAttackPayload()}, and any {@link
 * #getSuggestions()}.
 *
 * @since 10.9
 */
public class VulnerableRegexException extends IllegalArgumentException {
  private final RegexPattern pattern;
  private final String attackPayload;
  private final List<Suggestion> suggestions;

  VulnerableRegexException(
      String message, RegexPattern pattern, String attackPayload, List<Suggestion> suggestions) {
    super(message);
    this.pattern = requireNonNull(pattern, "pattern");
    this.attackPayload = requireNonNull(attackPayload, "attackPayload");
    this.suggestions = List.copyOf(suggestions);
  }

  /** Returns the AST of the vulnerable regular expression. */
  public RegexPattern getPattern() {
    return pattern;
  }

  /** Returns an example input string that triggers worst-case backtracking on this regex. */
  public String getAttackPayload() {
    return attackPayload;
  }

  /**
   * Returns a list of structured {@link Suggestion}s (e.g. safe regex rewrites, {@code
   * StringFormat}, or parser combinators), if any.
   */
  public List<Suggestion> getSuggestions() {
    return suggestions;
  }

  /** Returns the replacement expressions or strings of all suggested alternatives. */
  public List<String> getSuggestedAlternatives() {
    return suggestions.stream().map(Suggestion::replacement).toList();
  }

  /**
   * A suggested alternative or safe rewrite for a regular expression vulnerable to ReDoS or PDA.
   */
  public sealed interface Suggestion {

    /** Returns the replacement Java source expression or regex string. */
    String replacement();

    /**
     * Returns whether this suggestion is strictly language-equivalent to the original regular
     * expression without behavioral divergence on any inputs.
     */
    boolean isStrictlyEquivalent();

    /**
     * Returns a list of caveats, semantic differences, or boundary conditions that the caller
     * should be aware of when applying this suggestion.
     */
    List<String> caveats();

    /** A suggestion to rewrite the regex into a safer regular expression. */
    record RegexSuggestion(String replacement, boolean isStrictlyEquivalent, List<String> caveats)
        implements Suggestion {
      public RegexSuggestion {
        requireNonNull(replacement, "replacement");
        caveats = List.copyOf(caveats);
      }

      @ParametersMustMatchByName
      public RegexSuggestion(String replacement, String... caveats) {
        this(replacement, caveats.length == 0, List.of(caveats));
      }

      @Override public String toString() {
        return replacement;
      }
    }

    /** A suggestion to replace regex matching with {@code StringFormat}. */
    record StringFormatSuggestion(String format, boolean isStrictlyEquivalent, List<String> caveats)
        implements Suggestion {
      public StringFormatSuggestion {
        requireNonNull(format, "format");
        caveats = List.copyOf(caveats);
      }

      @ParametersMustMatchByName
      public StringFormatSuggestion(String format, String... caveats) {
        this(format, false, List.of(caveats));
      }

      @Override public String replacement() {
        return "new StringFormat(\"" + format + "\")";
      }

      @Override public String toString() {
        return replacement();
      }
    }

    /** A suggestion to replace regex parsing with {@code Parser} combinators. */
    record ParserSuggestion(String replacement, boolean isStrictlyEquivalent, List<String> caveats)
        implements Suggestion {
      public ParserSuggestion {
        requireNonNull(replacement, "replacement");
        caveats = List.copyOf(caveats);
      }

      @ParametersMustMatchByName
      public ParserSuggestion(String replacement, String... caveats) {
        this(replacement, false, List.of(caveats));
      }

      @Override public String toString() {
        return replacement;
      }
    }

    /** A suggestion to use {@code Substring} utilities for string extraction. */
    record SubstringSuggestion(
        String replacement, boolean isStrictlyEquivalent, List<String> caveats)
        implements Suggestion {
      public SubstringSuggestion {
        requireNonNull(replacement, "replacement");
        caveats = List.copyOf(caveats);
      }

      @ParametersMustMatchByName
      public SubstringSuggestion(String replacement, String... caveats) {
        this(replacement, false, List.of(caveats));
      }

      @Override public String toString() {
        return replacement;
      }
    }
  }
}

package com.google.mu.errorprone.regex;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import java.util.List;
import java.util.Optional;

/**
 * Utility to detect Exponential Degree of Ambiguity (EDA) / Catastrophic Backtracking (ReDoS) and
 * Polynomial Degree of Ambiguity (PDA) vulnerabilities in {@link RegexPattern} ASTs.
 *
 * @since 10.9
 */
public final class ReDos {

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to exponential backtracking
   * (ReDoS).
   *
   * @throws VulnerableRegexException if the pattern contains an exponential backtracking
   *     vulnerability
   */
  public static void checkRedosVulnerability(RegexPattern pattern) {
    VulnerabilityAnalyzer.detectExponentialBacktracking(pattern)
        .ifPresent(finding -> {
          List<Suggestion> suggestions = SuggestionSynthesizer.forRedos(pattern);
          String message = formatErrorMessage(
              "exponential backtracking (ReDoS)",
              pattern,
              finding.detail(),
              finding.attackPayload(),
              suggestions);
          throw new VulnerableRegexException(
              message, pattern, finding.attackPayload(), suggestions);
        });
  }

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to polynomial backtracking (PDA).
   *
   * @throws VulnerableRegexException if the pattern contains polynomial degree of ambiguity (e.g.
   *     consecutive overlapping quantifiers)
   */
  public static void checkPolynomialBacktracking(RegexPattern pattern) {
    VulnerabilityAnalyzer.detectPolynomialBacktracking(pattern)
        .ifPresent(finding -> {
          List<Suggestion> suggestions = SuggestionSynthesizer.forPolynomial(pattern);
          String message = formatErrorMessage(
              "polynomial backtracking (PDA)",
              pattern,
              finding.detail(),
              finding.attackPayload(),
              suggestions);
          throw new VulnerableRegexException(
              message, pattern, finding.attackPayload(), suggestions);
        });
  }

  /**
   * Returns a list of suggested alternatives or safe rewrites for an exponential ReDoS vulnerable
   * pattern, ordered by preference (Regex -> StringFormat -> Substring -> Parser).
   */
  public static List<Suggestion> suggestRedosAlternatives(RegexPattern pattern) {
    return SuggestionSynthesizer.forRedos(pattern);
  }

  /**
   * Returns a list of suggested alternatives or safe rewrites for a polynomial backtracking
   * vulnerable pattern, ordered by preference (Regex -> StringFormat -> Substring -> Parser).
   */
  public static List<Suggestion> suggestPolynomialAlternatives(RegexPattern pattern) {
    return SuggestionSynthesizer.forPolynomial(pattern);
  }

  /**
   * Suggests a safe rewrite for an exponential ReDoS vulnerable pattern if a high-confidence fix is
   * known.
   */
  public static Optional<String> suggestRedosRewrite(RegexPattern pattern) {
    return SuggestionSynthesizer.suggestRedosRewrite(pattern);
  }

  /**
   * Suggests a safe rewrite for a polynomial backtracking vulnerable pattern if a high-confidence
   * fix is known (e.g. using possessive quantifier).
   */
  public static Optional<String> suggestPolynomialRewrite(RegexPattern pattern) {
    return SuggestionSynthesizer.suggestPolynomialRewrite(pattern);
  }

  private static String formatErrorMessage(
      String vulnerabilityType,
      RegexPattern pattern,
      String detail,
      String payload,
      List<Suggestion> suggestions) {
    StringBuilder sb = new StringBuilder();
    sb.append("Regular expression is vulnerable to ")
        .append(vulnerabilityType)
        .append(": '")
        .append(pattern)
        .append("' ")
        .append(detail);
    if (!payload.isEmpty()) {
      sb.append("\n  attack payload: \"").append(payload).append("\"");
    }
    if (!suggestions.isEmpty()) {
      Suggestion first = suggestions.get(0);
      String replacement =
          first instanceof Suggestion.RegexSuggestion
              ? "'" + first.replacement() + "'"
              : first.replacement();
      sb.append("\n  consider: ").append(replacement);
      for (String caveat : first.caveats()) {
        sb.append("\n  caveat: ").append(caveat);
      }
    }
    return sb.toString();
  }

  private ReDos() {}
}

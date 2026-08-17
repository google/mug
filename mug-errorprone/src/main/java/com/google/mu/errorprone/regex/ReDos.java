package com.google.mu.errorprone.regex;

import static java.util.stream.Collectors.joining;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.StringFormat;
import java.util.List;

/**
 * Utility to detect Exponential Degree of Ambiguity (EDA) / Catastrophic Backtracking (ReDoS) and
 * Polynomial Degree of Ambiguity (PDA) vulnerabilities in {@link RegexPattern} ASTs.
 *
 * @since 10.9
 */
public final class ReDos {
  private static final StringFormat ERROR_MESSAGE = new StringFormat(
      "Regular expression is vulnerable to {vulnerabilityType}: '{pattern}'"
          + " {detail}{payload}{suggestion}{caveats}");
  private static final StringFormat ATTACK_PAYLOAD =
      new StringFormat("\n  attack payload: \"{payload}\"");
  private static final StringFormat CONSIDER_SUGGESTION =
      new StringFormat("\n  consider: {replacement}");
  private static final StringFormat CAVEAT_LINE = new StringFormat("\n  caveat: {caveat}");

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to exponential backtracking
   * (ReDoS).
   *
   * @throws VulnerableRegexException if the pattern contains an exponential backtracking
   *     vulnerability
   */
  public static void checkRedosVulnerability(RegexPattern pattern) {
    new VulnerabilityAnalyzer(pattern)
        .exponentialBacktracking()
        .ifPresent(finding -> {
          String message = formatErrorMessage(
              "exponential backtracking (ReDoS)",
              pattern,
              finding.detail(),
              finding.attackPayload(),
              finding.suggestions());
          throw new VulnerableRegexException(
              message, pattern, finding.attackPayload(), finding.suggestions());
        });
  }

  /**
   * Checks whether the given {@link RegexPattern} is vulnerable to polynomial backtracking (PDA).
   *
   * @throws VulnerableRegexException if the pattern contains polynomial degree of ambiguity (e.g.
   *     consecutive overlapping quantifiers)
   */
  public static void checkPolynomialBacktracking(RegexPattern pattern) {
    new VulnerabilityAnalyzer(pattern)
        .polynomialBacktracking()
        .ifPresent(finding -> {
          String message = formatErrorMessage(
              "polynomial backtracking (PDA)",
              pattern,
              finding.detail(),
              finding.attackPayload(),
              finding.suggestions());
          throw new VulnerableRegexException(
              message, pattern, finding.attackPayload(), finding.suggestions());
        });
  }

  private static String formatErrorMessage(
      String vulnerabilityType,
      RegexPattern pattern,
      String detail,
      String payload,
      List<Suggestion> suggestions) {
    String payloadPart = payload.isEmpty() ? "" : ATTACK_PAYLOAD.format(payload);
    String suggestionPart = "";
    String caveatsPart = "";
    if (!suggestions.isEmpty()) {
      Suggestion first = suggestions.get(0);
      String replacement =
          first instanceof Suggestion.RegexSuggestion
              ? "'" + first.replacement() + "'"
              : first.replacement();
      suggestionPart = CONSIDER_SUGGESTION.format(replacement);
      caveatsPart = first.caveats().stream().map(CAVEAT_LINE::format).collect(joining());
    }
    return ERROR_MESSAGE.format(
        vulnerabilityType, pattern, detail, payloadPart, suggestionPart, caveatsPart);
  }

  private ReDos() {}
}

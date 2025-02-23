package com.google.mu.errorprone;

import static com.google.common.base.CharMatcher.whitespace;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;

import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.mu.util.Substring;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.FixedPosition;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

/** Represents a single placeholder enclosed by curly braces or square brackets. */
final class Placeholder {
  /**
   * For cloud resource names, '=' is used to denote the sub-pattern; For SQL templates, "->" is
   * used to denote a conditional subquery.
   */
  private static final Substring.Pattern PLACEHOLDER_NAME_END =
      Stream.of("=", "->").map(Substring::first).collect(firstOccurrence());

  private final Substring.Match match;
  private final String name;

  Placeholder(Substring.Match match) {
    String placeholderText = match.skip(1, 1).toString();
    this.match = match;
    this.name =
        before(PLACEHOLDER_NAME_END)
            .in(placeholderText)
            .map(whitespace()::trimTrailingFrom)
            .orElse(placeholderText);
  }

  String name() {
    return name;
  }

  Substring.Match match() {
    return match;
  }

  /**
   * Returns the source position of this placeholder by looking in {@code node}.
   *
   * <p>It's required that this placeholder must be within {@code node} or else the result won't
   * be correct.
   */
  DiagnosticPosition sourcePosition(ExpressionTree node, VisitorState state) {
    return new FixedPosition(
        node,
        ASTHelpers.getStartPosition(node) + getStartIndexInSource(state.getSourceForNode(node)));
  }

  @VisibleForTesting
  int getStartIndexInSource(String source) {
    Substring.RepeatingPattern placeholderOpenings = first(match.charAt(0)).repeatedly();
    long placeholderOpeningsBeforeMe = placeholderOpenings.match(match.before()).count();
    return placeholderOpenings
        .match(source)
        .skip(placeholderOpeningsBeforeMe)
        .map(Substring.Match::index)
        .findFirst()
        .orElse(0);
  }

  boolean requiresBooleanArg() {
    return PLACEHOLDER_NAME_END.from(match).orElse("").equals("->");
  }

  boolean isFollowedBy(Placeholder next) {
    return match.index() + match.length() == next.match.index();
  }

  /**
   * Returns human readable excerpt with the surrounding text on the line that includes the
   * placeholder.
   */
  @Override
  public String toString() {
    String context = match.fullString();

    // Find the full line surrounding the placeholder.
    int snippetStart = match.index();
    int snippetEnd = match.index() + match.length();
    while (snippetStart > 0 && context.charAt(snippetStart - 1) != '\n') {
      snippetStart--;
    }
    while (snippetEnd < context.length() && context.charAt(snippetEnd) != '\n') {
      snippetEnd++;
    }

    StringBuilder builder = new StringBuilder();
    if (snippetStart > 0) {
      builder.append("...");
    }
    builder
        .append(context, snippetStart, match.index())
        .append('<')
        .append(match)
        .append(">")
        .append(context, match.index() + match.length(), snippetEnd);
    if (snippetEnd < context.length()) {
      builder.append("...");
    }
    return builder.toString();
  }
}

package com.google.mu.examples.mapflag.regex;

import static com.google.mu.examples.mapflag.ParseUtils.toNumber;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexMapFlagParser {
  private static final String SCALAR =
      "(?s:-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"
          + "|\"(?:[^\"\\\\]|\\\\.)*+\")";
  private static final String LIST =
      "\\[\\s*(?:"
          + SCALAR + "\\s*(?:,\\s*" + SCALAR + "\\s*)*"
          + "(?:,\\s*)?"
          + ")?\\]";
  // (?U) extends \s to Unicode whitespace.
  // Possessive \s*+ prevents polynomial backtracking when '}' is missing.
  private static final Pattern BRACES = Pattern.compile("(?sU)\\s*+\\{\\s*+(.*)\\}\\s*");
  // (?:,\s*|\z) requires a comma or end-of-body after each entry.
  private static final Pattern ENTRY = Pattern.compile(
      "(?U)([a-zA-Z0-9_-]+)\\s*=\\s*"
          + "(" + SCALAR + "|" + LIST + ")"
          + "\\s*(?:,\\s*|\\z)");
  private static final Pattern SCALAR_PATTERN = Pattern.compile(SCALAR);
  private static final Pattern ESCAPE = Pattern.compile("(?s)\\\\(.)");

  public static Map<String, Object> parse(String input) {
    Matcher braces = BRACES.matcher(input);
    if (!braces.matches()) {
      throw new IllegalArgumentException("expecting {...}");
    }
    String body = braces.group(1);
    Map<String, Object> result = new LinkedHashMap<>();
    Matcher entry = ENTRY.matcher(body);
    for (int pos = 0; pos < body.length(); pos = entry.end()) {
      if (!entry.region(pos, body.length()).lookingAt()) {
        throw new IllegalArgumentException("invalid entry at " + pos + ": " + body.substring(pos));
      }
      result.merge(
          entry.group(1),
          toValue(entry.group(2)),
          (a, b) -> {
            throw new IllegalArgumentException("Duplicate key");
          });
    }
    return result;
  }

  private static Object toValue(String value) {
    return value.startsWith("[")
        ? SCALAR_PATTERN.matcher(value)
            .results()
            .map(m -> toScalar(m.group()))
            .toList()
        : toScalar(value);
  }

  private static Object toScalar(String scalar) {
    return scalar.startsWith("\"")
        ? ESCAPE.matcher(scalar.substring(1, scalar.length() - 1))
            .replaceAll("$1")
        : toNumber(scalar);
  }
}

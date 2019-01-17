package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * Represents a substring found in an enclosing string.
 * 
 * For example, to strip off the "http://" prefix from a uri string if existent: <pre>
 * return Substring.prefix("http://")
 *     .in(uri)
 *     .map(Substring::chop)
 *     .orElse(uri);
 * </pre>
 *
 * To strip off either "http://" or "https://" prefix: <pre>
 * return prefix("http://").or(prefix("https://"))
 *     .in(uri)
 *     .map(Substring::chop)
 *     .orElse(uri);
 * </pre>
 *
 * To strip off the suffix at and after the last "_" character from a string: <pre>
 * return Substring.last('_')
 *     .in(string)
 *     .map(Substring::before)
 *     .orElse(string);
 * </pre>
 *
 * @since 1.2
 */
public final class Substring {
  private final String context;
  private final int startIndex;
  private final int endIndex;

  private Substring(String context, int startIndex, int endIndex) {
    this.context = context;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
  }

  /** Returns a {@code Matcher} that matches strings starting with {@code prefix}. */
  public static Matcher prefix(String prefix) {
    requireNonNull(prefix);
    return str -> str.startsWith(prefix)
        ? Optional.of(new Substring(str, 0, prefix.length()))
        : Optional.empty();
  }

  /** Returns a {@code Matcher} that matches strings ending with {@code suffix}. */
  public static Matcher suffix(String suffix) {
    requireNonNull(suffix);
    return str -> str.endsWith(suffix)
        ? Optional.of(new Substring(str, str.length() - suffix.length(), str.length()))
        : Optional.empty();
  }

  /** Returns a {@code Matcher} that matches the first occurrence of {@code c}. */
  public static Matcher first(char c) {
    return str -> substring(str, str.indexOf(c), 1);
  }

  /** Returns a {@code Matcher} that matches the first occurrence of {@code snippet}. */
  public static Matcher first(String snippet) {
    requireNonNull(snippet);
    return str -> substring(str, str.indexOf(snippet), snippet.length());
  }

  /** Returns a {@code Matcher} that matches the last occurrence of {@code c}. */
  public static Matcher last(char c) {
    return str -> substring(str, str.lastIndexOf(c), 1);
  }

  /** Returns a {@code Matcher} that matches the last occurrence of {@code snippet}. */
  public static Matcher last(String snippet) {
    requireNonNull(snippet);
    return str -> substring(str, str.lastIndexOf(snippet), snippet.length());
  }

  /** Returns part before this substring. */
  public String before() {
    return context.substring(0, startIndex);
  }

  /** Returns part after this substring. */
  public String after() {
    return context.substring(endIndex);
  }

  /** Returns a new string with the substring chopped off. */
  public String chop() {
    if (endIndex == context.length()) {
      return before();
    } else if (startIndex == 0) {
      return after();
    } else {
      return before() + after();
    }
  }

  /** Returns this substring. */
  @Override public String toString() {
    return context.substring(startIndex, endIndex);
  }

  /** A substring pattern that can be matched against a string to find substrings. */
  public interface Matcher {

    /** Finds the substring in {@code string} or returns {@code empty()} if not found. */
    Optional<Substring> in(String string);

    /**
     * Returns a {@code Matcher} that fall backs to using {@code that} if {@code this} fails to
     * match.
     */
    default Matcher or(Matcher that) {
      requireNonNull(that);
      return str -> {
        Optional<Substring> substring = in(str);
        return substring.isPresent() ? substring : that.in(str);
      };
    }
  }

  private static Optional<Substring> substring(String str, int index, int length) {
    return index >= 0 ? Optional.of(new Substring(str, index, index + length)) : Optional.empty();
  }
}
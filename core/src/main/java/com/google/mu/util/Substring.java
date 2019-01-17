package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Optional;

/**
 * A substring inside a string, providing easy access to substrings around it ({@link #before before()},
 * {@link #after after()} or with the substring itself {@link #remove removed}).
 * 
 * <p>For example, to strip off the "http://" prefix from a uri string if existent: <pre>
 *   static String stripHttp(String uri) {
 *     return Substring.prefix("http://").removeFrom(uri);
 *   }
 * </pre>
 *
 * To strip off either "http://" or "https://" prefix: <pre>
 *   static import com.google.mu.util.Substring.prefix;
 * 
 *   static String stripHttpOrHttps(String uri) {
 *     return prefix("http://").or(prefix("https://")).removeFrom(uri);
 *   }
 * </pre>
 *
 * To strip off the suffix at and after the last "_" character: <pre>
 *   static String beforeLastUnderscore(String str) {
 *     return Substring.last('_')
 *         .in(str)
 *         .map(Substring::before)
 *         .orElse(str);
 *   }
 * </pre>
 *
 * To extract the 'name' and 'value' from texts in the format of "name:value": <pre>
 *   String str = ...;
 *   Substring colon = Substring.first(':').in(str).orElseThrow(BadFormatException::new);
 *   String name = colon.before();
 *   String value = colon.after();
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

  /** Returns a {@code Pattern} that matches strings starting with {@code prefix}. */
  public static Pattern prefix(String prefix) {
    requireNonNull(prefix);
    return str -> str.startsWith(prefix)
        ? Optional.of(new Substring(str, 0, prefix.length()))
        : Optional.empty();
  }

  /** Returns a {@code Pattern} that matches strings ending with {@code suffix}. */
  public static Pattern suffix(String suffix) {
    requireNonNull(suffix);
    return str -> str.endsWith(suffix)
        ? Optional.of(new Substring(str, str.length() - suffix.length(), str.length()))
        : Optional.empty();
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code c}. */
  public static Pattern first(char c) {
    return str -> substring(str, str.indexOf(c), 1);
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code snippet}. */
  public static Pattern first(String snippet) {
    requireNonNull(snippet);
    return str -> substring(str, str.indexOf(snippet), snippet.length());
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}. */
  public static Pattern regex(java.util.regex.Pattern regexPattern) {
    requireNonNull(regexPattern);
    return str -> {
      java.util.regex.Matcher matcher = regexPattern.matcher(str);
      if (matcher.find()) {
        return Optional.of(new Substring(str, matcher.start(), matcher.end()));
      } else {
        return Optional.empty();
      }
    };
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}. */
  public static Pattern regex(String regexPattern) {
    return regex(java.util.regex.Pattern.compile(regexPattern));
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code c}. */
  public static Pattern last(char c) {
    return str -> substring(str, str.lastIndexOf(c), 1);
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code snippet}. */
  public static Pattern last(String snippet) {
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

  /** Returns a new string with the substring removed. */
  public String remove() {
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
  public interface Pattern {

    /** Finds the substring in {@code string} or returns {@code empty()} if not found. */
    Optional<Substring> in(String string);

    /**
     * Returns a new string with the substring matched by {@code this} removed, or returns
     * {@code string} as is.
     */
    default String removeFrom(String string) {
      return in(string).map(Substring::remove).orElse(string);
    }

    /**
     * Returns a {@code Pattern} that fall backs to using {@code that} if {@code this} fails to
     * match.
     */
    default Pattern or(Pattern that) {
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
package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Optional;


/**
 * A substring inside a string, providing easy access to substrings around it ({@link #before before()},
 * {@link #after after()} or with the substring itself {@link #remove removed}, {@link #replaceWith replaced}
 * etc.).
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
 * To replace trailing "//" with "/": <pre>
 *   static String fixTrailingSlash(String str) {
 *     return Substring.suffix("//").replaceFrom(str, '/');
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

  /** Returns a {@link Pattern} that never matches any substring. */
  public static Pattern none() {
    return Constants.NONE;
  }

  /** Returns a {@link Pattern} that matches all strings entirely. */
  public static Pattern all() {
    return Constants.ALL;
  }

  /** Returns a {@code Pattern} that matches strings starting with {@code prefix}. */
  public static Pattern prefix(String prefix) {
    requireNonNull(prefix);
    return (SerializablePattern) str -> str.startsWith(prefix)
        ? Optional.of(new Substring(str, 0, prefix.length()))
        : Optional.empty();
  }

  /** Returns a {@code Pattern} that matches strings ending with {@code suffix}. */
  public static Pattern suffix(String suffix) {
    requireNonNull(suffix);
    return (SerializablePattern) str -> str.endsWith(suffix)
        ? Optional.of(new Substring(str, str.length() - suffix.length(), str.length()))
        : Optional.empty();
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code c}. */
  public static Pattern first(char c) {
    return (SerializablePattern) str -> substring(str, str.indexOf(c), 1);
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code snippet}. */
  public static Pattern first(String snippet) {
    requireNonNull(snippet);
    return (SerializablePattern) str -> substring(str, str.indexOf(snippet), snippet.length());
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}.
   *
   * <p>Unlike {@code str.replaceFirst(regexPattern, replacement)},
   * <pre>regex(regexPattern).replaceFrom(str, replacement)</pre> treats the {@code replacement} as a literal
   * string with no special handling of backslash (\) and dollar sign ($) characters.
   */
  public static Pattern regex(java.util.regex.Pattern regexPattern) {
    return regexGroup(regexPattern, 0);
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}.
   *
   * <p>Unlike {@code str.replaceFirst(regexPattern, replacement)},
   * <pre>regex(regexPattern).replaceFrom(str, replacement)</pre> treats the {@code replacement} as a literal
   * string with no special handling of backslash (\) and dollar sign ($) characters.
   *
   * <p>Because this method internally compiles {@code regexPattern}, it's more efficient to reuse the
   * returned {@link Pattern} object than calling {@code regex(regexPattern)} repetitively.
   */
  public static Pattern regex(String regexPattern) {
    return regex(java.util.regex.Pattern.compile(regexPattern));
  }

  /**
   * Returns a {@code Pattern} that matches capturing {@code group} of {@code regexPattern}.
   *
   * <p>The returned {@code Pattern} will throw {@link IndexOutOfBoundsException} when matched against
   * strings without the target {@code group}.
   */
  public static Pattern regexGroup(java.util.regex.Pattern regexPattern, int group) {
    requireNonNull(regexPattern);
    if (group < 0) throw new IllegalArgumentException("group cannot be negative: " + group);
    return (SerializablePattern) str -> {
      java.util.regex.Matcher matcher = regexPattern.matcher(str);
      if (matcher.find()) {
        return Optional.of(new Substring(str, matcher.start(group), matcher.end(group)));
      } else {
        return Optional.empty();
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches capturing {@code group} of {@code regexPattern}.
   *
   * <p>Unlike {@code str.replaceFirst(regexPattern, replacement)},
   * <pre>regexGroup(regexPattern, group).replaceFrom(str, replacement)</pre> treats the {@code replacement}
   * as a literal string with no special handling of backslash (\) and dollar sign ($) characters.
   *
   * <p>Because this method internally compiles {@code regexPattern}, it's more efficient to reuse the
   * returned {@link Pattern} object than calling {@code regexGroup(regexPattern, group)} repetitively.
   *
   * <p>The returned {@code Pattern} will throw {@link IndexOutOfBoundsException} when matched against
   * strings without the target {@code group}.
   */
  public static Pattern regexGroup(String regexPattern, int group) {
    return regexGroup(java.util.regex.Pattern.compile(regexPattern), group);
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code c}. */
  public static Pattern last(char c) {
    return (SerializablePattern) str -> substring(str, str.lastIndexOf(c), 1);
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code snippet}. */
  public static Pattern last(String snippet) {
    requireNonNull(snippet);
    return (SerializablePattern) str -> substring(str, str.lastIndexOf(snippet), snippet.length());
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

  /** Returns a new string with {@code this} substring replaced by {@code replacement}. */
  public String replaceWith(char replacement) {
    return before() + replacement + after();
  }

  /** Returns a new string with {@code this} substring replaced by {@code replacement}. */
  public String replaceWith(CharSequence replacement) {
    requireNonNull(replacement);
    return before() + replacement + after();
  }

  /** Returns the starting index of this substring in the containing string. */
  public int index() {
    return startIndex;
  }

  /** Returns the length of this substring. */
  public int length() {
    return endIndex - startIndex;
  }

  /** Returns this substring. */
  @Override public String toString() {
    return context.substring(startIndex, endIndex);
  }

  @Override public int hashCode() {
    return context.hashCode();
  }

  /** Two {@code Substring} instances are equal if they are the same sub sequences of equal strings. */
  @Override public boolean equals(Object obj) {
    if (obj instanceof Substring) {
      Substring that = (Substring) obj;
      return startIndex == that.startIndex && endIndex == that.endIndex && context.equals(that.context);
    }
    return false;
  }

  /** A substring pattern that can be matched against a string to find substrings. */
  public interface Pattern {

    /** Finds the substring in {@code string} or returns {@code empty()} if not found. */
    Optional<Substring> in(String string);

    /**
     * Returns a new string with the substring matched by {@code this} removed. Returns {@code string} as is
     * if a substring is not found.
     */
    default String removeFrom(String string) {
      return in(string).map(Substring::remove).orElse(string);
    }

    /**
     * Returns a new string with the substring matched by {@code this} replaced by {@code replacement}.
     * Returns {@code string} as is if a substring is not found.
     */
    default String replaceFrom(String string, char replacement) {
      return in(string).map(sub -> sub.replaceWith(replacement)).orElse(string);
    }

    /**
     * Returns a new string with the substring matched by {@code this} replaced by {@code replacement}.
     * Returns {@code string} as is if a substring is not found.
     */
    default String replaceFrom(String string, CharSequence replacement) {
      requireNonNull(replacement);
      return in(string).map(sub -> sub.replaceWith(replacement)).orElse(string);
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

  private enum Constants implements Pattern {
    NONE {
      @Override public Optional<Substring> in(String s) {
        requireNonNull(s);
        return Optional.empty();
      }
    },
    ALL {
      @Override public Optional<Substring> in(String s) {
        return Optional.of(new Substring(s, 0, s.length()));
      }
    }
  }

  private interface SerializablePattern extends Pattern, Serializable {}
}
/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiCollectors;

/**
 * Utilities for creating patterns that attempt to match a substring in an input string. The matched
 * substring can be {@link Pattern#from extracted}, {@link Pattern#removeFrom removed}, {@link
 * Pattern#replaceFrom replaced}, or used to divide the input string into parts.
 *
 * <p>For example, to strip off the "http://" prefix from a uri string if present:
 *
 * <pre>
 *   static String stripHttp(String uri) {
 *     return Substring.prefix("http://").removeFrom(uri);
 *   }
 * </pre>
 *
 * To strip off either an "http://" or "https://" prefix if present:
 *
 * <pre>
 *   static import com.google.common.labs.base.Substring.prefix;
 *
 *   static String stripHttpOrHttps(String uri) {
 *     return prefix("http://").or(prefix("https://")).removeFrom(uri);
 *   }
 * </pre>
 *
 * To strip off a suffix starting with a dash (-) character:
 *
 * <pre>
 *   static String stripDashSuffix(String str) {
 *     return last('-').toEnd().removeFrom(str);
 *   }
 * </pre>
 *
 * To replace a trailing "//" with "/":
 *
 * <pre>
 *   static String fixTrailingSlash(String str) {
 *     return Substring.suffix("//").replaceFrom(str, '/');
 *   }
 * </pre>
 *
 * To extract the 'name' and 'value' from an input string in the format of "name:value":
 *
 * <pre>
 *   String str = ...;
 *   Substring.Match colon = Substring.first(':').in(str).orElseThrow(BadFormatException::new);
 *   String name = colon.before();
 *   String value = colon.after();
 * </pre>
 *
 * To split a stream of strings into key-value pairs:
 *
 * <pre>{@code
 *   ImmutableListMultimap<String, String> tags = lines.stream()
 *       .collect(first(':').splittingTrimmed(ImmutableListMultimap::toImmutableListMultimap));
 * }</pre>
 *
 * @since 2.0
 */
public final class Substring {

  /** {@code Pattern} that never matches any substring. */
  public static final Pattern NONE = new Pattern() {
    @Override public Match match(String s) {
      requireNonNull(s);
      return null;
    }
  };

  /**
   * {@code Pattern} that matches the empty substring at the beginning of the input string.
   *
   * @since 2.1
   */
  public static final Pattern BEGINNING = new Pattern() {
    @Override public Match match(String s) {
      requireNonNull(s);
      return new Match(s, 0, 0);
    }
  };

  /**
   * {@code Pattern} that matches the empty substring at the end of the input string.
   *
   * @since 2.1
   */
  public static final Pattern END = new Pattern() {
    @Override public Match match(String s) {
      return new Match(s, s.length(), s.length());
    }
  };

  /** Returns a {@code Pattern} that matches strings starting with {@code prefix}. */
  public static Pattern prefix(String prefix) {
    requireNonNull(prefix);
    return new Pattern() {
      @Override Match match(String input) {
        return input.startsWith(prefix) ? new Match(input, 0, prefix.length()) : null;
      }
    };
  }

  /** Returns a {@code Pattern} that matches strings starting with {@code prefix}. */
  public static Pattern prefix(char prefix) {
    return prefix(Character.toString(prefix));
  }

  /** Returns a {@code Pattern} that matches strings ending with {@code suffix}. */
  public static Pattern suffix(String suffix) {
    requireNonNull(suffix);
    return new Pattern() {
      @Override Match match(String input) {
        return input.endsWith(suffix)
            ? new Match(input, input.length() - suffix.length(), input.length())
            : null;
      }
    };
  }

  /** Returns a {@code Pattern} that matches strings ending with {@code suffix}. */
  public static Pattern suffix(char suffix) {
    return suffix(Character.toString(suffix));
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code c}. */
  public static Pattern first(char c) {
    return new Pattern() {
      @Override Match match(String input) {
        return matchIfValidIndex(input, input.indexOf(c), 1);
      }
    };
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code str}. */
  public static Pattern first(String str) {
    requireNonNull(str);
    return new Pattern() {
      @Override Match match(String input) {
        return matchIfValidIndex(input, input.indexOf(str), str.length());
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}.
   *
   * <p>Unlike {@code str.replaceFirst(regexPattern, replacement)},
   * <pre>first(regexPattern).replaceFrom(str, replacement)</pre> treats the {@code replacement} as a literal
   * string with no special handling of backslash (\) and dollar sign ($) characters.
   *
   * @since 2.2
   */
  public static Pattern first(java.util.regex.Pattern regexPattern) {
    return first(regexPattern, 0);
  }

  /**
   * Returns a {@code Pattern} that matches capturing {@code group} of {@code regexPattern}.
   *
   * @throws IndexOutOfBoundsException if {@code group} is negative or exceeds the number of
   *         capturing groups in {@code regexPattern}.
   *
   * @since 2.2
   */
  public static Pattern first(java.util.regex.Pattern regexPattern, int group) {
    requireNonNull(regexPattern);
    if (group < 0 || group > 0 && group > regexPattern.matcher("").groupCount()) {
      throw new IndexOutOfBoundsException("Capturing group " + group + " doesn't exist.");
    }
    return new Pattern() {
      @Override Match match(String input) {
        java.util.regex.Matcher matcher = regexPattern.matcher(input);
        return matcher.find() ? new Match(input, matcher.start(group), matcher.end(group)) : null;
      }
    };
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code c}. */
  public static Pattern last(char c) {
    return new Pattern() {
      @Override Match match(String input) {
        return matchIfValidIndex(input, input.lastIndexOf(c), 1);
      }
    };
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code str}. */
  public static Pattern last(String str) {
    requireNonNull(str);
    return new Pattern() {
      @Override Match match(String input) {
        return matchIfValidIndex(input, input.lastIndexOf(str), str.length());
      }
    };
  }

  /**
   * Returns a {@code Pattern} that will match from the beginning of the original string up to
   * the substring matched by {@code pattern} <em>inclusively</em>. For example:
   *
   * <pre>
   *   String uri = "http://google.com";
   *   String schemeStripped = upToIncluding(first("://")).removeFrom(uri);
   *   assertThat(schemeStripped).isEqualTo("google.com");
   * </pre>
   *
   * <p>To match from the start of {@code pattern} to the end of the original string, use
   * {@link Pattern#toEnd} instead.
   *
   * @since 2.2
   */
  public static Pattern upToIncluding(Pattern endingPoint) {
    return endingPoint.map(Match::fromBeginning);
  }

  /**
   * Returns a {@code Pattern} that will match the substring between {@code open} and {@code close}.
   * For example the following pattern finds the link text in markdown syntax:
   * <pre>
   *   private static final Substring.Pattern DEPOT_PATH =
   *       Substring.between(first("//depot/"), last('/'));
   *   String path = DEPOT_PATH.from("//depot/google3/foo/bar/baz.txt").get();
   *   assertThat(path).isEqualTo("google3/foo/bar");
   * </pre>
   *
   * <p>Note that the {@code close} pattern is matched against the substring after the end of
   * {@code open}. For example: <pre>
   *   String dir1 = Substring.between(first('/'), first('/')).from("/usr/home/abc").get();
   *   assertThat(dir1).isEqualTo("usr");
   * </pre>
   *
   * @since 2.1
   */
  public static Pattern before(Pattern delimiter) {
    return delimiter.map(Match::preceding);
  }

  /**
   * Returns a {@code Pattern} that covers the substring after {@code delimiter}. For example:
   *
   * <pre>
   *   String file = "/home/path/file.txt";
   *   String ext = Substring.after(last('.')).from(file).orElseThrow(...);
   *   assertThat(ext).isEqualTo("txt");
   * </pre>
   *
   * @since 2.1
   */
  public static Pattern after(Pattern delimiter) {
    return delimiter.map(Match::following);
  }

  /**
   * Returns a {@code Pattern} that will match the substring between {@code open} and {@code close}.
   * For example: <pre>
   *   String quoted = Substring.between(first('('), first(')'))
   *       .from(input)
   *       .orElseThrow(...);
   * </pre>
   *
   * @since 2.1
   */
  public static Pattern between(Pattern open, Pattern close) {
    requireNonNull(open);
    requireNonNull(close);
    return new Pattern() {
      @Override Match match(String input) {
        Match left = open.match(input);
        if (left == null) return null;
        Match right = close.match(input.substring(left.endIndex));
        if (right == null) return null;
        return new Match(input, left.endIndex, left.endIndex + right.startIndex);
      }
    };
  }

  /** A substring pattern that can be matched against a string to find substrings. */
  public static abstract class Pattern {
    /** Matches against {@code string} and returns null if not found. */
    abstract Match match(String input);

    /**
     * Matches this pattern against {@code string}, returning a {@code Match} if successful, or
     * {@code empty()} otherwise.
     *
     * <p>This is useful if you need to call {@link Match} methods, like {@link Match#remove} or
     * {@link Match#before}. If you just need the matched substring itself, prefer to use
     * {@link #from} instead.
     */
    public final Optional<Match> in(String string) {
      return Optional.ofNullable(match(string));
    }

    /**
     * Matches this pattern against {@code string}, returning the matched substring if successful,
     * or {@code empty()} otherwise. {@code pattern.from(str)} is equivalent to
     * {@code pattern.in(str).map(Object::toString)}.
     *
     * <p>This is useful if you only need the matched substring itself. Use {@link #in} if you need
     * to call {@link Match} methods, like {@link Match#remove} or {@link Match#before}.
     *
     * @since 2.1
     */
    public final Optional<String> from(String string) {
      return Optional.ofNullable(Objects.toString(match(string), null));
    }

    /**
     * Matches this pattern against {@code string}, and returns a copy with the matched substring
     * removed if successful. Otherwise, returns {@code string} unchanged.
     */
    public final String removeFrom(String string) {
      Match match = match(string);
      return match == null ? string : match.remove();
    }

    /**
     * Returns a new string with the substring matched by {@code this} replaced by {@code
     * replacement}. Returns {@code string} as-is if a substring is not found.
     */
    public final String replaceFrom(String string, CharSequence replacement) {
      requireNonNull(replacement);
      Match match = match(string);
      return match == null ? string : match.replaceWith(replacement);
    }

    /**
     * Returns a {@code Pattern} that fall backs to using {@code that} if {@code this} fails to
     * match.
     */
    public final Pattern or(Pattern that) {
      requireNonNull(that);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input) {
          Match match = base.match(input);
          return match == null ? that.match(input) : match;
        }
      };
    }

    /**
     * Returns a {@code Pattern} that will match from the substring matched by {@code this} to the
     * end of the input string. For example:
     *
     * <pre>
     *   String line = "return foo; // some comment...";
     *   String commentRemoved = first("//").toEnd().removeFrom(line).trim();
     *   assertThat(commentRemoved).isEqualTo("return foo;");
     * </pre>
     *
     * <p>To match from the beginning of the input string to the end of a pattern, use
     * {@link Substring#upToIncluding} instead.
     *
     * @since 2.2
     */
    public final Pattern toEnd() {
      return map(Match::toEnd);
    }

    /**
     * Returns a {@code Collector} that splits each string from a stream into two parts, with the
     * matched substring as the separator. For example:
     *
     * <pre>{@code
     * ImmutableSetMultimap<String, String> keyValues =
     *     readLines(file, UTF_8).stream()
     *         .collect(first(':').splitting(ImmutableSetMultimap::toImmutableSetMultimap));
     * }</pre>
     *
     * <p>If you need to trim the key-value pairs, you can use {@link #splittingTrimmed
     * splittingTrimmed()}.
     *
     * <p>For more flexible use cases, such as transforming the keys or values to another type, one
     * approach is to collect the pairs into a {@code BiStream} first and then chain away:
     *
     * <pre>{@code
     * ImmutableSetMultimap<Key, Value> keyValues =
     *     readLines(file, UTF_8).stream()
     *         .collect(first(':').splitting(BiStream::toBiStream))
     *         .mapKeys(Key::of)
     *         .mapValues(Value::of)
     *         .collect(ImmutableSetMultimap::toImmutableSetMultimap);
     * }</pre>
     *
     * <p>Alternatively, use the {@link #in} method to operate on the {@link Match} objects, which
     * also allows custom handling of the error of not finding the separator, as well as the ability
     * to report the offending line number:
     *
     * <pre>{@code
     * ImmutableSetMultimap<Key, Value> keyValues =
     *     BiStream.zip(MoreStreams.indexFrom(1), readLines(file, UTF_8).stream())
     *         .mapValues(first(':')::in)
     *         .mapValues((line, m) ->
     *             m.orElseThrow(() -> new InvalidArgumentException("Invalid line at " + line)));
     *         .mapKeys((line, m) -> Key.of(m.before().trim()))
     *         .mapValues(m -> Value.of(m.after().trim()))
     *         .collect(ImmutableSetMultimap::toImmutableSetMultimap);
     * }</pre>
     * 
     * The latter approach is also more efficient because it doesn't need to copy the key value
     * pairs into a temporary {@code BiStream}.
     *
     * <p>This method offers similar functionality to that of {@link
     * com.google.common.base.Splitter.MapSplitter#split}, with a few differences:
     *
     * <ul>
     *   <li>{@code MapSplitter} splits to a {@code Map}, while any arbitrary {@link BiCollector}
     *       can be used with this method to collect to custom objects, including but not limited to
     *       multimaps, {@code ImmutableBimap}, {@code BiStream} etc.
     *   <li>{@code MapSplitter} disallows duplicate keys, while with this method it's up to the
     *       passed-in {@code BiCollector} to reject or allow duplicate keys.
     *   <li>{@code MapSplitter} requires exactly one occurrence of the separator; the returned
     *       Collector allows more than one occurrences. {@code first('=').splitting()} will split
     *       "--args=a=b,c=d" into "--args" and "a=b,c=d"; and {@code last('.').splitting()} will
     *       split "my.file.txt" into "my.file" and "txt".
     * </ul>
     *
     * @since 4.6
     */
    public final <T> Collector<String, ?, T> splitting(
        BiCollector<? super String, ? super String, T> downstream) {
      return Collectors.mapping(this::doSplit, downstream.splitting(Match::before, Match::after));
    }

    /**
     * Similar to {@link #splitting}, returns a {@code Collector} that splits each string from a
     * stream into two parts, with the matched substring as the separator, but also with each part
     * trimmed. For example:
     *
     * <pre>{@code
     * ImmutableSetMultimap<String, String> keyValues =
     *     readLines(file, UTF_8).stream()
     *         .collect(first(':').splittingTrimmed(ImmutableSetMultimap::toImmutableSetMultimap));
     * }</pre>
     *
     * @since 4.6
     */
    public final <T> Collector<String, ?, T> splittingTrimmed(
        BiCollector<? super String, ? super String, T> downstream) {
      return splitting(BiCollectors.mapping((n, v) -> n.trim(), (n, v) -> v.trim(), downstream));
    }

    private Match doSplit(String s) {
      Match match = match(s);
      if (match == null) {
        throw new IllegalArgumentException("Failed to split '" + s + "'.");
      }
      return match;
    }

    private Pattern map(UnaryOperator<Match> mapper) {
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input) {
          Match match = base.match(input);
          return match == null ? null : mapper.apply(match);
        }
      };
    }
  }

  /**
   * The result of successfully matching a {@link Pattern} against a string, providing access to the
   * {@link #toString matched substring}, to the parts of the string {@link #before before} and
   * {@link #after after} it, and to copies with the matched substring {@link #remove removed} or
   * {@link #replaceWith replaced}.
   *
   * <p><em>Note:</em> a {@link Match} is a view of the original string and holds a strong
   * reference to the original string. It's advisable to construct and use a {@code Match} object
   * within the scope of a method; holding onto a {@code Match} object has the same risk of leaking
   * memory as holding onto the string it was produced from.
   *
   * @since 2.2
   */
  public static final class Match implements CharSequence {
    private final String context;
    private final int startIndex;
    private final int endIndex;

    private Match(String context, int startIndex, int endIndex) {
      this.context = context;
      this.startIndex = startIndex;
      this.endIndex = endIndex;
    }

    /**
     * Returns the part of the original string before the matched substring.
     *
     * <p>{@link #before} and {@link #after} are almost always used together to split a string
     * into two parts. If you just need the substring before the match, you might want to use
     * {@code Substring.before(pattern)} instead, because the pattern logic is encoded entirely in
     * the {@link Pattern} object. For example: <pre>
     *   private static final Substring.Pattern DIRECTORY = Substring.before(last("/"));
     * </pre>
     *
     * @since 4.6
     */
    public String before() {
      return context.substring(0, startIndex);
    }

    /** @deprecated Use {@link #before} instead. */
    @Deprecated
    public String getBefore() {
      return before();
    }

    /**
     * Returns the part of the original string before the matched substring.
     *
     * <p>{@link #before} and {@link #after} are almost always used together to split a string
     * into two parts. If you just need the substring after the match, you might want to use
     * {@code Substring.after(pattern)} instead, because the pattern logic is encoded entirely in
     * the {@link Pattern} object. For example: <pre>
     *   private static final Substring.Pattern LINE_COMMENT = Substring.after(first("//"));
     * </pre>
     *
     * @since 4.6
     */
    public String after() {
      return context.substring(endIndex);
    }

    /** @deprecated Use {@link #after} instead. */
    @Deprecated
    public String getAfter() {
      return after();
    }

    /**
     * Returns a copy of the original string with the matched substring removed.
     *
     * <p>This is equivalent to {@code match.before() + match.after()}.
     */
    public String remove() {
      if (endIndex == context.length()) {
        return before();
      } else if (startIndex == 0) {
        return after();
      } else {
        return before() + after();
      }
    }

    /**
     * Returns a copy of the original string with the matched substring replaced with {@code
     * replacement}.
     */
    public String replaceWith(CharSequence replacement) {
      requireNonNull(replacement);
      return before() + replacement + after();
    }
  
    /**
     * Returns the starting index of this substring in the containing string.
     *
     * @since 4.6
     */
    public int index() {
      return startIndex;
    }
  
    /** @deprecated Use {@link #index} instead. */
    @Deprecated
    public int getIndex() {
      return index();
    }

    /** Returns the length of the matched substring. */
    @Override public int length() {
      return endIndex - startIndex;
    }

    @Override
    public char charAt(int i) {
      if (i < 0 || i >= length()) {
        throw new IndexOutOfBoundsException("Invalid index: " + i);
      }
      return context.charAt(startIndex + i);
    }
  
    @Override public CharSequence subSequence(int begin, int end) {
      if (begin < 0) {
        throw new IndexOutOfBoundsException("Invalid index: " + begin);
      }
      if (end > length()) {
        throw new IndexOutOfBoundsException("Invalid index: " + end);
      }
      if (begin > end) {
        throw new IndexOutOfBoundsException("Invalid index: " + begin + " > " + end);
      }
      return new Match(context, startIndex + begin, startIndex + end);
    }

    /** Returns the matched substring. */
    @Override public String toString() {
      return context.substring(startIndex, endIndex);
    }

    private Match preceding() {
      return new Match(context, 0, startIndex);
    }

    private Match following() {
      return new Match(context, endIndex, context.length());
    }

    private Match fromBeginning() {
      return new Match(context, 0, endIndex);
    }

    private Match toEnd() {
      return new Match(context, startIndex, context.length());
    }
  }

  private static Match matchIfValidIndex(String input, int index, int length) {
    return index >= 0 ? new Match(input, index, index + length) : null;
  }
}
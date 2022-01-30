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
import static java.util.stream.Collectors.toList;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.MoreStreams;

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
 *   static import com.google.util.Substring.prefix;
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
 *   Substring.first(':')
 *       .split("name:joe")
 *       .map(NameValue::new)
 *       .orElseThrow(BadFormatException::new);
 * </pre>
 *
 * To parse key-value pairs:
 *
 * <pre>{@code
 * import static com.google.mu.util.stream.GuavaCollectors.toImmutableListMultimap;
 *
 * ImmutableListMultimap<String, String> tags =
 *     first(',')
 *         .repeatedly()
 *         .splitThenTrimKeyValuesAround(first('='), "k1=v1, k2=v2")  // => [(k1, v1), (k2, v2)]
 *         .collect(toImmutableListMultimap());
 * }</pre>
 *
 * @since 2.0
 */
public final class Substring {

  /** {@code Pattern} that never matches any substring. */
  public static final Pattern NONE = new Pattern() {
    @Override Match match(String s, int fromIndex) {
      requireNonNull(s);
      return null;
    }
    @Override public String toString() {
      return "NONE";
    }
  };
  /**
   * {@code Pattern} that matches the empty substring at the beginning of the input string.
   * Typically used to represent an optional delimiter. For example, the following pattern matches
   * the substring after optional "header_name=":
   *
   * <pre>
   * static final Substring.Pattern VALUE = Substring.after(first('=').or(BEGINNING));
   * </pre>
   */
  public static final Pattern BEGINNING =
      new Pattern() {
        @Override Match match(String str, int fromIndex) {
          return new Match(str, fromIndex, 0);
        }

        @Override public String toString() {
          return "BEGINNING";
        }
      };

  /**
   * {@code Pattern} that matches the empty substring at the end of the input string. Typically used
   * to represent an optional delimiter. For example, the following pattern matches the text between
   * the first occurrence of the string "id=" and the end of that line, or the end of the string:
   *
   * <pre>
   * static final Substring.Pattern ID =
   *     Substring.between(substring("id="), substring("\n").or(END));
   * </pre>
   */
  public static final Pattern END =
      new Pattern() {
        @Override Match match(String str, int fromIndex) {
          return new Match(str, str.length(), 0);
        }

        @Override public String toString() {
          return "END";
        }
      };

  /**
   * Returns a {@code Prefix} pattern that matches strings starting with {@code prefix}.
   *
   * <p>Typically if you have a {@code String} constant representing a prefix, consider to declare a
   * {@link Prefix} constant instead. The type is more explicit, and utilitiy methods like {@link
   * Pattern#removeFrom}, {@link Pattern#from} are easier to discover and use.
   */
  public static Prefix prefix(String prefix) {
    return new Prefix(requireNonNull(prefix));
  }

  /**
   * Returns a {@code Prefix} pattern that matches strings starting with {@code prefix}.
   *
   * <p>Typically if you have a {@code char} constant representing a prefix, consider to declare a
   * {@link Prefix} constant instead. The type is more explicit, and utilitiy methods like {@link
   * Pattern#removeFrom}, {@link Pattern#from} are easier to discover and use.
   */
  public static Prefix prefix(char prefix) {
    return new Prefix(Character.toString(prefix));
  }

  /**
   * Returns a {@code Suffix} pattern that matches strings ending with {@code suffix}.
   *
   * <p>Typically if you have a {@code String} constant representing a suffix, consider to declare a
   * {@link Suffix} constant instead. The type is more explicit, and utilitiy methods like {@link
   * Pattern#removeFrom}, {@link Pattern#from} are easier to discover and use.
   */
  public static Suffix suffix(String suffix) {
    return new Suffix(requireNonNull(suffix));
  }

  /**
   * Returns a {@code Suffix} pattern that matches strings ending with {@code suffix}.
   *
   * <p>Typically if you have a {@code char} constant representing a suffix, consider to declare a
   * {@link Suffix} constant instead. The type is more explicit, and utilitiy methods like {@link
   * Pattern#removeFrom}, {@link Pattern#from} are easier to discover and use.
   */
  public static Suffix suffix(char suffix) {
    return new Suffix(Character.toString(suffix));
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code str}. */
  public static Pattern first(String str) {
    if (str.length() == 1) {
      return first(str.charAt(0));
    }
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int index = input.indexOf(str, fromIndex);
        return index >= 0 ? new Match(input, index, str.length()) : null;
      }

      @Override public String toString() {
        return "first('" + str + "')";
      }
    };
  }

  /** Returns a {@code Pattern} that matches the first occurrence of {@code character}. */
  public static Pattern first(char character) {
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int index = input.indexOf(character, fromIndex);
        return index >= 0 ? new Match(input, index, 1) : null;
      }

      @Override public String toString() {
        return "first(\'" + character + "\')";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern}.
   *
   * <p>Unlike {@code str.replaceFirst(regexPattern, replacement)},
   *
   * <pre>first(regexPattern).replaceFrom(str, replacement)</pre>
   *
   * treats the {@code replacement} as a literal string, with no special handling of backslash (\)
   * and dollar sign ($) characters.
   */
  public static Pattern first(java.util.regex.Pattern regexPattern) {
    return first(regexPattern, 0);
  }

  /**
   * Returns a repeating pattern representing all the top-level groups from {@code regexPattern}.
   * If {@code regexPattern} has no capture group, the entire pattern is considered the only group.
   *
   * <p>For example, {@code topLevelGroups(compile("(g+)(o+)")).from("ggooo")} will return
   * {@code ["gg", "ooo"]}.
   *
   * <p>Nested capture groups are not taken into account. For example: {@code
   * topLevelGroups(compile("((foo)+(bar)*)(zoo)")).from("foofoobarzoo")} will return
   * {@code ["foofoobar", "zoo"]}.
   *
   * <p>Note that the top-level groups are statically determined by the {@code regexPattern}.
   * Particularly, quantifiers on a capture group do not increase or decrease the number of captured
   * groups. That is, when matching {@code "(foo)+"} against {@code "foofoofoo"}, there will only
   * be one top-level group, with {@code "foo"} as the value.
   *
   * @since 5.3
   */
  public static RepeatingPattern topLevelGroups(java.util.regex.Pattern regexPattern) {
    requireNonNull(regexPattern);
    return new RepeatingPattern() {
      @Override
      public Stream<Match> match(String string) {
        Matcher matcher = regexPattern.matcher(string);
        if (!matcher.find()) return Stream.empty();
        int groups = matcher.groupCount();
        if (groups == 0) {
          return Stream.of(new Match(string, matcher.start(), matcher.end() - matcher.start()));
        } else {
          return MoreStreams.whileNotNull(new Supplier<Match>() {
            private int next = 0;
            private int g = 1;

            @Override public Match get() {
              for (; g <= groups; g++) {
                int start = matcher.start(g);
                int end = matcher.end(g);
                if (start >= next) {
                  next = end;
                  return new Match(string, start, end - start);
                }
              }
              return null;
            }
          });
        }
      }

      @Override
      public String toString() {
        return "topLevelGroups(" + regexPattern + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code regexPattern} and then
   * selects the capturing group identified by {@code group}.
   *
   * <p>For example, the following pattern finds the shard number (12) from a string like {@code
   * 12-of-99}:
   *
   * <pre>
   *   import java.util.regex.Pattern;
   *
   *   private static final Substring.Pattern SHARD_NUMBER =
   *       Substring.first(Pattern.compile("(\\d+)-of-\\d+"), 1);
   * </pre>
   *
   * @throws IndexOutOfBoundsException if {@code group} is negative or exceeds the number of
   *     capturing groups in {@code regexPattern}.
   */
  public static Pattern first(java.util.regex.Pattern regexPattern, int group) {
    requireNonNull(regexPattern);
    if (group < 0 || (group > 0 && group > regexPattern.matcher("").groupCount())) {
      throw new IndexOutOfBoundsException("Capturing group " + group + " doesn't exist.");
    }
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        CharSequence remaining =
            fromIndex == 0 ? input : new Match(input, fromIndex, input.length() - fromIndex);
        Matcher matcher = regexPattern.matcher(remaining);
        if (matcher.find()) {
          int start = matcher.start(group);
          return new Match(
              input, fromIndex + start, matcher.end(group) - start, fromIndex + matcher.end());
        }
        return null;
      }

      @Override public String toString() {
        return "first(\"" + regexPattern + "\", " + group + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code stop1}, followed by an
   * occurrence of {@code stop2}, followed sequentially by occurrences of {@code moreStops} in
   * order, including any characters between consecutive stops.
   */
  public static Pattern spanningInOrder(String stop1, String stop2, String... moreStops) {
    Pattern result = first(stop1).spanTo(first(stop2));
    for (String stop : moreStops) {
      result = result.spanTo(first(stop));
    }
    return result;
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code str}. */
  public static Pattern last(String str) {
    if (str.length() == 1) {
      return last(str.charAt(0));
    }
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int index = input.lastIndexOf(str);
        return index >= fromIndex ? new Match(input, index, str.length()) : null;
      }

      @Override public String toString() {
        return "last('" + str + "')";
      }
    };
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code character}. */
  public static Pattern last(char character) {
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int index = input.lastIndexOf(character);
        return index >= fromIndex ? new Match(input, index, 1) : null;
      }

      @Override public String toString() {
        return "last('" + character + "')";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that covers the substring before {@code delimiter}. For example:
   *
   * <pre>
   *   String file = "/home/path/file.txt";
   *   String path = Substring.before(last('/')).from(file).orElseThrow(...);
   *   assertThat(path).isEqualTo("/home/path");
   * </pre>
   */
  public static Pattern before(Pattern delimiter) {
    requireNonNull(delimiter);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        Match match = delimiter.match(input, fromIndex);
        return match == null
            ? null
            // For example when matching before(first("//")) against "http://", there should be
            // only one iteration, which is "http:". If the next scan starts before //, we'd get
            // an empty string match.
            : new Match(input, fromIndex, match.startIndex - fromIndex, match.repetitionStartIndex);
      }

      @Override public String toString() {
        return "before(" + delimiter + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that covers the substring after {@code delimiter}. For example:
   *
   * <pre>
   *   String file = "/home/path/file.txt";
   *   String ext = Substring.after(last('.')).from(file).orElseThrow(...);
   *   assertThat(ext).isEqualTo("txt");
   * </pre>
   */
  public static Pattern after(Pattern delimiter) {
    requireNonNull(delimiter);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        Match match = delimiter.match(input, fromIndex);
        return match == null ? null : match.following();
      }

      @Override public String toString() {
        return "after(" + delimiter + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that will match from the beginning of the original string up to the
   * substring matched by {@code pattern} <em>inclusively</em>. For example:
   *
   * <pre>
   *   String uri = "http://google.com";
   *   String schemeStripped = upToIncluding(first("://")).removeFrom(uri);
   *   assertThat(schemeStripped).isEqualTo("google.com");
   * </pre>
   *
   * <p>To match from the start of {@code pattern} to the end of the original string, use {@link
   * Pattern#toEnd} instead.
   */
  public static Pattern upToIncluding(Pattern pattern) {
    requireNonNull(pattern);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        Match match = pattern.match(input, fromIndex);
        return match == null
            ? null
            // Do not include the delimiter pattern in the next iteration.
            : new Match(input, fromIndex, match.endIndex - fromIndex, match.repetitionStartIndex);
      }

      @Override public String toString() {
        return "upToIncluding(" + pattern + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that will match the substring between the first {@code open} and the
   * first {@code close} after it.
   *
   * <p>If for example you need to find the substring between the first {@code "<-"} and the
   * <em>last</em> {@code "->"}, use {@code between(first("<-"), last("->"))} instead.
   *
   * @since 6.0
   */
  public static Pattern between(String open, String close) {
    return between(first(open), first(close));
  }

  /**
   * Returns a {@code Pattern} that will match the substring between the first {@code open} and the
   * first {@code close} after it.
   *
   * <p>If for example you need to find the substring between the first and the <em>last</em> {@code
   * '/'}, use {@code between(first('/'), last('/'))} instead.
   *
   * @since 6.0
   */
  public static Pattern between(char open, char close) {
    return between(first(open), first(close));
  }

  /**
   * Returns a {@code Pattern} that will match the substring between {@code open} and {@code close}.
   * For example the following pattern finds the link text in markdown syntax:
   *
   * <pre>
   *   private static final Substring.Pattern DEPOT_PATH =
   *       Substring.between(first("//depot/"), last('/'));
   *   assertThat(DEPOT_PATH.from("//depot/google3/foo/bar/baz.txt")).hasValue("google3/foo/bar");
   * </pre>
   */
  public static Pattern between(Pattern open, Pattern close) {
    requireNonNull(open);
    requireNonNull(close);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        Match left = open.match(input, fromIndex);
        if (left == null) {
          return null;
        }
        Match right = close.match(input, left.endIndex);
        if (right == null) {
          return null;
        }
        return new Match(
            // Include the closing delimiter in the next iteration. This allows delimiters in
            // patterns like "/foo/bar/baz/" to be treated more intuitively.
            input, /*startIndex=*/ left.endIndex, /*length=*/ right.startIndex - left.endIndex);
      }

      @Override public String toString() {
        return "between(" + open + ", " + close + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} specified by the {@code format} string with {@code "%s"} as
   * inner pattern placeholders, which are provided through {@code params}.
   *
   * <p>For example, {@code pattern("http://%s/%s?%s", AUTHORITY, PATH, QUERY)} can be used
   * to match a full HTTP URI, where {@code AUTHORITY}, {@code PATH} and {@code QUERY} are
   * pattern objects defined to match uri authority, path and query string respectively.
   *
   * <p>Generally, if a string is formatted with {@code String.format(formatString, "foo", "bar")},
   * it can be matched by {@code pattern(formatString, pattern("foo"), pattern("bar"))}, but only
   * {@code "%s"} placeholder is supported.
   *
   * <p>This method provides a cheap (runtime and memory-wise) alternative to regex for string
   * patterns with simple to medium complexity (no quantifiers, look-behind or backtracking),
   * by composing {@link Pattern} objects together.
   *
   * <p>Pattern matching starts from the beginning of the string, but doesn't need to match to the
   * end of the input string.
   *
   * <p>If the pattern starts with a placeholder {@code "%s"}, then the result match doesn't have to
   * start from the beginning (say, if the corresponding sub-pattern is {@code first('/')}).
   *
   * <p>Note that the result pattern attempts no backtracking, so if any placeholder or the format
   * string in between already matches, the remaining must match or else matching fails. For example,
   * {@code pattern("%sbar", first("foo"))} won't match {@code "foonotbar,foobar"} because after
   * {@code first("foo")} matches, the following {@code "notbar"} aborts the match immediately.
   *
   * <p>No other JDK-style placeholders (like {@code %d}) are supported.
   *
   * <p>Character escaping isn't supported. If the pattern contains literal {@code %s},
   * make it a placeholder with {@code prefix("%s")} as the parameter value.
   *
   * @throws IllegalArgumentException if the number of parameters doesn't match the number of
   *         {@code "%s"} placeholders.
   * @throws NullPointerException if {@code format} or any parameter is null
   * @since 6.0
   */
  public static Pattern pattern(String format, Pattern... params) {
    List<String> fragments =
        first("%s").repeatedly().split(format).map(Match::toString).collect(toList());
    if (fragments.size() != params.length + 1) {
      throw new IllegalArgumentException(
          (fragments.size() - 1) + " %s placeholders in pattern; " + params.length + " parameters provided.");
    }
    if (params.length == 0) { // No params.
      return prefix(format);
    }
    if (fragments.get(0).isEmpty()) {
      // Pattern starts with %s, which means it doesn't necessarily start from the beginning
      Pattern p = params[0].spanImmediately(fragments.get(1));
      for (int i = 1; i < params.length; i++) {
        p = p.spanTo(params[i]).spanImmediately(fragments.get(i + 1));
      }
      return p;
    } else {
      Pattern p = prefix(fragments.get(0));
      for (int i = 0; i < params.length; i++) {
        p = p.then(params[i]).thenImmediately(fragments.get(i + 1));
      }
      return upToIncluding(p);
    }
  }

  /** A pattern that can be matched against a string, finding a single substring from it. */
  public abstract static class Pattern {
    /**
     * Matches this pattern against {@code string}, returning a {@code Match} if successful, or
     * {@code empty()} otherwise.
     *
     * <p>This is useful if you need to call {@link Match} methods, like {@link Match#remove} or
     * {@link Match#before}. If you just need the matched substring itself, prefer to use {@link
     * #from} instead.
     */
    public final Optional<Match> in(String string) {
      return Optional.ofNullable(match(string));
    }

    /**
     * Matches this pattern against {@code string}, returning the matched substring if successful,
     * or {@code empty()} otherwise. {@code pattern.from(str)} is equivalent to {@code
     * pattern.in(str).map(Object::toString)}.
     *
     * <p>This is useful if you only need the matched substring itself. Use {@link #in} if you need
     * to call {@link Match} methods, like {@link Match#remove} or {@link Match#before}.
     */
    public final Optional<String> from(CharSequence string) {
      return Optional.ofNullable(Objects.toString(match(string.toString()), null));
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
     * Returns a {@code Pattern} that will match from the substring matched by {@code this} to the
     * end of the input string. For example:
     *
     * <pre>
     *   String line = "return foo; // some comment...";
     *   String commentRemoved = first("//").toEnd().removeFrom(line).trim();
     *   assertThat(commentRemoved).isEqualTo("return foo;");
     * </pre>
     *
     * <p>To match from the beginning of the input string to the end of a pattern, use {@link
     * Substring#upToIncluding} instead.
     */
    public final Pattern toEnd() {
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match match = base.match(input, fromIndex);
          return match == null ? null : match.toEnd();
        }

        @Override public String toString() {
          return base + ".toEnd()";
        }
      };
    }

    /**
     * Returns a {@code Pattern} that falls back to using {@code that} if {@code this} fails to
     * match.
     */
    public final Pattern or(Pattern that) {
      requireNonNull(that);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match match = base.match(input, fromIndex);
          return match == null ? that.match(input, fromIndex) : match;
        }

        @Override public String toString() {
          return base + ".or(" + that + ")";
        }
      };
    }

    /**
     * Similar to regex lookahead, returns a pattern that matches the {@code following}
     * pattern after it has matched this pattern. For example {@code first('/').then(first('/'))}
     * finds the second '/' character.
     *
     * @since 5.7
     */
    public final Pattern then(Pattern following) {
      requireNonNull(following);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match preceding = base.match(input, fromIndex);
          if (preceding == null) {
            return null;
          }
          Match next = following.match(input, preceding.endIndex);
          if (next == null) {
            return null;
          }
          // Keep the repetitionStartIndex strictly increasing to avoid the next iteration
          // in repeatedly() to be stuck with no progress.
          return next.repetitionStartIndex < preceding.repetitionStartIndex
              ? new Match(input, next.startIndex, next.length(), preceding.repetitionStartIndex)
              : next;
        }

        @Override public String toString() {
          return base + ".then(" + following + ")";
        }
      };
    }

    /** Matches {@code following} string that immediately follows. */
    final Pattern thenImmediately(String following) {
      return following.isEmpty() ? this : then(prefix(following));
    }

    /**
     * Return a {@code Pattern} equivalent to this {@code Pattern}, except it will fail to match
     * if the {@code following} pattern can't find a match in the substring after the current match.
     *
     * <p>Useful in asserting that the current match is followed by the expected pattern. For example:
     * {@code SCHEME_NAME.peek(prefix(':'))} returns the URI scheme name.
     *
     * <p>Note that unlike regex lookahead, no backtracking is attempted. So {@code
     * first("foo").peek("bar")} will match "bafoobar" but won't match "foofoobar" because
     * the first "foo" isn't followed by "bar".
     *
     * @since 6.0
     */
    public final Pattern peek(Pattern following) {
      requireNonNull(following);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match preceding = base.match(input, fromIndex);
          if (preceding == null) {
            return null;
          }
          return following.match(input, preceding.endIndex) == null ? null : preceding;
        }

        @Override public String toString() {
          return base + ".peek(" + following + ")";
        }
      };
    }

    /**
     * Returns a {@code Pattern} that asserts that this pattern must <em>not</em> match the input,
     * in which case an empty match starting at the beginning of the input is returned.
     *
     * <p>Useful when combined with {@link #peek} to support negative lookahead.
     *
     * <p>Note that {@code pattern.not().not()} isn't equivalent to {@code pattern} because the
     * result match is empty.
     *
     * @since 6.0
     */
    public final Pattern not() {
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          return base.match(input, fromIndex) == null ? BEGINNING.match(input, fromIndex) : null;
        }
        @Override public String toString() {
          return base + ".not()";
        }
      };
    }

    /**
     * Return a {@code Pattern} equivalent to this {@code Pattern}, except it will fail to match
     * if it's not followed by the {@code following} string.
     *
     * <p>Useful in asserting that the current match is followed by the expected keyword. For example:
     * {@code SCHEME_NAME.peek(":")} returns the URI scheme name.
     *
     * <p>Note that unlike regex lookahead, no backtracking is attempted. So {@code
     * first("foo").peek("bar")} will match "bafoobar" but won't match "foofoobar".
     *
     * @since 6.0
     */
    public final Pattern peek(String following) {
      return peek(prefix(following));
    }

    /**
     * Matches this pattern and then matches {@code following}.
     * The result matches from the beginning of this pattern to the end of {@code following}.
     */
    final Pattern spanTo(Pattern following) {
      requireNonNull(following);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match preceding = base.match(input, fromIndex);
          if (preceding == null) {
            return null;
          }
          Match next = following.match(input, preceding.endIndex);
          if (next == null) {
            return null;
          }
          return new Match(
              input,
              preceding.startIndex,
              next.endIndex - preceding.startIndex,
              // Keep the repetitionStartIndex strictly increasing to avoid the next iteration
              // in repeatedly() to be stuck with no progress.
              Math.max(preceding.repetitionStartIndex, next.repetitionStartIndex));
        }

        @Override public String toString() {
          return base + ".spanTo(" + following + ")";
        }
      };
    }

    /** Span the match to the {@code following} string that immediately follows. */
    final Pattern spanImmediately(String following) {
      return following.isEmpty() ? this : spanTo(prefix(following));
    }

    /**
     * Splits {@code string} into two parts that are separated by this separator pattern. For
     * example:
     *
     * <pre>{@code
     * Optional<KeyValue> keyValue = first('=').split("name=joe").join(KeyValue::new);
     * }</pre>
     *
     * <p>If you need to trim the key-value pairs, use {@link #splitThenTrim}.
     *
     * <p>To split a string into multiple substrings delimited by a delimiter, use {@link #repeatedly}.
     *
     * @since 5.0
     */
    public final BiOptional<String, String> split(CharSequence string) {
      Match match = match(string.toString());
      return match == null ? BiOptional.empty() : BiOptional.of(match.before(), match.after());
    }

    /**
     * Splits {@code string} into two parts that are separated by this separator pattern, with
     * leading and trailing whitespaces trimmed. For example:
     *
     * <pre>{@code
     * Optional<KeyValue> keyValue = first('=').splitThenTrim("name = joe ").join(KeyValue::new);
     * }</pre>
     *
     * <p>If you are trying to parse a string to a key-value data structure ({@code Map}, {@code
     * Multimap} etc.), you can use {@code com.google.common.base.Splitter.MapSplitter} though it's
     * limited to {@code Map} and doesn't allow duplicate keys:
     *
     * <pre>{@code
     * String toSplit = " x -> y, z-> a ";
     * Map<String, String> result = Splitter.on(',')
     *     .trimResults()
     *     .withKeyValueSeparator(Splitter.on("->"))
     *     .split(toSplit);
     * }</pre>
     *
     * Alternatively, you can use {@code Substring} to allow duplicate keys and to split into
     * multimaps or other types:
     *
     * <pre>{@code
     * import static com.google.mu.util.stream.MoreCollectors.mapping;
     *
     * String toSplit = " x -> y, z-> a, x -> t ";
     * ImmutableListMultimap<String, String> result = first(',')
     *     .repeatedly()
     *     .split(toSplit)
     *     .map(first("->")::splitThenTrim)
     *     .collect(
     *         mapping(
     *             kv -> kv.orElseThrow(...),
     *             ImmutableListMultimap::toImmutableListMultimap));
     * }</pre>
     *
     * <p>To split a string into multiple substrings delimited by a delimiter, use {@link #repeatedly}.
     *
     * @since 5.0
     */
    public final BiOptional<String, String> splitThenTrim(CharSequence string) {
      Match match = match(string.toString());
      return match == null
          ? BiOptional.empty()
          : BiOptional.of(match.before().trim(), match.after().trim());
    }

    /**
     * Returns a {@link RepeatingPattern} that applies this pattern repeatedly against the input
     * string. That is, after each iteration, the pattern is applied again over the substring after
     * the match, repeatedly until no match is found.
     *
     * @since 5.2
     */
    public final RepeatingPattern repeatedly() {
      Pattern repeatable = Pattern.this;
      return new RepeatingPattern() {
        @Override
        public Stream<Match> match(String input) {
          return MoreStreams.whileNotNull(
              new Supplier<Match>() {
                private final int end = input.length();
                private int nextIndex = 0;

                @Override
                public Match get() {
                  if (nextIndex > end) {
                    return null;
                  }
                  Match match = repeatable.match(input, nextIndex);
                  if (match == null) {
                    return null;
                  }
                  if (match.endIndex == end) { // We've consumed the entire string.
                    nextIndex = Integer.MAX_VALUE;
                  } else if (match.repetitionStartIndex > nextIndex) {
                    nextIndex = match.repetitionStartIndex;
                  } else {
                    // instead of being stuck in infinite loop, consider this the end.
                    nextIndex = Integer.MAX_VALUE;
                  }
                  return match;
                }
              });
        }

        @Override
        public Stream<Match> split(String string) {
          if (repeatable.match("") != null) {
            throw new IllegalStateException("Pattern (" + repeatable + ") cannot be used as delimiter.");
          }
          return super.split(string);
        }

        @Override
        public String toString() {
          return repeatable + ".repeatedly()";
        }
      };
    }

    /**
     * Matches against {@code string} starting from {@code fromIndex}, and returns null if not
     * found.
     */
    abstract Match match(String string, int fromIndex);

    private Match match(String string) {
      return match(string, 0);
    }

    /**
     * Do not depend on the string representation of Substring, except for subtypes {@link Prefix}
     * and {@link Suffix} that have an explicitly defined representation.
     */
    @Override
    public String toString() {
      return super.toString();
    }
  }

  /**
   * A substring pattern to be applied repeatedly on the input string, each time over the remaining
   * substring after the previous match.
   *
   * @since 5.2
   */
  public abstract static class RepeatingPattern {
    /**
     * Applies this pattern against {@code string} and returns a stream of each iteration.
     *
     * <p>Iterations happen in strict character encounter order, from the beginning of the input
     * string to the end, with no overlapping. When a match is found, the next iteration is
     * guaranteed to be in the substring after the current match. For example, {@code
     * between(first('/'), first('/')).repeatedly().match("/foo/bar/baz/")} will return {@code
     * ["foo", "bar", "baz"]}. On the other hand, {@code
     * after(last('/')).repeatedly().match("/foo/bar")} will only return "bar".
     *
     * <p>Pattern matching is lazy and doesn't start until the returned stream is consumed.
     *
     * <p>An empty stream is returned if this pattern has no matches in the {@code input} string.
     */
    public abstract Stream<Match> match(String input);

    /**
     * Applies this pattern against {@code string} and returns a stream of each iteration.
     *
     * <p>Iterations happen in strict character encounter order, from the beginning of the input
     * string to the end, with no overlapping. When a match is found, the next iteration is
     * guaranteed to be in the substring after the current match. For example, {@code
     * between(first('/'), first('/')).repeatedly().from("/foo/bar/baz/")} will return {@code
     * ["foo", "bar", "baz"]}. On the other hand, {@code
     * after(last('/')).repeatedly().from("/foo/bar")} will only return "bar".
     *
     * <p>Pattern matching is lazy and doesn't start until the returned stream is consumed.
     *
     * <p>An empty stream is returned if this pattern has no matches in the {@code input} string.
     */
    public Stream<String> from(CharSequence input) {
      return match(input.toString()).map(Match::toString);
    }

    /**
     * Returns a new string with all {@link #match matches} of this pattern removed. Returns {@code
     * string} as is if no match is found.
     */
    public String removeAllFrom(String string) {
      return replaceAllFrom(string, m -> "");
    }

    /**
     * Returns a new string with all {@link #match matches} of this pattern replaced by applying
     * {@code replacementFunction} for each match.
     *
     * <p>{@code replacementFunction} must not return null. Returns {@code string} as-is if no match
     * is found.
     */
    public String replaceAllFrom(
        String string, Function<? super Match, ? extends CharSequence> replacementFunction) {
      requireNonNull(replacementFunction);
      Iterator<Match> matches = match(string).iterator();
      if (!matches.hasNext()) {
        return string;
      }
      // Add the chars between the previous and current match.
      StringBuilder builder = new StringBuilder(string.length());
      int index = 0;
      do {
        Match match = matches.next();
        CharSequence replacement = replacementFunction.apply(match);
        if (replacement == null) {
          throw new NullPointerException("No replacement is returned for " + match);
        }
        builder
            .append(string, index, match.startIndex)
            .append(replacement);
        index = match.endIndex;
      } while (matches.hasNext());

      // Add the remaining chars
      return builder.append(string, index, string.length()).toString();
    }

    /**
     * Returns a stream of {@code Match} objects delimited by every match of this pattern. If this
     * pattern isn't found in {@code string}, the full string is matched.
     *
     * <p>The returned {@code Match} objects are cheap "views" of the matched substring sequences.
     * Because {@code Match} implements {@code CharSequence}, the returned {@code Match} objects can
     * be directly passed to {@code CharSequence}-accepting APIs such as {@code
     * com.google.common.base.CharMatcher.trimFrom()} and {@link Pattern#splitThenTrim} etc.
     */
    public Stream<Match> split(String string) {
      return MoreStreams.whileNotNull(
          new Supplier<Match>() {
            int next = 0;
            Iterator<Match> it = match(string).iterator();

            @Override
            public Match get() {
              if (it.hasNext()) {
                Match delim = it.next();
                Match result = new Match(string, next, delim.index() - next);
                next = delim.endIndex;
                return result;
              }
              if (next >= 0) {
                Match result = new Match(string, next, string.length() - next);
                next = -1;
                return result;
              } else {
                return null;
              }
            }
          });
    }

    /**
     * Returns a stream of {@code Match} objects delimited by every match of this pattern. with
     * whitespaces trimmed.
     *
     * <p>The returned {@code Match} objects are cheap "views" of the matched substring sequences.
     * Because {@code Match} implements {@code CharSequence}, the returned {@code Match} objects can
     * be directly passed to {@code CharSequence}-accepting APIs such as {@code
     * com.google.common.base.CharMatcher.trimFrom()} and {@link Pattern#split} etc.
     */
    public Stream<Match> splitThenTrim(String string) {
      return split(string).map(Match::trim);
    }

    /**
     * Returns a {@link BiStream} of key value pairs from {@code input}.
     *
     * <p>The key-value pairs are delimited by this repeating pattern.
     * with the key and value separated by {@code keyValueSeparator}.
     *
     * <p>Empty parts (including leading and trailing separator) are ignored.
     * Although whitespaces are not trimmed. For example:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .splitKeyValuesAround(first('='), "k1=v1,,k2=v2,")
     * }</pre>
     * will result in a {@code BiStream} equivalent to {@code [(k1, v1), (k2, v2)]},
     * but {@code "k1=v1, ,k2=v2"} will fail to be split due to the whitespace after the first
     * {@code ','}.
     *
     * <p>Non-empty parts where {@code keyValueSeparator} is absent will result in
     * {@link IllegalArgumentException}.
     *
     * <p>For alternative splitting strategies, like, if you want to reject instead of ignoring
     * empty parts. consider to use {@link #split} and {@link Pattern#split} directly,
     * such as:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .split("k1=v1,,k2=v2,")  // the redundant ',' will throw IAE
     *     .collect(
     *         GuavaCollectors.toImmutableMap(
     *             m -> first('=').split(m).orElseThrow(...)));
     * }</pre>
     *
     * Or, if you want to ignore unparsable parts:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .split("k1=v1,k2>v2")  // Ignore the unknown "k2>v2"
     *     .map(first('=')::split)
     *     .collect(
     *         MoreCollectors.flatMapping(
     *             BiOptional::stream,
     *             toImmutableMap()));
     * }</pre>
     *
     * @since 5.9
     */
    public final BiStream<String, String> splitKeyValuesAround(
        Pattern keyValueSeparator, String input) {
      requireNonNull(keyValueSeparator);
      return BiStream.from(
          split(input)
              .filter(m -> m.length() > 0)
              .map(m -> keyValueSeparator
                  .split(m)
                  .orElseThrow(
                      () -> new IllegalArgumentException("Cannot split key values from '" + m + "'"))));
    }

    /**
     * Returns a {@link BiStream} of key value pairs from {@code input}.
     *
     * <p>The key-value pairs are delimited by this repeating pattern.
     * with the key and value separated by {@code keyValueSeparator}.
     *
     * <p>All keys and values are trimmed, with empty parts (including leading and trailing
     * separator) ignored. For example:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .splitThenTrimKeyValuesAround(first('='), "k1 = v1, , k2=v2,")
     * }</pre>
     * will result in a {@code BiStream} equivalent to {@code [(k1, v1), (k2, v2)]}.
     *
     * <p>Non-empty parts where {@code keyValueSeparator} is absent will result in
     * {@link IllegalArgumentException}.
     *
     * <p>For alternative splitting strategies, like, if you want to reject instead of ignoring
     * empty parts. consider to use {@link #split} and {@link Pattern#splitThenTrim} directly,
     * such as:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .split("k1 = v1, , k2=v2,")  // the redundant ',' will throw IAE
     *     .collect(
     *         GuavaCollectors.toImmutableMap(
     *             m -> first('=').splitThenTrim(m).orElseThrow(...)));
     * }</pre>
     *
     * Or, if you want to ignore unparsable parts:
     *
     * <pre>{@code
     * first(',')
     *     .repeatedly()
     *     .split("k1 = v1, k2 > v2")  // Ignore the unknown "k2 > v2"
     *     .map(first('=')::splitThenTrim)
     *     .collect(
     *         MoreCollectors.flatMapping(
     *             BiOptional::stream,
     *             toImmutableMap()));
     * }</pre>
     *
     * @since 5.9
     */
    public final BiStream<String, String> splitThenTrimKeyValuesAround(
        Pattern keyValueSeparator, String input) {
      requireNonNull(keyValueSeparator);
      return BiStream.from(
          splitThenTrim(input)
              .filter(m -> m.length() > 0)
              .map(m -> keyValueSeparator
                  .splitThenTrim(m)
                  .orElseThrow(
                      () -> new IllegalArgumentException("Cannot split key values from '" + m + "'"))));
    }

    RepeatingPattern() {}
  }

  /**
   * An immutable string prefix {@code Pattern} with extra utilities such as {@link
   * #addToIfAbsent(String)}, {@link #removeFrom(StringBuilder)}, {@link #isIn(CharSequence)} etc.
   *
   * <p>Can usually be declared as a constant to save allocation cost. Because {@code Prefix}
   * implements {@link CharSequence}, it can be used almost interchangeably as a string. You can:
   *
   * <ul>
   *   <li>directly prepend a prefix as in {@code HOME_PREFIX + path};
   *   <li>or prepend it into a {@code StringBuilder}: {@code builder.insert(0, HOME_PREFIX)};
   *   <li>pass it to any CharSequence-accepting APIs such as {@code
   *       CharMatcher.anyOf(...).matchesAnyOf(MY_PREFIX)}, {@code
   *       Substring.first(':').splitThenTrim(MY_PREFIX)} etc.
   * </ul>
   *
   * @since 4.6
   */
  public static final class Prefix extends Pattern implements CharSequence {
    private final String prefix;

    Prefix(String prefix) {
      this.prefix = prefix;
    }

    /**
     * Returns true if {@code source} starts with this prefix.
     *
     * @since 5.7
     */
    public boolean isIn(CharSequence source) {
      if (source instanceof String) {
        return ((String) source).startsWith(prefix);
      }
      int prefixChars = prefix.length();
      int existingChars = source.length();
      if (existingChars < prefixChars) {
        return false;
      }
      for (int i = 0; i < prefixChars; i++) {
        if (prefix.charAt(i) != source.charAt(i)) {
          return false;
        }
      }
      return true;
    }

    /**
     * If {@code string} has this prefix, return it as-is; otherwise, return it with this prefix
     * prepended.
     *
     * @since 4.8
     */
    public String addToIfAbsent(String string) {
      return string.startsWith(prefix) ? string : prefix + string;
    }

    /**
     * If {@code builder} does not already have this prefix, prepend this prefix to it.
     *
     * @return true if this prefix is prepended
     * @since 5.7
     */
    public boolean addToIfAbsent(StringBuilder builder) {
      boolean shouldAdd = !isIn(builder);
      if (shouldAdd) {
        builder.insert(0, prefix);
      }
      return shouldAdd;
    }

    /**
     * Removes this prefix from {@code builder} if it starts with the prefix.
     *
     * @return true if this prefix is removed
     * @since 5.7
     */
    public boolean removeFrom(StringBuilder builder) {
      boolean present = isIn(builder);
      if (present) {
        builder.delete(0, length());
      }
      return present;
    }

    /**
     * Replaces this prefix from {@code builder} with {@code replacement} if it starts with the
     * prefix.
     *
     * @return true if this prefix is replaced
     * @since 5.7
     */
    public boolean replaceFrom(StringBuilder builder, CharSequence replacement) {
      requireNonNull(replacement);
      boolean present = isIn(builder);
      if (present) {
        builder.replace(0, length(), replacement.toString());
      }
      return present;
    }

    /** @since 5.7 */
    @Override public char charAt(int index) {
      return prefix.charAt(index);
    }

    /** @since 5.7 */
    @Override public String subSequence(int start, int end) {
      return prefix.substring(start, end);
    }

    /**
     * Returns the length of this prefix.
     *
     * @since 5.7
     */
    @Override public int length() {
      return prefix.length();
    }

    @Override public int hashCode() {
      return prefix.hashCode();
    }

    @Override public boolean equals(Object obj) {
      return obj instanceof Prefix && prefix.equals(((Prefix) obj).prefix);
    }

    /** Returns this prefix string. */
    @Override public String toString() {
      return prefix;
    }

    @Override Match match(String input, int fromIndex) {
      return input.startsWith(prefix, fromIndex)
          ? new Match(input, fromIndex, prefix.length())
          : null;
    }
  }

  /**
   * An immutable string suffix {@code Pattern} with extra utilities such as {@link
   * #addToIfAbsent(String)}, {@link #removeFrom(StringBuilder)}, {@link #isIn(CharSequence)} etc.
   *
   * <p>Can usually be declared as a constant to save allocation cost. Because {@code Suffix}
   * implements {@link CharSequence}, it can be used almost interchangeably as a string. You can:
   *
   * <ul>
   *   <li>directly append a suffix as in {@code path + SHARD_SUFFIX};
   *   <li>or append the suffix into a {@code StringBuilder}: {@code builder.append(SHARD_SUFFIX)}};
   *   <li>pass it to any CharSequence-accepting APIs such as {@code
   *       CharMatcher.anyOf(...).matchesAnyOf(MY_PREFIX)}, {@code
   *       Substring.first(':').splitThenTrim(MY_PREFIX)} etc.
   * </ul>
   *
   * @since 4.6
   */
  public static final class Suffix extends Pattern implements CharSequence {
    private final String suffix;

    Suffix(String suffix) {
      this.suffix = suffix;
    }

    /**
     * Returns true if {@code source} ends with this suffix.
     *
     * @since 5.7
     */
    public boolean isIn(CharSequence source) {
      if (source instanceof String) {
        return ((String) source).endsWith(suffix);
      }
      int suffixChars = suffix.length();
      int existingChars = source.length();
      if (existingChars < suffixChars) {
        return false;
      }
      for (int i = 1; i <= suffixChars; i++) {
        if (suffix.charAt(suffixChars - i) != source.charAt(existingChars - i)) {
          return false;
        }
      }
      return true;
    }

    /**
     * If {@code string} has this suffix, return it as-is; otherwise, return it with this suffix
     * appended.
     *
     * @since 4.8
     */
    public String addToIfAbsent(String string) {
      return string.endsWith(suffix) ? string : string + suffix;
    }

    /**
     * If {@code builder} does not already have this suffix, append this suffix to it.
     *
     * @return true if this suffix is appended
     * @since 5.7
     */
    public boolean addToIfAbsent(StringBuilder builder) {
      boolean shouldAdd = !isIn(builder);
      if (shouldAdd) {
        builder.append(suffix);
      }
      return shouldAdd;
    }

    /**
     * Removes this suffix from {@code builder} if it ends with the suffix.
     *
     * @return true if this suffix is removed
     * @since 5.7
     */
    public boolean removeFrom(StringBuilder builder) {
      boolean present = isIn(builder);
      if (present) {
        builder.delete(builder.length() - length(), builder.length());
      }
      return present;
    }

    /**
     * Replaces this suffix from {@code builder} with {@code replacement} if it ends with the
     * suffix.
     *
     * @return true if this suffix is replaced
     * @since 5.7
     */
    public boolean replaceFrom(StringBuilder builder, CharSequence replacement) {
      requireNonNull(replacement);
      boolean present = isIn(builder);
      if (present) {
        builder.replace(builder.length() - length(), builder.length(), replacement.toString());
      }
      return present;
    }

    /** @since 5.7 */
    @Override public char charAt(int index) {
      return suffix.charAt(index);
    }

    /** @since 5.7 */
    @Override public String subSequence(int start, int end) {
      return suffix.substring(start, end);
    }

    /**
     * Returns the length of this suffix.
     *
     * @since 5.7
     */
    @Override public int length() {
      return suffix.length();
    }

    /** @since 5.7 */
    @Override public int hashCode() {
      return suffix.hashCode();
    }

    /** @since 5.7 */
    @Override public boolean equals(Object obj) {
      return obj instanceof Suffix && suffix.equals(((Suffix) obj).suffix);
    }

    /** Returns this suffix string. */
    @Override public String toString() {
      return suffix;
    }

    @Override Match match(String input, int fromIndex) {
      int index = input.length() - suffix.length();
      return index >= fromIndex && input.endsWith(suffix)
          ? new Match(input, index, suffix.length())
          : null;
    }
  }

  /**
   * The result of successfully matching a {@link Pattern} against a string, providing access to the
   * {@link #toString matched substring}, to the parts of the string {@link #before before} and
   * {@link #after after} it, and to copies with the matched substring {@link #remove removed} or
   * {@link #replaceWith replaced}.
   *
   * <p><em>Note:</em> a {@link Match} is a view of the original string and holds a strong reference
   * to the original string. It's advisable to construct and use a {@code Match} object within the
   * scope of a method; holding onto a {@code Match} object has the same risk of leaking memory as
   * holding onto the string it was produced from.
   */
  public static final class Match implements CharSequence {
    private final String context;
    private final int startIndex;
    private final int endIndex;

    /**
     * While {@code endIndex} demarcates the matched substring, {@code repetitionStartIndex} points to
     * the starting point to scan for the succeeding {@link Pattern#iterateIn iteration} of the same
     * pattern. It's by default equal to {@code endIndex}, but for {@link Substring#before} and
     * {@link Substring#upToIncluding}, {@code repetitionStartIndex} starts after the delimiters.
     */
    private final int repetitionStartIndex;

    private Match(String context, int startIndex, int length) {
      this(context, startIndex, length, startIndex + length);
    }

    private Match(String context, int startIndex, int length, int repetitionStartIndex) {
      this.context = context;
      this.startIndex = startIndex;
      this.endIndex = startIndex + length;
      this.repetitionStartIndex = repetitionStartIndex;
    }

    /**
     * Returns the part of the original string before the matched substring.
     *
     * <p>{@link #before} and {@link #after} are almost always used together to split a string into
     * two parts. If you just need the substring before the match, you might want to use {@code
     * Substring.before(pattern)} instead, because the pattern logic is encoded entirely in the
     * {@link Pattern} object. For example:
     *
     * <pre>
     *   private static final Substring.Pattern DIRECTORY = Substring.before(last("/"));
     * </pre>
     */
    public String before() {
      return context.substring(0, startIndex);
    }

    /**
     * Returns the part of the original string before the matched substring.
     *
     * <p>{@link #before} and {@link #after} are almost always used together to split a string into
     * two parts. If you just need the substring after the match, you might want to use {@code
     * Substring.after(pattern)} instead, because the pattern logic is encoded entirely in the
     * {@link Pattern} object. For example:
     *
     * <pre>
     *   private static final Substring.Pattern LINE_COMMENT = Substring.after(first("//"));
     * </pre>
     */
    public String after() {
      return context.substring(endIndex);
    }

    /** Return the full string being matched against. */
    public String fullString() {
      return context;
    }

    /**
     * Returns a copy of the original string with the matched substring removed.
     *
     * <p>This is equivalent to {@code match.before() + match.after()}.
     */
    public String remove() {
      // Minimize string concatenation.
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
     *
     * <p>This is equivalent to {@code match.before() + replacement + match.after()}.
     */
    public String replaceWith(CharSequence replacement) {
      requireNonNull(replacement);
      return before() + replacement + after();
    }

    /** Return 0-based index of this match in {@link #fullString}. */
    public int index() {
      return startIndex;
    }

    /** Returns the length of the matched substring. */
    @Override public int length() {
      return endIndex - startIndex;
    }

    /**
     * {@inheritDoc}
     * @since 4.6
     */
    @Override public char charAt(int i) {
      if (i < 0) {
        throw new IndexOutOfBoundsException("Invalid index (" + i + ") < 0");
      }
      if (i >= length()) {
        throw new IndexOutOfBoundsException(
            "Invalid index (" + i + ") >= length (" + length() + ")");
      }
      return context.charAt(startIndex + i);
    }

    /**
     * {@inheritDoc}
     * @since 4.6
     */
    @Override public CharSequence subSequence(int begin, int end) {
      if (begin < 0) {
        throw new IndexOutOfBoundsException("Invalid index: begin (" + begin + ") < 0");
      }
      if (end > length()) {
        throw new IndexOutOfBoundsException(
            "Invalid index: end (" + end + ") > length (" + length() + ")");
      }
      if (begin > end) {
        throw new IndexOutOfBoundsException(
            "Invalid index: begin (" + begin + ") > end (" + end + ")");
      }
      return new Match(context, startIndex + begin, end - begin);
    }

    /** Returns the matched substring. */
    @Override public String toString() {
      return context.substring(startIndex, endIndex);
    }

    Match preceding() {
      return new Match(context, 0, startIndex);
    }

    Match following() {
      return new Match(context, endIndex, context.length() - endIndex);
    }

    Match trim() {
      int left = startIndex;
      int right = endIndex - 1;
      while (left <= right) {
        if (Character.isWhitespace(context.charAt(left))) {
          left++;
          continue;
        }
        if (Character.isWhitespace(context.charAt(right))) {
          right--;
          continue;
        }
        break;
      }
      int trimmedLength = right - left + 1;
      return trimmedLength == length()
          ? this
          : new Match(context, left, trimmedLength, repetitionStartIndex);
    }

    private Match toEnd() {
      return new Match(context, startIndex, context.length() - startIndex);
    }
  }

  private Substring() {}
}

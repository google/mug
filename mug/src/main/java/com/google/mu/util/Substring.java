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
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Stream;

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
 * import static com.google.common.labs.collect.MoreCollectors.toImmutableListMultimap;
 *
 * ImmutableListMultimap<String, String> tags =
 *     first(',')
 *         .repeatedly()
 *         .split("k1=v1,k2=v2")  // Split into ["k1=v1", "k2=v2"]
 *         .collect(
 *             toImmutableListMultimap(
 *                 // Split "k1=v1" into (k1, v1) and "k2=v2" into (k2, v2)
 *                 s -> first('=').splitThenTrim(s).orElseThrow(...)));
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

  /** {@code Pattern} that matches the entire string. */
  private static final Pattern FULL_STRING =
      new Pattern() {
        @Override public Match match(String s, int fromIndex) {
          return new Match(s, fromIndex, s.length() - fromIndex);
        }

        @Override public String toString() {
          return "FULL_STRING";
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

  /** @deprecated Use {@code first(str)} instead. */
  @Deprecated
  public static Pattern substring(String str) {
    return first(str);
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
   *
   * <p>For example, {@code topLevelGroups(compile("(g+)(o+)")).from("ggooo")} will return
   * {@code ["gg", "ooo"]}.
   *
   * <p>Nested capture groups are not taken into consideration. For example: {@code
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
    List<String> stops =
        Stream.concat(Stream.of(stop1, stop2), Arrays.stream(moreStops))
            .peek(Objects::requireNonNull)
            .collect(toList());
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int begin = -1;
        for (String stop : stops) {
          int index = input.indexOf(stop, fromIndex);
          if (index < 0) {
            return null;
          }
          if (begin == -1) {
            begin = index;
          }
          fromIndex = index + stop.length();
        }
        return new Match(input, begin, fromIndex - begin);
      }

      @Override public String toString() {
        return "spanningInOrder("
            + stops.stream().map(s -> "'" + s + "'").collect(joining(", "))
            + ")";
      }
    };
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
            : new Match(input, fromIndex, match.startIndex - fromIndex, match.succeedingIndex);
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
            : new Match(input, fromIndex, match.endIndex - fromIndex, match.succeedingIndex);
      }

      @Override public String toString() {
        return "upToIncluding(" + pattern + ")";
      }
    };
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

    /** @deprecated Use {@code repeatedly().match(input)} instead. */
    @Deprecated
    public final Stream<Match> iterateIn(String input) {
      return repeatedly().match(input);
    }

    /** @deprecated Use {@code repeatedly().split(string)} instead. */
    @Deprecated
    public Stream<Match> delimit(String string) {
      return repeatedly().split(string);
    }

    /**
     * Matches this pattern against {@code string}, and returns a copy with the matched substring
     * removed if successful. Otherwise, returns {@code string} unchanged.
     */
    public final String removeFrom(String string) {
      Match match = match(string);
      return match == null ? string : match.remove();
    }

    /** @deprecated Use {@code repeatedly().removeAllFrom(string)} instead. */
    @Deprecated
    public final String removeAllFrom(String string) {
      return repeatedly().removeAllFrom(string);
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

    /** @deprecated Use {@code repeatedly().replaceAllFrom(string, replacementFunction)} instead. */
    @Deprecated
    public final String replaceAllFrom(
        String string, Function<? super Match, ? extends CharSequence> replacementFunction) {
      return repeatedly().replaceAllFrom(string, replacementFunction);
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
     * Splits {@code string} into two parts that are separated by this separator pattern. For
     * example:
     *
     * <pre>{@code
     * Optional<KeyValue> keyValue = first('=').split("name=joe").join(KeyValue::new);
     * }</pre>
     *
     * <p>If you need to trim the key-value pairs, use {@link #splitThenTrim}.
     *
     * <p>To split a string into multiple substrings delimited by a delimiter, use {@link #delimit}.
     *
     * @throws IllegalArgumentException if this separator pattern isn't found in {@code string}.
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
     * Multimap} etc.), you can use {@link com.google.common.base.Splitter.MapSplitter} though it's
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
     * String toSplit = " x -> y, z-> a, x -> t ";
     * ImmutableListMultimap<String, String> result = first(',')
     *     .delimit(toSplit)
     *     .map(Match::toString)
     *     .map(first("->")::splitThenTrim)
     *     .collect(concatenating(BiOptional::stream))  // Or use BiStream.concat()
     *     .collect(ImmutableListMultimap::toImmutableListMultimap);
     * }</pre>
     *
     * <p>To split a string into multiple substrings delimited by a delimiter, use {@link #delimit}.
     *
     * @throws IllegalArgumentException if this separator pattern isn't found in {@code string}.
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
                  } else if (match.succeedingIndex > nextIndex) {
                    nextIndex = match.succeedingIndex;
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
     * be directly passed to {@code CharSequence}-accepting APIs such as {@link
     * CharMatcher#trimFrom} and {@link Pattern#splitThenTrim} etc.
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
     * be directly passed to {@code CharSequence}-accepting APIs such as {@link
     * CharMatcher#trimFrom} and {@link Pattern#split} etc.
     */
    public Stream<Match> splitThenTrim(String string) {
      return split(string).map(Match::trim);
    }

    RepeatingPattern() {}
  }

  /**
   * A string prefix pattern.
   *
   * @since 4.6
   */
  public static final class Prefix extends Pattern {
    private final String prefix;

    Prefix(String prefix) {
      this.prefix = prefix;
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

    @Override Match match(String input, int fromIndex) {
      return input.startsWith(prefix, fromIndex)
          ? new Match(input, fromIndex, prefix.length())
          : null;
    }

    /** Returns this prefix string. */
    @Override public String toString() {
      return prefix;
    }
  }

  /**
   * A string suffix pattern.
   *
   * @since 4.6
   */
  public static final class Suffix extends Pattern {
    private final String suffix;

    Suffix(String suffix) {
      this.suffix = suffix;
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

    @Override Match match(String input, int fromIndex) {
      int index = input.length() - suffix.length();
      return index >= fromIndex && input.endsWith(suffix)
          ? new Match(input, index, suffix.length())
          : null;
    }

    /** Returns this suffix string. */
    @Override public String toString() {
      return suffix;
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
     * While {@code endIndex} demarcates the matched substring, {@code succeedingIndex} points to
     * the starting point to scan for the succeeding {@link Pattern#iterateIn iteration} of the same
     * pattern. It's by default equal to {@code endIndex}, but for {@link Substring#before} and
     * {@link Substring#upToIncluding}, {@code succeedingIndex} starts after the delimiters.
     */
    private final int succeedingIndex;

    private Match(String context, int startIndex, int length) {
      this(context, startIndex, length, startIndex + length);
    }

    private Match(String context, int startIndex, int length, int succeedingIndex) {
      this.context = context;
      this.startIndex = startIndex;
      this.endIndex = startIndex + length;
      this.succeedingIndex = succeedingIndex;
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
          : new Match(context, left, trimmedLength, succeedingIndex);
    }

    private Match toEnd() {
      return new Match(context, startIndex, context.length() - startIndex);
    }
  }

  private Substring() {}
}

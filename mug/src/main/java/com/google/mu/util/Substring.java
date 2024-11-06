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

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.regex.Pattern.compile;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collector;
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
 * To replace the placeholders in a text with values (although do consider using a proper templating
 * framework because it's a security vulnerability if your values come from untrusted sources like
 * the user inputs):
 *
 * <pre>{@code
 * ImmutableMap<String, String> variables =
 *     ImmutableMap.of("who", "Arya Stark", "where", "Braavos");
 * String rendered =
 *     spanningInOrder("{", "}")
 *         .repeatedly()
 *         .replaceAllFrom(
 *             "{who} went to {where}.",
 *             placeholder -> variables.get(placeholder.skip(1, 1).toString()));
 * assertThat(rendered).isEqualTo("Arya Stark went to Braavos.");
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
          return Match.nonBacktrackable(str, fromIndex, 0);
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
          return Match.suffix(str, 0);
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
        return index >= fromIndex ? Match.backtrackable(1, input, index, str.length()) : null;
      }

      @Override Pattern lookaround(String lookbehind, String lookahead) {
        // first(lookbehind + str).skip(lookbehind) is more efficient with native String#indexOf().
        //
        // Can't use first(lookbehind + str + lookbehind).skip(lookbehind, lookahead)
        // because skip().repeatedly() will repeat after `lookahead`.
        // between(lookbehind, lookahead).repeatedly() should repeat _at_ `lookahead`.
        return lookbehind.isEmpty()
            ? super.lookaround(lookbehind, lookahead)
            : first(lookbehind + str).skip(lookbehind.length(), 0).followedBy(lookahead);
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
        return index >= 0 ? Match.backtrackable(1, input, index, 1) : null;
      }

      @Override Pattern lookaround(String lookbehind, String lookahead) {
        // first(lookbehind + char).skip(lookbehind) is more efficient with native String#indexOf().
        return lookbehind.isEmpty()
            ? super.lookaround(lookbehind, lookahead)
            : first(lookbehind + character).skip(lookbehind.length(), 0).followedBy(lookahead);
      }

      @Override public String toString() {
        return "first(\'" + character + "\')";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first character found by {@code charMatcher}.
   *
   * @since 6.0
   */
  public static Pattern first(CharPredicate charMatcher) {
    requireNonNull(charMatcher);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        for (int i = fromIndex; i < input.length(); i++) {
          if (charMatcher.test(input.charAt(i))) {
            return Match.backtrackable(1, input, i, 1);
          }
        }
        return null;
      }

      @Override public String toString() {
        return "first(" + charMatcher + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the last character found by {@code charMatcher}.
   *
   * @since 6.0
   */
  public static Pattern last(CharPredicate charMatcher) {
    requireNonNull(charMatcher);
    return new Last() {
      @Override Match match(String input, int fromIndex, int endIndex) {
        for (int i = endIndex - 1; i >= fromIndex; i--) {
          if (charMatcher.test(input.charAt(i))) {
            return Match.nonBacktrackable(input, i, 1);
          }
        }
        return null;
      }

      @Override public String toString() {
        return "last(" + charMatcher + ")";
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
   * Returns a {@code Pattern} that matches the first occurrence of a word composed of {@code
   * [a-zA-Z0-9_]} characters.
   *
   * @since 6.0
   */
  public static Pattern word() {
    return consecutive(CharPredicate.WORD);
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code word} that isn't
   * immediately preceded or followed by another "word" ({@code [a-zA-Z0-9_]}) character.
   *
   * <p>For example, if you are looking for an English word "cat" in the string "catchie has a cat",
   * {@code first("cat")} won't work because it'll match the first three letters of "cathie".
   * Instead, you should use {@code word("cat")} to skip over "cathie".
   *
   * <p>If your word boundary isn't equivalent to the regex {@code \W} character class, you can
   * define your own word boundary {@code CharMatcher} and then use {@link Pattern#separatedBy}
   * instead. Say, if your word is lower-case alpha with dash ('-'), then:
   *
   * <pre>{@code
   * CharMatcher boundary = CharMatcher.inRange('a', 'z').or(CharMatcher.is('-')).negate();
   * Substring.Pattern petFriendly = first("pet-friendly").separatedBy(boundary);
   * }</pre>
   *
   * @since 6.0
   */
  public static Pattern word(String word) {
    return first(word).separatedBy(CharPredicate.WORD.not());
  }

  /**
   * Returns a {@code Pattern} that matches from the beginning of the input string, a non-empty
   * sequence of leading characters identified by {@code matcher}.
   *
   * <p>For example: {@code leading(javaLetter()).from("System.err")} will result in {@code
   * "System"}.
   *
   * @since 6.0
   */
  public static Pattern leading(CharPredicate matcher) {
    requireNonNull(matcher);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int len = 0;
        for (int i = fromIndex; i < input.length(); i++, len++) {
          if (!matcher.test(input.charAt(i))) {
            break;
          }
        }
        return len == 0 ? null : Match.nonBacktrackable(input, fromIndex, len);
      }

      @Override public String toString() {
        return "leading(" + matcher + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches from the end of the input string, a non-empty sequence
   * of trailing characters identified by {@code matcher}.
   *
   * <p>For example: {@code trailing(digit()).from("60612-3588")} will result in {@code "3588"}.
   *
   * @since 6.0
   */
  public static Pattern trailing(CharPredicate matcher) {
    requireNonNull(matcher);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int len = 0;
        for (int i = input.length() - 1; i >= fromIndex; i--, len++) {
          if (!matcher.test(input.charAt(i))) {
            break;
          }
        }
        return len == 0 ? null : Match.suffix(input, len);
      }

      @Override public String toString() {
        return "trailing(" + matcher + ")";
      }
    };
  }

  /**
   * Returns a {@code Pattern} that matches the first non-empty sequence of consecutive characters
   * identified by {@code matcher}.
   *
   * <p>For example: {@code consecutive(javaLetter()).from("(System.out)")} will find {@code
   * "System"}, and {@code consecutive(javaLetter()).repeatedly().from("(System.out)")} will produce
   * {@code ["System", "out"]}.
   *
   * <p>Equivalent to {@code matcher.collapseFrom(string, replacement)}, you can do {@code
   * consecutive(matcher).repeatedly().replaceAllFrom(string, replacement)}. But you can also do
   * things other than collapsing these consecutive groups, for example to inspect their values and
   * replace conditionally: {@code consecutive(matcher).repeatedly().replaceAllFrom(string, group ->
   * ...)}, or other more sophisticated use cases like building index maps of these sub sequences.
   *
   * @since 6.0
   */
  public static Pattern consecutive(CharPredicate matcher) {
    requireNonNull(matcher);
    return new Pattern() {
      @Override Match match(String input, int fromIndex) {
        int end = input.length();
        for (int i = fromIndex; i < end; i++) {
          if (matcher.test(input.charAt(i))) {
            int len = 1;
            for (int j = i + 1; j < end; j++, len++) {
              if (!matcher.test(input.charAt(j))) {
                break;
              }
            }
            return Match.backtrackable(len, input, i, len);
          }
        }
        return null;
      }

      @Override public String toString() {
        return "consecutive(" + matcher + ")";
      }
    };
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
      @Override public Stream<Match> match(String input, int fromIndex) {
        String string = input.substring(fromIndex);
        Matcher matcher = regexPattern.matcher(string);
        if (!matcher.find()) return Stream.empty();
        int groups = matcher.groupCount();
        if (groups == 0) {
          return Stream.of(
              Match.backtrackable(1, string, matcher.start(), matcher.end() - matcher.start()));
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
                  return Match.backtrackable(1, string, start, end - start);
                }
              }
              return null;
            }
          });
        }
      }

      @Override public String toString() {
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
        Matcher matcher = regexPattern.matcher(input);
        if (fromIndex <= input.length() && matcher.find(fromIndex)) {
          int start = matcher.start(group);
          return Match.backtrackable(1, input, start, matcher.end(group) - start);
        }
        return null;
      }

      /** Delegate to native regex backtracking, which can be more efficient for regex patterns. */
      @Override Pattern lookaround(String lookbehind, String lookahead) {
        StringBuilder builder = new StringBuilder();
        if (!lookbehind.isEmpty()) {
          builder.append("(?<=").append(quote(lookbehind)).append(")");
        }
        builder.append("(").append(regexPattern).append(")");
        if (!lookahead.isEmpty()) {
          builder.append("(?=").append(quote(lookahead)).append(")");
        }
        return first(compile(builder.toString()), group);
      }

      /** Delegate to native regex backtracking, which can be more efficient for regex patterns. */
      @Override Pattern negativeLookaround(String lookbehind, String lookahead) {
        if (lookahead.isEmpty()) { // negative lookbehind
          return first(compile("(?<!" + quote(lookbehind) + ")" + regexPattern));
        }
        if (lookbehind.isEmpty()) { // negative lookahead
          return first(compile(regexPattern + "(?!" + quote(lookahead) + ")"));
        }
        // Regex has no negative front-and-back lookaround
        return super.negativeLookaround(lookbehind, lookahead);
      }

      @Override public String toString() {
        return "first(\"" + regexPattern + "\", " + group + ")";
      }
    };
  }

  /**
   * Returns a {@link Collector} that collects the input candidate {@link Pattern} and reults in a
   * pattern that matches whichever that occurs first in the input string. For example you can use
   * it to find the first occurrence of any reserved word in a set:
   *
   * <pre>{@code
   * Substring.Pattern reserved =
   *     Stream.of("if", "else", "for", "public")
   *         .map(Substring::word)
   *         .collect(firstOccurrence());
   * }</pre>
   *
   * @since 6.1
   */
  public static Collector<Pattern, ?, Pattern> firstOccurrence() {
    final class Occurrence {
      private final Pattern pattern;
      private final int stableOrder;
      final Match match;

      @Override public String toString() {
        return pattern + ": " + match.toString();
      }

      Occurrence(Pattern pattern, Match match, int stableOrder) {
        this.pattern = pattern;
        this.match = match;
        this.stableOrder = stableOrder;
      }

      void enqueueNextOccurrence(String input, int fromIndex, Queue<Occurrence> queue) {
        Match nextMatch = pattern.match(input, fromIndex);
        if (nextMatch != null) {
          queue.add(new Occurrence(pattern, nextMatch, stableOrder));
        }
      }
    }
    Comparator<Occurrence> byIndex =
        comparingInt((Occurrence occurrence) -> occurrence.match.index())
            .thenComparingInt(occurrence -> occurrence.stableOrder);
    return collectingAndThen(
        toImmutableList(),
        candidates -> {
          return new Pattern() {
            @Override
            Match match(String input, int fromIndex) {
              requireNonNull(input);
              Match best = null;
              for (Pattern candidate : candidates) {
                Match match = candidate.match(input, fromIndex);
                if (match == null) {
                  continue;
                }
                if (match.index() == fromIndex) { // First occurrence for sure.
                  return match;
                }
                if (best == null || match.index() < best.index()) {
                  best = match;
                }
              }
              return best;
            }

            @Override Stream<Match> iterate(String input, int fromIndex) {
              PriorityQueue<Occurrence> occurrences =
                  new PriorityQueue<>(max(1, candidates.size()), byIndex);
              for (int i = 0; i < candidates.size(); i++) {
                Pattern candidate = candidates.get(i);
                Match match = candidate.match(input, fromIndex);
                if (match != null) {
                  occurrences.add(new Occurrence(candidate, match, i));
                }
              }
              return MoreStreams.whileNotNull(
                  () -> {
                    final Occurrence occurrence = occurrences.poll();
                    if (occurrence == null) {
                      return null;
                    }
                    final Match match = occurrence.match;

                    // For allOccurrencesOf([before(first('/')), first('/')]) against input = "foo/bar",
                    // before(first('/')) will match the first occurrence of "foo".
                    // In the next iteration, we want to start *after* the '/' for the repetition
                    // of before(first('/')), yet start from the '/' for the other unmatched first('/').
                    // The expected result is [foo, /].
                    if (match.repetitionStartIndex <= input.length()) {
                      occurrence.enqueueNextOccurrence(input, match.repetitionStartIndex, occurrences);
                    }
                    for (final int waterMark = match.endIndex; ;) {
                      Occurrence nextInLine = occurrences.peek();
                      if (nextInLine == null || nextInLine.match.index() >= waterMark) {
                        return match;
                      }
                      occurrences.remove().enqueueNextOccurrence(input, waterMark, occurrences);
                    }
                  });
            }

            // separatedBy() moves one char at a time when boundary mismatches.
            // As such firstOccurrence().separatedBy().repeatedly() will end up re-applying all
            // candidate patterns at every index.
            //
            // This override rewrites it to separatedBy().firstOccurrence().repeatedly(),
            // Because firstOccurrence() implements the fast-forwarding optimization, the
            // separatedBy() boundary scanning is significantly reduced.
            //
            // This rewrite is mostly equivalent before vs. after because if a boundary check
            // disqualifies a candidate pattern returned by firstOccurrence(), all of the other
            // candidate
            // patterns will be tried.
            //
            // There is one subtle difference though. Without this override:
            //     In ['foo', 'food'].collect(firstOccurrence()).separatedBy(whitespace());
            //     'foo' will be matched and then separatedBy(whitespace()) will disqualify it;
            //     The next iteration of separatedBy() will start from the second char 'o', so
            //     'food' will never get a chance to match.
            // With the override, the above expression is rewritten to:
            //     ['foo', 'food'].separatedBy(whitespace()).collect(firstOccurrence()).
            //     firstOccurrence().repeatedly() is in the driving seat and will attempt 'food'
            //     from the first char.
            @Override public Pattern separatedBy(CharPredicate boundaryBefore, CharPredicate boundaryAfter) {
              requireNonNull(boundaryBefore);
              requireNonNull(boundaryAfter);
              return candidates.stream()
                  .map(c -> c.separatedBy(boundaryBefore, boundaryAfter))
                  .collect(firstOccurrence());
            }

            @Override Pattern lookaround(String lookbehind, String lookahead) {
              return candidates.stream()
                  .map(c -> c.lookaround(lookbehind, lookahead))
                  .collect(firstOccurrence());
            }

            @Override Pattern negativeLookaround(String lookbehind, String lookahead) {
              return candidates.stream()
                  .map(c -> c.negativeLookaround(lookbehind, lookahead))
                  .collect(firstOccurrence());
            }

            @Override
            public String toString() {
              return "firstOccurrenceOf(" + candidates + ")";
            }
          };
        });
  }

  /**
   * Returns a {@code Pattern} that matches the first occurrence of {@code stop1}, followed by an
   * occurrence of {@code stop2}, followed sequentially by occurrences of {@code moreStops} in
   * order, including any characters between consecutive stops.
   *
   * <p>Note that with more than two stops and if all the stops are literals, you may want to use
   * {@link StringFormat#span StringFormat.span()} instead.
   *
   * <p>For example, to find hyperlinks like {@code <a href="...">...</a>}, you can use {@code
   * StringFormat.span("<a href=\"{link}\">{...}</a>")}, which is equivalent to {@code
   * spanningInOrder("<a href=\"", "\">", "</a>")} but more self-documenting with proper placeholder
   * names.
   */
  public static Pattern spanningInOrder(String stop1, String stop2, String... moreStops) {
    Pattern result = first(stop1).extendTo(first(stop2));
    for (String stop : moreStops) {
      result = result.extendTo(first(stop));
    }
    return result;
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code str}. */
  public static Pattern last(String str) {
    if (str.length() == 1) {
      return last(str.charAt(0));
    }
    return new Last() {
      @Override Match match(String input, int fromIndex, int endIndex) {
        int index = str.isEmpty() ? endIndex : input.lastIndexOf(str, endIndex - 1);
        return index >= fromIndex
            ? Match.nonBacktrackable(input, index, str.length())
            : null;
      }

      @Override public String toString() {
        return "last('" + str + "')";
      }
    };
  }

  /** Returns a {@code Pattern} that matches the last occurrence of {@code character}. */
  public static Pattern last(char character) {
    return new Last() {
      @Override Match match(String input, int fromIndex, int endIndex) {
        int index = input.lastIndexOf(character, endIndex - 1);
        return index >= fromIndex ? Match.nonBacktrackable(input, index, 1) : null;
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
            //
            // For before(first('/')).separatedBy(), if boundary doesn't match, it's not loggically correct
            // to try the second '/'.
            : new Match(
                input,
                fromIndex,
                match.startIndex - fromIndex,
                Integer.MAX_VALUE,
                match.repetitionStartIndex);
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
            // upToIncluding(first('/')).separatedBy() should not backtrack to the second '/'.
            : new Match(
                input,
                fromIndex,
                match.endIndex - fromIndex,
                Integer.MAX_VALUE,
                match.repetitionStartIndex);
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
   * Similar to {@link #between(String, String)} but allows to use alternative bound styles
   * to include or exclude the delimiters at both ends.
   *
   * @since 7.2
   */
  public static Pattern between(String open, BoundStyle openBound, String close, BoundStyle closeBound) {
    return between(first(open), openBound, first(close), closeBound);
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
   * Similar to {@link #between(char, char)} but allows to use alternative bound styles
   * to include or exclude the delimiters at both ends.
   *
   * @since 7.2
   */
  public static Pattern between(char open, BoundStyle openBound, char close, BoundStyle closeBound) {
    return between(first(open), openBound, first(close), closeBound);
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
    return between(open, BoundStyle.EXCLUSIVE, close, BoundStyle.EXCLUSIVE);
  }

  /**
   * Similar to {@link #between(Pattern, Pattern)} but allows to use alternative bound styles
   * to include or exclude the delimiters at both ends.
   *
   * @since 7.2
   */
  public static Pattern between(Pattern open, BoundStyle openBound, Pattern close, BoundStyle closeBound) {
    requireNonNull(open);
    requireNonNull(openBound);
    requireNonNull(close);
    requireNonNull(closeBound);
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
        int startIndex = openBound == BoundStyle.INCLUSIVE ? left.startIndex : left.endIndex;
        int len = (closeBound == BoundStyle.INCLUSIVE ? right.endIndex : right.startIndex) - startIndex;
        // Include the closing delimiter in the next iteration. This allows delimiters in
        // patterns like "/foo/bar/baz/" to be treated more intuitively.
        return Match.backtrackable(
            /*backtrackingOffset=*/ left.backtrackIndex - startIndex,
            input,
            startIndex,
            /*length=*/ len);
      }

      @Override public String toString() {
        return openBound == BoundStyle.EXCLUSIVE && closeBound == BoundStyle.EXCLUSIVE
            ? "between(" + open + ", " + close + ")"
            : "between(" + open + ", " + openBound + ", " + close + ", " + closeBound + ")";
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
     * Matches this pattern against {@code string} starting from the character at {@code fromIndex},
     * returning a {@code Match} if successful, or {@code empty()} otherwise.
     *
     * <p>Note that it treats {@code fromIndex} as the beginning of the string, so patterns like
     * {@link #prefix}, {@link #BEGINNING} will attempt to match from this index.
     *
     * @throws IndexOutOfBoundsException if fromIndex is negative or greater than {@code
     *     string.length()}
     * @since 7.0
     */
    public final Optional<Match> in(String string, int fromIndex) {
      if (fromIndex < 0) {
        throw new IndexOutOfBoundsException("Invalid index (" + fromIndex + ") < 0");
      }
      if (fromIndex > string.length()) {
        throw new IndexOutOfBoundsException(
            "Invalid index (" + fromIndex + ") > length (" + string.length() + ")");
      }
      return Optional.ofNullable(match(string, fromIndex));
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
      return replaceFrom(string, m -> replacement);
    }

    /**
     * Returns a new string with the substring matched by {@code this} replaced by
     * the return value of {@code replacementFunction}.
     *
     * <p>For example, you can replace a single template placeholder using:
     *
     * <pre>{@code
     * Substring.spanningInOrder("{", "}")
     *     .replaceFrom(s, placeholder -> replacements.get(placeholder.skip(1, 1).toString()));
     * }</pre>
     *
     * <p>Returns {@code string} as-is if a substring is not found.
     *
     * @since 5.6
     */
    public final String replaceFrom(
        String string, Function<? super Match, ? extends CharSequence> replacementFunction) {
      requireNonNull(replacementFunction);
      Match match = match(string);
      return match == null ? string : match.replaceWith(replacementFunction.apply(match));
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

        // Allow separatedBy() to trigger backtracking.
        @Override public Pattern separatedBy(
            CharPredicate boundaryBefore, CharPredicate boundaryAfter) {
          return base.separatedBy(boundaryBefore, boundaryAfter)
              .or(that.separatedBy(boundaryBefore, boundaryAfter));
        }

        // Allow between() to trigger backtracking.
        @Override Pattern lookaround(String lookbehind, String lookahead) {
          return base.lookaround(lookbehind, lookahead)
              .or(that.lookaround(lookbehind, lookahead));
        }

        // Allow notImmediatelyBetween() to trigger backtracking.
        @Override Pattern negativeLookaround(String lookbehind, String lookahead) {
          return base.negativeLookaround(lookbehind, lookahead)
              .or(that.negativeLookaround(lookbehind, lookahead));
        }

        @Override public Pattern peek(Pattern following) {
          return base.peek(following).or(that.peek(following));
        }

        @Override public Pattern limit(int maxChars) {
          return base.limit(maxChars).or(that.limit(maxChars));
        }

        @Override public Pattern skip(int fromBeginning, int fromEnd) {
          return base.skip(fromBeginning, fromEnd).or(that.skip(fromBeginning, fromEnd));
        }

        @Override public String toString() {
          return base + ".or(" + that + ")";
        }
      };
    }

    /**
     * Returns a {@code Pattern} that's equivalent to this pattern except it only matches at
     * most {@code maxChars}.
     *
     * @since 6.1
     */
    public Pattern limit(int maxChars) {
      checkNumChars(maxChars);
      Pattern base = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match m = base.match(input, fromIndex);
          return m == null ? null : m.limit(maxChars);
        }

        // For, firstOccurrence().limit().repeatedly(), apply firstOccurrence().iterate()
        // and then apply limit() on the result matches to take advantage of the optimization.
        @Override Stream<Match> iterate(String input, int fromIndex) {
          return base.iterate(input, fromIndex).map(m -> m.limit(maxChars));
        }

        @Override public String toString() {
          return base + ".limit(" + maxChars + ")";
        }
      };
    }

    /**
     * Returns a {@code Pattern} that's equivalent to this pattern except it will skip up to {@code
     * fromBeginnings} characters from the beginning of the match and up to {@code fromEnd} characters
     * from the end of the match.
     *
     * <p>If the match includes fewer characters, an empty match is returned.
     *
     * @since 6.1
     */
    public Pattern skip(int fromBeginning, int fromEnd) {
      checkNumChars(fromBeginning);
      checkNumChars(fromEnd);
      Pattern original = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          Match m = original.match(input, fromIndex);
          return m == null ? null : m.skip(fromBeginning, fromEnd);
        }

        // For firstOccurrence().skiip().repeatedly(), apply
        // firstOccurrence().iterate() to take advantage of the optimization and then apply
        // skip() on the result matches.
        @Override Stream<Match> iterate(String input, int fromIndex) {
          return original.iterate(input, fromIndex).map(m -> m.skip(fromBeginning, fromEnd));
        }

        @Override public String toString() {
          return original + ".skip(" + fromBeginning + ", " + fromEnd + ")";
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
              ? new Match(
                  input,
                  next.startIndex,
                  next.length(),
                  preceding.backtrackIndex,
                  preceding.repetitionStartIndex)
              : next;
        }

        @Override public Pattern separatedBy(CharPredicate boundaryBefore, CharPredicate boundaryAfter) {
          return base.then(following.separatedBy(boundaryBefore, boundaryAfter));
        }

        @Override Pattern lookaround(String lookbehind, String lookahead) {
          return base.then(following.lookaround(lookbehind, lookahead));
        }

        @Override
        public Pattern negativeLookaround(String lookbehind, String lookahead) {
          return base.then(following.negativeLookaround(lookbehind, lookahead));
        }

        @Override public String toString() {
          return base + ".then(" + following + ")";
        }
      };
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
     * <p>If look-ahead is needed, you can use {@link #followedBy} as in {@code
     * first("foo").followedBy("bar")}.
     *
     * <p>If you are trying to define a boundary around or after your pattern similar to regex
     * anchor {@code '\b'}, consider using {@link #separatedBy} if the boundary can be detected by
     * a character.
     *
     * @since 6.0
     */
    public Pattern peek(Pattern following) {
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
     * Returns an otherwise equivalent {@code Pattern}, except it only matches if it's next
     * to the beginning of the string, the end of the string, or the {@code separator} character(s).
     *
     * <p>Useful if you are trying to find a word with custom boundaries. To search for words
     * composed of regex {@code \w} character class, consider using {@link Substring#word} instead.
     *
     * <p>For lookahead and lookbehind assertions, consider using {@link #immediatelyBetween} or {@link
     * #followedBy} instead.
     *
     * @since 6.2
     */
    public final Pattern separatedBy(CharPredicate separator) {
      return separatedBy(separator, separator);
    }

    /**
     * Returns an otherwise equivalent {@code Pattern}, except it requires the
     * beginning of the match must either be the beginning of the string, or be separated from the rest
     * of the string by the {@code separatorBefore} character; and the end of the match must either
     * be the end of the string, or be separated from the rest of the string by the {@code
     * separatorAfter} character.
     *
     * <p>Useful if you are trying to find a word with custom boundaries. To search for words
     * composed of regex {@code \w} character class, consider using {@link Substring#word} instead.
     *
     * <p>For lookahead and lookbehind assertions, consider using {@link #between} or {@link
     * #followedBy} instead.
     *
     * @since 6.2
     */
    public Pattern separatedBy(CharPredicate separatorBefore, CharPredicate separatorAfter) {
      requireNonNull(separatorBefore);
      requireNonNull(separatorAfter);
      Pattern target = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          while (fromIndex <= input.length()) {
            if (fromIndex > 0 && !separatorBefore.test(input.charAt(fromIndex - 1))) {
              fromIndex++;
              continue; // The current position cannot possibly be the beginning of match.
            }
            Match match = target.match(input, fromIndex);
            if (match == null) {
              return null;
            }
            if (match.startIndex == fromIndex // Already checked boundaryBefore
                || separatorBefore.test(input.charAt(match.startIndex - 1))) {
              int boundaryIndex = match.endIndex;
              if (boundaryIndex >= input.length()
                  || separatorAfter.test(input.charAt(boundaryIndex))) {
                return match;
              }
            }
            fromIndex = match.backtrackFrom(fromIndex);
          }
          return null;
        }

        @Override public String toString() {
          return target + ".separatedBy(" + separatorBefore + ", " + separatorAfter + ")";
        }
      };
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring be
     * immediately preceded by the {@code lookbehind} string and immediately followed by the {@code
     * after} string.
     *
     * <p>Similar to regex lookarounds, the returned pattern will backtrack until the lookaround is
     * satisfied. That is, {@code word().immediatelyBetween("(", ")")} will find the "bar" substring inside the
     * parenthesis from "foo (bar)".
     *
     * <p>If you need lookahead only, use {@link #followedBy} instead; for lookbehind only, pass an
     * empty string as the {@code lookahead} string, as in: {@code word().immediatelyBetween(":", "")}.
     *
     * @since 6.2
     */
    public final Pattern immediatelyBetween(String lookbehind, String lookahead) {
      requireNonNull(lookbehind);
      requireNonNull(lookahead);
      return lookbehind.isEmpty() && lookahead.isEmpty()
          ? this
          : lookaround(lookbehind, lookahead);
    }

    /**
     * Similar to {@link #immediatelyBetween(String, String)}, but allows including the {@code
     * lookbehind} and/or {@code lookahead} inclusive in the match.
     *
     * <p>For example, to split around all "{placholder_name}", you can use:
     *
     * <pre>{@code
     * PLACEHOLDER_NAME_PATTERN.immediatelyBetween("{", INCLUSIVE, "}", INCLUSIVE)
     *     .split(input);
     * }</pre>
     *
     * @since 7.0
     */
    public final Pattern immediatelyBetween(
        String lookbehind,
        BoundStyle lookbehindBound,
        String lookahead,
        BoundStyle lookaheadBound) {
      Pattern withLookaround = lookaround(lookbehind, lookahead);
      int behind = requireNonNull(lookbehindBound) == BoundStyle.INCLUSIVE ? lookbehind.length() : 0;
      int ahead = requireNonNull(lookaheadBound) == BoundStyle.INCLUSIVE ? lookahead.length() : 0;
      if (behind == 0 && ahead == 0) {
        return withLookaround;
      }
      Pattern original = this;
      return new Pattern() {
        @Override
        Match match(String input, int fromIndex) {
          Match match = withLookaround.match(input, fromIndex);
          return match == null ? null : match.expand(behind, ahead);
        }

        @Override
        public String toString() {
          return original
              + ".immediatelyBetween('"
              + lookbehind
              + "', "
              + lookbehindBound
              + ", '"
              + lookahead
              + "', "
              + lookaheadBound
              + ")";
        }
      };
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring <em>not</em>
     * be immediately preceded by the {@code lookbehind} string and immediately followed by the {@code
     * after} string.
     *
     * <p>Similar to regex negative lookarounds, the returned pattern will backtrack until the
     * negative lookaround is satisfied. That is, {@code word().notImmediatelyBetween("(", ")")} will find the
     * "bar" substring from "(foo) bar".
     *
     * <p>If you need negative lookahead only, use {@link #notFollowedBy} instead; for negative
     * lookbehind only, pass an empty string as the {@code lookahead} string, as in: {@code
     * word().notImmediatelyBetween(":", "")}.
     *
     * <p>If the pattern shouldn't be preceded or followed by particular character(s), consider
     * using {@link #separatedBy}. The following code finds "911" but only if it's at the beginning
     * of a number:
     *
     * <pre>{@code
     * Substring.Pattern emergency =
     *     first("911").separatedBy(CharPredicate.range('0', '9').not(), CharPredicate.ANY);
     * }</pre>
     *
     * @since 6.2
     */
    public final Pattern notImmediatelyBetween(String lookbehind, String lookahead) {
      requireNonNull(lookbehind);
      requireNonNull(lookahead);
      return lookbehind.isEmpty() && lookahead.isEmpty()
          ? NONE
          : negativeLookaround(lookbehind, lookahead);
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring be
     * immediately followed by the {@code lookahead} string.
     *
     * <p>Similar to regex negative lookahead, the returned pattern will backtrack until the lookahead is
     * satisfied. That is, {@code word().followedBy(":")} will find the "Joe" substring from "To
     * Joe:".
     *
     * <p>If you need lookbehind, or both lookahead and lookbehind, use {@link #immediatelyBetween} instead.
     *
     * @since 6.2
     */
    public final Pattern followedBy(String lookahead) {
      return immediatelyBetween("", lookahead);
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring <em>not</em>
     * be immediately followed by the {@code lookahead} string.
     *
     * <p>Similar to regex negative lookahead, the returned pattern will backtrack until the
     * negative lookahead is satisfied. That is, {@code word().notFollowedBy(" ")} will find the
     * "Joe" substring from "To Joe:".
     *
     * <p>If you need negative lookbehind, or both negative lookahead and lookbehind, use {@link
     * #notImmediatelyBetween} instead.
     *
     * <p>If the pattern shouldn't be followed by particular character(s), consider using {@link
     * #separatedBy}. The following code finds the file extension name ".java" if it's not followed
     * by another letter:
     *
     * <pre>{@code
     * CharPredicate letter = Character::isJavaIdentifierStart;
     * Substring.Pattern javaExtension =
     *     first(".java").separatedBy(CharPredicate.ANY, letter.not());
     * }</pre>
     *
     * @since 6.2
     */
    public final Pattern notFollowedBy(String lookahead) {
      return notImmediatelyBetween("", lookahead);
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring be
     * immediately preceded by the {@code lookahead} string.
     *
     * <p>Similar to regex lookbehind, the returned pattern will backtrack until the lookbehind is
     * satisfied. That is, {@code word().precededBy(": ")} will find the "Please" substring from
     * "Amy: Please come in".
     *
     * @since 6.2
     */
    public final Pattern precededBy(String lookbehind) {
      return immediatelyBetween(lookbehind, "");
    }

    /**
     * Returns an otherwise equivalent pattern except it requires the matched substring <em>not</em> be
     * immediately preceded by the {@code lookbehind} string.
     *
     * <p>Similar to regex negative lookbehind, the returned pattern will backtrack until the
     * negative lookbehind is satisfied. For example, {@code word().notPrecededBy("(")} will find the
     * "bar" substring from "(foo+bar)".
     *
     * @since 6.2
     */
    public final Pattern notPrecededBy(String lookbehind) {
      return notImmediatelyBetween(lookbehind, "");
    }

    /**
     * Matches this pattern and then matches {@code following}.
     * The result matches from the beginning of this pattern to the end of {@code following}.
     */
    final Pattern extendTo(Pattern following) {
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
              preceding.backtrackIndex,
              // Keep the repetitionStartIndex strictly increasing to avoid the next iteration
              // in repeatedly() to be stuck with no progress.
              Math.max(preceding.repetitionStartIndex, next.repetitionStartIndex));
        }

        @Override public String toString() {
          return base + ".extendTo(" + following + ")";
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
    public RepeatingPattern repeatedly() {
      return new RepeatingPattern() {
        @Override public Stream<Match> match(String input, int fromIndex) {
          return iterate(requireNonNull(input), fromIndex);
        }

        @Override public String toString() {
          return Pattern.this + ".repeatedly()";
        }
      };
    }

    /**
     * Matches against {@code string} starting from {@code fromIndex}, and returns null if not
     * found.
     */
    abstract Match match(String string, int fromIndex);

    /** Applies this pattern repeatedly against {@code input} and returns all iterations. */
    Stream<Match> iterate(String input, int fromIndex) {
      return MoreStreams.whileNotNull(
          new Supplier<Match>() {
            private final int end = input.length();
            private int nextIndex = fromIndex;

            @Override public Match get() {
              if (nextIndex > end) {
                return null;
              }
              Match match = match(input, nextIndex);
              if (match == null) {
                return null;
              }
              if (match.endIndex == end) { // We've consumed the entire string.
                nextIndex = Integer.MAX_VALUE;
              } else if (match.repetitionStartIndex > nextIndex) {
                nextIndex = match.repetitionStartIndex;
              } else {
                throw new IllegalStateException(
                    "Infinite loop detected at " + match.repetitionStartIndex);
              }
              return match;
            }
          });
    }

    private Match match(String string) {
      return match(string, 0);
    }

    Pattern lookaround(String lookbehind, String lookahead) {
      Pattern target = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          int lastIndex = input.length() - lookahead.length();
          while (fromIndex <= lastIndex) {
            Match match = target.match(input, fromIndex);
            if (match == null || match.isImmediatelyBetween(lookbehind, lookahead)) {
              return match;
            }
            fromIndex = match.backtrackFrom(fromIndex);
          }
          return null;
        }

        @Override public String toString() {
          return target + ".immediatelyBetween('" + lookbehind + "', '" + lookahead + "')";
        }
      };
    }

    Pattern negativeLookaround(String lookbehind, String lookahead) {
      Pattern target = this;
      return new Pattern() {
        @Override Match match(String input, int fromIndex) {
          while (fromIndex <= input.length()) {
            Match match = target.match(input, fromIndex);
            if (match == null || !match.isImmediatelyBetween(lookbehind, lookahead)) {
              return match;
            }
            fromIndex = match.backtrackFrom(fromIndex);
          }
          return null;
        }

        @Override public String toString() {
          return target + ".notImmediatelyBetween('" + lookbehind + "', '" + lookahead + "')";
        }
      };
    }

    /**
     * Do not depend on the string representation of Substring, except for subtypes {@link Prefix}
     * and {@link Suffix} that have an explicitly defined representation.
     */
    @Override public String toString() {
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
     * Applies this pattern against {@code string} starting from {@code fromIndex} and returns a
     * stream of each iteration.
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
     *
     * @since 8.2
     */
    public abstract Stream<Match> match(String input, int fromIndex);

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
    public final Stream<Match> match(String input) {
      return match(input, 0);
    }

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

            @Override public Match get() {
              if (it.hasNext()) {
                Match delim = it.next();
                Match result = Match.nonBacktrackable(string, next, delim.index() - next);
                next = delim.endIndex;
                return result;
              }
              if (next >= 0) {
                Match result = Match.nonBacktrackable(string, next, string.length() - next);
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
     * Returns a stream of {@code Match} objects from the input {@code string} as demarcated by this
     * delimiter pattern. It's similar to {@link #split} but includes both the substrings split by
     * the delimiters and the delimiter substrings themselves, interpolated in the order they appear
     * in the input string.
     *
     * <p>For example,
     *
     * <pre>{@code
     * spanningInOrder("{", "}").repeatedly().cut("Dear {user}: please {act}.")
     * }</pre>
     *
     * will result in the stream of {@code ["Dear ", "{user}", ": please ", "{act}", "."]}.
     *
     * <p>The returned {@code Match} objects are cheap "views" of the matched substring sequences.
     * Because {@code Match} implements {@code CharSequence}, the returned {@code Match} objects can
     * be directly passed to {@code CharSequence}-accepting APIs such as Guava {@code
     * CharMatcher.trimFrom}, {@link Pattern#splitThenTrim}, etc.
     *
     * @since 7.1
     */
    public Stream<Match> cut(String string) {
      Iterator<Match> delimiters = match(string).iterator();
      return whileNotNull(
          new Supplier<Match>() {
            Match delimiter = null;
            int next = 0;

            @Override
            public Match get() {
              if (next == -1) {
                return null;
              }
              if (delimiter == null) { // Should return the substring before the next delimiter.
                if (delimiters.hasNext()) {
                  delimiter = delimiters.next();
                  Match result = Match.nonBacktrackable(string, next, delimiter.index() - next);
                  next = delimiter.endIndex;
                  return result;
                } else {
                  Match result = Match.nonBacktrackable(string, next, string.length() - next);
                  next = -1;
                  return result;
                }
              }
              // should return delimiter
              Match result = delimiter;
              delimiter = null;
              return result;
            }
          });
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

    /**
     * Returns the alternation of this pattern from the {@code input} string, with the matched
     * substring alternated with the trailing substring before the next match.
     *
     * <p>For example: to find bulleted items (strings prefixed by {@code 1:}, {@code 2:},
     * {@code 456:} etc.), you can:
     *
     * <pre>{@code
     * Substring.Pattern bulletNumber = consecutive(CharPredicate.range('0', '9'))
     *     .separatedBy(CharPredicate.WORD.not(), CharPredicate.is(':'));
     * Map<Integer, String> bulleted = bulletNumber.repeatedly()
     *     .alternationFrom("1: go home;2: feed 2 cats 3: sleep tight.")
     *     .mapKeys(n -> Integer.parseInt(n))
     *     .mapValues(withColon -> prefix(":").removeFrom(withColon.toString()).trim())
     *     .toMap();
     *     // => [{1, "go home;"}, {2, "feed 2 cats"}, {3, "sleep tight."}]
     * }</pre>
     *
     * @since 6.1
     */
    public final BiStream<String, String> alternationFrom(String input) {
      return Stream.concat(match(input), Stream.of(END.in(input).get()))
          .collect(BiStream.toAdjacentPairs())
          .mapValues((k, k2) -> input.substring(k.index() + k.length(), k2.index()))
          .mapKeys(Match::toString);
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
     * Returns a <em>view</em> of {@code source} with this prefix hidden away if present, or else
     * returns {@code source} as is.
     *
     * @since 6.5
     */
    public CharSequence hideFrom(String source) {
      if (length() == 0) {
        return requireNonNull(source);
      }
      Match match = match(source, 0);
      return match == null ? source : match.following();
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
          ? Match.nonBacktrackable(input, fromIndex, prefix.length())
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
     * Returns a <em>view</em> of {@code source} with this suffix hidden away if present, or else
     * returns {@code source} as is.
     *
     * @since 6.5
     */
    public CharSequence hideFrom(String source) {
      if (length() == 0) {
        return requireNonNull(source);
      }
      Match match = match(source, 0);
      return match == null ? source : match.preceding();
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
          ? Match.suffix(input, suffix.length())
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

    /** When the match fails lookahead or lookbehind conditions, use this index to backtrack. */
    private final int backtrackIndex;

    private String toString;

    private Match(String context, int startIndex, int length, int backtrackIndex, int repetitionStartIndex) {
      this.context = context;
      this.startIndex = startIndex;
      this.endIndex = startIndex + length;
      this.backtrackIndex = backtrackIndex;
      this.repetitionStartIndex = repetitionStartIndex;
      assert startIndex >= 0 : "Invalid index: " + startIndex;
      assert length >= 0 : "Invalid length: " + length;
      assert endIndex <= context.length() : "Invalid endIndex: " + endIndex;
      assert repetitionStartIndex >= endIndex : "Invalid repetitionStartIndex: " + repetitionStartIndex;
    }

    static Match suffix(String context, int length) {
      return nonBacktrackable(context, context.length() - length, length);
    }

    static Match backtrackable(int backtrackingOffset, String context, int fromIndex, int length) {
      return new Match(
          context, fromIndex, length, fromIndex + backtrackingOffset, fromIndex + max(1, length));
    }

    static Match nonBacktrackable(String context, int fromIndex, int length) {
      return new Match(context, fromIndex, length, Integer.MAX_VALUE, fromIndex + max(1, length));
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

    /**
     * Returns true if the match is empty.
     *
     * @since 7.2
     */
    @Override
    public boolean isEmpty() {
      return length() == 0;
    }

    /**
     * Returns true if the match isn't empty.
     *
     * @since 6.0
     */
    public boolean isNotEmpty() {
      return length() > 0;
    }

    /**
     * Returns an equivalent match with at most {@code maxChars}.
     *
     * @since 6.1
     */
    public Match limit(int maxChars) {
      return checkNumChars(maxChars) >= length()
          ? this
          : new Match(context, startIndex, maxChars, backtrackIndex, repetitionStartIndex);
    }

    /**
     * Returns a new instance that's otherwise equivalent except with {@code fromBeginning}
     * characters skipped from the beginning and {@code fromEnd} characters skipped from the end.
     * If there are fewer characters, an empty match is returned.
     *
     * <p>For example, {@code first("hello").in("say hello").get().skip(2, 1)} returns "ll".
     *
     * @since 6.1
     */
    public Match skip(int fromBeginning, int fromEnd) {
      checkNumChars(fromBeginning);
      checkNumChars(fromEnd);
      if (fromBeginning >= length()) {
        return new Match(context, endIndex, 0, backtrackIndex, repetitionStartIndex);
      }
      int index = startIndex + fromBeginning;
      if (fromEnd >= length() - fromBeginning) {
        return new Match(context, index, 0, backtrackIndex, repetitionStartIndex);
      }
      int len = length() - fromBeginning - fromEnd;
      return new Match(context, index, len, backtrackIndex, repetitionStartIndex);
    }

    /** Return 0-based index of this match in {@link #fullString}. */
    public int index() {
      return startIndex;
    }

    /**
     * Equivalent to {@code toString().equals(str)} but without copying the characters into a
     * temporary string.
     *
     * @since 7.1
     */
    public boolean contentEquals(String str) {
      return str.length() == length() && context.startsWith(str, startIndex);
    }

    /**
     * Returns true if this match starts with the given {@code prefix}.
     *
     * @since 7.0
     */
    public boolean startsWith(String prefix) {
      return prefix.length() <= length() && context.startsWith(prefix, startIndex);
    }

    /**
     * Returns true if this match ends with the given {@code suffix}.
     *
     * @since 7.0
     */
    public boolean endsWith(String suffix) {
      return suffix.length() <= length() && context.startsWith(suffix, endIndex - suffix.length());
    }

    /**
     * Returns true if the match is immediately followed by the {@code lookahead} string. Note that
     * {@code isFollowedBy("")} is always true.
     */

    public boolean isFollowedBy(String lookahead) {
      return context.startsWith(lookahead, endIndex);
    }

    /**
     * Returns true if the match immediately follows the {@code lookbehind} string. Note that {@code
     * isPrecededBy("")} is always true.
     */
    public boolean isPrecededBy(String lookbehind) {
      return context.startsWith(lookbehind, startIndex - lookbehind.length());
    }

    /**
     * Returns true if the match immediately follows the {@code lookbehind} string and is
     * immediately followed by the {@code lookahead} string. Note that {@code isBetween("", "")} is
     * always true.
     */
    public boolean isImmediatelyBetween(String lookbehind, String lookahead) {
      return isPrecededBy(lookbehind) && isFollowedBy(lookahead);
    }

    boolean isSeparatedBy(CharPredicate separatorBefore, CharPredicate separatorAfter) {
      return (startIndex == 0 || separatorBefore.test(context.charAt(startIndex - 1)))
          && (endIndex >= context.length() || separatorAfter.test(context.charAt(endIndex)));
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
     * Returns a {@link CharSequence} instance which is a sub-range of this {@code Match}.
     *
     * <p>For example, if this {@code Match} points to the range of {@code "wood"} from
     * the {@code "Holywood"} string, calling {@code subSequence(1, 3)} will point to the
     * range of {@code "oo"} from the original string.
     *
     * <p>Can be used to further reduce the matched range manually.
     *
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
      return new Match(
          context, startIndex + begin, end - begin, Integer.MAX_VALUE, repetitionStartIndex);
    }

    /** Returns the matched substring. */
    @Override public String toString() {
      // http://jeremymanson.blogspot.com/2008/12/benign-data-races-in-java.html
      String str = toString;
      if (str == null) {
        toString = str = context.substring(startIndex, endIndex);
      }
      return str;
    }

    Match following() {
      return suffix(context, context.length() - endIndex);
    }

    Match preceding() {
      return nonBacktrackable(context, 0, startIndex);
    }

    /**
     * Expands this match for {@code toLeft} characters before the starting index and {@code
     * toRight} characters beyond the end index.
     *
     * @throws IllegalArgumentException if either {@code toLeft} or {@code toRight} is negative
     * @throws IllegalStateException if there are not sufficient characters to expand
     */
    Match expand(int toLeft, int toRight) {
      assert toLeft >= 0 : "Invalid toLeft: " + toLeft;
      assert toRight >= 0 : "Invalid toRight: " + toRight;
      int newStartIndex = startIndex - toLeft;
      int newLength = length() + toLeft + toRight;
      int newEndIndex = newStartIndex + newLength;
      int newRepetitionStartIndex = max(repetitionStartIndex, newEndIndex);
      return new Match(context, newStartIndex, newLength, backtrackIndex, newRepetitionStartIndex);
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
          : new Match(context, left, trimmedLength, backtrackIndex, repetitionStartIndex);
    }

    private Match toEnd() {
      return endIndex == context.length()
          ? this
          : suffix(context, context.length() - startIndex);
    }

    private int backtrackFrom(int fromIndex) {
      if (backtrackIndex <= fromIndex) {
        throw new IllegalStateException("Not true that " + backtrackIndex + " > " + fromIndex);
      }
      return backtrackIndex;
    }
  }

  /**
   * The style of the bounds of a match. See {@link Substring.Pattern#immediatelyBetween(String,
   * BoundStyle, String, BoundStyle)}.
   *
   * @since 7.0
   */
  public enum BoundStyle {
    /** The match includes the bound */
    INCLUSIVE,
    /** The match doesn't include the bound */
    EXCLUSIVE;
  }

  abstract static class Last extends Pattern {
    abstract Match match(String input, int fromIndex, int endIndex);

    @Override Match match(String input, int fromIndex) {
      return match(input, fromIndex, input.length());
    }

    @Override public Pattern separatedBy(CharPredicate separatorBefore, CharPredicate separatorAfter) {
      requireNonNull(separatorBefore);
      requireNonNull(separatorAfter);
      return look(match -> match.isSeparatedBy(separatorBefore, separatorAfter));
    }

    @Override Pattern lookaround(String lookbehind, String lookahead) {
      return look(match -> match.isImmediatelyBetween(lookbehind, lookahead));
    }

    @Override Pattern negativeLookaround(String lookbehind, String lookahead) {
      return look(match -> !match.isImmediatelyBetween(lookbehind, lookahead));
    }

    private Pattern look(Predicate<Match> condition) {
      Last original = this;
      return new Last() {
        @Override Match match(String input, int fromIndex, int endIndex) {
          for (int i = endIndex; i >= fromIndex; ) {
            Match match = original.match(input, fromIndex, i);
            if (match == null || condition.test(match)) {
              return match;
            }
            i = min(match.endIndex - 1, i - 1);
          }
          return null;
        }
      };
    }
  }

  private static int checkNumChars(int maxChars) {
    if (maxChars < 0) {
      throw new IllegalArgumentException("Number of characters (" + maxChars + ") cannot be negative.");
    }
    return maxChars;
  }

  private Substring() {}
}

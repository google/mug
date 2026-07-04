/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Utils.caseInsensitivePrefixes;
import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.common.labs.parse.Utils.checkPositionIndex;
import static com.google.common.labs.parse.Utils.checkState;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.mu.util.stream.BiStream.biStream;
import static com.google.mu.util.stream.MoreCollectors.mapping;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Comparator.reverseOrder;
import static java.util.Objects.requireNonNull;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.mu.function.Function4;
import com.google.mu.function.ObjInt2Function;
import com.google.mu.function.TriFunction;
import com.google.mu.util.Both;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.Joiner;

/**
 * A simple recursive descent parser combinator intended to parse simple grammars such as regex, csv
 * format string patterns etc.
 *
 * <p>Different from most parser combinators (such as Haskell Parsec), a common source of bug
 * (infinite loop or StackOverFlowError caused by accidental zero-consumption rule in the context of
 * many() or recursive grammar) is made impossible by requiring all parsers to consume at least one
 * character. Optional suffix is achieved through using the built-in combinators such as {@link
 * #optionallyFollowedBy optionallyFollowedBy()} and {@link #withPostfixes withPostfixes()};
 * or you can use the {@link #zeroOrMore zeroOrMore()}, {@link #zeroOrMoreDelimitedBy zeroOrMoreDelimitedBy()},
 * {@link #orElse orElse()} and {@link #optional optional()} fluent chains.
 *
 * <p>For simplicity, {@link #or or()} and {@link #anyOf anyOf()} will always backtrack upon failure.
 * anyOf(expr.followedBy(";"), expr)}, use {@code expr.optionallyFollowedBy(";")} instead.
 */
@ThreadSafe
public abstract non-sealed class Parser<T> implements Production<T> {
  private static final Set<String> EMPTY_PREFIX = Set.of("");

  /**
   * Only use in context where input consumption is guaranteed. Do not use within a loop, like
   * atLeastOnce(), zeroOrMore()!
   */
  private static final Parser<Void> UNSAFE_EOF = new Parser<>() {
    @Override MatchResult<Void> skipAndMatch(
        Parser<?> skip, CharInput input, int start, ErrorContext context) {
      start = skipIfAny(skip, input, start);
      return input.isEof(start)
          ? new MatchResult.Success<>(start, start, null)
          : context.expecting("EOF", start);
    }
  };

  /**
   * Matches the given character {@code c}.
   *
   * @since 9.9.9
   */
  public static Parser<Character> one(char c) {
    return string(Character.toString(c)).thenReturn(c);
  }

  /**
   * Matches a character in {@code characterSet}.
   *
   * @deprecated Use {@link #one(String)} instead
   * @since 9.9.9
   */
  @Deprecated
  public static Parser<Character> one(CharacterSet characterSet) {
    return one(characterSet, characterSet.toString());
  }

  /**
   * Matches a character in {@code characterClass}.
   *
   * <p>For example: {@code one("[a-z]")} is equivalent to {@code one(range('a', 'z'))}.
   *
   * <p>Implementation Note: regex isn't used during parsing. The character class string is translated
   * to a {@link CharPredicate#precomputeForAscii precomputed} {@code CharPredicate}, at construction time.
   *
   * @param characterClass A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}).
   *        Starting v10.6, literal backslash ({@code "\\"} in Java source code literal),
   *        '[' and ']' can all be included inside the outer pair of brackets.
   *        The '-' character as long as not at the place of a range is also treated as literal.
   *        You can also use {@code '^'} to get negative character set like:
   *        {@code one("[^a-zA-Z]")}, which is any non-alphabet character.
   *        You are strongly recommended to install Google ErrorProne and mug-errorprone in your
   *        annotation processor path so that incorrect character class syntax will be caught
   *        at compile-time.
   * @since 10.2
   */
  @SuppressWarnings("CharacterSetLiteralCheck")
  public static Parser<Character> one(String characterClass) {
    return one(charsIn(characterClass), characterClass);
  }

  /**
   * Matches a character as specified by {@code matcher}.
   *
   * @since 9.9.3
   */
  public static Parser<Character> one(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override MatchResult<Character> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        return input.isInRange(start) && matcher.test(input.charAt(start))
            ? new MatchResult.Success<>(start, start + 1, input.charAt(start))
            : context.expecting(name, start);
      }

      @Override Set<String> getPrefixes() {
        return prefixesIfAscii(matcher);
      }
    };
  }

  /** Matches one or more consecutive characters as specified by {@code matcher}. */
  public static Parser<String> consecutive(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<Void>() {
      @Override MatchResult<Void> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        int end = start;
        for (; input.isInRange(end) && matcher.test(input.charAt(end)); end++) {}
        return end > start
            ? new MatchResult.Success<>(start, end, null)
            : context.expecting(name, end);
      }

      @Override Set<String> getPrefixes() {
        return prefixesIfAscii(matcher);
      }
    }.source();
  }

  /** Matches {@code n} consecutive characters as specified by {@code matcher}. */
  static Parser<String> consecutive(int n, CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    checkArgument(n > 0, "n(%s) must be positive", n);
    return new Parser<Void>() {
      @Override MatchResult<Void> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        if (!input.isInRange(start + n - 1)) {
          return context.expecting(name, start);
        }
        for (int i = 0; i < n; i++) {
          if (!matcher.test(input.charAt(start + i))) {
            return context.expecting(name, start + i);
          }
        }
        return  new MatchResult.Success<>(start, start + n, null);
      }

      @Override Set<String> getPrefixes() {
        return prefixesIfAscii(matcher);
      }
    }.source();
  }

  /**
   * Matches one or more consecutive characters contained in {@code characterSet}.
   *
   * @deprecated Use {@link #consecutive(String)} instead
   * @since 9.4
   */
  @Deprecated
  public static Parser<String> consecutive(CharacterSet characterSet) {
    return consecutive(characterSet, "one or more " + characterSet);
  }

  /**
   * Matches one or more consecutive characters contained in {@code characterClass}.
   *
   * <p>For example: {@code consecutive("[0-9]")} is equivalent to {@code consecutive(range('0', '9'))}.
   *
   * <p>Implementation Note: regex isn't used during parsing. The character class string is translated
   * to a {@link CharPredicate#precomputeForAscii precomputed} {@code CharPredicate}, at construction time.
   *
   * @param characterClass A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}).
   *        Starting v10.6, literal backslash ({@code "\\"} in Java source code literal),
   *        '[' and ']' can all be included inside the outer pair of brackets.
   *        The '-' character as long as not at the place of a range is also treated as literal.
   *        You can also use {@code '^'} to get negative character set like:
   *        {@code one("[^a-zA-Z]")}, which is any non-alphabet character.
   *        You are strongly recommended to install Google ErrorProne and mug-errorprone in your
   *        annotation processor path so that incorrect character class syntax will be caught
   *        at compile-time.
   * @since 10.2
   */
  @SuppressWarnings("CharacterSetLiteralCheck")
  public static Parser<String> consecutive(String characterClass) {
    return consecutive(charsIn(characterClass), "one or more " + characterClass);
  }

  /** Matches {@code n} consecutive characters contained in {@code characterClass}. */
  @SuppressWarnings("CharacterSetLiteralCheck")
  static Parser<String> consecutive(int n, String characterClass) {
    return consecutive(n, charsIn(characterClass), n + " " + characterClass);
  }

  /**
   * Consumes exactly {@code n} consecutive characters. {@code n} must be positive.
   *
   * @since 9.4
   */
  public static Parser<String> chars(int n) {
    return consecutive(n, CharPredicate.ANY, n + " char(s)");
  }

  /**
   * {@code word("or")} matches "or" but not "orange".
   *
   * @since 9.4
   */
  public static Parser<String> word(String word) {
    return string(word).notImmediatelyFollowedBy(CharPredicate.WORD, "[a-zA-Z0-9_]");
  }

  /**
   * One or more regex {@code \w+} characters.
   *
   * @since 9.4
   */
  public static Parser<String> word() {
    return Constants.WORD;
  }

  /**
   * One or more regex {@code \d+} characters.
   *
   * @since 9.4
   */
  public static Parser<String> digits() {
    return Constants.DIGITS;
  }

  /**
   * Matches {@code n} digits of {@code [0-9]}. {@code n} must be positive.
   *
   * @since 10.6
   */
  public static Parser<String> digits(int n) {
    return consecutive(n, CharacterSet.DECIMAL, n + " digits");
  }

  /**
   * Matches {@code n} hex digits of {@code [0-9a-fA-F]}. {@code n} must be positive.
   *
   * @since 10.6
   */
  public static Parser<String> hexDigits(int n) {
    return consecutive(n, CharacterSet.HEX, n + " hex digits");
  }

  /**
   * Returns a parser that finds the first {@code needle} string that may start from the current
   * position or after any number of characters.
   *
   * <p>Useful when you need to skip characters until a particular anchor point, something awkward
   * to express in regex.
   *
   * <p>For example, markdown supports using any number of backticks to start a code block. The
   * code block then ends with the same number of backticks. You can parse it trivially using
   * {@code first()}:
   *
   * <pre>{@code
   * import static com.google.mu.util.CharPredicate.is;
   *
   * Parser<String> codeBlock =
   *     // one or more consecutive backticks start a code block
   *     consecutive(is('`'), "backticks")
   *         .flatMap(Parser::first) // the same number of backticks conclude the code block
   *         .source();              // span the full ```code block```
   * }</pre>
   *
   * @since 9.5
   */
  public static Parser<String> first(String needle) {
    checkArgument(needle.length() > 0, "needle cannot be empty");
    return new Parser<>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        int found = input.indexOf(needle, start);
        return found >= 0
            ? new MatchResult.Success<>(found, found + needle.length(), needle)
            : context.expecting(needle, start);
      }
    };
  }

  /** Matches a literal {@code string}. */
  public static Parser<String> string(String string) {
    checkArgument(string.length() > 0, "string cannot be empty");
    return new Parser<>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        return input.startsWith(string, start)
            ? new MatchResult.Success<>(start, start + string.length(), string)
            : context.expecting(string, start);
      }

      @Override Set<String> getPrefixes() {
        return Set.of(string);
      }
    };
  }

  /**
   * Matches a literal {@code string} case insensitively.
   *
   * <p>If you need to access the input substring that matched case insensitively,
   * consider using {@code .source()}.
   *
   * @since 9.9.3
   */
  public static Parser<?> caseInsensitive(String string) {
    checkArgument(string.length() > 0, "string cannot be empty");
    return new Parser<String>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        return input.startsWithCaseInsensitive(string, start)
            ? new MatchResult.Success<>(start, start + string.length(), string)
            : context.expecting(string, start);
      }

      @Override Set<String> getPrefixes() {
        // Prune by up to 4 chars (16 combinations) to avoid prefix tree explosion.
        return caseInsensitivePrefixes(string, 4);
      }
    };
  }

  /**
   * {@code caseInsensitiveWord("or")} matches "Or" and "OR", but not "orange".
   *
   * <p>If you need to access the input substring that matched case insensitively,
   * consider using {@code .source()}.
   *
   * @since 9.9.3
   */
  public static Parser<?> caseInsensitiveWord(String word) {
    return caseInsensitive(word).notImmediatelyFollowedBy(CharPredicate.WORD, "[a-zA-Z0-9_]");
  }

  /**
   * Matches the characters quoted by {@code before} and {@code after}, and returns the string in
   * between. For example: {@code quotedBy('<', '>').parse("<foo>")} will return {@code "foo"}.
   *
   * <p>If you need to support backslash escapes, use {@link #quotedByWithEscapes} instead.
   *
   * @since 9.5
   */
  public static Parser<String> quotedBy(char before, char after) {
    checkArgument(!Character.isSurrogate(before), "before cannot be a surrogate character");
    checkArgument(!Character.isSurrogate(after), "after cannot be a surrogate character");
    return quotedBy(Character.toString(before), Character.toString(after));
  }

  /**
   * Matches the characters quoted by {@code before} and {@code after}, and returns the string in
   * between. For example: {@code quotedBy("<!--", "-->").parse("<!--comment-->")} will return
   * {@code "comment"}.
   *
   * <p>If you need to support backslash escapes, use {@link #quotedByWithEscapes} instead.
   *
   * @since 9.5
   */
  public static Parser<String> quotedBy(String before, String after) {
    return quotedBy(string(before), first(after));
  }

  /**
   * Matches the characters quoted by {@code before} and {@code after}, and returns the string in
   * between.
   */
  private static Parser<String> quotedBy(Parser<?> before, Parser<?> after) {
    requireNonNull(after);
    return before.then(
        new Parser<>() {
          @Override MatchResult<String> skipAndMatch(
              Parser<?> skip, CharInput input, int start, ErrorContext context) {
            return switch (after.skipAndMatch(skip, input, start, context)) {
              case MatchResult.Success<?> success ->
                  new MatchResult.Success<>(
                      start, success.tail(), input.snippet(start, success.head() - start));
              case MatchResult.Failure<?> failure -> failure.safeCast();
            };
          }
        });
  }

  /**
   * String literal quoted by {@code before} and {@code after} with backslash escapes.
   *
   * <p>When a backslash is encountered, the {@code escaped} parser is used to parse the escaped
   * character(s).
   *
   * <p>For example:
   *
   * <pre>{@code
   * quotedByWithEscapes('"', '"', chars(1)).parse("foo\\\\bar");
   * }</pre>
   *
   * will treat the escaped character as literal and return {@code "foo\\bar"}.
   *
   * <p>You can also support unicode escaping:
   *
   * <pre>{@code
   * Parser<String> unicodeEscaped = string("u")
   *     .then(bmpCodeUnit())
   *     .map(Character::toString);
   * quotedByWithEscapes('"', '"', unicodeEscaped.or(chars(1))).parse("foo\\uD83D");
   * }</pre>
   *
   * @since 9.5
   */
  public static Parser<String> quotedByWithEscapes(
      char before, char after, Production<? extends CharSequence> escaped) {
    checkArgument(!Character.isSurrogate(before), "before cannot be a surrogate character");
    checkArgument(!Character.isSurrogate(after), "after cannot be a surrogate character");
    return quotedByWithEscapes(Character.toString(before), after, escaped);
  }

  /**
   * String literal quoted by {@code before} and {@code after} with backslash escapes.
   *
   * <p>When a backslash is encountered, the {@code escaped} parser is used to parse the escaped
   * character(s).
   *
   * <p>For example:
   *
   * <pre>{@code
   * quotedByWithEscapes("(http://", ')', chars(1)).parse("(http://foo\\\\bar.com)");
   * }</pre>
   *
   * will treat the escaped character as literal and return {@code "foo\\bar.com"}.
   *
   * @since 9.9.3
   */
  public static Parser<String> quotedByWithEscapes(
      String before, char after, Production<? extends CharSequence> escaped) {
    var escape = string("\\").then(allowZeroWidth(escaped));
    checkArgument(after != '\\', "quoteChar cannot be '\\'");
    checkArgument(!Character.isISOControl(after), "quoteChar cannot be a control character");
    checkArgument(!Character.isSurrogate(after), "quoteChar cannot be a surrogate character");
    CharPredicate literalChars = isNot(after).and(isNot('\\')).precomputeForAscii();
    return anyOf(consecutive(literalChars, "quoted chars"), escape)
        .zeroOrMore(joining())
        .immediatelyBetween(before, Character.toString(after));
  }

  /**
   * Matches the characters nested by {@code before} and {@code after}, supporting balanced
   * nesting, and returns the nested string in between.
   *
   * <p>Unlike {@link #quotedBy(String, String)}, which stops at the first occurrence of the {@code
   * after} delimiter, {@code nestedBy()} tracks the nesting depth of the {@code before} and {@code
   * after} delimiters and only succeeds when the nesting is balanced.
   *
   * <p>For example, {@code nestedBy("(", ")").parse("(a(b)c)")} returns {@code "a(b)c"}.
   * In contrast, {@code quotedBy("(", ")")} will match {@code "(a(b)"}.
   *
   * <p>Does not support escaping. If the delimiters can be escaped by backslashes, use {@link
   * #nestedByWithEscapes} instead.
   *
   * @since 10.3
   */
  public static Parser<String> nestedBy(String before, String after) {
    checkArgument(!after.isEmpty(), "after cannot be empty");
    checkArgument(!before.equals(after), "before and after must be different for nesting");
    return string(before).then(
        new Parser<String>() {
          @Override MatchResult<String> skipAndMatch(
              Parser<?> skip, CharInput input, final int start, ErrorContext context) {
            for (int index = start, depth = 1; ; ) {
              if (input.isEof(index)) {
                return context.expecting(after, index); // Unclosed block
              }
              if (input.startsWith(after, index)) {
                if (--depth == 0) {
                  return new MatchResult.Success<>(
                      start,  index + after.length(), input.snippet(start, index - start));
                }
                index += after.length();
              } else if (input.startsWith(before, index)) {
                depth++;
                index += before.length();
              } else {
                index++;
              }
            }
          }
        });
  }

  /**
   * Matches the characters nested by {@code before} and {@code after} with backslash escapes,
   * supporting balanced nesting, and returns the unescaped nested string in between.
   *
   * <p>When a backslash is encountered, the {@code escaped} parser is used to parse the escaped
   * character(s). If the {@code escaped} parser fails, the entire parsing fails.
   *
   * <p>For example, to parse RFC 5322 email comments which allow nested comments and escaped
   * characters (where any character following a backslash is considered literal):
   *
   * <pre>{@code
   * Parser<String> comment = nestedByWithEscapes('(', ')', chars(1));
   * comment.parse("(comment with (nested) parens)");
   * }</pre>
   *
   * will return {@code "comment with (nested) parens"}.
   *
   * @since 10.3
   */
  public static Parser<String> nestedByWithEscapes(
      char before, char after, Production<? extends CharSequence> escaped) {
    Parser<? extends CharSequence> followingEscape = allowZeroWidth(escaped);
    checkArgument(before != '\\', "before cannot be '\\'");
    checkArgument(after != '\\', "after cannot be '\\'");
    checkArgument(before != after, "before and after must be different for nesting");
    checkArgument(!Character.isSurrogate(before), "before cannot be a surrogate character");
    checkArgument(!Character.isSurrogate(after), "after cannot be a surrogate character");
    String suffix = Character.toString(after);
    return one(before).then(
        new Parser<String>() {
          @Override MatchResult<String> skipAndMatch(
              Parser<?> skip, CharInput input, final int start, ErrorContext context) {
            StringBuilder builder = new StringBuilder();
            for (int index = start, depth = 1; ; ) {
              if (input.isEof(index)) {
                return context.expecting(suffix, index); // Unclosed block
              }
              char c = input.charAt(index++);
              if (c == after) {
                if (--depth == 0) {
                  return new MatchResult.Success<>(start, index, builder.toString());
                }
              } else if (c == before) {
                depth++;
              } else if (c == '\\') {
                switch (followingEscape.skipAndMatch(null, input, index, context)) {
                  case MatchResult.Success(int head, int tail, CharSequence value) -> {
                    builder.append(value);
                    index = tail;
                    continue;
                  }
                  case MatchResult.Failure<?> failure -> {
                    return failure.safeCast();
                  }
                }
              }
              builder.append(c);
            }
          }
        });
  }

  /**
   * Parses a 4-digit hex BMP code unit. The following example parses a surrogate pair of two UTF-16
   * code units and will return the emoji {@code 😀}:
   *
   * <pre>{@code
   * bmpCodeUnit()
   *     .map(Character::toString)
   *     .zeroOrMore(Collectors.joining())
   *     .parse("D83DDE00");
   * }</pre>
   *
   * <p>Note that starting from v9.6, it's recommended to use {@link Joiner} ({@code Joiner.on(delimiter)})
   * in place of JDK {@code Collectors.joining(delimiter)} because {@code Joiner} optimizes for single-string
   * input, which is a common case in the context of parsing.
   *
   * <p>You can also compose it with {@link #quotedByWithEscapes}:
   *
   * <pre>{@code
   * quotedByWithEscapes('"', '"', string("u").then(bmpCodeUnit()).map(Character::toString));
   * }</pre>
   *
   * @since 9.5
   */
  public static Parser<Integer> bmpCodeUnit() {
    return Constants.BMP_CODE_UNIT;
  }

  /**
   * Sequentially matches {@code left} then {@code right}, and then combines the results using the
   * {@code combiner} function.
   */
  public static <A, B, R> Parser<R> sequence(
      Parser<A> left, Parser<B> right, BiFunction<? super A, ? super B, ? extends R> combiner) {
    requireNonNull(right);
    requireNonNull(combiner);
    return left.new SamePrefix<>() {
      @Override  MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, A v1) ->
              switch (right.skipAndMatch(skip, input, tail, context)) {
                case MatchResult.Success(int head2, int tail2, B v2) ->
                    new MatchResult.Success<>(head, tail2, combiner.apply(v1, v2));
                case MatchResult.Failure<?> failure -> failure.safeCast();
              };
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }

      @Override Parser<?> ignoreReturn() {
        return combiner instanceof ElidableBiFunction ? sequence(left, right) : this;
      }
    };
  }

  /**
   * Sequentially matches {@code left} then {@code right}, and then combines the results using the
   * {@code combiner} function.
   *
   * @since 10.0
   */
  public static <A, B, R> Parser<R> sequence(
      Parser<A> left, Production<B> right, BiFunction<? super A, ? super B, ? extends R> combiner) {
    return sequence(left, allowZeroWidth(right), combiner);
  }

  /**
   * Sequentially matches {@code left} then {@code right}, with both allowed to be optional, and
   * then combines the results using the {@code combiner} function. If either is empty, the
   * corresponding default value is passed to the {@code combiner} function.
   */
  public static <A, B, R> Parser<R>.OrEmpty sequence(
      Parser<A>.OrEmpty left, Parser<B>.OrEmpty right,
      BiFunction<? super A, ? super B, ? extends R> combiner) {
    return anyOf(
        sequence(left.notEmpty(), right, combiner),
        right.notEmpty().map(v2 -> combiner.apply(left.computeDefaultValue(), v2)))
    .new OrEmpty(() -> combiner.apply(left.computeDefaultValue(), right.computeDefaultValue()));
  }

  /**
   * Sequentially matches {@code left} (which is allowed to be optional), then {@code right}, and
   * then combines the results using the {@code combiner} function. If {@code left} is empty, the
   * default value is passed to the {@code combiner} function.
   *
   * @since 10.0
   */
  public static <A, B, R> Parser<R> sequence(
      Parser<A>.OrEmpty left, Parser<B> right,
      BiFunction<? super A, ? super B, ? extends R> combiner) {
    return anyOf(
        sequence(left.notEmpty(), right, combiner),
        right.map(v2 -> combiner.apply(left.computeDefaultValue(), v2)));
  }

  /**
   * Sequentially matches {@code a}, {@code b} and {@code c}, and then combines the results using the
   * {@code combiner} function.
   *
   * @since 9.5
   */
  public static <A, B, C, R> Parser<R> sequence(
      Parser<A> a, Production<B> b, Production<C> c,
      TriFunction<? super A, ? super B, ? super C, ? extends R> combiner) {
    requireNonNull(combiner);
    return sequence(
        a, sequence(allowZeroWidth(b), c, AbstractMap.SimpleImmutableEntry<B, C>::new),
        (v1, bc) -> combiner.apply(v1, bc.getKey(), bc.getValue()));
  }

  /**
   * Sequentially matches {@code a}, {@code b}, {@code c} and {@code d},
   * and then combines the results using the {@code combiner} function.
   *
   * @since 9.5
   */
  public static <A, B, C, D, R> Parser<R> sequence(
      Parser<A> a, Production<B> b, Production<C> c, Production<D> d,
      Function4<? super A, ? super B, ? super C, ? super D, ? extends R> combiner) {
    requireNonNull(combiner);
    return sequence(
        sequence(a, b, AbstractMap.SimpleImmutableEntry<A, B>::new),
        sequence(allowZeroWidth(c), d, AbstractMap.SimpleImmutableEntry<C, D>::new),
        (ab, cd) -> combiner.apply(ab.getKey(), ab.getValue(), cd.getKey(), cd.getValue()));
  }

  /**
   * Sequentially matches {@code first} followed by {@code more} (always-consuming {@link Parser}'s
   * or optional {@link Parser.OrEmpty}'s), disregarding the return values, suitable when you only
   * care about matching but not extracting data.
   *
   * <p>For instance,
   *
   * <pre>{@code sequence(digits(), string("-"), digits(), string("-"), digits())}</pre>
   *
   * is a much more concise and <em>more efficient</em> equivalent of
   *
   * <pre>{@code
   * digits()
   *     .then(string("-"))
   *     .then(digits())
   *     .then(string("-"))
   *     .then(digits())
   * }</pre>
   *
   * <p>If you need to ensure no skipping between the constituent sub-parsers even when {@link
   * #parseSkipping parseSkipping()} or {@link #skipping skipping()} is used, wrap the returned
   * sequence with {@link #literally literally()}.
   *
   * <p>The returned parser's match spans all of the constituent parsers. To access the matched
   * source, use {@link #source}.
   *
   * @since 10.1
   */
  public static Parser<?> sequence(Parser<?> first, Production<?>... more) {
    return first.ignoreReturn().followedByInOrder(more);
  }

  /** Matches if any of the given {@code parsers} match. */
  @SafeVarargs public static <T> Parser<T> anyOf(Parser<? extends T>... parsers) {
    return stream(parsers).collect(or());
  }

  /**
   * Returns a parser that matches {@code s1}, {@code s2}, or any of the {@code more} strings.
   *
   * <p>Unlike {@link #anyOf(Parser[])}, the order of the strings isn't important as the parser
   * will automatically choose the longest match.
   *
   * @throws IllegalArgumentException if {@code strings} is empty or any element is empty.
   * @throws NullPointerException if {@code strings} is null or any element is null.
   * @since 10.5
   */
  public static Parser<String> anyOf(String s1, String s2, String... more) {
    return Stream.concat(Stream.of(s1, s2), stream(more))
        .distinct()
        .sorted(reverseOrder())
        .map(Parser::string)
        .collect(or());
  }

  /**
   * Returns a parser that matches any of the given enum {@code values} by their
   * {@link Enum#toString}.
   *
   * <p>For example if you want to parse all operators defined in an enum:
   *
   * <pre>{@code
   * enum Operator {
   *   PLUS("+"),
   *   MINUS("-"),
   *   INCREMENT("++"),
   *   DECREMENT("--");
   *   ...
   * }
   * }</pre>
   *
   * You can parse all of the operators with a one-liner:
   *
   * <pre>{@code
   * Parser<Operator> operatorParser = anyOf(Operator.values());
   * }</pre>
   *
   * <p>Unlike {@link #anyOf(Parser[])}, the order of the enum values isn't important as the parser
   * will automatically choose the longest match.
   *
   * @throws IllegalArgumentException if {@code values} is empty or {@link Object#toString} returns
   *     empty string, or are not unique.
   * @throws NullPointerException if {@code values} is null or any element is null.
   * @since 9.9.9
   */
  @SafeVarargs
  public static <T extends Enum<?>> Parser<T> anyOf(T... values) {
    checkArgument(values.length > 0, "values cannot be empty");
    Map<String, T> longerFirst = biStream(stream(values))
            .mapKeys(Object::toString)
            // reverse alphabetical order, so that we parse "++" before "+"
            .collect(toMap(() -> new TreeMap<String, T>(reverseOrder())));
    return BiStream.from(longerFirst)
        .mapToObj((s, value) -> string(s).thenReturn(value))
        .collect(or());
  }

  /**
   * Returns a collector that results in a parser that matches if any of the input {@code parsers}
   * match.
   */
  public static <T> Collector<Parser<? extends T>, ?, Parser<T>> or() {
    return collectingAndThen(
        toUnmodifiableList(),
        parsers -> parsers.size() == 1 ? covariant(parsers.get(0)) : new OrParser<>(parsers));
  }

  /** Matches if {@code this} or {@code that} matches. */
  public final Parser<T> or(Parser<? extends T> that) {
    return new OrParser<T>(asList(this, that));
  }

  /**
   * Matches if {@code this} or {@code that} matches. If both failed to match, use the default
   * result specified in {@code that}.
   *
   * @since 9.4
   */
  public final OrEmpty or(Parser<? extends T>.OrEmpty that) {
    return or(that.notEmpty()).new OrEmpty(that.defaultSupplier);
  }

  /** Returns a parser that applies this parser at least once, greedily. */
  public final Parser<List<T>> atLeastOnce() {
    return atLeastOnce(toUnmodifiableList());
  }

  /**
   * Returns a parser that applies this parser at least once, greedily, and reduces the results
   * using the {@code reducer} function.
   *
   * @since 9.4
   */
  public final Parser<T> atLeastOnce(BinaryOperator<T> reducer) {
    return atLeastOnce(reducing(requireNonNull(reducer))).elidableMap(Optional::get);
  }

  /**
   * Returns a parser that applies this parser at least once, greedily, and collects the return
   * values using {@code collector}.
   */
  public final <A, R> Parser<R> atLeastOnce(Collector<? super T, A, ? extends R> collector) {
    return this.andZeroOrMore(this, collector);
  }

  /**
   * Returns a parser that matches {@code this} pattern at least once, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf("a", "b", "c").atLeastOnceDelimitedBy("|")
   * }</pre>
   */
  public final Parser<List<T>> atLeastOnceDelimitedBy(String delimiter) {
    return atLeastOnceDelimitedBy(delimiter, toUnmodifiableList());
  }

  /**
   * Returns a parser that matches {@code this} pattern at least once, delimited by the given
   * delimiter, using the given {@code reducer} function to reduce the results.
   *
   * @since 9.4
   */
  public final Parser<T> atLeastOnceDelimitedBy(String delimiter, BinaryOperator<T> reducer) {
    return atLeastOnceDelimitedBy(delimiter, reducing(requireNonNull(reducer)))
        .elidableMap(Optional::get);
  }

  /**
   * Returns a parser that matches {@code this} pattern at least once, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf("a", "b", "c")
   *     .atLeastOnceDelimitedBy("|", RegexPattern.asAlternation())
   * }</pre>
   */
  public final <A, R> Parser<R> atLeastOnceDelimitedBy(
      String delimiter, Collector<? super T, A, ? extends R> collector) {
    return this.andZeroOrMore(this.afterDelimiter(delimiter), collector);
  }

  /**
   * Returns a parser that matches {@code this} pattern at least once, delimited by the given
   * delimiter.
   *
   * @since 9.9.4
   */
  public final <A, R> Parser<R> atLeastOnceDelimitedBy(
      Parser<?> delimiter, Collector<? super T, A, ? extends R> collector) {
    return this.andZeroOrMore(delimiter.then(this), collector);
  }

  /**
   * For example: {@code zeroOrMore(charsIn("[a-zA-Z0-9_-]"))}.
   *
   * @deprecated Use {@link #zeroOrMore(String)} instead.
   * @since 9.9.9
   */
  @Deprecated
  public static Parser<String>.OrEmpty zeroOrMore(CharacterSet characterSet) {
    return zeroOrMore(characterSet, characterSet.toString());
  }

  /**
   * Starts a fluent chain for matching consecutive characters in the {@code characterClass} zero or
   * more times. If no such character is found, empty string is the result.
   *
   * <p>For example: {@code zeroOrMore("[0-9]")} is equivalent to {@code zeroOrMore(range('0', '9'))}.
   *
   * <p>Implementation Note: regex isn't used during parsing. The character class string is translated
   * to a {@link CharPredicate#precomputeForAscii precomputed} {@code CharPredicate}, at construction time.
   *
   * @param characterClass A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}).
   *        Starting v10.6, literal backslash ({@code "\\"} in Java source code literal),
   *        '[' and ']' can all be included inside the outer pair of brackets.
   *        The '-' character as long as not at the place of a range is also treated as literal.
   *        You can also use {@code '^'} to get negative character set like:
   *        {@code one("[^a-zA-Z]")}, which is any non-alphabet character.
   *        You are strongly recommended to install Google ErrorProne and mug-errorprone in your
   *        annotation processor path so that incorrect character class syntax will be caught
   *        at compile-time.
   * @since 10.2
   */
  @SuppressWarnings("CharacterSetLiteralCheck")
  public static Parser<String>.OrEmpty zeroOrMore(String characterClass) {
    return zeroOrMore(charsIn(characterClass), "zero or more " + characterClass);
  }

  /**
   * Starts a fluent chain for matching consecutive {@code charsToMatch} zero or more times. If no
   * such character is found, empty string is the result.
   *
   * <p>For example if you need to parse a quoted literal that's allowed to be empty:
   *
   * <pre>{@code
   * zeroOrMore(c -> c != '\'', "quoted").between("'", "'")
   * }</pre>
   */
  public static Parser<String>.OrEmpty zeroOrMore(CharPredicate charsToMatch, String name) {
    return consecutive(charsToMatch, name).new OrEmpty(() -> "");
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times.
   *
   * <p>For example if you want to parse a list of statements between a pair of curly braces, you
   * can use:
   *
   * <pre>{@code
   * statement.zeroOrMore().between("{", "}")
   * }</pre>
   */
  public final Parser<List<T>>.OrEmpty zeroOrMore() {
    return atLeastOnce(toUnmodifiableList()).new OrEmpty(() -> List.of());
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times. {@code collector} is
   * used to collect the parsed results and the empty collector result will be used if this parser
   * matches zero times.
   *
   * <p>For example if you want to parse a list of statements between a pair of curly braces, you
   * can use:
   *
   * <pre>{@code
   * statement.zeroOrMore(toBlock()).between("{", "}")
   * }</pre>
   */
  public final <A, R> Parser<R>.OrEmpty zeroOrMore(Collector<? super T, A, ? extends R> collector) {
    return this.<A, R>atLeastOnce(collector).new OrEmpty(emptyValueSupplier(collector));
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times, delimited by {@code
   * delimiter}.
   *
   * <p>For example if you want to parse a list of names {@code [a,b,c]}, you can use:
   *
   * <pre>{@code
   * consecutive(ALPHA, "item")
   *     .zeroOrMoreDelimitedBy(",")
   *     .between("[", "]")
   * }</pre>
   */
  public final Parser<List<T>>.OrEmpty zeroOrMoreDelimitedBy(String delimiter) {
    return atLeastOnceDelimitedBy(delimiter, toUnmodifiableList()).new OrEmpty(() -> List.of());
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times, delimited by {@code
   * delimiter}. {@code collector} is used to collect the parsed results and the empty collector
   * result will be used if this parser matches zero times.
   * <p>For example if you want to parse a set of names {@code [a,b,c]}, you can use:
   *
   * <pre>{@code
   * consecutive(ALPHA, "item")
   *     .zeroOrMoreDelimitedBy(",", toSet())
   *     .between("[", "]")
   * }</pre>
   */
  public final <A, R> Parser<R>.OrEmpty zeroOrMoreDelimitedBy(
      String delimiter, Collector<? super T, A, ? extends R> collector) {
    return this.<A, R>atLeastOnceDelimitedBy(delimiter, collector)
        .new OrEmpty(emptyValueSupplier(collector));
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times, delimited by {@code
   * delimiter}. {@code collector} is used to collect the parsed results and the empty collector
   * result will be used if this parser matches zero times.
   *
   * @since 9.9.4
   */
  public final <A, R> Parser<R>.OrEmpty zeroOrMoreDelimitedBy(
      Parser<?> delimiter, Collector<? super T, A, ? extends R> collector) {
    return this.<A, R>atLeastOnceDelimitedBy(delimiter, collector)
        .new OrEmpty(emptyValueSupplier(collector));
  }

  /**
   * Applies {@code first} and the optional {@code second} pattern in order, for zero or more
   * times, collecting the results using the provided {@link BiCollector}.
   *
   * <p>Typically used to parse key-value pairs:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.BiCollectors.toMap;
   *
   * Parser<Map<String, Integer>> keyValues =
   *     zeroOrMoreDelimited(
   *            word(),
   *            string("=").then(digits()).map(Integer::parseInt).orElse(0),
   *            ",",
   *            toMap())
   *         .followedBy(string(",").optional()) // only if you need to allow trailing comma
   *         .between("{", "}");
   * }</pre>
   *
   * @since 9.4
   */
  public static <A, B, R> Parser<R>.OrEmpty zeroOrMoreDelimited(
      Parser<A> first,
      Production<B> second,
      String delimiter,
      BiCollector<? super A, ? super B, R> collector) {
    return sequence(first, second, Both::of)
        .zeroOrMoreDelimitedBy(delimiter, mapping(identity(), collector));
  }

  private <A, R> Parser<R> andZeroOrMore(
      Parser<? extends T> extra, Collector<? super T, A, ? extends R> collector) {
    var supplier = collector.supplier();
    var accumulator = collector.accumulator();
    var finisher = collector.finisher();
    return new SamePrefix<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) -> {
            A buffer = supplier.get();
            accumulator.accept(buffer, value);
            for (int index = tail; ; ) {
              switch (extra.skipAndMatch(skip, input, index, context)) {
                case MatchResult.Success(int head2, int tail2, T value2) -> {
                  accumulator.accept(buffer, value2);
                  index = tail2;
                }
                case MatchResult.Failure<?> failure -> {
                  return new MatchResult.Success<>(head, index, finisher.apply(buffer));
                }
              }
            }
          }
          case MatchResult.Failure<?> failure -> {
            return failure.safeCast();
          }
        }
      }

      @Override Parser<?> ignoreReturn() {
        @SuppressWarnings("unchecked") // return value is unused
        Parser<Object> elided = (Parser<Object>) left().ignoreReturn();
        return elided.andZeroOrMore(extra.ignoreReturn(), toNull());
      }
    };
  }

  /** Sequencing but the return value is elidable. */
  private <B, R> Parser<R> and(
      Production<B> right,
      ElidableBiFunction<? super T, ? super B, ? extends R> combiner) {
    return sequence(this, right, combiner);
  }

  private Parser<T> afterDelimiter(String delimiter) {
    checkArgument(delimiter.length() > 0, "delimiter cannot be empty");
    return new Parser<>() {
      @Override MatchResult<T> skipAndMatch(
           Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        return input.startsWith(delimiter, start)
            ? Parser.this.skipAndMatch(skip, input, start + delimiter.length(), context)
            : ErrorContext.MINIMAL.failAt(start, "expecting <{name}>", delimiter);
      }

      @Override Set<String> getPrefixes() {
        return Set.of(delimiter);
      }

      @Override Parser<?> ignoreReturn() {
        return Parser.this.ignoreReturn().afterDelimiter(delimiter);
      }
    };
  }

  /**
   * Returns a parser that applies the {@code operator} parser zero or more times before {@code
   * this} and applies the result unary operator functions iteratively.
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   *
   * @since 9.9.3
   */
  public final Parser<T> withPrefixes(Parser<? extends UnaryOperator<T>> operator) {
    return sequence(
        operator.zeroOrMore(), this, (ops, operand) -> applyOperators(ops.reversed(), operand));
  }

  /**
   * Returns a parser that after this parser succeeds, applies the {@code operator} parser zero or
   * more times and applies the result unary operator function iteratively.
   *
   * <p>This is useful to parse postfix operators such as in regex the quantifiers are usually
   * postfix.
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   *
   * @since 9.9.3
   */
  public final Parser<T> withPostfixes(Parser<? extends UnaryOperator<T>> operator) {
    return sequence(this, operator.zeroOrMore(), (operand, ops) -> applyOperators(ops, operand));
  }

  /**
   * Returns a parser that after this parser succeeds, applies the {@code operator} parser zero or
   * more times and applies the result unary operator function iteratively. For example:
   *
   * <pre>{@code
   * Parser<Expr> parser = word()
   *     .map(Expr::variable)
   *     .withPostfixes(string(".").then(word()), (expr, field) -> Expr.fieldAccess(expr, field)));
   * }</pre>
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   *
   * @since 9.9.3
   */
  public final <S> Parser<T> withPostfixes(
      Parser<S> operator, BiFunction<? super T, ? super S, ? extends T> postfixFunction) {
    return withPostfixes(asPostfixOperator(operator, postfixFunction));
  }

  /**
   * Returns a parser that after this parser succeeds, applies the {@code operator} parser zero or
   * more times and applies the result unary operator function iteratively. For example:
   *
   * <pre>{@code
   * Parser<AbcNote> middleNote = one("[ABCDEFG]")
   *     .map(AbcNote::middle)
   *     .withPostfixes(",", AbcNote::down);
   * }</pre>
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   *
   * @since 9.9.9
   */
  public final Parser<T> withPostfixes(String operator, UnaryOperator<T> postfixFunction) {
    return withPostfixes(string(operator).thenReturn(requireNonNull(postfixFunction)));
  }

  /**
   * Returns a parser that matches {@code this} pattern enclosed between {@code prefix} and {@code suffix},
   * both allowed to be empty.
   *
   * @since 9.5
   */
  @Override public final Parser<T> between(Parser<?>.OrEmpty prefix, Parser<?>.OrEmpty suffix) {
    return prefix.then(this).followedBy(suffix);
  }

  /** If this parser matches, returns the result of applying the given function to the match. */
  @Override public final <R> Parser<R> map(Function<? super T, ? extends R> f) {
    requireNonNull(f);
    return new SamePrefix<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              new MatchResult.Success<>(head, tail, f.apply(value));
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }

      @Override Parser<?> ignoreReturn() {
        return f instanceof ElidableFunction ? left().ignoreReturn() : this;
      }
    };
  }

  /**
   * If this parser matches, returns the result of applying the given function,
   * with the parse result of type {@code T}, the beginning index (inclusive) and the end index
   * (exclusive) as parameters passed to the function.
   *
   * <p>For example: <pre>{@code
   * Parser<NameNode> nameNode =
   *     word().mapWithIndex((name, begin, end) -> new NameNode(name, begin, end));
   * }</pre>
   *
   * @since 10.6
   */
  public final <R> Parser<R> mapWithIndex(ObjInt2Function<? super T, ? extends R> f) {
    requireNonNull(f);
    return new SamePrefix<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              new MatchResult.Success<>(head, tail, f.apply(value, head, tail));
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }
    };
  }

  private <R> Parser<R> elidableMap(ElidableFunction<? super T, ? extends R> f) {
    return map(f);
  }

  /**
   * If this parser matches, applies function {@code f} to get the next production rule to match
   * in sequence.
   *
   * <p>Starting from v10.0, the function can return either a {@link Parser} that consumes input,
   * or an {@link OrEmpty} that will succeed even if not matched.
   */
  public final <R> Parser<R> flatMap(Function<? super T, ? extends Production<? extends R>> f) {
    requireNonNull(f);
    return new SamePrefix<>() {
      @SuppressWarnings("unchecked")  // MatchResult<R> is covariant
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              (MatchResult<R>)
                  allowZeroWidth(f.apply(value)).skipAndMatch(skip, input, tail, context)
                      .startingFrom(head);
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }
    };
  }

  /** If this parser matches, returns the given result. */
  public <R> Parser<R> thenReturn(R result) {
    return ignoreReturn().elidableMap(unused -> result);
  }

  @Override public final <S> Parser<S> then(Parser<S> suffix) {
    return ignoreReturn().and(suffix, (a, b) -> b);
  }

  /**
   * If this parser matches, applies the given optional (or zero-or-more) parser on the remaining
   * input.
   *
   * @since 10.0
   */
  @Override public final <R> Parser<R> then(Parser<R>.OrEmpty suffix) {
    return ignoreReturn().and(suffix, (a, b) -> b);
  }

  /**
   * If this parser matches, applies the given {@code condition} and disqualifies the match if the
   * condition is false.
   *
   * <p>For example if you are trying to parse a non-reserved word, you can use:
   *
   * <pre>{@code
   * Set<String> reservedWords = ...;
   * Parser<String> identifier = Parser.WORD.suchThat(w -> !reservedWords.contains(w), "identifier");
   * }</pre>
   *
   * @since 9.4
   */
  public final Parser<T> suchThat(Predicate<? super T> condition, String name) {
    requireNonNull(condition);
    requireNonNull(name);
    return new SamePrefix<>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        var result = left().skipAndMatch(skip, input, start, context);
        return result instanceof MatchResult.Success<T> success && !condition.test(success.value())
            ? context.expecting(name, success.head(), success.tail())
            : result;
      }
    };
  }

  @Override public Parser<T> followedBy(Parser<?> suffix) {
    return and(suffix.ignoreReturn(), (a, b) -> a);
  }

  @Override public final <S> Parser<T> followedBy(Parser<S>.OrEmpty suffix) {
    return followedBy(suffix.unsafeZeroWidthParser);
  }

  /**
   * Specifies that the matched pattern must be either followed by {@code suffix} or EOF.
   * No other suffixes allowed.
   *
   * @since 9.4
   */
  public final Parser<T> followedByOrEof(Parser<?> suffix) {
    return followedBy(anyOf(suffix.ignoreReturn(), UNSAFE_EOF));
  }

  private Parser<T> followedByInOrder(Production<?>... suffixes) {
    Parser<?>[] followers = stream(suffixes)
        .map(Parser::allowZeroWidth)
        .map(Parser::ignoreReturn)
        .toArray(Parser<?>[]::new);
    return new SamePrefix<T>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<T> result -> {
            int index = result.tail();
            for (Parser<?> follower : followers) {
              switch (follower.skipAndMatch(skip, input, index, context)) {
                case MatchResult.Success<?> success -> {
                  index = success.tail();
                }
                case MatchResult.Failure<?> failure -> {
                  yield failure.safeCast();
                }
              }
            }
            yield new MatchResult.Success<T>(result.head(), index, result.value());
          }
          case MatchResult.Failure<T> failure -> failure;
        };
      }
    };
  }

  @Override public final Parser<T> optionallyFollowedBy(String suffix) {
    return optionallyFollowedBy(string(suffix));
  }

  @Override public final Parser<T> optionallyFollowedBy(Parser<?> suffix) {
    return followedBy(suffix.new OrEmpty(() -> null));
  }

  @Override public final Parser<T> optionallyFollowedBy(String suffix, Function<? super T, ? extends T> op) {
    return withOptionalSuffix(string(suffix).thenReturn(op::apply));
  }

  /**
   * If this parser matches, optionally matches {@code suffix} with the {@code op} BiFunction
   * to transform the current parser's result.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Parser<MarkdownLink> link = ...;
   * Parser<String> title = ...;
   * Parser<MarkdownLink> parser = link.optionallyFollowedBy(title, MarkdownLink::withTitle);
   * }</pre>
   *
   * @since 9.5
   */
  @Override public final <S> Parser<T> optionallyFollowedBy(
      Parser<S> suffix, BiFunction<? super T, ? super S, ? extends T> op) {
    requireNonNull(op);
    return withOptionalSuffix(suffix.map(s -> p -> op.apply(p, s)));
  }

  final Parser<T> withOptionalSuffix(Parser<UnaryOperator<T>> suffix) {
    return and(suffix.new OrEmpty(() -> identity()), (a, op) -> op.apply(a));
  }

  /** A form of negative lookahead such that the match is rejected if followed by {@code suffix}. */
  public final Parser<T> notFollowedBy(String suffix) {
    return notFollowedBy(string(suffix), suffix);
  }

  /** A form of negative lookahead such that the match is rejected if followed by {@code suffix}. */
  public final Parser<T> notFollowedBy(Parser<?> suffix, String name) {
    requireNonNull(name);
    Parser<?> elidedSuffix = suffix.ignoreReturn();
    return new SamePrefix<>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<T> success -> {
            yield switch (elidedSuffix.skipAndMatch(skip, input, success.tail(), ErrorContext.MINIMAL)) {
              case MatchResult.Success<?> followed ->
                context.failAt(
                    followed.head(), followed.tail(),
                    "unexpected `{name}`: {snippet}", name);
              default -> success;
            };
          }
          case MatchResult.Failure<T> failure -> failure;
        };
      }

      @Override Parser<?> ignoreReturn() {
        return left().ignoreReturn().notFollowedBy(elidedSuffix, name);
      }
    };
  }

  /**
   * A form of negative lookahead such that the match is rejected if followed by EOF.
   *
   * @since 10.2
   */
  public final Parser<T> notFollowedByEof() {
    return notFollowedBy(UNSAFE_EOF, "eof");
  }

  /**
   * A form of negative lookahead such that the match is rejected if <em>immediately</em> followed
   * by (no skippable characters as specified by {@link #parseSkipping parseSkipping()} in between)
   * a character that matches {@code predicate}. Useful for parsing keywords such as {@code
   * string("if").notImmediatelyFollowedBy(IDENTIFIER_CHAR, "identifier char")}.
   */
  public final Parser<T> notImmediatelyFollowedBy(CharPredicate predicate, String name) {
    return notFollowedBy(
        one(predicate, name).new SamePrefix<Character>() {
          @Override MatchResult<Character> skipAndMatch(
              Parser<?> ignored, CharInput input, int start, ErrorContext context) {
            return left().skipAndMatch(null, input, start, context);
          }
        },
        name);
  }

  /**
   * Starts a fluent chain for matching the current parser optionally. {@code defaultValue} will be
   * the result in case the current parser doesn't match.
   *
   * <p>For example if you want to parse an optional placeholder name enclosed by curly braces, you
   * can use:
   *
   * <pre>{@code
   * consecutive(ALPHA, "placeholder name")
   *     .orElse(EMPTY_PLACEHOLDER)
   *     .between("{", "}")
   * }</pre>
   */
  public final OrEmpty orElse(T defaultValue) {
    return new OrEmpty(() -> defaultValue);
  }

  /**
   * Starts a fluent chain for matching the current parser optionally. {@code Optional.empty()} will
   * be the result in case the current parser doesn't match.
   *
   * <p>For example if you want to parse an optional placeholder name enclosed by curly braces, you
   * can use:
   *
   * <pre>{@code
   * consecutive(ALPHA, "placeholder name")
   *     .optional()
   *     .between("{", "}")
   * }</pre>
   */
  public final Parser<Optional<T>>.OrEmpty optional() {
    return elidableMap(Optional::ofNullable).new OrEmpty(Optional::empty);
  }

  /** Returns a parser that matches {@code this} pattern and returns the matched string. */
  @Override public final Parser<String> source() {
    @SuppressWarnings("unchecked")  // original return value no longer needed
    Parser<Object> elided = (Parser<Object>) ignoreReturn();
    return elided.new SamePrefix<String>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left().skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<?>(int head, int tail, Object value) ->
              new MatchResult.Success<>(head, tail, input.snippet(head, tail - head));
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }

      @Override public <R> Parser<R> thenReturn(R result) {
        return elided.thenReturn(result);
      }

      @Override Parser<?> ignoreReturn() {
        return elided;
      }
    };
  }

  Parser<?> ignoreReturn() {
    return this;
  }

  /**
   * Returns an equivalent parser that suppresses character skipping that's otherwise applied if
   * {@link #parseSkipping parseSkipping()} or {@link #skipping skipping()}
   * are called. For example quoted string literals should not skip whitespaces.
   */
  public static <T> Parser<T> literally(Parser<T> parser) {
    requireNonNull(parser);
    return parser.new SamePrefix<T>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        return left().skipAndMatch(null, input, start, context);
      }

      @Override Parser<?> ignoreReturn() {
        return literally(left().ignoreReturn());
      }
    };
  }

  /**
   * Specifies that the optional (or zero-or-more) {@code rule} should be matched literally even if
   * {@link #parseSkipping parseSkipping()} or {@link #skipping skipping()} is called.
   */
  public static <T> Parser<T>.OrEmpty literally(Parser<T>.OrEmpty rule) {
    return literally(rule.notEmpty()).new OrEmpty(rule::computeDefaultValue);
  }

  /** Starts a fluent chain for parsing inputs while skipping patterns matched by {@code skip}. */
  public final Lexical skipping(Parser<?> skip) {
    return new Lexical(skip.ignoreReturn().atLeastOnce(toNull()));
  }

  /**
   * Starts a fluent chain for parsing inputs while skipping {@code charsToSkip}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * jsonRecord.skipping(whitespace()).parseToStream(input);
   * }</pre>
   */
  public final Lexical skipping(CharPredicate charsToSkip) {
    return new Lexical(consecutive(charsToSkip, "skipped"));
  }

  /**
   * Parses {@code input} while skipping patterns matched by {@code skip} around atomic matches.
   *
   * <p>Equivalent to {@code skipping(skip).parse(input)}.
   */
  @Override public final T parseSkipping(Parser<?> skip, String input) {
    return skipping(skip).parse(input);
  }

  /**
   * Parses {@code input} while {@code charsToSkip} around atomic matches.
   *
   * <p>Equivalent to {@code skipping(charsToSkip).parse(input)}.
   */
  @Override public final T parseSkipping(CharPredicate charsToSkip, String input) {
    return skipping(charsToSkip).parse(input);
  }

  /**
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  @Override public final T parse(String input) {
    return parse(CharInput.from(input), 0);
  }

  /**
   * Parses the input string starting from {@code fromIndex} and returns the result. Upon successful
   * return, the {@code input} starting from {@code fromIndex} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  public final T parse(String input, int fromIndex) {
    checkPositionIndex(fromIndex, input.length(), "fromIndex");
    return parse(CharInput.from(input), fromIndex);
  }

  private T parse(CharInput input, int fromIndex) {
    ErrorTracker errorTracker = new ErrorTracker();
    MatchResult<T> result = match(input, fromIndex, errorTracker);
    switch (result) {
      case MatchResult.Success(int head, int tail, T value) -> {
        if (!input.isEof(tail)) {
          throw errorTracker.report(errorTracker.expecting("EOF", tail), input);
        }
        return value;
      }
      case MatchResult.Failure<?> failure -> {
        throw errorTracker.report(failure, input);
      }
    }
  }

  /**
   * Returns true if this parser represents a non-empty prefix of the given {@code input}.
   *
   * @since 9.9.5
   */
  public final boolean isPrefixOf(String input) {
    CharInput charInput = CharInput.from(input);
    return ignoreReturn().match(charInput, 0, ErrorContext.MINIMAL) instanceof MatchResult.Success;
  }

  /**
   * Returns true if this parser matches the entirety of the {@code input}. It's similar to the
   * regex {@code Matcher.matches(String)} method.
   *
   * <p>If you don't need to match the entire input string, which is similar to the regex {@code
   * Matcher.lookingAt(String)} method, you can use {@link #probe(String)
   * probe(input).findFirst().isPresent()} to achieve the same effect.
   *
   * @since 9.9.1
   */
  @Override public final boolean matches(String input) {
    return matches(CharInput.from(input), 0);
  }

  private boolean matches(CharInput input, int fromIndex) {
    return ignoreReturn().match(input, fromIndex, ErrorContext.MINIMAL) instanceof MatchResult.Success<?> success
        && input.isEof(success.tail());
  }

  /**
   * Parses the entire input string lazily by applying this parser repeatedly until the end of
   * input. Results are returned in a lazy stream.
   */
  public final Stream<T> parseToStream(String input) {
    return parseToStream(input, 0);
  }

  /**
   * Parses {@code input} starting from {@code fromIndex} to a lazy stream while skipping the
   * skippable patterns around lexical tokens.
   */
  public final Stream<T> parseToStream(String input, int fromIndex) {
    checkPositionIndex(fromIndex, input.length(), "fromIndex");
    return parseToStream(CharInput.from(input), fromIndex);
  }

  /**
   * Parses the input reader lazily by applying this parser repeatedly until the end of
   * input. Results are returned in a lazy stream.
   *
   * <p>{@link UncheckedIOException} will be thrown if the underlying reader throws.
   *
   * <p>Characters are internally buffered, so you don't need to pass in {@code BufferedReader}.
   */
  public final Stream<T> parseToStream(Reader input) {
    return parseToStream(CharInput.from(input), 0);
  }

  final Stream<T> parseToStream(CharInput input, int fromIndex) {
    class Cursor {
      private int index = fromIndex;

      MatchResult.Success<T> nextOrNull() {
        if (input.isEof(index)) {
          return null;
        }
        ErrorTracker errorTracker = new ErrorTracker();
        return switch (match(input, index, errorTracker)) {
          case MatchResult.Success<T> success -> {
            index = success.tail();
            input.markCheckpoint(index);
            yield success;
          }
          case MatchResult.Failure<?> failure -> {
            throw errorTracker.report(failure, input);
          }
        };
      }
    }
    return whileNotNull(new Cursor()::nextOrNull).map(MatchResult.Success::value);
  }

  /**
   * Lazily and iteratively matches {@code input}, until the input is exhausted or matching failed.
   *
   * <p>Note that unlike {@link #parseToStream(String) parseToStream()},
   * a matching failure terminates the stream without throwing exception.
   *
   * <p>This allows quick probing without fully parsing it.
   */
  public final Stream<T> probe(String input) {
    return probe(input, 0);
  }

  /**
   * Lazily and iteratively matches {@code input} starting from {@code fromIndex}, skipping the
   * skippable patterns, until the input is exhausted or matching failed. Note that unlike {@link
   * #parseToStream(String, int) parseToStream()}, a matching failure terminates the stream
   * without throwing exception.
   *
   * <p>This allows quick probing without fully parsing it.
   */
  public final Stream<T> probe(String input, int fromIndex) {
    checkPositionIndex(fromIndex, input.length(), "fromIndex");
    return probe(CharInput.from(input), fromIndex);
  }

  /**
   * Lazily and iteratively matches {@code input} reader, until the input is exhausted or matching failed.
   *
   * <p>Note that unlike {@link #parseToStream(String) parseToStream()},
   * a matching failure terminates the stream without throwing exception.
   *
   * <p>This allows quick probing without fully parsing it.
   *
   * <p>{@link UncheckedIOException} will be thrown if the underlying reader throws.
   *
   * <p>Characters are internally buffered, so you don't need to pass in {@code BufferedReader}.
   */
  public final Stream<T> probe(Reader input) {
    return probe(CharInput.from(input), 0);
  }

  final Stream<T> probe(CharInput input, int fromIndex) {
    class Cursor {
      private int index = fromIndex;

      MatchResult.Success<T> nextOrNull() {
        return switch (match(input, index, ErrorContext.MINIMAL)) {
          case MatchResult.Success<T> success -> {
            index = success.tail();
            input.markCheckpoint(index);
            yield success;
          }
          case MatchResult.Failure<?> failure -> null;
        };
      }
    }
    return whileNotNull(new Cursor()::nextOrNull).map(MatchResult.Success::value);
  }

  /**
   * Facilitates a fluent chain for matching the current parser optionally. This is needed because
   * we require all parsers to match at least one character. So optionality is only legal when
   * combined together with a non-empty prefix, suffix or both, which will be specified by methods
   * of this class.
   *
   * <p>Besides {@link #between(String, String) between()} and {@link #followedBy(String) followedBy()},
   * the {@link Parser#sequence(Parser, Production, BiFunction) sequence()} and {@link
   * Parser#followedBy(Parser.OrEmpty)} methods can be used to specify that a {@code Parser.OrEmpty}
   * production rule follows a regular consuming {@code Parser}.
   *
   * <p>The following is a simplified example of parsing a CSV line: a comma-separated list of
   * fields with an optional trailing newline. The field values can be empty; empty line results in
   * empty list {@code []}, not {@code [""]}:
   *
   * <pre>{@code
   * Parser<String> field = consecutive(noneOf(",\n"));
   * Parser<?> newline = string("\n");
   * Parser<List<String>> csvRow =
   *     anyOf(
   *         newline.thenReturn(List.of()),          // empty line -> []
   *         field
   *             .orElse("")                         // empty field is ok
   *             .delimitedBy(",")                   // comma-separated
   *             .notEmpty()                         // non-empty line
   *             .followedByOrEof(newline));         // trailing newline optional on last line
   * }</pre>
   *
   * <p>In addition, the {@link #parse} convenience method is provided to parse potentially-empty
   * input in this one stop shop without having to remember to check for emptiness, because this
   * class already knows the default value to use when the input is empty.
   */
  public final class OrEmpty implements Production<T> {
    private final Supplier<? extends T> defaultSupplier;

    /**
     * A crippled zero-width parser, not safe to be used in a loop and must be carefully
     * composed with a parser that does consume!
     */
    private final Parser<T> unsafeZeroWidthParser =
        new Parser<T>() {
          @Override MatchResult<T> skipAndMatch(
              Parser<?> skip, CharInput input, int start, ErrorContext context) {
            return switch (notEmpty().skipAndMatch(skip, input, start, context)) {
              case MatchResult.Success<T> success -> success;
              default -> new MatchResult.Success<>(start, start, computeDefaultValue());
            };
          }

          @Override Parser<?> ignoreReturn() {
            return OrEmpty.this.ignoreReturn().unsafeZeroWidthParser;
          }
        };

    private OrEmpty(Supplier<? extends T> defaultSupplier) {
      this.defaultSupplier = defaultSupplier;
    }

    /**
     * Applies {@code f} to either the parse result, or the default value if matching fails.
     *
     * @since 10.6
     */
    @Override public final <R> Parser<R>.OrEmpty map(Function<? super T, ? extends R> f) {
      var defaultValue = defaultSupplier;
      return notEmpty().<R>map(f).new OrEmpty(() -> f.apply(defaultValue.get()));
    }

    /**
     * The current parser enclosed between {@code prefix} and {@code suffix}, both allowed to be empty.
     *
     * @since 9.5
     */
    @Override public final OrEmpty between(Parser<?>.OrEmpty prefix, Parser<?>.OrEmpty suffix) {
      return prefix.then(this).followedBy(suffix);
    }

    /**
     * The current optional parser repeated and delimited by {@code delimiter}. Since this is an
     * optional parser, at least one value is guaranteed to be collected by the provided {@code
     * collector}, even if match failed. That is, on match failure, the default value (e.g. from
     * {@code orElse()}) will be used.
     *
     * <p>Note that it's different from {@link Parser#zeroOrMoreDelimitedBy}, which may produce
     * empty list, but each element is guaranteed to be non-empty.
     */
    public <A, R> Parser<R>.OrEmpty delimitedBy(
        String delimiter, Collector<? super T, A, ? extends R> collector) {
      var supplier = collector.supplier();
      var accumulator = collector.accumulator();
      var finisher = collector.finisher();
      return sequence(
          this, string(delimiter).then(this).zeroOrMore(toList()),
          (head, tail) -> {
            A buffer = supplier.get();
            accumulator.accept(buffer, head);
            tail.forEach(value -> accumulator.accept(buffer, value));
            R result = finisher.apply(buffer);
            return result;
          });
    }

    /**
     * The current optional parser repeated and delimited by {@code delimiter}. Since this is an
     * optional parser, at least one element is guaranteed to be returned, even if match failed. For
     * example, {@code consecutive(WORD).orElse("").delimitedBy(",")} will {@link #parse parse}
     * input {@code ",a,"} as {@code List.of("", "a", "")}; and parse empty input {@code ""} as
     * {@code List.of("")}.
     *
     * <p>Note that it's different from {@link Parser#zeroOrMoreDelimitedBy}, which may produce
     * empty list, but each element is guaranteed to be non-empty.
     */
    public Parser<List<T>>.OrEmpty delimitedBy(String delimiter) {
      return delimitedBy(delimiter, toUnmodifiableList());
    }

    @Override public <S> Parser<S> then(Parser<S> suffix) {
      return this.ignoreReturn().and(suffix, (a, b) -> b);
    }

    /** After matching the current optional (or zero-or-more) parser, proceed to match {@code suffix}.  */
    @Override public <S> Parser<S>.OrEmpty then(Parser<S>.OrEmpty suffix) {
      return this.ignoreReturn().and(suffix, (a, b) -> b);
    }

    @Override public Parser<T> followedBy(Parser<?> suffix) {
      return this.and(suffix.ignoreReturn(), (a, b) -> a);
    }

    /** The current optional (or zero-or-more) parser may optionally be followed by {@code suffix}.  */
    @SuppressWarnings("unchecked")  // to make Eclipse compiler happy
    @Override public <S> OrEmpty followedBy(Parser<S>.OrEmpty suffix) {
      return this.and((Parser<Object>.OrEmpty) suffix.ignoreReturn(), (a, b) -> a);
    }

    /**
     * The current optional (or zero-or-more) parser may optionally be followed by {@code suffix}.
     *
     * @since 9.5
     */
    @Override public OrEmpty optionallyFollowedBy(String suffix) {
      return optionallyFollowedBy(string(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser may optionally be followed by {@code suffix}.
     *
     * @since 10.5
     */
    @Override public OrEmpty optionallyFollowedBy(Parser<?> suffix) {
      return followedBy(suffix.new OrEmpty(() -> null));
    }
    /**
     * If this parser matches, optionally applies the {@code op} function if the pattern is followed
     * by {@code suffix}.
     *
     * @since 10.0
     */
    @Override public final OrEmpty optionallyFollowedBy(
        String suffix, Function<? super T, ? extends T> op) {
      return withOptionalSuffix(string(suffix).thenReturn(op::apply));
    }

    /**
     * If this parser matches, optionally matches {@code suffix} with the {@code op} BiFunction
     * to transform the current parser's result.
     *
     * @since 10.0
     */
    @Override public final <S> OrEmpty optionallyFollowedBy(
        Parser<S> suffix, BiFunction<? super T, ? super S, ? extends T> op) {
      requireNonNull(op);
      return withOptionalSuffix(suffix.map(s -> p -> op.apply(p, s)));
    }

    private OrEmpty withOptionalSuffix(Parser<UnaryOperator<T>> suffix) {
      return sequence(this, suffix.new OrEmpty(() -> identity()), (operand, op) -> op.apply(operand));
    }

    /**
     * Returns an equivalent parser that matches {@code this} pattern and returns the matched string,
     * or empty string if mismatches.
     *
     * @since 10.6
     */
    @Override public final Parser<String>.OrEmpty source() {
      return notEmpty().source().new OrEmpty(() -> "");
    }

    /**
     * Returns the otherwise equivalent {@code Parser} that will fail instead of returning the
     * default value if empty.
     *
     * <p>{@code parser.optional().notEmpty()} is equivalent to {@code parser}.
     *
     * <p>Useful when multiple optional parsers are chained together with any of them successfully
     * consuming some input.
     */
    public Parser<T> notEmpty() {
      return Parser.this;
    }

    /**
     * Parses the entire input string and returns the result; if input is empty, returns the default
     * empty value.
     */
    @Override public T parse(String input) {
      return unsafeZeroWidthParser.parse(input);
    }

    /**
     * Parses the entire input string, ignoring {@code charsToSkip}, and returns the result;
     * if there's nothing to parse except skippable content, returns the default empty value.
     */
    @Override public T parseSkipping(CharPredicate charsToSkip, String input) {
      return parseSkipping(consecutive(charsToSkip, "skipped"), input);
    }

    /**
     * Parses the entire input string, ignoring patterns matched by {@code skip}, and returns the
     * result; if there's nothing to parse except skippable content, returns the default empty value.
     */
    @Override public T parseSkipping(Parser<?> skip, String input) {
      return unsafeZeroWidthParser.parseSkipping(skip, input);
    }

    /**
     * Returns true if this parser matches the entirety of the {@code input}, or if the input is
     * empty. It's similar to the regex {@code Matcher.matches(String)} method.
     *
     * @since 9.9.1
     */
    @Override public boolean matches(String input) {
      return unsafeZeroWidthParser.matches(input);
    }

    T computeDefaultValue() {
      return defaultSupplier.get();
    }

    private Parser<?>.OrEmpty ignoreReturn() {
      return notEmpty().ignoreReturn().new OrEmpty(() -> null);
    }

    private <B, R> Parser<R>.OrEmpty and(
        Parser<B>.OrEmpty right, ElidableBiFunction<? super T, ? super B, R> combiner) {
      return sequence(this, right, combiner);
    }

    private <B, R> Parser<R> and(
        Parser<B> right, ElidableBiFunction<? super T, ? super B, R> combiner) {
      return sequence(this, right, combiner);
    }
  }

  /**
   * Fluent API for parsing while skipping patterns around lexical tokens.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Parser<JsonRecord> jsonRecord = ...;
   * jsonRecord.zeroOrMoreDelimitedBy(",")
   *     .between("[", "]")
   *     .skipping(whitespace())
   *     .parseToStream(jsonInput)
   *     ...;
   * }</pre>
   */
  public final class Lexical {
    private final Parser<?> toSkip;

    private Lexical(Parser<?> toSkip) {
      this.toSkip = toSkip.ignoreReturn();
    }

    /** Parses {@code input} while skipping the skippable patterns around lexical tokens. */
    public T parse(String input) {
      return forTokens().parse(input);
    }

    /**
     * Parses {@code input} starting from {@code fromIndex} while skipping patterns around lexical
     * tokens.
     */
    public T parse(String input, int fromIndex) {
      return forTokens().parse(input, fromIndex);
    }

    /**
     * Returns true if this parser matches the entirety of the {@code input}. It's similar to the
     * regex {@code Matcher.matches(String)} method.
     *
     * <p>If you don't need to match the entire input string, which is similar to the regex {@code
     * Matcher.lookingAt(String)} method, you can use {@link #probe(String)
     * parser.skipping(...).probe(input).findFirst().isPresent()} to achieve the same effect.
     *
     * @since 9.9.1
     */
    public boolean matches(String input) {
      return forTokens().matches(input);
    }

    /**
     * Parses {@code input} to a lazy stream while skipping the skippable patterns around lexical tokens.
     */
    public Stream<T> parseToStream(String input) {
      return parseToStream(input, 0);
    }

    /**
     * Parses {@code input} starting from {@code fromIndex} to a lazy stream while skipping the
     * skippable patterns around lexical tokens.
     */
    public Stream<T> parseToStream(String input, int fromIndex) {
      checkPositionIndex(fromIndex, input.length(), "fromIndex");
      return parseToStream(CharInput.from(input), fromIndex);
    }

    /**
     * Parses {@code input} reader to a lazy stream while skipping the skippable patterns around lexical tokens.
     *
     * <p>{@link UncheckedIOException} will be thrown if the underlying reader throws.
     *
     * <p>Characters are internally buffered, so you don't need to pass in {@code BufferedReader}.
     */
    public Stream<T> parseToStream(Reader input) {
      return parseToStream(CharInput.from(input), 0);
    }

    Stream<T> parseToStream(CharInput input, int fromIndex) {
      // forTokens().parseToStream() would only skip the trailing upon success.
      // If everything is skippable, it will fail to match.
      // We use flatMap() to keep the buffer loading lazy upon the returned stream being consumed.
      return Stream.of(ErrorContext.MINIMAL)
          .flatMap(
              context ->
                  toSkip.match(input, fromIndex, context) instanceof MatchResult.Success<?> success
                          && input.isEof(success.tail())
                      ? Stream.empty()
                      : forTokens().parseToStream(input, fromIndex));
    }

    /**
     * Lazily and iteratively matches {@code input}, skipping the skippable patterns, until the
     * input is exhausted or matching failed.
     *
     * <p>Note that unlike {@link #parseToStream(String) parseToStream()}, a matching failure
     * terminates the stream out throwing exception.
     *
     * <p>This allows quick probing without fully parsing it.
     */
    public Stream<T> probe(String input) {
      return forTokens().probe(input);
    }

    /**
     * Lazily and iteratively matches {@code input} starting from {@code fromIndex}, skipping the
     * skippable patterns, until the input is exhausted or matching failed. Note that unlike {@link
     * #parseToStream(String, int) parseToStream()}, a matching failure terminates the stream
     * without throwing exception.
     *
     * <p>This allows quick probing without fully parsing it.
     */
    public Stream<T> probe(String input, int fromIndex) {
      return forTokens().probe(input, fromIndex);
    }

    /**
     * Lazily and iteratively matches {@code input} reader, skipping the skippable patterns, until the
     * input is exhausted or matching failed.
     *
     * <p>Note that unlike {@link #parseToStream(String) parseToStream()}, a matching failure
     * terminates the stream without throwing exception.
     *
     * <p>This allows quick probing without fully parsing it.
     *
     * <p>{@link UncheckedIOException} will be thrown if the underlying reader throws.
     *
     * <p>Characters are internally buffered, so you don't need to pass in {@code BufferedReader}.
     */
    public Stream<T> probe(Reader input) {
      return forTokens().probe(input);
    }

    private Parser<T> forTokens() {
      return new SamePrefix<>() {
        @Override MatchResult<T> skipAndMatch(
            Parser<?> ignored, CharInput input, int start, ErrorContext context) {
          return left().skipAndMatch(toSkip, input, start, context);
        }

        @Override MatchResult<T> match(CharInput input, int start, ErrorContext context) {
          return switch (super.match(input, start, context)) {
            case MatchResult.Success(int head, int tail, T value) ->
                new MatchResult.Success<>(head, skipIfAny(toSkip, input, tail), value);
            case MatchResult.Failure<T> failure -> failure;
          };
        }

        @Override Parser<?> ignoreReturn() {
          return Lexical.this.ignoreReturn().forTokens();
        }
      };
    }

    private Parser<?>.Lexical ignoreReturn() {
      @SuppressWarnings("unchecked") // return value isn't needed
      Parser<Object> elided = (Parser<Object>) Parser.this.ignoreReturn();
      return elided.new Lexical(toSkip);
    }
  }

  /**
   * Defines a simple recursive grammar without needing to explicitly forward-declare a {@link
   * Rule}. Essentially a fixed-point. For example:
   *
   * <pre>{@code
   * Parser<Expr> atomic = ...;
   * Parser<Expr> expression = define(
   *     expr ->
   *         anyOf(expr.between("(", ")"), atomic)
   *             .atLeastOnceDelimitedBy("+")
   *             .map(nums -> nums.stream().mapToInt(n -> n).sum()));
   * }</pre>
   *
   * @since 9.4
   */
  public static <T> Parser<T> define(
      Function<? super Parser<T>, ? extends Parser<? extends T>> definition) {
    Rule<T> rule = new Rule<>();
    return Parser.<T>covariant(rule.definedAs(definition.apply(rule)));
  }

  /**
   * A forward-declared production rule, to be used for recursive grammars.
   *
   * <p>For example, to create a parser for a simple calculator that supports single-digit numbers,
   * addition, and parentheses, you can write:
   *
   * <pre>{@code
   * var rule = new Parser.Rule<Integer>();
   * Parser<Integer> num = Parser.single(CharPredicate.inRange('0', '9')).map(c -> c - '0');
   * Parser<Integer> atomic = rule.between("(", ")").or(num);
   * Parser<Integer> expr =
   *     atomic.atLeastOnceDelimitedBy("+")
   *         .map(nums -> nums.stream().mapToInt(n -> n).sum());
   * return rule.definedAs(expr);
   * }</pre>
   *
   * <p>For simple definitions, you could use the {@link #define} method with a lambda
   * to elide the need of an explicit forward declaration.
   *
   * <p>To prevent StackOverflowError, rules enforce a maximum recursion depth limit.
   * The recursion depth is tracked globally per parse operation across all recursive rules
   * on the call stack. Each rule enforces its own limit against this global depth.
   */
  @ThreadSafe
  public static final class Rule<T> extends Parser<T> {
    private final AtomicReference<Parser<T>> ref = new AtomicReference<>();
    private final int maxRecursionDepth;
    private volatile boolean dryRun = false;

    /** Creates a rule with a default maximum recursion depth of 100. */
    public Rule() {
      this(100);
    }

    /**
     * Creates a rule with the given maximum recursion depth.
     *
     * <p>The recursion depth is tracked globally per parse operation across all recursive rules
     * on the call stack.
     *
     * @throws IllegalArgumentException if {@code maxRecursionDepth} is not positive.
     * @since 10.6
     */
    public Rule(int maxRecursionDepth) {
      checkArgument(maxRecursionDepth > 0, "maxRecursionDepth (%s) must be positive", maxRecursionDepth);
      this.maxRecursionDepth = maxRecursionDepth;
    }

    @Override MatchResult<T> skipAndMatch(
        Parser<?> skip, CharInput input, int start, ErrorContext context) {
      Parser<T> p = ref.get();
      if (start == 0 && input.isEof(0)) {
        checkState(
            !dryRun,
            "Left recursion not supported! Consider using withPostfixes() or the OperatorTable class"
                + " to define the left recursive grammar.");
        if (p == null) { // can happen when dry-running mutually recursive rules.
          return context.failAt(0, "empty input", ""); // A Parser must consume input.
        }
      }
      checkState(p != null, "definedAs() should have been called before parse()");
      try {
        if (++input.nestingLevel > maxRecursionDepth) {
          throw new ParseException(
              start,
              String.format(
                  "at %s: max recursion depth (%s) exceeded:%s",
                  input.sourcePosition(start), maxRecursionDepth, new Snippet(input, start)));
        }
        return p.skipAndMatch(skip, input, start, context);
      } finally {
        --input.nestingLevel;
      }
    }

    /** Define this rule as {@code parser} and returns it. */
    public <S extends T> Parser<S> definedAs(Parser<S> parser) {
      requireNonNull(parser);
      checkArgument(!(parser instanceof Rule), "Do not delegate to a Rule parser");
      dryRun = true;
      try {
        checkState(!parser.matches(""), "parser must not match empty string");
      } finally {
        dryRun = false;
      }
      checkState(ref.compareAndSet(null, covariant(parser)), "definedAs() already called");
      return parser;
    }
  }

  /** Thrown if parsing failed. */
  public static class ParseException extends IllegalArgumentException {
    private final int index;

    ParseException(int index, String message) {
      super(message);
      this.index = index;
    }

    /**
     * Returns the index in the source where this error was detected.
     *
     * <p>The index is for diagnostic purpose and isn't guaranteed to be
     * stable and deterministic across different versions.
     */
    public int getSourceIndex() {
      return index;
    }
  }

  /**
   * Returns metadata about the prefixes that can be used to prune out this parser, if the input
   * doesn't start with any of the prefixes. Return EMPTY_PREFIX to indicate no pruning is applicable.
   */
  Set<String> getPrefixes() {
    return EMPTY_PREFIX;
  }

  private static Set<String> prefixesIfAscii(CharPredicate predicate) {
    if (predicate instanceof CharacterSet cset) {
      return cset.candidateCharsIfAscii()
          .map(chars -> chars.stream().map(Object::toString).collect(toUnmodifiableSet()))
          .orElse(EMPTY_PREFIX);
    }
    return EMPTY_PREFIX;
  }

  /**
   * Matches the input string starting at the given position.
   *
   * @return a MatchResult containing the parsed value and the [start, end) range of the match.
   */
  MatchResult<T> match(CharInput input, int start, ErrorContext context) {
    return skipAndMatch(null, input, start, context);
  }

  abstract MatchResult<T> skipAndMatch(
      Parser<?> skip, CharInput input, int start, ErrorContext context);

  static int skipIfAny(Parser<?> skip, CharInput input, int start) {
    if (skip == null) {
      return start;
    }
    return switch (skip.match(input, start, ErrorContext.MINIMAL)) {
      case MatchResult.Success<?> success -> success.tail();
      case MatchResult.Failure<?> failure -> start;
    };
  }

  static <T> Parser<T> allowZeroWidth(Production<T> production) {
    return switch (production) {
      case Parser<T> parser -> parser;
      case Parser<T>.OrEmpty orEmpty -> orEmpty.unsafeZeroWidthParser;
    };
  }

  /** A derived parser, with {@code this} being the left-most rule. */
  private abstract class SamePrefix<R> extends Parser<R> {
    @Override Set<String> getPrefixes() {
      return left().getPrefixes();
    }

    final Parser<T> left() {
      return Parser.this;
    }
  }

  sealed interface MatchResult<V> {
    record Success<V>(int head, int tail, V value) implements MatchResult<V> {
      @Override public Success<V> startingFrom(int index) {
        return new MatchResult.Success<>(index, tail, value);
      }
    }

    /**
     * Represents failure with an index in the source, and an error message
     * with predefined {name} and {snippet} template placeholders to be filled when throwing exception.
     */
    record Failure<V>(int at, int frontier, String messageTemplate, String symbolName)
        implements MatchResult<V> {
      Failure(int at, String messageTemplate, String symbolName) {
        this(at, at, messageTemplate, symbolName);
      }

      @SuppressWarnings("unchecked")
      <X> Failure<X> safeCast() {
        return (Failure<X>) this;
      }

      ParseException toException(CharInput input) {
        return new ParseException(
            at, String.format("at %s: %s", input.sourcePosition(at), renderMessage(input)));
      }

      private String renderMessage(CharInput input) {
        return Substring.word()
            .immediatelyBetween("{", INCLUSIVE, "}", INCLUSIVE)
            .repeatedly()
            .replaceAllFrom(
                messageTemplate,
                placeholder -> switch (placeholder.toString()) {
                  case "{name}" -> symbolName;
                  case "{snippet}" -> new Snippet(input, at).toString();
                  default -> placeholder;
                });
      }

      @Override public Failure<V> startingFrom(int head) {
        return this;
      }
    }

    MatchResult<V> startingFrom(int head);
  }

  static class ErrorContext {
    static final ErrorContext MINIMAL = new ErrorContext();

    final <V> MatchResult.Failure<V> expecting(String symbolName, int at) {
      return expecting(symbolName, at, at);
    }

    <V> MatchResult.Failure<V> expecting(String symbolName, int at, int frontier) {
      return failAt(at, frontier, "expecting <{name}>.", symbolName);
    }

    final <V> MatchResult.Failure<V> failAt(int at, String messageTemplate, String symbolName) {
      return failAt(at, at, messageTemplate, symbolName);
    }

    <V> MatchResult.Failure<V> failAt(int at, int frontier, String messageTemplate, String symbolName) {
      return new MatchResult.Failure<V>(at, frontier, messageTemplate, symbolName);
    }
  }

  private static final class ErrorTracker extends ErrorContext {
    private MatchResult.Failure<?> farthestFailure = null;

    @Override <V> MatchResult.Failure<V> expecting(
        String symbolName, int at, int frontier) {
      return failAt(at, frontier, "expecting <{name}>, encountered: {snippet}", symbolName);
    }

    @Override <V> MatchResult.Failure<V> failAt(
        int at, int frontier, String messageTemplate, String symbolName) {
      MatchResult.Failure<V> failure = super.failAt(at, frontier, messageTemplate, symbolName);
      // prefer the farthest then the most recent failure
      if (farthestFailure == null || failure.frontier() >= farthestFailure.frontier()) {
        farthestFailure = failure;
      }
      return failure;
    }

    ParseException report(MatchResult.Failure<?> failure, CharInput input) {
      return (farthestFailure == null || failure.frontier() >= farthestFailure.frontier())
          ? failure.toException(input)
          : farthestFailure.toException(input);
    }
  }

  @SuppressWarnings("unchecked") // Parser<T> is covariant
  static <T> Parser<T> covariant(Parser<? extends T> parser) {
    return (Parser<T>) parser;
  }

  static <S, T> Parser<UnaryOperator<T>> asPostfixOperator(
      Parser<S> operator, BiFunction<? super T, ? super S, ? extends T> postfixFunction) {
    requireNonNull(postfixFunction);
    return operator.map(postfixValue -> operand -> postfixFunction.apply(operand, postfixValue));
  }

  private static <A, T> Supplier<T> emptyValueSupplier(Collector<?, A, ? extends T> collector) {
    var supplier = collector.supplier();
    var finisher = collector.finisher();
    return () -> finisher.apply(supplier.get());
  }

  private static <T> T applyOperators(Iterable<? extends UnaryOperator<T>> ops, T operand) {
    for (UnaryOperator<T> op : ops) {
      operand = op.apply(operand);
    }
    return operand;
  }

  private static <T, A, R> Collector<T, A, R> toNull() {
    return Collector.of(() -> null, (a, e) -> {}, (a, b) -> a, a -> null);
  }

  private interface ElidableFunction<F, T> extends Function<F, T> {}
  private interface ElidableBiFunction<A, B, R> extends BiFunction<A, B, R> {}

  private interface Constants {
    static Parser<String> DIGITS = consecutive(CharacterSet.DECIMAL, "digits");
    static Parser<String> WORD = consecutive(charsIn("[a-zA-Z0-9_]"), "word");
    static Parser<Integer> BMP_CODE_UNIT =
        consecutive(4, CharacterSet.HEX, "4-digit hex code point")
            .elidableMap(digits -> Integer.parseInt(digits, 16));
  }

  Parser() {}
}

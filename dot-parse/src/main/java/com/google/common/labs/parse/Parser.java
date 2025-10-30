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
package com.google.common.labs.parse;

import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;

/**
 * A simple recursive descent parser combinator intended to parse simple grammars such as regex, csv
 * format string patterns etc.
 *
 * <p>Different from most parser combinators (such as Haskell Parsec), a common source of bug
 * (infinite loop or StackOverFlowError caused by accidental zero-consumption rule in the context of
 * many() or recursive grammar) is made impossible by requiring all parsers to consume at least one
 * character. Optional suffix is achieved through using the built-in combinators such as {@link
 * #optionallyFollowedBy optionallyFollowedBy()} and {@link #postfix postfix()}; or you can use the
 * {@link #zeroOrMore zeroOrMore()}, {@link #zeroOrMoreDelimitedBy zeroOrMoreDelimitedBy()},
 * {@link #orElse orElse()} and {@link #optional optional()} fluent chains.
 *
 * <p>For simplicity, {@link #or or()} and {@link #anyOf anyOf()} will always backtrack upon failure.
 * But it's more efficient to factor out common left prefix. For example instead of {@code
 * anyOf(expr.followedBy(";"), expr)}, use {@code expr.optionallyFollowedBy(";"))} instead.
 *
 * <p>WARNING: careful using this class to parse user-provided input, or in performance critical hot
 * paths. Parser combinators are not known for optimal performance and recursive grammars can be
 * subject to stack overflow error on maliciously crafted input (think of 10K left parens).
 */
public abstract class Parser<T> {
  private static final Substring.Pattern SQUARE_BRACKETED =
      Substring.between(Substring.prefix('['), Substring.suffix(']'));

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
   * Convenience method taking a character set as parameter.
   *
   * <p>For example {@code anyCharIn("[a-zA-Z-_]")} is a short hand of {@code
   * single(range('a', 'z').orRange('A', 'Z').or('-').or('_')}.
   *
   * <p>You can also use {@code '^'} to get negative character set like:
   * {@code anyCharIn("[^a-zA-Z]")}, which is any non-alphabet character.
   *
   * <p>Note that it's differnt from {@code single(CharPredicate.anyOf(string))},
   * which treats the string as a list of literal characters, not a regex-like
   * character set.
   *
   * <p>Implementation Note: regex isn't used during parsing. The character set string is translated
   * to a plain {@link CharPredicate} at construction time.
   *
   * @param characterSet regex-like character set but disallows backslash so doesn't
   *        support escaping. If your character set includes special characters like literal backslash
   *        or right bracket, use {@link #single} with the corresponding {@link CharPredicate}.
   * @throws IllegalArgumentException if {@code characterSet} includes backslash
   *         or the right bracket (except the outmost pairs of {@code []}).
   * @since 9.4
   */
  public static Parser<Character> anyCharIn(String characterSet) {
    return single(compileCharacterSet(characterSet), characterSet);
  }

  /** Matches a character as specified by {@code matcher}. */
  public static Parser<Character> single(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override MatchResult<Character> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        if (input.isInRange(start) && matcher.test(input.charAt(start))) {
          return new MatchResult.Success<>(start, start + 1, input.charAt(start));
        }
        return context.expecting(name, start);
      }
    };
  }

  /**
   * Convenience method taking a character set as parameter.
   *
   * <p>For example {@code oneOrMoreCharsIn("[a-zA-Z-_]")} is a short hand of {@code
   * consecutive(range('a', 'z').orRange('A', 'Z').or('-').or('_')}.
   *
   * <p>You can also use {@code '^'} to get negative character set like:
   * {@code oneOrMoreCharsIn("[^a-zA-Z]")}, which represents consecutive non-alphabet characters.
   *
   * <p>Note that it's differnt from {@code consecutive(CharPredicate.anyOf(string))},
   * which treats the string as a list of literal characters, not a regex-like
   * character set.
   *
   * <p>Implementation Note: regex isn't used during parsing. The character set string is translated
   * to a plain {@link CharPredicate} at construction time.
   *
   * @param characterSet regex-like character set but disallows backslash so doesn't
   *        support escaping. If your character set includes special characters like literal backslash
   *        or right bracket, use {@link #consecutive} with the corresponding {@link CharPredicate}.
   * @throws IllegalArgumentException if {@code characterSet} includes backslash
   *         or the right bracket (except the outmost pairs of {@code []}).
   * @since 9.4
   */
  public static Parser<String> oneOrMoreCharsIn(String characterSet) {
    return consecutive(
        compileCharacterSet(characterSet), "one or more " + characterSet);
  }

  /** Matches one or more consecutive characters as specified by {@code matcher}. */
  public static Parser<String> consecutive(CharPredicate matcher, String name) {
    return skipConsecutive(matcher, name).source();
  }

  private static Parser<Void> skipConsecutive(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override MatchResult<Void> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        int end = start;
        for (; input.isInRange(end) && matcher.test(input.charAt(end)); end++) {}
        return end > start
            ? new MatchResult.Success<>(start, end, null)
            : context.expecting(name, end);
      }
    };
  }

  /**
   * One or more regex {@code \w+} characters.
   *
   * @since 9.4
   */
  public static Parser<String> word() {
    return consecutive(CharPredicate.WORD, "word");
  }

  /**
   * One or more regex {@code \d+} characters.
   *
   * @since 9.4
   */
  public static Parser<String> digits() {
    return consecutive(CharPredicate.range('0', '9'), "digits");
  }

  /** Matches a literal {@code string}. */
  public static Parser<String> string(String value) {
    checkArgument(value.length() > 0, "value cannot be empty");
    return new Parser<>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        if (input.startsWith(value, start)) {
          return new MatchResult.Success<>(start, start + value.length(), value);
        }
        return context.expecting(value, start);
      }
    };
  }

  /**
   * String literal quoted by {@code quoteChar} and allows backslash escapes (no Unicode escapes).
   *
   * <p>Any escaped character will be passed to the {@code unescapeFunction} to translate to the
   * literal character. If you need ST-Query style escaping that doesn't treat '\t', '\n' etc.
   * specially, just pass {@code Object::toString}.
   *
   * <p>For example, {@code "foo\\bar"} is parsed as {@code foo\bar}.
   *
   * @since 9.4
   */
  public static Parser<String> quotedStringWithEscapes(
      char quoteChar, Function<? super Character, ? extends CharSequence> unescapeFunction) {
    requireNonNull(unescapeFunction);
    checkArgument(quoteChar != '\\', "quoteChar cannot be '\\'");
    checkArgument(!Character.isISOControl(quoteChar), "quoteChar cannot be a control character");
    String quoteString = Character.toString(quoteChar);
    return anyOf(
            consecutive(isNot(quoteChar).and(isNot('\\')), "quoted chars"),
            string("\\").then(single(CharPredicate.ANY, "escaped char").map(unescapeFunction)))
        .zeroOrMore(joining())
        .immediatelyBetween(quoteString, quoteString);
  }

  /**
   * Sequentially matches {@code left} then {@code right}, and then combines the results using the
   * {@code combiner} function.
   */
  public static <A, B, C> Parser<C> sequence(
      Parser<A> left, Parser<B> right, BiFunction<? super A, ? super B, ? extends C> combiner) {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(combiner);
    return left.flatMap(
        leftValue -> right.map(rightValue -> combiner.apply(leftValue, rightValue)));
  }

  /**
   * Sequentially matches {@code left} then {@code right} (which is allowed to be optional), and
   * then combines the results using the {@code combiner} function. If {@code right} is empty, the
   * default value is passed to the {@code combiner} function.
   */
  public static <A, B, C> Parser<C> sequence(
      Parser<A> left,
      Parser<B>.OrEmpty right,
      BiFunction<? super A, ? super B, ? extends C> combiner) {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(combiner);
    return new Parser<C>() {
      @Override MatchResult<C> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (left.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int prefixBegin, int prefixEnd, A v1) ->
              switch (right.notEmpty().skipAndMatch(skip, input, prefixEnd, context)) {
                case MatchResult.Success(int suffixBegin, int suffixEnd, B v2) ->
                    new MatchResult.Success<>(prefixBegin, suffixEnd, combiner.apply(v1, v2));
                case MatchResult.Failure<?> failure ->
                    new MatchResult.Success<>(
                        prefixBegin, prefixEnd, combiner.apply(v1, right.computeDefaultValue()));
              };
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }
    };
  }

  /**
   * Sequentially matches {@code left} then {@code right}, with both allowed to be optional, and
   * then combines the results using the {@code combiner} function. If either is empty, the
   * corresponding default value is passed to the {@code combiner} function.
   */
  public static <A, B, C> Parser<C>.OrEmpty sequence(
      Parser<A>.OrEmpty left,
      Parser<B>.OrEmpty right,
      BiFunction<? super A, ? super B, ? extends C> combiner) {
    return anyOf(
        sequence(left.notEmpty(), right, combiner),
        right.notEmpty().map(v2 -> combiner.apply(left.computeDefaultValue(), v2)))
    .new OrEmpty(() -> combiner.apply(left.computeDefaultValue(), right.computeDefaultValue()));
  }

  /**
   * Sequentially matches {@code left} (which is allowed to be optional), then {@code right}, and
   * then combines the results using the {@code combiner} function. If {@code left} is empty, the
   * default value is passed to the {@code combiner} function.
   */
  static <A, B, C> Parser<C> sequence(
      Parser<A>.OrEmpty left,
      Parser<B> right,
      BiFunction<? super A, ? super B, ? extends C> combiner) {
    return anyOf(
        sequence(left.notEmpty(), right, combiner),
        right.map(v2 -> combiner.apply(left.computeDefaultValue(), v2)));
  }

  /** Matches if any of the given {@code parsers} match. */
  @SafeVarargs
  public static <T> Parser<T> anyOf(Parser<? extends T>... parsers) {
    return stream(parsers).collect(or());
  }

  /**
   * Returns a collector that results in a parser that matches if any of the input {@code parsers}
   * match.
   */
  public static <T> Collector<Parser<? extends T>, ?, Parser<T>> or() {
    return collectingAndThen(
        toUnmodifiableList(),
        parsers -> {
          checkArgument(parsers.size() > 0, "parsers cannot be empty");
          if (parsers.size() == 1) {
            @SuppressWarnings("unchecked") // Parser is covariant
            Parser<T> parser = (Parser<T>) parsers.get(0);
            return parser;
          }
          return new Parser<T>() {
            @Override MatchResult<T> skipAndMatch(
                Parser<?> skip, CharInput input, int start, ErrorContext context) {
              MatchResult.Failure<?> farthestFailure = null;
              for (Parser<? extends T> parser : parsers) {
                switch (parser.skipAndMatch(skip, input, start, context)) {
                  case MatchResult.Success(int head, int tail, T value) -> {
                    return new MatchResult.Success<>(head, tail, value);
                  }
                  case MatchResult.Failure<?> failure -> {
                    if (farthestFailure == null || farthestFailure.at() < failure.at()) {
                      farthestFailure = failure;
                    }
                  }
                }
              }
              return farthestFailure.safeCast();
            }
          };
        });
  }

  /** Matches if {@code this} or {@code that} matches. */
  public final Parser<T> or(Parser<T> that) {
    return anyOf(this, that);
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
    return atLeastOnce(reducing(requireNonNull(reducer))).map(Optional::get);
  }

  /**
   * Returns a parser that applies this parser at least once, greedily, and collects the return
   * values using {@code collector}.
   */
  public final <A, R> Parser<R> atLeastOnce(Collector<? super T, A, ? extends R> collector) {
    requireNonNull(collector);
    Parser<T> self = this;
    return new Parser<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        A buffer = collector.supplier().get();
        var accumulator = collector.accumulator();
        switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) -> {
            accumulator.accept(buffer, value);
            for (int from = tail; ; ) {
              switch (self.skipAndMatch(skip, input, from, context)) {
                case MatchResult.Success(int head2, int tail2, T value2) -> {
                  accumulator.accept(buffer, value2);
                  from = tail2;
                }
                case MatchResult.Failure<?> failure -> {
                  return new MatchResult.Success<>(head, from, collector.finisher().apply(buffer));
                }
              }
            }
          }
          case MatchResult.Failure<?> failure -> {
            return failure.safeCast();
          }
        }
      }
    };
  }

  /**
   * Returns a parser that matches the current parser at least once, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf(string("a"), string("b"), string("c"))
   *     .atLeastOnceDelimitedBy("|")
   * }</pre>
   */
  public final Parser<List<T>> atLeastOnceDelimitedBy(String delimiter) {
    return atLeastOnceDelimitedBy(delimiter, toUnmodifiableList());
  }

  /**
   * Returns a parser that matches the current parser at least once, delimited by the given
   * delimiter, using the given {@code reducer} function to reduce the results.
   *
   * @since 9.4
   */
  public final Parser<T> atLeastOnceDelimitedBy(String delimiter, BinaryOperator<T> reducer) {
    return atLeastOnceDelimitedBy(delimiter, reducing(requireNonNull(reducer))).map(Optional::get);
  }

  /**
   * Returns a parser that matches the current parser at least once, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf(string("a"), string("b"), string("c"))
   *     .atLeastOnceDelimitedBy("|", RegexPattern.asAlternation())
   * }</pre>
   */
  public final <A, R> Parser<R> atLeastOnceDelimitedBy(
      String delimiter, Collector<? super T, A, ? extends R> collector) {
    requireNonNull(collector);
    return sequence(
        this,
        string(delimiter).then(this).zeroOrMore(toCollection(ArrayDeque::new)),
        (first, deque) -> {
          deque.addFirst(first);
          return deque.stream().collect(collector);
        });
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
    return consecutive(charsToMatch, name).orElse("");
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
    return zeroOrMore(toUnmodifiableList());
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
    return zeroOrMoreDelimitedBy(delimiter, toUnmodifiableList());
  }

  /**
   * Starts a fluent chain for matching the current parser zero or more times, delimited by {@code
   * delimiter}. {@code collector} is used to collect the parsed results and the empty collector
   * result will be used if this parser matches zero times.
   * <p>For example if you want to parse a set of names {@code [a,b,c]}, you can use:
   *
   * <pre>{@code
   * consecutive(ALPHA, "item")
   *     .zeroOrMoreDelimitedBy(",", toImmutableSet())
   *     .between("[", "]")
   * }</pre>
   */
  public final <A, R> Parser<R>.OrEmpty zeroOrMoreDelimitedBy(
      String delimiter, Collector<? super T, A, ? extends R> collector) {
    return this.<A, R>atLeastOnceDelimitedBy(delimiter, collector)
        .new OrEmpty(emptyValueSupplier(collector));
  }

  /**
   * Returns a parser that applies the {@code operator} parser zero or more times before {@code
   * this} and applies the result unary operator functions iteratively.
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   */
  public final Parser<T> prefix(Parser<? extends UnaryOperator<T>> operator) {
    return sequence(
        operator.zeroOrMore(), this, (ops, operand) -> applyOperators(ops.reversed(), operand));
  }

  /**
   * Returns a parser that after this parser succeeds, applies the {@code operator} parser zero or
   * more times and apply the result unary operator function iteratively.
   *
   * <p>This is useful to parse postfix operators such as in regex the quantifiers are usually
   * postfix.
   *
   * <p>For infix operator support, consider using {@link OperatorTable}.
   */
  public final Parser<T> postfix(Parser<? extends UnaryOperator<T>> operator) {
    return sequence(this, operator.zeroOrMore(), (operand, ops) -> applyOperators(ops, operand));
  }

  /**
   * Returns a parser that matches the current parser enclosed between {@code prefix} and
   * {@code suffix}, which are non-empty string delimiters.
   */
  public final Parser<T> between(String prefix, String suffix) {
    return between(string(prefix), string(suffix));
  }

  /**
   * Returns a parser that matches the current parser enclosed between {@code prefix} and {@code suffix}.
   */
  public final Parser<T> between(Parser<?> prefix, Parser<?> suffix) {
    return prefix.then(followedBy(suffix));
  }

  /**
   * Returns a parser that matches the current parser <em>immediately</em> enclosed between {@code
   * prefix} and {@code suffix} (no skippable characters as specified by {@link #parseSkipping
   * parseSkipping()} in between).
   */
  public final Parser<T> immediatelyBetween(String prefix, String suffix) {
    return string(prefix).then(literally(followedBy(suffix)));
  }

  /** If this parser matches, returns the result of applying the given function to the match. */
  public final <R> Parser<R> map(Function<? super T, ? extends R> f) {
    requireNonNull(f);
    Parser<T> self = this;
    return new Parser<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              new MatchResult.Success<>(head, tail, f.apply(value));
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }
    };
  }

  /**
   * If this parser matches, applies function {@code f} to get the next parser to match in sequence.
   */
  public final <R> Parser<R> flatMap(Function<? super T, Parser<R>> f) {
    requireNonNull(f);
    Parser<T> self = this;
    return new Parser<>() {
      @Override MatchResult<R> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              switch (f.apply(value).skipAndMatch(skip, input, tail, context)) {
                case MatchResult.Success(int head2, int tail2, R value2) ->
                    new MatchResult.Success<>(head, tail2, value2);
                case MatchResult.Failure<?> failure -> failure.safeCast();
              };
          case MatchResult.Failure<?> failure -> failure.safeCast();
        };
      }
    };
  }

  /** If this parser matches, returns the given result. */
  public final <R> Parser<R> thenReturn(R result) {
    return map(unused -> result);
  }

  /** If this parser matches, applies the given parser on the remaining input. */
  public final <R> Parser<R> then(Parser<R> next) {
    requireNonNull(next);
    return flatMap(unused -> next);
  }

  /**
   * If this parser matches, applies the given optional (or zero-or-more) parser on the remaining
   * input.
   */
  public final <R> Parser<R> then(Parser<R>.OrEmpty next) {
    return sequence(this, next, (unused, value) -> value);
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
    Parser<T> self = this;
    return new Parser<>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        var result = self.skipAndMatch(skip, input, start, context);
        return result instanceof MatchResult.Success<T> success && !condition.test(success.value())
            ? context.expecting(name, success.head())
            : result;
      }
    };
  }

  /** If this parser matches, continue to match {@code suffix}. */
  public final Parser<T> followedBy(String suffix) {
    return followedBy(string(suffix));
  }

  /** If this parser matches, continue to match {@code suffix}. */
  public final Parser<T> followedBy(Parser<?> suffix) {
    return sequence(this, suffix, (value, unused) -> value);
  }

  /** If this parser matches, continue to match the optional {@code suffix}. */
  public final <X> Parser<T> followedBy(Parser<X>.OrEmpty suffix) {
    return sequence(this, suffix, (value, unused) -> value);
  }

  /**
   * Specifies that the matched pattern must be either followed by {@code suffix} or EOF.
   * No other suffixes allowed.
   *
   * @since 9.4
   */
  public final Parser<T> followedByOrEof(Parser<?> suffix) {
    return followedBy(anyOf(suffix, UNSAFE_EOF));
  }

  final Parser<T> followedByEof() {
    return followedBy(UNSAFE_EOF);
  }

  /** Returns an equivalent parser except it allows {@code suffix} if present. */
  public final Parser<T> optionallyFollowedBy(String suffix) {
    return followedBy(string(suffix).orElse(null));
  }

  /**
   * If this parser matches, optionally applies the {@code op} function if the pattern is followed
   * by {@code suffix}.
   */
  public final Parser<T> optionallyFollowedBy(String suffix, Function<? super T, ? extends T> op) {
    return optionallyFollowedBy(string(suffix).thenReturn(op::apply));
  }

  /**
   * Returns an equivalent parser except it will optionally apply the unary operator resulting from
   * {@code suffix}.
   */
  public final Parser<T> optionallyFollowedBy(Parser<? extends UnaryOperator<T>> suffix) {
    return sequence(
        this,
        suffix.orElse(null),
        (operand, operator) -> operator == null ? operand : operator.apply(operand));
  }

  /** A form of negative lookahead such that the match is rejected if followed by {@code suffix}. */
  public final Parser<T> notFollowedBy(String suffix) {
    return notFollowedBy(string(suffix), suffix);
  }

  /** A form of negative lookahead such that the match is rejected if followed by {@code suffix}. */
  public final Parser<T> notFollowedBy(Parser<?> suffix, String name) {
    requireNonNull(suffix);
    requireNonNull(name);
    Parser<T> self = this;
    return new Parser<>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<T> success -> {
            ErrorContext lookaheadContext = new ErrorContext(input);
            yield switch (suffix.skipAndMatch(skip, input, success.tail(), lookaheadContext)) {
              case MatchResult.Success<?> followed ->
                  lookaheadContext.failAt(
                      success.tail(), "unexpected `%s` – %s.", name, new Snippet(input, success.tail()));
              default -> success;
            };
          }
          case MatchResult.Failure<T> failure -> failure;
        };
      }
    };
  }

  /**
   * A form of negative lookahead such that the match is rejected if <em>immediately</em> followed
   * by (no skippable characters as specified by {@link #parseSkipping parseSkipping()} in between)
   * a character that matches {@code predicate}. Useful for parsing keywords such as {@code
   * string("if").notImmediatelyFollowedBy(IDENTIFIER_CHAR, "identifier char")}.
   */
  public final Parser<T> notImmediatelyFollowedBy(CharPredicate predicate, String name) {
    return notFollowedBy(literally(single(predicate, name)), name);
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
    return map(Optional::ofNullable).new OrEmpty(Optional::empty);
  }

  /** Returns a parser that matches the current parser and returns the matched string. */
  public final Parser<String> source() {
    Parser<T> self = this;
    return new Parser<String>() {
      @Override MatchResult<String> skipAndMatch(
          Parser<?> skip, CharInput input, int start, ErrorContext context) {
        return switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<T>(int head, int tail, T value) ->
              new MatchResult.Success<>(head, tail, input.snippet(head, tail - head));
          case MatchResult.Failure<T> failure -> failure.safeCast();
        };
      }
    };
  }

  /**
   * Returns an equivalent parser that suppresses character skipping that's otherwise applied if
   * {@link #parseSkipping parseSkipping()} or {@link #skipping skipping()}
   * are called. For example quoted string literals should not skip whitespaces.
   */
  public static <T> Parser<T> literally(Parser<T> parser) {
    requireNonNull(parser);
    return new Parser<T>() {
      @Override MatchResult<T> skipAndMatch(
          Parser<?> ignored, CharInput input, int start, ErrorContext context) {
        return parser.skipAndMatch(null, input, start, context);
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
    return new Lexical(skip.atLeastOnce(counting()));
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
    return new Lexical(skipConsecutive(charsToSkip, "skipped"));
  }

  /**
   * Parses {@code input} while skipping patterns matched by {@code skip} around atomic matches.
   *
   * <p>Equivalent to {@code skipping(skip).parse(input)}.
   */
  public final T parseSkipping(Parser<?> skip, String input) {
    return skipping(skip).parse(input);
  }

  /**
   * Parses {@code input} while {@code charsToSkip} around atomic matches.
   *
   * <p>Equivalent to {@code skipping(charsToSkip).parse(input)}.
   */
  public final T parseSkipping(CharPredicate charsToSkip, String input) {
    return skipping(charsToSkip).parse(input);
  }

  /**
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  public final T parse(String input) {
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
    ErrorContext context = new ErrorContext(input);
    MatchResult<T> result = match(input, fromIndex, context);
    switch (result) {
      case MatchResult.Success(int head, int tail, T value) -> {
        if (!input.isEof(tail)) {
          throw context.report(context.expecting("EOF", tail));
        }
        return value;
      }
      case MatchResult.Failure<?> failure -> {
        throw context.report(failure);
      }
    }
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
        ErrorContext context = new ErrorContext(input);
        return switch (match(input, index, context)) {
          case MatchResult.Success<T> success -> {
            index = success.tail();
            input.markCheckpoint(index);
            yield success;
          }
          case MatchResult.Failure<?> failure -> {
            throw context.report(failure);
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
        return switch (match(input, index, new ErrorContext(input))) {
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
   * <p>Besides {@link #between between()} and {@link #followedBy(String) followedBy()}, the {@link
   * Parser#sequence(Parser, Parser.OrEmpty, BiFunction) sequence()} and {@link
   * Parser#followedBy(Parser.OrEmpty)} methods can be used to specify that a {@code Parser.OrEmpty}
   * grammar rule follows a regular consuming {@code Parser}.
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
  public final class OrEmpty {
    private final Supplier<? extends T> defaultSupplier;

    private OrEmpty(Supplier<? extends T> defaultSupplier) {
      this.defaultSupplier = defaultSupplier;
    }

    /**
     * The current optional (or zero-or-more) parser must be enclosed between non-empty {@code
     * prefix} and {@code suffix}.
     */
    public Parser<T> between(String prefix, String suffix) {
      return between(string(prefix), string(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser must be enclosed between non-empty {@code
     * prefix} and {@code suffix}.
     */
    public Parser<T> between(Parser<?> prefix, Parser<?> suffix) {
      return prefix.then(followedBy(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser must be <em>immediately</em> enclosed between
     * non-empty {@code prefix} and {@code suffix} (no skippable characters as specified by {@link
     * #parseSkipping parseSkipping()} in between). Useful for matching a literal string, such as
     * {@code zeroOrMore(isNot('"')).immediatelyBetween("\"", "\"")}.
     */
    public final Parser<T> immediatelyBetween(String prefix, String suffix) {
      return string(prefix).then(literally(followedBy(suffix)));
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
    public <R> Parser<R>.OrEmpty delimitedBy(String delimiter, Collector<? super T, ?, R> collector) {
      return sequence(
          this,
          string(delimiter).then(this).zeroOrMore(toCollection(ArrayDeque::new)),
          (first, deque) -> {
            deque.addFirst(first);
            return deque.stream().collect(collector);
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

    /** After matching the current optional (or zero-or-more) parser, proceed to match {@code suffix}.  */
    public <S> Parser<S>.OrEmpty then(Parser<S>.OrEmpty suffix) {
      return sequence(this, suffix, (a, b) -> b);
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     *
     * <p>Note that there is no {@code after()}, but you can use {@link
     * Parser#sequence(Parser, Parser.OrEmpty, BiFunction) sequence()} and {@link
     * Parser#followedBy(Parser.OrEmpty)} to specify that a {@code Parser.OrEmpty} grammar rule
     * follows a regular consuming {@code Parser}.
     */
    public Parser<T> followedBy(String suffix) {
      return followedBy(string(suffix));
    }

    /** The current optional (or zero-or-more) parser may optionally be followed by {@code suffix}.  */
    public <S> Parser<T>.OrEmpty followedBy(Parser<S>.OrEmpty suffix) {
      return sequence(this, suffix, (a, b) -> a);
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     *
     * <p>Not public because {@code rule.definedAs(zeroOrMore().before(rule))} could potentially
     * introduce a left recursion.
     */
    Parser<T> followedBy(Parser<?> suffix) {
      return sequence(this, suffix, (a, b) -> a);
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
    public T parse(String input) {
      return input.isEmpty() ? computeDefaultValue() : notEmpty().parse(input);
    }

    /**
     * Parses the entire input string, ignoring patterns matched by {@code skip}, and returns the
     * result; if there's nothing to parse except skippable content, returns the default empty value.
     */
    public T parseSkipping(Parser<?> skip, String input) {
      return notEmpty()
          .followedByEof()
          .skipping(skip)
          .parseToStream(input)
          .findFirst()
          .orElseGet(defaultSupplier);
    }

    /**
     * Parses the entire input string, ignoring {@code charsToSkip}, and returns the result; if
     * there's nothing to parse except skippable content, returns the default empty value.
     */
    public T parseSkipping(CharPredicate charsToSkip, String input) {
      return parseSkipping(skipConsecutive(charsToSkip, "skipped"), input);
    }

    T computeDefaultValue() {
      return defaultSupplier.get();
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
      this.toSkip = toSkip;
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
      return Stream.of(new ErrorContext(input))
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
     * terminates the stream without throwing exception.
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
      Parser<T> self = Parser.this;
      return new Parser<T>() {
        @Override MatchResult<T> skipAndMatch(
            Parser<?> ignored, CharInput input, int start, ErrorContext context) {
          return self.skipAndMatch(toSkip, input, start, context);
        }

        @Override MatchResult<T> match(CharInput input, int start, ErrorContext context) {
          return switch (super.match(input, start, context)) {
            case MatchResult.Success(int head, int tail, T value) ->
                new MatchResult.Success<>(head, skipIfAny(toSkip, input, tail), value);
            case MatchResult.Failure<T> failure -> failure;
          };
        }
      };
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
    @SuppressWarnings("unchecked") // Parser<T> is covariant
    Parser<T> parser = (Parser<T>) rule.definedAs(definition.apply(rule));
    return parser;
  }

  /**
   * A forward-declared grammar rule, to be used for recursive grammars.
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
   */
  public static final class Rule<T> extends Parser<T> {
    private static final String DO_NOT_DELEGATE_TO_RULE_PARSER = "Do not delegate to a Rule parser";
    private final AtomicReference<Parser<T>> ref = new AtomicReference<>();

    @Override MatchResult<T> skipAndMatch(
        Parser<?> skip, CharInput input, int start, ErrorContext context) {
      Parser<T> p = ref.get();
      checkState(p != null, "definedAs() should have been called before parse()");
      return p.skipAndMatch(skip, input, start, context);
    }

    /** Define this rule as {@code parser} and returns it. */
    @SuppressWarnings("unchecked")  // Parser<T> is covariant
    public <S extends T> Parser<S> definedAs(Parser<S> parser) {
      requireNonNull(parser);
      checkArgument(!(parser instanceof Rule), DO_NOT_DELEGATE_TO_RULE_PARSER);
      checkState(ref.compareAndSet(null, (Parser<T>) parser), "definedAs() already called");
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
    return switch (skip.match(input, start, new ErrorContext(input))) {
      case MatchResult.Success<?> success -> success.tail();
      case MatchResult.Failure<?> failure -> start;
    };
  }

  sealed interface MatchResult<V> permits MatchResult.Success, MatchResult.Failure {
    /**
     * Represents a successful parse result with a value and the [head, tail) range of the match.
     */
    record Success<V>(int head, int tail, V value) implements MatchResult<V> {}

    /** Represents a partial parse result with a value and the [start, end) range of the match. */
    record Failure<V>(int at, String message, Object[] args) implements MatchResult<V> {
      @SuppressWarnings("unchecked")
      <X> Failure<X> safeCast() {
        return (Failure<X>) this;
      }

      ParseException toException(CharInput input) {
        return new ParseException(
            at,
            String.format("at %s: %s", input.sourcePosition(at), String.format(message, args)));
      }
    }
  }

  private static final class ErrorContext {
    private final CharInput input;
    private MatchResult.Failure<?> farthestFailure = null;

    ErrorContext(CharInput input) {
      this.input = input;
    }

    <V> MatchResult.Failure<V> expecting(String name, int at) {
      return failAt(at, "expecting <%s>, encountered %s.", name, new Snippet(input, at));
    }

    <V> MatchResult.Failure<V> failAt(int at, String message, Object... args) {
      var failure = new MatchResult.Failure<V>(at, message, args);
      if (farthestFailure == null || failure.at() > farthestFailure.at()) {
        farthestFailure = failure;
      }
      return failure;
    }

    ParseException report(MatchResult.Failure<?> failure) {
      return (farthestFailure == null || failure.at() >= farthestFailure.at())
          ? failure.toException(input)
          : farthestFailure.toException(input);
    }
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

  record Snippet(CharInput input, int at) {
    Snippet(String input, int at) {
      this(CharInput.from(input), at);
    }

    @Override public String toString() {
      if (input.isEof(at)) {
        return "<EOF>";
      }
      String snippet =
          Substring.upToIncluding(Substring.consecutive(c -> !Character.isWhitespace(c)))
              .limit(50)
              .or(Substring.BEGINNING.toEnd().limit(3))  // print a few whitespaces then
              .in(input.snippet(at, 50))
              .get()
              .toString();
      return "[" + (input.isInRange(at + snippet.length()) ? snippet + "..." : snippet) + "]";
    }
  }

  private static CharPredicate compileCharacterSet(String characterSet) {
    characterSet = Substring.between(Substring.prefix('['), Substring.suffix(']'))
        .from(characterSet)
        .orElseThrow(() -> new IllegalArgumentException("character set must be in square brackets."));
    if (characterSet.isEmpty()) {
      return CharPredicate.NONE;
    }
    Parser<Character> validChar = single(CharPredicate.noneOf("\\]"), "character");
    Parser<CharPredicate> range =
        sequence(validChar.followedBy("-"), validChar, CharPredicate::range);
    Parser<CharPredicate> positiveSet =
        anyOf(range, validChar.map(CharPredicate::is)).atLeastOnce(CharPredicate::or);
    return anyOf(string("^").then(positiveSet).map(CharPredicate::not), positiveSet)
        .parse(characterSet);
  }

  private static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  private static void checkState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  private static int checkPositionIndex(int index, int size, String name) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(
         String.format("%s (%s) must be in range of [0, %s]", name, index, size));
    }
    return index;
  }

  Parser() {}
}

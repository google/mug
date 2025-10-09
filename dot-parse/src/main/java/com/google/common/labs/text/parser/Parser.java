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
package com.google.common.labs.text.parser;

import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
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
 * <p>WARNING: do not use this class to parse user-provided input, or in performance critical hot
 * paths. Parser combinators are not known for optimal performance and recursive grammars can be
 * subject to stack overflow error on maliciously crafted input (think of 10K left parens).
 */
public abstract class Parser<T> {
  /** Matches a character as specified by {@code matcher}. */
  public static Parser<Character> single(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override
      MatchResult<Character> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        if (input.length() > start && matcher.test(input.charAt(start))) {
          return new MatchResult.Success<>(start, start + 1, input.charAt(start));
        }
        return context.expecting(name, start);
      }
    };
  }

  /** Matches one or more consecutive characters as specified by {@code matcher}. */
  public static Parser<String> consecutive(CharPredicate matcher, String name) {
    return skipConsecutive(matcher, name).map(Source::toString);
  }

  private static Parser<Source> skipConsecutive(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override
      MatchResult<Source> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        int end = start;
        for (; end < input.length() && matcher.test(input.charAt(end)); end++) {}
        return end > start
            ? new MatchResult.Success<>(start, end, new Source(input, start, end))
            : context.expecting(name, end);
      }
    };
  }

  /** Matches a literal {@code string}. */
  public static Parser<String> string(String value) {
    checkArgument(value.length() > 0, "value cannot be empty");
    return new Parser<>() {
      @Override
      MatchResult<String> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
        start = skipIfAny(skip, input, start);
        if (input.startsWith(value, start)) {
          return new MatchResult.Success<>(start, start + value.length(), value);
        }
        return context.expecting(value, start);
      }
    };
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
    return right.after(requireNonNull(left), requireNonNull(combiner));
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
    return left.before(right, requireNonNull(combiner));
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
            @Override
            MatchResult<T> skipAndMatch(
                Parser<?> skip, String input, int start, ErrorContext context) {
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
   * Returns a parser that applies this parser at least once, greedily, and collects the return
   * values using {@code collector}.
   */
  public final <A, R> Parser<R> atLeastOnce(Collector<? super T, A, ? extends R> collector) {
    requireNonNull(collector);
    Parser<T> self = this;
    return new Parser<>() {
      @Override
      MatchResult<R> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
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
                  return new MatchResult.Success<>(start, from, collector.finisher().apply(buffer));
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
   *
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
    return this.<A, R>delimitedBy(delimiter, collector).new OrEmpty(emptyValueSupplier(collector));
  }

  /**
   * Returns a parser that matches the current parser repeatedly, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf(string("a"), string("b"), string("c"))
   *     .delimitedBy("|")
   * }</pre>
   */
  public final Parser<List<T>> delimitedBy(String delimiter) {
    return delimitedBy(delimiter, toUnmodifiableList());
  }

  /**
   * Returns a parser that matches the current parser repeatedly, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use:
   *
   * <pre>{@code
   * Parser.anyOf(string("a"), string("b"), string("c"))
   *     .delimitedBy("|", RegexPattern.asAlternation())
   * }</pre>
   */
  public final <A, R> Parser<R> delimitedBy(
      String delimiter, Collector<? super T, A, ? extends R> collector) {
    checkArgument(delimiter.length() > 0, "delimiter cannot be empty");
    requireNonNull(collector);
    Parser<T> self = this;
    return new Parser<>() {
      @Override
      MatchResult<R> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
        A buffer = collector.supplier().get();
        var accumulator = collector.accumulator();
        for (int from = start; ; ) {
          switch (self.skipAndMatch(skip, input, from, context)) {
            case MatchResult.Success(int head, int tail, T value) -> {
              accumulator.accept(buffer, value);
              tail = skipIfAny(skip, input, tail);
              if (!input.startsWith(delimiter, tail)) {
                return new MatchResult.Success<>(start, tail, collector.finisher().apply(buffer));
              }
              from = tail + delimiter.length();
            }
            case MatchResult.Failure<?> failure -> {
              return failure.safeCast();
            }
          }
        }
      }
    };
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
   * Returns a parser that matches the current parser immediately enclosed between {@code open} and
   * {@code close}, which are non-empty string delimiters.
   */
  public final Parser<T> between(String open, String close) {
    return between(string(open), string(close));
  }

  /**
   * Returns a parser that matches the current parser immediately enclosed between {@code open} and
   * {@code close}.
   */
  public final Parser<T> between(Parser<?> open, Parser<?> close) {
    return open.then(this).followedBy(close);
  }

  /** If this parser matches, returns the result of applying the given function to the match. */
  public final <R> Parser<R> map(Function<? super T, ? extends R> f) {
    requireNonNull(f);
    Parser<T> self = this;
    return new Parser<>() {
      @Override
      MatchResult<R> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
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
      @Override
      MatchResult<R> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
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
   * Ensures that the pattern represented by this parser must be followed by {@code next} string.
   */
  public final Parser<T> followedBy(String next) {
    return followedBy(string(next));
  }

  /** If this parser matches, applies the given parser on the remaining input. */
  public final Parser<T> followedBy(Parser<?> next) {
    return sequence(this, next, (value, unused) -> value);
  }

  /**
   * If this parser matches, applies the given optional (or zero-or-more) parser on the remaining
   * input.
   */
  public final <X> Parser<T> followedBy(Parser<X>.OrEmpty next) {
    return sequence(this, next, (value, unused) -> value);
  }

  /** Returns an equivalent parser except it allows {@code suffix} if present. */
  public final Parser<T> optionallyFollowedBy(String suffix) {
    return sequence(this, string(suffix).orElse(null), (value, unused) -> value);
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
      @Override
      MatchResult<T> skipAndMatch(
          Parser<?> skip, String input, int start, ErrorContext context) {
        return switch (self.skipAndMatch(skip, input, start, context)) {
          case MatchResult.Success<T> success -> {
            ErrorContext lookaheadContext = new ErrorContext(input);
            yield switch (suffix.skipAndMatch(skip, input, success.tail(), lookaheadContext)) {
              case MatchResult.Success<?> followed ->
                  lookaheadContext.failAt(success.tail(), "unexpected `%s`", name);
              default -> success;
            };
          }
          case MatchResult.Failure<T> failure -> failure;
        };
      }
    };
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

  /**
   * Returns an equivalent parser that suppresses character skipping that's otherwise applied if
   * {@link #parseSkipping parseSkipping()} or {@link #parseToStreamSkipping parseToStreamSkipping()}
   * are called. For example quoted string literals should not skip whitespaces.
   */
  public static <T> Parser<T> literally(Parser<T> parser) {
    requireNonNull(parser);
    return new Parser<T>() {
      @Override
      MatchResult<T> skipAndMatch(
          Parser<?> ignored, String input, int start, ErrorContext context) {
        return parser.skipAndMatch(null, input, start, context);
      }
    };
  }

  /**
   * Specifies that the optional (or zero-or-more) {@code rule} should be matched literally even if
   * {@link Parser#parseSkipping parseSkipping()} or {@link Parser#parseToStreamSkipping
   * parseToStreamSkipping()} is called.
   */
  public static <T> Parser<T>.OrEmpty literally(Parser<T>.OrEmpty rule) {
    return rule.literally();
  }

  private Parser<T> skipping(Parser<?> patternToSkip) {
    Parser<?> skip = patternToSkip.atLeastOnce(counting());
    Parser<T> self = this;
    return new Parser<T>() {
      @Override
      MatchResult<T> skipAndMatch(
          Parser<?> ignored, String input, int start, ErrorContext context) {
        return self.skipAndMatch(skip, input, start, context);
      }

      @Override
      MatchResult<T> match(String input, int start, ErrorContext context) {
        return switch (super.match(input, start, context)) {
          case MatchResult.Success(int head, int tail, T value) ->
              new MatchResult.Success<>(head, skipIfAny(skip, input, tail), value);
          case MatchResult.Failure<T> failure -> failure;
        };
      }
    };
  }

  /** Parses {@code input} while skipping patterns matched by {@code skip} around atomic matches. */
  public final T parseSkipping(Parser<?> skip, String input) {
    return skipping(skip).parse(input);
  }

  /** Parses {@code input} while {@code charsToSkip} around atomic matches. */
  public final T parseSkipping(CharPredicate charsToSkip, String input) {
    return parseSkipping(skipConsecutive(charsToSkip, "skipped"), input);
  }

  /**
   * Parses {@code input} to a lazy stream while skipping patterns matched by {@code skip} around
   * atomic matches.
   */
  public final Stream<T> parseToStreamSkipping(Parser<?> skip, String input) {
    return skipping(skip).parseToStream(input);
  }

  /** Parses {@code input} to a lazy stream while {@code charsToSkip} around atomic matches. */
  public final Stream<T> parseToStreamSkipping(CharPredicate charsToSkip, String input) {
    return parseToStreamSkipping(skipConsecutive(charsToSkip, "skipped"), input);
  }

  /**
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  public final T parse(String input) {
    ErrorContext context = new ErrorContext(input);
    MatchResult<T> result = match(input, 0, context);
    switch (result) {
      case MatchResult.Success(int head, int tail, T value) -> {
        if (tail != input.length()) {
          throw new ParseException(
              "unmatched input at "
                  + MatchResult.Failure.sourcePosition(input, tail)
                  + ": "
                  + input.substring(tail));
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
    requireNonNull(input);
    class Cursor {
      private int index = 0;

      MatchResult.Success<T> nextOrNull() {
        if (index >= input.length()) {
          return null;
        }
        ErrorContext context = new ErrorContext(input);
        return switch (match(input, index, context)) {
          case MatchResult.Success<T> success -> {
            index = success.tail();
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
   * Facilitates a fluent chain for matching the current parser optionally. This is needed because
   * we require all parsers to match at least one character. So optionality is only legal when
   * combined together with a non-empty prefix, suffix or both, which will be specified by methods
   * of this class.
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
      return prefix.then(before(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     */
    public Parser<T> before(String suffix) {
      return before(string(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     *
     * <p>Not public because {@code lazy.delegateTo(zeroOrMore().before(lazy))} could potentially
     * introduce a left recursion.
     */
    Parser<T> before(Parser<?> suffix) {
      return before(suffix, (v1, v2) -> v1);
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     *
     * <p>Not public because {@code lazy.delegateTo(zeroOrMore().before(lazy))} could potentially
     * introduce a left recursion.
     */
    private <S, R> Parser<R> before(
        Parser<S> suffix, BiFunction<? super T, ? super S, ? extends R> combine) {
      return anyOf(
          sequence(Parser.this, suffix, combine),
          suffix.map(v2 -> combine.apply(defaultSupplier.get(), v2)));
    }

    /**
     * The current optional (or zero-or-more) parser must be follow non-empty {@code prefix}, and
     * then use the given {@code combine} function to combine the results.
     */
    private <P, R> Parser<R> after(
        Parser<P> prefix, BiFunction<? super P, ? super T, ? extends R> combine) {
      Parser<T> suffix = Parser.this;
      return new Parser<R>() {
        @Override
        MatchResult<R> skipAndMatch(
            Parser<?> skip, String input, int start, ErrorContext context) {
          return switch (prefix.skipAndMatch(skip, input, start, context)) {
            case MatchResult.Success(int prefixBegin, int prefixEnd, P v1) ->
                switch (suffix.skipAndMatch(skip, input, prefixEnd, context)) {
                  case MatchResult.Success(int suffixBegin, int suffixEnd, T v2) ->
                      new MatchResult.Success<>(prefixBegin, suffixEnd, combine.apply(v1, v2));
                  case MatchResult.Failure<?> failure ->
                      new MatchResult.Success<>(
                          prefixBegin, prefixEnd, combine.apply(v1, defaultSupplier.get()));
                };
            case MatchResult.Failure<?> failure -> failure.safeCast();
          };
        }
      };
    }

    /**
     * Parses the entire input string and returns the result; if input is empty, returns the default
     * empty value.
     */
    public T parse(String input) {
      return input.isEmpty() ? defaultSupplier.get() : Parser.this.parse(input);
    }

    private Parser<T>.OrEmpty literally() {
      return Parser.literally(Parser.this).new OrEmpty(defaultSupplier);
    }
  }

  /**
   * A lazy parser, to be used for recursive grammars.
   *
   * <p>For example, to create a parser for a simple calculator that supports single-digit numbers,
   * addition, and parentheses, you can write:
   *
   * <pre>{@code
   * var lazy = new Parser.Lazy<Integer>();
   * Parser<Integer> num = Parser.single(CharPredicate.inRange('0', '9')).map(c -> c - '0');
   * Parser<Integer> atomic = lazy.between("(", ")").or(num);
   * Parser<Integer> expr =
   *     atomic.delimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
   * return lazy.delegateTo(expr);
   * }</pre>
   */
  public static final class Lazy<T> extends Parser<T> {
    private static final String DO_NOT_DELEGATE_TO_LAZY_PARSER = "Do not delegate to a Lazy parser";
    private final AtomicReference<Parser<T>> ref = new AtomicReference<>();

    @Override
    MatchResult<T> skipAndMatch(
        Parser<?> skip, String input, int start, ErrorContext context) {
      Parser<T> p = ref.get();
      checkState(p != null, "delegateTo() should have been called before parse()");
      return p.skipAndMatch(skip, input, start, context);
    }

    /** Sets and returns the delegate parser. */
    public Parser<T> delegateTo(Parser<T> parser) {
      requireNonNull(parser);
      checkArgument(!(parser instanceof Lazy), DO_NOT_DELEGATE_TO_LAZY_PARSER);
      checkState(ref.compareAndSet(null, parser), "delegateTo() already called");
      return parser;
    }
  }

  /** Thrown if parsing failed. */
  public static class ParseException extends IllegalArgumentException {
    ParseException(String message) {
      super(message);
    }
  }

  /**
   * Matches the input string starting at the given position.
   *
   * @return a {@link MatchResult} containing the parsed value and the [start, end) range of the
   *     match.
   */
  MatchResult<T> match(String input, int start, ErrorContext context) {
    return skipAndMatch(null, input, start, context);
  }

  abstract MatchResult<T> skipAndMatch(
      Parser<?> skip, String input, int start, ErrorContext context);

  static int skipIfAny(Parser<?> skip, String input, int start) {
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
    record Failure<V>(int at, String message, List<?> args) implements MatchResult<V> {
      @SuppressWarnings("unchecked")
      <X> Failure<X> safeCast() {
        return (Failure<X>) this;
      }

      ParseException toException(String input) {
        return new ParseException(
            String.format(
                "at %s: %s", sourcePosition(input, at), String.format(message, args.toArray())));
      }

      static String sourcePosition(String input, int at) {
        int line = 1;
        int lineStartIndex = 0;
        for (Substring.Match match :
            iterateOnce(Substring.all('\n').match(input).takeWhile(m -> m.index() < at))) {
          lineStartIndex = match.index() + 1;
          line++;
        }
        return line + ":" + (at - lineStartIndex + 1);
      }
    }
  }

  private static final class ErrorContext {
    private final String input;
    private MatchResult.Failure<?> farthestFailure = null;

    ErrorContext(String input) {
      this.input = input;
    }

    <V> MatchResult.Failure<V> expecting(String name, int at) {
      return failAt(
          at,
          "expecting `%s`, encountered `%s`.",
          name,
          at == input.length() ? "EOF" : input.charAt(at));
    }

    <V> MatchResult.Failure<V> failAt(int at, String message, Object... args) {
      var failure = new MatchResult.Failure<V>(at, message, stream(args).collect(toUnmodifiableList()));
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

  private record Source(String input, int begin, int end) {
    @Override
    public String toString() {
      return input.substring(begin, end);
    }
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

  Parser() {}
}


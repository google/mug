package com.google.common.labs.regex;

import static com.google.common.labs.regex.InternalUtils.checkArgument;
import static com.google.common.labs.regex.InternalUtils.checkState;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;

import com.google.mu.util.CharPredicate;

/**
 * A simple recursive descent parser intended to parse regex.
 *
 * <p>To avoid risk of infinite loop caused by repetitive greedy grammar, all parsers are required
 * to consume at least one character upon success.
 */
@FunctionalInterface
interface Parser<T> {
  /** Matches a character as specified by {@code matcher}. */
  static Parser<Character> single(CharPredicate matcher) {
    requireNonNull(matcher);
    return (String input, int start) -> {
      if (input.length() > start && matcher.test(input.charAt(start))) {
        return new ParseResult.Success<>(start, start + 1, input.charAt(start));
      }
      return ParseResult.failAt(start, "expected: %s", matcher);
    };
  }

  /** Matches one or more consecutive characters as specified by {@code matcher}. */
  static Parser<String> consecutive(CharPredicate matcher) {
    requireNonNull(matcher);
    return (String input, int start) -> {
      int end = start;
      for (; end < input.length() && matcher.test(input.charAt(end)); end++) {}
      return end > start
          ? new ParseResult.Success<>(start, end, input.substring(start, end))
          : ParseResult.failAt(end, "expected: consecutive(%s)", matcher);
    };
  }

  /** Matches a literal {@code value}. */
  static Parser<String> literal(String value) {
    checkArgument(value.length() > 0, "value cannot be empty");
    return (String input, int start) -> {
      if (input.startsWith(value, start)) {
        return new ParseResult.Success<>(start, start + value.length(), value);
      }
      return ParseResult.failAt(start, "expected: %s", value);
    };
  }

  /**
   * Sequentially matches {@code left} then {@code right}, and then combines the results using the
   * {@code combiner} function.
   */
  static <A, B, C> Parser<C> sequence(
      Parser<A> left, Parser<B> right, BiFunction<? super A, ? super B, ? extends C> combiner) {
    requireNonNull(left);
    requireNonNull(right);
    requireNonNull(combiner);
    return left.flatMap(
        leftValue -> right.map(rightValue -> combiner.apply(leftValue, rightValue)));
  }

  /** Matches if any of the given {@code parsers} match. */
  @SafeVarargs
  static <T> Parser<T> anyOf(Parser<? extends T>... parsers) {
    return stream(parsers).collect(or());
  }

  /**
   * Returns a collector that results in a parser that matches if any of the input {@code parsers}
   * match.
   */
  static <T> Collector<Parser<? extends T>, ?, Parser<T>> or() {
    return collectingAndThen(
        toUnmodifiableList(),
        parsers -> {
          checkArgument(parsers.size() > 0, "parsers cannot be empty");
          return (input, start) -> {
            List<ParseResult.Failure<?>> failures = new ArrayList<>();
            for (Parser<? extends T> parser : parsers) {
              switch (parser.parse(input, start)) {
                case ParseResult.Success<? extends T>(int head, int tail, T value) -> {
                  return new ParseResult.Success<>(head, tail, value);
                }
                case ParseResult.Failure<?> failure -> failures.add(failure);
              }
            }
            return failures.stream()
                .max(Comparator.comparingInt(ParseResult.Failure<?>::at))
                .get()
                .safeCast();
          };
        });
  }

  /** Matches if {@code this} or {@code that} matches. */
  default Parser<T> or(Parser<T> that) {
    return anyOf(this, that);
  }

  /** Returns a parser that applies this parser at least once, greedily. */
  default Parser<List<T>> atLeastOnce() {
    return (input, start) -> {
      List<T> results = new ArrayList<>();
      switch (parse(input, start)) {
        case ParseResult.Success<T>(int head, int tail, T value) -> {
          results.add(value);
          for (int from = tail; ; ) {
            switch (parse(input, from)) {
              case ParseResult.Success<T>(int head2, int tail2, T value2) -> {
                results.add(value2);
                from = tail2;
              }
              case ParseResult.Failure<?> failure -> {
                return new ParseResult.Success<>(start, from, unmodifiableList(results));
              }
            }
          }
        }
        case ParseResult.Failure<?> failure -> {
          return failure.safeCast();
        }
      }
    };
  }

  /**
   * Returns a parser that matches the current parser immediately enclosed between {@code open} and
   * {@code close}, which are non-empty string delimiters.
   */
  default Parser<T> immediatelyBetween(String open, String close) {
    return literal(open).then(this).followedBy(literal(close));
  }

  /**
   * Returns a parser that matches the current parser repeatedly, delimited by the given delimiter.
   *
   * <p>For example if you want to express the regex pattern {@code (a|b|c)}, you can use: {@code
   * Parser.anyOf(literal("a"), literal("b"), literal("c")).delimitedBy("|")}.
   */
  default Parser<List<T>> delimitedBy(String delimiter) {
    checkArgument(delimiter.length() > 0, "delimiter cannot be empty");
    return (input, start) -> {
      List<T> results = new ArrayList<>();
      for (int from = start; ; ) {
        switch (parse(input, from)) {
          case ParseResult.Success<T>(int head, int tail, T value) -> {
            results.add(value);
            if (!input.startsWith(delimiter, tail)) {
              return new ParseResult.Success<>(start, tail, unmodifiableList(results));
            }
            from = tail + delimiter.length();
          }
          case ParseResult.Failure<?> failure -> {
            return failure.safeCast();
          }
        }
      }
    };
  }

  /**
   * Returns a parser that after this parser succeeds, applies the {@code op} parser zero or more
   * times and apply the result unary operator function iteratively.
   *
   * <p>This is useful to parse postfix operators such as in regex the quantifiers are usually
   * postfix.
   */
  default Parser<T> postfix(Parser<? extends UnaryOperator<T>> op) {
    requireNonNull(op);
    return (input, start) -> {
      switch (parse(input, start)) {
        case ParseResult.Success<T>(int operandBegin, int operandEnd, T value) -> {
          T operand = value;
          for (int end = operandEnd; ; ) {
            switch (op.parse(input, end)) {
              case ParseResult.Success<? extends Function<? super T, ? extends T>>(
                      int opBegin,
                      int opEnd,
                      Function<? super T, ? extends T> unary) -> {
                operand = unary.apply(operand);
                end = opEnd;
              }
              case ParseResult.Failure<?> failure -> {
                return new ParseResult.Success<>(start, end, operand);
              }
            }
          }
        }
        case ParseResult.Failure<?> failure -> {
          return failure.safeCast();
        }
      }
    };
  }

  /** If this parser matches, returns the result of applying the given function to the match. */
  default <R> Parser<R> map(Function<? super T, ? extends R> f) {
    requireNonNull(f);
    return (input, start) ->
        switch (parse(input, start)) {
          case ParseResult.Success<T>(int head, int tail, T value) ->
              new ParseResult.Success<>(head, tail, f.apply(value));
          case ParseResult.Failure<?> failure -> failure.safeCast();
        };
  }

  /**
   * If this parser matches, applies function {@code f} to get the next parser to match in sequence.
   */
  default <R> Parser<R> flatMap(Function<? super T, Parser<R>> f) {
    requireNonNull(f);
    return (input, start) ->
        switch (parse(input, start)) {
          case ParseResult.Success<T>(int head, int tail, T value) ->
              switch (f.apply(value).parse(input, tail)) {
                case ParseResult.Success<R>(int head2, int tail2, R value2) ->
                    new ParseResult.Success<>(head, tail2, value2);
                case ParseResult.Failure<?> failure -> failure.safeCast();
              };
          case ParseResult.Failure<?> failure -> failure.safeCast();
        };
  }

  /** If this parser matches, returns the given result. */
  default <R> Parser<R> thenReturn(R result) {
    return map(unused -> result);
  }

  /** If this parser matches, applies the given parser on the remaining input. */
  default <R> Parser<R> then(Parser<R> next) {
    requireNonNull(next);
    return flatMap(unused -> next);
  }

  /** If this parser matches, applies the given parser on the remaining input. */
  default Parser<T> followedBy(Parser<?> next) {
    requireNonNull(next);
    return flatMap(value -> next.thenReturn(value));
  }

  /** If this parser matches, applies the given parser on the remaining input. */
  default Parser<T> followedBy(String next) {
    return followedBy(literal(next));
  }

  /**
   * If this parser matches, optionally applies the {@code op} function if the pattern is followed
   * by {@code suffix}.
   */
  default Parser<T> optionallyFollowedBy(String suffix, Function<? super T, ? extends T> op) {
    requireNonNull(op);
    checkArgument(suffix.length() > 0, "suffix cannot be empty");
    return (input, start) -> {
      ParseResult<T> result = parse(input, start);
      if (result instanceof ParseResult.Success<T>(int head, int tail, T value)
          && input.startsWith(suffix, tail)) {
        return new ParseResult.Success<>(head, tail + suffix.length(), op.apply(value));
      }
      return result;
    };
  }

  /**
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   */
  default T parse(String input) throws ParseException {
    ParseResult<T> result = parse(input, 0);
    switch (result) {
      case ParseResult.Success<T>(int head, int tail, T value) -> {
        if (tail != input.length()) {
          throw new ParseException("unmatched input at " + tail + ": " + input.substring(tail));
        }
        return value;
      }
      case ParseResult.Failure<T>(int at, String message, List<?> args) -> {
        throw new ParseException(
            String.format("at %s: %s", at, String.format(message, args.toArray())));
      }
    }
  }

  /**
   * Parses the input string starting at the given position.
   *
   * @return a {@link ParseResult} containing the parsed value and the [start, end) range of the
   *     match.
   */
  ParseResult<T> parse(String input, int start);

  sealed interface ParseResult<V> permits ParseResult.Success, ParseResult.Failure {
    static <V> Failure<V> failAt(int at, String message, Object... args) {
      return new Failure<>(at, message, Arrays.asList(args));
    }

    /**
     * Represents a successful parse result with a value and the [head, tail) range of the match.
     */
    record Success<V>(int head, int tail, V value) implements ParseResult<V> {}

    /** Represents a partial parse result with a value and the [start, end) range of the match. */
    record Failure<V>(int at, String message, List<?> args) implements ParseResult<V> {
      <X> Failure<X> safeCast() {
        return new Failure<>(at, message, args);
      }
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
   * Parser<Integer> num = Parser.single(CharMatcher.inRange('0', '9')).map(c -> c - '0');
   * Parser<Integer> atomic = lazy.immediatelyBetween("(", ")").or(num);
   * Parser<Integer> expr =
   *     atomic.delimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
   * return lazy.delegateTo(expr);
   * }</pre>
   */
  final class Lazy<T> implements Parser<T> {
    private final AtomicReference<Parser<T>> ref = new AtomicReference<>();

    @Override
    public ParseResult<T> parse(String input, int start) {
      Parser<T> p = ref.get();
      checkState(p != null, "delegateTo() should have been called before parse()");
      return p.parse(input, start);
    }

    /** Sets and returns the delegate parser. */
    public Parser<T> delegateTo(Parser<T> parser) {
      requireNonNull(parser);
      checkState(ref.compareAndSet(null, parser), "delegateTo() already called");
      return parser;
    }
  }

  /** Thrown if parsing failed. */
  class ParseException extends IllegalArgumentException {
    ParseException(String message) {
      super(message);
    }
  }
}

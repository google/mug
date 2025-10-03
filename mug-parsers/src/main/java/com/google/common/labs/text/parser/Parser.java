package com.google.common.labs.text.parser;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
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

/**
 * A simple recursive descent parser combinator intended to parse simple grammars such as regex, csv
 * format string patterns etc.
 *
 * <p>Different from most parser combinators (such as Haskell Parsec), a common source of bug
 * (infinite loop or StackOverFlowError caused by accidental zero-consumption rule in the context of
 * many() or recursive grammar) is made impossible by requiring all parsers to consume at least one
 * character. Optional suffix is achieved through using the built-in combinators such as {@link
 * #optionallyFollowedBy} and {@link #postfix}; or you can use the {@link #zeroOrMore}, {@link
 * #zeroOrMoreDelimitedBy}, {@link #orElse} and {@link #optional} fluent chains.
 *
 * <p>For simplicity, {@link #or} and {@link #anyOf} will always backtrack upon failure. But it's
 * more efficient to factor out common left prefix. For example instead of {@code
 * anyOf(expr.followedBy(";"), expr)}, use {@code expr.optionallyFollowedBy(";"))} instead.
 */
public abstract class Parser<T> {
  /** Matches a character as specified by {@code matcher}. */
  public static Parser<Character> single(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override
      MatchResult<Character> match(String input, int start) {
        if (input.length() > start && matcher.test(input.charAt(start))) {
          return new MatchResult.Success<>(start, start + 1, input.charAt(start));
        }
        return MatchResult.failAt(start, "expecting %s", name);
      }
    };
  }

  /** Matches one or more consecutive characters as specified by {@code matcher}. */
  public static Parser<String> consecutive(CharPredicate matcher, String name) {
    requireNonNull(matcher);
    requireNonNull(name);
    return new Parser<>() {
      @Override
      MatchResult<String> match(String input, int start) {
        int end = start;
        for (; end < input.length() && matcher.test(input.charAt(end)); end++) {}
        return end > start
            ? new MatchResult.Success<>(start, end, input.substring(start, end))
            : MatchResult.failAt(end, "expecting one or more %s", name);
      }
    };
  }

  /** Matches a literal {@code value}. */
  public static Parser<String> literal(String value) {
    checkArgument(value.length() > 0, "value cannot be empty");
    return new Parser<>() {
      @Override
      MatchResult<String> match(String input, int start) {
        if (input.startsWith(value, start)) {
          return new MatchResult.Success<>(start, start + value.length(), value);
        }
        return MatchResult.failAt(start, "expecting `%s`", value);
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
          return new Parser<T>() {
            @Override
            MatchResult<T> match(String input, int start) {
              List<MatchResult.Failure<?>> failures = new ArrayList<>();
              for (Parser<? extends T> parser : parsers) {
                switch (parser.match(input, start)) {
                  case MatchResult.Success(int head, int tail, T value) -> {
                    return new MatchResult.Success<>(head, tail, value);
                  }
                  case MatchResult.Failure<?> failure -> failures.add(failure);
                }
              }
              return failures.stream()
                  .max(comparingInt(MatchResult.Failure<?>::at))
                  .get()
                  .safeCast();
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
      MatchResult<R> match(String input, int start) {
        A buffer = collector.supplier().get();
        var accumulator = collector.accumulator();
        switch (self.match(input, start)) {
          case MatchResult.Success(int head, int tail, T value) -> {
            accumulator.accept(buffer, value);
            for (int from = tail; ; ) {
              switch (self.match(input, from)) {
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
   * Parser.anyOf(literal("a"), literal("b"), literal("c"))
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
   * Parser.anyOf(literal("a"), literal("b"), literal("c"))
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
      MatchResult<R> match(String input, int start) {
        A buffer = collector.supplier().get();
        var accumulator = collector.accumulator();
        for (int from = start; ; ) {
          switch (self.match(input, from)) {
            case MatchResult.Success(int head, int tail, T value) -> {
              accumulator.accept(buffer, value);
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
   * Returns a parser that after this parser succeeds, applies the {@code op} parser zero or more
   * times and apply the result unary operator function iteratively.
   *
   * <p>This is useful to parse postfix operators such as in regex the quantifiers are usually
   * postfix.
   */
  public final Parser<T> postfix(Parser<? extends UnaryOperator<T>> op) {
    return sequence(
        this,
        op.zeroOrMore(),
        (operand, operators) -> {
          for (UnaryOperator<T> operator : operators) {
            operand = operator.apply(operand);
          }
          return operand;
        });
  }

  /**
   * Returns a parser that matches the current parser immediately enclosed between {@code open} and
   * {@code close}, which are non-empty string delimiters.
   */
  public final Parser<T> between(String open, String close) {
    return between(literal(open), literal(close));
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
      MatchResult<R> match(String input, int start) {
        return switch (self.match(input, start)) {
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
      MatchResult<R> match(String input, int start) {
        return switch (self.match(input, start)) {
          case MatchResult.Success(int head, int tail, T value) ->
              switch (f.apply(value).match(input, tail)) {
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

  /** If this parser matches, applies the given parser on the remaining input. */
  public final Parser<T> followedBy(Parser<?> next) {
    requireNonNull(next);
    return flatMap(value -> next.thenReturn(value));
  }

  /**
   * Ensures that the pattern represented by this parser must be followed by {@code next} string.
   */
  public final Parser<T> followedBy(String next) {
    return followedBy(literal(next));
  }

  /** Returns an equivalent parser except it allows {@code suffix} if present. */
  public final Parser<T> optionallyFollowedBy(String suffix) {
    return sequence(this, literal(suffix).orElse(null), (value, unused) -> value);
  }

  /**
   * If this parser matches, optionally applies the {@code op} function if the pattern is followed
   * by {@code suffix}.
   */
  public final Parser<T> optionallyFollowedBy(String suffix, Function<? super T, ? extends T> op) {
    return optionallyFollowedBy(literal(suffix).thenReturn(op::apply));
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
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  public final T parse(String input) {
    MatchResult<T> result = match(input, 0);
    switch (result) {
      case MatchResult.Success(int head, int tail, T value) -> {
        if (tail != input.length()) {
          throw new ParseException("unmatched input at " + tail + ": " + input.substring(tail));
        }
        return value;
      }
      case MatchResult.Failure<?> failure -> {
        throw failure.toException();
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
        return switch (match(input, index)) {
          case MatchResult.Success<T> success -> {
            index = success.tail();
            yield success;
          }
          case MatchResult.Failure<?> failure -> {
            throw failure.toException();
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
      return between(literal(prefix), literal(suffix));
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
      return before(literal(suffix));
    }

    /**
     * The current optional (or zero-or-more) parser must be followed by non-empty {@code suffix}.
     *
     * <p>Not public because {@code lazy.delegateTo(zeroOrMore().before(lazy))} could potentially
     * introduce a left recursion.
     */
    Parser<T> before(Parser<?> suffix) {
      return anyOf(Parser.this.followedBy(suffix), suffix.map(unused -> defaultSupplier.get()));
    }

    /**
     * The current optional (or zero-or-more) parser must be follow non-empty {@code prefix}, and
     * then use the given {@code combine} function to combine the results.
     */
    <P, R> Parser<R> after(
        Parser<P> prefix, BiFunction<? super P, ? super T, ? extends R> combine) {
      Parser<T> suffix = Parser.this;
      return new Parser<R>() {
        @Override
        MatchResult<R> match(String input, int start) {
          return switch (prefix.match(input, start)) {
            case MatchResult.Success(int prefixBegin, int prefixEnd, P v1) ->
                switch (suffix.match(input, prefixEnd)) {
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
  }

  /**
   * A lazy parser, to be used for recursive grammars.
   *
   * <p>For example, to create a parser for a simple calculator that supports single-digit numbers,
   * addition, and parentheses, you can write:
   *
   * <pre>{@code
   * var lazy = new Parser.Lazy<Integer>();
   * Parser<Integer> num = Parser.single(CharPredicate.range('0', '9')).map(c -> c - '0');
   * Parser<Integer> atomic = lazy.immediatelyBetween("(", ")").or(num);
   * Parser<Integer> expr =
   *     atomic.delimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
   * return lazy.delegateTo(expr);
   * }</pre>
   */
  public static final class Lazy<T> extends Parser<T> {
    private static final String DO_NOT_DELEGATE_TO_LAZY_PARSER = "Do not delegate to a Lazy parser";
    private final AtomicReference<Parser<T>> ref = new AtomicReference<>();

    @Override
    MatchResult<T> match(String input, int start) {
      Parser<T> p = ref.get();
      checkState(p != null, "delegateTo() should have been called before parse()");
      return p.match(input, start);
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
  abstract MatchResult<T> match(String input, int start);

  sealed interface MatchResult<V> permits MatchResult.Success, MatchResult.Failure {
    static <V> Failure<V> failAt(int at, String message, Object... args) {
      return new Failure<>(at, message, stream(args).collect(toUnmodifiableList()));
    }

    /**
     * Represents a successful parse result with a value and the [head, tail) range of the match.
     */
    record Success<V>(int head, int tail, V value) implements MatchResult<V> {}

    /** Represents a partial parse result with a value and the [start, end) range of the match. */
    record Failure<V>(int at, String message, List<?> args) implements MatchResult<V> {
      <X> Failure<X> safeCast() {
        return new Failure<>(at, message, args);
      }

      ParseException toException() {
        return new ParseException(
            String.format("at %s: %s", at, String.format(message, args.toArray())));
      }
    }
  }

  private static <A, T> Supplier<T> emptyValueSupplier(Collector<?, A, ? extends T> collector) {
    var supplier = collector.supplier();
    var finisher = collector.finisher();
    return () -> finisher.apply(supplier.get());
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
}


package com.google.common.labs.parse;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.util.CharPredicate;

/**
 * A sealed interface representing an abstract production rule that can either be an
 * always-consuming {@link Parser} or an optional {@link Parser.OrEmpty}.
 *
 * <p>Useful as a parameter to {@code Parser.sequence()} methods, so as to avoid exponential
 * number of overloads.
 *
 * @since 10.0
 */
public sealed interface Production<T> permits Parser, Parser.OrEmpty {

  /**
   * Parses the entire input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parse(String input);


  /**
   * Parses the entire input string, ignoring patterns matched by {@code skip}, and returns the
   * result.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parseSkipping(Parser<?> skip, String input);

  /**
   * Parses the entire input string, ignoring {@code charsToSkip}, and returns the result.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parseSkipping(CharPredicate charsToSkip, String input);

  /** Returns true if this production matches the entirety of {@code input} string. */
  boolean matches(String input);

  /** The current production must be enclosed between non-empty {@code prefix} and {@code suffix}. */
  default Parser<T> between(String prefix, String suffix) {
    return between(Parser.string(prefix), Parser.string(suffix));
  }

  /** The current production must be enclosed between non-empty {@code prefix} and {@code suffix}. */
  default Parser<T> between(Parser<?> prefix, Parser<?> suffix) {
    return Parser.sequence(prefix, this, (p, t) -> t).followedBy(suffix);
  }

  /**
   * Returns a parser that matches {@code this} pattern enclosed between {@code prefix} and {@code suffix},
   * both allowed to be empty.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<T>} or {@code Parser<T>.OrEmpty} subtypes respectively.
   */
  Production<T> between(Parser<?>.OrEmpty prefix, Parser<?>.OrEmpty suffix);

  /**
   * The current production must be <em>immediately</em> enclosed between
   * non-empty {@code prefix} and {@code suffix} (no skippable characters as specified by {@link
   * #parseSkipping parseSkipping()} in between). Useful for matching a literal string, such as
   * {@code zeroOrMore(isNot('"')).immediatelyBetween("\"", "\"")}.
   */
  default Parser<T> immediatelyBetween(String prefix, String suffix) {
    return Parser.string(prefix).then(Parser.literally(followedBy(suffix)));
  }

  /**
   * After matching the current production, proceed to match {@code suffix}.
   */
  default <S> Parser<S> then(Parser<S> suffix) {
    return Parser.sequence(Parser.maybeZeroWidth(this), suffix, (a, b) -> b);
  }

  /**
   * After matching the current optional (or zero-or-more) parser, proceed to match {@code suffix}.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<S>} or {@code Parser<S>.OrEmpty} subtypes respectively.
   */
  <S> Production<S> then(Parser<S>.OrEmpty suffix);

  /** The current production must be followed by non-empty {@code suffix}. */
  default Parser<T> followedBy(String suffix) {
    return followedBy(Parser.string(suffix));
  }

  /** The current production must be followed by non-empty {@code suffix}. */
  default Parser<T> followedBy(Parser<?> suffix) {
    return Parser.sequence(Parser.maybeZeroWidth(this), suffix, (a, b) -> a);
  }

  /**
   * The current production may optionally be followed by {@code suffix}.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<T>} or {@code Parser<T>.OrEmpty} subtypes respectively.
   */
  <S> Production<T> followedBy(Parser<S>.OrEmpty suffix);

  /**
   * Returns an equivalent production except it allows {@code suffix} if present.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<T>} or {@code Parser<T>.OrEmpty} subtypes respectively.
   */
  Production<T> optionallyFollowedBy(String suffix);

  /**
   * If this parser matches, optionally applies the {@code op} function if this production is followed
   * by {@code suffix}.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<T>} or {@code Parser<T>.OrEmpty} subtypes respectively.
   */
  Production<T> optionallyFollowedBy(String suffix, Function<? super T, ? extends T> op);

  /**
   * If this production matches, optionally matches {@code suffix} with the {@code op} BiFunction
   * to transform the current production's result.
   *
   * <p>Note that the {@link Parser} and {@link Parser.OrEmpty} implementations are re-declared to
   * return the more specific {@code Parser<T>} or {@code Parser<T>.OrEmpty} subtypes respectively.
   */
  <S> Production<T> optionallyFollowedBy(
      Parser<S> suffix, BiFunction<? super T, ? super S, ? extends T> op);
}

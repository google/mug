package com.google.common.labs.parse;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.util.CharPredicate;

/**
 * A sealed interface that can either be an always-consuming {@link Parser}
 * or an optional {@link Parser.OrEmpty}. Useful as a parameter to {@code Parser.sequence()}
 * methods, so that we can avoid exponential number of overloads.
 *
 * @since 10.0
 */
public sealed interface Grammar<T> permits Parser, Parser.OrEmpty {

  /**
   * Parses the input string and returns the result. Upon successful return, the {@code
   * input} is fully consumed.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parse(String input);


  /**
   * Parses the input string, ignoring patterns matched by {@code skip}, and returns the
   * result.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parseSkipping(Parser<?> skip, String input);

  /**
   * Parses the input string, ignoring {@code charsToSkip}, and returns the result.
   *
   * @throws ParseException if the input cannot be parsed.
   */
  T parseSkipping(CharPredicate charsToSkip, String input);

  /** Returns true if this grammar matches {@code input}. */
  boolean matches(String input);

  /** The current grammar must be enclosed between non-empty {@code prefix} and {@code suffix}. */
  default Parser<T> between(String prefix, String suffix) {
    return between(Parser.string(prefix), Parser.string(suffix));
  }

  /** The current grammar must be enclosed between non-empty {@code prefix} and {@code suffix}. */
  default Parser<T> between(Parser<?> prefix, Parser<?> suffix) {
    return prefix.then(this).followedBy(suffix);
  }

  /**
   * The current grammar must be <em>immediately</em> enclosed between
   * non-empty {@code prefix} and {@code suffix} (no skippable characters as specified by {@link
   * #parseSkipping parseSkipping()} in between). Useful for matching a literal string, such as
   * {@code zeroOrMore(isNot('"')).immediatelyBetween("\"", "\"")}.
   */
  default Parser<T> immediatelyBetween(String prefix, String suffix) {
    return Parser.string(prefix).then(Parser.literally(followedBy(suffix)));
  }

  /**
   * After matching the current grammary (whic hmay be an optional (or zero-or-more) parser),
   * proceed to match {@code suffix}.
   */
  default <S> Parser<S> then(Parser<S> suffix) {
    return Parser.sequence(Parser.maybeZeroWidth(this), suffix, (a, b) -> b);
  }

  /** The current grammar must be followed by non-empty {@code suffix}. */
  default Parser<T> followedBy(String suffix) {
    return followedBy(Parser.string(suffix));
  }

  /** The current grammar must be followed by non-empty {@code suffix}. */
  default Parser<T> followedBy(Parser<?> suffix) {
    return Parser.sequence(Parser.maybeZeroWidth(this), suffix, (a, b) -> a);
  }
}

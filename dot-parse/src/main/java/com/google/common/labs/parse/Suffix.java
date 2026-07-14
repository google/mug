package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.string;
import static java.util.Objects.requireNonNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A convenience helper to manage multiple optional suffixes.
 *
 * <p>Usually when you have an optional suffix, you should use {@link Parser#optionallyFollowedBy(String, Function) optionallyFollowedBy()}
 * directly, such as:
 *
 * <pre>{@code
 * expr.optionallyFollowedBy("!", (Integer n) -> factorial(n));
 * }</pre>
 *
 * However when there are more than one optional suffixes to be applied after the same parser, it
 * becomes awkward to compose them in a readable way.
 *
 * <p>The following is an example using the {@code Suffix} helper and {@link Parser#anyOf(Parser...)} to compose the optional
 * suffix operators together before passing to {@code optionallyFollowedBy()}:
 *
 * <pre>{@code
 * import static com.google.common.labs.parse.Suffix.suffix;
 *
 * expr.optionallyFollowedBy(
 *     anyOf(
 *         suffix("!", (Integer n) -> factorial(n)),
 *         suffix(exponential, (Integer i, Double e) -> pow(i, e))),
 *     Suffix::apply);
 * }</pre>
 *
 * <p>Occasionally you may need to wrap the left parser's result with or without optional suffixes,
 * regardless. For example, the parsed string needs to be wrapped in either one of {@code Expr} AST
 * types as determined by the optional suffixes, or wrapped in the default {@code LiteralExpr} when
 * no suffix is present, you can use:
 *
 * <pre>{@code
 * import static com.google.common.labs.parse.Suffix.suffix;
 *
 * Parser.sequence(
 *     expr,
 *     anyOf(
 *             suffix("!", FactorialExpr::new),
 *             suffix(exponential, PowExpr::new))
 *         .orElse(LiteralExpr::new),
 *     Suffix::apply);
 * }</pre>
 *
 * Or even a single optional suffix can benefit too:
 *
 * <pre>{@code
 * import static com.google.common.labs.text.Suffix.suffix;
 *
 * Parser.sequence(
 *     expr,
 *     suffix(exponential, PowExpr::new).orElse(LiteralExpr::new),
 *     Suffix::apply);
 * }</pre>
 *
 * @since 10.7
 */
public final class Suffix {
  /**
   * A convenience method to apply a suffix to a prefix. When passed to the {@link
   * Parser#optionallyFollowedBy(Parser, BiFunction) optionallyFollowedBy()} as a method reference
   * ({@code Suffix::apply}), it reads in the intuitive encounter order.
   */
  public static <P, R> R apply(P prefix, Function<? super P, ? extends R> suffix) {
    return suffix.apply(prefix);
  }

  /**
   * A suffix parser that combines together with its prefix parse's result using the {@code combine}
   * function.
   */
  public static <P, S, R> Parser<Function<P, R>> suffix(
      Parser<S> suffix, BiFunction<? super P, ? super S, ? extends R> combiner) {
    requireNonNull(combiner);
    return suffix.map(s -> p -> combiner.apply(p, s));
  }

  /** A suffix parser that uses the {@code mapper} function to transform the prefix's result. */
  public static <P, R> Parser<Function<P, R>> suffix(
      String suffix, Function<? super P, ? extends R> mapper) {
    return string(suffix).thenReturn(mapper::apply);
  }

  static <T, S> Parser<UnaryOperator<T>> postfix(
      Parser<S> postfix, BiFunction<? super T, ? super S, ? extends T> op) {
    requireNonNull(op);
    return postfix.map(s -> p -> op.apply(p, s));
  }

  static <T> Parser<UnaryOperator<T>> postfix(String postfix, Function<? super T, ? extends T> op) {
    return string(postfix).thenReturn(op::apply);
  }

  private Suffix() {}
}

package com.google.common.labs.parse;

import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.function.ObjInt2Function;
import com.google.mu.util.Substring;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

sealed interface MatchResult<V> {
  <T> MatchResult<T> map(Function<? super V, ? extends T> function, ErrorContext context);
  <T> MatchResult<T> mapWithIndex(
      ObjInt2Function<? super V, ? extends T> function, ErrorContext context);
  MatchResult<V> suchThat(Predicate<? super V> condition, String name, ErrorContext context);

  record Success<V>(int head, int tail, V value) implements MatchResult<V> {
    @Override public <T> MatchResult<T> map(
        Function<? super V, ? extends T> function, ErrorContext context) {
      try {
        return new Success<T>(head, tail, function.apply(value));
      } catch (ParseError e) {
        return context.errorAt(head, tail, e);
      }
    }

    @Override public <T> MatchResult<T> mapWithIndex(
        ObjInt2Function<? super V, ? extends T> function, ErrorContext context) {
      try {
        return new Success<T>(head, tail, function.apply(value, head, tail));
      } catch (ParseError e) {
        return context.errorAt(head, tail, e);
      }
    }

    @Override public MatchResult<V> suchThat(
        Predicate<? super V> condition, String name, ErrorContext context) {
      try {
        return condition.test(value) ? this : context.expecting(name, head, tail);
      } catch (ParseError e) {
        return context.errorAt(head, tail, e);
      }
    }

    <B, T> MatchResult<T> and(
        Success<B> b, BiFunction<? super V, ? super B, ? extends T> function,
        ErrorContext context) {
      try {
        return new Success<T>(head, b.tail, function.apply(value, b.value));
      } catch (ParseError e) {
        return context.errorAt(head, b.tail, e);
      }
    }

    <T> MatchResult<T> andThen(
        Supplier<? extends MatchResult<? extends T>> next, ErrorContext context) {
      MatchResult<? extends T> r2;
      try {
        r2 = next.get();
      } catch (ParseError e) {
        return context.errorAt(head, tail, e);
      }
      return switch (r2) {
        case Success<? extends T> success -> new Success<>(head, success.tail, success.value);
        case Failure<?> failure -> failure.safeCast();
      };
    }
  }

  /**
   * Represents failure with an index in the source, and an error message with predefined {name} and
   * {snippet} template placeholders to be filled when throwing exception.
   */
  record Failure<V>(int at, long frontier, String messageTemplate, Object symbolName)
      implements MatchResult<V> {
    Failure(int at, String messageTemplate, Object symbolName) {
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
                case "{name}" -> String.valueOf(symbolName);
                case "{snippet}" -> new Snippet(input, at).toString();
                default -> placeholder;
              });
    }

    @Override public <T> Failure<T> map(
        Function<? super V, ? extends T> function, ErrorContext context) {
      return safeCast();
    }

    @Override public <T> Failure<T> mapWithIndex(
        ObjInt2Function<? super V, ? extends T> function, ErrorContext context) {
      return safeCast();
    }

    @Override public Failure<V> suchThat(
        Predicate<? super V> condition, String name, ErrorContext context) {
      return this;
    }
  }
}

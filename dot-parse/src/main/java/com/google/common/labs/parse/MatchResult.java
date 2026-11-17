package com.google.common.labs.parse;

import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.mu.function.ObjInt2Function;
import com.google.mu.util.Substring;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The mutable result of {@link Parser#skipAndMatch skipAndMatch()}: a success if it returned true,
 * or else a failure.
 *
 * <p>A single instance is reused by all the parsers participating in a parse, so the result is only
 * valid until the next {@code skipAndMatch()} call. Anything needed after that must be copied out
 * first.
 */
final class MatchResult {
  private int head;
  private int tail;
  private Object value;

  private int at;
  private long frontier;
  private String messageTemplate;
  private Object symbolName;

  /** Sets this result to a match of {@code [head, tail)}. Always returns true. */
  boolean succeed(int head, int tail, Object value) {
    this.head = head;
    this.tail = tail;
    this.value = value;
    return true;
  }

  /**
   * Sets this result to a failure with an index in the source, and an error message with predefined
   * {name} and {snippet} template placeholders to be filled when throwing exception. Always returns
   * false.
   *
   * <p>A new failure should go through {@link ErrorContext} instead, so that it's tracked for error
   * reporting.
   */
  boolean fail(int at, long frontier, String messageTemplate, Object symbolName) {
    this.at = at;
    this.frontier = frontier;
    this.messageTemplate = messageTemplate;
    this.symbolName = symbolName;
    return false;
  }

  int head() {
    return head;
  }

  int tail() {
    return tail;
  }

  // The same instance holds the value of every parser, so reads can't be statically typed. The
  // caller knows the type of the parser that matched.
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  <V> V value() {
    return (V) value;
  }

  int at() {
    return at;
  }

  long frontier() {
    return frontier;
  }

  String messageTemplate() {
    return messageTemplate;
  }

  Object symbolName() {
    return symbolName;
  }

  @SuppressWarnings("unchecked") // The matched value is of the function's input type.
  boolean map(Function<?, ?> function, ErrorContext context) {
    try {
      value = ((Function<Object, ?>) function).apply(value);
      return true;
    } catch (ParseError e) {
      return context.errorAt(head, tail, e, this);
    }
  }

  @SuppressWarnings("unchecked") // The matched value is of the function's input type.
  boolean mapWithIndex(ObjInt2Function<?, ?> function, ErrorContext context) {
    try {
      value = ((ObjInt2Function<Object, ?>) function).apply(value, head, tail);
      return true;
    } catch (ParseError e) {
      return context.errorAt(head, tail, e, this);
    }
  }

  @SuppressWarnings("unchecked") // The matched value is of the predicate's input type.
  boolean suchThat(Predicate<?> condition, String name, ErrorContext context) {
    try {
      return ((Predicate<Object>) condition).test(value)
          || context.expecting(name, head, tail, this);
    } catch (ParseError e) {
      return context.errorAt(head, tail, e, this);
    }
  }

  ParseException toException(CharInput input) {
    return toException(input, at, messageTemplate, symbolName);
  }

  static ParseException toException(
      CharInput input, int at, String messageTemplate, Object symbolName) {
    return new ParseException(
        at,
        String.format(
            "at %s: %s",
            input.sourcePosition(at), renderMessage(input, at, messageTemplate, symbolName)));
  }

  private static String renderMessage(
      CharInput input, int at, String messageTemplate, Object symbolName) {
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
}

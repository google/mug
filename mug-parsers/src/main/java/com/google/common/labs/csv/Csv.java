package com.google.common.labs.csv;


import static com.google.common.labs.text.parser.Parser.consecutive;
import static com.google.mu.util.CharPredicate.is;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.labs.text.parser.Parser;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.stream.BiStream;

/**
 * An easy-to-use CSV parser with lazy parsing support.
 *
 * <p>For example:
 *
 * <pre>{@code
 * import static com.google.common.labs.text.Csv.CSV;
 *
 * List<ImmutableList<String>> rows =
 *     // skip(1) to skip the header row.
 *     CSV.parse(input, toImmutableList()).skip(1).toList();
 * }</pre>
 *
 * <p>Or, if the order and the number of fields are known at compile-time, you could directly
 * combine them to build objects of your choice:
 *
 * <pre>{@code
 * import com.google.common.labs.collect.EvenMoreCollectors.combining;
 * import static com.google.common.labs.text.Csv.CSV;
 *
 * List<Result> results =
 *     // assuming no header row
 *     CSV.parse(input, combining((foo, bar, baz) -> new Result(foo, bar, baz))).toList();
 * }</pre>
 */
public final class Csv {
  /** Default CSV parser. Configurable using {@link #withComments} and {@link #withDelimiter}. */
  public static final Csv CSV = new Csv(',', /* allowsComments= */ false);

  private static final CharPredicate UNRESERVED_CHAR = CharPredicate.noneOf("\"\r\n");
  private static final Parser<?> NEW_LINE =
      Stream.of("\n", "\r\n", "\r").map(Parser::string).collect(Parser.or());
  private static final Parser<?> COMMENT =
      Parser.string("#")
          .optionallyFollowedBy(consecutive(is('\n').not(), "comment").thenReturn(identity()))
          .optionallyFollowedBy(NEW_LINE.thenReturn(identity()));
  private static final Parser<String> QUOTED =
      Parser.consecutive(is('"').not(), "quoted")
          .or(Parser.string("\"\"").thenReturn("\"")) // escaped quote
          .zeroOrMore(joining()).between("\"", "\"");

  private final char delim;
  private final boolean allowsComments;

  private Csv(char delim, boolean allowsComments) {
    this.delim = delim;
    this.allowsComments = allowsComments;
  }

  /** Returns an otherwise equivalent CSV parser but using {@code delimiter} instead of comma. */
  public Csv withDelimiter(char delimiter) {
    checkArgument(
        UNRESERVED_CHAR.and(is('#').not()).test(delimiter), "delimiter cannot be '%s'", delimiter);
    return new Csv(delimiter, allowsComments);
  }

  /**
   * Returns an otherwise equivalent CSV parser but allows comment rows.
   *
   * <p>Comments are recognized by the presence of a hash sign (#) at the beginning of a line.
   *
   * <p>Note that comments are not standard CSV specification.
   */
  public Csv withComments() {
    return new Csv(delim, /* allowsComments= */ true);
  }

  /**
   * Parses {@code csv} string lazily, returning one row at a time in a stream, with field values
   * collected by {@code rowCollector}.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   */
  public <A, R> Stream<R> parse(String csv, Collector<? super String, A, R> rowCollector) {
    var supplier = rowCollector.supplier();
    var finisher = rowCollector.finisher();
    Parser<String> unquoted = consecutive(UNRESERVED_CHAR.and(is(delim).not()), "unquoted field");
    Parser<R> line =
        csv(QUOTED.or(unquoted), rowCollector)
            .optionallyFollowedBy(NEW_LINE.thenReturn(identity()))
            .or(NEW_LINE.map(unused -> finisher.apply(supplier.get())));
    return allowsComments
        ? Parser.anyOf(COMMENT.thenReturn(null), line).parseToStream(csv).filter(Objects::nonNull)
        : line.parseToStream(csv);
  }

  /**
   * Parses {@code csv} string lazily, returning each row in an {@link ImmutableMap} keyed by the
   * field names in the header row. The first non-empty row is expected to be the header row.
   */
  public Stream<Map<String, String>> parseToMaps(String csv) {
    AtomicReference<List<String>> fieldNames = new AtomicReference<>();
    return parse(csv, toUnmodifiableList())
        .filter(row -> row.size() > 0)
        .peek(values -> fieldNames.compareAndSet(null, values))
        .skip(1)
        .map(values -> BiStream.zip(fieldNames.get(), values).toMap());
  }

  /**
   * Given the {@code field} grammar, returns a parser that recognizes a CSV row with the fields
   * delimited by comma, while allowing empty fields. That is: "," should result in two empty
   * fields, and "foo,," results in one field "foo" followed by two empty fields.
   *
   * <p>As always, completely empty string isn't a valid row hence will not show up in the result
   * stream. The {@link #parse} method will only recognize empty strings terminated by newline as
   * empty rows.
   */
  private <A, R> Parser<R> csv(Parser<String> field, Collector<? super String, A, R> rowCollector) {
    var supplier = rowCollector.supplier();
    var accumulator = rowCollector.accumulator();
    var finisher = rowCollector.finisher();
    return Parser.anyOf(field, Parser.string(String.valueOf(delim)).thenReturn(this))
        .atLeastOnce()
        .map(
            values -> {
              A buffer = supplier.get();
              for (int i = 0; i < values.size(); i++) {
                if (values.get(i) instanceof String str) {
                  accumulator.accept(buffer, str);
                  continue;
                }
                if (i == 0 || values.get(i - 1) == this) {
                  // starts with comma, or between two commas
                  accumulator.accept(buffer, "");
                }
                if (i >= values.size() - 1) { // ends with comma, add the trailing empty
                  accumulator.accept(buffer, "");
                }
              }
              return finisher.apply(buffer);
            });
  }

  @Override
  public String toString() {
    return Character.toString(delim);
  }

  private static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}

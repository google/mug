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
package com.google.common.labs.csv;


import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static java.util.Arrays.asList;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;

/**
 * An easy-to-use CSV parser with lazy parsing support and friendly error reporting.
 *
 * <p>For example:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * // skip(1) to skip the header row.
 * List<List<String>> rows = CSV.parseToLists(input).skip(1).toList();
 * }</pre>
 *
 * <p>Empty rows are ignored.
 *
 * <p>You can also use the header row, and parse each row to a {@link Map} keyed by the header
 * field names:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * List<Map<String, String>> rows = CSV.parseToMaps(input).toList();
 * }</pre>
 *
 * <p>Starting from v9.5, spaces and tabs around double quoted fields are leniently ignored.
 *
 * <p>Also starting from v9.5, to write to CSV format, use the {@link #join(Collection)} method to
 * join to CSV format:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * String csv = CSV.join(myList);
 * }</pre>
 *
 * Field values with newline, quote or comma will be automatically quoted, with literal double quote
 * characters escaped.
 *
 * <p>You can also use the {@link #joining} collector:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * String csv = Stream.of("a", "b", "c").collect(CSV.joining());
 * }</pre>
 *
 * <p>Note that streams returned by this class are sequential and are <em>not</em> safe to be used
 * as parallel streams.
 */
public final class Csv {
  /** Default CSV parser. Configurable using {@link #withComments} and {@link #withDelimiter}. */
  public static final Csv CSV = new Csv(',', /* allowsComments= */ false);

  private static final CharPredicate UNRESERVED_CHAR = CharPredicate.noneOf("\"\r\n");
  private static final Parser<?>.OrEmpty IGNORED_WHITESPACES =
      Parser.zeroOrMore(c -> c == ' ' || c == '\t', "ignored");
  private static final Parser<?> NEW_LINE =
      Stream.of("\n", "\r\n", "\r").map(Parser::string).collect(Parser.or());
  private static final Parser<?> COMMENT =
      Parser.string("#")
          .followedBy(consecutive(isNot('\n'), "comment").orElse(null))
          .followedByOrEof(NEW_LINE);
  private static final Parser<String> QUOTED =
      Parser.zeroOrMore(isNot('"'), "quoted")
          .delimitedBy("\"\"", Collectors.joining("\""))
          .between("\"", "\"")
          .between(IGNORED_WHITESPACES, IGNORED_WHITESPACES);

  private final char delim;
  private final boolean allowsComments;

  private Csv(char delim, boolean allowsComments) {
    this.delim = delim;
    this.allowsComments = allowsComments;
  }

  /** Returns an otherwise equivalent CSV parser but using {@code delimiter} instead of comma. */
  public Csv withDelimiter(char delimiter) {
    checkArgument(
        UNRESERVED_CHAR.and(isNot('#')).test(delimiter), "delimiter cannot be '%s'", delimiter);
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
   * Parses the {@code input} string into a lazy stream of immutable {@code List}, one row at a time.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   */
  public Stream<List<String>> parseToLists(String csv) {
    return parseToLists(new StringReader(csv));
  }

  /**
   * Parses the {@code input} reader into a lazy stream of immutable {@code List}, one row at a time.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   *
   * <p>Implementation note: the parser uses internal buffer so you don't need to wrap it in {@code
   * BufferedReader}.
   */
  public Stream<List<String>> parseToLists(Reader csv) {
    Parser<String> unquoted = consecutive(regularChar(), "unquoted field");
    Parser<List<String>> line =
        Parser.anyOf(
            NEW_LINE.thenReturn(List.of()),  // empty line => [], not [""]
            QUOTED.or(unquoted)
                .orElse("")
                .delimitedBy(String.valueOf(delim))
                .notEmpty()
                .followedByOrEof(NEW_LINE));
    return allowsComments
        ? line.skipping(COMMENT).parseToStream(csv).filter(row -> !row.isEmpty())
        : line.parseToStream(csv).filter(row -> !row.isEmpty());
  }

  /**
   * Parses {@code csv} string lazily, returning each row in a {@link Map} keyed by the
   * field names in the header row. The first non-empty row is expected to be the header row.
   *
   * <p>Upon duplicate header names, the latter wins. If you need alternative strategies,
   * such as to reject duplicate header names, or to use {@link com.google.common.collect.ListMultimap}
   * to keep track of all duplicate header values, consider using {@link
   * #parseWithHeaderFields(String, BiCollector)} instead. That is:
   *
   * <pre>{@code
   * import static com.google.mu.util.stream.BiCollectors.toMap;
   *
   * CSV.parse(input, toMap());  // throw upon duplicate header names
   * }</pre>
   *
   * or:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * // keep track of duplicate header names
   * CSV.parse(input, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   */
  public Stream<Map<String, String>> parseToMaps(String csv) {
    return parseToMaps(new StringReader(csv));
  }

  /**
   * Similar to {@link #parseToMaps(String)}, but takes a {@code Reader} instead.
   *
   * <p>Implementation note: the parser uses internal buffer so you don't need to wrap it in {@code
   * BufferedReader}.
   */
  public Stream<Map<String, String>> parseToMaps(Reader csv) {
    return parseWithHeaderFields(csv, toMap((v1, v2) -> v2));
  }

  /**
   * {@code CSV.parseWithHeaderFields(input, toMap())} will parse the non-header rows in the {@code
   * input} string into a lazy stream of {@code Map}, keyed by the header field names.
   *
   * <p>Usually, if you need a {@code Map} of field names to column values, consider using {@link
   * #parseToMaps(String)} instead. But if you need alternative strategies, such as collecting
   * each row to a {@link com.google.common.collect.ListMultimap} to more gracefully handle
   * duplicate header names, you can use:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * CSV.parseWithHeaderFields(input, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   */
  public <R> Stream<R> parseWithHeaderFields(
      String csv, BiCollector<? super String, ? super String, ? extends R> rowCollector) {
    return parseWithHeaderFields(new StringReader(csv), rowCollector);
  }

  /**
   * {@code CSV.parseWithHeaderFields(input, toMap())} will parse the non-header rows from the
   * {@code input} reader into a lazy stream of {@code Map}, keyed by the header field names.
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * CSV.parseWithHeaderFields(input, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   *
   * <p>Implementation note: the parser uses internal buffer so you don't need to wrap it in {@code
   * BufferedReader}.
   */
  public <R> Stream<R> parseWithHeaderFields(
      Reader csv, BiCollector<? super String, ? super String, ? extends R> rowCollector) {
    AtomicReference<List<String>> fieldNames = new AtomicReference<>();
    return parseToLists(csv)
        .filter(row -> row.size() > 0)
        .peek(values -> fieldNames.compareAndSet(null, values))
        .skip(1)
        .map(values -> BiStream.zip(fieldNames.get(), values).collect(rowCollector));
  }

  /**
   * Joins {@code fields} as a CSV row, quote if needed.
   * If a field value is null, an empty string is used.
   *
   * @since 9.5
   */
  public String join(Collection<?> fields) {
    if (fields.size() == 1) {
      for (Object field : fields) {
        String s = quoteIfNeeded(field);
        return s.isEmpty() ? "\"\"" : s; // single empty field should produce [""]
      }
      throw new IllegalStateException("malformed collection!");
    }
    StringJoiner joiner = new StringJoiner(String.valueOf(delim));
    for (Object field : fields) {
      joiner.add(quoteIfNeeded(field));
    }
    return joiner.toString();
  }

  /**
   * Joins {@code fields} as a CSV row, quote if needed.
   * If a field value is null, an empty string is used.
   *
   * @since 9.5
   */
  public String join(Object... fields) {
    return join(asList(fields));
  }

  /**
   * Returns a collector that joins the input fields into a CSV row.
   *
   * <p>Fields are quoted and optionally escaped if needed.
   * If a field value is null, an empty string is used.
   *
   * @since 9.5
   */
  public Collector<Object, ?, String> joining() {
    return Collectors.collectingAndThen(Collectors.toList(), this::join);
  }

  @Override public String toString() {
    return "Csv{delimiter='" + delim + "', allowsComments=" + allowsComments + "}";
  }

  private String quoteIfNeeded(Object field) {
    if (field == null) {
      return "";
    }
    String str = field.toString();
    if (regularChar().matchesAllOf(str)) {
      return str;
    }
    return '"' + Substring.all('"').replaceAllFrom(str, q -> "\"\"") + '"';
  }

  private CharPredicate regularChar() {
    return UNRESERVED_CHAR.and(isNot(delim));
  }

  private static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}

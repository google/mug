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
import static java.util.stream.Collectors.joining;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;

/**
 * An easy-to-use CSV parser with lazy parsing support.
 *
 * <p>For example:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * // skip(1) to skip the header row.
 * List<List<String>> rows = CSV.parse(input).skip(1).toList();
 * }</pre>
 *
 * <p>You can also use the header row, and parse each row to a {@link Map} keyed by the header
 * field names:
 *
 * <pre>{@code
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * List<Map<String, String>> rows = CSV.parseToMaps(input).toList();
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
          .followedBy(consecutive(isNot('\n'), "comment").orElse(null))
          .followedBy(NEW_LINE.orElse(null));
  private static final Parser<String> QUOTED =
      Parser.consecutive(isNot('"'), "quoted")
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
  public Stream<List<String>> parse(String csv) {
    return parse(new StringReader(csv));
  }

  /**
   * Parses the {@code input} reader into a lazy stream of immutable {@code List}, one row at a time.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   */
  public Stream<List<String>> parse(Reader csv) {
    Parser<String> unquoted = consecutive(UNRESERVED_CHAR.and(isNot(delim)), "unquoted field");
    Parser<List<String>> line =
        Parser.anyOf(
            NEW_LINE.thenReturn(List.of()),  // empty line => [], not [""]
            QUOTED.or(unquoted)
                .orElse("")
                .delimitedBy(String.valueOf(delim))
                .followedBy(NEW_LINE.orElse(null))
                .notEmpty());
    return allowsComments
        ? line.skipping(COMMENT).parseToStream(csv)
        : line.parseToStream(csv);
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
    return parse(csv)
        .filter(row -> row.size() > 0)
        .peek(values -> fieldNames.compareAndSet(null, values))
        .skip(1)
        .map(values -> BiStream.zip(fieldNames.get(), values).collect(rowCollector));
  }

  @Override public String toString() {
    return Character.toString(delim);
  }

  private static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}

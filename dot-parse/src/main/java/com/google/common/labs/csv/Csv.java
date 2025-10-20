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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.common.labs.parse.Parser;
import com.google.errorprone.annotations.MustBeClosed;
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
 * List<List<String>> rows =
 *     // skip(1) to skip the header row.
 *     CSV.parse(input, toUnmodifiableList()).skip(1).toList();
 * }</pre>
 *
 * <p>Or, if the order and the number of fields are known at compile-time, you could directly
 * combine them to build objects of your choice:
 *
 * <pre>{@code
 * import com.google.mu.util.stream.MoreCollectors.combining;
 * import static com.google.common.labs.csv.Csv.CSV;
 *
 * List<Result> results =
 *     // assuming no header row
 *     CSV.parse(input, combining((foo, bar, baz) -> new Result(foo, bar, baz))).toList();
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
  private static final Logger logger = Logger.getLogger(Csv.class.getName());
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
   * Parses {@code csv} string lazily, returning one row at a time in a stream, with field values
   * collected by {@code rowCollector}.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   */
  public <A, R> Stream<R> parse(String csv, Collector<? super String, A, R> rowCollector) {
    return parse(new StringReader(csv), rowCollector);
  }

  /**
   * Similar to {@link #parse(String, Collector)}, but takes a {@code File} instead.
   *
   * <p>The file will be opened and read in UTF-8. The caller is responsible for closing the stream
   * using try-with-resources, to close the file. For example:
   *
   * <pre>{@code
   * try (Stream<User> users = CSV.parse(csvFile, combining((id, name) -> new User(id, name)))) {
   *   ...
   * }
   * }</pre>
   *
   * <p>If you need to read from a resource URL, or need an alternative charset, consider using
   * {@link #parse(Reader, Collector)} instead by opening the {@code Reader} explicitly and closing
   * it using try-with-resources.
   *
   * @throws UncheckedIOException if there is an error opening the {@code csvFile}. The returned
   *     stream can also throw {@code UncheckedIOException} if reading fails.
   */
  @MustBeClosed
  public <A, R> Stream<R> parse(File csvFile, Collector<? super String, A, R> rowCollector) {
    return openForStreaming(csvFile, reader -> parse(reader, rowCollector));
  }

  /**
   * Parses {@code csv} reader lazily, returning one row at a time in a stream, with field values
   * collected by {@code rowCollector}.
   *
   * <p>No special treatment of the header row. If you know you have a header row, consider calling
   * {@code .skip(1)} to skip it, or use {@link #parseToMaps} with the field names as the Map keys.
   */
  public <A, R> Stream<R> parse(Reader csv, Collector<? super String, A, R> rowCollector) {
    var supplier = rowCollector.supplier();
    var finisher = rowCollector.finisher();
    Parser<String> unquoted = consecutive(UNRESERVED_CHAR.and(isNot(delim)), "unquoted field");
    Parser<R> line =
        Parser.anyOf(
            NEW_LINE.map(unused -> finisher.apply(supplier.get())),  // empty line => [], not [""]
            QUOTED.or(unquoted)
                .orElse("")
                .delimitedBy(String.valueOf(delim), rowCollector)
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
   * #parseWithHeaderFieldNames(String, BiCollector)} instead. That is:
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
   * Similar to {@link #parseToMaps(String)}, but takes a {@code File} instead.
   *
   * <p>The file will be opened and read in UTF-8. The caller is responsible for closing the stream
   * using try-with-resources, to close the file. For example:
   *
   * <pre>{@code
   * try (Stream<Map<String, String>> maps = CSV.parseToMaps(csvFile)) {
   *   maps.filter(...).map(...).forEach(...);
   * }
   * }</pre>
   *
   * <p>If you need to read from a resource URL, or need an alternative charset, consider using
   * {@link #parseToMaps(Reader)} instead by opening the {@code Reader} explicitly and closing it
   * using try-with-resources.
   *
   * @throws UncheckedIOException if there is an error opening the {@code csvFile}. The returned
   *     stream can also throw {@code UncheckedIOException} if reading fails.
   */
  @MustBeClosed
  public Stream<Map<String, String>> parseToMaps(File csvFile) {
    return parseWithHeaderFieldNames(csvFile, toMap((v1, v2) -> v2));
  }

  /**
   * Parses {@code csv} reader lazily, returning each row in a {@link Map} keyed by the
   * field names in the header row. The first non-empty row is expected to be the header row.
   *
   * <p>Upon duplicate header names, the latter wins. If you need alternative strategies,
   * such as to reject duplicate header names, or to use {@link com.google.common.collect.ListMultimap}
   * to keep track of all duplicate header values, consider using {@link
   * #parseWithHeaderFieldNames(Reader, BiCollector)} instead. That is:
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
  public Stream<Map<String, String>> parseToMaps(Reader csv) {
    return parseWithHeaderFieldNames(csv, toMap((v1, v2) -> v2));
  }

  /**
   * Parses {@code csv} string lazily, expecting the first non-empty row as the header names.
   * For each row, the field names and corresponding values are collected using {@code rowCollector}.
   *
   * <p>Usually, if you need a {@code Map} of field names to column values, consider using {@link
   * #parseToMaps(String)} instead. But if you need alternative strategies, such as collecting
   * each row to a {@link com.google.common.collect.ListMultimap} to more gracefully handle
   * duplicate header names, you can use:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * CSV.parse(input, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   */
  public <R> Stream<R> parseWithHeaderFieldNames(
      String csv, BiCollector<? super String, ? super String, ? extends R> rowCollector) {
    return parseWithHeaderFieldNames(new StringReader(csv), rowCollector);
  }

  /**
   * Similar to {@link #parseWithHeaderFieldNames(String, BiCollector)}, but takes a {@code File}
   * instead. For example:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * CSV.parse(csvFile, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   *
   * <p>The file will be opened and read in UTF-8. The caller is responsible for closing the stream
   * using try-with-resources, to close the file. For example:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * try (Stream<ImmutableListMultimap<String, String>> maps =
   *     CSV.parseWithHeaderFieldNames(csvFile, ImmutableListMultimap::toImmutableListMultimap)) {
   *   ...
   * }
   * }</pre>
   *
   * <p>If you need to read from a resource URL, or need an alternative charset, consider using
   * {@link #parseWithHeaderFieldNames(Reader, BiCollector)} instead by opening the {@code Reader}
   * explicitly and closing it using try-with-resources.
   *
   * @throws UncheckedIOException if there is an error opening the {@code csvFile}. The returned
   *     stream can also throw {@code UncheckedIOException} if reading fails.
   */
  @MustBeClosed
  public <R> Stream<R> parseWithHeaderFieldNames(
      File csvFile, BiCollector<? super String, ? super String, ? extends R> rowCollector) {
    return openForStreaming(csvFile, reader -> parseWithHeaderFieldNames(reader, rowCollector));
  }

  /**
   * Parses {@code csv} reader lazily, expecting the first non-empty row as the header names.
   * For each row, the field names and corresponding values are collected using {@code rowCollector}.
   *
   * <p>Usually, if you need a {@code Map} of field names to column values, consider using {@link
   * #parseToMaps(Reader)} instead. But if you need alternative strategies, such as collecting
   * each row to a {@link com.google.common.collect.ListMultimap} to more gracefully handle
   * duplicate header names, you can use:
   *
   * <pre>{@code
   * import static com.google.common.collect.ImmutableListMultimap;
   *
   * CSV.parse(input, ImmutableListMultimap::toImmutableListMultimap);
   * }</pre>
   */
  public <R> Stream<R> parseWithHeaderFieldNames(
      Reader csv, BiCollector<? super String, ? super String, ? extends R> rowCollector) {
    AtomicReference<List<String>> fieldNames = new AtomicReference<>();
    return parse(csv, toUnmodifiableList())
        .filter(row -> row.size() > 0)
        .peek(values -> fieldNames.compareAndSet(null, values))
        .skip(1)
        .map(values -> BiStream.zip(fieldNames.get(), values).collect(rowCollector));
  }

  @Override
  public String toString() {
    return Character.toString(delim);
  }

  @MustBeClosed
  private static <T> Stream<T> openForStreaming(
      File file, Function<? super Reader, Stream<T>> streamer) {
    try {
      Reader reader = new InputStreamReader(new FileInputStream(file), UTF_8);
      return streamer
          .apply(reader)
          .onClose(
              () -> {
                try {
                  reader.close();
                } catch (IOException e) {
                  logger.log(Level.SEVERE, "Failed to close file " + file, e);
                }
              });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }
}

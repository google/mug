package com.google.mu.benchmarks.parsers.json;

import java.io.Reader;
import java.util.stream.Stream;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;

/** Interface for JSON parsers that can lazily stream JSON records from a Reader one-by-one. */
public interface StreamingJsonParser {
  /**
   * Parses the given reader lazily, returning a stream of JSON values.
   * Individual JSON records are parsed and yielded one-by-one as the stream is consumed.
   *
   * @throws Exception if a streaming or parsing error occurs.
   */
  Stream<JsonValue> parse(Reader reader) throws Exception;
}

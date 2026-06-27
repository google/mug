package com.google.mu.benchmarks.parsers.dotparse;

import java.io.Reader;
import java.util.stream.Stream;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;
import com.google.mu.util.CharPredicate;

/** Streaming JSON parser implemented using dot-parse. */
public final class DotParseStreamingParser implements StreamingJsonParser {
  private static final CharPredicate WHITESPACE = CharPredicate.anyOf(" \t\r\n");

  @Override
  public Stream<JsonValue> parse(Reader reader) {
    return JsonParser.PARSER.skipping(WHITESPACE).parseToStream(reader);
  }
}

package com.google.mu.benchmarks.parsers.dotparse;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;
import com.google.mu.util.CharPredicate;
import java.io.Reader;
import java.util.stream.Stream;

/** Streaming JSON parser implemented using dot-parse. */
public final class DotParseStreamingParser implements StreamingJsonParser {
  private static final CharPredicate WHITESPACE = CharPredicate.anyOf(" \t\r\n");
  private final Parser<?> skipping;

  public DotParseStreamingParser() {
    this.skipping = null;
  }

  public DotParseStreamingParser(Parser<?> skipping) {
    this.skipping = skipping;
  }

  @Override public Stream<JsonValue> parse(Reader reader) {
    return skipping != null
        ? JsonParser.PARSER.skipping(skipping).parseToStream(reader)
        : JsonParser.PARSER.skipping(WHITESPACE).parseToStream(reader);
  }
}

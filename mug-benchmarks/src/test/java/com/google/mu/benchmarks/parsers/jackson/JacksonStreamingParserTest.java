package com.google.mu.benchmarks.parsers.jackson;

import com.google.mu.benchmarks.parsers.json.AbstractStreamingJsonParserTest;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

/** Tests for JacksonStreamingParser. */
public final class JacksonStreamingParserTest extends AbstractStreamingJsonParserTest {
  @Override
  protected StreamingJsonParser parser() {
    return new JacksonStreamingParser();
  }
}

package com.google.mu.benchmarks.parsers.dotparse;

import com.google.mu.benchmarks.parsers.json.AbstractStreamingJsonParserTest;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

/** Tests for DotParseStreamingParser. */
public final class DotParseStreamingParserTest extends AbstractStreamingJsonParserTest {
  @Override
  protected StreamingJsonParser parser() {
    return new DotParseStreamingParser();
  }
}

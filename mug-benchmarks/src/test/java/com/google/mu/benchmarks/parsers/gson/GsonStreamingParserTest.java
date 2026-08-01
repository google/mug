package com.google.mu.benchmarks.parsers.gson;

import com.google.mu.benchmarks.parsers.json.AbstractStreamingJsonParserTest;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

/** Tests for GsonStreamingParser. */
public final class GsonStreamingParserTest extends AbstractStreamingJsonParserTest {
  @Override
  protected StreamingJsonParser parser() {
    return new GsonStreamingParser();
  }
}

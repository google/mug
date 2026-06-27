package com.google.mu.benchmarks.parsers.javacc;

import com.google.mu.benchmarks.parsers.json.AbstractStreamingJsonParserTest;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

/** Tests for JavaccStreamingParser. */
public final class JavaccStreamingParserTest extends AbstractStreamingJsonParserTest {
  @Override
  protected StreamingJsonParser parser() {
    return new JavaccStreamingParser();
  }
}

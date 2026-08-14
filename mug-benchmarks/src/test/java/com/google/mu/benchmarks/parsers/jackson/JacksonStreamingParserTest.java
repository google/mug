package com.google.mu.benchmarks.parsers.jackson;

import com.google.mu.benchmarks.parsers.json.AbstractStreamingJsonParserTest;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for JacksonStreamingParser. */
@RunWith(JUnit4.class)
public final class JacksonStreamingParserTest extends AbstractStreamingJsonParserTest {
  @Override protected StreamingJsonParser parser() {
    return new JacksonStreamingParser();
  }
}

package com.google.mu.benchmarks.parsers.jackson;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/** Streaming JSON parser implemented using Jackson. */
public final class JacksonStreamingParser implements StreamingJsonParser {
  private static final ObjectMapper MAPPER = new ObjectMapper().enable(Feature.ALLOW_COMMENTS);

  @Override public Stream<JsonValue> parse(Reader reader) throws Exception {
    MappingIterator<JsonNode> iterator =
        MAPPER.readValues(MAPPER.createParser(reader), JsonNode.class);

    return whileNotNull(() -> {
      try {
        if (!iterator.hasNextValue()) {
          return null;
        }
        return JacksonJsonParser.toJsonValue(iterator.nextValue());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }
}

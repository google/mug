package com.google.mu.benchmarks.parsers.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MappingIterator;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;

/** Streaming JSON parser implemented using Jackson. */
public final class JacksonStreamingParser implements StreamingJsonParser {
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Stream<JsonValue> parse(Reader reader) {
    try {
      MappingIterator<JsonNode> iterator = mapper.readValues(
          mapper.getFactory().createParser(reader), JsonNode.class);
      return whileNotNull(() -> iterator.hasNext() ? toJsonValue(iterator.next()) : null);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonValue toJsonValue(JsonNode node) {
    if (node.isNull()) {
      return JsonNull.INSTANCE;
    }
    if (node.isBoolean()) {
      return node.asBoolean() ? JsonBoolean.TRUE : JsonBoolean.FALSE;
    }
    if (node.isNumber()) {
      return new JsonNumber(node.asDouble());
    }
    if (node.isTextual()) {
      return new JsonString(node.asText());
    }
    if (node.isArray()) {
      List<JsonValue> list = new ArrayList<>(node.size());
      for (JsonNode el : node) {
        list.add(toJsonValue(el));
      }
      return new JsonArray(list);
    }
    if (node.isObject()) {
      Map<String, JsonValue> map = new LinkedHashMap<>();
      var fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        map.put(field.getKey(), toJsonValue(field.getValue()));
      }
      return new JsonObject(map);
    }
    throw new IllegalArgumentException("Unsupported Jackson node: " + node);
  }
}

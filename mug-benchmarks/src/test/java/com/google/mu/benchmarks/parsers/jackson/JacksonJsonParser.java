package com.google.mu.benchmarks.parsers.jackson;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JacksonJsonParser {
  private static final ObjectMapper MAPPER = new ObjectMapper().enable(Feature.ALLOW_COMMENTS);

  public static JsonValue parse(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      return toJsonValue(node);
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
      for (JsonNode element : node) {
        list.add(toJsonValue(element));
      }
      return new JsonArray(list);
    }
    if (node.isObject()) {
      Map<String, JsonValue> map = new LinkedHashMap<>();
      var fields = node.fields();
      while (fields.hasNext()) {
        var entry = fields.next();
        map.put(entry.getKey(), toJsonValue(entry.getValue()));
      }
      return new JsonObject(map);
    }
    throw new IllegalArgumentException("Unsupported Jackson node: " + node);
  }
}

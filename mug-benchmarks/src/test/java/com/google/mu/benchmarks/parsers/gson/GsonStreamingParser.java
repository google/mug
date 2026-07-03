package com.google.mu.benchmarks.parsers.gson;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue;
import com.google.mu.benchmarks.parsers.dotparse.JsonValue.*;
import com.google.mu.benchmarks.parsers.json.StreamingJsonParser;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;

/** Streaming JSON parser implemented using Gson. */
public final class GsonStreamingParser implements StreamingJsonParser {
  private static final Gson GSON = new Gson();

  @Override
  public Stream<JsonValue> parse(Reader reader) {
    JsonReader jsonReader = new JsonReader(reader);
    jsonReader.setLenient(true);

    return whileNotNull(() -> {
      try {
        if (jsonReader.peek() == JsonToken.END_DOCUMENT) {
          return null;
        }
        JsonElement element = GSON.fromJson(jsonReader, JsonElement.class);
        return toJsonValue(element);
      } catch (EOFException e) {
        return null;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private static JsonValue toJsonValue(JsonElement element) {
    if (element.isJsonNull()) {
      return JsonNull.INSTANCE;
    }
    if (element.isJsonPrimitive()) {
      var prim = element.getAsJsonPrimitive();
      if (prim.isBoolean()) {
        return prim.getAsBoolean() ? JsonBoolean.TRUE : JsonBoolean.FALSE;
      }
      if (prim.isNumber()) {
        return new JsonNumber(prim.getAsDouble());
      }
      if (prim.isString()) {
        return new JsonString(prim.getAsString());
      }
    }
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      List<JsonValue> list = new ArrayList<>(array.size());
      for (JsonElement el : array) {
        list.add(toJsonValue(el));
      }
      return new JsonValue.JsonArray(list);
    }
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      Map<String, JsonValue> map = new LinkedHashMap<>();
      for (var entry : obj.entrySet()) {
        map.put(entry.getKey(), toJsonValue(entry.getValue()));
      }
      return new JsonValue.JsonObject(map);
    }
    throw new IllegalArgumentException("Unsupported Gson element: " + element);
  }
}

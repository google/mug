package com.google.mu.benchmarks.parsers.dotparse;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/** Represents a standard RFC 8259 JSON value. */
public sealed interface JsonValue {

  /** Returns a stream of elements if this is a JSON array. */
  default Stream<JsonValue> stream() {
    throw new UnsupportedOperationException("Not a JSON array: " + this);
  }

  /** Returns a stream of member entries if this is a JSON object. */
  default Stream<Entry<String, JsonValue>> entryStream() {
    throw new UnsupportedOperationException("Not a JSON object: " + this);
  }

  enum JsonNull implements JsonValue {
    INSTANCE;

    @Override
    public String toString() {
      return "null";
    }
  }

  enum JsonBoolean implements JsonValue {
    TRUE(true),
    FALSE(false);

    private final boolean value;

    JsonBoolean(boolean value) {
      this.value = value;
    }

    public boolean value() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  record JsonNumber(double value) implements JsonValue {
    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  record JsonString(String value) implements JsonValue {
    @Override
    public String toString() {
      return "\"" + value + "\"";
    }
  }

  record JsonArray(List<JsonValue> elements) implements JsonValue {
    @Override
    public Stream<JsonValue> stream() {
      return elements.stream();
    }

    @Override
    public String toString() {
      return elements.toString();
    }
  }

  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    @Override
    public Stream<Entry<String, JsonValue>> entryStream() {
      return members.entrySet().stream();
    }

    @Override
    public String toString() {
      return members.toString();
    }
  }
}

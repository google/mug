package com.google.mu.protobuf.util;

import java.util.stream.Collector;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Value;

// Wouldn't have needed it if we don't mind the "protobuf-util" dependency.
final class MoreValues {
  static final Value NULL =
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
  private static final Value FALSE_VALUE = Value.newBuilder().setBoolValue(false).build();
  private static final Value TRUE_VALUE = Value.newBuilder().setBoolValue(true).build();

  static Value valueOf(double n) {
    return Value.newBuilder().setNumberValue(n).build();
  }

  static Value valueOf(boolean b) {
    return b ? TRUE_VALUE : FALSE_VALUE;
  }

  static Value valueOf(ListValue v) {
    return Value.newBuilder().setListValue(v).build();
  }

  static Collector<Value, ListValue.Builder, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        ListValue.Builder::addValues,
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }
}

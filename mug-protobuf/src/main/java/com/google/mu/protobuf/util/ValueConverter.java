package com.google.mu.protobuf.util;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.Table;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * A converter that can convert a Pojo to {@link Value} message, and recursively,
 * the {@code Collection}s, {@code Map}s and {@code Table}s thereof into corresponding
 * {@link ListValue} or {@link Struct} wrappers.
 *
 * <p>Users can implement custom conversion logic while delegating to {@link MoreStructs} for
 * the standard supported types.
 *
 * @since 5.8
 */
@FunctionalInterface
public interface ValueConverter extends Function<Object, Value> {

  /** Turns {@code map} into Struct. */
  default Struct struct(Map<? extends CharSequence, ?> map) {
    return BiStream.from(map).collect(toStruct());
  }

  /** Turns {@code table} into a nested Struct of Struct. */
  default Struct nestedStruct(Table<? extends CharSequence, ? extends CharSequence, ?> table) {
    return struct(table.rowMap());
  }

  /**
   * Returns a {@link Collector} that accumulates elements into a {@link Struct} whose keys and
   * values are the result of applying the provided mapping functions to the input elements.
   *
   * <p>Duplicate keys (according to {@link Object#equals(Object)}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values are represented with {@link NullValue}.
   */
  default <T> Collector<T, ?, Struct> toStruct(
      Function<? super T, ? extends CharSequence> toKey, Function<? super T, ?> toValue) {
    return collectingAndThen(
        toImmutableMap(toKey.andThen(CharSequence::toString), toValue.andThen(this)),
        fields -> Struct.newBuilder().putAllFields(fields).build());
  }

  /**
   * Returns a {@link BiCollector} that accumulates the name-value pairs into a {@link Struct} with
   * the values converted using {@link #toValue}.
   *
   * <p>Duplicate keys (according to {@link Object#equals(Object)}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values will be represented with {@link NullValue}.
   *
   * <p>Can also be used to create Struct literals conveniently, such as:
   *
   * <pre>{@code
   * BiStream.of("foo", 1. "bar", true).collect(converter.toStruct());
   * }</pre>
   */
  default BiCollector<CharSequence, Object, Struct> toStruct() {
    return this::toStruct;
  }

  /**
   * Returns a {@link Collector} that converts and accumulates the input objects into a {@link
   * ListValue}.
   */
  default Collector<Object, ListValue.Builder, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        (builder, v) -> builder.addValues(apply(v)),
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }
}

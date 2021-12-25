package com.google.mu.protobuf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
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
 * <p>For simple scenarios, prefer to use {@link MoreStructs} to create Struct,
 * for it's more convenient to use and static-import friendly.
 *
 * <p>This class can be used to implement custom conversion logic. For example, if the application
 * needs to convert {@code User} types to {@code Value} by using the user ids:
 *
 * <pre>{@code
 * ValueConverter customConverter = new ValueConverter() {
 *   protected Value convert(Object obj) {
 *     if (obj instanceof User) {  // custom logic
 *       return super.convertToValue(((User) obj).getId());
 *     }
 *     return super.convert(obj);  // else delegate to default implementation
 *   }
 * };
 * Struct userStruct = BiStream.of("user1", user1).collect(customConverter::toStruct);
 * }</pre>
 *
 * @since 5.8
 */
public class ValueConverter {
  private static final Value NULL_VALUE =
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
  private static final Value FALSE_VALUE = Value.newBuilder().setBoolValue(false).build();
  private static final Value TRUE_VALUE = Value.newBuilder().setBoolValue(true).build();

  /** Converts {@code object} to {@code Value}. */
  public final Value toValue(Object object) {
    return checkNotNull(
        convertRecursively(object), "Cannot convert to null. Consider converting to NullValue instead.");
  }

  /**
   * Returns a {@link Collector} that converts and accumulates the input objects into a {@link
   * ListValue}.
   */
  public final Collector<Object, ListValue.Builder, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        (builder, v) -> builder.addValues(toValue(v)),
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into a {@link Struct} whose keys and
   * values are the result of applying the provided mapping functions to the input elements.
   *
   * <p>Duplicate keys (according to {@link Object#equals(Object)}) are not allowed.
   *
   * <p>Null keys are not allowed.
   */
  public final <T> Collector<T, ?, Struct> toStruct(
      Function<? super T, ? extends CharSequence> keyFunction, Function<? super T, ?> valueFunction) {
    return collectingAndThen(
        toImmutableMap(keyFunction.andThen(CharSequence::toString), valueFunction.andThen(this::toValue)),
        fields -> Struct.newBuilder().putAllFields(fields).build());
  }

  /** Converts {@code object} to {@code Value}. Must not return null. */
  protected Value convert(Object object) {
    if (object == null || object instanceof NullValue) {
      return NULL_VALUE;
    }
    if (object instanceof Boolean) {
      return ((Boolean) object) ? TRUE_VALUE : FALSE_VALUE;
    }
    if (object instanceof Number) {
      return Value.newBuilder().setNumberValue(((Number) object).doubleValue()).build();
    }
    if (object instanceof CharSequence) {
      return Value.newBuilder().setStringValue(object.toString()).build();
    }
    if (object instanceof Value) {
      return (Value) object;
    }
    if (object instanceof Struct) {
      return Value.newBuilder().setStructValue((Struct) object).build();
    }
    if (object instanceof ListValue) {
      return Value.newBuilder().setListValue((ListValue) object).build();
    }
    throw new IllegalArgumentException("Unsupported type: " + object.getClass().getName());
  }

  /**
   * If {@code object} is of type {@link Iterable}, {@link Map}, {@link Multimap}
   * or {@link Table}, apply this converter recursively and wrap the converted elements
   * into {@code ListValue} or {@code Struct}. Otherwise delegates to {@link #convert}.
   *
   * <p>Subclasses can override this method to convert custom recursive types.
   * Must not return null.
   */
  protected Value convertRecursively(Object object) {
    if (object instanceof Optional) {
      return toValue(((Optional<?>) object).orElse(null));
    }
    if (object instanceof Iterable) {
      return Value.newBuilder()
          .setListValue(stream((Iterable<?>) object).collect(toListValue()))
          .build();
    }
    if (object instanceof Map) {
      return toStructValue((Map<?, ?>) object);
    }
    if (object instanceof Multimap) {
      return toStructValue(((Multimap<?, ?>) object).asMap());
    }
    if (object instanceof Table) {
      return toStructValue(((Table<?, ?, ?>) object).rowMap());
    }
    return convert(object);
  }

  private Value toStructValue(Map<?, ?> map) {
    Struct struct = BiStream.from(map)
        .mapKeys(ValueConverter::toStructKey)
        .collect(this::toStruct);
    return Value.newBuilder().setStructValue(struct).build();
  }

  private static String toStructKey(Object key) {
    checkNotNull(key, "Struct key cannot be null");
    checkArgument(
        key instanceof CharSequence, "Unsupported struct key type: %s", key.getClass().getName());
    return key.toString();
  }
}

package com.google.mu.protobuf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.IntStream;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableDoubleArray;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.primitives.ImmutableLongArray;
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
 * for it's more convenient and static-import friendly.
 *
 * <p>This class can be used to implement custom conversion logic. For example, if the application
 * needs to convert {@code User} types to {@code Value} by using the user ids:
 *
 * <pre>{@code
 * ValueConverter customConverter = new ValueConverter() {
 *   public Value convert(Object obj) {
 *     if (obj instanceof User) {  // custom logic
 *       return convert(((User) obj).getId());
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

  /**
   * Converts {@code object} to {@code Value}. Must not return null.
   *
   * <p>Supported types: <ul>
   * <li>Primitive types (boolean, number, string)
   * <li>{@code null} converted to {@link NullValue}
   * <li>{@link Iterable} and array elements recursively converted and wrapped in {@link ListValue}x
   * <li>{@link Map} values recursively converted and wrapped in {@link Struct}
   * <li>{@link Multimap} converted as {@code convert(multimap.asMap())}
   * <li>{@link Table} converted as {@code convert(table.rowMap())}
   * <li>{@link Optional} converted as {@code convert(optional.orElse(null))}
   * <li>{@link ImmutableIntArray}, {@link ImmutableLongArray} and {@link ImmutableDoubleArray}
   *     elements wrapped in {@link ListValue}
   * <li>Built-in protobuf types ({@link Struct}, {@link Value}, {@link ListValue}, {@link NullValue})
   * </ul>
   */
  public Value convert(Object object) {
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
    if (object instanceof Optional) {
      return convertNonNull(((Optional<?>) object).orElse(null));
    }
    if (object instanceof int[]) {
      return Arrays.stream((int[]) object)
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableIntArray) {
      return ((ImmutableIntArray) object).stream()
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof long[]) {
      return Arrays.stream((long[]) object)
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableLongArray) {
      return ((ImmutableLongArray) object).stream()
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof double[]) {
      return Arrays.stream((double[]) object)
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableDoubleArray) {
      return ((ImmutableDoubleArray) object).stream()
          .mapToObj(ValueConverter::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof Object[]) {
      return convert(Arrays.asList((Object[]) object));
    }
    if (object instanceof byte[]) {
      byte[] array = (byte[]) object;
      return IntStream.range(0, array.length)
          .mapToObj(i -> valueOf(array[i]))
          .collect(valuesToValue());
    }
    if (object instanceof short[]) {
      short[] array = (short[]) object;
      return IntStream.range(0, array.length)
          .mapToObj(i -> valueOf(array[i]))
          .collect(valuesToValue());
    }
    return defaultValue(object);
  }

  /**
   * Called by {@code #convert} when {@code object} cannot be converted. Subclasses can override
   * this method to throw a different exception type, or to return a catch-all default {@code Value}.
   *
   * @throws IllegalArgumentException to report that the type of {@code object} isn't supported
   */
  protected Value defaultValue(Object object) {
    throw new IllegalArgumentException("Unsupported type: " + object.getClass().getName());
  }

  /**
   * Returns a {@link Collector} that converts and accumulates the input objects into a {@link
   * ListValue}.
   */
  public final Collector<Object, ListValue.Builder, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        (builder, v) -> builder.addValues(convertNonNull(v)),
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
        toImmutableMap(
            keyFunction.andThen(CharSequence::toString),
            valueFunction.andThen(this::convertNonNull)),
        fields -> Struct.newBuilder().putAllFields(fields).build());
  }

  private Value convertNonNull(Object object) {
    return checkNotNull(
        convert(object), "Cannot convert to null. Consider converting to NullValue instead.");
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

  private static Collector<Value, ?, Value> valuesToValue() {
    return Collector.of(
        ListValue::newBuilder,
        ListValue.Builder::addValues,
        (a, b) -> a.addAllValues(b.getValuesList()),
        b -> Value.newBuilder().setListValue(b).build());
  }

  private static Value valueOf(double n) {
    return Value.newBuilder().setNumberValue(n).build();
  }
}

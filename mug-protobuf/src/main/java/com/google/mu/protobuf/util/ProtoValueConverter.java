/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.protobuf.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static com.google.mu.protobuf.util.MoreValues.NULL;
import static com.google.mu.protobuf.util.MoreValues.valueOf;
import static com.google.mu.protobuf.util.StructBuilder.toStruct;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.IntStream;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableDoubleArray;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.primitives.ImmutableLongArray;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * A converter that converts a POJO to protobuf {@link Value} message, and recursively,
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
 * ProtoValueConverter customConverter = new ProtoValueConverter() {
 *   public Value convert(Object obj) {
 *     if (obj instanceof User) {  // custom logic
 *       return convert(((User) obj).getId());
 *     }
 *     return super.convert(obj);  // else delegate to default implementation
 *   }
 * };
 * Struct userStruct = BiStream.of("user1", user1).collect(customConverter::convertingToStruct);
 * }</pre>
 *
 * @since 5.8
 */
public class ProtoValueConverter {
  /**
   * Converts {@code object} to {@code Value}. Must not return null.
   *
   * <p>Supported types: <ul>
   * <li>Primitive types (boolean, number, string)
   * <li>{@code null} converted to {@link NullValue}
   * <li>Enum encoded by {@link Enum#name name}
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
      return NULL;
    }
    if (object instanceof Boolean) {
      return valueOf((Boolean) object);
    }
    if (object instanceof Number) {
      return valueOf(((Number) object).doubleValue());
    }
    if (object instanceof CharSequence) {
      return valueOf(object.toString());
    }
    if (object instanceof Value) {
      return (Value) object;
    }
    if (object instanceof Struct) {
      return valueOf((Struct) object);
    }
    if (object instanceof ListValue) {
      return valueOf((ListValue) object);
    }
    if (object instanceof Iterable) {
      return valueOf(
          stream((Iterable<?>) object).map(this::convertNonNull).collect(MoreValues.toListValue()));
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
    if (object instanceof Enum) {
      return valueOf((((Enum<?>) object).name()));
    }
    if (object instanceof int[]) {
      return stream((int[]) object)
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableIntArray) {
      return ((ImmutableIntArray) object).stream()
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof long[]) {
      return stream((long[]) object)
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableLongArray) {
      return ((ImmutableLongArray) object).stream()
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof double[]) {
      return stream((double[]) object)
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableDoubleArray) {
      return ((ImmutableDoubleArray) object).stream()
          .mapToObj(MoreValues::valueOf)
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
   * Called by {@link #convert} when {@code object} cannot be converted. Subclasses can override
   * this method to throw a different exception type, or to return a catch-all default {@code Value}.
   *
   * @throws IllegalArgumentException to report that the type of {@code object} isn't supported
   */
  protected Value defaultValue(Object object) {
    throw new IllegalArgumentException("Unsupported type: " + object.getClass().getName());
  }

  private Value convertNonNull(Object object) {
    return checkNotNull(
        convert(object), "Cannot convert to null. Consider converting to NullValue instead.");
  }

  private Value toStructValue(Map<?, ?> map) {
    return valueOf(
        map.entrySet().stream()
            .collect(toStruct(e -> toStructKey(e.getKey()), e-> convertNonNull(e.getValue()))));
  }

  private static String toStructKey(Object key) {
    checkNotNull(key, "Struct key cannot be null");
    checkArgument(
        key instanceof CharSequence, "Unsupported struct key type: %s", key.getClass().getName());
    return key.toString();
  }

  private static Collector<Value, ?, Value> valuesToValue() {
    return collectingAndThen(MoreValues.toListValue(), MoreValues::valueOf);
  }
}

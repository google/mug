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
import static com.google.mu.protobuf.util.MoreValues.NULL;
import static com.google.mu.protobuf.util.MoreValues.toListValue;
import static com.google.mu.protobuf.util.MoreValues.valueOf;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.collectingAndThen;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.IntStream;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.primitives.ImmutableDoubleArray;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.primitives.ImmutableLongArray;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.annotations.RequiresProtobuf;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * A helper that makes {@link Struct}s and {@link Value}s from POJOs. Useful for
 * converting Json data to {@code Struct}. For example:
 *
 * <pre>{@code
 * Map<String, ?> jsonData = ...;
 * Struct struct = new Structor().struct(jsonData);
 * }</pre>
 *
 * <p>It can also be used to create a heterogeneous {@code Struct} literal like:
 *
 * <pre>{@code
 * Struct ironMan = new Structor()
 *     .struct(
 *         "name", "Tony Stark",
 *         "age", 10,
 *         "known_as", List.of("Iron Man", "Genius"));
 * }</pre>
 *
 * <p>Or if your structs have many more fields, consider to use the {@link #toStruct}
 * {@code BiCollector}, as in:
 *
 * <pre>{@code
 * BiStream.of("k1", 1, "k2", 2, "k3", 3, "k4", 4, ...)
 *     .collect(new Structor().toStruct());
 * }</pre>
 *
 * <p>For simple scenarios, prefer to use {@link MoreStructs} to create {@code Struct}.
 * Its single-field {@code struct()} factory methods are more efficient, can be static imported,
 * and unsupported types cause compilation error as opposed to runtime exception.
 *
 * <p>The {@link #toValue} method is responsible for converting POJO to {@code Value},
 * and recursively, the {@code Collection}s, {@code Map}s and {@code Table}s thereof into
 * corresponding {@link ListValue} or {@link Struct} wrappers.
 *
 * <p>You can create a subclass to implement custom mapping. For example,
 * if the application needs to map {@code User} types to {@code Value} by using the user ids:
 *
 * <pre>{@code
 * Structor customStructor = new Structor() {
 *   public Value toValue(Object obj) {
 *     if (obj instanceof User) {  // custom logic
 *       return toValue(((User) obj).getId());
 *     }
 *     return super.toValue(obj);  // else delegate to default implementation
 *   }
 * };
 * }</pre>
 *
 * This custom mapping logic will be applied recursively to {@code Iterable} elements,
 * {@code Map} keys/values, and all other supported collection types.
 *
 * @since 5.8
 */
@CheckReturnValue
@RequiresProtobuf
public class Structor {

  /**
   * Returns a Struct with {@code name} and {@code value}, with {@code value} converted using
   * {@link #toValue}. In particular, null is mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if {@code value} cannot be converted
   * @throws NullPointerException if {@code name} is null
   */
  public final Struct struct(CharSequence name, @Nullable Object value) {
    return Struct.newBuilder().putFields(name.toString(), convertNonNull(value)).build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   *    or if either value cannot be converted
   * @throws NullPointerException if either key is null
   */
  public final Struct struct(
      CharSequence k1, @Nullable Object v1, CharSequence k2, @Nullable Object v2) {
    return new StructBuilder()
        .add(k1.toString(), convertNonNull(v1))
        .add(k2.toString(), convertNonNull(v2))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public final Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3) {
    return new StructBuilder()
        .add(k1.toString(), convertNonNull(v1))
        .add(k2.toString(), convertNonNull(v2))
        .add(k3.toString(), convertNonNull(v3))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   * @since 5.9
   */
  public final Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4) {
    return new StructBuilder()
        .add(k1.toString(), convertNonNull(v1))
        .add(k2.toString(), convertNonNull(v2))
        .add(k3.toString(), convertNonNull(v3))
        .add(k4.toString(), convertNonNull(v4))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   * @since 5.9
   */
  public final Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5) {
    return new StructBuilder()
        .add(k1.toString(), convertNonNull(v1))
        .add(k2.toString(), convertNonNull(v2))
        .add(k3.toString(), convertNonNull(v3))
        .add(k4.toString(), convertNonNull(v4))
        .add(k5.toString(), convertNonNull(v5))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   * @since 5.9
   */
  public final Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6) {
    return new StructBuilder()
        .add(k1.toString(), convertNonNull(v1))
        .add(k2.toString(), convertNonNull(v2))
        .add(k3.toString(), convertNonNull(v3))
        .add(k4.toString(), convertNonNull(v4))
        .add(k5.toString(), convertNonNull(v5))
        .add(k6.toString(), convertNonNull(v6))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code map}.
   *
   * <p>Values are converted using {@link #toValue}. In particular, null values are mapped to
   * {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if a Map value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public final Struct struct(Map<String, ?> map) {
    return BiStream.from(map)
        .mapValues(this::convertNonNull)
        .collect(Struct.newBuilder(), Struct.Builder::putFields)
        .build();
  }

  /**
   * Returns a nested Struct of Struct equivalent to the {@link Table#rowMap row map} of {@code table}.
   *
   *
   * <p>Values are converted using {@link #toValue}. In particular, null values are mapped to
   * {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if a Table cell value cannot be converted
   * @throws NullPointerException if any row key or column key is null
   */
  public final Struct nestedStruct(Table<String, String, ?> table) {
    return BiStream.from(table.rowMap())
        .mapValues(cols -> valueOf(struct(cols)))
        .collect(Struct.newBuilder(), Struct.Builder::putFields)
        .build();
  }

  /**
   * Returns a {@link BiCollector} that accumulates the name-value pairs into a {@link Struct} with
   * the values converted using {@link #toValue}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values will be mapped to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link MoreStructs} or build Struct
   * manually with {@link StructBuilder}.
   */
  public final BiCollector<CharSequence, Object, Struct> toStruct() {
    return this::toStruct;
  }

  /**
   * Converts {@code object} to {@code Value}. Must not return null.
   *
   * <p>Supported types: <ul>
   * <li>Primitive types (boolean, number, string)
   * <li>{@code null} mapped to {@link NullValue}
   * <li>Enum mapped to {@link Enum#name name}
   * <li>{@link Iterable} and array elements recursively converted and wrapped in {@link ListValue}
   * <li>{@link Map} values recursively converted and wrapped in {@link Struct}
   * <li>{@link Multimap} converted as {@code toValue(multimap.asMap())}
   * <li>{@link Table} converted as {@code toValue(table.rowMap())}
   * <li>{@link Optional} converted as {@code toValue(optional.orElse(null))}
   * <li>{@link ImmutableIntArray}, {@link ImmutableLongArray} and {@link ImmutableDoubleArray}
   *     elements wrapped in {@link ListValue}
   * <li>Built-in protobuf types ({@link Struct}, {@link Value}, {@link ListValue}, {@link NullValue})
   * </ul>
   */
  public Value toValue(@Nullable Object object) {
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
      ListValue.Builder builder = ListValue.newBuilder();
      for (Object element : ((Iterable<?>) object)) {
        builder.addValues(convertNonNull(element));
      }
      return valueOf(builder.build());
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
      // TODO: use ImmutableIntArray.stream() when it's available in Android
     ImmutableIntArray array = (ImmutableIntArray) object;
     return IntStream.range(0, array.length())
          .mapToObj(i -> valueOf(array.get(i)))
          .collect(valuesToValue());
    }
    if (object instanceof long[]) {
      return stream((long[]) object)
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableLongArray) {
      // TODO: use ImmutableLongArray.stream() when it's available in Android
      ImmutableLongArray array = (ImmutableLongArray) object;
      return IntStream.range(0, array.length())
          .mapToObj(i -> valueOf(array.get(i)))
          .collect(valuesToValue());
    }
    if (object instanceof double[]) {
      return stream((double[]) object)
          .mapToObj(MoreValues::valueOf)
          .collect(valuesToValue());
    }
    if (object instanceof ImmutableDoubleArray) {
      // TODO: use ImmutableDoubleArray.stream() when it's available in Android
      ImmutableDoubleArray array = (ImmutableDoubleArray) object;
      return IntStream.range(0, array.length())
          .mapToObj(i -> valueOf(array.get(i)))
        .collect(valuesToValue());
    }
    if (object instanceof Object[]) {
      return Arrays.stream((Object[]) object)
          .map(this::convertNonNull)
          .collect(valuesToValue());
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
   * Called by {@link #toValue} when {@code object} cannot be converted. Subclasses can override
   * this method to throw a different exception type, or to return a catch-all default {@code Value}.
   *
   * @throws IllegalArgumentException to report that the type of {@code object} isn't supported
   */
  protected Value defaultValue(Object object) {
    throw new IllegalArgumentException("Unsupported type: " + object.getClass().getName());
  }

  private <T> Collector<T, ?, Struct> toStruct(
      Function<T, CharSequence> keyFunction, Function<T, ?> valueFunction) {
    return MoreStructs.toStruct(keyFunction, valueFunction.andThen(this::convertNonNull));
  }

  private Value convertNonNull(@Nullable Object object) {
    return checkNotNull(
        toValue(object), "Cannot convert to null. Consider converting to NullValue instead.");
  }

  private Value toStructValue(Map<?, ?> map) {
    return valueOf(
        BiStream.from(map)
            .mapKeys(Structor::toStructKey)
            .mapValues(this::convertNonNull)
            .collect(toStruct()));
  }

  private static String toStructKey(Object key) {
    checkNotNull(key, "Struct key cannot be null");
    checkArgument(
        key instanceof CharSequence, "Unsupported struct key type: %s", key.getClass().getName());
    return key.toString();
  }

  private static Collector<Value, ?, Value> valuesToValue() {
    return collectingAndThen(toListValue(), MoreValues::valueOf);
  }
}

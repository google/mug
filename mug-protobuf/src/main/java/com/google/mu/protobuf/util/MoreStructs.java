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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiCollectors;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Additional utilities to help create {@link Struct} messages.
 *
 * <p>Unlike {@link com.google.protobuf.util.Structs}, struct values of all supported types
 * (numeric, string, boolean, null) and the {@code Collection}s, {@code Map}s, {@code Table}s,
 * {@code Optional}s thereof are automatically converted to their corresponding {@link Value}
 * wrappers using {@link ProtoValueConverter}. This allows users to conveniently create {@link Struct}
 * whose fields are often heterogeneous maps of dynamically typed values. For example:
 *
 * <pre>{@code
 * Struct ironMan =
 *     struct("name", "Tony Stark", "age", 40, "talents", List.of("genius", "billionare"));
 * }</pre>
 *
 * <p>The {@code struct()} factory methods will throw {@link IllegalArgumentException} if duplicate keys
 * are provided. This is consistent with {@link Map#of} and {@link ImmutableMap#of},
 * and different from {@link com.google.protobuf.util.Structs} that leaves it as
 * "undefined behavior".
 *
 * <p>Occasionally, an application may need custom logic to convert a domain-specific type into
 * {@code Value}, and then recursively into {@code ListValue} and/or {@code Struct}. This can be
 * done by overriding {@link ProtoValueConverter#convert}.
 *
 * <p>To build {@link Struct} without risk of runtime {@link Value} conversion error caused by
 * unsupported types, use {@link #toStruct()} BiCollector on a BiStream, or build it
 * manually with {@link StructBuilder} instead.
 *
 * @since 5.8
 */
@CheckReturnValue
public final class MoreStructs {
  private static final ProtoValueConverter CONVERTER = new ProtoValueConverter();

  /**
   * Returns a Struct with {@code key} and {@code value}. Null {@code value} is translated to {@link NullValue}.
   *
   * @throws IllegalArgumentException if {@code value} cannot be converted
   * @throws NullPointerException if {@code key} is null
   */
  public static Struct struct(CharSequence key, @Nullable Object value) {
    return Struct.newBuilder().putFields(key.toString(), CONVERTER.convert(value)).build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   *    or if either value cannot be converted
   * @throws NullPointerException if either key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1, CharSequence k2, @Nullable Object v2) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .add(k6.toString(), CONVERTER.convert(v6))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6,
      CharSequence k7, @Nullable Object v7) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .add(k6.toString(), CONVERTER.convert(v6))
        .add(k7.toString(), CONVERTER.convert(v7))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6,
      CharSequence k7, @Nullable Object v7,
      CharSequence k8, @Nullable Object v8) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .add(k6.toString(), CONVERTER.convert(v6))
        .add(k7.toString(), CONVERTER.convert(v7))
        .add(k8.toString(), CONVERTER.convert(v8))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8, k9:v9}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6,
      CharSequence k7, @Nullable Object v7,
      CharSequence k8, @Nullable Object v8,
      CharSequence k9, @Nullable Object v9) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .add(k6.toString(), CONVERTER.convert(v6))
        .add(k7.toString(), CONVERTER.convert(v7))
        .add(k8.toString(), CONVERTER.convert(v8))
        .add(k9.toString(), CONVERTER.convert(v9))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8, k9:v9, k10:v10}}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided or if a value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, @Nullable Object v1,
      CharSequence k2, @Nullable Object v2,
      CharSequence k3, @Nullable Object v3,
      CharSequence k4, @Nullable Object v4,
      CharSequence k5, @Nullable Object v5,
      CharSequence k6, @Nullable Object v6,
      CharSequence k7, @Nullable Object v7,
      CharSequence k8, @Nullable Object v8,
      CharSequence k9, @Nullable Object v9,
      CharSequence k10, @Nullable Object v10) {
    return new StructBuilder()
        .add(k1.toString(), CONVERTER.convert(v1))
        .add(k2.toString(), CONVERTER.convert(v2))
        .add(k3.toString(), CONVERTER.convert(v3))
        .add(k4.toString(), CONVERTER.convert(v4))
        .add(k5.toString(), CONVERTER.convert(v5))
        .add(k6.toString(), CONVERTER.convert(v6))
        .add(k7.toString(), CONVERTER.convert(v7))
        .add(k8.toString(), CONVERTER.convert(v8))
        .add(k9.toString(), CONVERTER.convert(v9))
        .add(k10.toString(), CONVERTER.convert(v10))
        .build();
  }

  /**
   * Returns a Struct equivalent to {@code map}.
   *
   * <p>Values are converted using {@link ProtoValueConverter}. In particular, null values are translated to
   * {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if a Map value cannot be converted
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(Map<? extends CharSequence, ? extends @Nullable Object> map) {
    return BiStream.from(map).collect(convertingToStruct());
  }

  /**
   * Returns a nested Struct of Struct equivalent to the {@link Table#rowMap row map} of {@code table}.
   *
   *
   * <p>Values are converted using {@link ProtoValueConverter}. In particular, null values are translated to
   * {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   *
   * @throws IllegalArgumentException if a Table cell value cannot be converted
   * @throws NullPointerException if any row key or column key is null
   */
  public static Struct nestedStruct(
      Table<? extends CharSequence, ? extends CharSequence, ? extends @Nullable Object> table) {
    return struct(table.rowMap());
  }

  /**
   * Returns a {@link BiCollector} that accumulates the name-value pairs into a {@link Struct} with
   * the values converted using {@link ProtoValueConverter}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values will be converted to {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   */
  public static BiCollector<CharSequence, @Nullable Object, Struct> convertingToStruct() {
    return BiCollectors.mapping((k, v) -> k, (k, v) -> CONVERTER.convert(v), toStruct());
  }

  /**
   * Returns a {@link Collector} that collects input key-value pairs into {@link Struct}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   *
   * <p>Unlike {@link #convertingToStruct}, this Collector won't throw runtime {@link Value}
   * conversion error.
   */
  public static <T> Collector<T, ?, Struct> toStruct(
      Function<? super T, ? extends CharSequence> keyFunction,
      Function<? super T, Value> valueFunction) {
    checkNotNull(keyFunction);
    checkNotNull(valueFunction);
    return Collector.of(
        StructBuilder::new,
        (builder, input) -> builder.add(keyFunction.apply(input).toString(), valueFunction.apply(input)),
        StructBuilder::merge,
        StructBuilder::build);
  }

  /**
   * Returns a {@link BiCollector} that collects the input key-value pairs into {@link Struct}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   *
   * <p>Unlike {@link #convertingToStruct}, this BiCollector won't throw runtime {@link Value}
   * conversion error.
   */
  public static BiCollector<CharSequence, Value, Struct> toStruct() {
    return MoreStructs::toStruct;
  }

  private MoreStructs() {}
}

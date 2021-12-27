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

import static java.util.stream.Collectors.mapping;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiCollectors;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
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
 * unsupported types, use {@link #toStruct(Function)} BiCollector on a BiStream, or build it
 * manually with {@link StructBuilder} instead.
 *
 * @since 5.8
 */
@CheckReturnValue
public final class MoreStructs {
  private static final ProtoValueConverter CONVERTER = new ProtoValueConverter();

  /** Returns a Struct with {@code key} and {@code value}. Null {@code value} is translated to {@link NullValue}. */
  public static Struct struct(CharSequence key, Object value) {
    return BiStream.of(key, value).collect(convertingToStruct());
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
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(CharSequence k1, Object v1, CharSequence k2, Object v2) {
    return BiStream.of(k1, v1, k2, v2).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1, CharSequence k2, Object v2, CharSequence k3, Object v3) {
    return BiStream.of(k1, v1, k2, v2, k3, v3).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5,
      CharSequence k6, Object v6) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5,
      CharSequence k6, Object v6,
      CharSequence k7, Object v7) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7).collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5,
      CharSequence k6, Object v6,
      CharSequence k7, Object v7,
      CharSequence k8, Object v8) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8)
        .collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5,
      CharSequence k6, Object v6,
      CharSequence k7, Object v7,
      CharSequence k8, Object v8,
      CharSequence k9, Object v9) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9)
        .collect(convertingToStruct());
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
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4,
      CharSequence k5, Object v5,
      CharSequence k6, Object v6,
      CharSequence k7, Object v7,
      CharSequence k8, Object v8,
      CharSequence k9, Object v9,
      CharSequence k10, Object v10) {
    return BiStream.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10)
        .collect(convertingToStruct());
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
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(Map<? extends CharSequence, ?> map) {
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
   * @throws NullPointerException if any row key or column key is null
   */
  public static Struct nestedStruct(Table<? extends CharSequence, ? extends CharSequence, ?> table) {
    return struct(table.rowMap());
  }

  /**
   * Returns a {@link BiCollector} that accumulates the name-value pairs into a {@link Struct} with
   * the values converted using {@link ProtoValueConverter}.
   *
   * <p>Duplicate keys (according to {@link Object#equals(Object)}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values will be represented with {@link NullValue}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toStruct} or build Struct
   * manually with {@link StructBuilder}.
   */
  public static BiCollector<CharSequence, Object, Struct> convertingToStruct() {
    return BiCollectors.mapping((k, v) -> k, (k, v) -> CONVERTER.convert(v), toStruct());
  }

  /**
   * Returns a {@link BiCollector} that collects the input key-value pairs into {@link Struct}.
   *
   * <p>Unlike {@link #convertingToStruct}, this BiCollector won't throw runtime {@link Value}
   * conversion error.
   */
  public static BiCollector<CharSequence, Value, Struct> toStruct() {
    return StructBuilder::toStruct;
  }

  /**
   * Returns a {@link Collector} that collects to {@link ListValue} with the input elements
   * first converted using {@link ProtoValueConverter}.
   *
   * <p>If runtime conversion error is undesirable, consider to use {@link #toListValue}.
   */
  public static Collector<Object, ?, ListValue> convertingToListValue() {
    return mapping(CONVERTER::convert, toListValue());
  }

  /**
   * Returns a {@link Collector} that collects the input values into {@link ListValue}.
   *
   * <p>Unlike {@link #convertingToListValue}, this Collector won't throw runtime {@link Value}
   * conversion error.
   */
  public static Collector<Value, ?, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        ListValue.Builder::addValues,
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }

  private MoreStructs() {}
}

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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.util.stream.BiCollector;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Additional utilities to help create {@link Struct} and {@link Value} messages.
 *
 * <p>Since Struct fields map to dynamically typed values, throughout this class, struct values
 * of all supported types (numeric, string, boolean, null) and the {@code Collection}s, {@code Map}s,
 * {@code Table}s, {@code Optional}s thereof are automatically converted to their corresponding
 * {@link Value} wrappers. This allows users to more conveniently create {@link Struct} and {@link Value}.
 *
 * <p>Occasionally, an application may need custom logic to convert a domain-specific type into {@code Value},
 * and then recursively into {@code ListValue} and/or {@code Struct}. This can be done by implementing
 * {@link ValueConverter}.
 *
 * @since 5.8
 */
@CheckReturnValue
public final class MoreStructs {
  private static final ValueConverter DEFAULT = new ValueConverter();

  /** Returns a Struct with {@code key} and {@code value}. Null {@code value} is translated to {@link NullValue}. */
  public static Struct struct(CharSequence key, Object value) {
    return Struct.newBuilder().putFields(key.toString(), toValue(value)).build();
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(CharSequence k1, Object v1, CharSequence k2, Object v2) {
    return struct(ImmutableMap.of(k1.toString(), toValue(v1), k2.toString(), toValue(v2)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1, CharSequence k2, Object v2, CharSequence k3, Object v3) {
    return struct(
        ImmutableMap.of(k1.toString(), toValue(v1), k2.toString(), toValue(v2), k3.toString(), toValue(v3)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @throws NullPointerException if any key is null
   */
  public static Struct struct(
      CharSequence k1, Object v1,
      CharSequence k2, Object v2,
      CharSequence k3, Object v3,
      CharSequence k4, Object v4) {
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5),
            k6.toString(), toValue(v6)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5),
            k6.toString(), toValue(v6),
            k7.toString(), toValue(v7)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5),
            k6.toString(), toValue(v6),
            k7.toString(), toValue(v7),
            k8.toString(), toValue(v8)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8, k9:v9}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5),
            k6.toString(), toValue(v6),
            k7.toString(), toValue(v7),
            k8.toString(), toValue(v8),
            k9.toString(), toValue(v9)));
  }

  /**
   * Returns a Struct equivalent to {@code {k1:v1, k2:v2, k3:v3, k4:v4, k5:v5, k6:v6, k7:v7, k8:v8, k9:v9, k10:v10}}.
   *
   * <p>Values are converted using {@link #toValue}.
   * In particular, null values are translated to {@link NullValue}.
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
    return struct(
        ImmutableMap.of(
            k1.toString(), toValue(v1),
            k2.toString(), toValue(v2),
            k3.toString(), toValue(v3),
            k4.toString(), toValue(v4),
            k5.toString(), toValue(v5),
            k6.toString(), toValue(v6),
            k7.toString(), toValue(v7),
            k8.toString(), toValue(v8),
            k9.toString(), toValue(v9),
            k10.toString(), toValue(v10)));
  }

  /** Turns {@code map} into Struct. */
  public static Struct struct(Map<? extends CharSequence, ?> map) {
    return DEFAULT.struct(map);
  }

  /** Turns {@code table} into a nested Struct of Struct. */
  public static Struct nestedStruct(Table<? extends CharSequence, ? extends CharSequence, ?> table) {
    return DEFAULT.nestedStruct(table);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into a {@link Struct} whose keys and
   * values are the result of applying the provided mapping functions to the input elements.
   *
   * <p>Duplicate keys (according to {@link Object#equals(Object)}) are not allowed.
   *
   * <p>Null keys are not allowed, but null values are represented with {@link NullValue}.
   */
  public static <T> Collector<T, ?, Struct> toStruct(
      Function<? super T, ? extends CharSequence> toKey, Function<? super T, ?> toValue) {
    return DEFAULT.toStruct(toKey, toValue);
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
   * BiStream.of("foo", 1. "bar", true).collect(toStruct());
   * }</pre>
   */
  public static BiCollector<CharSequence, Object, Struct> toStruct() {
    return DEFAULT.toStruct();
  }

  /**
   * Converts {@code object} to {@link Value} according to the following rules:
   *
   * <ul>
   *   <li>Primitive types (numbers, strings, booleans) and {@code Struct} proto types ({@link
   *       Struct}, {@link ListValue} etc.) are wrapped inside the respective {@code Value} wrapper
   *       protos;
   *   <li>{@code null} and {@link NullValue} are converted to {@code NullValue};
   *   <li>{@code Value} is returned as is;
   *   <li>{@link Optional} of primitive types, {@code Value} or struct proto types is unwrapped
   *       then converted, with the {@code empty()} instance converted to {@code NullValue};
   *   <li>Collection (Iterable, Map, Multimap, Table)s are recursively converted. Specifically:
   *       <ul>
   *         <li>Iterable is wrapped as {@code ListValue};
   *         <li>Map is wrapped as {@code Struct};
   *         <li>Multimap is wrapped as {@code Struct} with all values mapped to the same key
   *             wrapped inside a {@code ListValue}.
   *         <li>Table is wrapped as a {@code Struct} with string to {@code Struct} mappings;
   *       </ul>
   * </ul>
   *
   * <pre>{@code
   * maps.stream()
   *     .map(MoreStructs::toValue)
   *     .collect(toListValue());
   * }</pre>
   *
   * @throws IllegalArgumentException if {@code object} cannot be converted to Value.
   */
  public static Value toValue(Object object) {
    return DEFAULT.toValue(object);
  }

  /**
   * Returns a {@link Collector} that converts and accumulates the input objects into a {@link
   * ListValue}.
   */
  public static Collector<Object, ListValue.Builder, ListValue> toListValue() {
    return DEFAULT.toListValue();
  }

  private MoreStructs() {}
}

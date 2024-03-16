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
import static com.google.mu.protobuf.util.MoreValues.valueOf;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.annotations.RequiresProtobuf;
import com.google.mu.util.stream.BiCollector;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Additional utilities to help create {@link Struct} messages.
 *
 * <p>The {@code struct(name, value)} helpers can be used to create {@code Struct} conveniently,
 * for example: {@code struct("age", 10)}.
 *
 * <p>To build {@code Struct} with more than one fields, use {@link StructBuilder}; or use
 * {@link #toStruct()} to collect from a {@code BiStream}.
 *
 * <p>If you have complex nested data structures such as {@code Multimap<String, Optional<Integer>>},
 * consider to use {@link Structor}, which translates POJO to Struct for common primitive types and
 * collection types.
 *
 * @since 5.8
 */
@CheckReturnValue
@RequiresProtobuf
public final class MoreStructs {
  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} is null
   */
  public static Struct struct(String name, boolean value) {
    return struct(name, valueOf(value));
  }

  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} is null
   */
  public static Struct struct(String name, double value) {
    return struct(name, valueOf(value));
  }

  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} or {@code value} is null
   */
  public static Struct struct(String name, String value) {
    return struct(name, valueOf(value));
  }

  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} or {@code value} is null
   */
  public static Struct struct(String name, Struct value) {
    return struct(name, valueOf(value));
  }

  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} or {@code value} is null
   */
  public static Struct struct(String name, Value value) {
    return Struct.newBuilder().putFields(name, value).build();
  }

  /**
   * Returns a Struct with {@code name} and {@code value}.
   *
   * @throws NullPointerException if {@code name} or {@code value} is null
   */
  public static Struct struct(String name, ListValue value) {
    return struct(name, valueOf(value));
  }

  /**
   * Returns a {@link Collector} that collects input key-value pairs into {@link Struct}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   */
  public static <T> Collector<T, ?, Struct> toStruct(
      Function<? super T, ? extends CharSequence> keyFunction,
      Function<? super T, Value> valueFunction) {
    checkNotNull(keyFunction);
    checkNotNull(valueFunction);
    return Collector.of(
        StructBuilder::new,
        (builder, input) -> builder.add(keyFunction.apply(input).toString(), valueFunction.apply(input)),
        StructBuilder::addAllFields,
        StructBuilder::build);
  }

  /**
   * Returns a {@link BiCollector} that collects the input key-value pairs into {@link Struct}.
   *
   * <p>Duplicate keys (according to {@link CharSequence#toString()}) are not allowed.
   */
  public static BiCollector<CharSequence, Value, Struct> toStruct() {
    return MoreStructs::toStruct;
  }

  /**
   * Returns a {@link Collector} that flattens all fields from the input {@code Struct}s
   * and collects them into the final {@link Struct}.
   *
   * <p>Duplicate field keys are not allowed.
   */
  public static Collector<Struct, ?, Struct> flatteningToStruct() {
    return Collector.of(
        StructBuilder::new,
        StructBuilder::addAllFields,
        StructBuilder::addAllFields,
        StructBuilder::build);
  }

  /**
   * Returns a {@code Map<String, Object>} <em>view</em> over {@code struct}.
   *
   * <p>{@link Value} wrappers are unwrapped using {@link MoreValues#fromValue},
   * such that {@code Values.of(1)} is unwrapped to {@code 1L},
   * {@code ListValue} is unwrapped as {@code List<Object>}, and {@link
   * com.google.protobuf.NullValue} is unwrapped as {@code null}, etc.
   *
   * <p>Field encounter order is preserved in the result {@code Map}.
   *
   * @since 5.9
   */
  public static Map<String, Object> asMap(Struct struct) {
    return Maps.transformValues(struct.getFieldsMap(), MoreValues::fromValue);
  }

  private MoreStructs() {}
}

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
import static com.google.mu.protobuf.util.MoreValues.valueOf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * A static type-safe builder that supports building heterogeneous {@link Struct} more conveniently,
 * while eliding most of the intermediary and verbose {@link Value} creation.
 *
 * <p>Unlike {@link Struct.Builder}, the {@code add()} methods will throw upon duplicate keys.
 *
 * <p>Compared with {@link MoreStructs#struct}, StructBuilder is more suitable to production code
 * where runtime {@link Value} conversion error caused by unsupported type is undesirable;
 * while MoreStructs is best used in tests and scenarios where runtime conversion error is tolerable.
 *
 * @since 5.8
 */
@CanIgnoreReturnValue
public final class StructBuilder {
  private final LinkedHashMap<String, Value> fields = new LinkedHashMap<>();

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, boolean value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, double value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, String value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds {@code name} and {@code value}.
   *
   * <p>See {@link MoreValues} for helpers that create common {@link ListValue} conveniently.
   *
   *<p>Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, ListValue value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Iterable<Value> values) {
    ListValue.Builder listValue = ListValue.newBuilder();
    for (Value v : values) {
      listValue.addValues(v);
    }
    return add(name, listValue.build());
  }

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Struct value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds {@code name} and {@code value}. Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, StructBuilder value) {
    return add(name, value.build());
  }

  /**
   * Adds {@code name} and {@code value}. {@code value} is converted to a nested Struct.
   * Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Map<String, Value> value) {
    return add(
        name,
        BiStream.from(value).collect(new StructBuilder(), StructBuilder::add).build());
  }

  /**
   * Adds {@code name} and {@code values}. {@code value} is converted to a nested Struct
   * mapping nested keys to {@code ListValue}.
   *
   * <p>Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Multimap<String, Value> value) {
    return add(
        name,
        BiStream.from(value.asMap()).collect(new StructBuilder(), StructBuilder::add).build());
  }

  /**
   * Adds {@code name} and {@code value}. {@code value} is converted to a nested Struct
   * mapping nested keys to {@code Struct}.
   *
   * <p>Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Table<String, String, Value> value) {
    return add(
        name,
        BiStream.from(value.rowMap()).collect(new StructBuilder(), StructBuilder::add).build());
  }

  /**
   * Adds {@code name} and {@code value}.
   *
   * <p>To add a null value, use {@link MoreValues#NULL} as in {@code add("name", NULL)}.
   *
   * <p>Returns this.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   */
  public StructBuilder add(String name, Value value) {
    checkNotNull(name, "name is null");
    checkNotNull(value, "value is null");
    checkArgument(fields.putIfAbsent(name, value) == null, "Field %s already present", name);
    return this;
  }

  /** Returns a new {@link Struct} instance with all added fields. */
  @CheckReturnValue
  public Struct build() {
    Struct.Builder struct = Struct.newBuilder();
    for (Map.Entry<String, Value> field : fields.entrySet()) {
      struct.putFields(field.getKey(), field.getValue());
    }
    return struct.build();
  }

  @Override public String toString() {
    return build().toString();
  }

  /**
   * Returns a {@link Collector} that collects to {@link Struct} using {@link StructBuilder}.
   *
   * <p>Different from {@link MoreStructs#convertingToStruct()}, this collector requires
   * {@code valueFunction} to return {@link Value}, thus won't result in runtime {@link Value}
   * conversion error.
   */
  public static <T> Collector<T, StructBuilder, Struct> toStruct(
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

  private StructBuilder merge(StructBuilder that) {
    BiStream.from(that.fields).forEachOrdered(this::add);
    return this;
  }
}
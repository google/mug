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

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.annotations.RequiresProtobuf;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * A builder that supports building heterogeneous {@link Struct} more conveniently,
 * while eliding most of the intermediary and verbose {@link Value} creation.
 *
 * <p>Unlike {@link Struct.Builder}, the {@code add()} methods will throw upon duplicate keys.
 *
 * @since 5.8
 */
@RequiresProtobuf
public final class StructBuilder {
  private final LinkedHashMap<String, Value> fields = new LinkedHashMap<>();

  /**
   * Adds a {@code (name, value)} field.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, boolean value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds a {@code (name, value)} field.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, double value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds a {@code (name, value)} field.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, String value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds a {@code (name, value)} field.
   *
   * <p>See {@link MoreValues} for helpers that create common {@link ListValue} conveniently.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, ListValue value) {
    return add(name, valueOf(value));
  }

  /**
   * Adds a {@code (name, values) field}, with {@code values} wrapped in {@link ListValue}.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, Iterable<Value> values) {
    ListValue.Builder listValue = ListValue.newBuilder();
    for (Value v : values) {
      listValue.addValues(v);
    }
    return add(name, listValue.build());
  }

  /**
   * Adds a {@code (name, map)} field. {@code map} is converted to a nested Struct.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, Map<String, Value> map) {
    return add(
        name,
        BiStream.from(map).collect(new StructBuilder(), StructBuilder::add).build());
  }

  /**
   * Adds a {@code (name, struct)} field.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, Struct struct) {
    return add(name, valueOf(struct));
  }

  /**
   * Adds a {@code (name, struct)} field.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, StructBuilder struct) {
    checkArgument(this != struct, "Cannot add this builder to itself.");
    return add(name, struct.build());
  }

  /**
   * Adds a {@code (name, value)} field.
   *
   * <p>To add a null value, use {@link MoreValues#NULL} as in {@code add("name", NULL)}.
   *
   * @throws IllegalArgumentException if {@code name} is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder add(String name, Value value) {
    checkNotNull(name, "name is null");
    checkNotNull(value, "value is null");
    checkArgument(fields.putIfAbsent(name, value) == null, "Field %s already present", name);
    return this;
  }

  /**
   * Adds all key-value pairs from {@code map} into this builder.
   *
   * @throws IllegalArgumentException if any key is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder addAll(Map<String, Value> map) {
    BiStream.from(map).forEachOrdered(this::add);
    return this;
  }

  /**
   * Adds all distinct keys from {@code multimap} into this builder.
   * Values mapping to the same key are grouped together in {@link ListValue}.
   *
   * @throws IllegalArgumentException if any key is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder addAll(Multimap<String, Value> multimap) {
    BiStream.from(multimap.asMap()).forEachOrdered(this::add);
    return this;
  }

  /**
   * Adds all rows from {@code table} into this builder. Columns of each row
   * are grouped together in {@link Struct}, keyed by column name.
   *
   * @throws IllegalArgumentException if any row key is duplicate
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder addAll(Table<String, String, Value> table) {
    BiStream.from(table.rowMap()).forEachOrdered(this::add);
    return this;
  }

  /**
   * Adds all fields from {@code that} into this builder.
   *
   * @throws IllegalArgumentException if duplicate field name is encountered
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder addAllFields(Struct that) {
    BiStream.from(that.getFieldsMap()).forEachOrdered(this::add);
    return this;
  }

  /**
   * Adds all fields from {@code that} into this builder.
   *
   * @throws IllegalArgumentException if duplicate field name is encountered
   * @return this builder
   */
  @CanIgnoreReturnValue
  public StructBuilder addAllFields(StructBuilder that) {
    checkArgument(this != that, "Cannot add this builder to itself.");
    BiStream.from(that.fields).forEachOrdered(this::add);
    return this;
  }

  /** Returns a new {@link Struct} instance with all added fields. */
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
}
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

import static java.util.Arrays.stream;

import java.util.List;
import java.util.stream.Collector;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.ListValue;
import com.google.protobuf.ListValueOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.ValueOrBuilder;

/**
 * Additional utilities to help create {@link Value} and {@link ListValue} messages.
 *
 * @since 5.8
 */
@CheckReturnValue
public final class MoreValues {
  /** The {@link Value} for null. */
  public static final Value NULL =
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();

  /** The {@link Value} for boolean {@code true}. */
  public static final Value TRUE = Value.newBuilder().setBoolValue(true).build();

  /** The {@link Value} for boolean {@code false}. */
  public static final Value FALSE = Value.newBuilder().setBoolValue(false).build();

  /** Returns {@link Value} wrapper for {@code string} if not null, or else returns {@link #NULL}. */
  public static Value nullableValueOf(@Nullable String string) {
    return string == null ? NULL : valueOf(string);
  }

  /** Returns {@link ListValue} wrapping {@code values}. */
  public static ListValue listValueOf(double... values) {
    return stream(values).mapToObj(MoreValues::valueOf).collect(toListValue());
  }

  /**
   * Returns {@link ListValue} wrapping {@code values}.
   * Null strings are converted to {@link NULL}.
   */
  public static ListValue listValueOf(@Nullable String... values) {
    return stream(values).map(MoreValues::nullableValueOf).collect(toListValue());
  }

  /**
   * Returns {@link ListValue} wrapping {@code values}.
   * Null structs are converted to {@link NULL}.
   */
  public static ListValue listValueOf(@Nullable Struct... values) {
    return stream(values).map(MoreValues::nullableValueOf).collect(toListValue());
  }

  /** Returns a {@link Collector} that collects the input values into {@link ListValue}. */
  public static Collector<Value, ?, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        ListValue.Builder::addValues,
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }

  /**
   * Unwraps {@code value}.
   *
   * <p>For example, {@code Values.of(1)} is unwrapped to {@code 1}; {@link ListValue} is
   * unwrapped as {@code List<Object>}; {@link Struct} is unwrapped as {@code Map<String, Object>};
   * and {@link NullValue} is unwrapped as {@code null}, etc.
   *
   * <p>Note that integral numbers in the range of {@code int} will be unwrapped as {@code Integer};
   * while integral numbers otherwise in the range of {@code long} will be unwrapped as {@code Long}.
   * All other numbers are unwrapped as {@code Double}. If you need to handle all number cases
   * unconditionally, consider to use {@link Number#doubleValue}.
   *
   * <p>The returned object is immutable. Even if {@code value} is an instance of {@link
   * Value.Builder}, and the underlying state is changed after this method returns,
   * the returned object remains unchanged.
   *
   * @see MoreStructs#asMap
   * @see #asList
   * @since 5.9
   */
  @Nullable
  public static Object fromValue(ValueOrBuilder value) {
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return null;
      case BOOL_VALUE:
        return value.getBoolValue();
      case STRING_VALUE:
        return value.getStringValue();
      case NUMBER_VALUE: {
        double v = value.getNumberValue();
        if (v >= Long.MIN_VALUE && v <= Long.MAX_VALUE && DoubleMath.isMathematicalInteger(v)) {
          if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
            return (int) v;
          } else {
            return (long) v;
          }
        } else {
          return v;
        }
      }
      case LIST_VALUE:
        return asList(value.getListValue());
      case STRUCT_VALUE:
        return MoreStructs.asMap(value.getStructValue());
      default:
        throw new AssertionError("Unsupported value: " + value);
    }
  }

  /**
   * Returns a {@code List<Object>} <em>view</em> over {@code listValue}.
   *
   * <p>For example, {@code Values.of(1)} is unwrapped to {@code 1L};
   * {@link Struct} is unwrapped as {@code Map<String, Object>};
   * and {@link NullValue} is unwrapped as {@code null}, etc.
   *
   * @see MoreStructs#asMap
   * @see #fromValue
   * @since 5.9
   */
  public static List<Object> asList(ListValueOrBuilder listValue) {
    return Lists.transform(listValue.getValuesList(), MoreValues::fromValue);
  }

  static Value valueOf(double n) {
    return Value.newBuilder().setNumberValue(n).build();
  }

  static Value valueOf(boolean b) {
    return b ? TRUE : FALSE;
  }

  static Value valueOf(String s) {
    return Value.newBuilder().setStringValue(s).build();
  }

  static Value valueOf(Struct v) {
    return Value.newBuilder().setStructValue(v).build();
  }

  static Value valueOf(ListValue v) {
    return Value.newBuilder().setListValue(v).build();
  }

  /** Returns {@link Value} wrapper for {@code struct} if not null, or else returns {@link #NULL}. */
  private static Value nullableValueOf(@Nullable Struct struct) {
    return struct == null ? NULL : valueOf(struct);
  }

  private MoreValues() {}
}

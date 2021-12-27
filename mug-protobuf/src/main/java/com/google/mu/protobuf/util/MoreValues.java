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

import java.util.stream.Collector;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

/**
 * Additional utilities to help create {@link Value} messages.
 *
 * @since 5.8
 */
public final class MoreValues {
  static final Value NULL =
      Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
  private static final Value FALSE_VALUE = Value.newBuilder().setBoolValue(false).build();
  private static final Value TRUE_VALUE = Value.newBuilder().setBoolValue(true).build();

  /** Returns {@link ListValue} wrapping {@code values}. */
  public static ListValue listValue(double... values) {
    return stream(values).mapToObj(MoreValues::valueOf).collect(toListValue());
  }

  /** Returns {@link ListValue} wrapping {@code values}. */
  public static ListValue listValue(String... values) {
    return stream(values).map(MoreValues::valueOf).collect(toListValue());
  }

  /** Returns a {@link Collector} that collects the input values into {@link ListValue}. */
  public static Collector<Value, ?, ListValue> toListValue() {
    return Collector.of(
        ListValue::newBuilder,
        ListValue.Builder::addValues,
        (a, b) -> a.addAllValues(b.getValuesList()),
        ListValue.Builder::build);
  }

  static Value valueOf(double n) {
    return Value.newBuilder().setNumberValue(n).build();
  }

  static Value valueOf(boolean b) {
    return b ? TRUE_VALUE : FALSE_VALUE;
  }

  static Value valueOf(String s) {
    return Value.newBuilder().setStringValue(s).build();
  }

  static Value valueOf(ListValue v) {
    return Value.newBuilder().setListValue(v).build();
  }

  static Value valueOf(Struct v) {
    return Value.newBuilder().setStructValue(v).build();
  }
}

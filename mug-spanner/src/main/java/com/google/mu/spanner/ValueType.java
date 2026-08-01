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
package com.google.mu.spanner;

import static com.google.mu.spanner.InternalUtils.checkArgument;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.cloud.ByteArray;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.Interval;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Value;
import com.google.mu.util.StringFormat;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ProtocolMessageEnum;

/** Type inference for {@link Value}. */
enum ValueType {
  BOOL(Boolean.class) {
    @Override public Value toValue(Object obj) {
      return Value.bool((Boolean) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.boolArray((Collection<Boolean>) elements);
    }
  },
  STRING(String.class) {
    @Override public Value toValue(Object obj) {
      return Value.string((String) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.stringArray((Collection<String>) elements);
    }
  },
  INT(Integer.class) {
    @Override public Value toValue(Object obj) {
      return Value.int64(((Integer) obj).longValue());
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<Integer> ints = (Collection<Integer>) elements;
      return Value.int64Array(ints.stream().map(Integer::longValue).collect(toList()));
    }
  },
  LONG(Long.class) {
    @Override public Value toValue(Object obj) {
      return Value.int64(((Long) obj));
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.int64Array((Collection<Long>) elements);
    }
  },
  FLOAT(Float.class) {
    @Override public Value toValue(Object obj) {
      return Value.float32((Float) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.float32Array((Collection<Float>) elements);
    }
  },
  DOUBLE(Double.class) {
    @Override public Value toValue(Object obj) {
      return Value.float64((Double) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.float64Array((Collection<Double>) elements);
    }
  },
  BIG_DECIMAL(BigDecimal.class) {
    @Override public Value toValue(Object obj) {
      return Value.numeric((BigDecimal) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.numericArray((Collection<BigDecimal>) elements);
    }
  },
  INSTANT(Instant.class) {
    @Override public Value toValue(Object obj) {
      return Value.timestamp(toTimestamp((Instant) obj));
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<Instant> instants = (Collection<Instant>) elements;
      return Value.timestampArray(instants.stream().map(t -> toTimestamp(t)).collect(toList()));
    }
  },
  ZONED_DATE_TIME(ZonedDateTime.class) {
    @Override public Value toValue(Object obj) {
      return Value.timestamp(toTimestamp((((ZonedDateTime) obj).toInstant())));
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<ZonedDateTime> times = (Collection<ZonedDateTime>) elements;
      return Value.timestampArray(times.stream().map(t -> toTimestamp(t.toInstant())).collect(toList()));
    }
  },
  OFFSET_DATE_TIME(OffsetDateTime.class) {
    @Override public Value toValue(Object obj) {
      return Value.timestamp(toTimestamp(((OffsetDateTime) obj).toInstant()));
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<OffsetDateTime> times = (Collection<OffsetDateTime>) elements;
      return Value.timestampArray(times.stream().map(t -> toTimestamp(t.toInstant())).collect(toList()));
    }
  },
  LOCAL_DATE(LocalDate.class) {
    @Override public Value toValue(Object obj) {
      return Value.date(toDate((LocalDate) obj));
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<LocalDate> dates = (Collection<LocalDate>) elements;
      return Value.dateArray(dates.stream().map(d -> toDate(d)).collect(toList()));
    }
  },
  UUID(UUID.class) {
    @Override public Value toValue(Object obj) {
      return Value.uuid((UUID) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.uuidArray((Collection<UUID>) elements);
    }
  },
  BYTE_ARRAY(ByteArray.class) {
    @Override public Value toValue(Object obj) {
      return Value.bytes((ByteArray) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.bytesArray((Collection<ByteArray>) elements);
    }
  },
  INTERVAL(Interval.class) {
    @Override public Value toValue(Object obj) {
      return Value.interval((Interval) obj);
    }
    @SuppressWarnings("unchecked")  // checked by toArrayValue static method
    @Override public Value toArrayValue(Collection<?> elements) {
      return Value.intervalArray((Collection<Interval>) elements);
    }
  },
  PROTO_ENUM(ProtocolMessageEnum.class) {
    @Override public Value toValue(Object obj) {
      return Value.protoEnum((ProtocolMessageEnum) obj);
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<ProtocolMessageEnum> enums = (Collection<ProtocolMessageEnum>) elements;
      return Value.protoEnumArray(enums, enums.iterator().next().getDescriptorForType());
    }
  },
  PROTO(AbstractMessage.class) {
    @Override public Value toValue(Object obj) {
      return Value.protoMessage((AbstractMessage) obj);
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<AbstractMessage> messages = (Collection<AbstractMessage>) elements;
      return Value.protoMessageArray(messages, messages.iterator().next().getDescriptorForType());
    }
  },
  STRUCT(Struct.class) {
    @Override public Value toValue(Object obj) {
      return Value.struct((Struct) obj);
    }
    @Override public Value toArrayValue(Collection<?> elements) {
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      Collection<Struct> structs = (Collection<Struct>) elements;
      return Value.structArray(structs.iterator().next().getType(), structs);
    }
  };

  private static final Map<Class<?>, ValueType> ALL_TYPES =
      Arrays.stream(values()).collect(Collectors.toMap(vt -> vt.javaType, vt -> vt));
  private static final StringFormat.Template<IllegalArgumentException> FAIL_TO_CONVERT =
      StringFormat.to(
          IllegalArgumentException::new,
          "Cannot convert object of {class} to Value for {{name}}. " +
          "Consider using the static factory methods in Value class to convert explicitly.");

  private final Class<?> javaType;

  ValueType(Class<?> javaType) {
    this.javaType = javaType;
  }

  static Value inferValue(String name, Object obj) {
    checkArgument(obj != null, "Cannot infer type from null. Use explicit Value instead for {%s}", name);
    if (obj instanceof Value) {
      return (Value) obj;
    }
    if (obj instanceof boolean[]) {
      return Value.boolArray((boolean[]) obj);
    }
    if (obj instanceof long[]) {
      return Value.int64Array((long[]) obj);
    }
    if (obj instanceof float[]) {
      return Value.float32Array((float[]) obj);
    }
    if (obj instanceof double[]) {
      return Value.float64Array((double[]) obj);
    }
    return inferFromPojo(name, obj).toValue(obj);
  }

  static Value inferArrayValue(String name, Collection<?> elements) {
    Set<ValueType> inferred = elements.stream()
        .filter(Objects::nonNull)
        .map(obj -> inferFromPojo(name, obj))
        .collect(toSet());
    checkArgument(inferred.size() > 0, "Failed to infer array type for {%s}", name);
    checkArgument(inferred.size() == 1, "Conflicting array type inferred: %s", inferred);
    return inferred.iterator().next().toArrayValue(elements);
  }

  private static ValueType inferFromPojo(String name, Object obj) {
    ValueType inferred = ALL_TYPES.get(obj.getClass());
    if (inferred != null) {
      return inferred;
    }
    return BiStream.from(ALL_TYPES)
        .filterKeys(cls -> cls.isInstance(obj))
        .values()
        .findFirst()
        .orElseThrow(() -> FAIL_TO_CONVERT.with(obj.getClass(), name));
  }

  private static Timestamp toTimestamp(Instant time) {
    return Timestamp.ofTimeSecondsAndNanos(time.getEpochSecond(), time.getNano());
  }

  private static Date toDate(LocalDate date) {
    return Date.fromYearMonthDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  abstract Value toValue(Object obj);
  abstract Value toArrayValue(Collection<?> elements);

  @Override public String toString() {
    return javaType.getName();
  }
}
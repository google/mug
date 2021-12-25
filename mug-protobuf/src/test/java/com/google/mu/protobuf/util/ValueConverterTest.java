package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class ValueConverterTest {
  private final ValueConverter converter = new ValueConverter();

  @Test
  public void toValue_fromMap() {
    assertThat(converter.convertRecursively(ImmutableMap.of("one", 1, "two", 2)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", Values.of(1))
                        .putFields("two", Values.of(2))
                        .build())
                .build());
  }

  @Test
  public void toValue_fromMultimap() {
    assertThat(converter.convertRecursively(ImmutableListMultimap.of("1", "uno", "1", "one")))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields("1", converter.convertRecursively(asList("uno", "one"))).build())
                .build());
  }

  @Test
  public void toValue_fromTable() {
    assertThat(converter.convertRecursively(ImmutableTable.of("one", "uno", 1)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", converter.convertRecursively(ImmutableMap.of("uno", 1)))
                        .build())
                .build());
  }

  @Test
  public void toValue_withNullMapKey() {
    Map<?, ?> map = BiStream.of(null, "null").collect(Collectors::toMap);
    assertThrows(NullPointerException.class, () -> converter.convertRecursively(map));
  }

  @Test
  public void toValue_withNonStringMapKey() {
    Map<?, ?> map = BiStream.of(1, "one").collect(Collectors::toMap);
    assertThrows(IllegalArgumentException.class, () -> converter.convertRecursively(map));
  }

  @Test
  public void toValue_fromIterable() {
    assertThat(converter.convertRecursively(asList(10, 20)))
        .isEqualTo(
            Value.newBuilder()
                .setListValue(ListValue.newBuilder().addValues(Values.of(10)).addValues(Values.of(20)))
                .build());
  }

  @Test
  public void toValue_fromOptional() {
    assertThat(converter.convertRecursively(Optional.empty())).isEqualTo(Values.ofNull());
    assertThat(converter.convertRecursively(Optional.of("foo"))).isEqualTo(Values.of("foo"));
  }

  @Test
  public void toValue_fromStruct() {
    Struct struct = struct("foo", 1, "bar", "x");
    assertThat(converter.convertRecursively(struct)).isEqualTo(Value.newBuilder().setStructValue(struct).build());
  }

  @Test
  public void toValue_fromListValue() {
    ListValue list = Stream.of(1, 2).collect(converter.toListValue());
    assertThat(converter.convertRecursively(list)).isEqualTo(Value.newBuilder().setListValue(list).build());
  }

  @Test
  public void toValue_fromNullValue() {
    assertThat(converter.convertRecursively(NullValue.NULL_VALUE))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
  }

  @Test
  public void toValue_fromNumber() {
    assertThat(converter.convertRecursively(10L)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(converter.convertRecursively(10)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(converter.convertRecursively(10F)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(converter.convertRecursively(10D)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
  }

  @Test
  public void toValue_fromString() {
    assertThat(converter.convertRecursively("42")).isEqualTo(Value.newBuilder().setStringValue("42").build());
  }

  @Test
  public void toValue_fromBoolean() {
    assertThat(converter.convertRecursively(true)).isEqualTo(Value.newBuilder().setBoolValue(true).build());
    assertThat(converter.convertRecursively(false)).isEqualTo(Value.newBuilder().setBoolValue(false).build());
    assertThat(converter.convertRecursively(true)).isSameAs(converter.convertRecursively(true));
    assertThat(converter.convertRecursively(false)).isSameAs(converter.convertRecursively(false));
  }

  @Test
  public void toValue_fromNull() {
    assertThat(converter.convertRecursively(null))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    assertThat(converter.convertRecursively(null)).isSameAs(converter.convertRecursively(null));
  }

  @Test
  public void toValue_fromEmptyOptional() {
    assertThat(converter.convertRecursively(Optional.empty())).isEqualTo(Values.ofNull());
  }

  @Test
  public void toValue_fromNonEmptyOptional() {
    assertThat(converter.convertRecursively(Optional.of(123))).isEqualTo(Values.of(123));
  }

  @Test
  public void toValue_fromValue() {
    Value value = Value.newBuilder().setBoolValue(false).build();
    assertThat(converter.convertRecursively(value).getBoolValue()).isFalse();
  }

  @Test
  public void toValue_fromUnsupportedType() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> converter.convertRecursively(this));
    assertThat(thrown).hasMessageThat().contains(getClass().getName());
  }

  @Test
  public void toValue_cyclic() {
    List<Object> list = new ArrayList<>();
    list.add(list);
    assertThrows(StackOverflowError.class, () -> converter.convertRecursively(list));
  }

  @Test
  public void testToListValue() {
    assertThat(
            Stream.of(1, "foo", asList(true, false), ImmutableMap.of("k", 20L))
                .collect(converter.toListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of("foo"))
                .addValues(converter.convertRecursively(asList(true, false)))
                .addValues(converter.convertRecursively(ImmutableMap.of("k", 20L)))
                .build());
  }

  @Test
  public void convertCannotReturnNull() {
    ValueConverter nullReturn = new ValueConverter() {
      @Override protected Value convert(Object obj) {
        return null;
      }
    };
    assertThrows(
        NullPointerException.class, () -> Stream.of("foo").collect(nullReturn.toListValue()));
    assertThrows(
        NullPointerException.class, () -> BiStream.of("foo", 1).collect(nullReturn::toStruct));
  }

  @Test
  public void convertRecursivelyCannotReturnNull() {
    ValueConverter nullReturn = new ValueConverter() {
      @Override public Value convertRecursively(Object obj) {
        return null;
      }
    };
    assertThrows(
        NullPointerException.class, () -> Stream.of("foo").collect(nullReturn.toListValue()));
    assertThrows(
        NullPointerException.class, () -> BiStream.of("foo", 1).collect(nullReturn::toStruct));
  }
}

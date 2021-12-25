package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreStructs.toListValue;
import static com.google.mu.protobuf.util.MoreStructs.toStruct;
import static com.google.mu.protobuf.util.MoreStructs.toValue;
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

@RunWith(JUnit4.class)
public class MoreStructsTest {

  @Test
  public void struct_onePair() {
    assertThat(struct("key", 1))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", toValue(1))
                .build());
  }

  @Test
  public void struct_onePair_nullKey() {
    assertThrows(NullPointerException.class, () -> struct(null, "v"));
  }

  @Test
  public void struct_onePair_nullValue() {
    assertThat(struct("key", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", toValue(null))
                .build());
  }

  @Test
  public void struct_twoPairs() {
    assertThat(struct("int", 1, "string", "two"))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("int", toValue(1))
                .putFields("string", toValue("two"))
                .build());
  }

  @Test
  public void struct_twoPairs_nullKey() {
    assertThrows(NullPointerException.class, () -> struct(null, "v1", "k2", "v2"));
  }

  @Test
  public void struct_twoPairs_nullValue() {
    assertThat(struct("k1", null, "k2", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .build());
  }

  @Test
  public void struct_twoPairs_duplicateKey() {
    assertThrows(IllegalArgumentException.class, () -> struct("a", 1, "a", 2));
  }

  @Test
  public void struct_3Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .build());
  }

  @Test
  public void struct_3Pairs_nullKey() {
    assertThrows(NullPointerException.class, () -> struct("k1", "v1", "k2", "v2", null, 3));
  }

  @Test
  public void struct_3Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .build());
  }

  @Test
  public void struct_3Pairs_duplicateKey() {
    assertThrows(IllegalArgumentException.class, () -> struct("a", 1, "b", 2, "a", 3));
  }

  @Test
  public void struct_4Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .build());
  }

  @Test
  public void struct_4Pairs_nullKey() {
    assertThrows(NullPointerException.class, () -> struct("k1", "v1", "k2", "v2", null, 3, "v4", 4));
  }

  @Test
  public void struct_4Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null, "k4", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .build());
  }

  @Test
  public void struct_4Pairs_duplicateKey() {
    assertThrows(IllegalArgumentException.class, () -> struct("a", 1, "b", 2, "a", 3, "d", 4));
  }

  @Test
  public void struct_5Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .build());
  }

  @Test
  public void struct_5Pairs_nullKey() {
    assertThrows(NullPointerException.class, () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5));
  }

  @Test
  public void struct_5Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .build());
  }

  @Test
  public void struct_5Pairs_duplicateKey() {
    assertThrows(IllegalArgumentException.class, () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5));
  }

  @Test
  public void struct_6Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .putFields("f", toValue(6))
                .build());
  }

  @Test
  public void struct_6Pairs_nullKey() {
    assertThrows(
        NullPointerException.class, () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5, "k6", 6));
  }

  @Test
  public void struct_6Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null, "k6", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .putFields("k6", toValue(null))
                .build());
  }

  @Test
  public void struct_6Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class, () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6));
  }

  @Test
  public void struct_7Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .putFields("f", toValue(6))
                .putFields("g", toValue(7))
                .build());
  }

  @Test
  public void struct_7Pairs_nullKey() {
    assertThrows(
        NullPointerException.class,
        () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5, "k6", 6, "k7", 7));
  }

  @Test
  public void struct_7Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null, "k6", null, "k7", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .putFields("k6", toValue(null))
                .putFields("k7", toValue(null))
                .build());
  }

  @Test
  public void struct_7Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6, "g", 7));
  }

  @Test
  public void struct_8Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .putFields("f", toValue(6))
                .putFields("g", toValue(7))
                .putFields("h", toValue(8))
                .build());
  }

  @Test
  public void struct_8Pairs_nullKey() {
    assertThrows(
        NullPointerException.class,
        () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5, "k6", 6, "k7", 7, "k8", 8));
  }

  @Test
  public void struct_8Pairs_nullValue() {
    assertThat(struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null, "k6", null, "k7", null, "k8", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .putFields("k6", toValue(null))
                .putFields("k7", toValue(null))
                .putFields("k8", toValue(null))
                .build());
  }

  @Test
  public void struct_8Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8));
  }

  @Test
  public void struct_9Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .putFields("f", toValue(6))
                .putFields("g", toValue(7))
                .putFields("h", toValue(8))
                .putFields("i", toValue(9))
                .build());
  }

  @Test
  public void struct_9Pairs_nullKey() {
    assertThrows(
        NullPointerException.class,
        () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5, "k6", 6, "k7", 7, "k8", 8, "k9", 9));
  }

  @Test
  public void struct_9Pairs_nullValue() {
    assertThat(
            struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null, "k6", null, "k7", null, "k8", null, "k9", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .putFields("k6", toValue(null))
                .putFields("k7", toValue(null))
                .putFields("k8", toValue(null))
                .putFields("k9", toValue(null))
                .build());
  }

  @Test
  public void struct_9Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9));
  }

  @Test
  public void struct_10Pairs() {
    assertThat(struct("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9, "j", 10))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", toValue(1))
                .putFields("b", toValue(2))
                .putFields("c", toValue(3))
                .putFields("d", toValue(4))
                .putFields("e", toValue(5))
                .putFields("f", toValue(6))
                .putFields("g", toValue(7))
                .putFields("h", toValue(8))
                .putFields("i", toValue(9))
                .putFields("j", toValue(10))
                .build());
  }

  @Test
  public void struct_10Pairs_nullKey() {
    assertThrows(
        NullPointerException.class,
        () -> struct("k1", "v1", "k2", "v2", null, 3, "k4", 4, "k5", 5, "k6", 6, "k7", 7, "k8", 8, "k9", 9, "k10", 10));
  }

  @Test
  public void struct_10Pairs_nullValue() {
    assertThat(
            struct("k1", null, "k2", null, "k3", null, "k4", null, "k5", null, "k6", null, "k7", null, "k8", null, "k9", null, "k10", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", toValue(null))
                .putFields("k2", toValue(null))
                .putFields("k3", toValue(null))
                .putFields("k4", toValue(null))
                .putFields("k5", toValue(null))
                .putFields("k6", toValue(null))
                .putFields("k7", toValue(null))
                .putFields("k8", toValue(null))
                .putFields("k9", toValue(null))
                .putFields("k10", toValue(null))
                .build());
  }

  @Test
  public void struct_10Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9, "j", 10));
  }

  @Test
  public void toValue_fromMap() {
    assertThat(toValue(ImmutableMap.of("one", 1, "two", 2)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", toValue(1))
                        .putFields("two", toValue(2))
                        .build())
                .build());
  }

  @Test
  public void toValue_fromMultimap() {
    assertThat(toValue(ImmutableListMultimap.of("1", "uno", "1", "one")))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields("1", toValue(asList("uno", "one"))).build())
                .build());
  }

  @Test
  public void toValue_fromTable() {
    assertThat(toValue(ImmutableTable.of("one", "uno", 1)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", toValue(ImmutableMap.of("uno", 1)))
                        .build())
                .build());
  }

  @Test
  public void toValue_withNullMapKey() {
    Map<?, ?> map = BiStream.of(null, "null").collect(Collectors::toMap);
    assertThrows(NullPointerException.class, () -> toValue(map));
  }

  @Test
  public void toValue_withNonStringMapKey() {
    Map<?, ?> map = BiStream.of(1, "one").collect(Collectors::toMap);
    assertThrows(IllegalArgumentException.class, () -> toValue(map));
  }

  @Test
  public void toValue_fromIterable() {
    assertThat(toValue(asList(10, 20)))
        .isEqualTo(
            Value.newBuilder()
                .setListValue(ListValue.newBuilder().addValues(toValue(10)).addValues(toValue(20)))
                .build());
  }

  @Test
  public void toValue_fromOptional() {
    assertThat(toValue(Optional.empty())).isEqualTo(toValue(null));
    assertThat(toValue(Optional.of("foo"))).isEqualTo(toValue("foo"));
  }

  @Test
  public void toValue_fromStruct() {
    Struct struct = BiStream.of("foo", 1, "bar", "x").collect(toStruct());
    assertThat(toValue(struct)).isEqualTo(Value.newBuilder().setStructValue(struct).build());
  }

  @Test
  public void toValue_fromListValue() {
    ListValue list = Stream.of(1, 2).collect(toListValue());
    assertThat(toValue(list)).isEqualTo(Value.newBuilder().setListValue(list).build());
  }

  @Test
  public void toValue_fromNullValue() {
    assertThat(toValue(NullValue.NULL_VALUE))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
  }

  @Test
  public void toValue_fromNumber() {
    assertThat(toValue(10L)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(toValue(10)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(toValue(10F)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
    assertThat(toValue(10D)).isEqualTo(Value.newBuilder().setNumberValue(10).build());
  }

  @Test
  public void toValue_fromString() {
    assertThat(toValue("42")).isEqualTo(Value.newBuilder().setStringValue("42").build());
  }

  @Test
  public void toValue_fromBoolean() {
    assertThat(toValue(true)).isEqualTo(Value.newBuilder().setBoolValue(true).build());
    assertThat(toValue(false)).isEqualTo(Value.newBuilder().setBoolValue(false).build());
    assertThat(toValue(true)).isSameAs(toValue(true));
    assertThat(toValue(false)).isSameAs(toValue(false));
  }

  @Test
  public void toValue_fromNull() {
    assertThat(toValue(null))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    assertThat(toValue(null)).isSameAs(toValue(null));
  }

  @Test
  public void toValue_fromEmptyOptional() {
    assertThat(toValue(Optional.empty())).isEqualTo(toValue(null));
  }

  @Test
  public void toValue_fromNonEmptyOptional() {
    assertThat(toValue(Optional.of(123))).isEqualTo(toValue(123));
  }

  @Test
  public void toValue_fromValue() {
    Value value = Value.newBuilder().setBoolValue(false).build();
    assertThat(toValue(value).getBoolValue()).isFalse();
  }

  @Test
  public void toValue_fromUnsupportedType() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> toValue(this));
    assertThat(thrown).hasMessageThat().contains(getClass().getName());
  }

  @Test
  public void toValue_cyclic() {
    List<Object> list = new ArrayList<>();
    list.add(list);
    assertThrows(StackOverflowError.class, () -> toValue(list));
  }

  @Test
  public void toStruct_biCollector() {
    Struct struct = BiStream.of("foo", 1, "bar", ImmutableMap.of("one", true)).collect(toStruct());
    assertThat(struct)
        .isEqualTo(
            Struct.newBuilder()
                .putFields("foo", toValue(1))
                .putFields(
                    "bar",
                    Value.newBuilder()
                        .setStructValue(Struct.newBuilder().putFields("one", toValue(true)).build())
                        .build())
                .build());
  }

  @Test
  public void testToListValue() {
    assertThat(
            Stream.of(1, "foo", asList(true, false), ImmutableMap.of("k", 20L))
                .collect(toListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(toValue(1))
                .addValues(toValue("foo"))
                .addValues(toValue(asList(true, false)))
                .addValues(toValue(ImmutableMap.of("k", 20L)))
                .build());
  }
}

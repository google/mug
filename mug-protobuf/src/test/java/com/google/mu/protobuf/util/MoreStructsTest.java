package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.convertingToListValue;
import static com.google.mu.protobuf.util.MoreStructs.convertingToStruct;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreStructs.toStruct;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class MoreStructsTest {

  @Test
  public void struct_onePair() {
    assertThat(struct("key", 1))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(1))
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
                .putFields("key", Values.ofNull())
                .build());
  }

  @Test
  public void struct_twoPairs() {
    assertThat(struct("int", 1, "string", "two"))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("int", Values.of(1))
                .putFields("string", Values.of("two"))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
                .putFields("f", Values.of(6))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
                .putFields("k6", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
                .putFields("f", Values.of(6))
                .putFields("g", Values.of(7))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
                .putFields("k6", Values.ofNull())
                .putFields("k7", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
                .putFields("f", Values.of(6))
                .putFields("g", Values.of(7))
                .putFields("h", Values.of(8))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
                .putFields("k6", Values.ofNull())
                .putFields("k7", Values.ofNull())
                .putFields("k8", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
                .putFields("f", Values.of(6))
                .putFields("g", Values.of(7))
                .putFields("h", Values.of(8))
                .putFields("i", Values.of(9))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
                .putFields("k6", Values.ofNull())
                .putFields("k7", Values.ofNull())
                .putFields("k8", Values.ofNull())
                .putFields("k9", Values.ofNull())
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
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .putFields("d", Values.of(4))
                .putFields("e", Values.of(5))
                .putFields("f", Values.of(6))
                .putFields("g", Values.of(7))
                .putFields("h", Values.of(8))
                .putFields("i", Values.of(9))
                .putFields("j", Values.of(10))
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
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .putFields("k4", Values.ofNull())
                .putFields("k5", Values.ofNull())
                .putFields("k6", Values.ofNull())
                .putFields("k7", Values.ofNull())
                .putFields("k8", Values.ofNull())
                .putFields("k9", Values.ofNull())
                .putFields("k10", Values.ofNull())
                .build());
  }

  @Test
  public void struct_10Pairs_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> struct("a", 1, "b", 2, "a", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9, "j", 10));
  }

  @Test
  public void convertingToStruct_biCollector() {
    Struct struct = BiStream.of("foo", 1, "bar", ImmutableMap.of("one", true)).collect(convertingToStruct());
    assertThat(struct)
        .isEqualTo(
            Struct.newBuilder()
                .putFields("foo", Values.of(1))
                .putFields(
                    "bar",
                    Value.newBuilder()
                        .setStructValue(Struct.newBuilder().putFields("one", Values.of(true)).build())
                        .build())
                .build());
  }

  @Test
  public void testToListValue() {
    ProtoValueConverter converter = new ProtoValueConverter();
    assertThat(
            Stream.of(1, "foo", asList(true, false), ImmutableMap.of("k", 20L))
                .collect(convertingToListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of("foo"))
                .addValues(converter.convert(asList(true, false)))
                .addValues(converter.convert(ImmutableMap.of("k", 20L)))
                .build());
  }

  @Test
  public void toStruct_biCollector() {
    Struct struct = BiStream.of("foo", 1).collect(toStruct(Values::of));
    assertThat(struct).isEqualTo(struct("foo", 1));
  }

  @Test
  public void toStruct_biCollector_empty() {
    Struct struct = BiStream.<String, String>empty().collect(toStruct(Values::of));
    assertThat(struct).isEqualTo(Struct.getDefaultInstance());
  }

  @Test
  public void toStruct_biCollector_duplicateKeys() {
    assertThrows(IllegalArgumentException.class, () -> BiStream.of("foo", 1, "foo", 1).collect(toStruct(Values::of)));
  }
}

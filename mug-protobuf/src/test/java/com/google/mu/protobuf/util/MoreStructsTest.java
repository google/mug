package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.flatteningToStruct;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreStructs.toStruct;
import static com.google.mu.protobuf.util.MoreValues.NULL;
import static com.google.mu.protobuf.util.MoreValues.listValueOf;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class MoreStructsTest {

  @Test public void struct_number() {
    assertThat(struct("key", 1))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(1))
                .build());
  }

  @Test public void struct_boolean() {
    assertThat(struct("key", true))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(true))
                .build());
  }

  @Test public void struct_string() {
    assertThat(struct("key", "value"))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of("value"))
                .build());
  }

  @Test public void struct_value() {
    assertThat(struct("key", MoreValues.NULL))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", MoreValues.NULL)
                .build());
  }

  @Test public void struct_listValue() {
    assertThat(struct("key", MoreValues.listValueOf(1, 2)))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(MoreValues.listValueOf(1, 2)))
                .build());
  }

  @Test public void struct_nestedStruct() {
    assertThat(struct("key", struct("k2", 1)))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(Struct.newBuilder().putFields("k2", Values.of(1)).build()))
                .build());
  }

  @Test public void toStruct_biCollector() {
    Struct struct = BiStream.of("foo", 1).mapValues(Values::of).collect(toStruct());
    assertThat(struct).isEqualTo(struct("foo", 1));
  }

  @Test public void toStruct_biCollector_empty() {
    Struct struct = BiStream.<String, String>empty().mapValues(Values::of).collect(toStruct());
    assertThat(struct).isEqualTo(Struct.getDefaultInstance());
  }

  @Test public void flatteningToStruct_empty() {
    assertThat(Stream.<Struct>empty().collect(flatteningToStruct()))
        .isEqualTo(Struct.getDefaultInstance());
  }

  @Test public void flatteningToStruct_singleStruct() {
    assertThat(Stream.of(struct("foo", 1)).collect(flatteningToStruct()))
        .isEqualTo(struct("foo", 1));
  }

  @Test public void flatteningToStruct_multipleStructs() {
    assertThat(Stream.of(struct("foo", 1), struct("bar", "boo")).collect(flatteningToStruct()))
        .isEqualTo(Struct.newBuilder()
            .putFields("foo", Values.of(1))
            .putFields("bar", Values.of("boo"))
            .build());
  }

  @Test public void flatteningToStruct_duplicateKeys() {
    Stream<Struct> structs = Stream.of(struct("foo", 1), struct("foo", "boo"));
    assertThrows(IllegalArgumentException.class, () -> structs.collect(flatteningToStruct()));
  }

  @Test public void toStruct_biCollector_duplicateKeys() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("foo", 1, "foo", 1).mapValues(Values::of).collect(toStruct()));
  }

  @Test public void testToStruct_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("k", Values.of(1), "k", Values.of(2)).collect(toStruct()));
  }

  @Test public void testAsMap_fromStruct() {
    assertThat(MoreStructs.asMap(new Structor().struct("one", 1, "twos", listValueOf(2, 2.5))))
        .containsExactly("one", 1, "twos", asList(2, 2.5D))
        .inOrder();
  }

  @Test public void testAsMap_withNullValue() {
    assertThat(MoreStructs.asMap(new Structor().struct("one", NULL)))
        .containsExactly("one", null)
        .inOrder();
  }

  @Test public void testNulls() {
    new NullPointerTester()
        .setDefault(ListValue.class, MoreValues.listValueOf(1))
        .setDefault(Value.class, Value.getDefaultInstance())
        .setDefault(Struct.class, Struct.getDefaultInstance())
        .testAllPublicStaticMethods(MoreStructs.class);
  }
}

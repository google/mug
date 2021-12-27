package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.listValueOf;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class StructBuilderTest {
  @Test public void testAdd_boolean() {
    assertThat(new StructBuilder().add("k", true).build())
        .isEqualTo(Structs.of("k", Values.of(true)));
    assertThat(new StructBuilder().add("k", false).build())
        .isEqualTo(Structs.of("k", Values.of(false)));
  }

  @Test public void testAdd_string() {
    assertThat(new StructBuilder().add("k", "v").build())
        .isEqualTo(Structs.of("k", Values.of("v")));
  }

  @Test public void testAdd_number() {
    assertThat(new StructBuilder().add("k", 1).build())
        .isEqualTo(Structs.of("k", Values.of(1)));
  }

  @Test public void testAdd_listValue() {
    ListValue listValue = listValueOf(1, 2);
    assertThat(new StructBuilder().add("k", listValue).build())
        .isEqualTo(Structs.of("k", Values.of(listValue)));
  }

  @Test public void testAdd_list() {
    assertThat(new StructBuilder().add("k", asList(Values.of(1), Values.of(2))).build())
        .isEqualTo(Structs.of("k", Values.of(listValueOf(1, 2))));
  }

  @Test public void testAdd_map() {
    assertThat(new StructBuilder().add("k", ImmutableMap.of("one", Values.of(1))).build())
        .isEqualTo(Structs.of("k", Values.of(struct("one", 1))));
  }

  @Test public void testAdd_multimap() {
    assertThat(new StructBuilder().add("k", ImmutableListMultimap.of("one", Values.of(1))).build())
        .isEqualTo(Structs.of("k", Values.of(struct("one", asList(1)))));
  }

  @Test public void testAdd_table() {
    assertThat(new StructBuilder().add("k", ImmutableTable.of("row", "col", Values.of(1))).build())
        .isEqualTo(Structs.of("k", Values.of(struct("row", struct("col", 1)))));
  }

  @Test public void testAdd_struct() {
    Struct struct =  struct("name", "v");
    assertThat(new StructBuilder().add("k", struct).build())
        .isEqualTo(Structs.of("k", Values.of(struct)));
  }

  @Test public void testAdd_emptyStructBuilder() {
    assertThat(new StructBuilder().add("k", new StructBuilder()).build())
        .isEqualTo(Structs.of("k", Values.of(Struct.getDefaultInstance())));
  }

  @Test public void testAdd_nonEmptyStructBuilder() {
    assertThat(new StructBuilder().add("k", new StructBuilder().add("name", "value")).build())
        .isEqualTo(Structs.of("k", Values.of(struct("name", "value"))));
  }

  @Test public void testAdd_value() {
    assertThat(new StructBuilder().add("k", Values.of(1)).build())
        .isEqualTo(Structs.of("k", Values.of(1)));
  }

  @Test public void testAddNull() {
    assertThat(new StructBuilder().addNull("k").build())
        .isEqualTo(Structs.of("k", Values.ofNull()));
  }

  @Test public void testToString() {
    assertThat(new StructBuilder().add("k", 1).toString())
        .isEqualTo(struct("k", 1).toString());
  }

  @Test public void testToStruct() {
    Struct struct = BiStream.of("k", Values.of(1)).collect(StructBuilder::toStruct);
    assertThat(struct).isEqualTo(Structs.of("k", Values.of(1)));
  }

  @Test public void testToStruct_empty() {
    Struct struct = BiStream.<String, Value>empty().collect(StructBuilder::toStruct);
    assertThat(struct).isEqualTo(Struct.getDefaultInstance());
  }

  @Test public void testToStruct_duplicateKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BiStream.of("k", Values.of(1), "k", Values.of(2)).collect(StructBuilder::toStruct));
  }

  @Test public void testDuplicateKey_boolean() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", true).add("k2", false);
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", true));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", false));
  }

  @Test public void testDuplicateKey_string() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", "v").add("k2", "v");
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", "v"));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", "v2"));
  }

  @Test public void testDuplicateKey_number() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", 1).add("k2", 1);
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", 1));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", 2));
  }

  @Test public void testDuplicateKey_value() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", Values.of(1)).add("k2", Values.of(2));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", Values.of(1)));
  }

  @Test public void testDuplicateKey_struct() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", struct("one", 1)).add("k2", struct("two", 2));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", struct("3", 3)));
  }

  @Test public void testDuplicateKey_structBuilder() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", new StructBuilder().add("one", 1).add("k2", new StructBuilder().add("two", 2)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", new StructBuilder()));
  }

  @Test public void testDuplicateKey_listValue() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", Values.of(asList(Values.of(1))));
    assertThrows(
        IllegalArgumentException.class, () -> builder.add("k", Values.of(asList(Values.of(1)))));
  }

  @Test public void testDuplicateKey_list() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", asList(Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", asList()));
  }

  @Test public void testDuplicateKey_map() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", ImmutableMap.of("one", Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", ImmutableMap.of()));
  }

  @Test public void testDuplicateKey_multimap() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", ImmutableListMultimap.of("one", Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", ImmutableListMultimap.of()));
  }

  @Test public void testDuplicateKey_table() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", ImmutableTable.of("row", "col", Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", ImmutableTable.of()));
  }

  @Test public void testDuplicateKey_null() {
    StructBuilder builder = new StructBuilder();
    builder.addNull("k");
    builder.addNull("k2");
    assertThrows(IllegalArgumentException.class, () -> builder.addNull("k"));
  }

  @Test public void testNulls() {
    new NullPointerTester()
        .setDefault(Value.class, Value.getDefaultInstance())
        .setDefault(ListValue.class, ListValue.getDefaultInstance())
        .setDefault(Struct.class, Struct.getDefaultInstance())
        .testAllPublicInstanceMethods(new StructBuilder());
  }
}

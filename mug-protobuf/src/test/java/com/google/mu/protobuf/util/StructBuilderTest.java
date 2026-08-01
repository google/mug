package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.listValueOf;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.testing.NullPointerTester;
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
    assertThat(new StructBuilder().add("k", ImmutableList.of(Values.of(1), Values.of(2))).build())
        .isEqualTo(Structs.of("k", Values.of(listValueOf(1, 2))));
  }

  @Test public void testAddAll_map() {
    assertThat(new StructBuilder().addAll(ImmutableMap.of("one", Values.of(1))).build())
        .isEqualTo(struct("one", 1));
  }

  @Test public void testAddAll_multimap() {
    assertThat(new StructBuilder().addAll(ImmutableListMultimap.of("one", Values.of(1))).build())
        .isEqualTo(struct("one", listValueOf(1)));
  }

  @Test public void testAddAll_table() {
    assertThat(new StructBuilder().addAll(ImmutableTable.of("row", "col", Values.of(1))).build())
        .isEqualTo(struct("row", struct("col", 1)));
  }

  @Test public void testAddAll_table_differentRowKeySameColumnKey() {
    StructBuilder builder = new StructBuilder().addAll(ImmutableTable.of("r1", "col", Values.of(1)));
    assertThat(builder.addAll(ImmutableTable.of("r2", "col", Values.of(2))).build())
        .isEqualTo(
            Struct.newBuilder()
                .putFields("r1", Values.of(struct("col", 1)))
                .putFields("r2", Values.of(struct("col", 2)))
                .build());
  }

  @Test public void testAdd_struct() {
    Struct struct =  struct("name", "v");
    assertThat(new StructBuilder().add("k", struct).build())
        .isEqualTo(Structs.of("k", Values.of(struct)));
  }

  @Test public void testAdd_thisStructBuilder() {
    StructBuilder builder = new StructBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", builder));
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

  @Test public void testAddAllFields_fromStruct() {
    Struct struct =  struct("name", "v");
    assertThat(new StructBuilder().addAllFields(struct).build())
        .isEqualTo(struct);
  }

  @Test public void testAddAllFields_fromStructBuilder() {
    assertThat(new StructBuilder().addAllFields(new StructBuilder().add("name", 1)).build())
        .isEqualTo(struct("name", 1));
  }

  @Test public void testAddAllFields_cyclic() {
    StructBuilder builder = new StructBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.addAllFields(builder));
  }

  @Test public void testToString() {
    assertThat(new StructBuilder().add("k", 1).toString())
        .isEqualTo(struct("k", 1).toString());
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
    builder.add("k", Values.of(ImmutableList.of(Values.of(1))));
    assertThrows(
        IllegalArgumentException.class, () -> builder.add("k", Values.of(ImmutableList.of(Values.of(1)))));
  }

  @Test public void testDuplicateKey_list() {
    StructBuilder builder = new StructBuilder();
    builder.add("k", ImmutableList.of(Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.add("k", ImmutableList.of()));
  }

  @Test public void testAddAll_duplicateKeyInMap() {
    StructBuilder builder = new StructBuilder();
    builder.addAll(ImmutableMap.of("one", Values.of(1)));
    assertThrows(IllegalArgumentException.class, () -> builder.addAll(ImmutableMap.of("one", Values.of(2))));
  }

  @Test public void testAddAll_duplicateKeyInMultimap() {
    StructBuilder builder = new StructBuilder();
    builder.addAll(ImmutableListMultimap.of("one", Values.of(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.addAll(ImmutableListMultimap.of("one", Values.of(2))));
  }

  @Test public void testAddAll_duplicateRowKeyInTable() {
    StructBuilder builder = new StructBuilder();
    builder.addAll(ImmutableTable.of("row", "col", Values.of(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.addAll(ImmutableTable.of("row", "col2", Values.of(2))));
  }

  @Test public void testAddAll_duplicateColumnKeyInTable() {
    StructBuilder builder = new StructBuilder();
    builder.addAll(ImmutableTable.of("row", "col", Values.of(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.addAll(ImmutableTable.of("row", "col", Values.of(2))));
  }

  @Test public void testDuplicateKey_addAllFieldsFromStruct() {
    StructBuilder builder = new StructBuilder().add("k", 1);
    assertThrows(IllegalArgumentException.class, () -> builder.addAllFields(struct("k", 1)));
  }

  @Test public void testDuplicateKey_addAllFieldsFromStructBuilder() {
    StructBuilder builder = new StructBuilder().add("k", 1);
    assertThrows(IllegalArgumentException.class, () -> builder.addAllFields(new StructBuilder().add("k", 1)));
  }

  @Test public void testNulls() {
    new NullPointerTester()
        .setDefault(Value.class, Value.getDefaultInstance())
        .setDefault(ListValue.class, ListValue.getDefaultInstance())
        .setDefault(Struct.class, Struct.getDefaultInstance())
        .testAllPublicInstanceMethods(new StructBuilder());
  }
}

package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.FALSE;
import static com.google.mu.protobuf.util.MoreValues.NULL;
import static com.google.mu.protobuf.util.MoreValues.TRUE;
import static com.google.mu.protobuf.util.MoreValues.listValueOf;
import static com.google.mu.protobuf.util.MoreValues.toListValue;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.NullPointerTester;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class MoreValuesTest {
  @Test public void testToListValue() {
    Structor converter = new Structor();
    assertThat(
            Stream.of(1, "foo", asList(true, false), ImmutableMap.of("k", 20L)).map(converter::toValue).collect(toListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of("foo"))
                .addValues(converter.toValue(asList(true, false)))
                .addValues(converter.toValue(ImmutableMap.of("k", 20L)))
                .build());
  }

  @Test public void testListValueOfNumbers() {
    assertThat(listValueOf(1, 2))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of(2))
                .build());
  }

  @Test public void testListValueOfStrings() {
    assertThat(listValueOf("foo", "bar", null))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of("foo"))
                .addValues(Values.of("bar"))
                .addValues(NULL)
                .build());
  }

  @Test public void testListValueOfStructs() {
    assertThat(listValueOf(struct("foo", 1), null, struct("bar", 2)))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(Structs.of("foo", Values.of(1))))
                .addValues(NULL)
                .addValues(Values.of(Structs.of("bar", Values.of(2))))
                .build());
  }

  @Test public void testListValueOf_nullStringArray() {
    assertThrows(NullPointerException.class, () -> listValueOf((String[]) null));
  }

  @Test public void testNullableValue_string() {
    assertThat(MoreValues.nullableValueOf((String) null)).isEqualTo(NULL);
    assertThat(MoreValues.nullableValueOf("abc")).isEqualTo(Values.of("abc"));
  }

  @Test public void testAsList() {
    assertThat(MoreValues.asList(listValueOf(1, 2)))
        .containsExactly(1L, 2L)
        .inOrder();
  }

  @Test public void testAsList_withNullElement() {
    assertThat(MoreValues.asList(ListValue.newBuilder().addValues(NULL)))
        .containsExactly((Object) null)
        .inOrder();
  }

  @Test public void testFromValue_null() {
    assertThat(MoreValues.fromValue(NULL)).isNull();
    assertThat(MoreValues.fromValue(NULL.toBuilder())).isNull();
  }

  @Test public void testFromValue_boolean() {
    assertThat(MoreValues.fromValue(TRUE)).isEqualTo(true);
    assertThat(MoreValues.fromValue(TRUE.toBuilder())).isEqualTo(true);
    assertThat(MoreValues.fromValue(FALSE)).isEqualTo(false);
    assertThat(MoreValues.fromValue(FALSE.toBuilder())).isEqualTo(false);
  }

  @Test public void testFromValue_integer() {
    assertThat(MoreValues.fromValue(Values.of(1))).isEqualTo(1L);
    assertThat(MoreValues.fromValue(Values.of(1).toBuilder())).isEqualTo(1L);
  }

  @Test public void testFromValue_double() {
    assertThat(MoreValues.fromValue(Values.of(0.5))).isEqualTo(0.5D);
    assertThat(MoreValues.fromValue(Values.of(0.5).toBuilder())).isEqualTo(0.5D);
  }

  @Test public void testFromValue_string() {
    assertThat(MoreValues.fromValue(Values.of("foo"))).isEqualTo("foo");
    assertThat(MoreValues.fromValue(Values.of("foo").toBuilder())).isEqualTo("foo");
  }

  @Test public void testFromValue_list() {
    assertThat(MoreValues.fromValue(Values.of(listValueOf("foo", "bar"))))
        .isEqualTo(asList("foo", "bar"));
    assertThat(MoreValues.fromValue(Values.of(listValueOf("foo", "bar")).toBuilder()))
        .isEqualTo(asList("foo", "bar"));
  }

  @Test public void testFromValue_struct() {
    assertThat(MoreValues.fromValue(Values.of(struct("one", 1))))
        .isEqualTo(ImmutableMap.of("one", 1L));
    assertThat(MoreValues.fromValue(Value.newBuilder().setStructValue(struct("one", 0.5))))
        .isEqualTo(ImmutableMap.of("one", 0.5D));
  }

  @Test public void testTrue() {
    assertThat(TRUE.getBoolValue()).isTrue();
  }

  @Test public void testFalse() {
    assertThat(FALSE.getBoolValue()).isFalse();
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MoreValues.class);
  }
}

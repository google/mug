package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.NULL;
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
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class MoreValuesTest {
  @Test public void testToListValue() {
    ProtoValueConverter converter = new ProtoValueConverter();
    assertThat(
            Stream.of(1, "foo", asList(true, false), ImmutableMap.of("k", 20L)).map(converter::convert).collect(toListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of("foo"))
                .addValues(converter.convert(asList(true, false)))
                .addValues(converter.convert(ImmutableMap.of("k", 20L)))
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
    assertThat(MoreValues.nullableValue((String) null)).isEqualTo(NULL);
    assertThat(MoreValues.nullableValue("abc")).isEqualTo(Values.of("abc"));
  }

  @Test public void testTrue() {
    assertThat(MoreValues.TRUE.getBoolValue()).isTrue();
  }

  @Test public void testFalse() {
    assertThat(MoreValues.FALSE.getBoolValue()).isFalse();
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MoreValues.class);
  }
}

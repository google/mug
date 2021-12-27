package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreValues.listValue;
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

  @Test public void testListValue_numbers() {
    assertThat(listValue(1, 2))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of(2))
                .build());
  }

  @Test public void testListValue_strings() {
    assertThat(listValue("foo", "bar"))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of("foo"))
                .addValues(Values.of("bar"))
                .build());
  }

  @Test public void testListValue_nullStringArray() {
    assertThrows(NullPointerException.class, () -> listValue((String[]) null));
  }

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(MoreValues.class);
  }
}

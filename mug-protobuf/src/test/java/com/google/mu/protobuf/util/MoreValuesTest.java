package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.FALSE;
import static com.google.mu.protobuf.util.MoreValues.NULL;
import static com.google.mu.protobuf.util.MoreValues.TRUE;
import static com.google.mu.protobuf.util.MoreValues.listValueOf;
import static com.google.mu.protobuf.util.MoreValues.toListValue;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
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
            Stream.of(1, "foo", list(true, false), ImmutableMap.of("k", 20L)).map(converter::toValue).collect(toListValue()))
        .isEqualTo(
            ListValue.newBuilder()
                .addValues(Values.of(1))
                .addValues(Values.of("foo"))
                .addValues(converter.toValue(list(true, false)))
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

  @Test public void testlist() {
    assertThat(MoreValues.asList(listValueOf(1, Long.MAX_VALUE, Long.MIN_VALUE)))
        .containsExactly(1, Long.MAX_VALUE, Long.MIN_VALUE)
        .inOrder();
  }

  @Test public void testlist_withNullElement() {
    assertThat(MoreValues.asList(ListValue.newBuilder().addValues(NULL)))
        .containsExactly((Object) null)
        .inOrder();
  }

  @Test public void testlist_fromBuilder_mutation() {
    ListValue.Builder builder = ListValue.newBuilder().addValues(Values.of("foo"));
    List<Object> list = MoreValues.asList(builder);
    builder.addValues(Values.of(2));
    assertThat(list)
        .containsExactly("foo", 2)
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
    assertThat(MoreValues.fromValue(Values.of(0))).isEqualTo(0L);
    assertThat(MoreValues.fromValue(Values.of(1))).isEqualTo(1L);
    assertThat(MoreValues.fromValue(Values.of(-1))).isEqualTo(-1L);
    assertThat(MoreValues.fromValue(Values.of(1).toBuilder())).isEqualTo(1L);
  }

  @Test public void testFromValue_int_minValue() {
    assertThat(MoreValues.fromValue(Values.of(Integer.MIN_VALUE))).isEqualTo(Integer.MIN_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Integer.MIN_VALUE))).isInstanceOf(Integer.class);
  }

  @Test public void testFromValue_int_maxValue() {
    assertThat(MoreValues.fromValue(Values.of(Integer.MAX_VALUE))).isEqualTo(Integer.MAX_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Integer.MAX_VALUE))).isInstanceOf(Integer.class);
  }

  @Test public void testFromValue_long_minValue() {
    assertThat(MoreValues.fromValue(Values.of(Long.MIN_VALUE))).isEqualTo(Long.MIN_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Long.MIN_VALUE))).isInstanceOf(Long.class);
    assertThat(MoreValues.fromValue(Values.of(((long) Integer.MIN_VALUE) - 1)))
        .isEqualTo(((long) Integer.MIN_VALUE) - 1);
    assertThat(MoreValues.fromValue(Values.of(((long) Integer.MIN_VALUE) * 2)))
        .isEqualTo(((long) Integer.MIN_VALUE) * 2);
  }

  @Test public void testFromValue_long_maxValue() {
    assertThat(MoreValues.fromValue(Values.of(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Long.MAX_VALUE))).isInstanceOf(Long.class);
    assertThat(MoreValues.fromValue(Values.of(((long) Integer.MAX_VALUE) + 1)))
        .isEqualTo(((long) Integer.MAX_VALUE) + 1);
    assertThat(MoreValues.fromValue(Values.of(((long) Integer.MAX_VALUE) * 2)))
        .isEqualTo(((long) Integer.MAX_VALUE) * 2);
  }

  @Test public void testFromValue_double() {
    assertThat(MoreValues.fromValue(Values.of(0.5))).isEqualTo(0.5D);
    assertThat(MoreValues.fromValue(Values.of(-0.5))).isEqualTo(-0.5D);
    assertThat(MoreValues.fromValue(Values.of(0.5).toBuilder())).isEqualTo(0.5D);
  }

  @Test public void testFromValue_double_minValue() {
    assertThat(MoreValues.fromValue(Values.of(Double.MIN_VALUE))).isEqualTo(Double.MIN_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Double.MIN_VALUE))).isInstanceOf(Double.class);
    assertThat(MoreValues.fromValue(Values.of(Double.MIN_VALUE + Double.MAX_VALUE / 2)))
        .isEqualTo(Double.MIN_VALUE + Double.MAX_VALUE / 2);
    assertThat(MoreValues.fromValue(Values.of(((double) Long.MIN_VALUE) * 2)))
        .isEqualTo(((double) Long.MIN_VALUE) * 2);
  }

  @Test public void testFromValue_double_maxValue() {
    assertThat(Values.of(Double.MAX_VALUE).getNumberValue()).isEqualTo(Double.MAX_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Double.MAX_VALUE))).isInstanceOf(Double.class);
    assertThat(MoreValues.fromValue(Values.of(Double.MAX_VALUE))).isEqualTo(Double.MAX_VALUE);
    assertThat(MoreValues.fromValue(Values.of(Double.MAX_VALUE / 2))).isEqualTo(Double.MAX_VALUE / 2);
    assertThat(MoreValues.fromValue(Values.of(((double) Long.MAX_VALUE) * 2)))
        .isEqualTo(((double) Long.MAX_VALUE) * 2);
  }

  @Test public void testFromValue_double_minNormal() {
    assertThat(MoreValues.fromValue(Values.of(Double.MIN_NORMAL))).isEqualTo(Double.MIN_NORMAL);
  }

  @Test public void testFromValue_double_infinity() {
    assertThat(MoreValues.fromValue(Values.of(Double.POSITIVE_INFINITY)))
        .isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(MoreValues.fromValue(Values.of(Double.NEGATIVE_INFINITY)))
        .isEqualTo(Double.NEGATIVE_INFINITY);
  }

  @Test public void testFromValue_double_nan() {
    assertThat(Values.of(Double.NaN).getNumberValue()).isEqualTo(Double.NaN);
  }

  @Test public void testFromValue_string() {
    assertThat(MoreValues.fromValue(Values.of("foo"))).isEqualTo("foo");
    assertThat(MoreValues.fromValue(Values.of("foo").toBuilder())).isEqualTo("foo");
  }

  @Test public void testFromValue_list() {
    assertThat(MoreValues.fromValue(Values.of(listValueOf("foo", "bar"))))
        .isEqualTo(list("foo", "bar"));
    assertThat(MoreValues.fromValue(Values.of(listValueOf("foo", "bar")).toBuilder()))
        .isEqualTo(list("foo", "bar"));
  }

  @Test public void testFromValue_struct() {
    assertThat(MoreValues.fromValue(Values.of(struct("one", 1))))
        .isEqualTo(ImmutableMap.of("one", 1));
    assertThat(MoreValues.fromValue(Value.newBuilder().setStructValue(struct("one", 0.5))))
        .isEqualTo(ImmutableMap.of("one", 0.5D));
  }

  @Test public void testFromValue_builder_listValueChangeNotReflected() {
    Value.Builder builder = Value.newBuilder().setListValue(listValueOf(1, 2));
    Object object = MoreValues.fromValue(builder);
    builder.getListValueBuilder().clear();
    assertThat(object).isEqualTo(list(1, 2));
  }

  @Test public void testFromValue_builder_structValueChangeNotReflected() {
    Value.Builder builder = Value.newBuilder().setStructValue(struct("one", 1));
    Object object = MoreValues.fromValue(builder);
    builder.getStructValueBuilder().clear();
    assertThat(object).isEqualTo(ImmutableMap.of("one", 1));
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

  private static <T> ImmutableList<T> list(T... values) {
    return ImmutableList.copyOf(values);
  }
}

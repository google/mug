package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreStructs.toListValue;
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.primitives.ImmutableDoubleArray;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.primitives.ImmutableLongArray;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class ProtoValueConverterTest {
  private final ProtoValueConverter converter = new ProtoValueConverter();

  @Test
  public void convert_fromMap() {
    assertThat(converter.convert(ImmutableMap.of("one", 1, "two", 2)))
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
  public void convert_fromMultimap() {
    assertThat(converter.convert(ImmutableListMultimap.of("1", "uno", "1", "one")))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields("1", converter.convert(asList("uno", "one"))).build())
                .build());
  }

  @Test
  public void convert_fromTable() {
    assertThat(converter.convert(ImmutableTable.of("one", "uno", 1)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", converter.convert(ImmutableMap.of("uno", 1)))
                        .build())
                .build());
  }

  @Test
  public void convert_withNullMapKey() {
    Map<?, ?> map = BiStream.of(null, "null").collect(Collectors::toMap);
    assertThrows(NullPointerException.class, () -> converter.convert(map));
  }

  @Test
  public void convert_withNonStringMapKey() {
    Map<?, ?> map = BiStream.of(1, "one").collect(Collectors::toMap);
    assertThrows(IllegalArgumentException.class, () -> converter.convert(map));
  }

  @Test
  public void convert_fromIterable() {
    assertThat(converter.convert(asList(10, 20)))
        .isEqualTo(
            Value.newBuilder()
                .setListValue(ListValue.newBuilder().addValues(Values.of(10)).addValues(Values.of(20)))
                .build());
  }

  @Test
  public void convert_fromOptional() {
    assertThat(converter.convert(Optional.empty())).isEqualTo(Values.ofNull());
    assertThat(converter.convert(Optional.of("foo"))).isEqualTo(Values.of("foo"));
  }

  @Test
  public void convert_fromStruct() {
    Struct struct = struct("foo", 1, "bar", "x");
    assertThat(converter.convert(struct)).isEqualTo(Value.newBuilder().setStructValue(struct).build());
  }

  @Test
  public void convert_fromListValue() {
    ListValue list = Stream.of(1, 2).map(converter::convert).collect(toListValue());
    assertThat(converter.convert(list)).isEqualTo(Value.newBuilder().setListValue(list).build());
  }

  @Test
  public void convert_fromNullValue() {
    assertThat(converter.convert(NullValue.NULL_VALUE))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
  }

  @Test
  public void convert_fromNumber() {
    assertThat(converter.convert(10L)).isEqualTo(Values.of(10));
    assertThat(converter.convert(10)).isEqualTo(Values.of(10));
    assertThat(converter.convert(10F)).isEqualTo(Values.of(10));
    assertThat(converter.convert(10D)).isEqualTo(Values.of(10));
  }

  @Test
  public void convert_fromString() {
    assertThat(converter.convert("42")).isEqualTo(Value.newBuilder().setStringValue("42").build());
  }

  @Test
  public void convert_fromBoolean() {
    assertThat(converter.convert(true)).isEqualTo(Value.newBuilder().setBoolValue(true).build());
    assertThat(converter.convert(false)).isEqualTo(Value.newBuilder().setBoolValue(false).build());
    assertThat(converter.convert(true)).isSameAs(converter.convert(true));
    assertThat(converter.convert(false)).isSameAs(converter.convert(false));
  }

  @Test
  public void convert_fromNull() {
    assertThat(converter.convert(null))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    assertThat(converter.convert(null)).isSameAs(converter.convert(null));
  }

  @Test
  public void convert_fromEmptyOptional() {
    assertThat(converter.convert(Optional.empty())).isEqualTo(Values.ofNull());
  }

  @Test
  public void convert_fromNonEmptyOptional() {
    assertThat(converter.convert(Optional.of(123))).isEqualTo(Values.of(123));
  }

  @Test
  public void convert_fromValue() {
    Value value = Value.newBuilder().setBoolValue(false).build();
    assertThat(converter.convert(value).getBoolValue()).isFalse();
  }

  @Test
  public void convert_fromUnsupportedType() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> converter.convert(this));
    assertThat(thrown).hasMessageThat().contains(getClass().getName());
  }

  @Test
  public void convert_cyclic() {
    List<Object> list = new ArrayList<>();
    list.add(list);
    assertThrows(StackOverflowError.class, () -> converter.convert(list));
  }

  @Test
  public void convert_fromIntArray() {
    assertThat(converter.convert(new int[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromImmutableIntArray() {
    assertThat(converter.convert(ImmutableIntArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromLongArray() {
    assertThat(converter.convert(new long[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromImmutableLongArray() {
    assertThat(converter.convert(ImmutableLongArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromDoubleArray() {
    assertThat(converter.convert(new double[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromImmutableDoubleArray() {
    assertThat(converter.convert(ImmutableDoubleArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test
  public void convert_fromByteArray() {
    assertThat(converter.convert(new byte[] {10, 20}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10), Values.of(20))));
  }

  @Test
  public void convert_fromShortArray() {
    assertThat(converter.convert(new short[] {10, 20}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10), Values.of(20))));
  }

  @Test
  public void convert_fromArray() {
    assertThat(converter.convert(new String[] {"foo", "bar"}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of("foo"), Values.of("bar"))));
  }

  @Test
  public void convert_fromEnum() {
    assertThat(converter.convert(Cast.values()))
        .isEqualTo(Values.of(ImmutableList.of(Values.of("VILLAIN"), Values.of("HERO"))));
  }

  @Test
  public void customConversion() {
    ProtoValueConverter custom = new ProtoValueConverter() {
      @Override public Value convert(Object obj) {
        if (obj instanceof Hero) {
          Hero hero = (Hero) obj;
          return convert(ImmutableMap.of("name", hero.name, "titles", hero.titles, "friends", hero.friends));
        }
        return super.convert(obj);
      }
    };
    Hero ironMan = new Hero("Tony Stark", "Iron Man", "Robert Downey");
    Hero scarletWitch = new Hero("Wanda", "Scarlet Witch");
    ironMan.friends.add(scarletWitch);
    scarletWitch.friends.add(new Hero("Vision"));
    assertThat(custom.convert(Optional.of(ironMan)))
        .isEqualTo(Values.of(struct(
                "name", "Tony Stark",
                "titles", ImmutableList.of("Iron Man", "Robert Downey"),
                "friends", ImmutableList.of(struct(
                    "name", "Wanda",
                    "titles", ImmutableList.of("Scarlet Witch"),
                    "friends", ImmutableList.of(struct(
                        "name", "Vision",
                        "titles", ImmutableList.of(),
                        "friends", ImmutableList.of())))))));
  }

  private static final class Hero {
    final String name;
    final ImmutableList<String> titles;
    final List<Hero> friends = new ArrayList<>();

    Hero(String name, String... titles) {
      this.name = name;
      this.titles = ImmutableList.copyOf(titles);
    }
  }

  private enum Cast {
    VILLAIN, HERO
  }
}

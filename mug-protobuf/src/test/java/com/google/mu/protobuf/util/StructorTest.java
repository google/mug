package com.google.mu.protobuf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.protobuf.util.MoreStructs.struct;
import static com.google.mu.protobuf.util.MoreValues.toListValue;
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
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Values;

@RunWith(JUnit4.class)
public class StructorTest {
  private final Structor maker = new Structor();

  @Test public void toValue_fromMap() {
    assertThat(maker.toValue(ImmutableMap.of("one", 1, "two", 2)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", Values.of(1))
                        .putFields("two", Values.of(2))
                        .build())
                .build());
  }

  @Test public void toValue_fromMultimap() {
    assertThat(maker.toValue(ImmutableListMultimap.of("1", "uno", "1", "one")))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields("1", maker.toValue(ImmutableList.of("uno", "one"))).build())
                .build());
  }

  @Test public void toValue_fromTable() {
    assertThat(maker.toValue(ImmutableTable.of("one", "uno", 1)))
        .isEqualTo(
            Value.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields("one", maker.toValue(ImmutableMap.of("uno", 1)))
                        .build())
                .build());
  }

  @Test public void toValue_withNullMapKey() {
    Map<?, ?> map = BiStream.of(null, "null").collect(Collectors::toMap);
    assertThrows(NullPointerException.class, () -> maker.toValue(map));
  }

  @Test public void toValue_withNonStringMapKey() {
    Map<?, ?> map = BiStream.of(1, "one").collect(Collectors::toMap);
    assertThrows(IllegalArgumentException.class, () -> maker.toValue(map));
  }

  @Test public void toValue_fromIterable() {
    assertThat(maker.toValue(ImmutableList.of(10, 20)))
        .isEqualTo(
            Value.newBuilder()
                .setListValue(ListValue.newBuilder().addValues(Values.of(10)).addValues(Values.of(20)))
                .build());
  }

  @Test public void toValue_fromOptional() {
    assertThat(maker.toValue(Optional.empty())).isEqualTo(Values.ofNull());
    assertThat(maker.toValue(Optional.of("foo"))).isEqualTo(Values.of("foo"));
  }

  @Test public void toValue_fromStruct() {
    Struct struct = maker.struct("foo", 1, "bar", "x");
    assertThat(maker.toValue(struct)).isEqualTo(Value.newBuilder().setStructValue(struct).build());
  }

  @Test public void toValue_fromListValue() {
    ListValue list = Stream.of(1, 2).map(maker::toValue).collect(toListValue());
    assertThat(maker.toValue(list)).isEqualTo(Value.newBuilder().setListValue(list).build());
  }

  @Test public void toValue_fromNullValue() {
    assertThat(maker.toValue(NullValue.NULL_VALUE))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
  }

  @Test public void toValue_fromNumber() {
    assertThat(maker.toValue(10L)).isEqualTo(Values.of(10));
    assertThat(maker.toValue(10)).isEqualTo(Values.of(10));
    assertThat(maker.toValue(10F)).isEqualTo(Values.of(10));
    assertThat(maker.toValue(10D)).isEqualTo(Values.of(10));
  }

  @Test public void toValue_fromString() {
    assertThat(maker.toValue("42")).isEqualTo(Value.newBuilder().setStringValue("42").build());
  }

  @Test public void toValue_fromBoolean() {
    assertThat(maker.toValue(true)).isEqualTo(Value.newBuilder().setBoolValue(true).build());
    assertThat(maker.toValue(false)).isEqualTo(Value.newBuilder().setBoolValue(false).build());
    assertThat(maker.toValue(true)).isSameInstanceAs(maker.toValue(true));
    assertThat(maker.toValue(false)).isSameInstanceAs(maker.toValue(false));
  }

  @Test public void toValue_fromNull() {
    assertThat(maker.toValue(null))
        .isEqualTo(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
    assertThat(maker.toValue(null)).isSameInstanceAs(maker.toValue(null));
  }

  @Test public void toValue_fromEmptyOptional() {
    assertThat(maker.toValue(Optional.empty())).isEqualTo(Values.ofNull());
  }

  @Test public void toValue_fromNonEmptyOptional() {
    assertThat(maker.toValue(Optional.of(123))).isEqualTo(Values.of(123));
  }

  @Test public void toValue_fromValue() {
    Value value = Value.newBuilder().setBoolValue(false).build();
    assertThat(maker.toValue(value).getBoolValue()).isFalse();
  }

  @Test public void toValue_fromUnsupportedType() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> maker.toValue(this));
    assertThat(thrown).hasMessageThat().contains(getClass().getName());
  }

  @Test public void toValue_cyclic() {
    List<Object> list = new ArrayList<>();
    list.add(list);
    assertThrows(StackOverflowError.class, () -> maker.toValue(list));
  }

  @Test public void toValue_fromIntArray() {
    assertThat(maker.toValue(new int[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromImmutableIntArray() {
    assertThat(maker.toValue(ImmutableIntArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromLongArray() {
    assertThat(maker.toValue(new long[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromImmutableLongArray() {
    assertThat(maker.toValue(ImmutableLongArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromDoubleArray() {
    assertThat(maker.toValue(new double[] {10}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromImmutableDoubleArray() {
    assertThat(maker.toValue(ImmutableDoubleArray.of(10)))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10))));
  }

  @Test public void toValue_fromByteArray() {
    assertThat(maker.toValue(new byte[] {10, 20}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10), Values.of(20))));
  }

  @Test public void toValue_fromShortArray() {
    assertThat(maker.toValue(new short[] {10, 20}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of(10), Values.of(20))));
  }

  @Test public void toValue_fromArray() {
    assertThat(maker.toValue(new String[] {"foo", "bar"}))
        .isEqualTo(Values.of(ImmutableList.of(Values.of("foo"), Values.of("bar"))));
  }

  @Test public void toValue_fromEnum() {
    assertThat(maker.toValue(Cast.values()))
        .isEqualTo(Values.of(ImmutableList.of(Values.of("VILLAIN"), Values.of("HERO"))));
  }

  @Test public void customConversion() {
    Structor custom = new Structor() {
      @Override public Value toValue(Object obj) {
        if (obj instanceof Hero) {
          Hero hero = (Hero) obj;
          return toValue(ImmutableMap.of("name", hero.name, "titles", hero.titles, "friends", hero.friends));
        }
        return super.toValue(obj);
      }
    };
    Hero ironMan = new Hero("Tony Stark", "Iron Man", "Robert Downey");
    Hero scarletWitch = new Hero("Wanda", "Scarlet Witch");
    ironMan.friends.add(scarletWitch);
    scarletWitch.friends.add(new Hero("Vision"));
    assertThat(custom.toValue(Optional.of(ironMan)))
        .isEqualTo(Values.of(custom.struct(
                "name", "Tony Stark",
                "titles", ImmutableList.of("Iron Man", "Robert Downey"),
                "friends", ImmutableList.of(custom.struct(
                    "name", "Wanda",
                    "titles", ImmutableList.of("Scarlet Witch"),
                    "friends", ImmutableList.of(custom.struct(
                        "name", "Vision",
                        "titles", ImmutableList.of(),
                        "friends", ImmutableList.of())))))));
  }

  @Test public void struct_onePair() {
    assertThat(new Structor().struct("key", 1))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.of(1))
                .build());
  }

  @Test public void struct_onePair_nullKey() {
    assertThrows(NullPointerException.class, () -> struct(null, "v"));
  }

  @Test public void struct_onePair_nullValue() {
    assertThat(new Structor().struct("key", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("key", Values.ofNull())
                .build());
  }

  @Test public void struct_twoPairs() {
    assertThat(new Structor().struct("int", 1, "string", "two"))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("int", Values.of(1))
                .putFields("string", Values.of("two"))
                .build());
  }

  @Test public void struct_twoPairs_nullKey() {
    Structor maker = new Structor();
    assertThrows(NullPointerException.class, () -> maker.struct(null, "v1", "k2", "v2"));
  }

  @Test public void struct_twoPairs_nullValue() {
    assertThat(new Structor().struct("k1", null, "k2", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .build());
  }

  @Test public void struct_twoPairs_duplicateKey() {
    Structor maker = new Structor();
    assertThrows(IllegalArgumentException.class, () -> maker.struct("a", 1, "a", 2));
  }

  @Test public void struct_3Pairs() {
    assertThat(new Structor().struct("a", 1, "b", 2, "c", 3))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("a", Values.of(1))
                .putFields("b", Values.of(2))
                .putFields("c", Values.of(3))
                .build());
  }

  @Test public void struct_3Pairs_nullKey() {
    Structor maker = new Structor();
    assertThrows(NullPointerException.class, () -> maker.struct("k1", "v1", "k2", "v2", null, 3));
  }

  @Test public void struct_3Pairs_nullValue() {
    assertThat(new Structor().struct("k1", null, "k2", null, "k3", null))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("k1", Values.ofNull())
                .putFields("k2", Values.ofNull())
                .putFields("k3", Values.ofNull())
                .build());
  }

  @Test public void struct_3Pairs_duplicateKey() {
    Structor maker = new Structor();
    assertThrows(IllegalArgumentException.class, () -> maker.struct("a", 1, "b", 2, "a", 3));
  }

  @Test public void struct_fromMap() {
    assertThat(new Structor().struct(ImmutableMap.of("int", 1, "string", "two")))
        .isEqualTo(
            Struct.newBuilder()
                .putFields("int", Values.of(1))
                .putFields("string", Values.of("two"))
                .build());
  }

  @Test public void nestedStruct_fromTable() {
    assertThat(new Structor().nestedStruct(ImmutableTable.of("row", "col", 1)))
        .isEqualTo(struct("row", struct("col", 1)));
  }


  @Test public void toStruct_biCollector() {
    Struct struct = BiStream.of("foo", 1, "bar", ImmutableMap.of("one", true)).collect(new Structor().toStruct());
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

  @Test public void testNulls() {
    CharSequence key = new CharSequence() {
      private int toStringCalled = 0;
      @Override public String toString() {
        return "string" + (++toStringCalled);
      }
      @Override public int length() {
        throw new UnsupportedOperationException();
      }
      @Override public char charAt(int index) {
        throw new UnsupportedOperationException();
      }
      @Override public CharSequence subSequence(int start, int end) {
        throw new UnsupportedOperationException();
      }
    };
    new NullPointerTester()
        .setDefault(CharSequence.class, key)
        .testAllPublicInstanceMethods(new Structor());
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

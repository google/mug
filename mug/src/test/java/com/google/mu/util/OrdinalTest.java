/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class OrdinalTest {
  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Ordinal.of(1), Ordinal.of(1))
        .addEqualityGroup(Ordinal.of(12), Ordinal.of(12))
        .addEqualityGroup(12)
        .testEquals();
  }

  @Test public void testComparable() {
    List<Ordinal> ordinals =
        Ordinal.natural().limit(100).collect(Collectors.toCollection(ArrayList::new));
    Collections.shuffle(ordinals);
    Collections.sort(ordinals);
    assertThat(ordinals).isInOrder();
    assertThat(ordinals.stream().map(Ordinal::toString).limit(7))
        .containsExactly("1st", "2nd", "3rd", "4th", "5th", "6th", "7th")
        .inOrder();
  }

  @Test public void testNatural() {
    assertThat(Ordinal.natural().limit(5).map(Ordinal::toString))
        .containsExactly("1st", "2nd", "3rd", "4th", "5th")
        .inOrder();
  }

  @Test public void singleDigit() {
    assertThat(name(1)).isEqualTo("1st");
    assertThat(name(2)).isEqualTo("2nd");
    assertThat(name(3)).isEqualTo("3rd");
    assertThat(name(4)).isEqualTo("4th");
    assertThat(name(5)).isEqualTo("5th");
    assertThat(name(6)).isEqualTo("6th");
    assertThat(name(7)).isEqualTo("7th");
    assertThat(name(8)).isEqualTo("8th");
    assertThat(name(9)).isEqualTo("9th");
  }

  @Test public void teens() {
    assertThat(name(10)).isEqualTo("10th");
    assertThat(name(11)).isEqualTo("11th");
    assertThat(name(12)).isEqualTo("12th");
    assertThat(name(13)).isEqualTo("13th");
    assertThat(name(14)).isEqualTo("14th");
    assertThat(name(15)).isEqualTo("15th");
    assertThat(name(16)).isEqualTo("16th");
    assertThat(name(17)).isEqualTo("17th");
    assertThat(name(18)).isEqualTo("18th");
    assertThat(name(19)).isEqualTo("19th");
  }

  @Test public void tweens() {
    assertThat(name(20)).isEqualTo("20th");
    assertThat(name(21)).isEqualTo("21st");
    assertThat(name(22)).isEqualTo("22nd");
    assertThat(name(23)).isEqualTo("23rd");
    assertThat(name(24)).isEqualTo("24th");
    assertThat(name(25)).isEqualTo("25th");
    assertThat(name(26)).isEqualTo("26th");
    assertThat(name(27)).isEqualTo("27th");
    assertThat(name(28)).isEqualTo("28th");
    assertThat(name(29)).isEqualTo("29th");
  }

  @Test public void hundredTeens() {
    assertThat(name(110)).isEqualTo("110th");
    assertThat(name(111)).isEqualTo("111th");
    assertThat(name(112)).isEqualTo("112th");
    assertThat(name(113)).isEqualTo("113th");
    assertThat(name(114)).isEqualTo("114th");
    assertThat(name(115)).isEqualTo("115th");
    assertThat(name(116)).isEqualTo("116th");
    assertThat(name(117)).isEqualTo("117th");
    assertThat(name(118)).isEqualTo("118th");
    assertThat(name(119)).isEqualTo("119th");
  }

  @Test public void hundredTweens() {
    assertThat(name(120)).isEqualTo("120th");
    assertThat(name(121)).isEqualTo("121st");
    assertThat(name(122)).isEqualTo("122nd");
    assertThat(name(123)).isEqualTo("123rd");
    assertThat(name(124)).isEqualTo("124th");
    assertThat(name(125)).isEqualTo("125th");
    assertThat(name(126)).isEqualTo("126th");
    assertThat(name(127)).isEqualTo("127th");
    assertThat(name(128)).isEqualTo("128th");
    assertThat(name(129)).isEqualTo("129th");
  }

  @Test public void invalidOrdinal() {
    assertThrows(IllegalArgumentException.class, () -> Ordinal.of(0));
    assertThrows(IllegalArgumentException.class, () -> Ordinal.of(-1));
  }

  @Test public void testOf() {
    for (int i = 1; i < 1000; i++) {
      assertThat(Ordinal.of(i).toIndex()).isEqualTo(i - 1);
    }
  }

  @Test public void testOf_enum() {
    assertThat(Ordinal.of(Fruit.APPLE)).isEqualTo(Ordinal.first());
    assertThat(Ordinal.of(Fruit.ORANGE)).isEqualTo(Ordinal.second());
    assertThat(Ordinal.of(Fruit.APPLE).next()).isEqualTo(Ordinal.of(Fruit.ORANGE));
    assertThat(Ordinal.of(Fruit.ORANGE).previous()).isEqualTo(Ordinal.of(Fruit.APPLE));
  }

  @Test public void interning() {
    assertThat(Ordinal.first()).isSameInstanceAs(Ordinal.first());
    for (int i = 1; i < 100; i++) {
      assertThat(Ordinal.of(i)).isSameInstanceAs(Ordinal.of(i));
    }
  }

  @Test public void fromIndex() {
    for (int i = 0; i < 1000; i++) {
      assertThat(Ordinal.fromIndex(i).toIndex()).isEqualTo(i);
    }
  }

  @Test public void fromIndex_overflow() {
    assertThrows(IllegalArgumentException.class, () -> Ordinal.fromIndex(Integer.MAX_VALUE));
  }

  @Test public void fromIndex_negative() {
    assertThrows(IllegalArgumentException.class, () -> Ordinal.fromIndex(-1));
    assertThrows(IllegalArgumentException.class, () -> Ordinal.fromIndex(-2));
    assertThrows(IllegalArgumentException.class, () -> Ordinal.fromIndex(Integer.MIN_VALUE));
  }

  @Test public void toIndex() {
    List<Ordinal> ordinals = Ordinal.natural().limit(100).collect(toList());
    for (Ordinal ordinal : ordinals) {
      assertThat(ordinals.get(ordinal.toIndex())).isSameInstanceAs(ordinal);
    }
  }

  @Test public void maxValue() {
    assertThat(name(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE + "th");
    assertThat(Ordinal.first().previous().next()).isEqualTo(Ordinal.first());
  }

  @Test public void testNext() {
    assertThat(Ordinal.first().next()).isEqualTo(Ordinal.second());
    assertThat(Ordinal.first().previous().next()).isEqualTo(Ordinal.first());
  }

  @Test public void testNext_overflow() {
    assertThat(Ordinal.MAX_VALUE.next()).isEqualTo(Ordinal.first());
  }

  @Test public void testPrevious() {
    assertThat(Ordinal.first().next().previous()).isEqualTo(Ordinal.first());
    assertThat(Ordinal.MAX_VALUE.next().previous()).isEqualTo(Ordinal.MAX_VALUE);
  }

  @Test public void testPrevious_underflow() {
    assertThat(Ordinal.first().previous()).isEqualTo(Ordinal.MAX_VALUE);
  }

  @Test public void testMinus_sameValue() {
    assertThat(Ordinal.first().minus(Ordinal.first())).isEqualTo(0);
    assertThat(Ordinal.MAX_VALUE.minus(Ordinal.MAX_VALUE)).isEqualTo(0);
  }

  @Test public void testMinus() {
    assertThat(Ordinal.first().minus(Ordinal.second())).isEqualTo(-1);
    assertThat(Ordinal.second().minus(Ordinal.of(1))).isEqualTo(1);
    assertThat(Ordinal.MAX_VALUE.minus(Ordinal.first())).isEqualTo(Integer.MAX_VALUE - 1);
    assertThat(Ordinal.first().minus(Ordinal.MAX_VALUE)).isEqualTo(1 - Integer.MAX_VALUE);
  }

  private static String name(int n) {
    return Ordinal.of(n).toString();
  }

  private enum Fruit {APPLE, ORANGE}
}

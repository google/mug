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
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class BiOptionalTest {
  @Test
  public void empty_toString() {
    assertThat(BiOptional.empty().toString()).isEqualTo("empty()");
  }

  @Test
  public void empty_singleton() {
    assertThat(BiOptional.empty()).isSameAs(BiOptional.empty());
  }

  @Test
  public void map_empty() {
    assertThat(BiOptional.empty().map((a, b) -> "test")).isEmpty();
  }

  @Test
  public void map_nonEmpty() {
    assertThat(BiOptional.of(1, 2).map((a, b) -> a + b)).hasValue(3);
  }

  @Test
  public void map_nullResultToEmpty() {
    assertThat(BiOptional.of(1, 2).map((a, b) -> null)).isEmpty();
  }

  @Test
  public void flatMap_empty() {
    assertThat(BiOptional.empty().flatMap((a, b) -> Optional.of("test"))).isEmpty();
  }

  @Test
  public void flatMap_emptyResultMappedToEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> Optional.empty())).isEmpty();
  }

  @Test
  public void flatMap_nonEmptyMappedToNonEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> Optional.of(a + b))).hasValue(3);
  }

  @Test
  public void flatMap_nullResultThrows() {
    assertThrows(NullPointerException.class, () -> BiOptional.of(1, 2).flatMap((a, b) -> null));
  }

  @Test
  public void filter_empty() {
    assertThat(BiOptional.empty().filter((a, b) -> true)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void filter_nonEmptyFilteredOut() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a > b)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void filter_nonEmptyFilteredIn() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a < b)).isEqualTo(BiOptional.of(1, 2));
  }

  @Test
  public void or_emptyOrEmpty() {
    assertThat(BiOptional.empty().or(BiOptional::empty)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void or_emptyOrNonEmptyReturnsAlternative() {
    assertThat(BiOptional.empty().or(() -> BiOptional.of(1, "one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void or_nonEmptyOrEmptyReturnsOriginal() {
    assertThat(BiOptional.of(1, "one").or(BiOptional::empty)).isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void or_nonEmptyOrNonEmptyReturnsOriginal() {
    assertThat(BiOptional.of(1, "one").or(() -> BiOptional.of(2, "two")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void ifPresent_empty() {
    BiOptional.empty()
        .ifPresent(
            (a, b) -> {
              throw new AssertionError();
            });
  }

  @Test
  public void ifPresent_nonEmpty() {
    AtomicInteger x = new AtomicInteger();
    AtomicReference<String> y = new AtomicReference<>();
    BiOptional.of(1, "one")
        .ifPresent(
            (a, b) -> {
              x.set(a);
              y.set(b);
            });
    assertThat(x.get()).isEqualTo(1);
    assertThat(y.get()).isEqualTo("one");
  }

  @Test
  public void isPresent_empty() {
    assertThat(BiOptional.empty().isPresent()).isFalse();
  }

  @Test
  public void isPresent_nonEmpty() {
    assertThat(BiOptional.of(1, 2).isPresent()).isTrue();
  }

  @Test
  public void both_bothEmpty() {
    assertThat(BiOptional.both(Optional.empty(), Optional.empty())).isEqualTo(BiOptional.empty());
  }

  @Test
  public void both_oneIsEmpty() {
    assertThat(BiOptional.both(Optional.empty(), Optional.of("one"))).isEqualTo(BiOptional.empty());
    assertThat(BiOptional.both(Optional.of(1), Optional.empty())).isEqualTo(BiOptional.empty());
  }

  @Test
  public void both_noneEmpty() {
    assertThat(BiOptional.both(Optional.of(1), Optional.of("one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void staticFlatMap_fromEmpty() {
    assertThat(BiOptional.flatMap(Optional.empty(), t -> BiOptional.of(t, t)))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void staticFlatMap_mapToEmpty() {
    assertThat(BiOptional.flatMap(Optional.of(1), t -> BiOptional.empty()))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void staticFlatMap_mapToNonEmpty() {
    assertThat(BiOptional.flatMap(Optional.of(1), t -> BiOptional.of(t, t)))
        .isEqualTo(BiOptional.of(1, 1));
  }

  @Test
  public void mapPair_fromEmpty() {
    assertThat(BiOptional.from(Optional.empty()).map(l -> l, r -> r)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void mapPair_fromNonEmpty() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> l, r -> r * 10))
        .isEqualTo(BiOptional.of(1, 10));
  }

  @Test
  public void mapPair_keyMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> null, r -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void mapPair_valueMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> l, r -> null))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void binaryMapPair_fromEmpty() {
    assertThat(BiOptional.from(Optional.empty()).map((l, r) -> l, (l, r) -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void binaryMapPair_fromNonEmpty() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> l, (l, r) -> r * 10))
        .isEqualTo(BiOptional.of(1, 10));
  }

  @Test
  public void binaryMapPair_keyMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> null, (l, r) -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void binaryMapPair_valueMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> l, (l, r) -> null))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void testOrElseThrow_npe() {
    assertThrows(NullPointerException.class, () -> BiOptional.empty().orElseThrow(() -> null));
  }

  @Test
  public void testOrElseThrow_empty() {
    Exception e = new Exception("test");
    Exception thrown = assertThrows(Exception.class, () -> BiOptional.empty().orElseThrow(() -> e));
    assertThat(thrown).isSameAs(e);
  }

  @Test
  public void testOrElseThrow_notEmpty() {
    assertThat(BiOptional.of("foo", "bar").orElseThrow().combine(String::concat))
        .isEqualTo("foobar");
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(BiOptional.class);
    new NullPointerTester().testAllPublicInstanceMethods(BiOptional.empty());
    new NullPointerTester().testAllPublicInstanceMethods(BiOptional.of(1, "one"));
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(BiOptional.empty(), BiOptional.of(1, 2).filter((a, b) -> a > b))
        .addEqualityGroup(BiOptional.of(1, "one"), BiOptional.of(1, "one").filter((a, b) -> true))
        .addEqualityGroup(BiOptional.of(1, "two"))
        .addEqualityGroup(BiOptional.of(2, "one"))
        .addEqualityGroup(Optional.empty())
        .addEqualityGroup(Optional.of(1))
        .testEquals();
  }
}

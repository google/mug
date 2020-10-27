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
  public void join_empty() {
    assertThat(BiOptional.empty().join((a, b) -> "test")).isEmpty();
  }

  @Test
  public void join_notEmpty() {
    assertThat(BiOptional.of(1, 2).join((a, b) -> a + b)).hasValue(3);
  }

  @Test
  public void join_notEmptyMappedToNull() {
    assertThat(BiOptional.of(1, 2).join((a, b) -> null)).isEmpty();
  }

  @Test
  public void flatJoin_empty() {
    assertThat(BiOptional.empty().flatJoin((a, b) -> Optional.of("test"))).isEmpty();
  }

  @Test
  public void flatJoin_notEmptyMappedToEmpty() {
    assertThat(BiOptional.of(1, 2).flatJoin((a, b) -> Optional.empty())).isEmpty();
  }

  @Test
  public void flatJoin_notEmptyMappedToNonEmpty() {
    assertThat(BiOptional.of(1, 2).flatJoin((a, b) -> Optional.of(a + b))).hasValue(3);
  }

  @Test
  public void flatJoin_notEmptyMappedToNull() {
    assertThrows(NullPointerException.class, () -> BiOptional.of(1, 2).flatJoin((a, b) -> null));
  }

  @Test
  public void flatMap_empty() {
    assertThat(BiOptional.empty().flatMap((a, b) -> BiOptional.of(b, a)))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void flatMap_notEmptyMappedToEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> BiOptional.empty()))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void flatMap_notEmptyMappedToNonEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> BiOptional.of(b, a)))
        .isEqualTo(BiOptional.of(2, 1));
  }

  @Test
  public void flatMap_notEmptyMappedToNull() {
    assertThrows(NullPointerException.class, () -> BiOptional.of(1, 2).flatMap((a, b) -> null));
  }

  @Test
  public void filter_empty() {
    assertThat(BiOptional.empty().filter((a, b) -> true)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void filter_notEmptyFilteredOut() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a > b)).isEqualTo(BiOptional.empty());
  }

  @Test
  public void filter_notEmptyFilteredIn() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a < b)).isEqualTo(BiOptional.of(1, 2));
  }

  @Test
  public void or_emptyOrEmpty() {
    assertThat(BiOptional.empty().or(BiOptional::empty))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void or_emptyOrNonEmpty() {
    assertThat(BiOptional.empty().or(() -> BiOptional.of(1, "one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void or_nonEmptyOrEmpty() {
    assertThat(BiOptional.of(1, "one").or(BiOptional::empty))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void or_nonEmptyOrNonEmpty() {
    assertThat(BiOptional.of(1, "one").or(() -> BiOptional.of(2, "two")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void match_empty() {
    assertThat(BiOptional.empty().match((a, b) -> true)).isFalse();
  }

  @Test
  public void match_nonEmptyDoesNotMatch() {
    assertThat(BiOptional.of(1, 2).match((a, b) -> a > b)).isFalse();
  }

  @Test
  public void match_nonEmptyMatches() {
    assertThat(BiOptional.of(1, 2).match((a, b) -> a < b)).isTrue();
  }

  @Test
  public void ifPresent_empty() {
    BiOptional.empty().ifPresent((a, b) -> {throw new AssertionError();});
  }

  @Test
  public void ifPresent_notEmpty() {
    AtomicInteger x = new AtomicInteger();
    AtomicReference<String> y = new AtomicReference<>();
    BiOptional.of(1, "one").ifPresent((a, b) -> {
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
  public void isPresent_notEmpty() {
    assertThat(BiOptional.of(1, 2).isPresent()).isTrue();
  }

  @Test
  public void stream_empty() {
    assertThat(BiOptional.empty().stream().toMap()).isEmpty();
  }

  @Test
  public void stream_notEmpty() {
    assertThat(BiOptional.of(1, "one").stream().toMap()).containsExactly(1, "one");
  }

  @Test
  public void both_bothEmpty() {
    assertThat(BiOptional.both(Optional.empty(), Optional.empty()))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void both_oneIsEmpty() {
    assertThat(BiOptional.both(Optional.empty(), Optional.of("one")))
        .isEqualTo(BiOptional.empty());
    assertThat(BiOptional.both(Optional.of(1), Optional.empty()))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void both_noneEmpty() {
    assertThat(BiOptional.both(Optional.of(1), Optional.of("one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test
  public void from_empty() {
    assertThat(BiOptional.from(Optional.empty(), l -> l, r -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test
  public void from_nonEmpty() {
    assertThat(BiOptional.from(Optional.of(1), l -> l, r -> r * 10))
        .isEqualTo(BiOptional.of(1, 10));
  }

  @Test
  public void from_nonEmpty_cannotMapToNull() {
    assertThrows(
        NullPointerException.class,
        () -> BiOptional.from(Optional.of(1), l -> null, r -> r));
    assertThrows(
        NullPointerException.class,
        () -> BiOptional.from(Optional.of(1), l -> l, r -> null));
  }

  @Test
  public void testNulls() {
    new NullPointerTester()
        .testAllPublicStaticMethods(BiOptional.class);
    new NullPointerTester()
        .testAllPublicInstanceMethods(BiOptional.empty());
    new NullPointerTester()
        .testAllPublicInstanceMethods(BiOptional.of(1, "one"));
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

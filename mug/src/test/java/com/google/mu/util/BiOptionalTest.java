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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class BiOptionalTest {
  @Mock private BiConsumer<Object, Object> consumer;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void empty_toString() {
    assertThat(BiOptional.empty().toString()).isEqualTo("empty()");
  }

  @Test public void empty_singleton() {
    assertThat(BiOptional.empty()).isSameAs(BiOptional.empty());
  }

  @Test public void map_empty() {
    assertThat(BiOptional.empty().map((a, b) -> "test")).isEmpty();
  }

  @Test public void map_nonEmpty() {
    assertThat(BiOptional.of(1, 2).map((a, b) -> a + b)).hasValue(3);
  }

  @Test public void map_nullResultToEmpty() {
    assertThat(BiOptional.of(1, 2).map((a, b) -> null)).isEmpty();
  }

  @Test public void flatMap_empty() {
    assertThat(BiOptional.empty().flatMap((a, b) -> Optional.of("test"))).isEmpty();
  }

  @Test public void flatMap_emptyResultMappedToEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> Optional.empty())).isEmpty();
  }

  @Test public void flatMap_nonEmptyMappedToNonEmpty() {
    assertThat(BiOptional.of(1, 2).flatMap((a, b) -> Optional.of(a + b))).hasValue(3);
  }

  @Test public void flatMap_nullResultThrows() {
    assertThrows(NullPointerException.class, () -> BiOptional.of(1, 2).flatMap((a, b) -> null));
  }

  @Test public void filter_empty() {
    assertThat(BiOptional.empty().filter((a, b) -> true)).isEqualTo(BiOptional.empty());
  }

  @Test public void filter_nonEmptyFilteredOut() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a > b)).isEqualTo(BiOptional.empty());
  }

  @Test public void filter_nonEmptyFilteredIn() {
    assertThat(BiOptional.of(1, 2).filter((a, b) -> a < b)).isEqualTo(BiOptional.of(1, 2));
  }

  @Test public void skipIf_empty() {
    assertThat(BiOptional.empty().skipIf((a, b) -> false)).isEqualTo(BiOptional.empty());
  }

  @Test public void skipIf_nonEmptyFilteredOut() {
    assertThat(BiOptional.of(1, 2).skipIf((a, b) -> a < b)).isEqualTo(BiOptional.empty());
  }

  @Test public void skipIf_nonEmptyFilteredIn() {
    assertThat(BiOptional.of(1, 2).skipIf((a, b) -> a >= b)).isEqualTo(BiOptional.of(1, 2));
  }

  @Test public void peek_empty() {
    assertThat(BiOptional.empty().peek(consumer)).isEqualTo(BiOptional.empty());
    verifyZeroInteractions(consumer);
  }

  @Test public void orElse_peek_empty() {
    assertThat(
            BiOptional.<String, String>empty()
                .orElse("foo", "bar")
                .peek(consumer)
                .andThen(String::concat))
        .isEqualTo("foobar");
    verify(consumer).accept("foo", "bar");
  }

  @Test public void peek_nonEmpty() {
    BiOptional<?, ?> optional = BiOptional.of("foo", "bar");
    assertThat(optional.peek(consumer)).isSameAs(optional);
    verify(consumer).accept("foo", "bar");
  }

  @Test public void orElse_peek_nonEmpty() {
    BiOptional<String, String> optional = BiOptional.of("foo", "bar");
    assertThat(optional.orElse("x", null).peek(consumer)).isSameAs(optional);
    verify(consumer).accept("foo", "bar");
  }

  @Test public void or_emptyOrEmpty() {
    assertThat(BiOptional.empty().or(BiOptional::empty)).isEqualTo(BiOptional.empty());
  }

  @Test public void or_emptyOrNonEmptyReturnsAlternative() {
    assertThat(BiOptional.empty().or(() -> BiOptional.of(1, "one")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test public void or_nonEmptyOrEmptyReturnsOriginal() {
    assertThat(BiOptional.of(1, "one").or(BiOptional::empty)).isEqualTo(BiOptional.of(1, "one"));
  }

  @Test public void or_nonEmptyOrNonEmptyReturnsOriginal() {
    assertThat(BiOptional.of(1, "one").or(() -> BiOptional.of(2, "two")))
        .isEqualTo(BiOptional.of(1, "one"));
  }

  @Test public void ifPresent_empty() {
    BiOptional.empty()
        .ifPresent(
            (a, b) -> {
              throw new AssertionError();
            });
  }

  @Test public void ifPresent_nonEmpty() {
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

  @Test public void isPresent_empty() {
    assertThat(BiOptional.empty().isPresent()).isFalse();
  }

  @Test public void isPresent_nonEmpty() {
    assertThat(BiOptional.of(1, 2).isPresent()).isTrue();
  }

  @Test public void staticFlatMap_fromEmpty() {
    assertThat(BiOptional.flatMap(Optional.empty(), t -> BiOptional.of(t, t)))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void staticFlatMap_mapToEmpty() {
    assertThat(BiOptional.flatMap(Optional.of(1), t -> BiOptional.empty()))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void staticFlatMap_mapToNonEmpty() {
    assertThat(BiOptional.flatMap(Optional.of(1), t -> BiOptional.of(t, t)))
        .isEqualTo(BiOptional.of(1, 1));
  }

  @Test public void mapPair_fromEmpty() {
    assertThat(BiOptional.from(Optional.empty()).map(l -> l, r -> r)).isEqualTo(BiOptional.empty());
  }

  @Test public void mapPair_fromNonEmpty() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> l, r -> r * 10))
        .isEqualTo(BiOptional.of(1, 10));
  }

  @Test public void mapPair_keyMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> null, r -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void mapPair_valueMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map(l -> l, r -> null))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void binaryMapPair_fromEmpty() {
    assertThat(BiOptional.from(Optional.empty()).map((l, r) -> l, (l, r) -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void binaryMapPair_fromNonEmpty() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> l, (l, r) -> r * 10))
        .isEqualTo(BiOptional.of(1, 10));
  }

  @Test public void binaryMapPair_keyMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> null, (l, r) -> r))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void binaryMapPair_valueMapsToNull() {
    assertThat(BiOptional.from(Optional.of(1)).map((l, r) -> l, (l, r) -> null))
        .isEqualTo(BiOptional.empty());
  }

  @Test public void testOrElse_empty() {
    String result = BiOptional.empty().orElse("foo", "bar").andThen((a, b) -> a + ":" + b);
    assertThat(result).isEqualTo("foo:bar");
  }

  @Test public void testOrElse_nonEmpty() {
    int result = BiOptional.of(1, 2).orElse(30, 40).andThen(Integer::sum);
    assertThat(result).isEqualTo(3);
  }

  @Test public void testOrElse_nullAlternatives() {
    String result = BiOptional.empty().orElse(null, null).andThen((a, b) -> a + ":" + b);
    assertThat(result).isEqualTo("null:null");
  }

  @Test public void testOrElseThrow_npe() {
    assertThrows(NullPointerException.class, () -> BiOptional.empty().orElseThrow(() -> null));
  }

  @Test public void testOrElseThrow_empty() {
    Exception e = new Exception("test");
    Exception thrown = assertThrows(Exception.class, () -> BiOptional.empty().orElseThrow(() -> e));
    assertThat(thrown).isSameAs(e);
  }

  @Test public void testOrElseThrow_notEmpty() {
    assertThat(BiOptional.of("foo", "bar").orElseThrow().andThen(String::concat))
        .isEqualTo("foobar");
    assertThat(BiOptional.of("foo", "bar").orElseThrow().filter(String::equals))
        .isEqualTo(BiOptional.empty());
    assertThat(BiOptional.of("foo", "foo").orElseThrow().filter(String::equals))
        .isEqualTo(BiOptional.of("foo", "foo"));
    assertThat(BiOptional.of("foo", "bar").orElseThrow().matches(String::equals))
        .isFalse();
    assertThat(BiOptional.of("foo", "foo").orElseThrow().matches(String::equals))
        .isTrue();
  }

  @Test
  public void testOrElseThrow_withMessage_present() {
    assertThat(BiOptional.of(1, 2).orElseThrow(IllegalStateException::new, "bad"))
        .isEqualTo(BiOptional.of(1, 2));
  }

  @Test
  public void testOrElseThrow_withMessage_npe() {
    assertThrows(NullPointerException.class, () -> BiOptional.empty().orElseThrow(msg -> null, ""));
  }

  @Test
  public void testOrElseThrow_withMessage_empty() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> BiOptional.empty().orElseThrow(IllegalArgumentException::new, "my rank=%s", 1));
    assertThat(thrown).hasMessageThat().isEqualTo("my rank=1");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(BiOptional.class);
    testNullChecks(BiOptional.empty());
    testNullChecks(BiOptional.of(1, "one"));
  }

  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(BiOptional.empty(), BiOptional.of(1, 2).filter((a, b) -> a > b))
        .addEqualityGroup(BiOptional.of(1, "one"), BiOptional.of(1, "one").filter((a, b) -> true))
        .addEqualityGroup(BiOptional.of(1, "two"))
        .addEqualityGroup(BiOptional.of(2, "one"))
        .addEqualityGroup(Optional.empty())
        .addEqualityGroup(Optional.of(1))
        .addEqualityGroup(BiOptional.empty().orElse(null, null))
        .addEqualityGroup(BiOptional.empty().orElse("foo", "bar"))
        .addEqualityGroup(BiOptional.empty().orElse("foo", "zoo"))
        .addEqualityGroup(BiOptional.empty().orElse("zoo", "bar"))
        .addEqualityGroup(BiOptional.of("x", "y").orElse("foo", "bar"))
        .addEqualityGroup(BiOptional.of("x", "z").orElse("foo", "bar"))
        .addEqualityGroup(BiOptional.of("z", "y").orElse("foo", "bar"))
        .testEquals();
  }

  private static void testNullChecks(BiOptional<?, ?> optional) throws Exception {
    new NullPointerTester()
        .ignore(optional.getClass().getMethod("orElse", Object.class, Object.class))
        .testAllPublicInstanceMethods(optional);
  }
}

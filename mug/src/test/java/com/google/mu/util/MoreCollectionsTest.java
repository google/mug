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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.MoreCollections.findFirstElements;
import static com.google.mu.util.MoreCollections.findOnlyElements;
import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class MoreCollectionsTest {
  @TestParameter private CollectionType makeCollection;

  @Test public void testFindOnlyTwoElements() {
    assertThat(findOnlyElements(makeCollection.of(1, 10), Integer::sum)).hasValue(11);
    assertThat(findOnlyElements(makeCollection.of(1), Integer::sum)).isEmpty();
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3), Integer::sum)).isEmpty();
  }

  @Test public void testFindOnlyThreeElements() {
    assertThat(findOnlyElements(makeCollection.of(1, 3, 5), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findOnlyElements(makeCollection.of(1, 2), (a, b, c) -> a + b + c)).isEmpty();
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4), (a, b, c) -> a + b + c)).isEmpty();
  }

  @Test public void testFindOnlyFourElements() {
    assertThat(findOnlyElements(makeCollection.of(1, 3, 5, 7), (a, b, c, d) -> a + b + c + d)).hasValue(16);
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3), (a, b, c, d) -> a + b + c + d)).isEmpty();
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d) -> a + b + c)).isEmpty();
  }

  @Test public void testFindOnlyFiveElements() {
    assertThat(findOnlyElements(makeCollection.of(1, 3, 5, 7, 9), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4), (a, b, c, d, e) -> a + b + c + d + e))
        .isEmpty();
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4, 5, 6), (a, b, c, d, e) -> a + b + c + e))
        .isEmpty();
  }

  @Test public void testFindOnlySixElements() {
    assertThat(findOnlyElements(makeCollection.of(1, 3, 5, 7, 9, 11), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(findOnlyElements(makeCollection.of(1, 2, 3, 4, 5, 6, 7), (a, b, c, d, e, f) -> a + b + c + e + f))
        .isEmpty();
  }

  @Test
  public void testFindOnlySevenElements() {
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13),
                (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .hasValue(49);
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d, e, f, g) -> a + b + c + d + e + f))
        .isEmpty();
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 2, 3, 4, 5, 6, 7, 8),
                (a, b, c, d, e, f, g) -> a + b + c + e + f))
        .isEmpty();
  }

  @Test
  public void testFindOnlyEightElements() {
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13, 15),
                (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .hasValue(64);
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 2, 3, 4, 5),
                (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + h))
        .isEmpty();
    assertThat(
            findOnlyElements(
                makeCollection.of(1, 2, 3, 4, 5, 6, 7, 8, 9),
                (a, b, c, d, e, f, g, h) -> a + b + c + e + f + h))
        .isEmpty();
  }

  @Test public void testFindFirstTwoElements() {
    assertThat(findFirstElements(makeCollection.of(1, 2), (a, b) -> a + b)).hasValue(3);
    assertThat(findFirstElements(makeCollection.of(1, 3, 5), (a, b) -> a + b)).hasValue(4);
    assertThat(findFirstElements(makeCollection.of(1), (a, b) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstThreeElements() {
    assertThat(findFirstElements(makeCollection.of(1, 3, 5), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7), (a, b, c) -> a + b + c)).hasValue(9);
    assertThat(findFirstElements(makeCollection.of(1, 2), (a, b, c) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstFourElements() {
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7), (a, b, c, d) -> a + b + c + d))
        .hasValue(16);
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7, 9), (a, b, c, d) -> a + b + c + d))
        .hasValue(16);
    assertThat(findFirstElements(makeCollection.of(1, 2, 3), (a, b, c, d) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstFiveElements() {
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7, 9), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7, 9, 11), (a, b, c, d, e) -> a + b + c + d + e))
        .hasValue(25);
    assertThat(findFirstElements(makeCollection.of(1, 2, 3, 4), (a, b, c, d, e) -> a + b)).isEmpty();
  }

  @Test public void testFindFirstSixElements() {
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7, 9, 11), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findFirstElements(makeCollection.of(1, 3, 5, 7, 9, 11, 13), (a, b, c, d, e, f) -> a + b + c + d + e + f))
        .hasValue(36);
    assertThat(findFirstElements(makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d, e, f) -> a + b)).isEmpty();
  }

  @Test
  public void testFindFirstSevenElements() {
    assertThat(
            findFirstElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13),
                (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .hasValue(49);
    assertThat(
            findFirstElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13, 15),
                (a, b, c, d, e, f, g) -> a + b + c + d + e + f + g))
        .hasValue(49);
    assertThat(findFirstElements(makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d, e, f, g) -> a + b))
        .isEmpty();
  }

  @Test
  public void testFindFirstEightElements() {
    assertThat(
            findFirstElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13, 15),
                (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .hasValue(64);
    assertThat(
            findFirstElements(
                makeCollection.of(1, 3, 5, 7, 9, 11, 13, 15, 17),
                (a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h))
        .hasValue(64);
    assertThat(
            findFirstElements(makeCollection.of(1, 2, 3, 4, 5), (a, b, c, d, e, f, g, h) -> a + b))
        .isEmpty();
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicStaticMethods(MoreCollections.class);
  }

  private enum CollectionType {
    ARRAY_LIST {
      @Override <T> Collection<T> of(@SuppressWarnings("unchecked") T... elements) {
        return asList(elements);
      }
    },
    LINKED_LIST {
      @Override <T> Collection<T> of(@SuppressWarnings("unchecked") T... elements) {
        return new LinkedList<>(asList(elements));
      }
    },
    IMMUTABLE_SET {
      @Override <T> Collection<T> of(@SuppressWarnings("unchecked") T... elements) {
        return ImmutableSet.copyOf(elements);
      }
    },
    UNMODIFIABLE_COLLECTION {
      @Override <T> Collection<T> of(@SuppressWarnings("unchecked") T... elements) {
        return Collections.unmodifiableCollection(asList(elements));
      }
    },
    ;

    abstract <T> Collection<T> of(@SuppressWarnings("unchecked") T... elements);
  }
}

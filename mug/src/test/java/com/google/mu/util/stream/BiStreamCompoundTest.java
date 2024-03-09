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
package com.google.mu.util.stream;

import static com.google.mu.util.stream.BiStreamTest.assertKeyValues;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

import java.util.Comparator;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMultimap;

@RunWith(JUnit4.class)
public class BiStreamCompoundTest {

  @Test public void testMappedValuesAndSortedByKeys() {
    assertKeyValues(
            BiStream.of("a", 1, "c", 2, "b", 3)
                .mapValues(Function.identity())
                .sortedByKeys(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 3, "c", 2))
        .inOrder();
  }

  @Test public void testMappedValuesAndSortedByValues() {
    assertKeyValues(
            BiStream.of("a", 3, "b", 1, "c", 2)
                .mapValues(Function.identity())
                .sortedByValues(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("b", 1, "c", 2, "a", 3))
        .inOrder();
  }

  @Test public void testMappedKeysAndSortedByKeys() {
    assertKeyValues(
            BiStream.of("a", 1, "c", 2, "b", 3)
                .mapKeys(Function.identity())
                .sortedByKeys(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 1, "b", 3, "c", 2))
        .inOrder();
  }

  @Test public void testMappedKeysAndSortedByValues() {
    assertKeyValues(
            BiStream.of("a", 3, "b", 1, "c", 2)
                .mapKeys(Function.identity())
                .sortedByValues(Comparator.naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("b", 1, "c", 2, "a", 3))
        .inOrder();
  }

  @Test public void testMappedKeysAndSorted() {
    assertKeyValues(
            BiStream.of("b", 10, "a", 11, "a", 22)
                .mapKeys(Function.identity())
                .sorted(comparing(Object::toString), naturalOrder()))
        .containsExactlyEntriesIn(ImmutableMultimap.of("a", 11, "a", 22, "b", 10))
        .inOrder();
  }
}

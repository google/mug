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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.stream.BiCollection.toBiCollection;
import static java.util.Arrays.asList;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.testing.NullPointerTester;
import com.google.common.truth.MultimapSubject;

@RunWith(JUnit4.class)
public class BiCollectionTest {

  @Test public void empty() {
    assertKeyValues(BiCollection.of()).isEmpty();
  }

  @Test public void onePair() {
    assertKeyValues(BiCollection.of(1, "one"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one"))
        .inOrder();
  }

  @Test public void twoDistinctPairs() {
    assertKeyValues(BiCollection.of(1, "one", 2, "two"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two"))
        .inOrder();
  }

  @Test public void twoPairsSameKey() {
    assertKeyValues(BiCollection.of(1, "1.1", 1, "1.2"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "1.1", 1, "1.2"))
        .inOrder();
  }

  @Test public void threePairs() {
    assertKeyValues(BiCollection.of(1, "one", 2, "two", 3, "three"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two", 3, "three"))
        .inOrder();
  }

  @Test public void fourPairs() {
    assertKeyValues(BiCollection.of(1, "1", 2, "2", 3, "3", 4, "4"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "1", 2, "2", 3, "3", 4, "4"))
        .inOrder();
  }

  @Test public void fivePairs() {
    assertKeyValues(BiCollection.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5"))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5"))
        .inOrder();
  }

  @Test public void fromEntries() {
    assertKeyValues(BiCollection.from(ImmutableListMultimap.of(1, "one", 2, "two").entries()))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two"))
        .inOrder();
  }

  @Test public void fromMap() {
    assertKeyValues(BiCollection.from(ImmutableMap.of(1, "one", 2, "two")))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two"))
        .inOrder();
  }

  @Test public void toBiCollectionWithoutCollectorStrategy() {
    BiCollection<Integer, String> biCollection = ImmutableMap.of(1, "one", 2, "two")
        .entrySet()
        .stream()
        .collect(toBiCollection(Map.Entry::getKey, Map.Entry::getValue));
    assertKeyValues(biCollection)
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two"))
        .inOrder();
  }

  @Test public void toBiCollectionWithCollectorStrategy() {
    BiCollection<Integer, String> biCollection = ImmutableMap.of(1, "one", 2, "two")
        .entrySet()
        .stream()
        .collect(toBiCollection(
            Map.Entry::getKey, Map.Entry::getValue, ImmutableList::toImmutableList));
    assertKeyValues(biCollection)
        .containsExactlyEntriesIn(ImmutableListMultimap.of(1, "one", 2, "two"))
        .inOrder();
  }

  @Test public void testBuilder_put() {
    assertKeyValues(new BiCollection.Builder<>().put("one", 1).build())
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
  }

  @Test public void testBuilder_putAll() {
    assertKeyValues(new BiCollection.Builder<>().putAll(ImmutableMap.of("one", 1)).build())
        .containsExactlyEntriesIn(ImmutableMultimap.of("one", 1))
        .inOrder();
  }

  @Test public void testNulls() {
    NullPointerTester tester = new NullPointerTester();
    asList(BiCollection.class.getDeclaredMethods()).stream()
        .filter(m -> m.getName().equals("of"))
        .forEach(tester::ignore);
    tester.testAllPublicStaticMethods(BiCollection.class);
    tester.testAllPublicInstanceMethods(BiCollection.of());
  }

  private static <K, V> MultimapSubject assertKeyValues(BiCollection<K, V> collection) {
    ImmutableListMultimap<K, V> multimap = collection.stream()
        .<ImmutableListMultimap<K, V>>collect(ImmutableListMultimap::toImmutableListMultimap);
    assertThat(collection.size()).isEqualTo(multimap.size());
    return assertThat(multimap);
  }
}

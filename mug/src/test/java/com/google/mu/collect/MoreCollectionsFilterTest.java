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
package com.google.mu.collect;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.collect.MoreCollections.filter;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreCollectionsFilterTest {

  @Test public void filter_emptyList() {
    assertThat(filter(List.of(), x -> true)).isEmpty();
  }

  @Test public void filter_sizeOne_matches() {
    assertThat(filter(List.of(1), x -> x > 0)).containsExactly(1);
  }

  @Test public void filter_sizeOne_noMatch() {
    assertThat(filter(List.of(1), x -> x < 0)).isEmpty();
  }

  @Test public void filter_sizeTwo_bothMatch() {
    assertThat(filter(List.of(1, 2), x -> x > 0)).containsExactly(1, 2).inOrder();
  }

  @Test public void filter_sizeTwo_oneMatches() {
    assertThat(filter(List.of(1, -2), x -> x > 0)).containsExactly(1);
  }

  @Test public void filter_sizeTwo_noneMatches() {
    assertThat(filter(List.of(-1, -2), x -> x > 0)).isEmpty();
  }

  @Test public void filter_sizeThree_allMatch() {
    assertThat(filter(List.of(1, 2, 3), x -> x > 0)).containsExactly(1, 2, 3).inOrder();
  }

  @Test public void filter_sizeThree_someMatch() {
    assertThat(filter(List.of(1, -2, 3), x -> x > 0)).containsExactly(1, 3).inOrder();
  }

  @Test public void filter_sizeThree_noneMatch() {
    assertThat(filter(List.of(-1, -2, -3), x -> x > 0)).isEmpty();
  }

  @Test public void filter_sizeFour() {
    assertThat(filter(List.of(1, -2, 3, -4), x -> x > 0)).containsExactly(1, 3).inOrder();
  }

  @Test public void filter_emptyList_returnsSameInstance() {
    List<Integer> list = List.of();
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeOne_allMatch_returnsSameInstance() {
    List<Integer> list = List.of(1);
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeTwo_allMatch_returnsSameInstance() {
    List<Integer> list = List.of(1, 2);
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeThree_allMatch_returnsSameInstance() {
    List<Integer> list = List.of(1, 2, 3);
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeThree_predicateEvaluatedExactlyOnce() {
    AtomicInteger count = new AtomicInteger();
    List<Integer> list = List.of(1, 2, 3);
    // 2 elements match, so size 3 and matchCount 2 (the fallback/non-early-return case for size 3)
    filter(list, x -> {
      count.incrementAndGet();
      return x != 2;
    });
    assertThat(count.get()).isEqualTo(3);
  }

  @Test public void filter_sizeFive_someMatch() {
    assertThat(filter(List.of(1, -2, 3, -4, 5), x -> x > 0)).containsExactly(1, 3, 5).inOrder();
  }

  @Test public void filter_sizeSixtyFour_allMatch_returnsSameInstance() {
    Integer[] array = new Integer[64];
    Arrays.fill(array, 1);
    List<Integer> list = Arrays.asList(array);
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeSixtyFour_predicateEvaluatedExactlyOnce() {
    Integer[] array = new Integer[64];
    Arrays.fill(array, 1);
    List<Integer> list = Arrays.asList(array);
    AtomicInteger count = new AtomicInteger();
    List<Integer> filtered = filter(list, x -> {
      count.incrementAndGet();
      return true;
    });
    assertThat(count.get()).isEqualTo(64);
    assertThat(filtered).isSameInstanceAs(list);
  }

  @Test public void filter_sizeSixtyFive_allMatch_returnsSameInstance() {
    Integer[] array = new Integer[65];
    Arrays.fill(array, 1);
    List<Integer> list = Arrays.asList(array);
    assertThat(filter(list, x -> true)).isSameInstanceAs(list);
  }

  @Test public void filter_sizeSixtyFive_someMatch() {
    Integer[] array = new Integer[65];
    Arrays.fill(array, 1);
    array[0] = -1;
    List<Integer> list = Arrays.asList(array);
    List<Integer> filtered = filter(list, x -> x > 0);
    assertThat(filtered).hasSize(64);
  }

  @Test public void filter_sizeSixtyThree_someMatch() {
    List<Integer> list = IntStream.range(0, 63).boxed().collect(toList());
    assertThat(filter(list, x -> x % 2 == 0))
        .containsExactlyElementsIn(range(0, 63).filter(i -> i % 2 == 0).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void filter_sizeSixtyFour_someMatch() {
    List<Integer> list = IntStream.range(0, 64).boxed().collect(toList());
    assertThat(filter(list, x -> x % 2 == 0))
        .containsExactlyElementsIn(range(0, 64).filter(i -> i % 2 == 0).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void filter_sizeSixtyFive_someMatch_exactElements() {
    List<Integer> list = IntStream.range(0, 65).boxed().collect(toList());
    assertThat(filter(list, x -> x % 2 == 0))
        .containsExactlyElementsIn(range(0, 65).filter(i -> i % 2 == 0).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void filter_supportsNullElements() {
    List<String> list = Arrays.asList("foo", null, "bar");
    assertThat(filter(list, x -> x == null || x.startsWith("f")))
        .containsExactly("foo", null)
        .inOrder();
  }

  @Test public void filter_nonRandomAccessList() {
    List<Integer> list = new LinkedList<>(List.of(1, -2, 3, -4, 5));
    assertThat(filter(list, x -> x > 0)).containsExactly(1, 3, 5).inOrder();
  }
}

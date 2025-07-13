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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.testing.ClassSanityTester;

public class BiIterationTest {
  @Test public void binarySearchTrials() {
    assertThat(binarySearchTrials(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 8))
        .containsExactly(0, 8, 5, 8, 7, 8)
        .inOrder();
    assertThat(binarySearchTrials(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 1))
      .containsExactly(0, 8, 0, 3, 0, 0)
      .inOrder();
    assertThat(binarySearchTrials(new int[] {10, 20, 30, 40, 50, 60, 70, 80, 90}, 35))
      .containsExactly(0, 8, 0, 3, 2, 3, 3, 3)
      .inOrder();
  }

  @Test public void oneTimeIteration() {
    BiIteration<String, String> iteration = new BiIteration<>();
    iteration.iterate();
    assertThrows(IllegalStateException.class, iteration::iterate);
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(BiIteration.class);
  }

  private static final class IterativeBinarySearch extends BiIteration<Integer, Integer> {
     IterativeBinarySearch search(int[] arr, int low, int high, int target) {
       if (low > high) {
         return this;
       }
       generate(low, high);
       int mid = (low + high) / 2;
       if (arr[mid] < target) {
        this.yield(() -> search(arr, mid + 1, high, target));
       } else if (arr[mid] > target) {
        this.yield(() -> search(arr, low, mid - 1, target));
       }
       return this;
     }
   }

   static ImmutableListMultimap<Integer, Integer> binarySearchTrials(int[] arr, int target) {
     return new IterativeBinarySearch()
     .search(arr, 0, arr.length - 1, target).iterate()
         .collect(ImmutableListMultimap::toImmutableListMultimap);
   }
}

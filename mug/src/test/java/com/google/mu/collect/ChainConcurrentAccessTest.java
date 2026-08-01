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
import static com.google.mu.util.stream.MoreStreams.indexesFrom;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import org.junit.Test;

import com.google.mu.util.concurrent.Parallelizer;

/**
 * Tests for concurrent access to Chain focusing on correct values.
 *
 * <p>These tests verify that concurrent readers always see correct data.
 * Since Chain is immutable, all threads should see the same correct values.
 *
 * <p>Note: Parallelizer automatically propagates exceptions from worker threads
 * to the main thread, enabling immediate test failure with clear error messages.
 *
 * <p>Note: Assertions are performed in the main thread after all workers
 * complete, to avoid thread-safety issues with assertion libraries.
 */
public class ChainConcurrentAccessTest {

  /**
   * Verifies multiple threads calling get() all get correct values.
   */
  @Test
  public void testConcurrentGet_returnsCorrectValues() throws Exception {
    Chain<String> chain = Chain.concat(
        Chain.of("alpha", "beta"),
        Chain.of("gamma", "delta", "epsilon"));

    int threadCount = 16;
    List<String> results = new CopyOnWriteArrayList<>();
    runConcurrently(
        threadCount,
        i -> {
          int index = i % 5;  // Access different indices
          String expectedValue = List.of("alpha", "beta", "gamma", "delta", "epsilon").get(index);
          String result = chain.get(index);
          // Parallelizer will propagate exceptions to main thread
          if (!result.equals(expectedValue)) {
            throw new AssertionError(
                "Expected '" + expectedValue + "' at index " + index + " but got '" + result + "'");
          }
          results.add(result);
        });

    // Assert in main thread after all workers complete
    assertThat(results).containsAtLeastElementsIn(
        List.of("alpha", "beta", "gamma", "delta", "epsilon"));
    assertThat(results).hasSize(threadCount);
  }

  /**
   * Verifies multiple threads iterating all see correct elements.
   */
  @Test
  public void testConcurrentIteration_allSeeCorrectElements() throws Exception {
    Chain<Integer> chain = Chain.concat(
        Chain.of(1, 2, 3),
        Chain.concat(Chain.of(4, 5), Chain.of(6, 7, 8, 9, 10)));

    int threadCount = 12;
    List<List<Integer>> collectedResults = new CopyOnWriteArrayList<>();

    runConcurrently(
        threadCount,
        i -> {
          List<Integer> threadResult = chain.stream().collect(Collectors.toList());
          collectedResults.add(threadResult);
        });

    // Every thread should see exactly [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    List<Integer> expected = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    for (List<Integer> threadResult : collectedResults) {
      assertThat(threadResult).containsExactlyElementsIn(expected).inOrder();
    }
    assertThat(collectedResults).hasSize(threadCount);
  }

  /**
   * Verifies multiple threads calling size() all see the same size.
   */
  @Test
  public void testConcurrentSize_allSeeSameSize() throws Exception {
    Chain<Integer> chain = Chain.concat(
        Chain.of(1, 2, 3, 4, 5),
        Chain.concat(Chain.of(6, 7, 8), Chain.of(9, 10)));

    int threadCount = 20;
    List<Integer> sizes = new CopyOnWriteArrayList<>();
    runConcurrently(threadCount, i -> sizes.add(chain.size()));
    // All threads should see size = 10
    assertThat(sizes).hasSize(threadCount);
    assertThat(sizes).doesNotContain(0);
    for (int size : sizes) {
      assertThat(size).isEqualTo(10);
    }
  }

  /**
   * Verifies multiple threads calling stream() all see correct elements.
   */
  @Test
  public void testConcurrentStream_allSeeCorrectElements() throws Exception {
    Chain<String> chain = Chain.concat(
        Chain.of("one", "two"),
        Chain.concat(Chain.of("three"), Chain.of("four", "five")));

    int threadCount = 15;
    List<List<String>> collectedResults = new CopyOnWriteArrayList<>();
    runConcurrently(
        threadCount,
        i -> collectedResults.add(chain.stream().collect(Collectors.toList())));

    // Every thread should see exactly ["one", "two", "three", "four", "five"]
    List<String> expected = List.of("one", "two", "three", "four", "five");
    for (List<String> threadResult : collectedResults) {
      assertThat(threadResult).containsExactlyElementsIn(expected).inOrder();
    }
    assertThat(collectedResults).hasSize(threadCount);
  }

  /**
   * Verifies mixed concurrent operations all return correct values.
   */
  @Test
  public void testConcurrentMixedOperations_allReturnCorrectValues() throws Exception {
    Chain<Integer> chain = Chain.concat(
        Chain.of(10, 20, 30, 40, 50),
        Chain.of(60, 70, 80, 90, 100));

    int threadCount = 24;

    // Track results from different operation types
    List<Integer> getSizeResults = new CopyOnWriteArrayList<>();
    List<Integer> getResults = new CopyOnWriteArrayList<>();
    List<List<Integer>> streamResults = new CopyOnWriteArrayList<>();

    List<Integer> validValues = List.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);

    runConcurrently(
        threadCount,
        threadId -> {
          // Different threads perform different operations
          switch (threadId % 3) {
            case 0:  // size()
              int size = chain.size();
              if (size != 10) {
                throw new AssertionError("Expected size=10 but got " + size);
              }
              getSizeResults.add(size);
              break;
            case 1:  // get()
              Integer value = chain.get(threadId % 10);
              if (!validValues.contains(value)) {
                throw new AssertionError(
                    "Invalid value: " + value + " (threadId=" + threadId + ")");
              }
              getResults.add(value);
              break;
            case 2:  // stream()
              List<Integer> streamResult = chain.stream().collect(Collectors.toList());
              streamResults.add(streamResult);
              break;
          }
        });

    // All size() calls should return 10
    assertThat(getSizeResults).isNotEmpty();
    for (int size : getSizeResults) {
      assertThat(size).isEqualTo(10);
    }

    // All get() calls should return correct values
    assertThat(getResults).isNotEmpty();
    for (Integer value : getResults) {
      assertThat(value).isIn(validValues);
    }

    // All stream() calls should return complete correct sequence
    assertThat(streamResults).isNotEmpty();
    List<Integer> expected = List.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
    for (List<Integer> streamResult : streamResults) {
      assertThat(streamResult).containsExactlyElementsIn(expected).inOrder();
    }
  }

  private static void runConcurrently(int numThreads, IntConsumer task)
      throws InterruptedException {
    try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
      new Parallelizer(executor, numThreads)
          .parallelize(indexesFrom(0).limit(numThreads), i -> task.accept(i));
    }
  }
}

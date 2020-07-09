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
package com.google.mu.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.ClassSanityTester;

@RunWith(JUnit4.class)
public class ParallelizerPreconditionsTest {
  private final ExecutorService threadPool = Executors.newCachedThreadPool();

  @Test public void testZeroMaxInFlight() {
    assertThrows(IllegalArgumentException.class, () -> new Parallelizer(threadPool, 0));
  }

  @Test public void testNegativeMaxInFlight() {
    assertThrows(IllegalArgumentException.class, () -> new Parallelizer(threadPool, -1));
  }

  @Test public void testZeroTimeout() {
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> parallelizer.parallelize(Stream.empty(), 0, TimeUnit.MILLISECONDS));
  }

  @Test public void testNegaiveTimeout() {
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> parallelizer.parallelize(Stream.empty(), -1, TimeUnit.MILLISECONDS));
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(Parallelizer.class);
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        NullPointerException.class,
        () -> parallelizer.parallelize(nullTasks(), 1, TimeUnit.MILLISECONDS));
    assertThrows(NullPointerException.class, () -> parallelizer.parallelize(nullTasks()));
    assertThrows(
        NullPointerException.class, () -> parallelizer.parallelizeUninterruptibly(nullTasks()));
  }

  private static Stream<Runnable> nullTasks() {
    Runnable task = null;
    return Stream.of(task);
  }
}

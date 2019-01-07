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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelStreamBlowsMemoryTest {

  //@Test
  public void test() throws InterruptedException, ExecutionException {
    ForkJoinPool pool = new ForkJoinPool(2);
    Stream<String> parallelStream = IntStream.range(0, 10000).boxed()
        .map(i -> {
          System.out.println("Evaluating " + i);
          return "" + i;
        })
        .parallel();
    try {
      pool.submit(() -> parallelStream.forEach(s -> {
        System.out.println("Executing " + s);
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {}
      })).get();
    } finally {
      pool.shutdown();
      pool.awaitTermination(1, TimeUnit.HOURS);
    }
  }

 // @Test
  public void testExceptionCancelsPendingTasks() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Thread> thread = new AtomicReference<>();
    Stream<Runnable> tasks = Stream.of(
        () -> {
          thread.set(Thread.currentThread());
          System.out.println("going to pending");
          latch.countDown();
          for (int i = 0; i < 10; i++) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              System.out.println("interrupted!");
            } catch (Throwable e) {
              System.err.println("got an error");
              e.printStackTrace();
            }
            System.out.println("waking up");
          }
        },
        () -> {
          try {
            latch.await();
          } catch (InterruptedException shouldNotHappen) {
            System.err.println("Should not have been interrupted!");
            return;
          }
          throw new RuntimeException("deliberate");
        });
    try {
      tasks.parallel().forEach(Runnable::run);
    } catch (Exception e) {
      System.out.println(thread.get().isDaemon());
      Thread.sleep(10000);
    }
  }

  private void process(String s) {
    try {
      System.out.println("starting " + s);
      Thread.sleep(1000);
      System.out.println("done " + s);
      //throw new RuntimeException();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}

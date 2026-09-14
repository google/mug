package com.google.mu.util.concurrent24;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.concurrent24.BoundedConcurrency.withMaxConcurrency;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.mu.testing.concurrent.Happenstance;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class BoundedConcurrencyTest {

  @Test public void concurrently_emptyInput() {
    assertThat(Stream.empty().collect(withMaxConcurrency(3).concurrently(Object::toString)).toMap())
        .isEmpty();
    assertThat(Stream.empty().collect(withMaxConcurrency(1).concurrently(Object::toString)).toMap())
        .isEmpty();
  }

  @Test public void concurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(withMaxConcurrency(3).concurrently(Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(withMaxConcurrency(5).concurrently(Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyEqualToElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(withMaxConcurrency(4).concurrently(Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyEqualToOne() {
    assertThat(Stream.of("1", "2", "3", "4").collect(withMaxConcurrency(1).concurrently(Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_maxConcurrency() {
    assertThat(Stream.of("1", "2", "3", "4").collect(withMaxConcurrency(Integer.MAX_VALUE).concurrently(Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void withMaxConcurrency_zeroConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> withMaxConcurrency(0));
  }

  @Test public void withMaxConcurrency_negativeConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> withMaxConcurrency(-1));
  }

  @Test public void withMaxConcurrency_minConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> withMaxConcurrency(Integer.MIN_VALUE));
  }

  @Test public void concurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").collect(withMaxConcurrency(2).concurrently(Integer::parseInt)).toMap());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void concurrently_multipleExceptionsPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "two", "three", "four").collect(withMaxConcurrency(2).concurrently(Integer::parseInt)).toMap());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void concurrently_findFirstCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(10, 1, 5, 0).collect(withMaxConcurrency(3).concurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return n;
            })).keys().findFirst())
        .hasValue(1);
    assertThat(started).containsExactly(10, 1, 5);
    assertThat(interrupted).containsExactly(5, 10);
  }

  @Test public void concurrently_findAnyCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(10, 1, 3, 0).collect(withMaxConcurrency(3).concurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return n;
            })).values().findAny())
        .hasValue(1);
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void concurrently_threadsInterruptedUponException() {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(10, 5, 7, 1).collect(withMaxConcurrency(4).concurrently(n -> {
          if (n > 1) { // 1 fails first; the rest are still running when it does
            blockUntilInterrupted(n, interrupted);
          }
          throw new ApplicationException(String.valueOf(n));
        })).toMap());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("1");
    assertThat(interrupted).containsExactly(5, 7, 10);
    assertThat(Stream.of(thrown.getSuppressed()).map(Throwable::getMessage).toList())
        .containsExactly("5", "7", "10");
  }

  @Test public void concurrently_mainThreadInterrupted_propagatedInterruption()
      throws InterruptedException {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    AtomicReference<Map<Integer,String>> results = new AtomicReference<>();
    Thread mainThread = new Thread(
        () -> {
          try {
            results.set(
              Stream.of(10, 30, 40, 20).collect(withMaxConcurrency(2).concurrently(n -> {
                blockUntilInterrupted(n, interrupted);
                return String.valueOf(n);
              })).toMap());
          } catch (Throwable e) {
            e.printStackTrace();
          }
        });
    mainThread.start();
    mainThread.interrupt();
    mainThread.join();
    assertThat(results.get()).containsExactly(10, "10", 20, "20", 30, "30", 40, "40");
    assertThat(interrupted).containsExactly(10, 20, 30, 40);
  }

  @Test public void mapConcurrently_emptyInput() {
    assertThat(Stream.empty().gather(withMaxConcurrency(3).mapConcurrently(Object::toString)))
        .isEmpty();
    assertThat(Stream.empty().gather(withMaxConcurrency(1).mapConcurrently(Object::toString)))
        .isEmpty();
  }

  @Test public void mapConcurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(withMaxConcurrency(3).mapConcurrently(Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(withMaxConcurrency(5).mapConcurrently(Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyEqualToElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(withMaxConcurrency(4).mapConcurrently(Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyEqualToOne() {
    assertThat(Stream.of("1", "2", "3", "4").gather(withMaxConcurrency(1).mapConcurrently(Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_maxConcurrency() {
    assertThat(Stream.of("1", "2", "3", "4").gather(withMaxConcurrency(Integer.MAX_VALUE).mapConcurrently(Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").gather(withMaxConcurrency(2).mapConcurrently(Integer::parseInt)).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void mapConcurrently_multipleExceptionsPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "two", "three", "four").gather(withMaxConcurrency(2).mapConcurrently(Integer::parseInt)).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void mapConcurrently_findFirstCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(10, 1, 3, 0).gather(withMaxConcurrency(3).mapConcurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return n;
            })).findFirst())
        .hasValue(1);
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void mapConcurrently_findAnyCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(10, 1, 3, 0).gather(withMaxConcurrency(3).mapConcurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return n;
            })).findAny())
        .hasValue(1);
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void mapConcurrently_threadsInterruptedUponException() {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(10, 20, 30, 1).gather(withMaxConcurrency(4).mapConcurrently(n -> {
          if (n > 1) { // 1 fails first; the rest are still running when it does
            blockUntilInterrupted(n, interrupted);
          }
          throw new ApplicationException(String.valueOf(n));
        })).toList());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("1");
    assertThat(interrupted).containsExactly(20, 30, 10);
    assertThat(Stream.of(thrown.getSuppressed()).map(Throwable::getMessage).toList())
        .containsExactly("10", "20", "30");
  }

  @Test public void mapConcurrently_mainThreadInterrupted_propagatedInterruption()
      throws InterruptedException {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    AtomicReference<List<String>> results = new AtomicReference<>();
    Thread mainThread = new Thread(
        () -> {
          try {
            results.set(
              Stream.of(10, 30, 40, 20).gather(withMaxConcurrency(2).mapConcurrently(n -> {
                blockUntilInterrupted(n, interrupted);
                return String.valueOf(n);
              })).toList());
          } catch (Throwable e) {
            e.printStackTrace();
          }
        });
    mainThread.start();
    mainThread.interrupt();
    mainThread.join();
    assertThat(results.get()).containsExactly("10", "20", "30", "40");
    assertThat(interrupted).containsExactly(10, 20, 30, 40);
  }

  /**
   * Shows that with a heartbeat or monitoring task at the beginning, mapConcurrently()
   * isn't subject to halting.
   */
  @Test public void mapConcurrently_withHeartbeatTask_works() {
    CountDownLatch latch = new CountDownLatch(3);
    Runnable heartbeat = () -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    };
    Runnable countDown = latch::countDown;
    assertThat(Stream.of(heartbeat, countDown, countDown, countDown)
        .gather(withMaxConcurrency(2).mapConcurrently(task -> {
          task.run();
          return "done";
        }))).hasSize(4);
  }

  /**
   * Shows that with a heartbeat or monitoring task at the beginning, mapConcurrent()
   * will halt (we use a timeout to avoid halting).
   */
  @Test public void mapConcurrent_withHeartbeatTask_halts() {
    CountDownLatch latch = new CountDownLatch(3);
    Runnable heartbeat = () -> {
      try {
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    };
    Runnable countDown = latch::countDown;
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(heartbeat, countDown, countDown, countDown)
            .gather(Gatherers.mapConcurrent(2, task -> {
              task.run();
              return "done";
            }))
            .toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(AssertionError.class);
  }

  @Test public void mapConcurrently_firstSuccessInterruptsTheRest() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(10, 1, 3, 0)
            .gather(withMaxConcurrency(3).mapConcurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return String.valueOf(n);
            }))
            .findAny())
        .hasValue("1");
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void mapConcurrently_failurePropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    BoundedConcurrency.UncheckedExecutionException thrown = assertThrows(
        BoundedConcurrency.UncheckedExecutionException.class,
        () -> Stream.of(10, 1, 3, 0)
            .gather(withMaxConcurrency(3).mapConcurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              throw new IllegalArgumentException(String.valueOf(n));
            }))
            .findAny());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("1");
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void mapConcurrent_firstSuccessInterruptsTheRest() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    assertThat(
        Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return String.valueOf(n);
            }))
            .findAny())
        .hasValue("1");
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void mapConcurrent_failurePropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              throw new IllegalArgumentException(String.valueOf(n));
            }))
            .findAny());
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
    assertThat(thrown).hasMessageThat().isEqualTo("1");
  }

  @Test public void mapConcurrent_downstreamFailureInterrupts() throws Exception {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    CountDownLatch finished = new CountDownLatch(3);
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              finished.countDown();
              return n;
            }))
            .peek(n -> {
              // When 1 is pushed here, [3, 10] are still running
              throw new IllegalArgumentException(String.valueOf(n));
            })
            .findAny());
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
    assertThat(thrown).hasMessageThat().isEqualTo("1");
    assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
  }

  @Test public void mapConcurrently_upstreamFailureDoesNotInterrupt() throws Exception {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens = Happenstance.<String>builder()
        .sequence("1 running", "3 throws")
        .sequence("10 running", "3 throws")
        .build();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(2);
    try {
      RuntimeException thrown = assertThrows(
          RuntimeException.class,
          () -> Stream.of(1, 10, 3, 0)
              .peek(n -> {
                if (n == 3) { // throws only once 1 and 10 are confirmed running
                  happens.checkpoint("3 throws");
                  throw new IllegalArgumentException(String.valueOf(n));
                }
              })
              .gather(withMaxConcurrency(3).mapConcurrently(n -> {
                started.add(n);
                happens.checkpoint(n + " running");
                try {
                  release.await();
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
                finished.countDown();
                return n;
              }))
              .findAny());
      assertThat(started).containsExactly(10, 1);
      assertThat(interrupted).isEmpty();
      assertThat(thrown).hasMessageThat().isEqualTo("3");
    } finally {
      release.countDown();
    }
    // The two orphaned threads are still ours to clean up: nothing interrupted them.
    assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(interrupted).isEmpty();
  }

  @Test public void mapConcurrently_downstreamFailurePropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(10, 1, 3, 0)
            .gather(withMaxConcurrency(3).mapConcurrently(n -> {
              started.add(n);
              if (n > 1) { // the losers stay running until they are cancelled
                othersRunning.countDown();
                blockUntilInterrupted(n, interrupted);
              } else { // 1 wins, but only once the losers are confirmed running
                awaitUninterruptibly(othersRunning);
              }
              return n;
            }))
            .peek(n -> {
              throw new IllegalArgumentException(String.valueOf(n));
            })
            .findAny());
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
    assertThat(thrown).hasMessageThat().isEqualTo("1");
  }

  @Test public void mapConcurrent_upstreamFailurePropagated() throws Exception {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    Happenstance<String> happens = Happenstance.<String>builder()
        .sequence("1 running", "3 throws")
        .sequence("10 running", "3 throws")
        .build();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(2);
    try {
      RuntimeException thrown = assertThrows(
          RuntimeException.class,
          () -> Stream.of(1, 10, 3, 0)
              .peek(n -> {
                if (n == 3) { // throws only once 1 and 10 are confirmed running
                  happens.checkpoint("3 throws");
                  throw new IllegalArgumentException(String.valueOf(n));
                }
              })
              .gather(Gatherers.mapConcurrent(3, n -> {
                started.add(n);
                happens.checkpoint(n + " running");
                try {
                  release.await();
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
                finished.countDown();
                return n;
              }))
              .findAny());
      assertThat(started).containsExactly(10, 1);
      assertThat(interrupted).isEmpty();
      assertThat(thrown).hasMessageThat().isEqualTo("3");
    } finally {
      release.countDown();
    }
    assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(interrupted).isEmpty();
  }

  // Demonstrates that when a task in Jdk parallelStream throws an exception,
  // other concurrently running sibling tasks are not interrupted.
  @Test public void parallelStream_upstreamFailureDoesNotInterruptDownstream() throws Exception {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    boolean[] failed = new boolean[1];
    boolean[] seen = new boolean[1];
    Happenstance<String> happens = Happenstance.<String>builder()
        .sequence("10 started", "3 start")
        .sequence("1 started", "3 start")
        .sequence("3 thrown", "10 finished", "all done")
        .sequence("3 thrown", "1 finished", "all done")
        .build();
    try (ForkJoinPool pool = new ForkJoinPool(3)) {
      ExecutionException thrown = assertThrows(
          ExecutionException.class,
          () -> {
            try {
              pool.submit(() -> asList(10, 3, 1)
                  .parallelStream()
                  .map(n -> {
                    if (n == 3) {
                      happens.join("3 start");
                      failed[0] = true;
                      throw new IllegalArgumentException(String.valueOf(n));
                    }
                    started.add(n);
                    happens.join(n + " started");
                    happens.join(n + " finished");
                    if (Thread.interrupted()) {
                      interrupted.add(n);
                    }
                    seen[0] = true;
                    return n;
                  })
                  .findAny()).get();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(e);
            } finally {
              happens.join("3 thrown");
            }
          });
      assertThat(thrown).hasCauseThat().hasMessageThat().contains("3");
    }
    happens.join("all done");
    assertThat(started).containsExactly(10, 1);
    assertThat(interrupted).isEmpty();
    assertThat(failed[0]).isTrue();
    assertThat(seen[0]).isTrue();
  }

  @Test public void flatMapConcurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(withMaxConcurrency(3).flatMapConcurrently(n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(withMaxConcurrency(6).flatMapConcurrently(n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyEqualToElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(withMaxConcurrency(6).flatMapConcurrently(n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyEqualToOne() {
    assertThat(Stream.of(1, 2, 3, 4).gather(withMaxConcurrency(1).flatMapConcurrently(n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_emptyInput() {
    assertThat(Stream.empty().gather(withMaxConcurrency(3).flatMapConcurrently(Stream::of)))
        .isEmpty();
    assertThat(Stream.empty().gather(withMaxConcurrency(1).flatMapConcurrently(Stream::of)))
        .isEmpty();
  }

  @Test public void flatMapConcurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").gather(withMaxConcurrency(2).flatMapConcurrently(s -> Stream.of(Integer.parseInt(s)))).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void race_recoverableFailuresIgnored() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch othersRunning = new CountDownLatch(2);
    List<Callable<String>> tasks = Stream.of(10, 1, 0, 3).<Callable<String>>map(n -> () -> {
      started.add(n);
      if (n > 0) { // the losers stay running until they are cancelled
        othersRunning.countDown();
        blockUntilInterrupted(n, interrupted);
      } else { // only 0 succeeds, and only once the losers are confirmed running
        awaitUninterruptibly(othersRunning);
      }
      assertThat(n).isEqualTo(0);
      return "0";
    }).toList();
    assertThat(withMaxConcurrency(3).race(tasks, e -> true)).isEqualTo("0");
    assertThat(started).containsExactly(10, 0, 1);
    assertThat(interrupted).containsExactly(10, 1);
  }

  @Test public void race_noSuccess_recoverableFailuresPropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = Stream.of(10, 1, 0, 3).<Callable<String>>map(n -> () -> {
      started.add(n);
      throw new ApplicationException(String.valueOf(n));
    }).toList();
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> withMaxConcurrency(3).race(tasks, e -> true));
    // Which failure ends up as the cause is a genuine race; all four must be reported though.
    assertThat(
            Stream.concat(Stream.of(thrown.getCause()), Stream.of(thrown.getSuppressed()))
                .map(Throwable::getMessage)
                .toList())
        .containsExactly("10", "1", "0", "3");
    assertThat(started).containsExactly(10, 0, 1, 3);
  }

  @Test public void race_noSuccess_unrecoverableFailuresPropagatedWithRecoverableErrorsSuppressed() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch tenStarted = new CountDownLatch(1);
    List<Callable<String>> tasks = Stream.of(1, 0, 2, 10).<Callable<String>>map(n -> () -> {
      started.add(n);
      if (n == 10) { // stays running so that the unrecoverable failure has something to cancel
        tenStarted.countDown();
        blockUntilInterrupted(n, interrupted);
      }
      if (n == 2) { // 3rd is not recoverable; it only fails once 10 is running
        assertThat(tenStarted.await(30, TimeUnit.SECONDS)).isTrue();
        throw new AssertionError(String.valueOf(n));
      }
      throw new ApplicationException(String.valueOf(n));
    }).toList();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> withMaxConcurrency(3).race(tasks, ApplicationException.class::isInstance));
    assertThat(thrown).hasCauseThat().isInstanceOf(AssertionError.class);
    assertThat(asList(thrown.getSuppressed())).hasSize(3);
    assertThat(started).containsExactly(1, 0, 2, 10);
    assertThat(interrupted).containsExactly(10);
  }

  @Test
  public void race_returnsNullSuccessfully() {
    List<Callable<String>> tasks = asList(() -> null);
    assertThat(withMaxConcurrency(1).race(tasks, e -> true)).isNull();
  }

  @Test
  public void concurrently_threadStartFailure_cancelsPendingAndPropagates() {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    AtomicInteger factoryCallCount = new AtomicInteger();
    ThreadFactory statefulFactory =
        runnable -> {
          if (factoryCallCount.getAndIncrement() == 0) {
            return Thread.ofVirtual().unstarted(runnable);
          }
          return null; // Fail on second thread creation
        };

    BoundedConcurrency concurrency = BoundedConcurrency.withMaxConcurrency(2, statefulFactory);

    RejectedExecutionException thrown =
        assertThrows(
            RejectedExecutionException.class,
            () ->
                Stream.of(10, 20) // 10 will start and sleep, 20 will fail to start
                    .gather(
                        concurrency.mapConcurrently(
                            n -> {
                              blockUntilInterrupted(n, interrupted);
                              return n;
                            }))
                    .toList());

    assertThat(thrown).hasMessageThat().isEqualTo("thread factory returned null");
    assertThat(interrupted).containsExactly(10); // Verifies that the running thread was interrupted
  }

  /**
   * When one task fails, the others are cancelled and joined. Exceptions queued during that join
   * are part of the diagnosis and belong on the propagated exception as suppressed.
   */
  @Test public void mapConcurrently_exceptionsFromCancelledTasksAreSuppressed() {
    Happenstance<String> happens = Happenstance.<String>builder()
        .sequence("2 running", "1 fails")
        .sequence("3 running", "1 fails")
        .build();
    BoundedConcurrency.UncheckedExecutionException thrown = assertThrows(
        BoundedConcurrency.UncheckedExecutionException.class,
        () -> Stream.of(1, 2, 3, 4)
            .gather(withMaxConcurrency(3).mapConcurrently(n -> {
              if (n == 1) { // fails only after the other two are confirmed running
                happens.checkpoint("1 fails");
                throw new ApplicationException("boom " + n);
              }
              happens.checkpoint(n + " running");
              try {
                Thread.sleep(Duration.ofSeconds(30)); // interrupted long before this elapses
                return n;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("cancelled " + n, e);
              }
            }))
            .toList());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("boom 1");
    assertThat(Stream.of(thrown.getSuppressed()).map(Throwable::getMessage).toList())
        .containsExactly("cancelled 2", "cancelled 3");
  }

  @Test public void mapConcurrently_integratorIsGreedy() {
    assertThat(withMaxConcurrency(2).mapConcurrently(n -> n).integrator())
        .isInstanceOf(Integrator.Greedy.class);
  }

  @Test public void flatMapConcurrently_integratorIsGreedy() {
    assertThat(withMaxConcurrency(2).flatMapConcurrently(Stream::of).integrator())
        .isInstanceOf(Integrator.Greedy.class);
  }

  @Test public void race_emptyTasks_rejected() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> withMaxConcurrency(2).race(List.<Callable<String>>of(), e -> true));
    assertThat(thrown).hasMessageThat().isEqualTo("At least one task should have been provided");
  }

  /**
   * With maxConcurrency 1 the tasks are observed strictly in order, so the success is seen before
   * the unrecoverable failure and the failing task is never even started.
   */
  @Test public void race_successObservedFirst_unrecoverableFailureNeverObserved() {
    ConcurrentLinkedQueue<String> ran = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = asList(
        () -> {
          ran.add("success");
          return "ok";
        },
        () -> {
          ran.add("failure");
          throw new IllegalStateException("unrecoverable");
        });
    assertThat(withMaxConcurrency(1).race(tasks, ApplicationException.class::isInstance))
        .isEqualTo("ok");
    assertThat(ran).containsExactly("success");
  }

  /** The mirror image: the unrecoverable failure is observed first, so it wins. */
  @Test public void race_unrecoverableFailureObservedFirst_propagated() {
    ConcurrentLinkedQueue<String> ran = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = asList(
        () -> {
          ran.add("failure");
          throw new IllegalStateException("unrecoverable");
        },
        () -> {
          ran.add("success");
          return "ok";
        });
    BoundedConcurrency.UncheckedExecutionException thrown = assertThrows(
        BoundedConcurrency.UncheckedExecutionException.class,
        () -> withMaxConcurrency(1).race(tasks, ApplicationException.class::isInstance));
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("unrecoverable");
    assertThat(ran).containsExactly("failure");
  }

  /** The collector returns before any work starts; the returned BiStream is what runs it. */
  @Test public void concurrently_collectorIsLazy() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    var lazy = Stream.of(1, 2, 3).collect(withMaxConcurrency(2).concurrently(n -> {
      started.add(n);
      return n * 2;
    }));
    assertThat(started).isEmpty();
    assertThat(lazy.toMap()).containsExactly(1, 2, 2, 4, 3, 6);
    assertThat(started).containsExactly(1, 2, 3);
  }

  /** Input is consumed into a List first, so an upstream failure starts no thread at all. */
  @Test public void concurrently_upstreamFailure_startsNoThread() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> Stream.of(1, 2, 3)
            .peek(n -> {
              throw new IllegalArgumentException("upstream " + n);
            })
            .collect(withMaxConcurrency(2).concurrently(n -> {
              started.add(n);
              return n;
            }))
            .toMap());
    assertThat(thrown).hasMessageThat().isEqualTo("upstream 1");
    assertThat(started).isEmpty();
  }

  @Test public void mapConcurrently_platformThreadFactory() {
    ConcurrentLinkedQueue<Boolean> virtual = new ConcurrentLinkedQueue<>();
    ThreadFactory platformThreads = Thread.ofPlatform().name("test-", 0).factory();
    assertThat(
        Stream.of(1, 2, 3, 4)
            .gather(BoundedConcurrency.withMaxConcurrency(2, platformThreads).mapConcurrently(n -> {
              virtual.add(Thread.currentThread().isVirtual());
              return n * 2;
            })))
        .containsExactly(2, 4, 6, 8);
    assertThat(virtual).containsExactly(false, false, false, false);
  }

  /**
   * Blocks until interrupted, recording {@code n}. Used in place of a timed sleep so that "the
   * other tasks are still running when the winner finishes" holds regardless of machine speed.
   * The bound is there so that a regression fails the build instead of hanging it.
   */
  /** Awaits {@code latch}, failing rather than hanging if the expected progress never happens. */
  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static void blockUntilInterrupted(int n, ConcurrentLinkedQueue<Integer> interrupted) {
    try {
      Thread.sleep(Duration.ofSeconds(30));
    } catch (InterruptedException e) {
      interrupted.add(n);
    }
  }

  private static class ApplicationException extends RuntimeException {
    ApplicationException(String s) {
      super(s);
    }
  }
}

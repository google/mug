package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.concurrent.Race.concurrently;
import static com.google.mu.util.concurrent.Race.flatMapConcurrently;
import static com.google.mu.util.concurrent.Race.mapConcurrently;
import static com.google.mu.util.concurrent.Race.race;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class RaceTest {

  @Test public void concurrently_emptyInput() {
    assertThat(Stream.empty().collect(concurrently(3, Object::toString)).toMap())
        .isEmpty();
    assertThat(Stream.empty().collect(concurrently(1, Object::toString)).toMap())
        .isEmpty();
  }

  @Test public void concurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(concurrently(3, Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(concurrently(5, Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyEqualToElements() {
    assertThat(Stream.of("1", "2", "3", "4").collect(concurrently(4, Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_concurrencyEqualToOne() {
    assertThat(Stream.of("1", "2", "3", "4").collect(concurrently(1, Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_maxConcurrency() {
    assertThat(Stream.of("1", "2", "3", "4").collect(concurrently(Integer.MAX_VALUE, Integer::parseInt)).toMap())
        .containsExactly("1", 1, "2", 2, "3", 3, "4", 4);
  }

  @Test public void concurrently_zeroConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> concurrently(0, Object::toString));
  }

  @Test public void concurrently_negativeConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> concurrently(-1, Object::toString));
  }

  @Test public void concurrently_minConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> concurrently(Integer.MIN_VALUE, Object::toString));
  }

  @Test public void concurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").collect(concurrently(2, Integer::parseInt)).toMap());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void concurrently_multipleEsxceptionsPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "two", "three", "four").collect(concurrently(2, Integer::parseInt)).toMap());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void concurrently_findFirstCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    assertThat(
        Stream.of(10, 1, 3, 0).collect(concurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n);
              } catch (InterruptedException e) {
                interrupted.add(n);
              }
              return n;
            })).keys().findFirst())
        .hasValue(1);
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
  }

  @Test public void concurrently_findAnyCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    assertThat(
        Stream.of(10, 1, 3, 0).collect(concurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
        () -> Stream.of(10, 2, 3, 1).collect(concurrently(4, n -> {
          try {
            Thread.sleep(n);
          } catch (InterruptedException e) {
            interrupted.add(n);
          }
          throw new ApplicationException(String.valueOf(n));
        })).toMap());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("1");
    assertThat(interrupted).containsExactly(2, 3, 10);
  }

  @Test public void concurrently_mainThreadInterrupted_propagatedInterruption()
      throws InterruptedException {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    AtomicReference<Map<Integer,String>> results = new AtomicReference<>();
    Thread mainThread = new Thread(
        () -> {
          try {
            results.set(
              Stream.of(10, 30, 40, 20).collect(concurrently(2, n -> {
                try {
                  Thread.sleep(n);
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
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
    assertThat(Stream.empty().gather(mapConcurrently(3, Object::toString)))
        .isEmpty();
    assertThat(Stream.empty().gather(mapConcurrently(1, Object::toString)))
        .isEmpty();
  }

  @Test public void mapConcurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(mapConcurrently(3, Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(mapConcurrently(5, Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyEqualToElements() {
    assertThat(Stream.of("1", "2", "3", "4").gather(mapConcurrently(4, Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_concurrencyEqualToOne() {
    assertThat(Stream.of("1", "2", "3", "4").gather(mapConcurrently(1, Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_maxConcurrency() {
    assertThat(Stream.of("1", "2", "3", "4").gather(mapConcurrently(Integer.MAX_VALUE, Integer::parseInt)))
        .containsExactly(1, 2, 3, 4);
  }

  @Test public void mapConcurrently_zeroConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> mapConcurrently(0, Object::toString));
  }

  @Test public void mapConcurrently_negativeConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> mapConcurrently(-1, Object::toString));
  }

  @Test public void mapConcurrently_minConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> mapConcurrently(Integer.MIN_VALUE, Object::toString));
  }

  @Test public void mapConcurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").gather(mapConcurrently(2, Integer::parseInt)).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void mapConcurrently_multipleEsxceptionsPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "two", "three", "four").gather(mapConcurrently(2, Integer::parseInt)).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void mapConcurrently_findFirstCancelsPending() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    assertThat(
        Stream.of(10, 1, 3, 0).gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
    assertThat(
        Stream.of(10, 1, 3, 0).gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
        () -> Stream.of(10, 2, 3, 1).gather(mapConcurrently(4, n -> {
          try {
            Thread.sleep(n);
          } catch (InterruptedException e) {
            interrupted.add(n);
          }
          throw new ApplicationException(String.valueOf(n));
        })).toList());
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("1");
    assertThat(interrupted).containsExactly(2, 3, 10);
  }

  @Test public void mapConcurrently_mainThreadInterrupted_propagatedInterruption()
      throws InterruptedException {
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    AtomicReference<List<String>> results = new AtomicReference<>();
    Thread mainThread = new Thread(
        () -> {
          try {
            results.set(
              Stream.of(10, 30, 40, 20).gather(mapConcurrently(2, n -> {
                try {
                  Thread.sleep(n);
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
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
        .gather(mapConcurrently(2, task -> {
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
    assertThat(
        Stream.of(10, 1, 3, 0)
            .gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
    Race.UncheckedExecutionException thrown = assertThrows(
        Race.UncheckedExecutionException.class,
        () -> Stream.of(10, 1, 3, 0)
            .gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
    assertThat(
        Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
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
    CountDownLatch latch = new CountDownLatch(2);
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
              }
              latch.countDown();
              return n;
            }))
            .peek(n -> {
              // When 0 throws here, [3, 10] are still running
              throw new IllegalArgumentException(String.valueOf(n));
            })
            .findAny());
    assertThat(started).containsExactly(10, 1, 3);
    assertThat(interrupted).containsExactly(3, 10);
    assertThat(thrown).hasMessageThat().isEqualTo("1");
    latch.await();
  }

  @Test public void mapConcurrently_upstreamFailureDoesNotInterrupt() throws Exception {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    CountDownLatch latch = new CountDownLatch(2);
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .peek(n -> {
              if (n == 3) {
                try { // give 1 and 10 some time to have at least started
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
                throw new IllegalArgumentException(String.valueOf(n));
              }
            })
            .gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 10000);
              } catch (InterruptedException e) {
                interrupted.add(n);
              }
              latch.countDown();
              return n;
            }))
            .findAny());
    assertThat(started).containsExactly(10, 1);
    assertThat(interrupted).isEmpty();
    assertThat(thrown).hasMessageThat().isEqualTo("3");
    // latch.await();
  }

  @Test public void mapConcurrently_downstreamFailurePropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(10, 1, 3, 0)
            .gather(mapConcurrently(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 1000);
              } catch (InterruptedException e) {
                interrupted.add(n);
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

  @Test public void mapConcurrent_upstreamFailurePropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of(1, 10, 3, 0)
            .peek(n -> {
              if (n == 3) {
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  interrupted.add(n);
                }
                throw new IllegalArgumentException(String.valueOf(n));
              }
            })
            .gather(Gatherers.mapConcurrent(3, n -> {
              started.add(n);
              try {
                Thread.sleep(n * 10000);
              } catch (InterruptedException e) {
                interrupted.add(n);
              }
              return n;
            }))
            .findAny());
    assertThat(started).containsExactly(10, 1);
    assertThat(interrupted).isEmpty();
    assertThat(thrown).hasMessageThat().isEqualTo("3");
  }

  @Test public void flatMapConcurrently_concurrencySmallerThanElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(flatMapConcurrently(3, n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyLargerThanElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(flatMapConcurrently(6, n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyEqualToElements() {
    assertThat(Stream.of(1, 2, 3, 4).gather(flatMapConcurrently(6, n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_concurrencyEqualToOne() {
    assertThat(Stream.of(1, 2, 3, 4).gather(flatMapConcurrently(1, n -> Collections.nCopies(n, n).stream())))
        .containsExactly(1, 2, 2, 3, 3, 3, 4, 4, 4, 4);
  }

  @Test public void flatMapConcurrently_emptyInput() {
    assertThat(Stream.empty().gather(flatMapConcurrently(3, Stream::of)))
        .isEmpty();
    assertThat(Stream.empty().gather(flatMapConcurrently(1, Stream::of)))
        .isEmpty();
  }

  @Test public void flatMapConcurrently_exceptionPropagated() {
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> Stream.of("1", "2", "3", "four").gather(flatMapConcurrently(2, s -> Stream.of(Integer.parseInt(s)))).toList());
    assertThat(thrown).hasCauseThat().isInstanceOf(NumberFormatException.class);
  }

  @Test public void race_recoverableFailuresIgnored() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = Stream.of(10, 1, 0, 3).<Callable<String>>map(n -> () -> {
      started.add(n);
      try {
        Thread.sleep(n * 1000);
      } catch (InterruptedException e) {
        interrupted.add(n);
      }
      assertThat(n).isEqualTo(0);
      return "0";
    }).toList();
    assertThat(race(3, tasks, e -> true)).isEqualTo("0");
    assertThat(started).containsExactly(10, 0, 1);
    assertThat(interrupted).containsExactly(10, 1);
  }

  @Test public void race_noSuccess_recoverableFailuresPropagated() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = Stream.of(10, 1, 0, 3).<Callable<String>>map(n -> () -> {
      started.add(n);
      try {
        Thread.sleep(n * 100);
      } catch (InterruptedException e) {
        interrupted.add(n);
      }
      throw new ApplicationException(String.valueOf(n));
    }).toList();
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> race(3, tasks, e -> true));
    assertThat(thrown).hasMessageThat().contains("0");
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("0");
    assertThat(asList(thrown.getSuppressed())).hasSize(3);
    assertThat(started).containsExactly(10, 0, 1, 3);
    assertThat(interrupted).isEmpty();
  }

  @Test public void race_noSuccess_unrecoverableFailuresPropagatedWithRecoverableErrorsSuppressed() {
    ConcurrentLinkedQueue<Integer> started = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<Integer> interrupted = new ConcurrentLinkedQueue<>();
    List<Callable<String>> tasks = Stream.of(1, 0, 2, 10).<Callable<String>>map(n -> () -> {
      started.add(n);
      try {
        Thread.sleep(n * 100);
      } catch (InterruptedException e) {
        interrupted.add(n);
      }
      assertThat(n).isNotEqualTo(2); // 3rd is not recoverable
      throw new ApplicationException(String.valueOf(n));
    }).toList();
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> race(3, tasks, ApplicationException.class::isInstance));
    assertThat(thrown).hasCauseThat().isInstanceOf(AssertionError.class);
    assertThat(asList(thrown.getSuppressed())).hasSize(2);
    assertThat(started).containsExactly(1, 0, 2, 10);
    assertThat(interrupted).containsExactly(10);
  }

  private static class ApplicationException extends RuntimeException {
    ApplicationException(String s) {
      super(s);
    }
  }
}

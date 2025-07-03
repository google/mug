package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.concurrent.Race.flatMapConcurrently;
import static com.google.mu.util.concurrent.Race.mapConcurrently;
import static com.google.mu.util.concurrent.Race.race;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Collections;
import java.util.List;
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

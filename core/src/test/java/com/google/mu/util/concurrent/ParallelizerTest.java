package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.concurrent.Parallelizer.forAll;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.collect.Lists;
import com.google.common.truth.IterableSubject;
import com.google.mu.util.concurrent.Parallelizer.UncheckedExecutionException;

@RunWith(Parameterized.class)
public class ParallelizerTest {

  private final Mode mode;
  private final ExecutorService threadPool = Executors.newCachedThreadPool();
  private volatile int maxInFlight = 3;
  private Duration timeout = Duration.ofMillis(10);
  private final AtomicInteger activeThreads = new AtomicInteger();
  private final ConcurrentMap<Integer, String> translated = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Object> interrupted = new ConcurrentLinkedQueue<>();

  public ParallelizerTest(Mode mode) {
    this.mode = mode;
  }

  @Rule public final Verifier verifyTaskAssertions = new Verifier() {
    @Override protected void verify() throws Throwable {
      ParallelizerTest.this.shutdownThreadPool();
      for (Throwable e : thrown) {
        throw e;
      }
      assertThat(activeThreads.get()).isEqualTo(0);
    }
  };

  @Test public void testOneInFlight() throws Exception {
    maxInFlight = 1;
    List<Integer> numbers = asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
    parallelize(numbers.stream(), this::translateToString);
    assertThat(translated).containsExactlyEntriesIn(numbers.stream()
        .collect(Collectors.toMap(n -> n, Object::toString)));
  }

  @Test public void testTranslateStrings() throws Exception {
    List<Integer> numbers = asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
    parallelize(numbers.stream(), this::translateToString);
    assertThat(translated).containsExactlyEntriesIn(numbers.stream()
        .collect(Collectors.toMap(n -> n, Object::toString)));
  }

  @Test public void testTaskExceptionDismissesPendingTasks() {
    maxInFlight = 2;
    List<Integer> numbers = asList(1, 2, -1, -1, 5, 6, 7);
    UncheckedExecutionException exception = assertThrows(
        UncheckedExecutionException.class,
        () -> parallelize(limit(numbers.stream(), this::translatePositiveNumberToString, 4)));
    assertThat(exception.getCause().getMessage()).contains("-1");
    assertThat(translated).containsEntry(1, "1");
    assertThat(translated).containsEntry(2, "2");
    assertThat(translated).doesNotContainKey(5);
    assertThat(translated).doesNotContainKey(6);
    assertThat(translated).doesNotContainKey(7);
  }

  @Test public void testTaskExceptionCancelsInFlightTasks() throws InterruptedException {
    maxInFlight = 2;
    // One of the two negatives will fail, canceling the other.
    List<Integer> numbers = asList(1, 2, -1, -1, 5, 6, 7);
    UncheckedExecutionException exception = assertThrows(
        UncheckedExecutionException.class,
        () -> parallelize(limit(numbers.stream(), this::translatePositiveNumberToString, 3)));
    assertThat(exception.getCause().getMessage()).contains("-1");
    assertThat(translated).containsEntry(1, "1");
    assertThat(translated).containsEntry(2, "2");
    assertThat(translated).doesNotContainKey(-1);
    assertThat(translated).doesNotContainKey(-2);
    assertThat(translated).doesNotContainKey(5);
    assertThat(translated).doesNotContainKey(6);
    assertThat(translated).doesNotContainKey(7);
    assertInterruptedKeys().containsExactly(-1);
  }

  @Test public void testSubmissionTimeoutCancelsInFlightTasks() throws InterruptedException {
    Assume.assumeTrue(mode == Mode.INTERRUPTIBLY);
    maxInFlight = 2;
    // Only 1 and 2 will be translated, 3 and 4 will be interrupted, 5 and 6 will never get there.
    // 3 will not schedule until either 1 or 2 finishes.
    List<Integer> numbers = asList(1, 2, 3, 4, 5, 6);
    assertThrows(
        TimeoutException.class,
        () -> parallelize(limit(numbers.stream(), this::translateToString, 2)));
    assertThat(translated).containsEntry(1, "1");
    assertThat(translated).containsEntry(2, "2");
    assertThat(translated).doesNotContainKey(3);
    assertThat(translated).doesNotContainKey(4);
    assertThat(translated).doesNotContainKey(5);
    assertThat(translated).doesNotContainKey(6);
    assertInterruptedKeys().containsExactly(3, 4);
  }

  @Test public void testAwaitTimeoutCancelsInFlightTasks() throws InterruptedException {
    Assume.assumeTrue(mode == Mode.INTERRUPTIBLY);
    maxInFlight = 2;
    // Only 1 and 2 will be translated, 3 and 4 will be interrupted, 5 and 6 will never get there.
    List<Integer> numbers = asList(1, 2, 3, 4);
    assertThrows(
        TimeoutException.class,
        () -> parallelize(limit(numbers.stream(), this::translateToString, 2)));
    assertThat(translated).containsEntry(1, "1");
    assertThat(translated).containsEntry(2, "2");
    assertThat(translated).doesNotContainKey(3);
    assertThat(translated).doesNotContainKey(4);
    assertInterruptedKeys().containsExactly(3, 4);
  }

  @Test public void testUninterruptible() throws InterruptedException {
    Assume.assumeTrue(mode == Mode.UNINTERRUPTIBLY);
    maxInFlight = 2;
    List<Integer> numbers = asList(1, 2, 3, 4, 5);
    CountDownLatch allowTranslation = new CountDownLatch(1);
    Thread thread = new Thread(() -> {
      try {
        parallelize(numbers.stream(), input -> {
          try {
            allowTranslation.await();
          } catch (InterruptedException e) {
            thrown.add(e);
            return;
          }
          translateToString(input);
        });
      } catch (InterruptedException | TimeoutException impossible) {
        thrown.add(impossible);
      }
    });
    thread.start();
    thread.interrupt();
    assertThat(translated).isEmpty();
    allowTranslation.countDown();
    thread.join();
    // Even interrupted, all numbers should be printed.
    assertThat(translated).containsExactlyEntriesIn(numbers.stream()
        .collect(Collectors.toMap(n -> n, Object::toString)));
  }

  @Test public void testInterruptible() throws InterruptedException {
    Assume.assumeTrue(mode == Mode.INTERRUPTIBLY);
    maxInFlight = 2;
    List<Integer> numbers = asList(1, 2, 3, 4);
    CountDownLatch inflight = new CountDownLatch(maxInFlight);
    CountDownLatch allowTranslation = new CountDownLatch(1);
    AtomicBoolean paralllelizationInterrupted = new AtomicBoolean();
    Thread thread = new Thread(() -> {
      try {
        parallelize(numbers.stream(), input -> {
          inflight.countDown();
          try {
            allowTranslation.await();
          } catch (InterruptedException e) {
            return;
          }
          translateToString(input);
        });
      } catch (InterruptedException expected) {
        paralllelizationInterrupted.set(true);
      } catch (TimeoutException e) {
        thrown.add(e);
      }
    });
    thread.start();
    inflight.await();
    thread.interrupt();
    assertThat(translated).isEmpty();
    allowTranslation.countDown();
    thread.join();
    // Only numbers already inflight are translated.
    assertThat(translated).doesNotContainKey(3);
    assertThat(translated).doesNotContainKey(4);
    assertThat(paralllelizationInterrupted.get()).isTrue();
  }

  @Test public void testMemoryLeak() throws Exception {
    AtomicInteger processed = new AtomicInteger();
    parallelize(
        IntStream.range(0, 5000).parallel().boxed()
            .map(i -> Collections.nCopies(i, i))
            .map(l -> l.toString()),
        (String s) -> {
          assertThat(s).doesNotContain("nosuchstring");
          processed.incrementAndGet();
        });
    assertThat(processed.get()).isEqualTo(5000);
  }

  private void translateToString(int i) {
    translated.put(i, Integer.toString(i));
  }

  private void translatePositiveNumberToString(int i) {
    assertThat(i).isGreaterThan(0);
    translated.put(i, Integer.toString(i));
  }

  private void runTask(Runnable task) {
    try {
      try {
        assertThat(activeThreads.incrementAndGet()).isAtMost(maxInFlight);
      } catch (Throwable e) {
        thrown.add(e);
      }
      task.run();
    }
    finally {
      activeThreads.decrementAndGet();
    }
  }

  private <T> void parallelize(Stream<? extends T> inputs, Consumer<? super T> consumer)
      throws InterruptedException, TimeoutException {
    parallelize(forAll(inputs, consumer));
  }

  private <T> void parallelize(Stream<? extends Runnable> tasks)
      throws InterruptedException, TimeoutException {
    mode.run(
        new Parallelizer(threadPool, maxInFlight), tasks, this::runTask, timeout);
  }

  private <T> Stream<Runnable> limit(
      Stream<? extends T> inputs, Consumer<? super T> consumer, int maxElements) {
    Semaphore semaphore = new Semaphore(maxElements);
    return grouped(inputs.map(input -> () -> {
      System.out.println("Blocking for " + input);
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        interrupted.add(input);
        Thread.currentThread().interrupt();
        System.out.println("Task interrupted");
        return;
      }
      System.out.println("Proceeding for " + input);
      consumer.accept(input);
      System.out.println("Done for " + input);
    }), maxInFlight);
  }

  private static Stream<Runnable> grouped(Stream<? extends Runnable> tasks, int groupSize) {
    return Lists.partition(tasks.collect(toList()), groupSize).stream()
        .flatMap(group -> {
          // For each group, no member returns until all have checked in.
          // This allows us to hold the flight tickets until every member have arrived,
          // precluding a later task from taking away the "limit"
          // intended for a slow-poke that hadn't started yet.
          CountDownLatch latch = new CountDownLatch(group.size());
          return group.stream().map(task -> () -> {
            latch.countDown();
            try {
              task.run();
            } finally {
              try {
                latch.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          });
        });
  }

  private IterableSubject assertInterruptedKeys() throws InterruptedException {
    shutdownThreadPool();  // Allow left-over threads to respond to interruptions.
    return assertThat(interrupted);
  }

  private void shutdownThreadPool() throws InterruptedException {
    threadPool.shutdown();
    threadPool.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Parameters(name = "{index}: {0}")
  public static Mode[] data() {
    return Mode.values();
  }

  private enum Mode {
    INTERRUPTIBLY {
      @Override <T> void run(
          Parallelizer parallelizer,
          Stream<? extends T> inputs, Consumer<? super T> consumer,
          Duration timeout)
          throws TimeoutException, InterruptedException {
        parallelizer.parallelize(inputs, consumer, timeout.toMillis(), TimeUnit.MILLISECONDS);
      }
    },
    UNINTERRUPTIBLY {
      @Override <T> void run(
          Parallelizer parallelizer,
          Stream<? extends T> inputs,
          Consumer<? super T> consumer,
          Duration timeout) {
        parallelizer.parallelizeUninterruptibly(inputs, consumer);
      }
    },
    ;

    abstract <T> void run(
        Parallelizer parallelizer,
        Stream<? extends T> inputs,
        Consumer<? super T> consumer,
        Duration timeout)
        throws TimeoutException, InterruptedException;
  }
}

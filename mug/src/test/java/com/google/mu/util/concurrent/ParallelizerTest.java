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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.concurrent.Parallelizer.forAll;
import static com.google.mu.util.concurrent.Parallelizer.newDaemonParallelizer;
import static java.util.Arrays.asList;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.base.Preconditions;
import com.google.common.truth.IterableSubject;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mu.util.concurrent.Parallelizer.UncheckedExecutionException;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(Enclosed.class)
public class ParallelizerTest {

  @RunWith(TestParameterInjector.class)
  public static class CollectorTest {
    @TestParameter private Threading threading;
    private ExecutorService threadPool;

    @Before public void initialize() {
      threadPool = threading.newExecutorService();
    }

    @After public void shutdown() {
      threadPool.shutdownNow();
    }

    @Test public void testInParallel_emptyInputStream() {
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      assertThat(
              Stream.empty()
                  .collect(parallelizer.inParallel(Object::toString))
                  .toMap())
          .isEmpty();
    }

    @Test public void testInParallel_fromSequentialStream() {
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      assertThat(
              Stream.of(1, 2, 3)
                  .collect(parallelizer.inParallel(Object::toString))
                  .toMap())
          .containsExactly(1, "1", 2, "2", 3, "3")
          .inOrder();
    }

    @Test public void testInParallel_fromParallelStream() {
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      assertThat(
              Stream.of(1, 2, 3)
                  .parallel()
                  .collect(parallelizer.inParallel(Object::toString))
                  .toMap())
          .containsExactly(1, "1", 2, "2", 3, "3")
          .inOrder();
    }

    @Test public void testInParallel_failure() {
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      UncheckedExecutionException thrown = assertThrows(
          UncheckedExecutionException.class,
          () -> Stream.of(1, 2, 3)
              .collect(
                  parallelizer.inParallel(i -> {
                    Preconditions.checkState(i < 3);
                    return i.toString();
                  })));
      assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test public void testNulls() {
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      assertThrows(NullPointerException.class, () -> parallelizer.inParallel(null));
    }
  }

  @RunWith(TestParameterInjector.class)
  public static class ExceptionTest {
    @Test
    public void exceptionTunnelingWorks() throws InterruptedException, TimeoutException {
      IOException exception = new IOException("deliberate");
      ExecutorService threadPool = Executors.newCachedThreadPool();
      Parallelizer parallelizer = new Parallelizer(threadPool, 3);
      try {
        parallelizer.parallelize(
            Stream.of(
                () ->
                    tunnel(
                        () -> {
                          raise(exception);
                          return "na";
                        })));
        throw new AssertionError("should have thrown");
      } catch (TunnelException e) {
        assertThat(e.getCause()).isSameInstanceAs(exception);
      } finally {
        threadPool.shutdownNow();
      }
    }
  }

  @RunWith(TestParameterInjector.class)
  public static class FactoryMethodsTest {
    @Test
    public void newExitingParallelizer_doesNotHangVm() {
      assertThat(
              Stream.of(1, 2, 3, 4, 5)
                  .collect(newDaemonParallelizer(2).inParallel(Object::toString))
                  .toMap())
          .containsExactly(1, "1", 2, "2", 3, "3", 4, "4", 5, "5")
          .inOrder();
    }

    @Test
    public void newExitingParallelizer_zeroMaxInflight() {
      assertThrows(IllegalArgumentException.class, () -> newDaemonParallelizer(0));
    }

    @Test
    public void newExitingParallelizer_negativeMaxInflight() {
      assertThrows(IllegalArgumentException.class, () -> newDaemonParallelizer(-1));
      assertThrows(IllegalArgumentException.class, () -> newDaemonParallelizer(Integer.MIN_VALUE));
    }
  }

  @RunWith(Parameterized.class)
  public static class CoreApiTest {
    private final Mode mode;
    private final Threading threading;
    private ExecutorService threadPool;
    private volatile int maxInFlight = 3;
    private Duration timeout = Duration.ofMillis(100);
    private final AtomicInteger activeThreads = new AtomicInteger();
    private final ConcurrentMap<Integer, String> translated = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Object> interrupted = new ConcurrentLinkedQueue<>();

    @Rule public final Verifier verifyTaskAssertions = new Verifier() {
      @Override protected void verify() throws Throwable {
        shutdownThreadPool();
        for (Throwable e : thrown) {
          throw e;
        }
        assertThat(activeThreads.get()).isEqualTo(0);
      }
    };

    public CoreApiTest(Mode mode, Threading threading) {
      this.mode = mode;
      this.threading = threading;
    }

    @Before public void initializeThreadPool() {
      threadPool = threading.newExecutorService();
    }

    @Test public void testOneInFlight() throws Exception {
      maxInFlight = 1;
      List<Integer> numbers = asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
      parallelize(numbers.stream(), this::translateToString);
      assertThat(translated).containsExactlyEntriesIn(mapToString(numbers));
    }

    @Test public void testFastTasks() throws Exception {
      List<Integer> numbers = asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
      parallelize(numbers.stream(), this::translateToString);
      assertThat(translated).containsExactlyEntriesIn(mapToString(numbers));
    }

    @Test public void testSlowTasks() throws Exception {
      List<Integer> numbers = asList(1, 2, 3, 4, 5);
      parallelize(numbers.stream(), delayed(Duration.ofMillis(2), this::translateToString));
      assertThat(translated).containsExactlyEntriesIn(mapToString(numbers));
    }

    @Test public void testLargeMaxInFlight() throws Exception {
      maxInFlight = Integer.MAX_VALUE;
      List<Integer> numbers = asList(1, 2, 3);
      parallelize(numbers.stream(), this::translateToString);
      assertThat(translated).containsExactlyEntriesIn(mapToString(numbers));
    }

    @Test public void testTaskExceptionDismissesPendingTasks() {
      maxInFlight = 2;
      UncheckedExecutionException exception = assertThrows(
          UncheckedExecutionException.class,
          () -> parallelize(Stream.of(
              // With maxInflight=2, at least one will print, even if a fail() task races it.
              () -> translateToString(1), () -> translateToString(1),
              () -> fail("foobar"), () -> fail("foobar"),  // both should fail
              () -> translateToString(5))));  // should be dismissed
      assertThat(exception.getCause().getMessage()).contains("foobar");
      assertThat(translated).containsEntry(1, "1");
      assertThat(translated).doesNotContainKey(5);
    }

    @Test public void testTaskExceptionCancelsInFlightTasks() throws InterruptedException {
      assumeFalse(threading == Threading.DIRECT);
      maxInFlight = 2;
      UncheckedExecutionException exception = assertThrows(
          UncheckedExecutionException.class,
          () -> parallelize(serialTasks(
              () -> translateToString(1),  // should print
              () -> blockFor(2), // Will be interrupted
              () -> fail("foobar"),  // kills the pipeline
              () -> translateToString(4))));  // should be dismissed
      assertThat(exception.getCause().getMessage()).contains("foobar");
      shutdownAndAssertInterruptedKeys().containsExactly(2);
      assertThat(translated).containsEntry(1, "1");
      assertThat(translated).doesNotContainKey(4);
    }

    @Test public void testSubmissionTimeoutCancelsInFlightTasks() throws InterruptedException {
      assumeFalse(threading == Threading.DIRECT);
      assumeTrue(mode == Mode.INTERRUPTIBLY);
      maxInFlight = 2;
      timeout = Duration.ofMillis(1);
      assertThrows(
          TimeoutException.class,
          () -> parallelize(serialTasks(
              () -> blockFor(1), // Will be interrupted
              () -> blockFor(2), // Will be interrupted
              () -> translateToString(3))));  // Times out
      shutdownAndAssertInterruptedKeys().containsExactly(1, 2);
      assertThat(translated).doesNotContainKey(3);
    }

    @Test public void testAwaitTimeoutCancelsInFlightTasks() throws InterruptedException {
      assumeFalse(threading == Threading.DIRECT);
      assumeTrue(mode == Mode.INTERRUPTIBLY);
      maxInFlight = 2;
      timeout = Duration.ofMillis(1);
      assertThrows(
          TimeoutException.class,
          () -> parallelize(serialTasks(
              () -> blockFor(1), // Will be interrupted
              () -> blockFor(2)))); // Might be interrupted
      shutdownAndAssertInterruptedKeys().contains(1);
    }

    @Test public void testUninterruptible() throws InterruptedException {
      assumeFalse(threading == Threading.DIRECT);
      assumeTrue(mode == Mode.UNINTERRUPTIBLY);
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
      assertThat(translated).containsExactlyEntriesIn(mapToString(numbers));
    }

    @Test public void testInterruptible() throws InterruptedException {
      assumeFalse(threading == Threading.DIRECT);
      assumeTrue(mode == Mode.INTERRUPTIBLY);
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

    @Test public void testErrorPropagated() {
      Error error = new Error();
      UncheckedExecutionException exception = assertThrows(
          UncheckedExecutionException.class,
          () -> parallelize(Stream.of(() -> raise(error))));
      assertThat(exception.getCause()).isSameInstanceAs(error);
    }

    @Test public void testExceptionPropagated() {
      RuntimeException exception = new RuntimeException();
      UncheckedExecutionException caught = assertThrows(
          UncheckedExecutionException.class,
          () -> parallelize(Stream.of(() -> raise(exception))));
      assertThat(caught.getCause()).isSameInstanceAs(exception);
    }

    private void translateToString(int i) {
      translated.put(i, Integer.toString(i));
    }

    private static <K> Map<K, String> mapToString(Collection<K> keys) {
      return keys.stream().collect(Collectors.toMap(k -> k, Object::toString));
    }

    /** Keeps track of active threads and makes sure it doesn't exceed {@link #maxInFlight}. */
    private void runTask(Runnable task) {
      try {
        try {
          assertThat(activeThreads.incrementAndGet()).isAtMost(maxInFlight);
        } catch (Throwable e) {
          thrown.add(e);
          return;
        }
        task.run();
      } finally {
        activeThreads.decrementAndGet();
      }
    }

    private <T> void parallelize(Stream<? extends T> inputs, Consumer<? super T> consumer)
        throws InterruptedException, TimeoutException {
      parallelize(forAll(inputs, consumer));
    }

    private void parallelize(Stream<? extends Runnable> tasks)
        throws InterruptedException, TimeoutException {
      mode.run(
          new Parallelizer(threadPool, maxInFlight), tasks, this::runTask, timeout);
    }

    private void blockFor(Object key) {
      try {
        new CountDownLatch(1).await();
      } catch (InterruptedException e) {
        interrupted.add(key);
      }
    }

    // Returns a consumer that delegates to {@code consumer} after {@code delay}. */
    private static <T> Consumer<T> delayed(Duration delay, Consumer<T> consumer) {
      return input -> {
        try {
          Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
          return;
        }
        consumer.accept(input);
      };
    }

    // Creates a task stream such that a task has to be started first before tasks after it can be
    // taken out of the stream. Helps to ensure in-flight status for tasks where we care.
    private static Stream<Runnable> serialTasks(Runnable... tasks) {
      Semaphore semaphore = new Semaphore(1);
      return asList(tasks).stream().map(task -> {
        semaphore.acquireUninterruptibly();
        return () -> {
          semaphore.release();
          task.run();
        };
      });
    }

    private IterableSubject shutdownAndAssertInterruptedKeys() throws InterruptedException {
      shutdownThreadPool();  // Allow left-over threads to respond to interruptions.
      return assertThat(interrupted);
    }

    private void shutdownThreadPool() throws InterruptedException {
      threadPool.shutdown();
      threadPool.awaitTermination(1, TimeUnit.SECONDS);
    }

    private static <E extends Throwable> void raise(E throwable) throws E {
      throw throwable;
    }

    @Parameters(name = "{index}: {0}/{1}")
    public static Object[][] data() {
      List<Object[]> groups = new ArrayList<>();
      for (Mode mode : Mode.values()) {
        for (Threading threading : Threading.values()) {
          groups.add(new Object[] {mode, threading});
        }
      }
      return groups.toArray(new Object[0][]);
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
      INTERRUPTIBLY_FOR_ITERATOR {
        @Override <T> void run(
            Parallelizer parallelizer,
            Stream<? extends T> inputs, Consumer<? super T> consumer,
            Duration timeout)
            throws TimeoutException, InterruptedException {
          parallelizer.parallelize(
              inputs.iterator(), consumer, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
      },
      UNINTERRUPTIBLY_FOR_ITERATOR {
        @Override <T> void run(
            Parallelizer parallelizer,
            Stream<? extends T> inputs,
            Consumer<? super T> consumer,
            Duration timeout) {
          parallelizer.parallelizeUninterruptibly(inputs.iterator(), consumer);
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

  private enum Threading {
    DIRECT {
      @Override ExecutorService newExecutorService() {
        return MoreExecutors.newDirectExecutorService();
      }
    },
    CACHED_THREAD_POOL {
      @Override ExecutorService newExecutorService() {
        return Executors.newCachedThreadPool();
      }
    },
    FIXED_THREAD_POOL {
      @Override ExecutorService newExecutorService() {
        return Executors.newFixedThreadPool(10);
      }
    },
    ;
    abstract ExecutorService newExecutorService();
  }

  private static <E extends Throwable> void raise(E throwable) throws E {
    throw throwable;
  }

  private static <T> T tunnel(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw new TunnelException(e);
    }
  }

  private static final class TunnelException extends RuntimeException {
    TunnelException(Throwable cause) {
      super(cause);
    }
  }
}

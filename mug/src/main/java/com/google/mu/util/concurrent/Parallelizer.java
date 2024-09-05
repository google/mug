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

import static com.google.mu.util.stream.MoreStreams.iterateOnce;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.mu.util.stream.BiStream;

/**
 * Utility to support <a href="https://en.wikipedia.org/wiki/Structured_concurrency">structured
 * concurrency</a> for <em>IO-bound</em> subtasks of a single unit of work, while limiting the max
 * concurrency.
 *
 * <p>For example, the following code saves a stream of {@code UserData} in parallel with at most 3
 * concurrent RPC calls at the same time:
 *
 * <pre>{@code
 * new Parallelizer(executor, 3)
 *     .parallelize(userDataStream.filter(UserData::isModified), userService::save);
 * }</pre>
 *
 * <p>Similar to parallel streams (and unlike executors), these sub-tasks are considered integral
 * parts of one unit of work. Failure of any sub-task aborts the entire work, automatically. If an
 * exception isn't fatal, the sub-task should catch and handle it.
 *
 * <p>How does it stack against parallel stream itself?
 * The parallel stream counterpart to the above example use case may look like:
 * <pre>  {@code
 *   userDataStream.filter(UserData::isModified).parallel().forEach(userService::save);
 * }</pre>
 *
 * A few key differences: <ul>
 * <li>A parallel stream doesn't use arbitrary {@link ExecutorService}. It by default uses
 *     either the enclosing {@link ForkJoinPool} or the common {@code ForkJoinPool} instance.
 * <li>By running in a dedicated {@link ForkJoinPool}, a parallel stream can take a custom target
 *     concurrency, but it's not guaranteed to be <em>max</em> concurrency.
 * <li>Parallel streams are for CPU-bound computations; while {@code Parallelizer} deals with
 *     IO-bound operations.
 * <li>{@link #parallelize parallelize()} can be interrupted, and can time out;
 *     parallel streams are uninterruptible.
 * <li>When a task throws, {@code Parallelizer} dismisses pending tasks, and cancels all in-flight
 *     tasks (it's up to the user code to properly handle thread interruptions).
 *     So if a worker thread is waiting on some resource, it'll be interrupted without hanging
 *     the thread forever.
 * <li>{@code Parallelizer} wraps exceptions thrown by the worker threads, making stack trace
 *     clearer.
 * </ul>
 *
 * <p>And how do you choose between {@code Parallelizer} and {@link ExecutorService}?
 * Could you use something like the following instead?
 * <pre>  {@code
 *   ExecutorService pool = Executors.newFixedThreadPool(3);
 *   try {
 *     List<Future<?>> futures = userData
 *         .filter(...)
 *         .map(() -> pool.submit(() -> userService.save(data)))
 *         .collect(toList());
 *     for (Future<?> future : futures) {
 *       future.get();
 *     }
 *   } finally {
 *     pool.shutdownNow();
 *   }
 * }</pre>
 *
 * Some differences for consideration:<ul>
 * <li><b>Memory Concern</b>
 *     <ul>
 *     <li>The thread pool queues all pending tasks. For large streams (like reading hundreds
 *         of thousands of task input data from a file), it can run out of memory quickly.
 *      <li>Storing all the future objects in a list may also use up too much memory for large
 *          number of sub tasks.
 *     </ul>
 * <li><b>Exception Handling (and fail fast)</b>
 *     <ul>
 *     <li>Executors treat submitted tasks as independent. One task may fail and the other tasks
 *         won't be affected.
 *
 *         <p>But for co-dependent sub tasks that were parallelized only for performance reasons
 *         (as in parallel streams), you'll want to abort the whole parallel pipeline upon any
 *         critical exception, the same as if they were run sequentially.
 *
 *         <p>Aborting a parallel pipeline requires complex concurrent logic to coordinate between
 *         the sub tasks and the executor in order to dismiss pending sub tasks and also to cancel
 *         sub tasks that are already running. Otherwise, when an exception is thrown from a sub
 *         task, the other left-over sub tasks will continue to run, some may even hang
 *         indefinitely.
 *     <li>Automatic cancellation propagation. When {@link #parallelize parallelize()} is
 *         interrupted, all running tasks will be automatically canceled; all pending tasks
 *         automatically dismissed.
 *     <li>You may resort to shutting down the executor to achieve similar result (cancelling the
 *         left-over sub tasks). Although even knowing whether a sub task has failed isn't trivial.
 *         The above code example uses {@link Future#get}, but it won't help if a sub task
 *         submitted earlier is still running or being blocked, while a later-submitted sub task
 *         has failed.
 *     <li>And, {@code ExecutorService}s are often set up centrally and shared among different
 *         classes and components in the application. You may not have the option to create and
 *         shut down a thread pool of your own.
 *     </ul>
 * </ul>
 *
 * <p>Stream parameters used in this class are always consumed in the calling thread and don't have
 * to be thread safe.
 *
 * @since 1.1
 */
public final class Parallelizer {
  private static final Logger logger = Logger.getLogger(Parallelizer.class.getName());

  private final ExecutorService executor;
  private final int maxInFlight;

  /**
   * Constructs a {@code Parallelizer} that runs tasks with {@code executor}.
   * At any given time, at most {@code maxInFlight} tasks are allowed to be submitted to
   * {@code executor}.
   *
   * <p>Note that a task being submitted to {@code executor} doesn't guarantee immediate
   * execution, if for example all worker threads in {@code executor} are busy.
   */
  public Parallelizer(ExecutorService executor, int maxInFlight) {
    this.executor = requireNonNull(executor);
    this.maxInFlight = maxInFlight;
    if (maxInFlight <= 0) throw new IllegalArgumentException("maxInFlight = " + maxInFlight);
  }

  /**
   * Returns a {@link Parallelizer} using virtual threads for running tasks, with at most
   * {@code maxInFlight} tasks running concurrently.
   *
   * <p>Only applicable in JDK 21 (throws if below JDK 21).
   *
   * @since 7.2
   */
  public static Parallelizer virtualThreadParallelizer(int maxInFlight) {
    return new Parallelizer(VirtualThread.executor, maxInFlight);
  }

  /**
   * Returns a new {@link Parallelizer} based on an ExecutorService that exits when the application
   * is complete. It does so by using daemon threads.
   *
   * <p>Typically used by the {@code main()} method or as a static final field.
   *
   * @since 6.5
   */
  public static Parallelizer newDaemonParallelizer(int maxInFlight) {
    AtomicInteger threadCount = new AtomicInteger();
    return new Parallelizer(
        Executors.newFixedThreadPool(
            maxInFlight,
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setDaemon(true);
              thread.setName("DaemonParallelizer#" + threadCount.getAndIncrement());
              return thread;
            }),
        maxInFlight);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public <T> void parallelize(Stream<? extends T> inputs, Consumer<? super T> consumer)
      throws InterruptedException {
    parallelize(forAll(inputs, consumer));
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public <T> void parallelize(Iterator<? extends T> inputs, Consumer<? super T> consumer)
      throws InterruptedException {
    parallelize(stream(inputs), consumer);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, timeout is triggered, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(
      Stream<? extends T> inputs, Consumer<? super T> consumer,
      Duration heartbeatTimeout)
      throws TimeoutException, InterruptedException {
    parallelize(inputs, consumer, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, timeout is triggered, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @param timeUnit the unit of {@code heartbeatTimeout}
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(
      Stream<? extends T> inputs, Consumer<? super T> consumer,
      long heartbeatTimeout, TimeUnit timeUnit)
      throws TimeoutException, InterruptedException {
    parallelize(forAll(inputs, consumer), heartbeatTimeout, timeUnit);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, timeout is triggered, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(
      Iterator<? extends T> inputs, Consumer<? super T> consumer,
      Duration heartbeatTimeout)
      throws TimeoutException, InterruptedException {
    parallelize(inputs, consumer, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks until either all tasks have
   * finished, timeout is triggered, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @param timeUnit the unit of {@code heartbeatTimeout}
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(
      Iterator<? extends T> inputs, Consumer<? super T> consumer,
      long heartbeatTimeout, TimeUnit timeUnit)
      throws TimeoutException, InterruptedException {
    parallelize(stream(inputs), consumer, heartbeatTimeout, timeUnit);
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks uninterruptibly until
   * either all tasks have finished, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   */
  public <T> void parallelizeUninterruptibly(
      Stream<? extends T> inputs, Consumer<? super T> consumer) {
    parallelizeUninterruptibly(forAll(inputs, consumer));
  }

  /**
   * Runs {@code consumer} for {@code inputs} in parallel and blocks uninterruptibly until
   * either all tasks have finished, or any exception is thrown upon which all pending tasks are
   * canceled (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @param inputs the inputs to be passed to {@code consumer}
   * @param consumer to be parallelized
   */
  public <T> void parallelizeUninterruptibly(
      Iterator<? extends T> inputs, Consumer<? super T> consumer) {
    parallelizeUninterruptibly(stream(inputs), consumer);
  }

  /**
   * Runs {@code tasks} in parallel and blocks until either all tasks have finished,
   * or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code tasks} stream is consumed only in the calling thread in iteration order.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void parallelize(Stream<? extends Runnable> tasks) throws InterruptedException {
    try {
      parallelize(tasks, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Runs {@code tasks} in parallel and blocks uninterruptibly until either all tasks have finished,
   * timeout is triggered, or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code tasks} stream is consumed only in the calling thread in iteration order.
   *
   * @param tasks the tasks to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if timeout exceeded while waiting.
   */
  public void parallelize(
      Stream<? extends Runnable> tasks, Duration heartbeatTimeout)
      throws TimeoutException, InterruptedException {
    parallelize(tasks, heartbeatTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Runs {@code tasks} in parallel and blocks uninterruptibly until either all tasks have finished,
   * timeout is triggered, or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   *
   * <p>The {@code tasks} stream is consumed only in the calling thread in iteration order.
   *
   * @param tasks the tasks to be parallelized
   * @param heartbeatTimeout at least one task needs to complete every {@code heartbeatTimeout}.
   * @param timeUnit the unit of {@code heartbeatTimeout}
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if timeout exceeded while waiting.
   */
  public void parallelize(
      Stream<? extends Runnable> tasks, long heartbeatTimeout, TimeUnit timeUnit)
      throws TimeoutException, InterruptedException {
    requireNonNull(tasks);
    requireNonNull(timeUnit);
    if (heartbeatTimeout <= 0) throw new IllegalArgumentException("timeout = " + heartbeatTimeout);
    Flight flight = new Flight();
    try {
      for (Runnable task : iterateOnce(tasks)) {
        flight.checkIn(heartbeatTimeout, timeUnit);
        flight.board(task);
      }
      flight.land(heartbeatTimeout, timeUnit);
    } catch (Throwable e) {
      flight.cancel();
      throw e;
    }
  }

  /**
   * Runs {@code tasks} in parallel and blocks uninterruptibly until either all tasks have finished,
   * or any exception is thrown upon which all pending tasks are canceled
   * (but the method returns without waiting for the tasks to respond to cancellation).
   */
  public void parallelizeUninterruptibly(Stream<? extends Runnable> tasks) {
    Flight flight = new Flight();
    try {
      for (Runnable task : iterateOnce(tasks)) {
        flight.checkInUninterruptibly();
        flight.board(task);
      }
      flight.landUninterruptibly();
    } catch (Throwable e) {
      flight.cancel();
      throw e;
    }
  }

  /**
   * Returns a {@link Collector} that runs {@code concurrentFunction} in parallel using this {@code
   * Parallelizer} and returns the inputs and outputs in a {@link BiStream}, in encounter order of
   * the input elements.
   *
   * <p>For example: <pre>{@code
   * ImmutableListMultimap<String, Asset> resourceAssets =
   *     resources.stream()
   *         .collect(parallelizer.inParallel(this::listAssets))
   *         .collect(flatteningToImmutableListMultimap(List::stream));
   * }</pre>
   *
   * <p>In Java 20 using structured concurrency, it can be implemented equivalently as in:
   * <pre>{@code
   * ImmutableListMultimap<String, Asset> resourceAssets;
   * try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
   *   ImmutableList<Future<?>> results =
   *       resources.stream()
   *           .map(resource -> scope.fork(() -> listAssets(resource)))
   *           .collect(toImmutableList());
   *   scope.join();
   *   resourceAssets =
   *       BiStream.zip(resources, results)
   *           .mapValues(Future::resultNow)
   *           .collect(flatteningToImmutableListMultimap(List::stream));
   * }
   * }</pre>
   *
   * @param concurrentFunction a function that's safe to be run concurrently, and is usually
   *     IO-intensive (such as an outgoing RPC or reading distributed storage).
   *
   * @since 6.5
   */
  public <I, O> Collector<I, ?, BiStream<I, O>> inParallel(
      Function<? super I, ? extends O> concurrentFunction) {
    requireNonNull(concurrentFunction);
    return collectingAndThen(
        toList(),
        inputs -> {
          List<O> outputs = new ArrayList<>(inputs.size());
          outputs.addAll(Collections.nCopies(inputs.size(), null));
          parallelizeUninterruptibly(
              IntStream.range(0, inputs.size()).boxed(),
              i -> outputs.set(i, concurrentFunction.apply(inputs.get(i))));
          return BiStream.zip(inputs, outputs);
        });
  }

  static <T> Stream<Runnable> forAll(Stream<? extends T> inputs, Consumer<? super T> consumer) {
    requireNonNull(consumer);
    return inputs.map(input -> () -> consumer.accept(input));
  }

  private final class Flight {
    // fairness is irrelevant here since only the main thread ever calls acquire().
    private final Semaphore semaphore = new Semaphore(maxInFlight);
    private final ConcurrentMap<Object, Future<?>> onboard = new ConcurrentHashMap<>();
    private volatile ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();

    void checkIn(long timeout, TimeUnit timeUnit)
        throws InterruptedException, TimeoutException, UncheckedExecutionException {
      boolean acquired = semaphore.tryAcquire(timeout, timeUnit);
      propagateExceptions();
      if (!acquired) throw new TimeoutException();
    }

    void checkInUninterruptibly() throws UncheckedExecutionException {
      semaphore.acquireUninterruptibly();
      propagateExceptions();
    }

    void board(Runnable task) {
      requireNonNull(task);
      AtomicBoolean done = new AtomicBoolean();
      // Use '<:' to denote happens-before throughout this method body.
      Future<?> future = executor.submit(() -> {
        try {
          try {
            task.run();
          } finally {
            done.set(true);  // A
            onboard.remove(done);  // B
          }
        } catch (Throwable e) {
          ConcurrentLinkedQueue<Throwable> toPropagate = thrown;
          if (toPropagate == null) {
            if (Thread.currentThread().isInterrupted()) {
              // If we are cancelled (and interrupted), the exception is likely due to the
              // cancellation. Don't log the noisy stack trace.
              logger.info(
                  String.format(
                      "worker thread (%s) interrupted - %s",
                      Thread.currentThread().getName(), e.getMessage()));
            } else {
              // The main thread propagates exceptions as soon as any task fails.
              // If a task did not respond in time and yet fails afterwards, the main thread has
              // already thrown and nothing will propagate this exception.
              // So just log it as best effort.
              logger.log(Level.WARNING, "Orphan task failure", e);
            }
          } else {
            // Upon race condition, the exception may be added while the main thread is propagating.
            // It's ok though since the best we could have done is logging.
            toPropagate.add(e);
          }
        } finally {
          semaphore.release();
        }
      });
      onboard.put(done, future);  // C
      checkInFlight();
      // A <: B, C <: D <: E
      // if B <: C => A <: C => done == true => put() <: remove()
      // if C <: B => put() <: remove()
      // remove() could be executed more than once, but it's idempotent.
      if (done.get()) {  // D
        onboard.remove(done);  // E
      }
      propagateExceptions();
    }

    void land(long timeout, TimeUnit timeUnit)
        throws InterruptedException, TimeoutException, UncheckedExecutionException {
      for (int i = freeze(); i > 0; i--) checkIn(timeout, timeUnit);
    }

    void landUninterruptibly() throws UncheckedExecutionException {
      for (int i = freeze(); i > 0; i--) checkInUninterruptibly();
    }

    void cancel() {
      // When we cancel a scheduled-but-not-executed task, we'll leave the semaphore unreleased.
      // But it's okay because the only time we cancel is when we are aborting the whole pipeline
      // and nothing will use the semaphore after that.
      onboard.values().forEach(f -> f.cancel(true));
    }

    private void checkInFlight() {
      int inflight = onboard.size();
      if (inflight > maxInFlight) throw new IllegalStateException("inflight = " + inflight);
    }

    /** If any task has thrown, propagate all task exceptions. */
    private void propagateExceptions() {
      ConcurrentLinkedQueue<Throwable> toPropagate = thrown;
      RuntimeException wrapperException = null;
      for (Throwable exception : toPropagate) {
        if (wrapperException == null) {
          wrapperException = new UncheckedExecutionException(exception);
        } else {
          wrapperException.addSuppressed(exception);
        }
      }
      if (wrapperException != null) {
        thrown = null;
        throw wrapperException;
      }
    }

    private int freeze() {
      int remaining = maxInFlight - semaphore.drainPermits();
      propagateExceptions();
      return remaining;
    }
  }

  static final class VirtualThread {
    static final ExecutorService executor;
    static {
      try {
        executor = (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new AssertionError(e);
      }
    }
  }

  /** While we don't pull in Guava for its {@code UncheckedExecutionException}. */
  static class UncheckedExecutionException extends RuntimeException {
    UncheckedExecutionException(Throwable cause) {
      super(cause);
    }
    private static final long serialVersionUID = 1L;
  }

  private static <T> Stream<T> stream(Iterator<? extends T> it) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
  }
}

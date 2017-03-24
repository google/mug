package com.google.mu.util.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.google.mu.util.Iterate;

/**
 * Utility for running a (large) stream of tasks in parallel while limiting the maximum number of
 * in-flight tasks.
 *
 * <p>For example, the following code saves a stream of {@code UserData} in parallel with at most
 * 3 in-flight remote service calls at the same time: <pre>  {@code
 *   new Parallelizer(executor, 3)
 *       .parallelize(userDataStream.filter(UserData::isModified), userService::save);
 * }</pre>
 *
 * It's worth noting that the above code looks similar to built-in parallel stream:
 * <pre>  {@code
 *   userDataStream.filter(UserData::isModified).parallel().forEach(userService::save);
 * }</pre>
 *
 * Both get the job done, with a few differences: <ul>
 * <li>A parallel stream doesn't use arbitrary {@link ExecutorService}. It by default uses
 *     either the enclosing {@link ForkJoinPool} or the common {@code ForkJoinPool} instance.
 *     <ul>
 *     <li>Use {@code Parallelizer} if you wish to use a shared ExecutorService.
 *     <li>Use parallel streams otherwise.
 *     </ul>
 * <li>By running in a dedicated {@link ForkJoinPool}, a parallel stream can take a custom target
 *     concurrency, but it's not guaranteed to be <em>max</em> concurrency. <ul>
 *     <li>Use {@code Parallelizer} if setting max concurrency is important.
 *     <li>Or else use parallel streams.
 *     </ul>
 * <li>Parallel streams are for running mass <em>computations</em> concurrently. The framework
 *     can parallelize the inputs, the {@code map()} calls, the {@code filter()} calls, or
 *     all of them. This comes with a requirement that the input stream must be safe to read
 *     from multiple threads. On the other hand, {@code Parallelizer} is for parallelizing
 *     <em>side-effects</em> encapsulated in the {@link Runnable} or {@link Consumer}.
 *     The input stream is always read from the calling thread so it doesn't have to be
 *     thread safe. <ul>
 *     <li>Use {@code Parallelizer} if the input stream isn't thread safe
 *         (and is too large to be copied into a thread-safe collection}.
 *     <li>Use parallel streams otherwise.
 *     </ul>
 * <li>{@link #parallelize} can be interrupted, and can time out;
 *     parallel streams are uninterruptible.
 * <li>When a task throws, {@code Parallelizer} dismisses pending tasks, and cancels all in-flight
 *     tasks (it's up to the user code to properly handle thread interruptions).
 *     So if a worker thread is waiting on some resource, it'll be interrupted without hanging
 *     the thread forever (arguably only a problem when parallelizing side-effects). <ul>
 *     <li>Use {@code Parallelizer} if you use a shared {@code ExecutorService} and want
 *         tasks to be interrupted upon failures.
 *     <li>Use parallel streams otherwise.
 *     </ul>
 * <li>{@code Parallelizer} wraps exceptions thrown by the worker threads, making stack trace
 *     clearer.
 * </ul>
 *
 * <p>Another relevant comparison is with using {@link ExecutorService#submit} manually.
 * For example, it seems relatively straight-forward to do: <pre>  {@code
 *   ExecutorService pool = Executors.newFixedThreadPool(3);
 *   try {
 *     List<Future<?>> futures = tasks.map(pool::submit).collect(toList());
 *     for (Future<?> future : futures) {
 *       future.get();
 *     }
 *   } finally {
 *     pool.shutdownNow();
 *   }
 * }</pre>
 * While the above code appears to do most of the stuff, it doesn't satisfy all the requirements.
 * Namely: <ul>
 * <li>Fixed thread pool still queues all pending tasks. For large streams, it will run out of
 *     memory.
 * <li>When using a shared {@code ExecutorService} instance, creating a dedicated
 *     {@code fixedThreadPool()} isn't an option anyway.
 * <li>Like built-in {@code Stream}s, we want fail-fast when any task throws.
 *     The above code doesn't always fail fast.
 * <li>Putting all {@code Future} objects into a List will run out of memory for large streams.
 * </ul>
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
   * Consumes {@code inputs} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(Stream<? extends T> inputs, Consumer<? super T> consumer)
      throws InterruptedException {
    parallelize(forAll(inputs, consumer));
  }

  /**
   * Consumes {@code inputs} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if the configured timeout is exceeded while waiting.
   */
  public <T> void parallelize(
      Stream<? extends T> inputs, Consumer<? super T> consumer, long taskTimeout, TimeUnit timeUnit)
      throws TimeoutException, InterruptedException {
    parallelize(forAll(inputs, consumer), taskTimeout, timeUnit);
  }

  /**
   * Consumes {@code inputs} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
   *
   * <p>The {@code inputs} stream is consumed only in the calling thread in iteration order.
   */
  public <T> void parallelizeUninterruptibly(
      Stream<? extends T> inputs, Consumer<? super T> consumer) {
    parallelizeUninterruptibly(forAll(inputs, consumer));
  }

  /**
   * Runs {@code tasks} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
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
   * Runs {@code tasks} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
   *
   * <p>The {@code tasks} stream is consumed only in the calling thread in iteration order.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws TimeoutException if timeout exceeded while waiting.
   */
  public void parallelize(Stream<? extends Runnable> tasks, long taskTimeout, TimeUnit timeUnit)
      throws TimeoutException, InterruptedException {
    requireNonNull(timeUnit);
    if (taskTimeout <= 0) throw new IllegalArgumentException("timeout = " + taskTimeout);
    Flight flight = new Flight();
    try {
      for (Runnable task : Iterate.once(tasks)) {
        flight.checkIn(taskTimeout, timeUnit);
        flight.board(task);
      }
      flight.land(taskTimeout, timeUnit);
    } catch (Throwable e) {
      flight.cancel();
      throw e;
    }
  }

  /**
   * Runs {@code tasks} in parallel.
   * This method blocks until either all tasks have finished, or any exception is thrown,
   * upon which all pending tasks are canceled (but the method returns without waiting for
   * the tasks to respond to cancellation).
   */
  public void parallelizeUninterruptibly(Stream<? extends Runnable> tasks) {
    Flight flight = new Flight();
    try {
      for (Runnable task : Iterate.once(tasks)) {
        flight.checkInUninterruptibly();
        flight.board(task);
      }
      flight.landUninterruptibly();
    } catch (Throwable e) {
      flight.cancel();
      throw e;
    }
  }

  static <T> Stream<Runnable> forAll(Stream<? extends T> inputs, Consumer<? super T> consumer) {
    requireNonNull(consumer);
    return inputs.map(input -> () -> consumer.accept(input));
  }

  private final class Flight {
    // fairness is irrelevant here since only the main thread ever calls acquire().
    private final Semaphore semaphore = new Semaphore(maxInFlight);
    private final BoundedBuffer<Future<?>> onboard = new BoundedBuffer<>(maxInFlight);
    private volatile ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
  
    void checkIn(long timeout, TimeUnit timeUnit)
        throws InterruptedException, TimeoutException, UncheckedExecutionException {
      propagateExceptions();
      boolean acquired = semaphore.tryAcquire(timeout, timeUnit);
      propagateExceptions();
      if (!acquired) throw new TimeoutException();
    }
  
    void checkInUninterruptibly() throws UncheckedExecutionException {
      propagateExceptions();
      semaphore.acquireUninterruptibly();
      propagateExceptions();
    }

    void board(Runnable task) {
      requireNonNull(task);
      onboard.add(executor.submit(() -> {
        try {
          task.run();
        } catch (Throwable e) {
          ConcurrentLinkedQueue<Throwable> toPropagate = thrown;
          if (toPropagate == null) {
            // The main thread propagates exceptions as soon as any task fails.
            // If a task did not respond in time and yet fails afterwards, the main thread has
            // already thrown and nothing will propagate this exception.
            // So just log it as best effort.
            logger.log(Level.WARNING, "Orphan task failure", e);
          } else {
            // Upon race condition, the exception may be added while the main thread is propagating.
            // It's ok though since the best we could have done is logging.
            toPropagate.add(e);
          }
        } finally {
          semaphore.release();
        }
      }));
    }

    void land(long timeout, TimeUnit timeUnit)
        throws InterruptedException, TimeoutException, UncheckedExecutionException {
      for (int i = 0; i < maxInFlight; i++) checkIn(timeout, timeUnit);
    }

    void landUninterruptibly() throws UncheckedExecutionException {
      for (int i = 0; i < maxInFlight; i++) checkInUninterruptibly();
    }

    void cancel() {
      // When we cancel a scheduled-but-not-executed task, we'll leave the semaphore unreleased.
      // But it's okay because the only time we cancel is when we are aborting the whole pipeline
      // and nothing will use the semaphore after that.
      onboard.asList().stream().forEach(f -> f.cancel(true));
    }

    /** If any task has thrown, propagate all task exceptions. */
    private void propagateExceptions() {
      ConcurrentLinkedQueue<Throwable> toPropagate = thrown;
      UncheckedExecutionException executionException = null;
      for (Throwable exception : toPropagate) {
        if (executionException == null) {
          executionException = new UncheckedExecutionException(exception);
        } else {
          executionException.addSuppressed(exception);
        }
      }
      if (executionException != null) {
        thrown = null;
        throw executionException;
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
}
